package com.liuzhangjie.wardrobe.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 阿里云 OSS 客户端配置。
 * <p>
 * OSSClient 是人家 SDK 里的对象，不受 Spring 管理，所以这里手动造一个交给容器，
 * 以后哪里要用就构造器注入 {@code OSS}，全应用共用同一个实例。
 * <p>
 * 为什么必须做成单例：OSSClient 内部维护着 HTTP 连接池，
 * 如果每次上传都 new 一个，等于每次都重开一个连接池，传几百张图之后连接会被耗干，
 * 表现就是上传越来越慢、最后超时。
 * <p>
 * 注意 {@code build} 只传 endpoint / AK / SK 三个参数：
 * OSSClient 是「账号级」的，一个实例能操作该账号下所有 bucket，
 * bucket 名和目录前缀在真正上传时才用，所以不参与创建。
 */
@Slf4j
@Configuration
public class OssConfig {

    private final OssProperties ossProperties;

    public OssConfig(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @Bean
    public OSS ossClient() {
        String ak = ossProperties.getAccessKeyId();
        String sk = ossProperties.getAccessKeySecret();
        // AK/SK 现在只从环境变量读，yml 里是空的。SDK 拿到空串会直接抛异常把整个应用卡在启动阶段，
        // 而这里只是一个可选能力（传图/存生图），不该拖垮启动。所以缺 key 时先给个占位客户端：
        // 应用照常起来，等真去上传时再抛看得懂的错。
        if (!StringUtils.hasText(ak) || !StringUtils.hasText(sk)) {
            log.error("OSS 未配置 AK/SK，图片上传与生图功能不可用。"
                    + "请在 IDEA 运行配置或系统环境变量里设置 OSS_AK 和 OSS_SK 后重启。");
            ak = "not-configured";
            sk = "not-configured";
        }
        // 签名类报错（SignatureDoesNotMatch）最难查，因为配置读没读到、读的是哪一把都不体现在错误里。
        // 这里在启动时把 AK 前缀和两端长度打出来，方便一眼看出"是不是环境变量没生效/填串了"。
        // 只打前缀，不打密钥内容。
        log.info("OSS 客户端初始化：endpoint={}，bucket={}，AK={}***（{} 位），SK 长度 {}",
                ossProperties.getEndpoint(), ossProperties.getBucketName(),
                head(ak, 8), ak.length(), sk.length());
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ak,
                sk
        );
    }

    private static String head(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

}
