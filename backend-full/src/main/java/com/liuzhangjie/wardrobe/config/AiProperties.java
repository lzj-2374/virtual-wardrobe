package com.liuzhangjie.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * AI 相关配置的搬运工，对应 application.yml 里的 {@code ai} 段。
 * <p>
 * 分成两组：{@code ai.llm}（对话推荐，DeepSeek）和 {@code ai.image}（生图，通义万相）。
 * 两组的 {@code api-key} 都可以留空——留空时对应的实现会自动降级：
 * <ul>
 *   <li>llm 没 key → 走内置规则引擎，从真实衣柜里挑衣服组合，效果差些但一定跑得起来</li>
 *   <li>image 没 key → 走服务端合成，用真实单品图拼一张效果图，链路依然完整</li>
 * </ul>
 * 这个设计的原因很实际：答辩现场断网、额度用完、或者 key 还没申请下来，
 * 系统不能整个瘫掉。降级后功能语义不变，只是"聪明程度"下降。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Llm llm = new Llm();

    private Image image = new Image();

    /** 对话推荐是否配置了可用的大模型。没配就走规则引擎 */
    public boolean isLlmReady() {
        return llm != null && StringUtils.hasText(llm.getApiKey());
    }

    /** 生图是否配置了可用的图像模型。没配就走服务端合成 */
    public boolean isImageReady() {
        return image != null && StringUtils.hasText(image.getApiKey());
    }

    @Data
    public static class Llm {

        /**
         * auto  —— 有 key 用 DeepSeek，没 key 退回规则引擎（默认，推荐）
         * deepseek —— 强制走 DeepSeek，没 key 直接报错（用于确认配置是否生效）
         * rule —— 强制用规则引擎，不发任何外部请求（省钱、离线演示）
         */
        private String provider = "auto";

        private String apiKey = "";

        /** DeepSeek 的 OpenAI 兼容地址，末尾不带斜杠 */
        private String baseUrl = "https://api.deepseek.com";

        private String model = "deepseek-chat";

        private Duration timeout = Duration.ofSeconds(60);

        /** 返回内容里有 1~3 套搭配的 JSON，1200 token 够用且不至于让响应太慢 */
        private Integer maxTokens = 1200;

        private Double temperature = 0.7;
    }

    @Data
    public static class Image {

        /**
         * auto —— 有 key 用通义万相，没 key 用服务端合成（默认）
         * wanx —— 强制走通义万相
         * composite —— 强制用服务端合成
         */
        private String provider = "auto";

        private String apiKey = "";

        private String baseUrl = "https://dashscope.aliyuncs.com";

        /**
         * 万相图生图模型名。
         * <p>
         * 换模型不是改这一行就完事：不同代际的 {@code input} 结构不一样。
         * wan2.5+ 用 {@code {prompt, images[]}}，wanx2.1 用 {@code {function, prompt, base_image_url}}，
         * 传错结构会返回 InvalidParameter。所以改这里的同时要改 {@code DashScopeImageEditor.submit()}。
         */
        private String model = "wan2.5-i2i-preview";

        /** 生图 10~30s，超时给足；比这更久基本是挂了，早失败早重试 */
        private Duration timeout = Duration.ofSeconds(180);

        /** 效果图尺寸，竖向 3:4 更像人台照 */
        private Integer width = 768;

        private Integer height = 1024;

        /**
         * 视觉模型名，衣物属性识别用。
         * <p>
         * 和生图共用同一个 DashScope key，但走的是另一条接口（OpenAI 兼容的 chat/completions），
         * 所以模型名单独配。没 key 时识别走本地启发式，不会报错。
         * <p>
         * 这里必须是现役模型名。老名字 {@code qwen-vl-max} 已经下线，
         * 传过去会返回 {@code model_not_found}，然后被上层当成异常吞掉、静默降级到本地启发式，
         * 表现是"配了 key 但识别结果和没配一样"，很难查。
         */
        private String visionModel = "qwen3-vl-plus";
    }

}
