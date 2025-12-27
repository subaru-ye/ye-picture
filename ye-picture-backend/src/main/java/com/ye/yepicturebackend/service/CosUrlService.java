package com.ye.yepicturebackend.service;


/**
 * COS URL 生成服务接口
 *
 * @author Ye
 */
public interface CosUrlService {
    /**
     * 生成默认有效期的签名 URL
     */
    String generateDefaultSignedUrl(String key);

    /**
     * 生成指定有效期的签名 URL
     */
    String generateSignedUrl(String key, long expireMillis);
}