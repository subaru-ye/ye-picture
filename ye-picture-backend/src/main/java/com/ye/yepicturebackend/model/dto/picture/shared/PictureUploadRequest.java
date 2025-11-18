package com.ye.yepicturebackend.model.dto.picture.shared;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片上传请求
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 空间 id
     */
    private Long SpaceId;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片标签列表
     */
    private List<String> tags;

    /**
     * 图片简介
     */
    private String introduction;

    private static final long serialVersionUID = -5543856672405026612L;
}
