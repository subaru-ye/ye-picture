package com.ye.yepicturebackend.api.imageSearch;

import com.ye.yepicturebackend.api.imageSearch.model.ImageSearchResult;
import com.ye.yepicturebackend.api.imageSearch.sub.GetImageFirstUrlApi;
import com.ye.yepicturebackend.api.imageSearch.sub.GetImageListApi;
import com.ye.yepicturebackend.api.imageSearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 以图搜图功能的外观类
 * 内部协作流程如下：
 * <ol>
 *   <li>【步骤1】通过目标图片 URL 获取搜索引擎结果页地址（如百度/Google 图片搜索页）</li>
 *   <li>【步骤2】从结果页中提取首张相似图片的详情页跳转链接</li>
 *   <li>【步骤3】解析该详情页，获取所有相似图片的结构化信息列表</li>
 * </ol>
 * </p>
 * <p>
 * 所有具体实现逻辑由以下子模块完成（遵循单一职责原则）：
 * <ul>
 *   <li>{@link GetImagePageUrlApi}：负责生成或获取以图搜图的结果页 URL</li>
 *   <li>{@link GetImageFirstUrlApi}：负责从结果页中提取首图详情页链接</li>
 *   <li>{@link GetImageListApi}：负责解析详情页并返回结构化图片列表</li>
 * </ul>
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 执行完整的以图搜图流程，返回相似图片的结构化结果列表
     *
     * @param imageUrl 待搜索的原始图片 URL，必须为公网可访问的 HTTP/HTTPS 链接
     * @return 相似图片结果列表，每个元素包含图片标题、URL、来源网站等核心信息
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        // 步骤1: 调用子模块，根据输入图片 URL 生成搜索引擎的结果页地址
        // 例如：将图片上传至百度识图并返回其搜索结果页 URL
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);

        // 步骤2: 从结果页中解析出首张相似图片的详情页跳转链接
        // （通常用于进入包含更多相似图的聚合页面）
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);

        // 步骤3: 访问该详情页，提取所有相似图片的结构化数据
        // 返回格式统一的 ImageSearchResult 列表，供上层业务使用
        return GetImageListApi.getImageList(imageFirstUrl);
    }
}