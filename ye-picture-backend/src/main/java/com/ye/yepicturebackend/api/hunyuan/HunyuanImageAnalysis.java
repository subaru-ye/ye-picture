package com.ye.yepicturebackend.api.hunyuan;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import com.tencentcloudapi.hunyuan.v20230901.models.*;
import com.ye.yepicturebackend.api.hunyuan.model.ImageAnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 腾讯云混元大模型服务 - 图片内容分析专用
 */
@Slf4j
@Service
public class HunyuanImageAnalysis {

    @Resource
    private HunyuanClient hunyuanClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分析图片并自动生成简介、标签和分类
     *
     * @param imageUrl 要分析的图片URL（公网可访问）
     * @return 图片分析结果，直接包含description、tags、category三个参数
     */
    public ImageAnalysisResult analyzeImage(String imageUrl) {
        try {
            log.info("开始分析图片: {}", imageUrl);

            // 参数校验
            validateImageUrl(imageUrl);

            // 构建专业的Prompt
            String prompt = buildAnalysisPrompt();

            // 创建对话请求
            ChatCompletionsRequest request = buildChatRequest(imageUrl, prompt);

            // 调用API
            ChatCompletionsResponse response = hunyuanClient.ChatCompletions(request);

            // 解析响应
            String jsonResponse = AbstractModel.toJsonString(response);
            log.debug("混元API原始响应: {}", jsonResponse);

            // 直接解析为结果对象
            ImageAnalysisResult result = parseAIResponse(jsonResponse);

            // 验证分析结果
            validateAnalysisResult(result);

            log.info("图片分析完成: 分类={}, 标签={}", result.getCategory(), result.getTags());

            return result;

        } catch (Exception e) {
            log.error("图片分析失败, imageUrl: {}", imageUrl, e);
            throw new RuntimeException("图片分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证图片URL格式
     */
    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("图片URL不能为空");
        }
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            throw new IllegalArgumentException("图片URL必须以http://或https://开头");
        }
    }

    /**
     * 验证分析结果的有效性
     */
    private void validateAnalysisResult(ImageAnalysisResult result) {
        if (result == null) {
            throw new RuntimeException("AI分析结果为空");
        }
        if (result.getDescription() == null || result.getDescription().trim().isEmpty()) {
            throw new RuntimeException("AI返回的description为空");
        }
        if (result.getTags() == null || result.getTags().trim().isEmpty()) {
            throw new RuntimeException("AI返回的tags为空");
        }
        if (result.getCategory() == null || result.getCategory().trim().isEmpty()) {
            throw new RuntimeException("AI返回的category为空");
        }
    }

    /**
     * 构建专业的图片分析Prompt
     */
    private String buildAnalysisPrompt() {
        return "你是一个专业的图片内容分析师，负责为图库网站生成图片的简介、标签和分类。\n\n" +
                "请仔细分析用户上传的图片，并生成以下信息：\n" +
                "1.  简介（description）: 用一段流畅、简洁、有吸引力的中文描述图片内容，长度在10-20字之间。描述应涵盖主要物体、场景、氛围和色彩。\n" +
                "2.  标签（tags）: 生成2到3个关键词作为标签，用中文逗号\"，\"分隔。标签应涵盖图中物体、场景、风格、颜色、情绪等。\n" +
                "3.  分类（category）: 从以下固定分类列表中选出最贴切的一个：[自然风光，城市建筑，人像摄影，静物特写，动物植物，美食餐饮，抽象艺术，商务科技]。如果都不匹配，请输出\"其他\"。\n\n" +
                "请严格只返回一个JSON对象，不要有任何额外的解释、前缀或后缀。\n\n" +
                "示例输出：\n" +
                "{\n" +
                "  \"description\": \"日落时分，金色海滩上一只金毛犬欢快奔跑，海浪轻抚沙滩，画面温馨充满活力。\",\n" +
                "  \"tags\": \"金毛犬，海滩，日落\",\n" +
                "  \"category\": \"动物植物\"\n" +
                "}\n\n" +
                "现在，请分析当前图片：";
    }

    /**
     * 构建与混元大模型的对话请求
     */
    private ChatCompletionsRequest buildChatRequest(String imageUrl, String prompt) {
        ChatCompletionsRequest request = new ChatCompletionsRequest();
        request.setModel("hunyuan-vision");
        request.setStream(false);

        Message userMessage = new Message();
        userMessage.setRole("user");

        Content[] contents = new Content[2];

        Content textContent = new Content();
        textContent.setType("text");
        textContent.setText(prompt);
        contents[0] = textContent;

        Content imageContent = new Content();
        imageContent.setType("image_url");
        ImageUrl imageUrlObj = new ImageUrl();
        imageUrlObj.setUrl(imageUrl);
        imageContent.setImageUrl(imageUrlObj);
        contents[1] = imageContent;

        userMessage.setContents(contents);
        Message[] messages = new Message[]{userMessage};
        request.setMessages(messages);

        return request;
    }

    /**
     * 解析AI回复内容为结构化对象
     */
    private ImageAnalysisResult parseAIResponse(String jsonResponse) {
        try {
            // 解析JSON响应
            Map<String, Object> responseMap = objectMapper.readValue(
                    jsonResponse, new TypeReference<Map<String, Object>>() {
                    });

            // 检查关键字段
            if (!responseMap.containsKey("Choices")) {
                throw new RuntimeException("API响应格式错误：缺少Choices字段");
            }

            // 提取Choices数组
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("Choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("API响应格式错误：Choices为空");
            }

            // 提取第一个Choice
            Map<String, Object> firstChoice = choices.get(0);
            if (!firstChoice.containsKey("Message")) {
                throw new RuntimeException("API响应格式错误：缺少Message字段");
            }

            Map<String, Object> message = (Map<String, Object>) firstChoice.get("Message");
            String content = (String) message.get("Content");

            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("AI返回的内容为空");
            }

            log.debug("AI回复内容: {}", content);

            // 直接解析为结果对象
            return objectMapper.readValue(content, ImageAnalysisResult.class);

        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage());
            throw new RuntimeException("解析AI响应失败: " + e.getMessage(), e);
        }
    }
}