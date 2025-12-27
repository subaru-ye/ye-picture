package com.ye.yepicturebackend.service.impl;

import com.ye.yepicturebackend.config.CosClientConfig;
import com.ye.yepicturebackend.service.CosUrlService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * COS URL 生成服务实现类
 */
@Service
@RequiredArgsConstructor
public class CosUrlServiceImpl implements CosUrlService {

    private final CosClientConfig cosClientConfig;

    @Override
    public String buildPublicUrl(String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        // 确保 key 不以 / 开头（避免双斜杠）
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosClientConfig.getBucket(),
                cosClientConfig.getRegion(),
                normalizedKey);
    }
}