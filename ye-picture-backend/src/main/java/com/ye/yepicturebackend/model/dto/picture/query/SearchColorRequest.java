package com.ye.yepicturebackend.model.dto.picture.query;

import lombok.Data;

import java.io.Serializable;

@Data
public class SearchColorRequest implements Serializable {

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = -7844030714198772701L;
}
