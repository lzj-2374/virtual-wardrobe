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

    /** 本轮推荐结果，0~3 套 */
    private List<AiRecommendationVO> recommendations = new ArrayList<>();

}
