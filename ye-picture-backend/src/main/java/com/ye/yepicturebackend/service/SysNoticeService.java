package com.ye.yepicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.model.entity.SysNotice;
import com.ye.yepicturebackend.model.vo.user.ReviewNoticeVO;

/**
 * 系统通知服务接口
 */
public interface SysNoticeService extends IService<SysNotice> {

    /**
     * 获取用户未读消息数量
     *
     * @param userId 用户ID，用于标识特定用户
     * @return 返回该用户的未读消息数量，使用long类型存储
     */
    long getUnreadCount(Long userId);

    /**
     * 根据用户ID获取通知列表（包括系统公告、活动提醒等非审核类通知）
     *
     * @param userId 用户ID，用于查询该用户的通知
     * @param page   页码，从1开始，用于分页查询
     * @param size   每页大小，控制单次返回的通知数量
     * @return 返回一个包含通知列表的分页对象，其中泛型SysNotice表示通知实体类型
     */
    Page<SysNotice> listNoticesByUser(Long userId, int page, int size);

    /**
     * 将指定通知标记为已读
     *
     * @param noticeId 通知ID，用于标识需要标记的通知
     * @param userId   用户ID，用于校验操作权限（确保只能操作自己的通知）
     * @return 操作结果，成功返回true，失败（如通知不存在或无权限）返回false
     */
    boolean markAsRead(Long noticeId, Long userId);

    /**
     * 分页查询用户的【图片审核通知】，并转换为前端友好的 ReviewNoticeVO
     * <p>
     * 该方法会关联查询对应的图片信息，包括：
     * - 图片标题（name）
     * - 图片预览 URL（带临时签名的缩略图地址，用于前端直接展示）
     * </p>
     *
     * @param userId 用户ID，用于筛选该用户收到的审核通知
     * @param page   页码，从1开始
     * @param size   每页大小，建议不超过50
     * @return 分页的 ReviewNoticeVO 列表，每个 VO 包含通知内容及关联图片的展示信息
     */
    Page<ReviewNoticeVO> listReviewNoticesByUser(Long userId, int page, int size);
}