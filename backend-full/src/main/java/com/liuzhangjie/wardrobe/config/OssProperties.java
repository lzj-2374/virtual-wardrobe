package com.liuzhangjie.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 配置的搬运工：把 application.yml 里 aliyun.oss 下面的 5 个值，
 * 装进这个对象的 5 个字段里，让 Java 代码能读到。
 * <p>
 * yml 写 access-key-id（中划线），这里写 accessKeyId（驼峰），
 * Spring 的宽松绑定会自动对上，不需要 @Value，也不需要在字段上加注解。
 * <p>
 * {@code @Data} 是必需的：@ConfigurationProperties 靠 setter 注入，
 * 没有 setter 时 5 个字段全是 null。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** 例如 oss-cn-beijing.aliyuncs.com，不带 https:// */
    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    /** 存储桶名，例如 lzj-wardrobe-ai */
    private String bucketName;

    /** 对象 key 的统一前缀，例如 wardrobe */
    private String dirPrefix;

}
