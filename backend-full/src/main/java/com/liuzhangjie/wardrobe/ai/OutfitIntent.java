package com.liuzhangjie.wardrobe.ai;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户那句话里读出来的「搭配意图」。
 * <p>
 * 用户不会说"请给我一套 scene=通勤、season=4 的搭配"，他说的是"明天面试穿什么"。
 * 这个类干的活就是把自然语言翻译成筛选维度：场景 + 季节 + 风格。
 * 规则引擎和大模型两条路都会用到它——大模型那条路也拿它做兜底：
 * 模型返回的 scene/season 缺字段时，用这里推出来的值补上。
 *
 * @param scene       场景：日常 / 通勤 / 约会 / 运动 / 正式
 * @param season      季节位标识：1 春 / 2 夏 / 4 秋 / 8 冬，15 = 四季
 * @param styleHint   风格倾向，如「休闲」；读不出来时为 null
 * @param temperature 用户提到的气温（℃，可以为负，如零下 3 度 = -3）；没说就是 null
 */
public record OutfitIntent(String scene, Integer season, String styleHint, Integer temperature) {

    /** 季节位标识：春 */
    public static final int SPRING = 1;
    /** 季节位标识：夏 */
    public static final int SUMMER = 2;
    /** 季节位标识：秋 */
    public static final int AUTUMN = 4;
    /** 季节位标识：冬 */
    public static final int WINTER = 8;
    /** 四季 */
    public static final int ALL_SEASON = 15;

    /**
     * 关键词表。用 LinkedHashMap 是因为要按插入顺序匹配——
     * 「面试」既像通勤又像正式，谁先命中就听谁的，顺序就是优先级。
     */
    private static final Map<String, String> SCENE_WORDS = new LinkedHashMap<>();
    private static final Map<String, Integer> SEASON_WORDS = new LinkedHashMap<>();

    /**
     * 气温的正则。能认「15度」「15℃」「15 °C」「零下3度」「-5度」这几种写法。
     * 第 1 组是负号（零下 / -），第 2 组是数字。
     */
    private static final Pattern TEMPERATURE =
            Pattern.compile("(零下\\s*|-\\s*)?(\\d{1,2})\\s*(?:度|℃|°c|°C)");

    static {
        // 场景词。特意把「面试」「答辩」归到正式而不是日常：
        // 这类场合对"别出错"的要求最高，推荐要偏保守，配饰宁少勿多
        SCENE_WORDS.put("答辩", "正式");
        SCENE_WORDS.put("婚礼", "正式");
        SCENE_WORDS.put("年会", "正式");
        SCENE_WORDS.put("典礼", "正式");
        SCENE_WORDS.put("商务", "正式");
        SCENE_WORDS.put("正式", "正式");
        SCENE_WORDS.put("面试", "通勤");
        SCENE_WORDS.put("上班", "通勤");
        SCENE_WORDS.put("通勤", "通勤");
        SCENE_WORDS.put("办公", "通勤");
        SCENE_WORDS.put("开会", "通勤");
        SCENE_WORDS.put("见客户", "通勤");
        SCENE_WORDS.put("约会", "约会");
        SCENE_WORDS.put("聚餐", "约会");
        SCENE_WORDS.put("逛街", "约会");
        SCENE_WORDS.put("看电影", "约会");
        SCENE_WORDS.put("运动", "运动");
        SCENE_WORDS.put("跑步", "运动");
        SCENE_WORDS.put("健身", "运动");
        SCENE_WORDS.put("打球", "运动");
        SCENE_WORDS.put("爬山", "运动");
        SCENE_WORDS.put("徒步", "运动");

        SEASON_WORDS.put("羽绒", WINTER);
        SEASON_WORDS.put("下雪", WINTER);
        SEASON_WORDS.put("降温", WINTER);
        SEASON_WORDS.put("冷", WINTER);
        SEASON_WORDS.put("冬天", WINTER);
        SEASON_WORDS.put("高温", SUMMER);
        SEASON_WORDS.put("短袖", SUMMER);
        SEASON_WORDS.put("凉快", SUMMER);
        SEASON_WORDS.put("热", SUMMER);
        SEASON_WORDS.put("夏天", SUMMER);
        SEASON_WORDS.put("风衣", AUTUMN);
        SEASON_WORDS.put("秋天", AUTUMN);
        SEASON_WORDS.put("回暖", SPRING);
        SEASON_WORDS.put("春天", SPRING);
    }

    /**
     * 只解析当前这一句，不带上文。
     * <p>
     * 没有历史可参考的调用方（比如单元测试）用这个；业务链路请走
     * {@link #parse(String, String)}，否则多轮追问会丢上文。
     */
    public static OutfitIntent parse(String message) {
        return parse(message, null);
    }

