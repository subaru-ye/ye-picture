package com.ye.yepicturebackend.model.dto.picture.upload;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量导入图片请求
 */
@Data
public class UploadBatchRequest implements Serializable {

    /**
     * 搜索词  
     */  
    private String searchText;  
  
    /**  
     * 抓取数量  
     */  
    private Integer count = 10;

    /**
     * 图片名称前缀
     */
    private String namePrefix;

    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片标签列表
     */
    private List<String> tags;

    private static final long serialVersionUID = 9195388255179967892L;

}
