package com.liuzhangjie.wardrobe.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生图队列里的消息体。
 * <p>
 * 只带几个 id 和一个 gender，不带任何衣物数据。原因：
 * <ul>
 *   <li>消息要尽量小。把整套衣物塞进去，消息体可能几 KB，队列里堆几千条就是几十 MB</li>
 *   <li>衣物可能在排队期间被改（换图、改名）。消费时按 id 重新查，拿到的一定是最新状态；
 *       消息里存快照反而会生成一张"用旧数据画出来的图"</li>
 * </ul>
 * {@code userId} 冗余在这里不是为了鉴权（消费时不走 Security），
 * 而是为了让日志能直接定位"是谁的任务"，不用再反查一次库。
 * <p>
 * {@code gender} 是唯一一个"快照式"字段，因为它不是衣物属性、不会在排队期间变，
 * 而且它决定提示词里写"男性模特"还是"女性模特"——提示词在消费时才拼，
 * 所以必须在投递时就定下来。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiImageMessage {

    /** 对外任务号，也是消费端定位 ai_image_task 的唯一依据 */
    private String taskId;

    private Long recommendationId;

    private Long userId;

    /** 模特性别：1 男 / 2 女 / null 未知（交给模型自己定）。取的是"对话里说过的 > 个人资料" */
    private Integer gender;

}
