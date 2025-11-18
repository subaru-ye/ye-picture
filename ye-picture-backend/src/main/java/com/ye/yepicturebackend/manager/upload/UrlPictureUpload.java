package com.ye.yepicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL图片上传处理器
 * 功能：处理从网络URL上传图片的场景，包括URL合法性校验、图片格式/大小验证及文件下载
 * 继承自PictureUploadTemplate，实现了针对URL类型输入源的抽象方法
 */
@Slf4j
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    /**
     * 校验URL图片输入源的合法性
     * 校验逻辑包括：非空检查、URL格式验证、协议限制、文件存在性及类型/大小限制
     *
     * @param inputSource 输入源，实际类型为String（图片URL地址）
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 1.校验非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl),
                ErrorCode.PARAMS_ERROR, "url地址为空");
        // 2.校验url格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式错误");
        }
        // 3.校验url的协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持HTTP或HTTPS协议的文件地址");
        // 4.发送head请求验证文件是否存在
        try (HttpResponse httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl).execute()) {
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                // 文件不存在直接返回
                return;
            }
            // 5.文件存在,文件类型校验
            String contentType = httpResponse.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg"
                        , "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 6.文件存在,文件大小校验
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > 3 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过3M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        }
    }

    /**
     * 获取URL对应的原始文件名
     *
     * @param inputSource 输入源，实际类型为String（图片URL地址）
     * @return 从URL中提取的文件名（如"example"，取自"<a href="https://xxx.com/example.jpg">...</a>"）
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl) + "." + FileUtil.extName(fileUrl);
    }

    /**
     * 处理URL输入源，将远程图片下载到本地临时文件
     *
     * @param inputSource 输入源，实际类型为String（图片URL地址）
     * @param file        本地临时文件对象，用于存储下载的图片内容
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file);
    }
}
