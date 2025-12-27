package com.ye.yepicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统通知表（存储审核结果通知、消息状态等）
 */
@TableName("sys_notice")
@Data
public class SysNotice implements Serializable {

    private static final long serialVersionUID = -6680232802422457365L;

    /**
     * 通知ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的用户ID（图片上传者）
     */
    private Long userId;

    /**
     * 关联图片ID
     */
    private Long pictureId;

    /**
     * 通知类型：1-图片审核结果
     */
    private Integer noticeType;

    /**
     * 通知内容（如审核通过/驳回理由）
     */
    private String noticeContent;

    /**
     * 发送状态：0-待发送；1-已发送；2-发送失败
     */
    private Integer noticeStatus;

    /**
     * 阅读状态：0-未读；1-已读
     */
    private Integer readStatus;

    /**
     * 审核状态：0-待审，1-通过，2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 通知发送时间
     */
    private Date sendTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;
}