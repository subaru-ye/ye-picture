package com.ye.yepicturebackend.model.dto.picture.query;

import lombok.Data;

import java.io.Serializable;

/**
 * 以图搜图请求
 */
@Data
public class SearchPictureRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
