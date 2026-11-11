package com.liuzhangjie.wardrobe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话的返回体，对应 {@code POST /api/ai/chat}。
 * <p>
 * <pre>
 * {
 *   "conversationId": 99,
 *   "reply": "为你推荐了 2 套搭配，已按你指定的 1 件单品来搭",
 *   "recommendations": [ { ... }, { ... } ]
 * }
 * </pre>
 * 前端拿到 {@code conversationId} 后会存下来，下一句话带着它继续问；
 * {@code recommendations} 直接渲染成聊天气泡里的推荐卡片，空数组表示这次没配出来
 * （衣柜里缺某个品类时是正常情况，不是错误）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatVO {

    /** 本轮所属会话 id。前端必须保存，下一轮带回来才能接上同一个会话 */
    private Long conversationId;

    /**
     * 本轮 AI 要说的话，直接渲染成气泡里的正文。
     * <p>
     * 以前没有这个字段，前端只好写死一句「为你推荐以下搭配：」，后端算出来的
     * 「已按你指定的 N 件单品来搭」「衣柜里当前季节能穿的只有 1 件上装」这些解释
     * 全部只进了数据库，用户在聊天时根本看不见——于是「我明明选了那条裤子」这种
     * 误会就没法当场解释清楚。文案的生成逻辑在服务端，就必须由服务端带回来。
     */
    private String reply;

    /** 本轮推荐结果，0~3 套 */
    private List<AiRecommendationVO> recommendations = new ArrayList<>();

}
