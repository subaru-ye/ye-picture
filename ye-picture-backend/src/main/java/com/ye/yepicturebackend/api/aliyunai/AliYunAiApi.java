package com.ye.yepicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ye.yepicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阿里云AI图像扩展（Image Out-Painting）接口调用工具类
 */
@Slf4j
@Component
public class AliYunAiApi {
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 用于发起图像扩展任务的创建请求，请求方式为POST
    public static final String CREATE_OUT_PAINTING_TASK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 占位符%s需替换为具体任务ID（taskId），请求方式为GET
    public static final String GET_OUT_PAINTING_TASK_URL =
            "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    /**
     * 调用阿里云AI接口，创建图像扩展任务
     *
     * @param createOutPaintingTaskRequest 图像扩展任务请求参数封装对象
     *                                     包含模型指定（默认image-out-painting）、输入图像URL、扩展参数（如扩展比例、像素填充等）
     * @return CreateOutPaintingTaskResponse 任务创建响应对象
     * 成功时包含taskId（任务唯一标识）和初始taskStatus（通常为PENDING/RUNNING）；失败时包含错误码和错误信息
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        log.info("进入阿里云AI扩图方法，接收的请求参数：{}", JSONUtil.toJsonStr(createOutPaintingTaskRequest));


        // 1. 请求参数合法性校验
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null,
                ErrorCode.OPERATION_ERROR, "扩图参数为空");

        // 打印最终发送给阿里云的请求体
        String requestBody = JSONUtil.toJsonStr(createOutPaintingTaskRequest);
        log.info("发送给阿里云AI的请求体：{}", requestBody);

        // 2. 构建POST请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));

        // 3. 执行HTTP请求
        try (HttpResponse httpResponse = httpRequest.execute()) {

            // 打印阿里云的响应状态和响应体
            int statusCode = httpResponse.getStatus();
            String responseBody = httpResponse.body();
            log.info("阿里云AI扩图响应状态码：{}，响应体：{}", statusCode, responseBody);

            // 4. 校验HTTP响应状态
            if (!httpResponse.isOk()) {
                log.error("创建图像扩展任务请求异常，响应内容：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }

            // 5. 解析响应结果
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);

            // 6. 校验接口业务错误
            String errorCode = response.getCode();
            if (StrUtil.isNotBlank(errorCode)) {
                String errorMessage = response.getMessage();
                log.error("AI 扩图任务创建失败，错误码:{}, 错误信息:{}", errorCode, errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图接口响应异常");
            }

            // 7. 任务创建成功，返回响应对象
            return response;
        }catch (Exception e) {
            log.error("调用阿里云AI扩图接口时发生异常，请求参数：{}", requestBody, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图接口调用失败，异常原因：" + e.getMessage());
        }
    }

    /**
     * 调用阿里云AI接口，查询图像扩展任务的状态及结果
     *
     * @param taskId 图像扩展任务的唯一标识（从createOutPaintingTask方法的返回结果中获取）
     * @return GetOutPaintingTaskResponse 任务查询响应对象
     * 包含任务全生命周期信息：taskStatus（当前状态）、submitTime/scheduledTime/endTime（时间节点）、
     * outputImageUrl（任务成功时返回扩展后图像URL）、taskMetrics（任务统计指标）；失败时包含错误码和错误信息
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        // 1. 请求参数合法性校验
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        // 2. 构建GET请求,执行请求并解析结果
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            // 3. 校验HTTP响应状态
            if (!httpResponse.isOk()) {
                log.error("查询图像扩展任务异常，taskId:{}, 响应内容：{}", taskId, httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            // 5. 解析响应结果
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}