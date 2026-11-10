package com.liuzhangjie.wardrobe.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuzhangjie.wardrobe.config.AiProperties;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.ClothingTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DeepSeek 推荐引擎：把衣柜和用户原话拼成提示词，让模型直接返回结构化搭配。
 * <p>
 * 三个工程上的关键约束，缺一个这条链路就不稳：
 * <ol>
 *   <li><b>要求 JSON 输出 + 本地二次校验</b>：模型返回的 clothingId 可能根本不存在，
 *       直接落库会出现"搭配里有件不存在的衣服"，详情页直接崩。所以解析后必须拿衣柜 id 集合过滤一遍</li>
 *   <li><b>失败必须能降级</b>：网络抖动、模型抽风、返回不是 JSON，任何一步失败都抛异常，
 *       由 {@link OutfitAdvisorRouter} 接住改用规则引擎，而不是让用户看到 500</li>
 *   <li><b>温度调低</b>：同一个问题两次给出完全不同的搭配，演示时很难解释，也让缓存失去意义</li>
 * </ol>
 */
@Slf4j
@Component
public class DeepSeekOutfitAdvisor implements OutfitAdvisor {

    private static final int MAX_OUTFITS = 3;

    /**
     * 系统提示词。写死在代码里而不是塞进数据库：
     * 它是"接口约定"的一部分，改它就得同步改解析逻辑，两者必须一起提交、一起评审。
     */
    private static final String SYSTEM_PROMPT = """
            你是「虚拟衣柜」小程序的穿搭顾问。你只能从用户已录入的衣柜里挑选单品，绝对不能编造任何衣服。

            输出要求（必须严格遵守）：
            1. 只输出一个 JSON 对象，不要输出解释文字，不要用 markdown 代码块包裹。
            2. JSON 结构如下：
            {"outfits":[{"name":"为你搭配：xxx","reason":"一到两句话，具体到单品名与颜色","colorNote":"主色：白 / 点缀：米","scene":"日常|通勤|约会|运动|正式","season":4,"items":[{"clothingId":1,"role":1}]}]}
            3. role 取值：1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰。同一套搭配里 role 不能重复。
            4. season 取值：1 春 / 2 夏 / 4 秋 / 8 冬 / 15 四季。
            5. clothingId 必须来自用户衣柜列表里出现过的 ID，不许新造。
            6. 给出 1 到 3 套，套与套之间要有明显区别（换单品或换风格），不要三套只是鞋不一样。
            7. 每一套至少要有一件 role=1 的上装（套装、连衣裙也算上装）。
            8. 如果衣柜里的衣服不足以搭出一套，返回 {"outfits":[]}。
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekOutfitAdvisor(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout((int) aiProperties.getLlm().getTimeout().toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String name() {
        return "deepseek:" + aiProperties.getLlm().getModel();
    }

    @Override
    public List<OutfitDraft> advise(String message, OutfitIntent intent, List<Clothing> wardrobe,
                                    List<String> history, List<Long> pinnedClothingIds) {
        if (wardrobe == null || wardrobe.isEmpty()) {
            return List.of();
        }

        String userPrompt = buildUserPrompt(message, intent, wardrobe, history, pinnedClothingIds);
        String raw = callModel(userPrompt);
        return enforcePinned(parse(raw, intent, wardrobe), pinnedClothingIds);
    }

    /**
     * 校验模型有没有真的用上用户指定的单品。
     * <p>
     * 提示词里写了"必须用"不代表模型一定照做——这类约束在小模型上经常漏。
     * 所以返回前再查一遍：漏了的套直接丢掉；一套都不剩就抛异常，
     * 让 {@link OutfitAdvisorRouter} 降级到规则引擎，那边是把候选池收窄过的硬约束，
     * 一定能用上。这样"必须用这几件"就不会因为模型不听话而悄悄失效。
     */
    private List<OutfitDraft> enforcePinned(List<OutfitDraft> drafts, List<Long> pinned) {
        if (pinned == null || pinned.isEmpty()) {
            return drafts;
        }
        Set<Long> needed = new java.util.HashSet<>(pinned);
        List<OutfitDraft> kept = drafts.stream()
                .filter(d -> d.items().stream()
                        .map(OutfitDraft.Item::clothingId)
                        .collect(Collectors.toSet())
                        .containsAll(needed))
                .toList();
        if (kept.isEmpty()) {
            throw new IllegalStateException("模型没能用上用户指定的单品");
        }
        return kept;
    }

    // ===================== 提示词 =====================

    /**
     * 把衣柜拍平成一行一件的清单。
     * <p>
     * 用「ID=12 | 名称 | 品类 | 颜色 | 季节 | 厚薄 | 标签」这种固定格式，
     * 而不是直接丢 JSON：模型对紧凑的竖排清单更稳，也不容易把字段名记错。
     * ID 放在最前面，是为了让模型在生成 clothingId 时"抄"最近的数字，
     * 减少它自己数错的情况。
     */
    private String buildUserPrompt(String message, OutfitIntent intent, List<Clothing> wardrobe,
                                   List<String> history, List<Long> pinnedClothingIds) {
        StringBuilder sb = new StringBuilder();

        // 先放上文，再放当前这句。顺序不能反：模型读到最后一句时，
        // 脑子里已经装好了"上一轮聊的是什么"，理解省略句最准。
        if (history != null && !history.isEmpty()) {
            sb.append("最近的对话（按时间正序，用来理解下面这句里的省略和补充）：\n");
            for (String line : history) {
                sb.append(line).append('\n');
            }
            sb.append("注意：如果用户这次是在上一套的基础上提要求（换个场合、正式点、暖一点），\n")
                    .append("要在上文的语境里调整，不要当成本轮全新提问，也不要重复推荐已经给过的搭配。\n\n");
        }

        sb.append("用户需求：").append(message == null ? "随便搭一套" : message).append('\n');
        sb.append("已解析意图：场景=").append(intent.scene())
                .append("，季节=").append(OutfitIntent.seasonName(intent.season()));
        if (intent.temperature() != null) {
            // 气温单独报给模型，让它判断厚薄。只给季节名是不够的：
            // "秋"既可能是 10 度也可能是 22 度，穿的东西差一个季节
            sb.append("，当前气温=").append(intent.temperature()).append("℃");
        }
        if (intent.styleHint() != null) {
            sb.append("，风格倾向=").append(intent.styleHint());
        }
        sb.append('\n');

        // 用户用「+」点名的单品。单独开一段、放在衣柜清单之前：
        // 紧挨着"用户需求"更容易被当成硬约束，混在几十行清单里就淹了
        if (pinnedClothingIds != null && !pinnedClothingIds.isEmpty()) {
            sb.append("\n【本轮硬性要求】用户亲手指定了下面这些单品，每一套搭配都必须包含它们：\n");
            for (Clothing c : wardrobe) {
                if (pinnedClothingIds.contains(c.getId())) {
                    sb.append("  - ").append(c.getName())
                            .append("（clothingId=").append(c.getId())
                            .append("，").append(WardrobeSupport.roleLabel(WardrobeSupport.roleOf(c)))
                            .append("）\n");
                }
            }
            sb.append("不许替换、不许省略；其他位置（鞋、配饰）你可以自由搭配，好让几套之间有区别。\n");
        }

        sb.append("\n衣柜（只能从这里挑，共 ").append(wardrobe.size()).append(" 件）：\n");

        for (Clothing c : wardrobe) {
            sb.append("ID=").append(c.getId())
                    .append(" | ").append(c.getName())
                    .append(" | ").append(WardrobeSupport.roleLabel(WardrobeSupport.roleOf(c)))
                    .append(nullSafe(c.getSubCategory()) ? "" : "/" + c.getSubCategory())
                    .append(" | 颜色=").append(c.getColor() == null ? "未知" : c.getColor())
                    .append(" | 季节=").append(seasonText(c.getSeason()))
                    .append(" | 厚薄=").append(thicknessText(c.getThickness()))
                    .append(" | 标签=").append(tagText(c.getTags()))
                    .append('\n');
        }
        sb.append("\n请按系统要求返回 JSON。");
        return sb.toString();
    }

    private boolean nullSafe(String s) {
        return !StringUtils.hasText(s);
    }

    private String seasonText(Integer season) {
        if (season == null) {
            return "未知";
        }
        List<String> parts = new ArrayList<>();
        if ((season & OutfitIntent.SPRING) != 0) parts.add("春");
        if ((season & OutfitIntent.SUMMER) != 0) parts.add("夏");
        if ((season & OutfitIntent.AUTUMN) != 0) parts.add("秋");
        if ((season & OutfitIntent.WINTER) != 0) parts.add("冬");
        return parts.isEmpty() ? "四季" : String.join("", parts);
    }

    private String thicknessText(Integer thickness) {
        if (thickness == null) {
            return "未知";
        }
        return switch (thickness) {
            case 1 -> "薄";
            case 3 -> "厚";
            default -> "适中";
        };
    }

    private String tagText(List<ClothingTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return "无";
        }
        return tags.stream()
                .map(ClothingTag::getTagValue)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    // ===================== 调用 =====================

    private String callModel(String userPrompt) {
        AiProperties.Llm llm = aiProperties.getLlm();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llm.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", llm.getTemperature());
        body.put("max_tokens", llm.getMaxTokens());
        // 强制 JSON 模式：DeepSeek 支持 response_format，开了它之后模型不会先寒暄两句再给 JSON
        body.put("response_format", Map.of("type", "json_object"));
        body.put("stream", false);

        String response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + llm.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("模型返回里没有 choices[0].message.content");
            }
            return content.asText();
        } catch (Exception e) {
            throw new IllegalStateException("解析模型响应失败", e);
        }
    }

    // ===================== 解析与校验 =====================

    /**
     * 把模型返回的 JSON 变成草稿列表，顺手做四道清洗：
     * 去掉 markdown 代码块、过滤不存在的 clothingId、同套内 role 去重、丢掉没有上装的套。
     */
    private List<OutfitDraft> parse(String raw, OutfitIntent intent, List<Clothing> wardrobe) {
        Set<Long> validIds = wardrobe.stream().map(Clothing::getId).collect(Collectors.toSet());
        String json = stripCodeFence(raw);

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + preview(raw), e);
        }

        JsonNode outfits = root.path("outfits");
        if (!outfits.isArray()) {
            // 有些模型会把顶层直接写成数组，兼容一下
            outfits = root.isArray() ? root : objectMapper.createArrayNode();
        }

        List<OutfitDraft> drafts = new ArrayList<>();
        for (JsonNode node : outfits) {
            List<OutfitDraft.Item> items = new ArrayList<>();
            Set<Integer> usedRoles = new java.util.HashSet<>();

            for (JsonNode itemNode : node.path("items")) {
                long clothingId = itemNode.path("clothingId").asLong(-1);
                int role = itemNode.path("role").asInt(-1);
                if (!validIds.contains(clothingId) || role < 1 || role > 5) {
                    log.warn("模型给出的单品无效，已丢弃：clothingId={}, role={}", clothingId, role);
                    continue;
                }
                if (!usedRoles.add(role)) {
                    // 同一套里一个位置两件衣服，落库会撞 uk_rec_role，这里直接丢后者
                    log.warn("同一套里 role={} 重复，已丢弃后出现的单品", role);
                    continue;
                }
                items.add(new OutfitDraft.Item(clothingId, role));
            }

            if (items.isEmpty()) {
                continue;
            }

            OutfitDraft draft = new OutfitDraft(
                    textOrDefault(node, "name", "为你搭配：" + intent.scene()),
                    textOrDefault(node, "reason", "按你的衣柜和当前季节为你搭配。"),
                    textOrDefault(node, "colorNote", "主色：以单品原色为主"),
                    textOrDefault(node, "scene", intent.scene()),
                    node.path("season").asInt(intent.season()),
                    items
            );

            if (!draft.hasTop()) {
                log.warn("这套搭配没有上装，不成一套，已丢弃");
                continue;
            }
            drafts.add(draft);
            if (drafts.size() >= MAX_OUTFITS) {
                break;
            }
        }

        if (drafts.isEmpty()) {
            throw new IllegalStateException("模型没有产出可用的搭配");
        }
        return drafts;
    }

    /** 模型经常不听话，还是把 ```json 包进来。这里剥掉，否则 readTree 直接抛异常 */
    private String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
        }
        return s.trim();
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value : fallback;
    }

    /** 日志里只留前 200 字，避免把整段模型输出灌进日志文件 */
    private String preview(String raw) {
        if (raw == null) {
            return "null";
        }
        return raw.length() <= 200 ? raw : raw.substring(0, 200) + "...";
    }

}
