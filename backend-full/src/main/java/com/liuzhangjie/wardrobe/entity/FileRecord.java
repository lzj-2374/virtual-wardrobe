package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件记录表。
 * <p>
 * 类名 FileRecord 转下划线是 file_record，跟实际表名 file 对不上，
 * 所以必须用 {@code @TableName("file")} 手动指定，否则一查就报 Table doesn't exist。
 * <p>
 * 这张表不做逻辑删除：没有 deleted 字段，也不能加 {@code @TableLogic}。
 */
@Data
@TableName("file")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传人 id，从登录态里取，不接受前端传 */
    private Long userId;

    /** 原始文件名，只给人看；OSS 上的 key 用 UUID 重新生成 */
    private String fileName;

    /** OSS 完整访问地址，前端直接拿去当 img 的 src */
    private String url;

    /** 缩略图地址，目前没有做真缩略图，与 url 相同 */
    private String thumbUrl;

    /** OSS 对象 key，如 wardrobe/2026/11/03/xxx.jpg；删文件时 OSS 只认它 */
    private String ossKey;

    /** 1 衣物图 / 2 AI 效果图 / 3 头像 / 4 搭配封面 */
    private Integer bizType;

    /** 文件字节数，列是 BIGINT，所以用 Long */
    private Long fileSize;

    /** 由数据库 DEFAULT CURRENT_TIMESTAMP 填充，代码里不 set */
    private LocalDateTime createTime;

}
