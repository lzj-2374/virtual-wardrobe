package com.liuzhangjie.wardrobe.ai;

import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.ClothingTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规则引擎：不调用任何外部服务，纯靠打分从真实衣柜里挑。
 * <p>
 * 它的存在不是为了"替代大模型"，而是保证一件事：<b>没有 key、断网、额度用完，
 * 推荐功能依然能出结果</b>。答辩现场最怕的就是"网一断整个系统瘫掉"。
 * <p>
 * 打分维度只有四个，都是能解释的：
 * <ol>
 *   <li>季节匹配（权重最高）—— 冬天推短袖是硬伤，其他都还能解释</li>
 *   <li>厚薄匹配 —— 同样是冬装，羽绒服和薄风衣得分不同</li>
 *   <li>标签命中 —— 衣服上打的"场合/风格"标签和用户说的场景对上就加分</li>
 *   <li>中性色偏好 —— 没有配色模型的前提下，选中性色是最不容易翻车的策略</li>
 * </ol>
 * 组合方式：上装 × 下装做笛卡尔积，再按场景补外套/鞋/配饰，最后按总分排序取前三。
 * 之所以做笛卡尔积而不是只取每个位置的第一名，是为了让三套推荐真的不一样——
 * 否则用户看到的三张卡片上装永远是同一件。
 */
@Slf4j
@Component
public class RuleBasedOutfitAdvisor implements OutfitAdvisor {

    /** 一次最多给几套，和前端的展示上限一致 */
    private static final int MAX_OUTFITS = 3;

    /** 每个位置最多取前几名参与组合，取太多会让组合数爆炸而收益很小 */
    private static final int CANDIDATE_LIMIT = 3;

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public List<OutfitDraft> advise(String message, OutfitIntent intent, List<Clothing> wardrobe,
                                    List<String> history, List<Long> pinnedClothingIds) {
        // history 在这里用不上：规则引擎是纯打分排序，行为完全由 intent 决定。
        // 上文的作用已经在解析 intent 时体现过了（气温、场景都从历史里继承了过来），
        // 所以这里不需要再读一遍对话原文。参数保留是为了和大模型那条路共用同一个接口。
        if (wardrobe == null || wardrobe.isEmpty()) {
            return List.of();
        }

        Set<Long> pinnedIds = pinnedClothingIds == null ? Set.of() : new HashSet<>(pinnedClothingIds);

        // 1. 先按「位置」分组，同时给每件衣服打分
        Map<Integer, List<Scored>> byRole = groupAndScore(wardrobe, intent);

        // 1.5 用户指定了必选单品的话，把这些位置的候选池收窄到只剩它们
        byRole = restrictToPinned(byRole, pinnedIds);

        // 1.6 再把「这个季节根本穿不了」的整件剔出去。
        //     放在收窄之后：先保证用户点的东西不被别人挤掉，再谈合不合季节
        byRole = dropUnwearable(byRole, intent.season(), pinnedIds);

        // 被指定的单品分别属于哪些位置，后面用来放宽"外套只在冷天加""配饰只在约会加"这两道门
        Set<Integer> pinnedRoles = wardrobe.stream()
                .filter(c -> pinnedIds.contains(c.getId()))
                .map(WardrobeSupport::roleOf)
                .collect(Collectors.toSet());

        List<Scored> tops = byRole.getOrDefault(WardrobeSupport.ROLE_TOP, List.of());
        if (tops.isEmpty()) {
            // 一件上装都没有（或没有套装），凑不出搭配。返回空列表，由上层给出"衣柜还不够"的提示
            log.debug("衣柜里没有上装，规则引擎无法组搭配，userId 对应的衣物数={}", wardrobe.size());
            return List.of();
        }
        List<Scored> bottoms = byRole.getOrDefault(WardrobeSupport.ROLE_BOTTOM, List.of());

        // 一条下装都没有时，只有「本身成套」的套装/连衣裙能单穿。
        // 普通上装孤零零一件不能算一套搭配——真返回出去，用户看到的是"推荐了一件T恤"，
        // 还不如直接说衣柜不够。套装用 category=4 判定，它的 role 也是上装。
        if (bottoms.isEmpty()) {
            tops = tops.stream()
                    .filter(s -> Objects.equals(s.item().getCategory(), 4))
                    .collect(Collectors.toList());
            if (tops.isEmpty()) {
                log.debug("没有下装也没有套装，凑不成一套完整搭配");
                return List.of();
            }
        }
        List<Scored> outers = byRole.getOrDefault(WardrobeSupport.ROLE_OUTER, List.of());
        List<Scored> shoes = byRole.getOrDefault(WardrobeSupport.ROLE_SHOES, List.of());
        List<Scored> accessories = byRole.getOrDefault(WardrobeSupport.ROLE_ACCESSORY, List.of());

        // 2. 上装 × 下装做组合。下装为空时仍给一次机会（套装/连衣裙本身就是完整一套）
        int topCount = Math.min(CANDIDATE_LIMIT, tops.size());
        int bottomCount = Math.max(1, Math.min(CANDIDATE_LIMIT, bottoms.size()));
        List<Combo> combos = new ArrayList<>();
        for (int i = 0; i < topCount; i++) {
            for (int j = 0; j < bottomCount; j++) {
                Clothing top = tops.get(i).item();
                Clothing bottom = bottoms.isEmpty() ? null : bottoms.get(j).item();
                combos.add(buildCombo(top, bottom, outers, shoes, accessories, intent, i, pinnedRoles));
            }
        }

        // 3. 按总分排序，取前 MAX_OUTFITS 套，且同一组上装+下装只出现一次
        combos.sort(Comparator.comparingInt(Combo::score).reversed());
        Map<String, Combo> picked = new LinkedHashMap<>();
        for (Combo c : combos) {
            picked.putIfAbsent(c.key(), c);
            if (picked.size() >= MAX_OUTFITS) {
                break;
            }
        }

        // 4. 转成草稿
        List<OutfitDraft> drafts = new ArrayList<>();
        for (Combo combo : picked.values()) {
            drafts.add(toDraft(combo, intent));
        }
        log.debug("规则引擎产出 {} 套搭配，场景={}，季节={}", drafts.size(), intent.scene(), intent.season());
        return drafts;
    }

