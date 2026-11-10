package com.liuzhangjie.wardrobe.service;

import com.liuzhangjie.wardrobe.entity.UploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传一张图片到 OSS，并在 file 表留一条记录。
     *
     * @param file    前端 multipart 表单里的 file 字段
     * @param bizType 业务类型：1 衣物图 / 2 效果图 / 3 头像 / 4 搭配封面，可为空，默认 1
     * @return 可直接给前端当 img src 的地址
     */
    UploadVO upload(MultipartFile file, Integer bizType);

}
