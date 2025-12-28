package com.ye.yepicturebackend.model.dto.picture.review;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片审核通知消息体（用于 RabbitMQ 传输）
 */
@Data
public class ReviewNoticeMessage implements Serializable {
    private Long pictureId;
    private Long userId;
    private Integer reviewStatus;
    private String reviewMessage;
    private Long reviewerId;

    private static final long serialVersionUID = -7341636611162453656L;
}