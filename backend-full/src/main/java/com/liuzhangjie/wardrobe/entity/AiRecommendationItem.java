package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 推荐单品表：一套推荐里用到的每一件衣服。
 * <p>
 * 为什么不用「推荐时先建一条 outfit 草稿、把单品挂在 outfit_item 上」的做法：
 * 那样草稿会立刻出现在 {@code GET /outfit/page} 里，用户还没点「放入我的搭配」，
 * 搭配列表就多了几套没名字的东西。要么再加一个 draft 标志位去过滤，
 * 要么像这里一样把中间态放在 AI 自己的表里，主体表保持干净。
 * <p>
 * {@code uk_rec_role} 唯一索引保证一套推荐里同一个位置只有一件单品，
 * 和 outfit_item 的约束一致，搬运时不会突然撞索引。
 */
@Data
@TableName("ai_recommendation_item")
public class AiRecommendationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recommendationId;

    /** 衣物 id。必须是该用户真实存在的衣物，引擎编造的 id 会在落库前被过滤掉 */
    private Long clothingId;

    /** 1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰 */
    private Integer role;

    private Integer sort;

    private LocalDateTime createTime;

}
