package com.liuzhangjie.wardrobe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 个人中心"我的数据"那两个方块。
 * <p>
 * 字段名必须和前端 Profile.vue 里的 ov.totalClothing / ov.totalOutfit 对上，
 * 对不上就是 undefined，页面上会显示成空白。
 */
@Data
@NoArgsConstructor
public class OverviewVO {

    /** 当前用户的衣物总数 */
    private Long totalClothing;

    /** 当前用户的搭配总数 */
    private Long totalOutfit;

    private Long categoryCount;
    private Long styleCount;
    private List<AdminOverviewVO.NameValueVO> byCategory;
    private List<AdminOverviewVO.NameValueVO> byStyle;
    private List<Clothing> recent;

}
