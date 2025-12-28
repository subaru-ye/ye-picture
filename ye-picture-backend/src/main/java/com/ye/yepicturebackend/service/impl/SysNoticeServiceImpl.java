package com.ye.yepicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ye.yepicturebackend.mapper.SysNoticeMapper;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.SysNotice;
import com.ye.yepicturebackend.model.vo.user.ReviewNoticeVO;
import com.ye.yepicturebackend.service.CosUrlService;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.service.SysNoticeService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统通知服务实现类
 */
@Service
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice>
        implements SysNoticeService {

    @Resource
    private PictureService pictureService;

    @Resource
    private CosUrlService cosUrlService;

    /**
     * 获取用户未读通知数量
     *
     * @param userId 用户 ID
     * @return 未读通知数
     */
    @Override
    public long getUnreadCount(Long userId) {
        return this.count(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getReadStatus, 0) // 0 = 未读
                .eq(SysNotice::getIsDelete, 0)); // 未删除
    }

    /**
     * 分页查询用户的所有系统通知（非审核类）
     * 该方法用于获取指定用户的系统通知列表，支持分页查询，并按照创建时间降序排列
     *
     * @param userId 用户 ID，用于筛选特定用户的通知
     * @param page   当前页（从 1 开始）
     * @param size   每页大小
     * @return 分页结果
     */
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

    /**
     * 将指定通知标记为已读（需校验归属）
     *
     * @param noticeId 通知 ID
     * @param userId   用户 ID
     * @return 是否成功
     */
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

    /**
     * 分页查询用户的【图片审核通知】，并转换为 ReviewNoticeVO（含图片标题和预览图）
     *
     * @param userId 用户 ID
     * @param page   当前页（从 1 开始）
     * @param size   每页大小
     * @return 分页的 ReviewNoticeVO 列表
     */
    @Override
    public Page<ReviewNoticeVO> listReviewNoticesByUser(Long userId, int page, int size) {
        // 1. 构造分页对象和查询条件：仅查询类型为“审核通知”（noticeType = 1）的通知
        Page<SysNotice> sysNoticePage = new Page<>(page, size);
        LambdaQueryWrapper<SysNotice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getNoticeType, 1) // 1 表示审核通知
                .eq(SysNotice::getIsDelete, 0)
                .orderByDesc(SysNotice::getCreateTime);

        // 2. 执行分页查询
        Page<SysNotice> resultPage = this.page(sysNoticePage, queryWrapper);

        // 3. 提取所有 pictureId（用于批量查询图片信息，避免 N+1 问题）
        List<Long> pictureIds = resultPage.getRecords().stream()
                .map(SysNotice::getPictureId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 4. 批量查询关联的图片信息（提升性能）
        Map<Long, Picture> pictureMap;
        if (!pictureIds.isEmpty()) {
            List<Picture> pictures = pictureService.listByIds(pictureIds);
            pictureMap = pictures.stream()
                    .collect(Collectors.toMap(Picture::getId, picture -> picture));
        } else {
            pictureMap = new HashMap<>();
        }

        // 5. 转换为 ReviewNoticeVO 列表
        List<ReviewNoticeVO> voList = resultPage.getRecords().stream().map(sysNotice -> {
            ReviewNoticeVO vo = new ReviewNoticeVO();
            vo.setId(sysNotice.getId());
            vo.setUserId(sysNotice.getUserId());
            vo.setPictureId(sysNotice.getPictureId());
            vo.setReviewStatus(sysNotice.getReviewStatus());
            vo.setReviewMessage(sysNotice.getNoticeContent());
            vo.setIsRead(sysNotice.getReadStatus());
            vo.setNoticeType("REVIEW"); // 固定类型标识

            // 时间转换：MyBatis Plus 返回的是 Date，转为 LocalDateTime
            if (sysNotice.getCreateTime() != null) {
                vo.setCreateTime(
                        sysNotice.getCreateTime().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                );
            }

            // 6. 填充图片相关信息（标题 + 预览图 URL）
            if (sysNotice.getPictureId() != null) {
                Picture picture = pictureMap.get(sysNotice.getPictureId());
                if (picture != null) {
                    vo.setPictureTitle(picture.getName() != null ? picture.getName() : "未知图片");
                    vo.setPictureUrl(cosUrlService.generateDefaultSignedUrl(picture.getThumbnailKey()));
                } else {
                    vo.setPictureTitle("图片已删除");
                    vo.setPictureUrl(null);
                }
            } else {
                vo.setPictureTitle("系统通知");
                vo.setPictureUrl(null);
            }

            return vo;
        }).collect(Collectors.toList());

        // 7. 构造最终的分页 VO 对象
        Page<ReviewNoticeVO> voPage = new Page<>(page, size, resultPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}