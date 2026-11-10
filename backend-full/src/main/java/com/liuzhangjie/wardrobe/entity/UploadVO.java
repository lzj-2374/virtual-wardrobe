package com.liuzhangjie.wardrobe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传成功的返回体。
 * <p>
 * 字段名必须和前端 api/file.js 的调用方对上：
 * OutfitEdit.vue 第 204 行读的是 {@code res.url || res.thumbUrl}，
 * 所以 url 不能改名，否则前端拿到 undefined，封面永远是空的。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadVO {

    /** OSS 完整访问地址 */
    private String url;

    /** 缩略图地址，目前没做真缩略图，跟 url 一样 */
    private String thumbUrl;

}
