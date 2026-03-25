package com.ye.yepicturebackend.api.searachimage;

import com.aliyun.imagesearch20201214.Client;
import com.aliyun.imagesearch20201214.models.SearchImageByPicAdvanceRequest;
import com.aliyun.imagesearch20201214.models.SearchImageByPicResponse;
import com.aliyun.imagesearch20201214.models.SearchImageByPicResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import com.ye.yepicturebackend.api.imageSearch.model.ImageSearchResult;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云图像搜索服务实现类
 * 提供基于图片内容搜索相似图片的功能
 */
@Slf4j
@Service
public class AliyunImageSearchService {

    @Resource
    private Client aliyunImageSearchClient; // 注入在配置类中创建的 Client Bean

    @Resource
    private AliyunImageSearchConfig config; // 注入配置类，用于获取实例名称

    /**
     * 根据图片URL执行以图搜图
     * 此方法会下载URL对应的图片内容，然后调用阿里云搜索接口
     *
     * @param imageUrl 需要搜索的图片的URL
     * @return 搜索结果列表，每个元素包含单张图片的核心信息
     */
//    public List<ImageSearchResult> searchImageByPicUrl(String imageUrl) {
//        log.info("开始执行阿里云以图搜图，实例名称: {}, 图片URL: {}", config.getInstanceName(), imageUrl);
//
//        // 1. 从 URL 下载图片内容
//        InputStream picContent = downloadImage(imageUrl);
//        if (picContent == null) {
//            log.error("下载图片失败，URL: {}", imageUrl);
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法下载指定的图片URL");
//        }
//
//        // 2. 调用内部的 searchImageByPic 方法
//        return searchImageByPic(picContent);
//    }

    /**
     * 根据图片输入流执行以图搜图 (内部方法，也可以公开供直接传InputStream的场景使用)
     *
     * @return 搜索结果列表，每个元素包含单张图片的核心信息
     */
    private List<ImageSearchResult> searchImageByPic(String imageUrl) {
        SearchImageByPicAdvanceRequest request = new SearchImageByPicAdvanceRequest();
        // 设置实例名称，从配置中获取
        request.setInstanceName(config.getInstanceName());

        // 设置请求参数
        request.setNum(5); // 返回结果的数目。取值范围：1-100。默认值：10
        request.setCrop(true); // 是否需要进行主体识别，默认为true。
        request.distinctProductId=false;
        RuntimeOptions runtimeObject =  new RuntimeOptions();
        try {
             String url = imageUrl;
             request.picContentObject = new URL(url).openStream();
            // 发起搜索请求
            SearchImageByPicResponse response = aliyunImageSearchClient.searchImageByPicAdvance(request, runtimeObject);
            log.info("阿里云以图搜图请求成功，RequestId: {}", response.getBody().getRequestId());

            // 解析响应结果
            List<SearchImageByPicResponseBody
                    .SearchImageByPicResponseBodyAuctions> auctions = response.getBody().getAuctions();

            if (auctions == null || auctions.isEmpty()) {
                log.warn("阿里云以图搜图未返回结果");
                return new ArrayList<>(); // 返回空列表
            }

            List<ImageSearchResult> results = new ArrayList<>();
            for (var auction : auctions) {
                // 将阿里云返回的每个 auction 对象转换为你项目中定义的 ImageSearchResult 对象
                ImageSearchResult result = new ImageSearchResult();

                // --- 核心字段映射 ---
                // picName 通常存储的是图片的 URL 或标识符，可以作为缩略图或来源地址
                String picName = auction.getPicName();
                result.setThumbUrl(picName); // 将 picName 作为缩略图地址
                result.setFromUrl(picName);  // 同时也可以作为来源地址，或者从 customContent 中解析

                // --- 从 customContent 解析更多信息 (如果需要) ---
                String customContent = auction.getCustomContent();
                if (customContent != null && !customContent.isEmpty()) {
                    // 如果 customContent 包含 JSON 格式的 URL 信息，可以在这里解析
                    // 例如: {"imageUrl": "...", "sourceUrl": "..."}
                    // 需要引入 JSON 解析库 (如 Jackson, Gson)
                    // 示例伪代码:
                    // try {
                    //     ObjectMapper mapper = new ObjectMapper();
                    //     JsonNode jsonNode = mapper.readTree(customContent);
                    //     String imageUrlFromContent = jsonNode.path("imageUrl").asText();
                    //     String sourceUrlFromContent = jsonNode.path("sourceUrl").asText();
                    //     if (StrUtil.isNotBlank(imageUrlFromContent)) {
                    //         result.setThumbUrl(imageUrlFromContent);
                    //     }
                    //     if (StrUtil.isNotBlank(sourceUrlFromContent)) {
                    //         result.setFromUrl(sourceUrlFromContent);
                    //     }
                    // } catch (Exception e) {
                    //     log.warn("解析 customContent 失败: {}", customContent, e);
                    //     // 解析失败则使用 picName
                    //     result.setThumbUrl(picName);
                    //     result.setFromUrl(picName);
                    // }
                    // 为了简单，当前仍使用 picName
                }

                results.add(result);
            }

            log.info("阿里云以图搜图成功，返回结果数量: {}", results.size());
            return results;

        } catch (TeaException e) {
            log.error("阿里云以图搜图失败，Code: {}, Message: {}, Data: {}", e.getCode(), e.getMessage(), e.getData(), e);
            // 根据错误码或消息进行更具体的错误处理，或者统一抛出业务异常
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "阿里云以图搜图失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("阿里云以图搜图发生未知错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "阿里云以图搜图发生未知错误: " + e.getMessage());
        } finally {
        }
    }

    /**
     * 从 URL 下载图片内容并返回 InputStream
     *
     * @param imageUrl 图片的 URL
     * @return 图片的 InputStream，如果下载失败则返回 null
     */
    private InputStream downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            // 设置一些常见的请求头，模拟浏览器访问，避免被拒绝
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setConnectTimeout(5000); // 设置连接超时
            connection.setReadTimeout(10000);  // 设置读取超时
            return connection.getInputStream();
        } catch (IOException e) {
            log.error("下载图片失败，URL: {}", imageUrl, e);
            return null; // 返回 null 表示下载失败
        }
    }
}