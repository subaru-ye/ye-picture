package com.ye.yepicturebackend.service;


/**
 * COS URL 生成服务接口
 *
 * @author Ye
 */
public interface CosUrlService {

    /**
     * 根据 COS Key 生成公开可访问的完整 URL
     *
     * @param key COS 对象的相对路径（如 "user/123/origin/xxx.png"）
     * @return 完整 URL（如 "<a href="https://bucket.cos.region.myqcloud.com/user/123/origin/xxx.png">...</a>"）
     */
    String buildPublicUrl(String key);
}