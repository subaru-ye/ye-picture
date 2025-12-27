package com.ye.yepicturebackend.config;

import com.ye.yepicturebackend.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 全局配置
 * - 声明 Exchange / Queue / Binding
 * - 配置 JSON 消息转换器
 * - 设置死信队列（DLQ）
 */
@Configuration
public class RabbitMQConfig {

    // 1. 消息转换器：使用 JSON 而非 Java 序列化
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 2. 主交换机（Direct 类型）
    @Bean
    public DirectExchange reviewNoticeExchange() {
        return new DirectExchange(RabbitMQConstant.REVIEW_NOTICE_EXCHANGE, true, false);
    }

    // 3. 死信交换机
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(RabbitMQConstant.DLX_EXCHANGE, true, false);
    }

    // 4. 死信队列
    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(RabbitMQConstant.DLX_QUEUE).build();
    }

    // 5. 绑定死信队列到死信交换机
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(RabbitMQConstant.DLX_ROUTING_KEY);
    }

    // 6. 主队列（带死信配置）
    @Bean
    public Queue reviewNoticeQueue() {
        Map<String, Object> args = new HashMap<>();

        // 指定死信去向
        args.put("x-dead-letter-exchange", RabbitMQConstant.DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", RabbitMQConstant.DLX_ROUTING_KEY);

        // 🔹 可选：设置消息 TTL（单位：毫秒）
        // args.put("x-message-ttl", 30000); // 30秒未消费自动进死信

        // 🔹 可选：设置队列最大长度（防堆积）
        // args.put("x-max-length", 1000);

        return QueueBuilder.durable(RabbitMQConstant.REVIEW_NOTICE_QUEUE)
                .withArguments(args)
                .build();
    }

    // 7. 绑定主队列到主交换机
    @Bean
    public Binding reviewNoticeBinding() {
        return BindingBuilder.bind(reviewNoticeQueue())
                .to(reviewNoticeExchange())
                .with(RabbitMQConstant.REVIEW_NOTICE_ROUTING_KEY);
    }
}