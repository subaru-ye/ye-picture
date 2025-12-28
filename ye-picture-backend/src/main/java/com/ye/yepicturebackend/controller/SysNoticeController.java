package com.ye.yepicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.common.BaseResponse;
import com.ye.yepicturebackend.common.ResultUtils;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.vo.user.ReviewNoticeVO;
import com.ye.yepicturebackend.service.SysNoticeService;
import com.ye.yepicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 站内信通知控制器
 * <p>
 * 提供用户通知相关的接口，包括：
 * - 查询未读通知数量
 * - 分页获取审核类通知列表（含图片预览）
 * - 标记通知为已读
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/notice")
public class SysNoticeController {

    @Resource
    private SysNoticeService sysNoticeService;

    @Resource
    private UserService userService;

    /**
     * 获取当前登录用户的未读通知总数
     *
     * @param request HTTP 请求对象，用于从中解析当前登录用户
     * @return {@link BaseResponse} 包含未读通知数量（long 类型），成功时 code = 0
     */
    @GetMapping("/unread-count")
    public BaseResponse<Long> getUnreadCount(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        long count = sysNoticeService.getUnreadCount(userId);
        return ResultUtils.success(count);
    }

    /**
     * 分页查询当前登录用户的【图片审核通知】列表
     * <p>
     * 返回的每条通知包含关联图片的标题和带签名的缩略图 URL，
     * 前端可直接用于展示，无需额外请求图片信息。
     * </p>
     *
     * @param page    页码，从 1 开始，默认值为 1
     * @param size    每页大小，默认值为 10，建议不超过 50
     * @param request HTTP 请求对象，用于获取当前登录用户身份
     * @return {@link BaseResponse} 包含分页的 {@link ReviewNoticeVO} 列表，成功时 code = 0
     */
    @GetMapping("/list")
    public BaseResponse<Page<ReviewNoticeVO>> listNotices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Page<ReviewNoticeVO> voPage = sysNoticeService.listReviewNoticesByUser(userId, page, size);
        return ResultUtils.success(voPage);
    }

    /**
     * 将指定 ID 的通知标记为“已读”
     * <p>
     * 接口会校验该通知是否属于当前登录用户，防止越权操作。
     * </p>
     *
     * @param id      通知 ID，路径参数
     * @param request HTTP 请求对象，用于获取当前登录用户身份
     * @return {@link BaseResponse} 包含操作结果：true 表示成功，false 表示失败（如通知不存在或无权限）
     */
    @PostMapping("/read/{id}")
    public BaseResponse<Boolean> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        boolean success = sysNoticeService.markAsRead(id, userId);
        return ResultUtils.success(success);
    }
}