package com.liuzhangjie.wardrobe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.ai.OutfitAdvisorRouter;
import com.liuzhangjie.wardrobe.ai.OutfitDraft;
import com.liuzhangjie.wardrobe.ai.OutfitIntent;
import com.liuzhangjie.wardrobe.common.BusinessException;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.entity.AiChatDTO;
import com.liuzhangjie.wardrobe.entity.AiChatVO;
import com.liuzhangjie.wardrobe.entity.AiConversation;
import com.liuzhangjie.wardrobe.entity.AiImageRequestDTO;
import com.liuzhangjie.wardrobe.entity.AiImageTask;
import com.liuzhangjie.wardrobe.entity.AiImageTaskVO;
import com.liuzhangjie.wardrobe.entity.AiMessage;
import com.liuzhangjie.wardrobe.entity.AiRecommendation;
import com.liuzhangjie.wardrobe.entity.AiRecommendationItem;
import com.liuzhangjie.wardrobe.entity.AiRecommendationVO;
import com.liuzhangjie.wardrobe.entity.AiToOutfitDTO;
import com.liuzhangjie.wardrobe.entity.AiToOutfitVO;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.ClothingTag;
import com.liuzhangjie.wardrobe.entity.Outfit;
import com.liuzhangjie.wardrobe.entity.OutfitItem;
import com.liuzhangjie.wardrobe.entity.User;
import com.liuzhangjie.wardrobe.mapper.AiConversationMapper;
import com.liuzhangjie.wardrobe.mapper.AiImageTaskMapper;
import com.liuzhangjie.wardrobe.mapper.AiMessageMapper;
import com.liuzhangjie.wardrobe.mapper.AiRecommendationItemMapper;
import com.liuzhangjie.wardrobe.mapper.AiRecommendationMapper;
import com.liuzhangjie.wardrobe.mapper.ClothingMapper;
import com.liuzhangjie.wardrobe.mapper.ClothingTagMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitItemMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitMapper;
import com.liuzhangjie.wardrobe.mapper.UserMapper;
import com.liuzhangjie.wardrobe.mq.AiImageMessage;
import com.liuzhangjie.wardrobe.mq.AiImageProducer;
import com.liuzhangjie.wardrobe.service.AiService;
import com.liuzhangjie.wardrobe.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * AI 穿搭模块的实现。
 * <p>
 * 这个类只做"编排"，不做"算法"：
 * <ul>
 *   <li>怎么从衣柜里挑衣服 → {@link OutfitAdvisorRouter}</li>
 *   <li>怎么把单品画成效果图 → {@code OutfitImageService}</li>
 *   <li>怎么异步跑 → {@link AiImageProducer} + 消费者</li>
 * </ul>
 * 分开的好处是：想换推荐模型只改 ai 包，想换生图服务只改 image 包，
 * 这个类里的业务流程完全不用动。
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    /** 会话标题取用户首句的前 32 个字，够列表展示又不至于把卡片撑开 */
    private static final int TITLE_MAX_LENGTH = 32;

    /**
     * 喂给模型的上文条数（一问一答算两条）。
     * <p>
     * 6 条 = 最近 3 轮。够听懂"那正式点的呢"这种省略句，又不至于把提示词撑到几千 token
     * （衣柜清单本身已经占了不少）。这是最朴素的「滑动窗口」：
     * <b>只控制喂给模型多少，库里一条都不删</b>——用户回看聊天记录时，
     * 几十轮之前的对话还在，这是"上下文"和"聊天记录"两件不同的事。
     */
    private static final int CONTEXT_WINDOW = 6;

    /** 历史效果图默认返回条数 */
    private static final int DEFAULT_HISTORY_SIZE = 24;

    /** 历史效果图最多返回条数。一张图 1.4MB，给太多前端会卡死 */
    private static final int MAX_HISTORY_SIZE = 60;

    /**
     * 输入框「+」最多能指定几件单品。
     * 再多就不是"指定"而是"整套都定死了"，那还不如直接去编辑搭配。
     */
    private static final int MAX_PINNED = 3;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiRecommendationMapper recommendationMapper;
    private final AiRecommendationItemMapper recommendationItemMapper;
    private final AiImageTaskMapper imageTaskMapper;
    private final ClothingMapper clothingMapper;
    private final ClothingTagMapper clothingTagMapper;
    private final OutfitMapper outfitMapper;
    private final OutfitItemMapper outfitItemMapper;
    private final OutfitAdvisorRouter advisorRouter;
    private final AiImageProducer imageProducer;
    private final UserMapper userMapper;

    public AiServiceImpl(AiConversationMapper conversationMapper,
                         AiMessageMapper messageMapper,
                         AiRecommendationMapper recommendationMapper,
                         AiRecommendationItemMapper recommendationItemMapper,
                         AiImageTaskMapper imageTaskMapper,
                         ClothingMapper clothingMapper,
                         ClothingTagMapper clothingTagMapper,
                         OutfitMapper outfitMapper,
                         OutfitItemMapper outfitItemMapper,
                         OutfitAdvisorRouter advisorRouter,
                         AiImageProducer imageProducer,
                         UserMapper userMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationItemMapper = recommendationItemMapper;
        this.imageTaskMapper = imageTaskMapper;
        this.clothingMapper = clothingMapper;
        this.clothingTagMapper = clothingTagMapper;
        this.outfitMapper = outfitMapper;
        this.outfitItemMapper = outfitItemMapper;
        this.advisorRouter = advisorRouter;
        this.imageProducer = imageProducer;
        this.userMapper = userMapper;
    }

    // ===================== 对话推荐 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatVO chat(AiChatDTO dto) {
        Long userId = UserContext.getUserId();

        if (dto == null || !StringUtils.hasText(dto.getMessage())) {
            throw new BusinessException(ResultCode.AI_MESSAGE_EMPTY);
        }
        String message = dto.getMessage().trim();

        // 1. 定位或新建会话。前端第一次进来带的是空 id，这里顺手建一个，
        //    省掉"先调创建会话接口"这一步，也避免用户点开页面什么都没说就先写一条空会话
        AiConversation conversation = resolveConversation(dto.getConversationId(), userId, message);

        // 2. 取衣柜（带标签）。引擎只能从这里挑，不能编造
        List<Clothing> wardrobe = loadWardrobe(userId);
        if (wardrobe.isEmpty()) {
            // 衣柜空着还硬算，只能产出一堆凭空编的衣服。直接给一条说明，不生成推荐
            saveMessage(conversation.getId(), AiMessage.ROLE_USER, message, null);
            saveMessage(conversation.getId(), AiMessage.ROLE_AI,
                    ResultCode.AI_WARDROBE_EMPTY.getMessage(), null);
            touchConversation(conversation);
            return new AiChatVO(conversation.getId(), new ArrayList<>());
        }

        // 3. 取最近几轮对话当上下文。
        //    必须放在下面写 ai_message 之前——否则当前这句也会被读进来，
        //    模型会看到两遍同一个问题，容易理解成"用户又问了一遍"
        List<AiMessage> recent = loadRecentMessages(conversation.getId(), CONTEXT_WINDOW);
        String historyText = toHistoryText(recent);
        List<String> historyLines = toHistoryLines(recent);

        // 4. 解析意图 → 交给引擎。
        //    解析时带上 historyText：这句话里没说温度/场合，就从上一轮继承。
        //    典型场景是"今天 15 度穿啥" → "那正式点的呢"，第二句自己读不出温度
        OutfitIntent intent = OutfitIntent.parse(message, historyText);

        // 4.5 输入框「+」点名的单品：先跟衣柜求交集，再把硬约束交给引擎
        List<Long> pinned = resolvePinned(dto.getClothingIds(), wardrobe);

        List<OutfitDraft> drafts = advisorRouter.advise(message, intent, wardrobe, historyLines, pinned);

        // 5. 落库：每套推荐一行主表 + 若干单品行
        List<AiRecommendationVO> vos = new ArrayList<>();
        Long firstRecommendationId = null;
        for (OutfitDraft draft : drafts) {
            AiRecommendation rec = persistDraft(userId, conversation.getId(), draft);
            if (firstRecommendationId == null) {
                firstRecommendationId = rec.getId();
            }
            vos.add(toRecommendationVO(rec, draft));
        }

        // 6. 记两条消息。AI 那条的文案跟着结果走，用户回看历史时能看懂当时为什么这么配
        saveMessage(conversation.getId(), AiMessage.ROLE_USER, message, null);
        String reply;
        if (vos.isEmpty()) {
            reply = "衣柜里的单品还凑不出一套完整搭配，再补充几件上衣或裤子试试。";
        } else {
            StringBuilder sb = new StringBuilder("为你推荐了 ").append(vos.size()).append(" 套搭配");
            if (!pinned.isEmpty()) {
                sb.append("，已按你指定的 ").append(pinned.size()).append(" 件单品来搭");
                // 指定是硬约束：钉住上装+下装后，只剩鞋和配饰能换，凑不满 3 套是正常的。
                // 不说这一句，用户会以为接口只返回了一套、是 bug
                if (vos.size() < 3) {
                    sb.append("。指定得越具体，能替换的位置就越少，多备几件同类单品就能搭出更多套");
                }
            }
            sb.append("（引擎：").append(advisorRouter.activeEngineName()).append("）");
            reply = sb.toString();
        }
        saveMessage(conversation.getId(), AiMessage.ROLE_AI, reply, firstRecommendationId);

        touchConversation(conversation);

        return new AiChatVO(conversation.getId(), vos);
    }

    @Override
    public Page<AiConversation> conversationPage(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        return conversationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdateTime));
    }

    @Override
    public Page<AiMessage> messages(Long conversationId, Integer page, Integer size) {
        Long userId = UserContext.getUserId();

        // 先确认会话是本人的，否则传一个别人的 id 就能读到别人的聊天记录
        AiConversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getId, conversationId)
                        .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            throw new BusinessException(ResultCode.AI_RECOMMENDATION_NOT_FOUND);
        }

        // 聊天记录要按时间正序读，这里用 orderByAsc 而不是 Desc，
        // 否则前端从第一页开始翻会先看到最后几句话，上下文是断的
        return messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getCreateTime)
                        .orderByAsc(AiMessage::getId));
    }

    // ===================== 上下文 =====================

    /**
     * 取最近 N 条消息，返回按时间正序。
     * <p>
     * 先按 id 倒序取最新的 N 条再反转。为什么不直接正序 + limit：
     * 那取到的是「最早的 N 条」，也就是用户第一天说的话，对当前这一轮毫无参考价值。
     */
    private List<AiMessage> loadRecentMessages(Long conversationId, int limit) {
        List<AiMessage> latest = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByDesc(AiMessage::getId)
                        .last("limit " + limit));
        Collections.reverse(latest);
        return latest;
    }

    /**
     * 拼成一段纯文本，给意图解析用。
     * 这里不区分谁说的：解析温度、场合时，是用户说的还是 AI 复述的不重要。
     */
    private String toHistoryText(List<AiMessage> recent) {
        if (recent.isEmpty()) {
            return null;
        }
        return recent.stream()
                .map(AiMessage::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
    }

    /** 拼成「用户：…」「AI：…」的行，给大模型当 prompt 用（它需要知道谁说的） */
    private List<String> toHistoryLines(List<AiMessage> recent) {
        List<String> lines = new ArrayList<>(recent.size());
        for (AiMessage m : recent) {
            if (!StringUtils.hasText(m.getContent())) {
                continue;
            }
            String who = m.getRole() != null && m.getRole() == AiMessage.ROLE_USER ? "用户" : "AI";
            lines.add(who + "：" + stripEngineSuffix(m.getContent()));
        }
        return lines;
    }

    /**
     * 把前端传来的单品 id 跟该用户的衣柜求交集，最多留 3 件。
     * <p>
     * 必须过滤：id 是前端传来的，传别人的衣物 id、或者传了一件已经被删的衣服，都很容易发生。
     * 不过滤就等于让模型去挑一件不属于他的衣服，落库后详情页会直接崩。
     * 这里选择<b>静默丢掉</b>非法项而不是报错——用户只是勾了几件衣服，
     * 不该因为一条脏 id 让整轮对话失败。
     */
    private List<Long> resolvePinned(List<Long> requested, List<Clothing> wardrobe) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<Long> owned = wardrobe.stream().map(Clothing::getId).collect(Collectors.toSet());
        return requested.stream()
                .filter(Objects::nonNull)
                .filter(owned::contains)
                .distinct()
                .limit(MAX_PINNED)
                .toList();
    }

    /**
     * 去掉 AI 回复里的「（引擎：deepseek:deepseek-chat）」。
     * 这句是写给用户看的，喂给模型纯属噪音，还可能诱导它模仿这种格式。
     */
    private String stripEngineSuffix(String content) {
        int idx = content.indexOf("（引擎：");
        return idx > 0 ? content.substring(0, idx) : content;
    }

    // ===================== 异步生图 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiImageTaskVO generateImage(AiImageRequestDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto == null || dto.getRecommendationId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        AiRecommendation rec = recommendationMapper.selectOne(
                new LambdaQueryWrapper<AiRecommendation>()
                        .eq(AiRecommendation::getId, dto.getRecommendationId())
                        .eq(AiRecommendation::getUserId, userId)
                        .eq(AiRecommendation::getDeleted, 0));
        if (rec == null) {
            throw new BusinessException(ResultCode.AI_RECOMMENDATION_NOT_FOUND);
        }

        // 已经在生成中就别重复投递，否则同一个推荐会排队画好几张，白烧额度
        if (AiRecommendation.IMAGE_RUNNING == rec.getImageStatus()) {
            throw new BusinessException(ResultCode.AI_TASK_RUNNING);
        }

        Long itemCount = recommendationItemMapper.selectCount(
                new LambdaQueryWrapper<AiRecommendationItem>()
                        .eq(AiRecommendationItem::getRecommendationId, rec.getId()));
        if (itemCount == null || itemCount == 0) {
            throw new BusinessException(ResultCode.AI_RECOMMENDATION_EMPTY);
        }

        // 模特用什么性别，在这里一次定好，随消息一起投递。
        // 放到消费端再算的话，消费者手上只有 userId，拿不到"用户这句话里说了什么"
        Integer gender = resolveModelGender(userId, rec.getConversationId());

        // 1. 建任务，状态直接置"生成中"。前端提交后立刻开始轮询，不需要再等一次状态翻转
        String taskId = nextTaskId();
        AiImageTask task = new AiImageTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setRecommendationId(rec.getId());
        task.setPrompt(buildPrompt(rec, gender));
        task.setStatus(AiImageTask.STATUS_RUNNING);
        task.setRetryCount(0);
        imageTaskMapper.insert(task);

        // 2. 推荐结果也标记为生成中，卡片上的骨架屏靠它
        rec.setImageStatus(AiRecommendation.IMAGE_RUNNING);
        recommendationMapper.updateById(rec);

        // 3. 丢进队列。注意这里不 try-catch：投递失败要让事务回滚，
        //    否则会留下一条"永远不会被处理"的任务记录，前端永远轮询不到结果
        imageProducer.send(new AiImageMessage(taskId, rec.getId(), userId, gender));

        return new AiImageTaskVO(taskId, AiImageTask.STATUS_RUNNING, null);
    }

    @Override
    public AiImageTaskVO imageStatus(String taskId) {
        Long userId = UserContext.getUserId();

        // 必须带 user_id：taskId 虽然不可猜，但能猜到的场景一律按越权处理
        AiImageTask task = imageTaskMapper.selectOne(
                new LambdaQueryWrapper<AiImageTask>()
                        .eq(AiImageTask::getTaskId, taskId)
                        .eq(AiImageTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(ResultCode.AI_TASK_NOT_FOUND);
        }
        return new AiImageTaskVO(task.getTaskId(), task.getStatus(), task.getImageUrl());
    }

    @Override
    public List<AiRecommendationVO> imageHistory(Integer limit) {
        Long userId = UserContext.getUserId();
        // 兜一下上限：前端就算传 1000 也不能真去查 1000 条，一张图 1.4MB，抽屉会直接卡死
        int size = (limit == null || limit <= 0)
                ? DEFAULT_HISTORY_SIZE
                : Math.min(limit, MAX_HISTORY_SIZE);

        List<AiRecommendation> recs = recommendationMapper.selectList(
                new LambdaQueryWrapper<AiRecommendation>()
                        .eq(AiRecommendation::getUserId, userId)
                        // 只要真的出过图的。生成中(1)和失败(3)的没有地址，放进去是一格空白
                        .eq(AiRecommendation::getImageStatus, AiRecommendation.IMAGE_SUCCESS)
                        // 用户在「我的搭配」里删掉之后，这里会被标脏，不该再出现在历史里
                        .eq(AiRecommendation::getDeleted, 0)
                        .isNotNull(AiRecommendation::getImageUrl)
                        // 按更新时间倒序 = 最近生成的排最前
                        .orderByDesc(AiRecommendation::getUpdateTime)
                        .last("limit " + size));
        if (recs.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次把所有单品行查出来再按推荐分组，避免每条推荐都查一次（N+1）
        List<Long> ids = recs.stream().map(AiRecommendation::getId).toList();
        Map<Long, List<AiRecommendationItem>> grouped = recommendationItemMapper.selectList(
                        new LambdaQueryWrapper<AiRecommendationItem>()
                                .in(AiRecommendationItem::getRecommendationId, ids)
                                .orderByAsc(AiRecommendationItem::getSort))
                .stream()
                .collect(Collectors.groupingBy(AiRecommendationItem::getRecommendationId));

        List<AiRecommendationVO> vos = new ArrayList<>(recs.size());
        for (AiRecommendation rec : recs) {
            AiRecommendationVO vo = new AiRecommendationVO();
            vo.setId(rec.getId());
            vo.setName(rec.getName());
            // reason / colorNote 是详情弹窗要显示的"为什么这么配"，顺手一起返回，
            // 免得前端为了看详情再单独请求一次
            vo.setReason(rec.getReason());
            vo.setColorNote(rec.getColorNote());
            vo.setScene(rec.getScene());
            vo.setSeason(rec.getSeason());
            vo.setImageStatus(rec.getImageStatus());
            vo.setImageUrl(rec.getImageUrl());
            vo.setSaved(rec.getSaved() != null && rec.getSaved() == 1);
            // items 只带 id 和位置，前端在进入页面时已经拉过整份衣柜，靠 id 就能还原缩略图和名字
            vo.setItems(grouped.getOrDefault(rec.getId(), List.of()).stream()
                    .map(i -> new AiRecommendationVO.ItemVO(i.getClothingId(), i.getRole()))
                    .toList());
            vos.add(vo);
        }
        return vos;
    }

    // ===================== 推荐沉淀为搭配 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiToOutfitVO toOutfit(Long recommendationId, AiToOutfitDTO dto) {
        Long userId = UserContext.getUserId();
        AiToOutfitDTO body = dto == null ? new AiToOutfitDTO() : dto;

        AiRecommendation rec = recommendationMapper.selectOne(
                new LambdaQueryWrapper<AiRecommendation>()
                        .eq(AiRecommendation::getId, recommendationId)
                        .eq(AiRecommendation::getUserId, userId)
                        .eq(AiRecommendation::getDeleted, 0));
        if (rec == null) {
            throw new BusinessException(ResultCode.AI_RECOMMENDATION_NOT_FOUND);
        }

        // 幂等第一层：已经放过且那条搭配还在，直接返回原 id
        if (rec.getOutfitId() != null) {
            Outfit existing = outfitMapper.selectById(rec.getOutfitId());
            if (existing != null) {
                return new AiToOutfitVO(existing.getId(), true);
            }
            // 搭配被用户删了（逻辑删除后 selectById 查不到），把推荐重置回"未放入"
            log.info("推荐引用的搭配已被删除，重置状态，recommendationId={}", recommendationId);
            rec.setOutfitId(null);
            rec.setSaved(0);
        }

        List<AiRecommendationItem> items = recommendationItemMapper.selectList(
                new LambdaQueryWrapper<AiRecommendationItem>()
                        .eq(AiRecommendationItem::getRecommendationId, rec.getId())
                        .orderByAsc(AiRecommendationItem::getRole));
        if (items.isEmpty()) {
            throw new BusinessException(ResultCode.AI_RECOMMENDATION_EMPTY);
        }

        // 建正式搭配。source 硬编码 2（AI 推荐），不允许前端指定
        Outfit outfit = new Outfit();
        outfit.setUserId(userId);
        outfit.setName(firstNonBlank(body.getName(), rec.getName(), "AI 推荐搭配"));
        outfit.setScene(firstNonBlank(body.getScene(), rec.getScene(), "日常"));
        outfit.setSeason(body.getSeason() != null ? body.getSeason()
                : (rec.getSeason() != null ? rec.getSeason() : 15));
        outfit.setDescription(firstNonBlank(body.getDescription(), rec.getReason(), null));
        outfit.setCoverUrl(resolveCover(body.getCoverUrl(), rec));
        outfit.setSource(2);
        outfit.setLikeFlag(0);
        outfitMapper.insert(outfit);

        // 搬单品。role 在 ai_recommendation_item 上已有唯一约束，这里不会撞 uk_outfit_role
        for (AiRecommendationItem src : items) {
            OutfitItem item = new OutfitItem();
            item.setOutfitId(outfit.getId());
            item.setClothingId(src.getClothingId());
            item.setRole(src.getRole());
            item.setSort(src.getSort() == null ? 0 : src.getSort());
            outfitItemMapper.insert(item);
        }

        // 记下转正后的 id，下次重复调用就走上面的幂等分支
        rec.setOutfitId(outfit.getId());
        rec.setSaved(1);
        recommendationMapper.updateById(rec);

        log.info("推荐已放入我的搭配，recommendationId={}，outfitId={}", recommendationId, outfit.getId());
        return new AiToOutfitVO(outfit.getId(), true);
    }

    // ===================== 内部方法 =====================

    /**
     * 定位会话：既能复用前端传来的 id，也能在 id 为空/无效时新建。
     * <p>
     * 为什么要校验归属：前端目前把 conversationId 写死成 99，如果真有人传了别人的会话 id，
     * 不校验的话这轮对话就会写进别人的会话里。
     */
    private AiConversation resolveConversation(Long conversationId, Long userId, String message) {
        if (conversationId != null) {
            AiConversation existing = conversationMapper.selectOne(
                    new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getId, conversationId)
                            .eq(AiConversation::getUserId, userId));
            if (existing != null) {
                return existing;
            }
            log.debug("会话 {} 不存在或不属于当前用户，将为 userId={} 新建会话", conversationId, userId);
        }

        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(truncateTitle(message));
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 把衣柜连同标签一次性查出来。
     * <p>
     * 两次 SQL 而不是 N+1：先查全部衣物，再按 clothing_id in (...) 查一次标签，
     * 在内存里按衣物分组挂回去。衣柜再大也就两次往返。
     */
    private List<Clothing> loadWardrobe(Long userId) {
        List<Clothing> wardrobe = clothingMapper.selectList(
                new LambdaQueryWrapper<Clothing>()
                        .eq(Clothing::getUserId, userId)
                        .orderByDesc(Clothing::getCreateTime));
        if (wardrobe.isEmpty()) {
            return wardrobe;
        }

        List<Long> ids = wardrobe.stream().map(Clothing::getId).toList();
        List<ClothingTag> tags = clothingTagMapper.selectList(
                new LambdaQueryWrapper<ClothingTag>().in(ClothingTag::getClothingId, ids));
        Map<Long, List<ClothingTag>> grouped = tags.stream()
                .collect(Collectors.groupingBy(ClothingTag::getClothingId));

        for (Clothing c : wardrobe) {
            c.setTags(grouped.getOrDefault(c.getId(), new ArrayList<>()));
        }
        return wardrobe;
    }

    /** 一套草稿落库：主表插一行拿到自增 id，再把单品挂上去 */
    private AiRecommendation persistDraft(Long userId, Long conversationId, OutfitDraft draft) {
        AiRecommendation rec = new AiRecommendation();
        rec.setUserId(userId);
        rec.setConversationId(conversationId);
        rec.setName(draft.name());
        rec.setReason(draft.reason());
        rec.setColorNote(draft.colorNote());
        rec.setScene(draft.scene());
        rec.setSeason(draft.season() == null ? 15 : draft.season());
        rec.setImageStatus(AiRecommendation.IMAGE_NONE);
        rec.setSaved(0);
        rec.setDeleted(0);
        recommendationMapper.insert(rec);

        int sort = 0;
        for (OutfitDraft.Item item : draft.items()) {
            AiRecommendationItem entity = new AiRecommendationItem();
            entity.setRecommendationId(rec.getId());
            entity.setClothingId(item.clothingId());
            entity.setRole(item.role());
            entity.setSort(sort++);
            recommendationItemMapper.insert(entity);
        }
        return rec;
    }

    /** 草稿 → 前端卡片结构。items 直接从草稿搬，不用再查一次库 */
    private AiRecommendationVO toRecommendationVO(AiRecommendation rec, OutfitDraft draft) {
        AiRecommendationVO vo = new AiRecommendationVO();
        vo.setId(rec.getId());
        vo.setName(rec.getName());
        vo.setReason(rec.getReason());
        vo.setColorNote(rec.getColorNote());
        vo.setScene(rec.getScene());
        vo.setSeason(rec.getSeason());
        vo.setSaved(false);
        vo.setImageStatus(rec.getImageStatus());
        vo.setImageUrl(rec.getImageUrl());
        vo.setItems(draft.items().stream()
                .map(i -> new AiRecommendationVO.ItemVO(i.clothingId(), i.role()))
                .toList());
        return vo;
    }

    private void saveMessage(Long conversationId, int role, String content, Long recommendationId) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setRecommendationId(recommendationId);
        messageMapper.insert(message);
    }

    /** 刷新会话活跃时间，会话列表按它倒序 */
    private void touchConversation(AiConversation conversation) {
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    /**
     * 生成对外任务号：{@code task_毫秒时间戳_四位随机}。
     * <p>
     * 不用自增主键做任务号，是因为自增 id 连续可枚举，
     * 别人从 100 改成 101 就能看到另一个用户的任务状态。
     */
    private String nextTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    /** 提示词落库，方便事后复现"当时到底让模型画了什么" */
    private String buildPrompt(AiRecommendation rec, Integer gender) {
        return "搭配：" + rec.getName()
                + "；场景：" + rec.getScene()
                + "；配色：" + rec.getColorNote()
                // 顺手记进 ai_image_task.prompt：以后排查"这张图怎么画成男的了"，
                // 看这一行就知道当时判定的是什么性别，不用再猜
                + "；模特性别：" + genderText(gender);
    }

    // ===================== 模特性别 =====================

    /** user.gender 的取值：0 未设置 / 1 男 / 2 女 */
    private static final int GENDER_MALE = 1;
    private static final int GENDER_FEMALE = 2;

    /**
     * 决定这张图里的模特用什么性别。
     * <p>
     * 优先级：<b>这轮对话里用户自己说过的 &gt; 个人资料里的</b>。
     * 为什么不直接读资料：用户在资料里填了"男"，但这轮明确说"给我搭一套女生穿的"，
     * 这时候得听当场的；反过来，什么都没说就沿用资料里的，不用让用户每句话都重复一遍。
     * 两个都没有（资料里是"未设置"）时返回 null，提示词写"一位真人模特"，交给模型自己定。
     */
    private Integer resolveModelGender(Long userId, Long conversationId) {
        Integer fromChat = matchGenderFromConversation(conversationId);
        if (fromChat != null) {
            log.debug("模特性别取自对话：{}", fromChat);
            return fromChat;
        }
        User user = userMapper.selectById(userId);
        if (user != null && user.getGender() != null && user.getGender() > 0) {
            log.debug("模特性别取自个人资料：{}", user.getGender());
            return user.getGender();
        }
        return null;
    }

    /**
     * 回看这个会话里用户自己说过的话，找有没有明确提到性别。
     * <p>
     * 只看 {@code role=1}（用户），不看 AI 的回复——AI 那条文案本来就可能是"为你推荐女士通勤搭配"，
     * 拿它判断等于自己给自己下结论。按 id 倒序取，最新的说法优先（用户中途可能改主意）。
     */
    private Integer matchGenderFromConversation(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        List<AiMessage> recent = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .eq(AiMessage::getRole, AiMessage.ROLE_USER)
                        .orderByDesc(AiMessage::getId)
                        .last("limit 10"));
        for (AiMessage m : recent) {
            Integer gender = matchGender(m.getContent());
            if (gender != null) {
                return gender;
            }
        }
        return null;
    }

    /** 从一句话里判断性别，认不出来返回 null */
    private Integer matchGender(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        boolean male = text.contains("男") || text.contains("帅哥")
                || text.contains("小哥哥") || text.contains("汉子");
        boolean female = text.contains("女") || text.contains("妹子")
                || text.contains("小姐姐") || text.contains("姑娘");
        if (male == female) {
            // 都没提，或者两个都提了（"男女同款"），都属于"判断不了"，不硬猜
            return null;
        }
        return male ? GENDER_MALE : GENDER_FEMALE;
    }

    private String genderText(Integer gender) {
        if (gender == null) {
            return "未指定";
        }
        return gender == GENDER_MALE ? "男" : "女";
    }

    /**
     * 封面取值：显式传了就用传的（空串表示明确不要封面），
     * 没传则回落到推荐的效果图。
     */
    private String resolveCover(String passedCover, AiRecommendation rec) {
        if (passedCover != null) {
            return passedCover.isBlank() ? null : passedCover;
        }
        return rec.getImageUrl();
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String truncateTitle(String message) {
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() <= TITLE_MAX_LENGTH ? flat : flat.substring(0, TITLE_MAX_LENGTH);
    }

}
