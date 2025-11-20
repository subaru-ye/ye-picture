package com.ye.yepicturebackend.api.hunyuan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片分析结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAnalysisResult {
    /**
     * 图片简介：用一段流畅、简洁、有吸引力的中文描述图片内容，长度在10-20字之间
     */
    private String description;

    /**
     * 图片标签：2到3个关键词，用中文逗号"，"分隔
     */
    private String tags;

    /**
     * 图片分类：从固定分类列表中选出的最贴切分类
     */
    private String category;
}