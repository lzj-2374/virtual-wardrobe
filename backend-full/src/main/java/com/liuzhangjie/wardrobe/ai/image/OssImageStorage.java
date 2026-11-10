package com.liuzhangjie.wardrobe.ai.image;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.liuzhangjie.wardrobe.config.OssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 把生图结果写到 OSS，返回可访问地址。
 * <p>
 * 为什么不复用 {@code FileService.upload}：那个方法是给「用户上传 multipart 文件」用的，
 * 入参是 {@code MultipartFile}，而这里手上只有一个 byte[]（模型返回的或 Java2D 画出来的），
 * 硬套要么先落地成临时文件、要么伪造一个 MultipartFile，两种都别扭。
 * <p>
 * 和用户上传的图分开放：key 前缀用 {@code 效果图/} 下的独立目录，
 * 以后要统计"AI 生图占了多少存储"或者批量清理过期效果图，按目录就能捞出来。
 */
@Slf4j
@Component
public class OssImageStorage {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final OSS ossClient;
    private final OssProperties ossProperties;

    public OssImageStorage(OSS ossClient, OssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    /**
     * @param bytes 图片字节
     * @param ext   扩展名，png / jpg
     * @return 公网可访问地址；失败时抛 IllegalStateException，由上层决定是否降级
     */
    public String upload(byte[] bytes, String ext) {
        String key = ossProperties.getDirPrefix() + "/ai-effect/"
                + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            // 必须显式给 Content-Type，否则 OSS 按 octet-stream 存，浏览器打开会变成"下载"
            metadata.setContentType("png".equalsIgnoreCase(ext) ? "image/png" : "image/jpeg");

            ossClient.putObject(ossProperties.getBucketName(), key,
                    new ByteArrayInputStream(bytes), metadata);

            String url = "https://" + ossProperties.getBucketName()
                    + "." + ossProperties.getEndpoint() + "/" + key;
            log.info("效果图已上传 OSS，key={}，大小={} 字节", key, bytes.length);
            return url;
        } catch (Exception e) {
            log.error("效果图上传 OSS 失败，key={}", key, e);
            throw new IllegalStateException("效果图上传失败：" + e.getMessage(), e);
        }
    }

}
