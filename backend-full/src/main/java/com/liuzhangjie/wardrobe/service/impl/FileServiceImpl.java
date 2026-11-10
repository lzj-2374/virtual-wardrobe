package com.liuzhangjie.wardrobe.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.liuzhangjie.wardrobe.common.BusinessException;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.config.OssProperties;
import com.liuzhangjie.wardrobe.entity.FileRecord;
import com.liuzhangjie.wardrobe.entity.UploadVO;
import com.liuzhangjie.wardrobe.mapper.FileMapper;
import com.liuzhangjie.wardrobe.service.FileService;
import com.liuzhangjie.wardrobe.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传。一次上传做四件事：
 * 1. 校验文件能不能收（空文件、非图片都挡掉）
 * 2. 自己生成一个不会重名的 key
 * 3. 把字节流推到 OSS，拿到访问地址
 * 4. 往 file 表补一条记录，把这次上传记账
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 允许的图片扩展名。不校验 MIME 是因为浏览器给的 contentType 可以伪造，后缀够用了 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** key 里按年月日分目录，避免同目录下文件过多，在 OSS 控制台翻起来费劲 */
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileMapper fileMapper;
    private final OSS ossClient;
    private final OssProperties ossProperties;

    public FileServiceImpl(FileMapper fileMapper, OSS ossClient, OssProperties ossProperties) {
        this.fileMapper = fileMapper;
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    @Override
    public UploadVO upload(MultipartFile file, Integer bizType) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // ===== 1. 校验 =====
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY);
        }
        String originalName = file.getOriginalFilename();
        String ext = extractExt(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
        }

        // ===== 2. 生成 key =====
        // 不用原始文件名：中文名会让 url 变得又长又难读；不同用户都传 IMG_001.jpg 会互相覆盖；
        // 极端情况下文件名里带 ../ 还会跑出桶目录。所以统一换成 UUID，原名只存进 file_name 列。
        String key = ossProperties.getDirPrefix() + "/"
                + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // ===== 3. 上传 OSS =====
        String url;
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            // 必须显式指定 Content-Type。不设的话 OSS 按 application/octet-stream 存，
            // 浏览器打开这个地址会变成「下载文件」而不是直接显示图片。
            metadata.setContentType(resolveContentType(ext));
            ossClient.putObject(ossProperties.getBucketName(), key, in, metadata);
            url = "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + key;
        } catch (IOException e) {
            log.error("读取上传文件流出错，fileName={}", originalName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        } catch (Exception e) {
            log.error("上传 OSS 失败，key={}", key, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }

        // ===== 4. 记账 =====
        // 这里没加 @Transactional：整个方法只有一次 insert，事务没有额外保护作用；
        // 真正需要兜底的是「图已经传上 OSS，但表没写成功」——那种情况事务回滚不了 OSS，
        // 只能手动把刚传上去的对象删掉，否则桶里会留下一个永远没人引用的孤儿文件。
        try {
            FileRecord record = new FileRecord();
            record.setUserId(userId);
            record.setFileName(originalName);
            record.setUrl(url);
            record.setThumbUrl(url);
            record.setOssKey(key);
            record.setBizType(bizType == null ? 1 : bizType);
            record.setFileSize(file.getSize());
            fileMapper.insert(record);
        } catch (Exception e) {
            log.error("文件记录写库失败，尝试删除已上传的 OSS 对象，key={}", key, e);
            safeDelete(key);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }

        return new UploadVO(url, url);
    }

    /** 取扩展名并转小写；没有扩展名返回空串，交给上面的白名单挡掉 */
    private String extractExt(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase();
    }

    /** 按扩展名给 Content-Type，认不出来的统一当 jpeg */
    private String resolveContentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    /** 补偿删除：删失败只能记日志，不能把异常再抛出去盖住原始错误 */
    private void safeDelete(String key) {
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), key);
        } catch (Exception ex) {
            log.error("补偿删除 OSS 对象失败，需人工清理，key={}", key, ex);
        }
    }

}
