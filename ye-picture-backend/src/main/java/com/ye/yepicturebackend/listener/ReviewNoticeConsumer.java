package com.ye.yepicturebackend.listener;

import com.rabbitmq.client.Channel;
import com.ye.yepicturebackend.constant.RabbitMQConstant;
import com.ye.yepicturebackend.model.dto.picture.review.ReviewNoticeMessage;
import com.ye.yepicturebackend.model.entity.SysNotice;
import com.ye.yepicturebackend.service.MailService;
import com.ye.yepicturebackend.service.SysNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;

/**
 * 图片审核结果通知消费者
 * <p>
 * 该消费者监听 {@link RabbitMQConstant#REVIEW_NOTICE_QUEUE} 队列，
 * 负责处理异步的图片审核结果通知，包括：
 * - 创建站内信通知（核心业务，必须成功）
 * - 发送邮件通知（辅助渠道，失败不影响核心）
 * - 手动 ACK 消息以确保消息可靠性
 * </p>
 *
 * <h3>设计原则</h3>
 * <ol>
 *   <li><strong>站内信是核心</strong>：必须确保通知记录成功写入数据库</li>
 *   <li><strong>邮件是增强通道</strong>：邮件发送失败不影响站内信，仅记录日志</li>
 *   <li><strong>消息可靠性</strong>：采用手动 ACK 确认机制，防止消息丢失</li>
 *   <li><strong>失败处理</strong>：仅当核心步骤（写库）失败时才拒绝消息（Nack）</li>
 * </ol>
 */
@Component
@Slf4j
public class ReviewNoticeConsumer {

    @Resource
    private SysNoticeService sysNoticeService;

    @Resource
    private MailService mailService;

    /**
     * 处理图片审核结果通知消息
     * <p>
     * 消息处理流程：
     * 1. 构建站内信实体并保存到数据库（核心步骤）
     * 2. 尝试发送邮件通知（辅助步骤，失败不影响主流程）
     * 3. 更新站内信发送状态
     * 4. 手动 ACK 确认消息已处理
     * </p>
     *
     * @param message 消息体，包含图片 ID、用户 ID、审核状态等信息
     * @param msg     Spring AMQP 原生消息对象，用于获取消息属性（如 deliveryTag）
     * @param channel RabbitMQ 信道，用于手动 ACK/Nack 消息
     */
    @RabbitListener(queues = RabbitMQConstant.REVIEW_NOTICE_QUEUE)
    public void handleReviewNotice(ReviewNoticeMessage message, Message msg, Channel channel) throws IOException {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        log.info("【审核通知消费者】收到消息: pictureId={}, userId={}", message.getPictureId(), message.getUserId());

        try {
            // Step 1: 构建并持久化站内信通知（核心业务逻辑）
            SysNotice notice = buildSysNotice(message);
            sysNoticeService.save(notice);
            Long noticeId = notice.getId();
            log.info("✅ 站内信已成功创建, noticeId={}", noticeId);

            // Step 2: 发送邮件通知（增强功能）
            try {
                boolean emailSent = mailService.sendReviewResultEmail(notice);
                if (emailSent) {
                    log.info("📧 审核结果邮件发送成功, noticeId={}", noticeId);
                } else {
                    log.warn("📧 邮件发送失败（但站内信不受影响）, noticeId={}", noticeId);
                }
            } catch (Exception e) {
                log.error("📧 发送邮件异常（但站内信已保留）, noticeId={}", noticeId, e);
                // 邮件发送失败不中断主流程，继续处理后续步骤
            }

            // Step 3: 更新站内信发送状态和时间
            notice.setNoticeStatus(1); // 1 = 成功（站内信持久化成功）
            notice.setSendTime(new Date());
            sysNoticeService.updateById(notice);

            // Step 4: 手动 ACK，确认消息已成功处理
            channel.basicAck(deliveryTag, false);
            log.info("✅ 消息处理完成并 ACK, noticeId={}", noticeId);

        } catch (Exception e) {
            // 仅当核心步骤（保存站内信）失败时才拒绝消息，避免消息无限重试
            log.error("❌ 保存站内信失败，消息将被拒绝, pictureId={}", message.getPictureId(), e);
            channel.basicNack(deliveryTag, false, false); // requeue=false，消息被丢弃
        }
    }

    /**
     * 根据审核消息构建系统通知实体
     * <p>
     * 该方法负责将 {@link ReviewNoticeMessage} 转换为 {@link SysNotice}，
     * 设置通知类型、状态、内容等核心字段。
     * </p>
     *
     * @param message 审核结果消息 DTO
     * @return 构建好的 SysNotice 实体，包含待插入数据库的初始数据
     */
    private SysNotice buildSysNotice(ReviewNoticeMessage message) {
        SysNotice notice = new SysNotice();

        // 关联信息
        notice.setUserId(message.getUserId());
        notice.setPictureId(message.getPictureId());

        // 通知类型：1 - 图片审核结果
        notice.setNoticeType(1);

        // 审核状态（由消息提供）
        notice.setReviewStatus(message.getReviewStatus());

        // 通知内容：根据审核状态和原因生成
        String content;
        if (message.getReviewStatus() == 1) { // 1 = 通过
            content = "您的图片已通过审核";
        } else {
            content = "您的图片未通过审核";
            // 如果有具体的审核原因，则追加到内容中
            if (message.getReviewMessage() != null && !message.getReviewMessage().trim().isEmpty()) {
                content += "，原因：" + message.getReviewMessage();
            }
        }
        notice.setNoticeContent(content);

        // 初始状态：待发送，未读，未删除
        notice.setNoticeStatus(0);
        notice.setReadStatus(0);
        notice.setIsDelete(0);

        // 时间戳
        Date now = new Date();
        notice.setCreateTime(now);
        notice.setUpdateTime(now);

        return notice;
    }
}