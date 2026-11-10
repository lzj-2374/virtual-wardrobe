package com.liuzhangjie.wardrobe.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liuzhangjie.wardrobe.ai.image.OutfitImageComposer;
import com.liuzhangjie.wardrobe.ai.image.OutfitImageService;
import com.liuzhangjie.wardrobe.ai.WardrobeSupport;
import com.liuzhangjie.wardrobe.config.RabbitConfig;
import com.liuzhangjie.wardrobe.entity.AiImageTask;
import com.liuzhangjie.wardrobe.entity.AiRecommendation;
import com.liuzhangjie.wardrobe.entity.AiRecommendationItem;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.Outfit;
import com.liuzhangjie.wardrobe.mapper.AiImageTaskMapper;
import com.liuzhangjie.wardrobe.mapper.AiRecommendationItemMapper;
import com.liuzhangjie.wardrobe.mapper.AiRecommendationMapper;
import com.liuzhangjie.wardrobe.mapper.ClothingMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生图任务的消费者：真正干活的地方。
 * <p>
 * 这里是整个异步链路最长的一段（10~30 秒），所以要特别注意三件事：
 * <ol>
 *   <li><b>幂等</b>——消息可能被重复投递（网络抖动、消费者重启）。
 *       开头先查状态，已经是"成功"就直接返回，不重复画、不重复扣额度</li>
 *   <li><b>终态</b>——任务必须收敛到"成功"或"失败"，不能永远卡在"生成中"。
 *       所以最后一次重试失败时，要主动把状态改成失败，而不是放任异常往上抛</li>
 *   <li><b>回写</b>——图生成完了，除了改任务表，还要把推荐结果和（如果已放入的）搭配封面一起更新，
 *       否则用户看到的是"任务成功了但卡片上还是没图"</li>
 * </ol>
 * 重试次数刻意和 yml 里的 {@code listener.simple.retry.max-attempts} 保持一致：
 * 两边对不上就会出现"任务已标记失败，但消息还在重试"的错位状态。
 */
@Slf4j
@Component
public class AiImageConsumer {

    /** 与 application.yml 的 retry.max-attempts 保持一致 */
    private static final int MAX_ATTEMPTS = 3;

    private final AiImageTaskMapper imageTaskMapper;
    private final AiRecommendationMapper recommendationMapper;
    private final AiRecommendationItemMapper recommendationItemMapper;
    private final ClothingMapper clothingMapper;
    private final OutfitMapper outfitMapper;
    private final OutfitImageService imageService;

    public AiImageConsumer(AiImageTaskMapper imageTaskMapper,
                           AiRecommendationMapper recommendationMapper,
                           AiRecommendationItemMapper recommendationItemMapper,
                           ClothingMapper clothingMapper,
                           OutfitMapper outfitMapper,
                           OutfitImageService imageService) {
        this.imageTaskMapper = imageTaskMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationItemMapper = recommendationItemMapper;
        this.clothingMapper = clothingMapper;
        this.outfitMapper = outfitMapper;
        this.imageService = imageService;
    }

    @RabbitListener(queues = RabbitConfig.IMAGE_QUEUE)
    public void onMessage(AiImageMessage message) {
        String taskId = message.getTaskId();
        log.info("开始处理生图任务，taskId={}，recommendationId={}", taskId, message.getRecommendationId());

        AiImageTask task = imageTaskMapper.selectOne(
                new LambdaQueryWrapper<AiImageTask>().eq(AiImageTask::getTaskId, taskId));
        if (task == null) {
            // 任务被删了，或者消息来自一个已经回滚的事务。直接丢弃，让它正常 ack
            log.warn("生图任务不存在，丢弃消息，taskId={}", taskId);
            return;
        }

        // 幂等第一道闸：已经成功过就不再画
        if (AiImageTask.STATUS_SUCCESS == task.getStatus()) {
            log.info("任务已成功，跳过重复消费，taskId={}", taskId);
            return;
        }

        int attempt = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        imageTaskMapper.incrementRetry(taskId);

        try {
            String imageUrl = doGenerate(task, attempt, message.getGender());

            // 幂等第二道闸：带条件更新。影响行数为 0 说明别的线程已经把它置成功了，
            // 这次生成的图直接不要，避免两边互相覆盖
            int updated = imageTaskMapper.updateStatusIfNotSuccess(
                    taskId, AiImageTask.STATUS_SUCCESS, imageUrl, null);
            if (updated == 0) {
                log.warn("任务状态已被其他消费者改写，本次结果丢弃，taskId={}，url={}", taskId, imageUrl);
                return;
            }

            writeBack(task, imageUrl);
            log.info("生图完成，taskId={}，第 {} 次尝试，url={}", taskId, attempt, imageUrl);

        } catch (Exception e) {
            handleFailure(task, attempt, e);
        }
    }

