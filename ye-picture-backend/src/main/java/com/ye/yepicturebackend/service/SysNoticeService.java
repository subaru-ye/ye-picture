package com.ye.yepicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.model.entity.SysNotice;
import com.ye.yepicturebackend.model.vo.user.ReviewNoticeVO;

public interface SysNoticeService extends IService<SysNotice> {

    /**
     * 获取用户未读消息数量
     *
     * @param userId 用户ID，用于标识特定用户
     * @return 返回该用户的未读消息数量，使用long类型存储
     */
    long getUnreadCount(Long userId);

    /**
     * 根据用户ID获取通知列表
     *
     * @param userId 用户ID，用于查询该用户的通知
     * @param page   页码，用于分页查询
     * @param size   每页大小，用于分页查询
     * @return 返回一个包含通知列表的分页对象，其中泛型SysNotice表示通知实体类型
     */
    Page<SysNotice> listNoticesByUser(Long userId, int page, int size);

    /**
     * 将指定通知标记为已读
     *
     * @param noticeId 通知ID，用于标识需要标记的通知
     * @param userId   用户ID，用于标识执行标记操作的用户
     * @return 操作结果，成功返回true，失败返回false
     */
    boolean markAsRead(Long noticeId, Long userId);

    Page<ReviewNoticeVO> listReviewNoticesByUser(Long userId, int page, int size);
}