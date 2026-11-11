package com.liuzhangjie.wardrobe.ai.image;

import com.liuzhangjie.wardrobe.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 效果图生成的总入口：先合成，能接模型就再接一步换成上身图。
 * <p>
 * 三步固定流程：
 * <ol>
 *   <li>用真实单品图拼一张平铺图（{@link OutfitImageComposer}）</li>
 *   <li>平铺图传 OSS 拿到公网地址</li>
 *   <li>配了图像模型就把这张地址交给万相做指令编辑，换成模特上身图；
 *       任何失败都保留第 2 步的平铺图，不让用户空手而归</li>
 * </ol>
 * 为什么"先合成再编辑"而不是直接文生图：直接文生图画出来的衣服跟用户衣柜毫无关系，
 * 那就不是"虚拟衣柜"了，是个随机换装玩具。以真实单品图为底，是这项目唯一的差异化。
 */
@Slf4j
@Service
public class OutfitImageService {

    private final OutfitImageComposer composer;
    private final OssImageStorage storage;
    private final DashScopeImageEditor editor;
    private final AiProperties aiProperties;

    public OutfitImageService(OutfitImageComposer composer,
                              OssImageStorage storage,
                              DashScopeImageEditor editor,
                              AiProperties aiProperties) {
        this.composer = composer;
        this.storage = storage;
        this.editor = editor;
        this.aiProperties = aiProperties;
    }

    /**
     * @param gender 模特性别：1 男 / 2 女 / null 未指定。
     *               只在接万相那一步用得上——纯合成图里没有模特，只有平铺的衣服
     * @return 效果图的公网地址。这个方法要么返回一个可访问的 URL，要么抛异常，
     * 不会返回 null —— 状态机那边只要拿到 URL 就直接置成功
     */
    public String generate(String outfitName, String colorNote, String scene,
                           String seasonText, List<OutfitImageComposer.RenderItem> items,
                           Integer gender) {

        byte[] flatLay = composer.compose(outfitName, colorNote, scene, seasonText, items);
        String flatLayUrl = storage.upload(flatLay, "png");

        if (!shouldCallModel()) {
            log.debug("未配置图像模型 key 或指定了 composite 模式，直接返回合成图：{}", flatLayUrl);
            return flatLayUrl;
        }

        try {
            byte[] edited = editor.edit(flatLayUrl, outfitName + "，" + scene, gender, describePieces(items));
            String finalUrl = storage.upload(edited, "png");
            log.info("万相生图成功，结果：{}", finalUrl);
            return finalUrl;
        } catch (Exception e) {
            // 降级日志分级别：强制模式说明配置有问题，用 ERROR；auto 模式属于可接受，用 WARN
            if ("wanx".equalsIgnoreCase(aiProperties.getImage().getProvider())) {
                log.error("万相生图失败（provider=wanx 强制模式），已降级为合成图", e);
            } else {
                log.warn("万相生图失败，已降级为合成图：{}", e.getMessage());
            }
            return flatLayUrl;
        }
    }

    /**
     * 这套搭配实际包含哪些位置，拼成「上装、下装」这种给模型看的清单。
     * <p>
     * 必须按真实单品算，不能写死。写死「上衣、下装、外套、鞋」的话，
     * 一套只有 T恤+短裤的搭配传进去，模型会照着清单自作主张再添一件外套——
     * 效果图里出现一件衣柜里根本没有的衣服，等于把"基于真实衣物"这个卖点废掉。
     * <p>
     * 用 LinkedHashSet 去重并按加入顺序输出：同一个位置在一套里只会有一件
     * （{@code uk_rec_role} 兜着），顺序就是上装→下装→外套→鞋→配饰，读起来自然。
     */
    private String describePieces(List<OutfitImageComposer.RenderItem> items) {
        if (items == null || items.isEmpty()) {
            return "单品";
        }
        Set<String> labels = new LinkedHashSet<>();
        for (OutfitImageComposer.RenderItem item : items) {
            if (item.getRoleLabel() != null && !item.getRoleLabel().isBlank()) {
                labels.add(item.getRoleLabel());
            }
        }
        return labels.isEmpty() ? "单品" : String.join("、", labels);
    }

    private boolean shouldCallModel() {        String provider = aiProperties.getImage().getProvider();
        if ("composite".equalsIgnoreCase(provider)) {
            return false;
        }
        if ("wanx".equalsIgnoreCase(provider)) {
            return true;
        }
        return aiProperties.isImageReady();
    }

}
