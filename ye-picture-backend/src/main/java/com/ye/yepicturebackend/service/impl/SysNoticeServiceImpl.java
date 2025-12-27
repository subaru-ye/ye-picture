package com.ye.yepicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ye.yepicturebackend.mapper.SysNoticeMapper;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.SysNotice;
import com.ye.yepicturebackend.model.vo.user.ReviewNoticeVO;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.service.SysNoticeService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统通知服务实现类
 */
@Service
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice>
        implements SysNoticeService {
    @Resource
    private PictureService pictureService;

    @Override
    public long getUnreadCount(Long userId) {
        return this.count(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getReadStatus, 0) // 0 = 未读
                .eq(SysNotice::getIsDelete, 0)); // 未删除
    }

    @Override
    public Page<SysNotice> listNoticesByUser(Long userId, int page, int size) {
        return this.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getUserId, userId)
                        .eq(SysNotice::getIsDelete, 0)
                        .orderByDesc(SysNotice::getCreateTime)
        );
    }

    @Override
    public boolean markAsRead(Long noticeId, Long userId) {
        // 安全校验：确保是自己的通知
        SysNotice notice = this.getById(noticeId);
        if (notice == null || !notice.getUserId().equals(userId)) {
            return false;
        }
        notice.setReadStatus(1); // 1 = 已读
        return this.updateById(notice);
    }

    @Override
    public Page<ReviewNoticeVO> listReviewNoticesByUser(Long userId, int page, int size) {
        Page<SysNotice> sysNoticePage = new Page<>(page, size);
        LambdaQueryWrapper<SysNotice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getNoticeType, 1)
                .orderByDesc(SysNotice::getCreateTime);

        Page<SysNotice> resultPage = this.page(sysNoticePage, queryWrapper);

        // 手动转换 VO 列表
        List<ReviewNoticeVO> voList = resultPage.getRecords().stream().map(sysNotice -> {
            ReviewNoticeVO vo = new ReviewNoticeVO();
            vo.setId(sysNotice.getId());
            vo.setUserId(sysNotice.getUserId());
            vo.setPictureId(sysNotice.getPictureId());
            vo.setReviewStatus(sysNotice.getReviewStatus());
            vo.setReviewMessage(sysNotice.getNoticeContent());
            vo.setIsRead(sysNotice.getReadStatus());

            // 时间转换
            if (sysNotice.getCreateTime() != null) {
                vo.setCreateTime(
                        sysNotice.getCreateTime().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                );
            }

            vo.setNoticeType("REVIEW");

            // 图片名称
            if (sysNotice.getPictureId() != null) {
                Picture picture = pictureService.getById(sysNotice.getPictureId());
                vo.setPictureTitle(picture != null && picture.getName() != null ? picture.getName() : "未知图片");
            } else {
                vo.setPictureTitle("系统通知");
            }

            return vo;
        }).collect(Collectors.toList());

        // 构造最终的 Page<ReviewNoticeVO>
        Page<ReviewNoticeVO> voPage = new Page<>(page, size, resultPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}