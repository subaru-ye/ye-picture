package com.ye.yepicturebackend.model.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审核通知 VO（用于前端展示）
 */
@Data
public class ReviewNoticeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通知 ID
     */
    private Long id;

    /**
     * 用户 ID（接收通知的用户）
     */
    private Long userId;

    /**
     * 图片 ID
     */
    private Long pictureId;

    /**
     * 图片标题（便于前端直接展示，避免额外请求）
     */
    private String pictureTitle;

    /**
     * 图片预览 URL（带临时签名的缩略图地址，用于前端展示）
     */
    private String pictureUrl;

    /**
     * 审核状态：0-待审核，1-通过，2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核结果描述（如：图片内容违规）
     */
    private String reviewMessage;

    /**
     * 通知创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否已读：0-未读，1-已读
     */
    private Integer isRead;

    /**
     * 通知类型（可扩展，如：审核、系统公告等）
     */
    private String noticeType;

}