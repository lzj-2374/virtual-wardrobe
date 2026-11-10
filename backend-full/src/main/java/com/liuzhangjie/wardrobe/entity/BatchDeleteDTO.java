package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

import java.util.List;

/**
 * 批量删除的入参。
 * <p>
 * 前端 api/clothing.js 发的是 {@code { "ids": [3, 5, 7] }}，是一个<b>对象</b>。
 * 所以 Controller 不能写 {@code @RequestBody List<Long> ids}，
 * 那期望的是裸数组 {@code [3,5,7]}，Jackson 反序列化会直接失败。
 */
@Data
public class BatchDeleteDTO {

    private List<Long> ids;

}
