package com.ye.yepicturebackend.model.dto.picture.shared;


import lombok.Data;

import java.io.Serializable;

/**
 * 图片文件删除结果DTO
 */
@Data
public class PictureFileDeleteResult implements Serializable {

    /**
     * 是否执行了文件删除操
     */
    private boolean deleted;

    /**
     * 原图URL
     */
    private String url;

    /**
     * 压缩图URL
     */
    private String compressUrl;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * 操作说明信息
     */
    private String message;

    private static final long serialVersionUID = -3641798413928059616L;

}
