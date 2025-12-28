package com.ye.yepicturebackend.model.dto.picture.edit;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class EditBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIdList;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 命名规则
     */
    private String nameRule;

    private static final long serialVersionUID = 4977502386624748437L;
}