    // ===================== 打分 =====================

    /**
     * 给一件衣服打分。分数只用于横向比较，绝对值没有意义，所以不需要归一化。
     */
    private int score(Clothing c, OutfitIntent intent) {
        int score = 0;

        // 季节：匹配 +4，不匹配 -3。差值 7 分是刻意拉大的——
        // 冬天推荐短袖属于"一看就错"，比配色不好看严重得多
        if (WardrobeSupport.matchSeason(c, intent.season())) {
            score += 4;
        } else {
            score -= 3;
        }

        Integer thickness = c.getThickness();
        if (thickness != null) {
            // 用位与而不是 ==：气温解析出来的是"秋冬(12)""春夏(6)"这类组合值，
            // 写成 == WINTER（8）的话 12 永远不成立，厚薄加分整个失效
            if ((intent.season() & OutfitIntent.WINTER) != 0) {
                score += switch (thickness) {
                    case 3 -> 2;
                    case 1 -> -2;
                    default -> 0;
                };
            } else if ((intent.season() & OutfitIntent.SUMMER) != 0) {
                score += switch (thickness) {
                    case 1 -> 2;
                    case 3 -> -2;
                    default -> 0;
                };
            }
        }

        List<ClothingTag> tags = c.getTags();
        if (tags != null) {
            for (ClothingTag tag : tags) {
                if (tag.getTagValue() == null || tag.getTagType() == null) {
                    continue;
                }
                // tagType=2 是场合，命中用户说的场景加分最多：说明这件衣服本来就是为这个场合买的
                if (tag.getTagType() == 2 && tag.getTagValue().contains(intent.scene())) {
                    score += 3;
                }
                // tagType=1 是风格，命中用户的风格倾向（如"休闲"）加分
                if (tag.getTagType() == 1 && intent.styleHint() != null
                        && tag.getTagValue().contains(intent.styleHint())) {
                    score += 2;
                }
            }
        }

        if (WardrobeSupport.isNeutralColor(c.getColor())) {
            score += 1;
        }
        return score;
    }

    private Map<Integer, List<Scored>> groupAndScore(List<Clothing> wardrobe, OutfitIntent intent) {
        Map<Integer, List<Scored>> byRole = new LinkedHashMap<>();
        for (Clothing c : wardrobe) {
            int role = WardrobeSupport.roleOf(c);
            byRole.computeIfAbsent(role, k -> new ArrayList<>()).add(new Scored(c, score(c, intent)));
        }
        // 每个位置内部按分数倒序，这样后面按下标取就是取最好的那几个
        for (List<Scored> list : byRole.values()) {
            list.sort(Comparator.comparingInt(Scored::score).reversed());
        }
        return byRole;
    }

