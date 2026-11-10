package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 衣物表。
 * <p>
 * season 是"位标识"而不是普通枚举：1 春 / 2 夏 / 4 秋 / 8 冬，相加即组合，15 = 四季。
 * 判断"属于某个季节"的 SQL 是 {@code season & ? != 0}，这样一条记录能覆盖多选，
 * 不用再开一张 clothing_season 关联表。
 * <p>
 * 风格、场合这类多值属性不放这里，走 {@link ClothingTag}。
 */
@Data
@TableName("clothing")
public class Clothing {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 id，所有查询都要带上它做数据隔离 */
    private Long userId;

    private String name;

    /** 一级品类：1 上衣 / 2 裤子 / 3 外套 / 4 套装 / 5 鞋包配饰 */
    private Integer category;

    /** 子品类，如 T恤 / 牛仔裤 */
    private String subCategory;

    /** 主图地址（OSS 或本地） */
    private String imageUrl;

    /** 主色，如 白 / 黑 / 蓝 */
    private String color;

    /** 季节位标识：1 春 / 2 夏 / 4 秋 / 8 冬，15 = 四季 */
    private Integer season;

    /** 厚薄：1 薄 / 2 适中 / 3 厚 */
    private Integer thickness;

    /** 1 正常 / 0 已归档，和 user 表的 status 保持同一套语义 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /**
     * 风格 / 场合标签，不来自 clothing 表，而是按 clothing_id 去 clothing_tag 查出来的。
     * <p>
     * {@code @TableField(exist = false)} 告诉 MyBatis-Plus"这不是数据库列"，
     * 所有自动生成的 SQL 都会跳过它；但 Jackson 序列化时照常输出，
     * 所以前端最终拿到的 JSON 里能看到 tags 数组。
     */
    @TableField(exist = false)
    private List<ClothingTag> tags;

}
