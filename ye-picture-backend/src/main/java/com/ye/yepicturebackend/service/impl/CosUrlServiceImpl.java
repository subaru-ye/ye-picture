package com.ye.yepicturebackend.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.ye.yepicturebackend.manager.upload.CosClientConfig;
import com.ye.yepicturebackend.service.CosUrlService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;

/**
 * COS URL 生成服务实现类
 */
@Service
@RequiredArgsConstructor
public class CosUrlServiceImpl implements CosUrlService {

    private final CosClientConfig cosClientConfig;
    private final COSClient cosClient;

    /**
     * 生成一个带时效性的、可公开访问的临时签名 URL（Presigned URL）
     *
     * @param key          COS 中的对象键（即文件路径），例如 "user/123/avatar.png"
     *                     如果以 "/" 开头，会自动去除（COS Key 不应以 / 开头）
     * @param expireMillis 签名 URL 的有效时间（毫秒），例如 3600000 = 1 小时
     * @return 临时可访问的完整 HTTPS URL，如：
     * <a href="https://your-bucket.cos.ap-beijing.myqcloud.com/user/123/avatar.png?sign=xxx">...</a>
     * 若 key 为空，则返回 null
     */
    @Override
    public String generateSignedUrl(String key, long expireMillis) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        // COS 的 Key 规范：不应以 "/" 开头，否则可能导致签名失败或路径错误
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;

        // 计算过期时间：当前时间 + 指定毫秒数
        Date expiration = new Date(System.currentTimeMillis() + expireMillis);

        // 构造预签名请求：指定 bucket、key、HTTP 方法（GET 表示只读）
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
                cosClientConfig.getBucket(),   // 存储桶名称
                normalizedKey,                 // 文件在 COS 中的路径（Key）
                HttpMethodName.GET             // 只允许 GET 请求（下载）
        );
        req.setExpiration(expiration); // 设置过期时间

        // 调用 COS SDK 生成带签名的 URL
        URL signedUrl = cosClient.generatePresignedUrl(req);
        return signedUrl.toString();
    }

    /**
     * 生成默认有效期的签名 URL
     */
    @Override
    public String generateDefaultSignedUrl(String key) {
        return generateSignedUrl(key, 30 * 60 * 1000); // 默认 30 分钟
    }

}