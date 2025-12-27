package com.ye.yepicturebackend.utils;

import com.ye.yepicturebackend.mapper.PictureMapper;
import com.ye.yepicturebackend.config.CosClientConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.model.entity.Picture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 启动时自动迁移旧图片 URL 到 COS Key 字段
 */
//@Component
@RequiredArgsConstructor
@Slf4j
public class PictureKeyMigrationTask implements ApplicationRunner {

    private final PictureMapper pictureMapper;
    private final CosClientConfig cosClientConfig;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始检查并迁移旧图片 COS Key 字段...");

        LambdaQueryWrapper<Picture> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.isNull(Picture::getOriginKey)
                .isNotNull(Picture::getUrl);
        long count = pictureMapper.selectCount(checkWrapper);

        if (count == 0) {
            log.info("无需迁移：所有图片均已包含 originKey");
            return;
        }

        log.info("发现 {} 条旧图片记录需要迁移，开始分页处理...", count);

        int pageSize = 100;
        int pageNum = 1;
        int totalMigrated = 0;

        while (true) {
            Page<Picture> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Picture> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNull(Picture::getOriginKey)
                    .isNotNull(Picture::getUrl)
                    .orderByAsc(Picture::getId);

            Page<Picture> resultPage = pictureMapper.selectPage(page, wrapper);
            List<Picture> pictures = resultPage.getRecords();

            if (pictures.isEmpty()) break;

            // 调用事务方法（通过 this，因为本类有 @Transactional）
            int migrated = migrateBatch(pictures);
            totalMigrated += migrated;

            log.info("已迁移第 {} 页，本批 {} 条，累计 {} 条", pageNum, migrated, totalMigrated);

            if (pictures.size() < pageSize) break;
            pageNum++;
        }

        log.info("✅ 图片 COS Key 迁移完成，共处理 {} 条记录", totalMigrated);
    }

    @Transactional(rollbackFor = Exception.class)
    public int migrateBatch(List<Picture> pictures) {
        int count = 0;
        for (Picture picture : pictures) {
            try {
                String originKey = extractCosKey(picture.getUrl());
                String compressKey = picture.getCompressUrl() != null 
                        ? extractCosKey(picture.getCompressUrl()) : null;
                String thumbnailKey = picture.getThumbnailUrl() != null 
                        ? extractCosKey(picture.getThumbnailUrl()) : null;

                picture.setOriginKey(originKey);
                picture.setCompressKey(compressKey);
                picture.setThumbnailKey(thumbnailKey);

                pictureMapper.updateById(picture);
                count++;
            } catch (Exception e) {
                log.error("迁移单条图片失败, id={}, url={}", picture.getId(), picture.getUrl(), e);
            }
        }
        return count;
    }

    /**
     * 复用你原有的 extractCosKey 逻辑
     */
    private String extractCosKey(String url) {
        if (url == null) return null;
        String baseUrl = String.format("https://%s.cos.%s.myqcloud.com",
                cosClientConfig.getBucket(),
                cosClientConfig.getRegion());
        if (url.startsWith(baseUrl)) {
            String key = url.substring(baseUrl.length());
            return key.startsWith("/") ? key.substring(1) : key;
        }
        return url; // fallback（理论上旧数据都是 COS URL）
    }
}