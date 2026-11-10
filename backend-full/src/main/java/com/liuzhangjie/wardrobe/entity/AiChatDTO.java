package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

import java.util.List;

/**
 * 发起 AI 对话的入参，对应 {@code POST /api/ai/chat}。
 * <p>
 * 只有两个字段，和前端的 payload 一一对应：
 * <pre>
 * { "message": "明天面试穿什么？", "conversationId": 99 }
 * </pre>
 * <b>故意不放的字段：</b>{@code userId}。用户身份一律从 token 取，
 * 让前端传就等于允许替别人发起对话，推荐结果会写进别人的账号里。
 */
@Data
public class AiChatDTO {

    /** 用户这一句提问，走到引擎里当需求描述用 */
    private String message;

    /**
     * 会话 id，可为空。
     * <p>
     * 为空或指向一条不存在/不属于当前用户的会话时，服务端**新建**一个会话并把真实 id 返回，
     * 前端下一轮拿这个 id 继续问即可。这样前端不必先调一个"创建会话"接口，
     * 也就不会出现"用户点开助手但一句话没说，库里先多一条空会话"的脏数据。
     */
    private Long conversationId;

    /**
     * 本轮「必须用上」的单品 id，来自输入框那个「+」按钮（从衣柜里挑几件）。
     * <p>
     * 最多 3 件：再多就不是「指定」而是「整套都定死了」，那还不如直接去编辑搭配。
     * 服务端会拿它跟该用户的衣柜求交集，传别人的衣物 id 会被静默丢掉，
     * 不会因为一个非法 id 就让整轮对话失败。
     */
    private List<Long> clothingIds;

}
