package com.ye.yepicturebackend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用删除请求参数类
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
