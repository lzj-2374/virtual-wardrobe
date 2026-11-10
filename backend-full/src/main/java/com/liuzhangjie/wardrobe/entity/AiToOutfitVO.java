package com.liuzhangjie.wardrobe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「放入我的搭配」的返回体。
 * <p>
 * {@code saved} 恒为 true，留着是为了和 {@code /ai/chat} 里推荐项的 saved 字段对上，
 * 前端可以直接用同一个字段名更新卡片状态，不用记两套。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiToOutfitVO {

    /** 转正后的搭配 id。重复调用返回的永远是同一个值（幂等） */
    private Long outfitId;

    /** 是否已放入，恒 true */
    private Boolean saved;

}
