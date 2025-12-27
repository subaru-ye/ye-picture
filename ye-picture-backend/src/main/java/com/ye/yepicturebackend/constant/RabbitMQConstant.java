package com.ye.yepicturebackend.constant;

/**
 * RabbitMQ 相关常量定义
 */
public final class RabbitMQConstant {

    private RabbitMQConstant() {
        // 私有构造，防止实例化
    }

    // ==================== 图片审核通知 ====================

    /** 审核通知交换机 */
    public static final String REVIEW_NOTICE_EXCHANGE = "picture.review.notice.exchange";

    /** 审核通知队列 */
    public static final String REVIEW_NOTICE_QUEUE = "picture.review.notice.queue";

    /** 审核通知路由键 */
    public static final String REVIEW_NOTICE_ROUTING_KEY = "review.notice";

    // ==================== 死信队列（DLX） ====================

    /** 死信交换机 */
    public static final String DLX_EXCHANGE = "picture.dlx.exchange";

    /** 死信队列 */
    public static final String DLX_QUEUE = "picture.dlx.queue";

    /** 死信路由键 */
    public static final String DLX_ROUTING_KEY = "dlx.routing";
}