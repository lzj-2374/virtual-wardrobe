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
 * 搭配表。
 * <p>
 * coverUrl 的取值优先级：用户上传 &gt; AI 效果图 &gt; 空（列表用占位图）。
 * 所以"放入到我的搭配"这个动作，如果原搭配没封面，顺手把 AI 效果图写进 coverUrl。
 */
@Data
@TableName("outfit")
public class Outfit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /** 封面图 */
    private String coverUrl;

    /** 1 手动创建 / 2 AI 推荐沉淀 */
    private Integer source;

    /** 场景：日常 / 通勤 / 约会 / 运动 / 正式 */
    private String scene;

    /** 季节位标识，含义同 {@link Clothing#getSeason()} */
    private Integer season;

    /** 说明或推荐理由 */
    private String description;

    /** 是否收藏：0 否 / 1 是。字段名不能叫 like，那是 Java 关键字 */
    private Integer likeFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /**
     * 这套搭配包含的单品，来自 outfit_item 表，不是 outfit 的列。
     * exist = false 告诉 MyBatis-Plus：查表和插入时都别管这个字段，
     * 它只用于把关联数据捎带返回给前端（跟 Clothing.tags 同一个路子）。
     */
    @TableField(exist = false)
    private List<OutfitItem> items;

}
