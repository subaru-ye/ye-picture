package com.ye.yepicturebackend.api.searachimage;

import com.aliyun.imagesearch20201214.Client;
import com.aliyun.teaopenapi.models.Config;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云图像搜索配置类
 */
@Slf4j
@Configuration
@Data
@ConfigurationProperties(prefix = "aliyun.imagesearch")
public class AliyunImageSearchConfig {

    /**
     * 阿里云 Access Key ID
     */
    private String accessKeyId;

    /**
     * 阿里云 Access Key Secret
     */
    private String accessKeySecret;

    /**
     * 阿里云图像搜索实例的名称
     * 从阿里云控制台的图像搜索实例信息中获取
     */
    private String instanceName;

    /**
     * 阿里云图像搜索实例所在的地域
     * 例如: cn-shanghai, cn-hangzhou
     */
    private String regionId;

    /**
     * 阿里云图像搜索服务的访问端点
     * 通常与地域相关，例如: imagesearch.cn-shanghai.aliyuncs.com
     */
    private String endpoint;

    /**
     * 创建并返回阿里云图像搜索 Client Bean。
     * 该方法在 Spring 容器启动时执行，根据配置属性初始化 Client。
     *
     * @return 初始化好的阿里云图像搜索 Client 实例
     * @throws Exception 如果配置错误或初始化失败，则抛出异常
     */
    @Bean
    public Client aliyunImageSearchClient() throws Exception {
        log.info("正在初始化阿里云图像搜索客户端...");
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setRegionId(regionId)
                .setEndpoint(endpoint);

        Client client = new Client(config);
        log.info("阿里云图像搜索客户端初始化成功，地域: {}, 端点: {}", regionId, endpoint);
        return client;
    }
}