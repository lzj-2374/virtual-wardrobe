package com.liuzhangjie.wardrobe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生图任务的返回体，两个接口共用：
 * <ul>
 *   <li>{@code POST /api/ai/image/generate} —— 只用 {@code taskId}，status 恒为 1</li>
 *   <li>{@code GET /api/ai/image/status/{taskId}} —— 前端每秒轮询一次，直到 status 变成 2 或 3</li>
 * </ul>
 * 合成一个 VO 是为了让前端只处理一种结构：提交时先拿到 taskId，
 * 之后每次轮询拿到的是同一个形状，只是 status / imageUrl 会变。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiImageTaskVO {

    private String taskId;

    /** 0 未生成 / 1 生成中 / 2 成功 / 3 失败 */
    private Integer status;

    /** 结果图地址，status=2 才有值，其余情况为 null */
    private String imageUrl;

}
