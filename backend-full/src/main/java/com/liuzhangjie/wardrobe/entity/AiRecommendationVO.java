package com.liuzhangjie.wardrobe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐卡片的返回结构，对应 `/ai/chat` 里 {@code recommendations[]} 的每一项。
 * <p>
 * 字段是照着前端 AiAssistant.vue 的模板一个个对出来的，多一个少一个都会让卡片渲染出问题：
 * <ul>
 *   <li>{@code rec.name} → 卡片标题</li>
 *   <li>{@code rec.reason} / {@code rec.colorNote} → 两行说明文字</li>
 *   <li>{@code rec.items} → 底部一排单品缩略图，靠 clothingId 回衣柜里查图片和名称</li>
 *   <li>{@code rec.imageStatus} → 0/2/3 决定显示什么，"生成效果图"按钮的 loading 也看它</li>
 *   <li>{@code rec.saved} → 控制「放入我的搭配」按钮是否置灰</li>
 * </ul>
 */
@Data
public class AiRecommendationVO {

    /** 推荐结果 id，前端调用生图接口和「放入我的搭配」时都要带回来 */
    private Long id;

    private String name;

    private String reason;

    private String colorNote;

    /** 场景，前端「放入我的搭配」时作为默认值提交 */
    private String scene;

    /** 季节位标识，同上 */
    private Integer season;

    /** 是否已放入我的搭配 */
    private Boolean saved;

    /** 0 未生成 / 1 生成中 / 2 成功 / 3 失败 */
    private Integer imageStatus;

    /** 效果图地址，imageStatus=2 时才有值 */
    private String imageUrl;

    /** 这套搭配用到的单品 */
    private List<ItemVO> items = new ArrayList<>();

    /**
     * 单品项。只给前端三样东西：id、位置、以及（前端自己查出来的）衣物信息。
     * <p>
     * 不直接把整个 Clothing 对象塞进来：推荐卡片只需要一张缩略图和名字，
     * 前端在进入页面时已经用 {@code pageClothing} 把全部衣物拉过一次，
     * 后端再重复传一遍等于把同一份数据在报文里放大好几倍。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemVO {

        private Long clothingId;

        /** 1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰 */
        private Integer role;

    }

}
