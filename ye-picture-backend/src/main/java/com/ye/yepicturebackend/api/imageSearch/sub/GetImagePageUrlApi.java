package com.ye.yepicturebackend.api.imageSearch.sub;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取以图搜图的页面地址
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取搜索的结果页URL
     *
     * @param imageUrl 待搜索的图片URL
     * @return 百度图片在百度图片搜索结果页的URL地址
     */
    public static String getImagePageUrl(String imageUrl) {
        // 1. 准备请求参数：构造百度接口所需的表单数据
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + System.currentTimeMillis();

        try {
            // 2. 发送 POST 请求到百度接口
            HttpResponse response = HttpRequest.post(url)
                    .form(formData)
                    .header("acs-token", RandomUtil.randomString(1))
                    .timeout(5000)
                    .execute();
            // 判断响应状态
            int status = response.getStatus();
            ThrowUtils.throwIf(HttpStatus.HTTP_OK != status,
                    ErrorCode.OPERATION_ERROR, "接口调用失败");
            // 解析响应
            String responseBody = response.body();
            log.info("百度接口完整响应体：{}", responseBody);
//            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);
            Map<?, ?> rawMap = JSONUtil.toBean(responseBody, Map.class);
            Map<String, Object> result = typeConversionMap(rawMap, String.class, Object.class);

            // 3. 处理响应结果
            if (!Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 提取结果数据中的原始URL
//            Map<String, Object> data = (Map<String, Object>) result.get("data");
            Object dataObj = result.get("data");
            Map<String, Object> data = typeConversionMap(dataObj, String.class, Object.class);

            String rawUrl = (String) data.get("url");
            // 对 URL 进行解码
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // 校验解码后的URL有效性
            ThrowUtils.throwIf(searchResultUrl == null,
                    ErrorCode.OPERATION_ERROR, "未返回有效结果");

            // 4. 返回结果
            return searchResultUrl;
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    /**
     * 将任意对象安全转换为指定键值类型的 Map 集合
     *
     * @param obj    待转换的对象（支持 Map 类型、null、非 Map 类型）
     * @param tClass 目标 Map 的键（Key）对应的 Class 类型（如 String.class）
     * @param vClass 目标 Map 的值（Value）对应的 Class 类型（如 Object.class）
     * @param <K>    目标 Map 键的泛型类型
     * @param <V>    目标 Map 值的泛型类型
     * @return 转换后的 HashMap<K, V>，永远非 null（转换成功则含原 Map 数据，失败则为空）
     */
    public static <K, V> Map<K, V> typeConversionMap(Object obj, Class<K> tClass, Class<V> vClass) {
        HashMap<K, V> result = new HashMap<>();
        if (obj instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) (obj);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(tClass.cast(entry.getKey()), vClass.cast(entry.getValue()));
            }
        }
        return result;
    }

    // 测试方法
    public static void main(String[] args) {
        String imageUrl = "https://tonight-picture-1352619299.cos." +
                "ap-nanjing.myqcloud.com/public/1971555205276835842/" +
                "origin/2025-09-26_SFreRh8U.jpg";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}

