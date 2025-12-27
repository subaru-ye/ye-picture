package com.ye.yepicturebackend.service.impl;

import com.ye.yepicturebackend.model.entity.SysNotice;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.service.MailService;
import com.ye.yepicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;

/**
 * 邮件通知服务实现类
 */
@Service
@Slf4j
public class MailServiceImpl implements MailService {

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private UserService userService;

    @Override
    public boolean sendReviewResultEmail(SysNotice notice) {
        try {
            // 1. 查询用户
            User user = userService.getById(notice.getUserId());
            if (user == null || !StringUtils.hasText(user.getUserEmail())) {
                log.warn("用户无邮箱，跳过邮件发送, userId={}", notice.getUserId());
                return false;
            }

            // 2. 构建内容
            String subject = "【云图库】您的图片审核结果";
            String content = buildContent(notice);

            // 3. 发送邮件
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getUserEmail());
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("审核结果邮件发送成功, userId={}, pictureId={}", notice.getUserId(), notice.getPictureId());

            return true;

        } catch (Exception e) {
            log.error("发送审核邮件失败, noticeId={}", notice.getId(), e);
            return false;
        }
    }

    private String buildContent(SysNotice notice) {
        return "<h2>您好，" + notice.getNoticeContent() + "！</h2>" +
                "<p><strong>图片 ID：</strong>" + notice.getPictureId() + "</p>" +
                "<p>您可以登录 Yepicture 查看详情。</p>" +
                "<hr>" +
                "<p style='color:#888;'>此为系统通知邮件，请勿回复。</p>";
    }
}