    /**
     * 带上下文解析：当前这句话里读不出来的信息，去最近几轮对话里找。
     * <p>
     * 为什么需要它——用户问完「今天 15 度穿啥」，紧接着问「那正式点的呢」。
     * 第二句里既没有温度也没有季节词，只解析这一句的话，季节只能按月猜（11 月 = 秋），
     * 上文的 15 度就白说了。带上历史，第二句能沿用第一句的气温和场合。
     *
     * @param message 当前这句话
     * @param history 最近的对话原文（按时间正序拼好的文本），可为 null
     */
    public static OutfitIntent parse(String message, String history) {
        String text = message == null ? "" : message;
        String context = history == null ? "" : history;

        // 场景：这句话说了就听这句，没说再沿用上文
        String scene = matchScene(text);
        if (scene == null) {
            scene = matchScene(context);
        }
        if (scene == null) {
            scene = "日常";
        }

        // 气温只用来判断厚薄和写进提示词，本身不参与筛选
        Integer temperature = matchTemperature(text);
        if (temperature == null) {
            temperature = matchTemperature(context);
        }

        // 季节的优先级，从强到弱：这句的季节词 > 这句的气温 > 上文的季节词 > 上文的气温 > 当前月份
        Integer season = matchSeasonWord(text);
        if (season == null) {
            Integer temp = matchTemperature(text);
            season = temp == null ? null : seasonByTemperature(temp);
        }
        if (season == null) {
            season = matchSeasonWord(context);
        }
        if (season == null) {
            Integer temp = matchTemperature(context);
            season = temp == null ? null : seasonByTemperature(temp);
        }
        if (season == null) {
            season = seasonByMonth(LocalDate.now().getMonthValue());
        }

        String styleHint = matchStyle(text);
        if (styleHint == null) {
            styleHint = matchStyle(context);
        }

        return new OutfitIntent(scene, season, styleHint, temperature);
    }

    // ===================== 各个维度的匹配 =====================

    private static String matchScene(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : SCENE_WORDS.entrySet()) {
            if (text.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Integer matchSeasonWord(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Integer> e : SEASON_WORDS.entrySet()) {
            if (text.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String matchStyle(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.contains("舒服") || text.contains("宽松") || text.contains("休闲") || text.contains("随意")) {
            return "休闲";
        }
        if (text.contains("显瘦") || text.contains("好看") || text.contains("气质")) {
            return "气质";
        }
        return null;
    }

    /**
     * 从文本里抠出气温。取第一个能匹配上的数字——
     * 「早上 8 度中午 20 度」这种一句话两个温度的情况很少见，
     * 真出现就按第一个算，不值得为它写一套区间逻辑。
     */
    private static Integer matchTemperature(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher m = TEMPERATURE.matcher(text);
        if (!m.find()) {
            return null;
        }
        int value = Integer.parseInt(m.group(2));
        return m.group(1) != null ? -value : value;
    }

    /**
     * 气温 → 季节位标识。
     * <p>
     * 返回值刻意用「多个季节相加」而不是单个季节：15℃ 这种天气春秋都能穿，
     * 只给一个季节会把一半本该合适的衣服筛掉。分档参考的是体感，不是气象学定义。
     */
    private static int seasonByTemperature(int temp) {
        if (temp <= 5) {
            return WINTER;              // 5℃ 以下：正经冬天
        }
        if (temp <= 14) {
            return AUTUMN | WINTER;     // 6~14℃：秋冬
        }
        if (temp <= 22) {
            return SPRING | AUTUMN;     // 15~22℃：春秋，最尴尬的换季温度
        }
        if (temp <= 28) {
            return SPRING | SUMMER;     // 23~28℃：春夏
        }
        return SUMMER;                  // 29℃ 以上：盛夏
    }

    /** 月份 → 季节位标识。三到五月算春，依此类推，没有"四季"这一说——总有个当下 */
    private static int seasonByMonth(int month) {
        return switch (month) {
            case 3, 4, 5 -> SPRING;
            case 6, 7, 8 -> SUMMER;
            case 9, 10, 11 -> AUTUMN;
            default -> WINTER;
        };
    }

    /**
     * 位掩码 → 中文名。
     * <p>
     * 必须支持组合值：温度解析出来的是「春秋(5)」「秋冬(12)」这类多季节，
     * 早先这里只写了 switch 1/2/4/8，组合值全落到 default 返回"四季"，
     * 结果 15℃ 会在提示词里被写成"季节=四季"，模型照样乱推——温度等于没解析。
     */
    public static String seasonName(int season) {
        if (season == ALL_SEASON || season <= 0) {
            return "四季";
        }
        StringBuilder sb = new StringBuilder();
        if ((season & SPRING) != 0) {
            sb.append('春');
        }
        if ((season & SUMMER) != 0) {
            sb.append('夏');
        }
        if ((season & AUTUMN) != 0) {
            sb.append('秋');
        }
        if ((season & WINTER) != 0) {
            sb.append('冬');
        }
        return sb.isEmpty() ? "四季" : sb.toString();
    }

}
