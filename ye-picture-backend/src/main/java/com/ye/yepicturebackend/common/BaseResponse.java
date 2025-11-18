package com.ye.yepicturebackend.common;

import com.ye.yepicturebackend.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用响应结果封装类
 * 用于统一API接口的返回格式，包含状态码、数据和消息信息
 * @param <T> 响应数据的类型，支持泛型以适应不同的数据结构
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}

