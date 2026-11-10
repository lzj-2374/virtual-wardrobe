package com.liuzhangjie.wardrobe.ai;

import com.liuzhangjie.wardrobe.entity.Clothing;

import java.util.List;
import java.util.Set;

/**
 * 衣柜侧的小工具：把一件衣物翻译成「它在搭配里算哪个位置」，以及一些颜色常识。
 * <p>
 * 单独抽出来是因为这套规则有两条路在用——规则引擎要用它分组，
 * 大模型返回结果后也要用它校验（模型可能把一条裤子标成 role=1，得纠正）。
 */
public final class WardrobeSupport {

    /** 位置：上装 */
    public static final int ROLE_TOP = 1;
    /** 位置：下装 */
    public static final int ROLE_BOTTOM = 2;
    /** 位置：外套 */
    public static final int ROLE_OUTER = 3;
    /** 位置：鞋 */
    public static final int ROLE_SHOES = 4;
    /** 位置：配饰 */
    public static final int ROLE_ACCESSORY = 5;

    /**
     * 中性色。判断依据不是审美而是「跟谁搭都不打架」——
     * 推荐算法在没有配色模型的情况下，优先选中性色是最不容易翻车的策略。
     */
    private static final Set<String> NEUTRAL_COLORS =
            Set.of("白", "黑", "灰", "米", "卡其", "藏蓝", "牛仔", "驼", "杏", "咖", "棕", "银", "奶油");

    /** 判定一双鞋靠的是名字里的这些字，因为 category=5 把鞋和包混在一起了 */
    private static final List<String> SHOE_WORDS =
            List.of("鞋", "靴", "板鞋", "球鞋", "帆布", "皮鞋", "凉鞋", "拖鞋", "乐福");

    private static final String[] ROLE_LABELS = {"", "上装", "下装", "外套", "鞋", "配饰"};

    private WardrobeSupport() {
    }

    /**
     * 一件衣物在搭配里占哪个位置。
     * <p>
     * 套装（category=4）被当成上装处理，因为 {@code outfit_item} 上有
     * {@code uk_outfit_role} 唯一索引，一套搭配里同一个位置只能有一件。
     * 把连衣裙拆成"上装+下装"反而会占掉两个位置，跟真实穿衣逻辑也对不上。
     */
    public static int roleOf(Clothing c) {
        Integer category = c.getCategory();
        if (category == null) {
            return ROLE_ACCESSORY;
        }
        return switch (category) {
            case 1 -> ROLE_TOP;
            case 2 -> ROLE_BOTTOM;
            case 3 -> ROLE_OUTER;
            case 4 -> ROLE_TOP;          // 套装/连衣裙占上装位
            case 5 -> isShoes(c) ? ROLE_SHOES : ROLE_ACCESSORY;
            default -> ROLE_ACCESSORY;
        };
    }

    public static boolean isShoes(Clothing c) {
        String haystack = (nullToEmpty(c.getSubCategory()) + nullToEmpty(c.getName()));
        return SHOE_WORDS.stream().anyMatch(haystack::contains);
    }

    public static String roleLabel(Integer role) {
        if (role == null || role < 1 || role >= ROLE_LABELS.length) {
            return "单品";
        }
        return ROLE_LABELS[role];
    }

    /** 是不是中性色。颜色字段是自由文本，取包含判断而不是相等 */
    public static boolean isNeutralColor(String color) {
        if (color == null || color.isBlank()) {
            return false;
        }
        return NEUTRAL_COLORS.stream().anyMatch(color::contains);
    }

    /**
     * 这件衣服适不适合这个季节。
     * <p>
     * season 是位标识：{@code 9 = 春(1) + 冬(8)}，所以要按位与，不能直接相等比较。
     * 15（四季）按位与任何值都非 0，天然算通过。
     */
    public static boolean matchSeason(Clothing c, int season) {
        Integer s = c.getSeason();
        if (s == null) {
            return true;
        }
        return (s & season) != 0;
    }

    /** 读出来一件衣服的颜色，用于拼「主色：白 / 点缀：米」这类说明 */
    public static String colorOf(Clothing c) {
        return c.getColor() == null || c.getColor().isBlank() ? "无明确主色" : c.getColor();
    }

    /**
     * 颜色归一化：把「白色」和「白」当成同一个颜色。
     * <p>
     * 用户的颜色字段是自由文本，同一种颜色可能被写成「白」「白色」「纯白」「米白」。
     * 比较前统一去掉末尾的「色」字，能挡掉最常见的一种不一致，
     * 免得配色说明写出「主色：白色 / 点缀：白」这种明显重复的话。
     */
    public static String normalizeColor(String color) {
        if (color == null) {
            return "";
        }
        String c = color.trim();
        while (c.endsWith("色") && c.length() > 1) {
            c = c.substring(0, c.length() - 1);
        }
        return c;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

}
