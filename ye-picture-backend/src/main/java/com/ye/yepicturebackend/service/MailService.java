package com.ye.yepicturebackend.service;

import com.ye.yepicturebackend.model.entity.SysNotice;

/**
 * 邮件通知服务接口
 */
public interface MailService {

    /**
     * 发送图片审核结果邮件
     *
     * @param notice 系统通知记录
     * @return 是否发送成功
     */
    boolean sendReviewResultEmail(SysNotice notice);
}