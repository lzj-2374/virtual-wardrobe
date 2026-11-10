package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

/**
 * 衣物属性识别的入参，对应 {@code POST /api/clothing/recognize}。
 * <p>
 * 两个字段都允许为空，识别器会尽量从有的那个里提取信息，实在没线索就返回一套安全的默认值。
 * <ul>
 *   <li>{@code fileName} —— 当前前端（ClothingAdd.vue）只传这个。文件名里常带颜色和品类，
 *       例如「白色针织衫.jpg」，能直接抽出来</li>
 *   <li>{@code imageUrl} —— 图片的 OSS 地址。传了的话可以做两件文件名做不到的事：
 *       用视觉模型直接读图；或者本地算图片主色。前端目前不传，因为识别发生在图片上传之前</li>
 * </ul>
 */
@Data
public class RecognizeDTO {

    /** 原始文件名，如 白色针织衫.jpg */
    private String fileName;

    /** 图片可访问地址，可选。有值且配了视觉模型 key 时才会真正"看图" */
    private String imageUrl;

}
