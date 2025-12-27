package com.ye.yepicturebackend.converter;

import cn.hutool.json.JSONUtil;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.vo.picture.PictureVO;
import com.ye.yepicturebackend.service.CosUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;


/**
 * Picture 实体与 VO 的转换器（由 Spring 管理，可注入服务）
 */
@Component
@RequiredArgsConstructor
public class PictureVoConverter {

    private final CosUrlService cosUrlService;

    /**
     * 将 Picture 实体转换为 PictureVO
     */
    public PictureVO toVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();

        // todo : 阶段 1：静态字段复制
        // 复制除 url/compressUrl/thumbnailUrl 外的所有字段
        BeanUtils.copyProperties(picture, pictureVO,
                "url", "compressUrl", "thumbnailUrl");
        // 手动处理 tags（JSON 转 List）
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));

        // ====== 阶段 2：动态生成 URL ======
        // 原图
        String url = null;
        if (picture.getOriginKey() != null) {
            url = cosUrlService.buildPublicUrl(picture.getOriginKey());
        } else if (picture.getUrl() != null) {
            url = picture.getUrl(); // fallback 旧数据
        }
        pictureVO.setUrl(url);

        // 压缩图
        String compressUrl = null;
        if (picture.getCompressKey() != null) {
            compressUrl = cosUrlService.buildPublicUrl(picture.getCompressKey());
        } else if (picture.getCompressUrl() != null) {
            compressUrl = picture.getCompressUrl();
        }
        pictureVO.setCompressUrl(compressUrl);

        // 缩略图
        String thumbnailUrl = null;
        if (picture.getThumbnailKey() != null) {
            thumbnailUrl = cosUrlService.buildPublicUrl(picture.getThumbnailKey());
        } else if (picture.getThumbnailUrl() != null) {
            thumbnailUrl = picture.getThumbnailUrl();
        }
        pictureVO.setThumbnailUrl(thumbnailUrl);
        // ================================

        return pictureVO;
    }
}