    /**
     * 把用户指定位置上的候选池收窄到只剩那几件。
     * <p>
     * 为什么是"剔除"而不是"加分"：只给必选品加分的话，上装循环仍然会走三件不同的上装，
     * 三套搭配变成"第一套用了你选的、另外两套没用"，跟「必须用这几件」的承诺不符；
     * 收窄候选池之后，上装只剩 1 件，变化自然由下装和鞋来提供，
     * 既能保证每套都用上必选单品，三套之间又不会长得一样。
     */
    private Map<Integer, List<Scored>> restrictToPinned(Map<Integer, List<Scored>> byRole, Set<Long> pinnedIds) {
        if (pinnedIds.isEmpty()) {
            return byRole;
        }
        Map<Integer, List<Scored>> restricted = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Scored>> e : byRole.entrySet()) {
            List<Scored> kept = e.getValue().stream()
                    .filter(s -> pinnedIds.contains(s.item().getId()))
                    .collect(Collectors.toList());
            // 这个位置有必选单品就只留它们；没有的话原样保留，别把别的位置也清空了
            restricted.put(e.getKey(), kept.isEmpty() ? e.getValue() : kept);
        }
        return restricted;
    }

    // ===================== 组合与文案 =====================

    /**
     * 把当前季节穿不了的单品从候选池里整件剔掉。
     * <p>
     * 为什么必须是"剔除"而不是"在 {@link #score} 里扣分"：扣分只在候选充足时有用。
     * 用户衣柜里往往只有一两条裤子，那条夏装短裤就算扣 3 分也还是唯一选项，
     * 于是 15℃ 的天气照样能配出「短裤 + 长袖 + 薄外套」——这类错误一眼就能看出，
     * 不能靠分数去"尽量避开"，只能不让它进场。
     * <p>
     * 剔除之后某个位置空了是正常结果（比如夏天过滤掉所有外套），
     * {@link #buildCombo} 会自然跳过这个位置，搭出件数更少的组合，而不是硬塞一件。
     * <p>
     * 用户点名的单品不参与剔除：那是他明确要看的，季节基准已经在
     * {@code AiServiceImpl} 里跟着它调整过了。
     */
    private Map<Integer, List<Scored>> dropUnwearable(Map<Integer, List<Scored>> byRole,
                                                     int season, Set<Long> pinnedIds) {
        Map<Integer, List<Scored>> filtered = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Scored>> e : byRole.entrySet()) {
            List<Scored> kept = e.getValue().stream()
                    .filter(s -> pinnedIds.contains(s.item().getId())
                            || WardrobeSupport.wearableIn(s.item(), season))
                    .collect(Collectors.toList());
            filtered.put(e.getKey(), kept);
        }
        return filtered;
    }

    /**
     * 用指定的一件上装、一件下装，配上外套/鞋/配饰，凑成一套。
     * <p>
     * {@code variant} 是上装的下标，拿它去错开外套和鞋的选择，
     * 让三套推荐里的鞋和外套也不一样，而不是三套共用同一双鞋。
     */
    private Combo buildCombo(Clothing top, Clothing bottom,
                             List<Scored> outers, List<Scored> shoes, List<Scored> accessories,
                             OutfitIntent intent, int variant, Set<Integer> pinnedRoles) {
        // 套装（连衣裙、西装套装）本身已经是完整一套，再配一条裤子就荒唐了。
        // 这个判断必须放在最前面，后面的 items 和文案都依赖它
        if (top.getCategory() != null && top.getCategory() == 4) {
            bottom = null;
        }

        List<Clothing> items = new ArrayList<>();
        items.add(top);
        if (bottom != null) {
            items.add(bottom);
        }

        // 外套只在偏冷的季节加：秋天/冬天不穿外套说不过去，夏天硬加一件反而是错的。
        // 这里也用位与——15℃ 会被解析成"春秋(5)"，写成 == AUTUMN 就漏掉了
        boolean coldEnough = (intent.season() & (OutfitIntent.AUTUMN | OutfitIntent.WINTER)) != 0;
        // 但用户点名要穿这件外套时，季节这道门不该拦他——他就是想看这件怎么搭
        boolean outerWanted = coldEnough || pinnedRoles.contains(WardrobeSupport.ROLE_OUTER);
        Clothing outer = null;
        if (!outers.isEmpty() && outerWanted) {
            outer = outers.get(Math.min(variant, outers.size() - 1)).item();
            items.add(outer);
        }

        Clothing shoe = null;
        if (!shoes.isEmpty()) {
            shoe = shoes.get(Math.min(variant, shoes.size() - 1)).item();
            items.add(shoe);
        }

        // 配饰只给约会/正式场合加。日常通勤塞一堆配饰反而显得刻意；
        // 同样是"用户点了就优先满足"
        boolean sceneWantsAccessory = "约会".equals(intent.scene()) || "正式".equals(intent.scene());
        Clothing accessory = null;
        if (!accessories.isEmpty()
                && (sceneWantsAccessory || pinnedRoles.contains(WardrobeSupport.ROLE_ACCESSORY))) {
            accessory = accessories.get(0).item();
            items.add(accessory);
        }

        int total = items.stream().mapToInt(c -> score(c, intent)).sum();
        // 配色奖励：主色与点缀色都是中性色时额外加分，这类组合几乎不会出错
        if (WardrobeSupport.isNeutralColor(top.getColor())
                && (bottom == null || WardrobeSupport.isNeutralColor(bottom.getColor()))) {
            total += 2;
        }

        List<OutfitDraft.Item> draftItems = new ArrayList<>();
        for (Clothing c : items) {
            draftItems.add(new OutfitDraft.Item(c.getId(), WardrobeSupport.roleOf(c)));
        }

        return new Combo(top, bottom, outer, shoe, accessory, draftItems, total, intent);
    }

    private OutfitDraft toDraft(Combo combo, OutfitIntent intent) {
        String name = "为你搭配：" + combo.intent().scene() + "·" + OutfitIntent.seasonName(intent.season());

        StringBuilder reason = new StringBuilder();
        reason.append("以「").append(combo.top().getName()).append("」打底");
        if (combo.bottom() != null) {
            reason.append("，下配「").append(combo.bottom().getName()).append("」");
        }
        if (combo.outer() != null) {
            reason.append("，外搭「").append(combo.outer().getName()).append("」");
        }
        if (combo.shoe() != null) {
            reason.append("，脚上「").append(combo.shoe().getName()).append("」");
        }
        reason.append("。整体偏").append(sceneTone(intent.scene()))
                .append("，适合").append(intent.scene()).append("场合。");
        if (intent.styleHint() != null) {
            reason.append("按你说的「").append(intent.styleHint()).append("」优先挑了版型宽松的单品。");
        }

        return new OutfitDraft(
                name,
                reason.toString(),
                buildColorNote(combo),
                intent.scene(),
                intent.season(),
                combo.items()
        );
    }

    /** 「主色：白 / 点缀：米」。点缀色优先取外套，其次下装，最后鞋 —— 从视觉面积从大到小 */
    private String buildColorNote(Combo combo) {
        String main = WardrobeSupport.colorOf(combo.top());
        Clothing accentSource = combo.outer() != null ? combo.outer()
                : combo.bottom() != null ? combo.bottom()
                : combo.shoe();
        if (accentSource == null) {
            return "主色：" + main;
        }
        String accent = WardrobeSupport.colorOf(accentSource);
        // 归一化之后再比：「白色」和「白」是同一个颜色，不该被当成点缀色写出重复的话
        if (WardrobeSupport.normalizeColor(accent).equals(WardrobeSupport.normalizeColor(main))
                || "无明确主色".equals(accent)) {
            return "主色：" + main + " / 同色系叠穿";
        }
        return "主色：" + main + " / 点缀：" + accent;
    }

    private String sceneTone(String scene) {
        return switch (scene) {
            case "正式" -> "克制、不出错";
            case "通勤" -> "干净利落";
            case "约会" -> "柔和一点、有细节";
            case "运动" -> "轻便好动";
            default -> "轻松随意";
        };
    }

    /** 一件衣服 + 它的分数。用 record 是因为它只是打分过程中的临时载体 */
    private record Scored(Clothing item, int score) {
    }

    /**
     * 一套候选搭配。字段存的是"选中的衣物引用"，不是 id，
     * 因为后面生成文案要读名字和颜色。
     */
    private record Combo(Clothing top, Clothing bottom, Clothing outer, Clothing shoe, Clothing accessory,
                         List<OutfitDraft.Item> items, int score, OutfitIntent intent) {

        /** 去重键：同一组上装+下装只保留分最高的那一套 */
        String key() {
            return top.getId() + "-" + (bottom == null ? "0" : bottom.getId());
        }
    }

}
