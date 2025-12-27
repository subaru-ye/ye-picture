package com.ye.yepicturebackend.utils;

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

        BeanUtils.copyProperties(picture, pictureVO);
        // 处理 tags
        if (picture.getTags() != null) {
            pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
        }

        pictureVO.setUrl(cosUrlService.generateDefaultSignedUrl(picture.getOriginKey()));
        pictureVO.setCompressUrl(cosUrlService.generateDefaultSignedUrl(picture.getCompressKey()));
        pictureVO.setThumbnailUrl(cosUrlService.generateDefaultSignedUrl(picture.getThumbnailKey()));

        return pictureVO;
    }
}