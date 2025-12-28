package com.ye.yepicturebackend.model.dto.picture.delete;


import lombok.Data;

import java.io.Serializable;

/**
 * 图片文件删除结果DTO
 */
@Data
public class DeletePictureResult implements Serializable {

    /**
     * 是否执行了文件删除操
     */
    private boolean deleted;

//    /**
//     * 原图URL
//     */
//    private String url;
//
//    /**
//     * 压缩图URL
//     */
//    private String compressUrl;
//
//    /**
//     * 缩略图URL
//     */
//    private String thumbnailUrl;

    /**
     * 被删除的原图 COS Key（相对路径，如 "user/123/origin/xxx.png"）
     */
    private String originKey;

    /**
     * 被删除的压缩图 COS Key
     */
    private String compressKey;

    /**
     * 被删除的缩略图 COS Key
     */
    private String thumbnailKey;

    /**
     * 操作说明信息
     */
    private String message;

    private static final long serialVersionUID = -3641798413928059616L;

}
