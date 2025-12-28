package com.ye.yepicturebackend.api.hunyuan;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import com.tencentcloudapi.hunyuan.v20230901.models.*;
import com.ye.yepicturebackend.api.hunyuan.model.ImageAnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 腾讯云混元大模型（HunYuan Vision）图片智能分析服务
 * <p>
 * 本服务封装了调用腾讯云混元多模态大模型（hunyuan-vision）的能力，
 * 用于对公网可访问的图片进行内容理解，自动生成：
 * - 图片简介（description）
 * - 语义标签（tags）
 * - 所属分类（category）
 * </p>
 */
@Slf4j
@Service
public class HunyuanImageAnalysis {

    /**
     * 注入已配置好的腾讯云 HunYuan 客户端（含 SecretId/SecretKey 和 Region）
     */
    @Resource
    private HunyuanClient hunyuanClient;

    /**
     * Jackson 的 ObjectMapper 实例，用于 JSON 序列化与反序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 主入口方法：调用混元大模型对指定图片进行智能分析
     *
     * @param imageUrl 待分析的图片 URL，必须为公网可访问的 HTTP/HTTPS 链接
     * @return {@link ImageAnalysisResult} 包含 description、tags、category 三个字段的结构化结果
     */
    public ImageAnalysisResult analyzeImage(String imageUrl) {
        try {
            log.info("开始分析图片: {}", imageUrl);

            // 1. 参数校验
            validateImageUrl(imageUrl);

            // 2. 构建引导 AI 输出结构化 JSON 的专业 Prompt
            String prompt = buildAnalysisPrompt();

            // 3. 创建对话请求: 包含图片和文本指令的多模态请求
            ChatCompletionsRequest request = buildChatRequest(imageUrl, prompt);

            // 4. 调用混元大模型 API
            ChatCompletionsResponse response = hunyuanClient.ChatCompletions(request);

            // 5. 将 API 响应转为 JSON 字符串
            String jsonResponse = AbstractModel.toJsonString(response);
            log.debug("混元API原始响应: {}", jsonResponse);

            // 6. 从响应中提取并解析 AI 生成的 JSON 内容
            ImageAnalysisResult result = parseAIResponse(jsonResponse);

            // 7. 验证 AI 返回结果是否符合业务要求
            validateAnalysisResult(result);

            log.info("图片分析完成: 分类={}, 标签={}", result.getCategory(), result.getTags());

            return result;

        } catch (Exception e) {
            log.error("图片分析失败, imageUrl: {}", imageUrl, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片分析失败，异常原因：" + e.getMessage());
        }
    }

    /**
     * 校验图片 URL 是否合法
     * <p>
     * 要求：
     * - 非 null 且非空字符串
     * - 必须以 http:// 或 https:// 开头（确保为有效网络地址）
     * </p>
     *
     * @param imageUrl 待校验的图片 URL
     */
    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片URL不能为空");
        }
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片URL必须以http://或https://开头");
        }
    }

    /**
     * 验证 AI 返回的分析结果是否满足业务完整性要求
     * <p>
     * 要求：
     * - result 对象非 null
     * - description、tags、category 三个字段均非 null 且非空（trim 后）
     * </p>
     *
     * @param result AI 生成的分析结果对象
     */
    private void validateAnalysisResult(ImageAnalysisResult result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI分析结果为空");
        }
        if (result.getDescription() == null || result.getDescription().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI返回的description为空");
        }
        if (result.getTags() == null || result.getTags().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI返回的tags为空");
        }
        if (result.getCategory() == null || result.getCategory().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI返回的category为空");
        }
    }

    /**
     * 构建引导混元大模型输出结构化 JSON 的系统级 Prompt
     * <p>
     * 设计要点：
     * - 明确角色：专业图片内容分析师
     * - 精确定义输出字段（description/tags/category）
     * - 限制 category 为预设枚举值
     * - 要求仅返回纯 JSON，无额外文本（便于解析）
     * - 提供标准示例，降低模型幻觉风险
     * </p>
     *
     * @return 格式严谨、意图明确的文本 Prompt
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
     * 构造混元大模型多模态对话请求（文本 + 图片）
     * <p>
     * 使用 hunyuan-vision 模型，构造包含：
     * - 文本指令（Prompt）
     * - 图片 URL（通过 image_url 类型内容传入）
     * 的 user 消息
     * </p>
     *
     * @param imageUrl 图片的公网 URL
     * @param prompt   引导 AI 行为的文本指令
     * @return 配置完整的 {@link ChatCompletionsRequest} 请求对象
     */
    private ChatCompletionsRequest buildChatRequest(String imageUrl, String prompt) {
        // 1. 创建 API 请求对象，用于封装调用参数
        ChatCompletionsRequest request = new ChatCompletionsRequest();

        // 2. 指定使用支持图文理解的多模态模型
        //    "hunyuan-vision" 是腾讯云混元专用于视觉任务的模型名称
        request.setModel("hunyuan-vision");

        // 3. 设置为非流式响应（同步调用）
        //    false 表示一次性返回完整结果
        request.setStream(false);

        // 4. 创建一条用户消息（User Message）
        //    混元 API 要求以对话形式传入：[{"role": "user", "contents": [...]}]
        Message userMessage = new Message();
        userMessage.setRole("user");

        // 5. 准备多模态内容数组：混元 Vision 支持在一个消息中同时传入文本和图片
        //    数组顺序无严格要求，但建议先文本后图片
        Content[] contents = new Content[2];

        // --- 构建文本内容部分 ---
        // 6. 创建文本类型的内容块
        Content textContent = new Content();
        textContent.setType("text");        // 明确指定内容类型为纯文本
        textContent.setText(prompt);        // 设置 Prompt 指令，引导 AI 输出结构化 JSON
        contents[0] = textContent;          // 放入内容数组第一个位置

        // --- 构建图片内容部分 ---
        // 7. 创建图片类型的内容块
        Content imageContent = new Content();
        imageContent.setType("image_url");  // 指定内容类型为图片 URL（混元仅支持 URL 方式传图）

        // 8. 封装图片 URL 对象
        //    注意：腾讯云 SDK 要求图片通过 ImageUrl 对象传入，而非直接字符串
        ImageUrl imageUrlObj = new ImageUrl();
        imageUrlObj.setUrl(imageUrl);       // 设置图片的公网可访问链接
        imageContent.setImageUrl(imageUrlObj);
        contents[1] = imageContent;         // 放入内容数组第二个位置

        // 9. 将多模态内容绑定到用户消息
        userMessage.setContents(contents);

        // 10. 将用户消息放入消息列表（目前只有一条用户消息）
        //     混元 Vision 当前不支持多轮对话上下文，通常只传一条 user 消息即可
        Message[] messages = {userMessage};
        request.setMessages(messages);

        // 11. 返回构造完成的请求对象，供后续 API 调用使用
        return request;
    }

    /**
     * 从混元 API 响应中提取并解析 AI 生成的 JSON 内容
     * <p>
     * 流程：
     * 1. 将整个响应解析为 Map 结构
     * 2. 定位到 Choices[0].Message.Content 字段
     * 3. 将该 Content 字符串再次解析为 {@link ImageAnalysisResult} 对象
     * </p>
     *
     * @param jsonResponse 混元 API 返回的完整 JSON 响应字符串
     * @return 解析后的结构化分析结果
     */
    private ImageAnalysisResult parseAIResponse(String jsonResponse) {
        try {
            // 第一层解析：整个 API 响应
            Map<String, Object> responseMap = objectMapper.readValue(
                    jsonResponse, new TypeReference<>() {
                    }
            );

            // 校验顶层结构
            if (!responseMap.containsKey("Choices")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "API响应格式错误：缺少Choices字段");
            }

            // 提取 Choices 数组
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("Choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "API响应格式错误：Choices为空");
            }

            // 获取第一个 Choice
            Map<String, Object> firstChoice = choices.get(0);
            if (!firstChoice.containsKey("Message")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "API响应格式错误：缺少Message字段");
            }

            // 提取 AI 生成的内容（即 JSON 字符串）
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("Message");
            String content = (String) message.get("Content");

            if (content == null || content.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI返回的内容为空");
            }

            log.debug("AI回复内容: {}", content);

            // 第二层解析：将 content 字符串解析为目标结果对象
            return objectMapper.readValue(content, ImageAnalysisResult.class);

        } catch (Exception e) {
            log.error("解析AI响应失败: 原始响应={}", jsonResponse, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析AI响应失败: " + e.getMessage());
        }
    }
}