package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品类字典表。parent_id = 0 表示一级品类。
 */
@Data
@TableName("clothing_category")
public class ClothingCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父品类 id，0 表示一级 */
    private Long parentId;

    private String name;

    /** 1 一级 / 2 二级 */
    private Integer level;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
