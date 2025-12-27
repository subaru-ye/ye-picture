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
     * 获取当前用户的未读通知数量
     */
    @GetMapping("/unread-count")
    public BaseResponse<Long> getUnreadCount(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        long count = sysNoticeService.getUnreadCount(userId);
        return ResultUtils.success(count);
    }

    /**
     * 分页查询当前用户的通知列表（返回 ReviewNoticeVO）
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
     * 标记某条通知为已读
     */
    @PostMapping("/read/{id}")
    public BaseResponse<Boolean> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        boolean success = sysNoticeService.markAsRead(id, userId);
        return ResultUtils.success(success);
    }
}