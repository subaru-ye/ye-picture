package com.ye.yepicturebackend.config;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hunyuan.client")
@Data
public class HunyuanClientConfig {

    /**
     * secretId
     */
    private String secretId;

    /**
     * secretKey
     */
    private String secretKey;

    /**
     * 地域端点，默认为 ap-beijing
     */
    private String region;

    /**
     * 端点URL
     */
    private String endpoint = "hunyuan.tencentcloudapi.com";

    @Bean
    public HunyuanClient hunyuanClient() {
        // 1. 实例化一个认证对象
        Credential cred = new Credential(secretId, secretKey);
        
        // 2. 实例化一个http选项，可选的，没有特殊需求可以跳过
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(endpoint); // 设置端点
        
        // 3. 实例化一个客户端配置对象
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        
        // 4. 实例化要请求产品的客户端对象
        return new HunyuanClient(cred, region, clientProfile);
    }
}