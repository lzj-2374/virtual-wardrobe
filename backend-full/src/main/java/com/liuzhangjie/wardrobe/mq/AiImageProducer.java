package com.liuzhangjie.wardrobe.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liuzhangjie.wardrobe.config.RabbitConfig;
import com.liuzhangjie.wardrobe.entity.AiImageTask;
import com.liuzhangjie.wardrobe.mapper.AiImageTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 生图任务的生产者：把任务 id 丢进队列，然后立刻返回。
 * <p>
 * 生产者这一侧最容易被忽略的问题是「以为自己发出去了，其实没有」。两种失败：
 * <ol>
 *   <li><b>消息没到交换机</b>（broker 挂了、vhost 不对、权限不足）——
 *       靠 ConfirmCallback 发现，{@code ack=false}</li>
 *   <li><b>到了交换机但没有队列接</b>（路由键写错、队列没声明）——
 *       这种情况下 confirm 仍然是 ack！所以必须再靠 ReturnCallback 兜住</li>
 * </ol>
 * 两个回调都必须接。只接 confirm 的话，路由键打错一个字，消息就静默消失了，
 * 用户界面一直转圈，日志里干干净净。
 * <p>
 * 两种失败都要把任务置为失败（status=3），否则前端会一直轮询"生成中"。
 */
@Slf4j
@Component
public class AiImageProducer {

    /** 消息头里带一份 taskId。ReturnCallback 拿不到 CorrelationData，只能从消息头反查 */
    private static final String HEADER_TASK_ID = "x-task-id";

    private final RabbitTemplate rabbitTemplate;
    private final AiImageTaskMapper imageTaskMapper;

    public AiImageProducer(RabbitTemplate rabbitTemplate, AiImageTaskMapper imageTaskMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.imageTaskMapper = imageTaskMapper;
    }

    /**
     * 注册两个回调。放在 @PostConstruct 里是因为 RabbitTemplate 是单例，
     * 回调也是全局的——写在这里等于"这个应用发出去的所有消息都受这两道检查"。
     */
    @PostConstruct
    void registerCallbacks() {
        // ① 到达交换机了没
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                return;
            }
            String taskId = correlationData != null ? correlationData.getId() : null;
            log.error("生图消息未到达交换机，taskId={}，原因={}", taskId, cause);
            if (taskId != null) {
                imageTaskMapper.updateStatusIfNotSuccess(taskId, AiImageTask.STATUS_FAILED, null,
                        "消息投递失败：" + (cause == null ? "未知原因" : cause));
            }
        });

        // ② 到了交换机，但有没有队列接住
        rabbitTemplate.setReturnsCallback(returned -> {
            Object taskId = returned.getMessage().getMessageProperties().getHeader(HEADER_TASK_ID);
            log.error("生图消息没有队列接收，已被退回。exchange={}，routingKey={}，replyText={}，taskId={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText(), taskId);
            if (taskId != null) {
                imageTaskMapper.updateStatusIfNotSuccess(taskId.toString(), AiImageTask.STATUS_FAILED, null,
                        "消息无法路由到生图队列：" + returned.getReplyText());
            }
        });
    }

    /**
     * 投递一条生图任务。这个方法不做任何业务判断，只负责发出去——
     * 任务状态在调用前已经由 Service 置成"生成中"了。
     */
    public void send(AiImageMessage message) {
        CorrelationData correlationData = new CorrelationData(message.getTaskId());

        // 把 taskId 塞进消息头，ReturnCallback 才有办法反查是哪条任务被退回了
        MessagePostProcessor addHeader = msg -> {
            msg.getMessageProperties().setHeader(HEADER_TASK_ID, message.getTaskId());
            return msg;
        };

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.IMAGE_ROUTING_KEY,
                message,
                addHeader,
                correlationData);

        // 异步确认，这里只能打日志；真正的结果在 confirmCallback 里
        correlationData.getFuture().whenComplete((confirm, ex) -> {
            if (ex != null) {
                log.error("等待生图消息确认时出错，taskId={}", message.getTaskId(), ex);
            } else if (confirm != null && !confirm.isAck()) {
                log.error("生图消息被 broker 拒收，taskId={}，原因={}", message.getTaskId(), confirm.getReason());
            }
        });

        log.info("生图任务已投递，taskId={}，recommendationId={}，userId={}",
                message.getTaskId(), message.getRecommendationId(), message.getUserId());
    }

    /** 兜底查询：任务当前是不是还在"生成中"，用于 Service 判断要不要重复投递 */
    public AiImageTask findByTaskId(String taskId) {
        return imageTaskMapper.selectOne(new LambdaQueryWrapper<AiImageTask>()
                .eq(AiImageTask::getTaskId, taskId));
    }

}
