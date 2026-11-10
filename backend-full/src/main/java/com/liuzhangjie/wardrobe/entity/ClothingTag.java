package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 衣物标签关联表（存风格 / 场合这类多值属性）。
 * <p>
 * 注意这张表<b>没有</b> deleted 字段，也<b>不要</b>加 {@code @TableLogic}。
 * 编辑衣物时走"先按 clothingId 全删、再整批插入"，物理删除。
 * 如果做成逻辑删除，被删的旧标签还留在表里，下次查询会捞出来重复的。
 */
@Data
@TableName("clothing_tag")
public class ClothingTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clothingId;

    /** 1 风格 / 2 场合 / 3 其他 */
    private Integer tagType;

    /** 标签值，如 休闲 / 通勤 */
    private String tagValue;

    private LocalDateTime createTime;

}
