package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息表。
 * <p>
 * 会话里的一句话。role 区分是用户说的还是 AI 回的。
 * <p>
 * 一轮对话可能产出多条推荐结果，这里只在 AI 那条消息上用
 * {@code recommendationId} 锚定**第一条**推荐，剩下的靠 conversation_id 反查。
 * 这样设计的原因：消息表不该为了"一对多"再裂一张关联表，
 * 而前端展示时本来就是按会话整体拉取，用不着按消息精确回溯每一条推荐。
 */
@Data
@TableName("ai_message")
public class AiMessage {

    /** 消息角色：用户 */
    public static final int ROLE_USER = 1;

    /** 消息角色：AI */
    public static final int ROLE_AI = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 1 用户 / 2 AI */
    private Integer role;

    /** 消息正文。用户消息是原始提问，AI 消息是一句概括性的回复 */
    private String content;

    /** 关联的推荐结果 id，用户消息恒为空 */
    private Long recommendationId;

    private LocalDateTime createTime;

}
