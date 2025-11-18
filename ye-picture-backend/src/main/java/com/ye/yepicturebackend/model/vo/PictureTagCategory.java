package com.ye.yepicturebackend.model.vo;


import lombok.Data;

import java.util.List;

/**
 * 图片标签与分类VO
 */
@Data
public class PictureTagCategory {

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 分类列表
     */
    private List<String> categoryList;
}
