package com.ye.yepicturebackend.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * Disruptor 配置类：为图片编辑事件提供高性能异步处理管道
 * <p>
 * 本类负责创建并初始化一个基于 LMAX Disruptor 的环形缓冲区（RingBuffer），
 * 用于解耦 WebSocket 消息接收（生产者）与业务逻辑处理（消费者），
 * 实现高吞吐、低延迟的实时协作编辑能力。
 * <p>
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    /**
     * 创建并启动 Disruptor 实例，作为 Spring 容器中的 Bean 管理
     * <p>
     * ⚠️ 注意：Disruptor 是有状态的资源，需确保应用关闭时正确 shutdown（Spring 会自动管理）。
     *
     * @return 配置好的 Disruptor 实例，泛型为 {@link PictureEditEvent}
     */
    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 1. RingBuffer 大小：必须是 2 的幂（Disruptor 要求）
        // 256K = 262,144，可缓存约 26 万条未处理事件
        // 若系统 QPS 极高或处理慢，可能丢事件；可根据压测调整
        int bufferSize = 1024 * 256; // 262,144

        // 2. 创建 Disruptor 实例
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                // EventFactory：用于预分配 RingBuffer 中的事件对象（避免 GC）
                PictureEditEvent::new,
                // RingBuffer 大小
                bufferSize,
                // 线程工厂：自定义消费者线程名称，便于日志追踪和 JVM 监控
                ThreadFactoryBuilder.create()
                        .setNamePrefix("pictureEditEventDisruptor-")
                        .build()
        );

        // 3. 设置消费者模型：使用 Worker Pool（工作线程池）模式
        // - 多个消费者线程竞争消费同一个事件流（无序，但高效）
        // - 适用于彼此独立、无需严格顺序的事件（如用户操作广播）
        // - 对比：handleEventsWith(...) 是串行/并行有序消费，此处不需要
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);

        // 4. 启动 Disruptor（必须调用，否则不消费）
        disruptor.start();

        // 5. 返回 Bean，供 PictureEditEventProducer 注入使用
        return disruptor;
    }
}