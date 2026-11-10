package com.liuzhangjie.wardrobe.controller;

import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.UploadVO;
import com.liuzhangjie.wardrobe.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口。这一层只负责「接住请求、递回结果」，不碰 OSS、不碰数据库。
 * <p>
 * 前端 api/file.js 用的是 FormData，字段名固定 file，所以这里必须 @RequestParam("file")。
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件相关接口")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传图片。
     * 这个路径没加进 SecurityConfig 的放行清单，所以必须带 token —— 这是刻意的，
     * file 表要记 user_id，匿名上传没法归属。
     *
     * @param file    前端表单里的 file 字段
     * @param bizType 可选，1 衣物图 / 2 效果图 / 3 头像 / 4 搭配封面；前端暂时没传，默认 1
     */
    @PostMapping("/upload")
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "bizType", required = false) Integer bizType) {
        return Result.ok(fileService.upload(file, bizType));
    }

}
