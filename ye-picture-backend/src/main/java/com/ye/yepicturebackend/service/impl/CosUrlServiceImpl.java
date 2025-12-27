package com.ye.yepicturebackend.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.ye.yepicturebackend.config.CosClientConfig;
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
     * <p><b>什么是临时签名 URL？</b><br>
     * 腾讯云 COS（对象存储）默认是私有读写的，外部无法直接通过链接访问文件。
     * 但某些场景（如：调用第三方 AI 服务、前端预览、临时分享）需要让别人在限定时间内访问某个文件。
     * 这时就可以用 SDK 生成一个“带数字签名 + 过期时间”的特殊 URL —— 即“临时签名 URL”。</p>
     *
     * <p><b>它有什么特点？</b><br>
     * - 🔒 安全：URL 中包含加密签名，只有持有合法密钥的服务端才能生成<br>
     * - ⏳ 有时效：过了指定时间（expireMillis）后自动失效，无法再访问<br>
     * - 🌐 可公网访问：第三方（如阿里云 AI）可以直接用这个 URL 下载图片<br>
     * - 🚫 不暴露密钥：URL 本身不包含 SecretId/SecretKey，不会泄露账号凭证</p>
     *
     * <p><b>典型使用场景：</b><br>
     * - 调用阿里云/百度/腾讯 AI 接口时提供图片地址<br>
     * - 前端上传后立即预览（但不想永久公开）<br>
     * - 生成限时分享链接（如：24 小时内可下载的合同）</p>
     *
     * <p><b>⚠️ 注意事项：</b><br>
     * - 有效期不宜过短（如 AI 处理可能需要几十秒到几分钟）<br>
     * - 有效期也不宜过长（避免被恶意缓存或转发）<br>
     * - 此 URL 仅用于“读取”（GET），不能用于上传或删除</p>
     *
     * @param key          COS 中的对象键（即文件路径），例如 "user/123/avatar.png"
     *                     如果以 "/" 开头，会自动去除（COS Key 不应以 / 开头）
     * @param expireMillis 签名 URL 的有效时间（毫秒），例如 3600000 = 1 小时
     * @return 临时可访问的完整 HTTPS URL，如：
     *         <a href="https://your-bucket.cos.ap-beijing.myqcloud.com/user/123/avatar.png?sign=xxx">...</a>
     *         若 key 为空，则返回 null
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