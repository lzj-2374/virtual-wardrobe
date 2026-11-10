package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搭配-衣物关联表。
 * <p>
 * 这张表<b>必须物理删除</b>，不能加 {@code @TableLogic}：
 * 表上有唯一索引 uk_outfit_role (outfit_id, role)，一个搭配里一个角色只能有一件单品。
 * 如果做成逻辑删除，"删掉旧上装换一件新的"时旧记录还占着那个 (outfit_id, 1) 的组合，
 * 插入新记录会直接撞唯一索引报 Duplicate entry。
 */
@Data
@TableName("outfit_item")
public class OutfitItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long outfitId;

    private Long clothingId;

    /** 1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰 */
    private Integer role;

    private Integer sort;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private Clothing clothing;

}
