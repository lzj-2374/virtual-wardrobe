package com.liuzhangjie.wardrobe.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuzhangjie.wardrobe.config.AiProperties;
import com.liuzhangjie.wardrobe.entity.ClothingDTO;
import com.liuzhangjie.wardrobe.entity.ClothingTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 衣物属性识别：给一张图，猜它是什么衣服、什么颜色、什么品类。
 * <p>
 * 两条路，能用哪条走哪条：
 * <ol>
 *   <li><b>视觉模型</b>——配了 DashScope key 且有图片地址时，调 qwen-vl 直接读图，最准</li>
 *   <li><b>本地启发式</b>——没 key 或没图片时，从文件名里抽颜色/品类关键词，
 *       再从图片里算主色。听起来简陋，但用户上传时文件名常常就是「白色针织衫.jpg」，
 *       命中率比想象中高，而且零成本、零延迟</li>
 * </ol>
 * 两条路都只产出「建议值」，最终仍由用户在表单里确认。这是有意的：
 * 识别错了不应该是灾难，用户改一下就好；识别对了能省他两分钟。
 */
@Slf4j
@Component
public class ClothingRecognizer {

    /** 品类关键词表，按优先级排列。顺序很重要：「连衣裙」要排在「裙」前面、「西装外套」排在「西装」前面 */
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    /** 颜色关键词。（词， 标准色名） */
    private static final String[][] COLOR_KEYWORDS = {
            {"藏蓝", "藏蓝"}, {"卡其", "卡其"}, {"驼", "驼"}, {"杏", "杏"},
            {"白", "白"}, {"黑", "黑"}, {"灰", "灰"}, {"米", "米"}, {"银", "银"},
            {"红", "红"}, {"橙", "橙"}, {"黄", "黄"}, {"绿", "绿"}, {"青", "青"},
            {"蓝", "蓝"}, {"紫", "紫"}, {"粉", "粉"}, {"棕", "棕"}, {"咖", "咖"}
    };

    private static final String[][] CATEGORY_TOP = {
            {"T恤", "T恤"}, {"恤", "T恤"}, {"衬衫", "衬衫"}, {"卫衣", "卫衣"},
            {"针织", "针织衫"}, {"毛衣", "针织衫"}, {"吊带", "吊带"}, {"背心", "背心"},
            {"POLO", "POLO衫"}, {"打底", "打底衫"}
    };
    private static final String[][] CATEGORY_BOTTOM = {
            {"牛仔裤", "牛仔裤"}, {"牛仔", "牛仔裤"}, {"短裤", "短裤"},
            {"阔腿裤", "阔腿裤"}, {"西裤", "西裤"}, {"裤", "休闲裤"}
    };
    private static final String[][] CATEGORY_OUTER = {
            {"羽绒", "羽绒服"}, {"风衣", "风衣"}, {"大衣", "大衣"}, {"夹克", "夹克"},
            {"开衫", "开衫"}, {"冲锋衣", "冲锋衣"}, {"外套", "外套"}
    };
    private static final String[][] CATEGORY_SUIT = {
            {"连衣裙", "连衣裙"}, {"套装", "西装套装"}, {"旗袍", "旗袍"}, {"裙", "半身裙"}
    };
    private static final String[][] CATEGORY_ACCESSORY = {
            {"运动鞋", "运动鞋"}, {"板鞋", "板鞋"}, {"皮鞋", "皮鞋"}, {"靴", "靴子"},
            {"凉鞋", "凉鞋"}, {"拖鞋", "拖鞋"}, {"鞋", "鞋"}, {"背包", "包"},
            {"包", "包"}, {"帽", "帽子"}, {"围巾", "围巾"}, {"腰带", "腰带"}, {"袜", "袜子"}
    };

