package com.liuzhangjie.wardrobe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.entity.AiChatDTO;
import com.liuzhangjie.wardrobe.entity.AiChatVO;
import com.liuzhangjie.wardrobe.entity.AiConversation;
import com.liuzhangjie.wardrobe.entity.AiImageRequestDTO;
import com.liuzhangjie.wardrobe.entity.AiImageTaskVO;
import com.liuzhangjie.wardrobe.entity.AiMessage;
import com.liuzhangjie.wardrobe.entity.AiRecommendationVO;
import com.liuzhangjie.wardrobe.entity.AiToOutfitDTO;
import com.liuzhangjie.wardrobe.entity.AiToOutfitVO;

import java.util.List;

/**
 * AI 穿搭模块的业务接口。
 * <p>
 * 分成三块：对话推荐（chat / 会话 / 消息）、异步生图（generate / status）、
 * 推荐沉淀（toOutfit）。三块共用 {@code ai_recommendation} 这张表，
 * 所以生图和沉淀都要先确认"这条推荐确实是当前用户的"。
 */
public interface AiService {

    /**
     * 发起一轮对话，返回 1~3 套推荐。
     * <p>
     * 这个方法会写两张表：{@code ai_message}（一问一答两条）和
     * {@code ai_recommendation}（每套推荐一行 + 对应的单品行）。
     */
    AiChatVO chat(AiChatDTO dto);

    /** 会话分页，按最近活跃时间倒序 */
    Page<AiConversation> conversationPage(Integer page, Integer size);

    /** 某个会话下的消息分页，按时间正序（聊天记录从早到晚读） */
    Page<AiMessage> messages(Long conversationId, Integer page, Integer size);

    /**
     * 提交生图任务。
     * <p>
     * 关键点：这个方法<b>立刻返回</b>，不做任何生成动作。
     * 它只建任务、改状态、把消息丢进 RabbitMQ，真正的图由消费者在后台画。
     */
    AiImageTaskVO generateImage(AiImageRequestDTO dto);

    /** 查询生图任务状态，前端每秒轮询一次 */
    AiImageTaskVO imageStatus(String taskId);

    /**
     * 历史效果图：当前用户所有「已经生成成功」的推荐，按最近更新倒序。
     * <p>
     * 只返回出过图的（{@code image_status=2} 且 {@code image_url} 非空），
     * 生成中和失败的没有地址，放进来只会是一格空白。
     *
     * @param limit 返回条数，null 或 ≤0 用默认值，超过上限会被截断
     */
    List<AiRecommendationVO> imageHistory(Integer limit);

    /**
     * 把推荐结果「放入我的搭配」。
     * <p>
     * 幂等：同一个推荐 id 重复调用，永远返回同一个 outfitId，不会在搭配列表里堆重复项。
     */
    AiToOutfitVO toOutfit(Long recommendationId, AiToOutfitDTO dto);

}
