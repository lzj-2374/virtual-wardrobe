package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 新增 / 修改衣物的入参。
 * <p>
 * 字段是从前端 ClothingAdd.vue 提交的 payload 一个个对过来的，多一个都没有。
 * <p>
 * <b>故意不放的字段：</b>
 * <ul>
 *   <li>{@code id} —— 数据库自增，新增时由 MySQL 生成</li>
 *   <li>{@code userId} —— 从 token 里取，让前端传就等于允许替别人建衣服</li>
 *   <li>{@code status} —— 业务默认值，服务端定</li>
 *   <li>{@code createTime / updateTime} —— 数据库自动填</li>
 *   <li>{@code deleted} —— 逻辑删除标记，不该由外部控制</li>
 * </ul>
 * 这几个字段只要出现在 DTO 里，前端就能往请求体里塞值改掉它们，属于典型的批量赋值漏洞。
 */
@Data
public class ClothingDTO {

    private String name;

    /** 一级品类：1 上衣 / 2 裤子 / 3 外套 / 4 套装 / 5 鞋包配饰 */
    private Integer category;

    /** 子品类，如 T恤 / 牛仔裤 */
    private String subCategory;

    private String color;

    /** 季节位标识，前端已把多选加成一个数：1 春 / 2 夏 / 4 秋 / 8 冬，15 = 四季 */
    private Integer season;

    /** 厚薄：1 薄 / 2 适中 / 3 厚 */
    private Integer thickness;

    /** 主图地址，当前是本地预览地址，第 5 步接 OSS 后换成 OSS 返回的 URL */
    private String imageUrl;

    /**
     * 风格 / 场合标签，每个元素会被拆成 clothing_tag 表的一行。
     * <p>
     * 默认给个空 List 而不是 null，前端不传 tags 时遍历不会空指针。
     * 但注意：如果前端明确传了 {@code "tags": null}，Jackson 仍会覆盖成 null，
     * 所以实现类里遍历前保险起见再判一次。
     */
    private List<ClothingTag> tags = new ArrayList<>();

}