    /**
     * 真正的生成动作：把推荐里的单品查出来，拼成绘制项，交给合成/生图服务。
     *
     * @param gender 模特性别（1 男 / 2 女 / null 未指定），由生产者在投递时判定好
     */
    private String doGenerate(AiImageTask task, int attempt, Integer gender) {
        AiRecommendation recommendation = recommendationMapper.selectById(task.getRecommendationId());
        if (recommendation == null || Integer.valueOf(1).equals(recommendation.getDeleted())) {
            // 推荐已经失效（用户删了对应搭配），这时候再画没有意义
            throw new IllegalStateException("推荐结果不存在或已失效");
        }

        List<AiRecommendationItem> items = recommendationItemMapper.selectList(
                new LambdaQueryWrapper<AiRecommendationItem>()
                        .eq(AiRecommendationItem::getRecommendationId, recommendation.getId())
                        .orderByAsc(AiRecommendationItem::getRole));

        if (items.isEmpty()) {
            throw new IllegalStateException("推荐结果里没有单品，无法生图");
        }

        // 一次把这一套搭配涉及的衣服查出来，避免循环里逐条查
        List<Long> clothingIds = items.stream().map(AiRecommendationItem::getClothingId).toList();
        Map<Long, Clothing> clothingMap = clothingMapper.selectList(
                        new LambdaQueryWrapper<Clothing>().in(Clothing::getId, clothingIds))
                .stream().collect(Collectors.toMap(Clothing::getId, Function.identity()));

        List<OutfitImageComposer.RenderItem> renderItems = new ArrayList<>();
        for (AiRecommendationItem item : items) {
            Clothing c = clothingMap.get(item.getClothingId());
            if (c == null) {
                // 衣服被删了，跳过这件，不要让整张图失败
                log.warn("推荐引用的衣物已不存在，跳过：clothingId={}", item.getClothingId());
                continue;
            }
            renderItems.add(new OutfitImageComposer.RenderItem(
                    c.getName(), item.getRole(), WardrobeSupport.roleLabel(item.getRole()), c.getImageUrl()));
        }
        if (renderItems.isEmpty()) {
            throw new IllegalStateException("推荐引用的衣物全部不存在，无法生图");
        }

        log.debug("生图第 {} 次尝试，单品数={}，模特性别={}，taskId={}",
                attempt, renderItems.size(), gender, task.getTaskId());

        return imageService.generate(
                recommendation.getName(),
                recommendation.getColorNote(),
                recommendation.getScene(),
                com.liuzhangjie.wardrobe.ai.OutfitIntent.seasonName(
                        recommendation.getSeason() == null ? 15 : recommendation.getSeason()),
                renderItems,
                gender);
    }

    /**
     * 生成成功后的三处回写。
     * 顺序有意：先任务表（上面已做），再推荐表（卡片上要显示图），最后搭配封面（列表页要显示图）。
     */
    private void writeBack(AiImageTask task, String imageUrl) {
        AiRecommendation recommendation = recommendationMapper.selectById(task.getRecommendationId());
        if (recommendation == null) {
            return;
        }

        recommendation.setImageUrl(imageUrl);
        recommendation.setImageStatus(AiRecommendation.IMAGE_SUCCESS);
        recommendationMapper.updateById(recommendation);

        // 如果用户已经点过「放入我的搭配」，封面也要跟着换，否则搭配列表里还是占位图。
        // 只在当前没有封面时才写，避免覆盖用户手动传的封面
        Long outfitId = recommendation.getOutfitId();
        if (outfitId != null) {
            Outfit outfit = outfitMapper.selectById(outfitId);
            if (outfit != null && (outfit.getCoverUrl() == null || outfit.getCoverUrl().isBlank())) {
                outfit.setCoverUrl(imageUrl);
                outfitMapper.updateById(outfit);
                log.info("已同步搭配封面，outfitId={}", outfitId);
            }
        }
    }

    /**
     * 失败处理。核心判断是"这是最后一次机会吗"：
     * <ul>
     *   <li>不是 → 抛出去，让 Spring 的重试机制再来一次</li>
     *   <li>是 → 先把任务置成失败（保证前端能收到终态），再抛出去让消息进死信队列，
     *       这样运维还能在 {@code wardrobe.ai.image.dead} 里看到原始的失败消息</li>
     * </ul>
     */
    private void handleFailure(AiImageTask task, int attempt, Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        log.warn("生图失败，taskId={}，第 {} 次尝试，原因：{}", task.getTaskId(), attempt, reason);

        if (attempt < MAX_ATTEMPTS) {
            // 还有机会，交给 Spring 重试（yml 里 max-attempts=3）
            throw new IllegalStateException("生图失败，将重试：" + reason, e);
        }

        imageTaskMapper.updateStatusIfNotSuccess(
                task.getTaskId(), AiImageTask.STATUS_FAILED, null, truncate(reason));

        // 推荐结果的状态也要改，否则 AI 助手那张卡片会一直显示"生成中"的骨架屏
        AiRecommendation recommendation = recommendationMapper.selectById(task.getRecommendationId());
        if (recommendation != null) {
            recommendation.setImageStatus(AiRecommendation.IMAGE_FAILED);
            recommendationMapper.updateById(recommendation);
        }

        // 抛出去 → Spring 的重试已耗尽 → 消息被 reject 且不重新入队 → 进死信队列
        throw new IllegalStateException("生图重试用尽：" + reason, e);
    }

    /** error_msg 列只有 255，超长会把 insert 打挂，截断保护 */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 240 ? text : text.substring(0, 240);
    }

}
