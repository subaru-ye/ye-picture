package com.ye.yepicturebackend.listener;

import com.rabbitmq.client.Channel;
import com.ye.yepicturebackend.constant.RabbitMQConstant;
import com.ye.yepicturebackend.model.dto.picture.admin.ReviewNoticeMessage;
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
 * 设计原则：
 * - 站内信（写入 sys_notice 表）是核心，必须成功
 * - 邮件是增强通道，失败不影响站内信
 */
@Component
@Slf4j
public class ReviewNoticeConsumer {

    @Resource
    private SysNoticeService sysNoticeService;

    @Resource
    private MailService mailService;

    /**
     * 监听审核通知队列，处理异步通知
     */
    @RabbitListener(queues = RabbitMQConstant.REVIEW_NOTICE_QUEUE)
    public void handleReviewNotice(ReviewNoticeMessage message, Message msg, Channel channel) throws IOException {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        log.info("【审核通知消费者】收到消息: pictureId={}, userId={}", message.getPictureId(), message.getUserId());

        try {
            // 1. 构建并保存站内信（核心步骤，必须成功）
            SysNotice notice = buildSysNotice(message);
            sysNoticeService.save(notice);
            Long noticeId = notice.getId();
            log.info("✅ 站内信已成功创建, noticeId={}", noticeId);

            // 2. 发送邮件通知
            try {
                boolean emailSent = mailService.sendReviewResultEmail(notice);
                if (emailSent) {
                    log.info("📧 审核结果邮件发送成功, noticeId={}", noticeId);
                } else {
                    log.warn("📧 邮件发送失败（但站内信不受影响）, noticeId={}", noticeId);
                }
            } catch (Exception e) {
                log.error("📧 发送邮件异常（但站内信已保留）, noticeId={}", noticeId, e);
                // 不抛出异常，避免影响主流程
            }

            // 3. 更新站内信状态为“发送成功”（指入库成功）
            notice.setNoticeStatus(1); // 1 = 成功（这里指站内信持久化成功）
            notice.setSendTime(new Date());
            sysNoticeService.updateById(notice);

            // 4. 手动 ACK：消息处理完成
            channel.basicAck(deliveryTag, false);
            log.info("✅ 消息处理完成并 ACK, noticeId={}", noticeId);

        } catch (Exception e) {
            // 仅当保存站内信失败时才拒绝消息（极罕见）
            log.error("❌ 保存站内信失败，消息将被拒绝, pictureId={}", message.getPictureId(), e);
            channel.basicNack(deliveryTag, false, false); // 不重回队列
        }
    }

    /**
     * 构建系统通知实体（站内信内容）
     */
    private SysNotice buildSysNotice(ReviewNoticeMessage message) {
        SysNotice notice = new SysNotice();
        notice.setUserId(message.getUserId());
        notice.setPictureId(message.getPictureId());
        notice.setNoticeType(1); // 1-图片审核结果
        notice.setReviewStatus(message.getReviewStatus());

        String content;
        if (message.getReviewStatus() == 1) { // 假设 1=通过
            content = "您的图片已通过审核";
        } else {
            content = "您的图片未通过审核";
            if (message.getReviewMessage() != null && !message.getReviewMessage().trim().isEmpty()) {
                content += "，原因：" + message.getReviewMessage();
            }
        }
        notice.setNoticeContent(content);

        notice.setNoticeStatus(0); // 初始为待发送（稍后更新为1）
        notice.setReadStatus(0);   // 0 = 未读
        notice.setIsDelete(0);
        Date now = new Date();
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        return notice;
    }
}