    /**
     * 识别不出来的字段统一填这个占位符。
     * <p>
     * 用一个常量而不是到处写「待补充」字面量，是因为它同时被三处使用：
     * 填进 DTO、拼默认名称时判断、以及文档里说明"什么样的值代表没识别出来"。
     * 三处写死字符串，改错一处就会出现「识别成功但其实没识别」的假象。
     */
    private static final String PENDING = "待补充";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public ClothingRecognizer(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 识别入口。
     *
     * @param fileName 原始文件名，可为空
     * @param imageUrl 图片的公网地址，可为空。有值且配了 key 时才可能走视觉模型
     * @return 建议值，永远不为 null；识别不出来时给一套安全的默认值
     */
    public ClothingDTO recognize(String fileName, String imageUrl) {
        if (StringUtils.hasText(imageUrl) && aiProperties.isImageReady() && aiProperties.isLlmReady()) {
            try {
                ClothingDTO result = recognizeByVision(imageUrl);
                if (result != null) {
                    log.info("衣物识别走视觉模型，fileName={}", fileName);
                    return result;
                }
            } catch (Exception e) {
                // 模型这条路失败不该让用户点按钮就报错，静默降级
                log.warn("视觉模型识别失败，降级为本地启发式：{}", e.getMessage());
            }
        }
        return recognizeLocally(fileName, imageUrl);
    }

    // ===================== 视觉模型 =====================

    /**
     * 调通义千问 VL（OpenAI 兼容接口）读图。
     * <p>
     * 提示词里把「只返回 JSON」写死，并要求颜色用单字（白/黑/蓝…），
     * 因为前端是拿这个值直接填进颜色输入框的，返回「米白色」和「米白」会让用户的衣柜出现两套说法。
     */
    private ClothingDTO recognizeByVision(String imageUrl) throws Exception {
        AiProperties.Image cfg = aiProperties.getImage();

        Map<String, Object> systemMessage = Map.of("role", "system", "content",
                "你是服装识别助手。只输出 JSON，不要解释。字段："
                        + "name(简短中文名称，不超过12字), category(1上衣/2裤子/3外套/4套装/5鞋包配饰), "
                        + "subCategory(子品类，如T恤/牛仔裤), color(单个中文颜色字，如白/黑/蓝/米), "
                        + "season(1春2夏4秋8冬，不确定给15), thickness(1薄2适中3厚), "
                        + "style(风格，从 休闲/通勤/运动/复古/甜美 里选一个), "
                        + "occasion(场合，从 日常/上班/约会/运动/正式 里选一个)");

        Map<String, Object> userMessage = Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", "识别这件衣物的属性。"),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getVisionModel());
        body.put("messages", List.of(systemMessage, userMessage));
        body.put("temperature", 0.1);
        body.put("max_tokens", 300);

        String base = cfg.getBaseUrl().replaceAll("/+$", "");
        String response = RestClient.create(base)
                .post()
                .uri("/compatible-mode/v1/chat/completions")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        String content = objectMapper.readTree(response)
                .path("choices").path(0).path("message").path("content").asText("");
        JsonNode node = objectMapper.readTree(stripCodeFence(content));
        if (node.isMissingNode() || node.isEmpty()) {
            return null;
        }

        ClothingDTO dto = new ClothingDTO();
        dto.setName(text(node, "name", "未命名衣物"));
        dto.setCategory(node.path("category").asInt(1));
        dto.setSubCategory(text(node, "subCategory", ""));
        dto.setColor(text(node, "color", ""));
        dto.setSeason(node.path("season").asInt(15));
        dto.setThickness(node.path("thickness").asInt(2));

        List<ClothingTag> tags = new ArrayList<>();
        String style = text(node, "style", "");
        if (StringUtils.hasText(style)) {
            tags.add(newTag(1, style));
        }
        String occasion = text(node, "occasion", "");
        if (StringUtils.hasText(occasion)) {
            tags.add(newTag(2, occasion));
        }
        dto.setTags(tags);
        return dto;
    }

    // ===================== 本地启发式 =====================

    private ClothingDTO recognizeLocally(String fileName, String imageUrl) {
        String clean = cleanFileName(fileName);

        // 1. 从文件名里抠颜色和品类
        String color = matchColor(clean);
        String[] categoryHit = matchCategory(clean);
        Integer category = categoryHit == null ? null : Integer.valueOf(categoryHit[0]);
        String subCategory = categoryHit == null ? null : categoryHit[1];

        // 2. 文件名没给出颜色时，从图片里算主色。这一步是真实计算，不是猜的
        if (!StringUtils.hasText(color) && StringUtils.hasText(imageUrl)) {
            color = dominantColor(imageUrl);
        }

        // 3. 组装建议值。缺什么补什么，保证前端拿到的是一套能直接提交的数据
        ClothingDTO dto = new ClothingDTO();
        dto.setCategory(category == null ? 1 : category);
        dto.setSubCategory(StringUtils.hasText(subCategory) ? subCategory : PENDING);
        dto.setColor(StringUtils.hasText(color) ? color : PENDING);
        dto.setThickness(2);
        // 季节不再一律给 15。以前写死 15 的意思是"都没识别出来，先按四季皆宜算"，
        // 但它和"真的四季皆宜"区分不开，用户只会觉得 AI 没干活。改成按子品类推。
        dto.setSeason(inferSeason(dto.getSubCategory(), dto.getThickness()));

        // 名称优先用清理后的文件名：它常常本来就是人话（「白色针织衫」），比拼接出来的更自然
        String name = StringUtils.hasText(clean) ? clean : buildName(dto.getColor(), dto.getSubCategory());
        dto.setName(name.length() > 32 ? name.substring(0, 32) : name);
        List<ClothingTag> tags = new ArrayList<>();
        tags.add(newTag(1, "休闲"));
        tags.add(newTag(2, defaultOccasion(dto.getCategory())));
        dto.setTags(tags);

        log.debug("衣物识别走本地启发式，fileName={}，结果：name={}，category={}，color={}",
                fileName, dto.getName(), dto.getCategory(), dto.getColor());
        return dto;
    }

    /**
     * 清理文件名，去掉扩展名和常见的无意义前缀。
     * <p>
     * 手机照片叫 {@code IMG_20261012_183000.jpg}，微信保存的叫 {@code 微信图片_20261012183000.jpg}，
     * 这些原样拿来当衣物名称毫无意义，不如退回到「颜色 + 品类」的拼装名。
     */
    private String cleanFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        // 去掉一堆字母数字下划线组成的"机器名"，以及微信/截图的固定前缀
        name = name.replaceAll("(?i)^(img|dsc|pxl|screenshot|微信图片|wechat)[-_\\s]*", "");
        name = name.replaceAll("^[\\d_\\-\\s]+$", "");
        name = name.replaceAll("[_\\-]{2,}", "_").trim();
        // 剩下的如果全是数字和符号，说明确实没有语义，返回空
        return name.replaceAll("[\\d\\s_\\-.]", "").isEmpty() ? "" : name;
    }

