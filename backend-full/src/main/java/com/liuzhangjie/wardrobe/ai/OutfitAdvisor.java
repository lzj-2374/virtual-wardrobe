package com.liuzhangjie.wardrobe.ai;

import com.liuzhangjie.wardrobe.entity.Clothing;

import java.util.List;

/**
 * 穿搭推荐引擎的接口。两个实现，靠 {@link OutfitAdvisorRouter} 在运行时二选一：
 * <ul>
 *   <li>{@link DeepSeekOutfitAdvisor} —— 配了大模型 key 时走这条，理解力强，能听懂「明天答辩」这种话</li>
 *   <li>{@link RuleBasedOutfitAdvisor} —— 没配 key 或调用失败时兜底，纯本地计算，永远可用</li>
 * </ul>
 * 抽象成接口而不是写成一堆 if-else，是因为加第三个实现（比如换个模型厂商）
 * 时只需要新增一个类，不用碰调用方，也不用回归测试另外两条路径。
 */
public interface OutfitAdvisor {

    /**
     * 根据用户这句话和衣柜内容，给出 1~3 套搭配。
     *
     * @param message  用户原话，如「明天面试穿什么」
     * @param intent   从原话（含上文）解析出的场景、季节、气温、风格
     * @param wardrobe 当前用户的全部衣物（已带 tags），引擎只能从这里挑，不能编造
     * @param history  最近几轮对话，按时间正序，形如 {@code ["用户：今天15度穿啥", "AI：为你推荐了3套搭配"]}。
     *                 用来理解「那正式点的呢」这类省略句，以及避免连续两轮推荐同一套搭配。
     *                 可以为空列表，实现方必须能处理这种情况
     * @param pinnedClothingIds 用户点「+」指定的、必须用上的单品 id（最多 3 个）。
     *                 空列表表示没有指定。实现方要保证这些单品真的出现在结果里——
     *                 大模型靠提示词约束，规则引擎直接把候选池收窄到它们
     * @return 搭配草稿列表；衣柜不足以成一套时返回空列表，调用方按"没配出来"处理
     */
    List<OutfitDraft> advise(String message, OutfitIntent intent, List<Clothing> wardrobe,
                             List<String> history, List<Long> pinnedClothingIds);

    /** 引擎名字，写进日志用，排查时能看出这次推荐是谁给的 */
    String name();

}
