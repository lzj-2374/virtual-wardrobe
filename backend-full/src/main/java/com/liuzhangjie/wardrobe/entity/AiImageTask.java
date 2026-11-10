package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生图任务表。
 * <p>
 * 这是「异步」这件事落到数据库上的凭证：HTTP 接口只负责建一条任务并投递消息，
 * 真实生图由 RabbitMQ 消费者在另一个线程里做，完成后回来改这一行。
 * <p>
 * {@link #taskId} 是对外暴露的字符串任务号（形如 {@code task_1731031200000_7}），
 * 不直接用自增主键：自增 id 是连续的，前端改个数字就能查到别人的任务；
 * 带随机后缀的字符串既防遍历，也能在日志里一眼看出是哪一次。
 */
@Data
@TableName("ai_image_task")
public class AiImageTask {

    /** 未生成 */
    public static final int STATUS_NONE = 0;

    /** 生成中 */
    public static final int STATUS_RUNNING = 1;

    /** 成功 */
    public static final int STATUS_SUCCESS = 2;

    /** 失败 */
    public static final int STATUS_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外任务号，唯一索引 */
    private String taskId;

    private Long userId;

    /** 关联的推荐 id，生图内容由这条推荐的单品决定 */
    private Long recommendationId;

    /** 生图提示词，落库便于事后复现当时到底让模型画了什么 */
    private String prompt;

    /** 结果图地址（OSS） */
    private String imageUrl;

    /** 0 未生成 / 1 生成中 / 2 成功 / 3 失败 */
    private Integer status;

    /** 已重试次数。超过上限直接置失败，避免消费者无限重投把额度刷光 */
    private Integer retryCount;

    /** 失败原因，只写一句人话，不塞整段堆栈（堆栈进日志） */
    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
