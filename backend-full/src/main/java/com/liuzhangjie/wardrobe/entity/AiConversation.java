package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话表。
 * <p>
 * 一次「打开 AI 助手 → 连着问几句」对应这里的一条记录。
 * 这张表本身不存对话内容，只存「这是谁的一段对话、叫什么名字」，
 * 具体每句话在 {@link AiMessage} 里。
 * <p>
 * 为什么不像 user / clothing / outfit 那样加 {@code deleted}：
 * 会话是临时性的中间产物，删掉就该从列表里消失，留一个逻辑删除的空壳没有意义。
 * 全项目只有 user / clothing / outfit 三张主体表做逻辑删除。
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 id，所有查询都要带上它做数据隔离 */
    private Long userId;

    /** 会话标题，取首句用户发言的前 32 个字，仅用于会话列表展示 */
    private String title;

    private LocalDateTime createTime;

    /** 每发一条消息都会刷新它，会话列表按这个字段倒序，最近聊过的排最前 */
    private LocalDateTime updateTime;

}
