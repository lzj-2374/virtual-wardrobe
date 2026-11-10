package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 推荐结果表。
 * <p>
 * 一轮对话可能出 1~3 套搭配，每一套在这里是一行。
 * 关键字段是 {@link #saved} 和 {@link #outfitId}：
 * <ul>
 *   <li>{@code saved=0}：还只是推荐卡片，没进「我的搭配」</li>
 *   <li>{@code saved=1}：用户点过「放入我的搭配」，{@code outfitId} 指向真正那条 outfit</li>
 * </ul>
 * 这两个字段一起构成「放入我的搭配」的幂等依据——重复点只会返回同一个 outfitId，
 * 不会在搭配列表里生成一堆重复项。
 * <p>
 * {@code deleted} 是这张表唯一带逻辑删除语义的字段，用途单一：
 * 用户在「我的搭配」里把那条 AI 来源的搭配删了之后，把这里标脏、saved 归零，
 * 让推荐卡片的按钮回到「放入我的搭配」，可以重新放一次。
 */
@Data
@TableName("ai_recommendation")
public class AiRecommendation {

    /** 生图状态：未生成 */
    public static final int IMAGE_NONE = 0;

    /** 生图状态：生成中 */
    public static final int IMAGE_RUNNING = 1;

    /** 生图状态：成功 */
    public static final int IMAGE_SUCCESS = 2;

    /** 生图状态：失败 */
    public static final int IMAGE_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long conversationId;

    /** 已转正的搭配 id；为空表示用户还没点「放入我的搭配」 */
    private Long outfitId;

    /** 推荐名称，如「为你搭配：面试通勤」 */
    private String name;

    /** 推荐理由，由引擎生成，如实说明为什么这么配 */
    private String reason;

    /** 配色说明，如「主色：白 / 点缀：米」 */
    private String colorNote;

    /** 场景：日常 / 通勤 / 约会 / 运动 / 正式 */
    private String scene;

    /** 季节位标识：1 春 / 2 夏 / 4 秋 / 8 冬，15 = 四季 */
    private Integer season;

    /** 效果图地址，生图成功后回写 */
    private String imageUrl;

    /** 0 未生成 / 1 生成中 / 2 成功 / 3 失败 */
    private Integer imageStatus;

    /** 0 未放入 / 1 已放入我的搭配 */
    private Integer saved;

    /** 0 正常 / 1 已失效（对应搭配被删） */
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 这套推荐用到的单品，来自 ai_recommendation_item 表。
     * <p>
     * {@code @TableField(exist = false)}：不是本表的列，MyBatis-Plus 生成 SQL 时会跳过，
     * 但 Jackson 序列化照常输出，前端能在推荐卡片里直接拿到 items 数组。
     */
    @TableField(exist = false)
    private List<AiRecommendationItem> items;

}
