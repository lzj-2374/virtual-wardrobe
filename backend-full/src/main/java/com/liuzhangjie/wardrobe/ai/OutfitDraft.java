package com.liuzhangjie.wardrobe.ai;

import java.util.List;

/**
 * 「一套搭配草稿」——引擎产出的中间结构，还没落库。
 * <p>
 * 用 record 而不是 @Data 类：这东西从头到尾只在方法之间传递，不该被外部改。
 * 用可变对象的话，解析大模型返回时很容易在某个分支里把 items 改成 null，
 * 后面落库时才 NPE，排查要跨好几个类。
 * <p>
 * 字段和前端推荐卡片一一对应，落库时直接映射到 {@code ai_recommendation} + {@code ai_recommendation_item}。
 *
 * @param name      推荐名称，如「为你搭配：面试通勤」
 * @param reason    推荐理由，要说出"为什么这么配"，不能是空话
 * @param colorNote 配色说明，如「主色：白 / 点缀：米」
 * @param scene     场景：日常 / 通勤 / 约会 / 运动 / 正式
 * @param season    季节位标识：1 春 / 2 夏 / 4 秋 / 8 冬，15 = 四季
 * @param items     单品列表，clothingId 必须是该用户真实拥有的衣物
 */
public record OutfitDraft(
        String name,
        String reason,
        String colorNote,
        String scene,
        Integer season,
        List<Item> items
) {

    /**
     * 一件单品在搭配里的位置。
     *
     * @param clothingId 衣物 id
     * @param role       1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰
     */
    public record Item(Long clothingId, Integer role) {
    }

    /** 这套搭配里有没有上装或套装——没有的话根本不成一套，落库前会被丢掉 */
    public boolean hasTop() {
        return items != null && items.stream().anyMatch(i -> i.role() != null && i.role() == 1);
    }

}
