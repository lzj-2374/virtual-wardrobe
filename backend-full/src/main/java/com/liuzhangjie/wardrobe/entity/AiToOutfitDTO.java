package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

/**
 * 「放入我的搭配」的入参，对应 {@code POST /api/ai/recommendation/{id}/to-outfit}。
 * <p>
 * 所有字段都可选：不传就用推荐结果里已经存好的默认值（名称、场景、季节、理由）。
 * 前端目前是把推荐卡片上展示的内容原样传回来，等于"用户看到的什么就存什么"，
 * 允许改是因为用户可能想换个名字再存。
 * <p>
 * <b>故意不放</b> {@code items}：单品由服务端按推荐记录还原。
 * 如果允许前端传，就等于开了个口子，能借「放入搭配」把任意衣物 id 塞进别人的搭配。
 */
@Data
public class AiToOutfitDTO {

    private String name;

    private String scene;

    private Integer season;

    private String description;

    /**
     * 封面图地址。一般传效果图 URL。
     * <p>
     * 传空字符串 {@code ""} 表示明确不要封面；不传（null）则走服务端兜底：
     * 推荐结果里有效果图就用它，没有就留空让列表显示占位图。
     */
    private String coverUrl;

}
