package com.ye.yepicturebackend.common;

import lombok.Data;

/**
 * 通用分页请求参数类
 * 用于接收分页查询的请求参数，统一封装分页、排序相关的配置信息
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}
