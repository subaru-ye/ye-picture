package com.ye.yepicturebackend.api.imageSearch;

import com.ye.yepicturebackend.api.imageSearch.model.ImageSearchResult;
import com.ye.yepicturebackend.api.imageSearch.sub.GetImageFirstUrlApi;
import com.ye.yepicturebackend.api.imageSearch.sub.GetImageListApi;
import com.ye.yepicturebackend.api.imageSearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 以图搜图功能外观类（Facade）
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl 待搜索的图片公网URL
     * @return 以图搜图的结果列表，每个元素包含单张图片的核心信息
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {

        // 1. 获取以图搜图的结果页URL
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);

        // 2. 提取首图跳转链接
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);

        // 3. 最终的图片搜索结果列表
        return GetImageListApi.getImageList(imageFirstUrl);
    }

    // 测试方法
    public static void main(String[] args) {
        // 测试用的待搜索图片URL
        String imageUrl = "https://tonight-picture-1352619299." +
                "cos.ap-nanjing.myqcloud.com/public" +
                "/1971555205276835842/origin/2025-09-26_SFreRh8U.jpg";
        // 执行完整以图搜图流程
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        // 打印结果
        System.out.println("以图搜图测试成功，结果列表长度：" + resultList.size());
        System.out.println("结果列表详情：" + resultList);
    }
}