    /** 在文本里找颜色词。按 COLOR_KEYWORDS 的顺序匹配，长词在前（藏蓝优先于蓝） */
    private String matchColor(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        for (String[] pair : COLOR_KEYWORDS) {
            if (text.contains(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    /**
     * 在文本里找品类，返回 {category, subCategory}。
     * 匹配顺序是「套装 → 外套 → 下装 → 上装 → 配饰」：
     * 「连衣裙」必须先命中套装，否则会被「裙」带到别处；
     * 「西装外套」必须先命中外套，否则会被当成上装。
     */
    private String[] matchCategory(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String[] hit = match(text, CATEGORY_SUIT, 4);
        if (hit != null) return hit;
        hit = match(text, CATEGORY_OUTER, 3);
        if (hit != null) return hit;
        hit = match(text, CATEGORY_BOTTOM, 2);
        if (hit != null) return hit;
        hit = match(text, CATEGORY_TOP, 1);
        if (hit != null) return hit;
        return match(text, CATEGORY_ACCESSORY, 5);
    }

    private String[] match(String text, String[][] table, int category) {
        for (String[] pair : table) {
            if (text.contains(pair[0])) {
                return new String[]{String.valueOf(category), pair[1]};
            }
        }
        return null;
    }

    /**
     * 算图片主色。
     * <p>
     * 做法是把图缩到 64×64 再逐像素统计，而不是原图遍历：
     * 一张 4000×3000 的照片有 1200 万像素，缩到 4096 个点足够代表整体颜色，速度快两个数量级。
     * 纯白/近白背景会被剔除，否则所有衣服的主色都会算成「白」（电商图基本都是白底）。
     */
    private String dominantColor(String imageUrl) {
        BufferedImage source = download(imageUrl);
        if (source == null) {
            return null;
        }
        try {
            BufferedImage small = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            small.createGraphics().drawImage(source, 0, 0, 64, 64, null);

            Map<String, Integer> buckets = new LinkedHashMap<>();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int rgb = small.getRGB(x, y);
                    String family = classify(rgb);
                    if (family != null) {
                        buckets.merge(family, 1, Integer::sum);
                    }
                }
            }
            return buckets.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        } finally {
            source.flush();
        }
    }

    /**
     * 把一个像素归到颜色族。返回 null 表示这个像素不参与统计（背景、噪点）。
     * <p>
     * 判断顺序：太亮且几乎无饱和 → 当背景丢掉；然后按明度挑黑/白/灰；
     * 剩下的按色相分。
     */
    private String classify(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] * 360f;
        float sat = hsb[1];
        float bri = hsb[2];

        // 近白背景：很亮且没什么颜色。这是"背景排除"，不是"白色衣服"——
        // 白色衣服会带一点点色偏或阴影，饱和度略高，不会全被删掉
        if (bri > 0.93f && sat < 0.08f) {
            return null;
        }
        if (bri < 0.18f) {
            return "黑";
        }
        if (sat < 0.12f) {
            return bri > 0.7f ? "白" : "灰";
        }
        if (hue < 15 || hue >= 345) return "红";
        if (hue < 40) return bri < 0.55f ? "棕" : "橙";
        if (hue < 65) return "黄";
        if (hue < 160) return "绿";
        if (hue < 200) return "青";
        if (hue < 250) return "蓝";
        if (hue < 290) return "紫";
        return "粉";
    }

    private BufferedImage download(String imageUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "wardrobe-recognizer");
            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            log.warn("识别主色时下载图片失败：{}", e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ===================== 小工具 =====================

    private ClothingTag newTag(int type, String value) {
        ClothingTag tag = new ClothingTag();
        tag.setTagType(type);
        tag.setTagValue(value);
        return tag;
    }

    private String defaultOccasion(Integer category) {
        return switch (category == null ? 1 : category) {
            case 4 -> "正式";
            case 5 -> "运动";
            default -> "日常";
        };
    }

    /**
     * 按子品类推季节，推不出来再按厚薄兜底。
     * <p>
     * 季节在库里是位掩码：1 春、2 夏、4 秋、8 冬，相加即多选（春秋 = 1+4 = 5，四季 = 15）。
     * 所以这里返回的是"能穿它的那几季"，羽绒服给 8（冬），风衣给 5（春秋），
     * 衬衫、牛仔裤这种确实全年能穿的才给 15——15 从此只表示"真的四季皆宜"，
     * 不再兼任"没识别出来"这个含义。
     */
    private Integer inferSeason(String subCategory, Integer thickness) {
        if (StringUtils.hasText(subCategory) && !PENDING.equals(subCategory)) {
            String s = subCategory;
            if (s.contains("羽绒")) return 8;
            if (s.contains("大衣") || s.contains("冲锋衣") || s.contains("毛衣")) return 12; // 秋冬
            if (s.contains("风衣") || s.contains("夹克") || s.contains("开衫")
                    || s.contains("外套") || s.contains("卫衣") || s.contains("针织衫")
                    || s.contains("西装套装")) return 5;                                     // 春秋
            if (s.contains("T恤") || s.contains("POLO") || s.contains("吊带") || s.contains("背心")
                    || s.contains("短裤") || s.contains("裙") || s.contains("旗袍")) return 6; // 春夏
            // 衬衫和各类长裤裤型确实四季都能穿，返回 15 是准确的，不是兜底
            if (s.contains("衬衫") || s.contains("打底") || s.contains("裤")) return 15;
            // 鞋包配饰不挑季节
            if (s.contains("鞋") || s.contains("包") || s.contains("帽")
                    || s.contains("围巾") || s.contains("腰带") || s.contains("袜")) return 15;
        }
        // 连子品类都没有，只能凭厚薄猜：薄=春夏，适中=春秋，厚=秋冬
        return switch (thickness == null ? 2 : thickness) {
            case 1 -> 6;
            case 3 -> 12;
            default -> 5;
        };
    }

    /**
     * 拼一个兜底名称。
     * <p>
     * 宁可叫「未命名衣物」，也不要把「待补充」拼进去——用户看到的应该是"这里需要你填"，
     * 而不是「智能识别：待补充待补充」这种一眼是程序故障的字。
     */
    private String buildName(String color, String subCategory) {
        boolean hasColor = StringUtils.hasText(color) && !PENDING.equals(color);
        boolean hasSub = StringUtils.hasText(subCategory) && !PENDING.equals(subCategory);
        if (hasColor && hasSub) {
            return "智能识别：" + color + subCategory;
        }
        if (hasSub) {
            return "智能识别：" + subCategory;
        }
        return "未命名衣物";
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int i = s.indexOf('\n');
            if (i > 0) {
                s = s.substring(i + 1);
            }
            int j = s.lastIndexOf("```");
            if (j >= 0) {
                s = s.substring(0, j);
            }
        }
        return s.trim();
    }

}
