package com.ye.yepicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.ye.yepicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.ye.yepicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑事件生产者（Publisher）
 * <p>
 * 负责将 WebSocket 接收到的用户操作消息封装为 {@link PictureEditEvent}，
 * 并发布到 Disruptor 的 RingBuffer 中，交由后台消费者线程异步处理。
 * <p>
 * 设计亮点：
 * - 使用 Disruptor 的无锁 RingBuffer 实现高吞吐事件发布；
 * - 通过 {@code ringBuffer.next()} + {@code publish()} 保证内存可见性与顺序性；
 * - 支持 Spring 生命周期管理，应用关闭时自动 shutdown Disruptor，避免资源泄漏；
 * - 线程安全：Disruptor 天然支持单生产者模型（本项目为单生产者场景）。
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    /**
     * 注入由 {@link PictureEditEventDisruptorConfig} 创建的 Disruptor 实例
     * <p>
     * Bean 名为 "pictureEditEventDisruptor"，在配置类中通过 @Bean 指定。
     */
    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布图片编辑事件到 Disruptor 队列
     *
     * @param pictureEditRequestMessage 客户端发送的原始请求消息
     * @param session                   WebSocket 会话（用于后续响应或广播）
     * @param user                      当前操作用户（已通过拦截器鉴权）
     * @param pictureId                 目标图片 ID
     */
    public void publishEvent(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId) {

        // 1. 获取 RingBuffer（Disruptor 的核心数据结构）
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();

        long sequence = -1;
        try {
            // 2. 申请下一个可用的序列号（可能阻塞，若缓冲区满）
            sequence = ringBuffer.next();

            // 3. 获取该位置的事件对象（预分配，避免 GC）
            PictureEditEvent event = ringBuffer.get(sequence);

            // 4. 填充事件数据（注意：不要创建新对象，复用已有实例）
            event.setSession(session);
            event.setPictureEditRequestMessage(pictureEditRequestMessage);
            event.setUser(user);
            event.setPictureId(pictureId);

            // 5. 发布事件，通知消费者线程可以消费
            ringBuffer.publish(sequence);

        } catch (Exception e) {
            // 如果在 next() 或 publish() 过程中出错（如中断、缓冲区异常）
            // 需要确保 sequence 被正确释放（否则 RingBuffer 会卡住）
            if (sequence != -1) {
                ringBuffer.publish(sequence);
            }
            log.error("发布图片编辑事件失败", e);
            throw new RuntimeException("Failed to publish edit event", e);
        }
    }

    /**
     * Spring 容器销毁 Bean 前调用，用于优雅关闭 Disruptor
     * <p>
     * - 等待所有已发布事件被消费完毕；
     * - 关闭消费者线程池；
     * - 释放 RingBuffer 内存。
     * <p>
     * ⚠️ 若不调用 shutdown()，可能导致：
     *   - 应用无法正常退出；
     *   - 后台线程持续运行；
     *   - 资源泄漏。
     */
    @PreDestroy
    public void close() {
        log.info("正在关闭 PictureEditEventDisruptor...");
        pictureEditEventDisruptor.shutdown();
        log.info("PictureEditEventDisruptor 已关闭");
    }
}