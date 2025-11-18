package com.ye.yepicturebackend.api.imageSearch.sub;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ye.yepicturebackend.api.imageSearch.model.ImageSearchResult;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 获取以图搜图结果列表
 */
@Slf4j
public class GetImageListApi {

    /**
     * 获取图片搜索结果列表
     *
     * @param url 百度以图搜图结果页的接口URL
     * @return 标准化的图片搜索结果列表，每个元素为ImageSearchResult实体
     */
    public static List<ImageSearchResult> getImageList(String url) {
        try {
            // 1. 构建GET请求并执行
            HttpResponse response = HttpUtil.createGet(url).execute();

            // 2. 提取响应核心信息：状态码（判断请求是否成功）、响应体（接口返回的JSON数据）
            int statusCode = response.getStatus();
            String body = response.body();

            // 3. 校验响应状态
            if (statusCode == 200) {
                // 4. 委托处理响应体：将JSON字符串解析为ImageSearchResult列表
                return processResponse(body);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "接口调用失败，响应状态码：" + statusCode);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取图片列表失败，请求URL：{}", url, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "获取图片列表失败：" + e.getMessage());
        }
    }

    /**
     * 解析百度接口的JSON响应体，转换为ImageSearchResult列表
     *
     * @param responseBody 百度接口返回的JSON格式字符串（含图片列表数据）
     * @return 标准化的ImageSearchResult列表，与JSON数组"list"中的元素一一对应
     */
    private static List<ImageSearchResult> processResponse(String responseBody) {
        // 1. 解析JSON根对象
        JSONObject jsonObject = new JSONObject(responseBody);

        // 2. 校验并提取"data"节点
        if (!jsonObject.containsKey("data")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "未获取到图片列表，JSON响应缺失\"data\"节点");
        }
        JSONObject data = jsonObject.getJSONObject("data");

        // 3. 校验并提取"list"数组节点
        if (!data.containsKey("list")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "未获取到图片列表，JSON响应缺失\"data.list\"节点");
        }
        JSONArray list = data.getJSONArray("list");

        // 4. JSON数组转实体列表
        return JSONUtil.toList(list, ImageSearchResult.class);
    }

    // 测试方法
    public static void main(String[] args) {
        String url = "https://graph.baidu.com/ajax/pcsimi?" +
                "carousel=503&entrance=GENERAL&extUiData%" +
                "5BisLogoShow%5D=1&inspire=general_pc&limit=30&next=2&" +
                "render_type=card&session_id=16156639229421145195&sign" +
                "=12602bffcfaad4a12d98c01760003244&tk=01dc9&tpl_from=pc";
        List<ImageSearchResult> imageList = getImageList(url);
        System.out.println("搜索成功，获取到的图片数量：" + imageList.size());
        System.out.println("图片列表详情：" + imageList);
    }
}