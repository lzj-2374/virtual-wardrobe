package com.liuzhangjie.wardrobe.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明：AI 生图用的交换机、队列、死信链路。
 * <p>
 * 为什么要有交换机：现在的 {@code /api/ai/image/generate} 只服务生图一件事，
 * 看起来直接往队列名投递就够了。但生图之后大概率还会加"失败重试""批量预热"这类队列，
 * 到时候没有交换机就得改生产者代码。多声明一个 Direct 交换机几乎没有成本，
 * 换来的是以后加队列只改这里。
 * <p>
 * 拓扑长这样：
 * <pre>
 *   生产者 ──routingKey=ai.image.generate──> wardrobe.ai.exchange
 *                                              │
 *                                              ▼
 *                                     wardrobe.ai.image.queue
 *                                              │ 消费失败且重试用尽
 *                                              ▼
 *                                       wardrobe.ai.dlx (DLX)
 *                                              │
 *                                              ▼
 *                                    wardrobe.ai.image.dead
 * </pre>
 * 和 demo 项目里踩过的坑一样：队列参数一旦写进 broker 就固定了，
 * 以后想改 {@code x-dead-letter-exchange} 必须先删掉旧队列再启动。
 */
@Configuration
public class RabbitConfig {

    /** 业务交换机 */
    public static final String EXCHANGE = "wardrobe.ai.exchange";

    /** 生图队列 */
    public static final String IMAGE_QUEUE = "wardrobe.ai.image.queue";

    /** 生图路由键，生产者投递时用它 */
    public static final String IMAGE_ROUTING_KEY = "ai.image.generate";

    /** 死信交换机 */
    public static final String DLX = "wardrobe.ai.dlx";

    /** 死信队列：重试用尽的消息落到这里，人工排查后可以捞出来重发 */
    public static final String DEAD_QUEUE = "wardrobe.ai.image.dead";

    /** 死信路由键 */
    public static final String DEAD_ROUTING_KEY = "ai.image.dead";

    // ===================== 业务链路 =====================

    @Bean
    public DirectExchange aiExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * 生图队列。
     * <p>
     * 三个参数各自的作用：
     * <ul>
     *   <li>{@code durable(true)}：broker 重启后队列还在，不然一重启消息全丢</li>
     *   <li>{@code deadLetterExchange}：重试用尽后往哪送。不配的话消息会被直接丢弃，
     *       用户界面一直转圈、后台什么都查不到</li>
     *   <li>{@code deadLetterRoutingKey}：送到死信交换机后按什么路由键投递</li>
     * </ul>
     */
    @Bean
    public Queue aiImageQueue() {
        return QueueBuilder.durable(IMAGE_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding aiImageBinding() {
        return BindingBuilder.bind(aiImageQueue()).to(aiExchange()).with(IMAGE_ROUTING_KEY);
    }

    // ===================== 死信链路 =====================

    @Bean
    public DirectExchange aiDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue aiDeadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    public Binding aiDeadBinding() {
        return BindingBuilder.bind(aiDeadQueue()).to(aiDlx()).with(DEAD_ROUTING_KEY);
    }

    // ===================== 序列化 =====================

    /**
     * 消息体转 JSON。
     * <p>
     * 不配这个，Spring 会用 JDK 原生序列化：管理台里看到一坨乱码，
     * 而且生产者和消费者的 DTO 必须字节级完全一致，改个字段名就反序列化失败。
     * <p>
     * 只需要写成 {@code @Bean}：Spring Boot 会自动把容器里唯一的 MessageConverter
     * 同时装进 RabbitTemplate 和监听容器，生产者消费者两边都不用显式引用它。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
