package com.liuzhangjie.wardrobe.ai;

import com.liuzhangjie.wardrobe.config.AiProperties;
import com.liuzhangjie.wardrobe.entity.Clothing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 推荐引擎的选择器。调用方只认 {@link OutfitAdvisor} 这个接口，
 * 至于本次到底是大模型还是规则引擎算的，由这里决定。
 * <p>
 * 三种模式（对应 {@code ai.llm.provider}）：
 * <ul>
 *   <li>{@code auto} —— 配了 key 就走 DeepSeek，没配或不填就走规则引擎（默认，推荐）</li>
 *   <li>{@code deepseek} —— 优先 DeepSeek；调用失败时仍然回落到规则引擎，只是日志打 ERROR</li>
 *   <li>{@code rule} —— 强制规则引擎，一个外部请求都不发（离线演示、省额度）</li>
 * </ul>
 * 为什么"强制 deepseek"失败了也不抛异常：这个系统的定位是"随时能演示"，
 * 因为一次网络抖动就让用户看到 500，比降级成规则推荐严重得多。
 * 所以降级一定会发生，区别只在于日志级别——真出问题时能在日志里一眼看到 ERROR。
 */
@Slf4j
@Service
public class OutfitAdvisorRouter {

    private final RuleBasedOutfitAdvisor ruleAdvisor;
    private final DeepSeekOutfitAdvisor deepSeekAdvisor;
    private final AiProperties aiProperties;

    public OutfitAdvisorRouter(RuleBasedOutfitAdvisor ruleAdvisor,
                               DeepSeekOutfitAdvisor deepSeekAdvisor,
                               AiProperties aiProperties) {
        this.ruleAdvisor = ruleAdvisor;
        this.deepSeekAdvisor = deepSeekAdvisor;
        this.aiProperties = aiProperties;
    }

    /**
     * 拿到本次该用的引擎，并执行推荐。
     *
     * @param history 最近几轮对话，只对大模型那条路有意义；规则引擎会忽略它
     * @param pinnedClothingIds 用户点「+」指定的必选单品，两条路都必须用上
     */
    public List<OutfitDraft> advise(String message, OutfitIntent intent, List<Clothing> wardrobe,
                                    List<String> history, List<Long> pinnedClothingIds) {
        String provider = aiProperties.getLlm().getProvider();

        if ("rule".equalsIgnoreCase(provider)) {
            return ruleAdvisor.advise(message, intent, wardrobe, history, pinnedClothingIds);
        }

        boolean wantDeepSeek = "deepseek".equalsIgnoreCase(provider) || aiProperties.isLlmReady();
        if (!wantDeepSeek) {
            // auto 模式且没配 key：这是正常的开发态，用 debug 就够，别把日志刷满
            log.debug("未配置大模型 key，使用规则引擎");
            return ruleAdvisor.advise(message, intent, wardrobe, history, pinnedClothingIds);
        }

        try {
            List<OutfitDraft> drafts = deepSeekAdvisor.advise(message, intent, wardrobe, history, pinnedClothingIds);
            log.info("推荐由 {} 产出，共 {} 套（上文 {} 条，指定单品 {} 件）", deepSeekAdvisor.name(),
                    drafts.size(), history == null ? 0 : history.size(),
                    pinnedClothingIds == null ? 0 : pinnedClothingIds.size());
            return drafts;
        } catch (Exception e) {
            // 强制模式下这是配置错误，用 ERROR；auto 模式下属于可接受的降级，用 WARN
            if ("deepseek".equalsIgnoreCase(provider)) {
                log.error("DeepSeek 推荐失败（provider=deepseek 强制模式），已降级为规则引擎", e);
            } else {
                log.warn("DeepSeek 推荐失败，已降级为规则引擎：{}", e.getMessage());
            }
            return ruleAdvisor.advise(message, intent, wardrobe, history, pinnedClothingIds);
        }
    }

    /** 当前实际生效的引擎名，写进 AI 回复文案和日志，便于确认走的是哪条路 */
    public String activeEngineName() {
        String provider = aiProperties.getLlm().getProvider();
        if ("rule".equalsIgnoreCase(provider)) {
            return "rule-based";
        }
        return aiProperties.isLlmReady() ? deepSeekAdvisor.name() : "rule-based";
    }

}
