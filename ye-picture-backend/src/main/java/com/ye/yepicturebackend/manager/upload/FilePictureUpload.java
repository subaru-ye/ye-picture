package com.ye.yepicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 本地文件上传处理器
 * 功能：处理前端上传的本地图片文件，包括文件非空校验、大小限制及格式验证
 * 继承自PictureUploadTemplate，实现了针对MultipartFile类型输入源的抽象方法
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {

    /**
     * 校验本地文件输入源的合法性
     * 校验逻辑包括：文件非空检查、大小限制（不超过3M）及格式验证（仅支持指定图片类型）
     *
     * @param inputSource 输入源，实际类型为MultipartFile（前端上传的文件对象）
     */
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 校验非空
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 校验大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 3 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 3M");
        // 校验格式
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 获取本地文件的原始文件名（包含扩展名）
     *
     * @param inputSource 输入源，实际类型为MultipartFile（前端上传的文件对象）
     * @return 前端上传的原始文件名（如"example.jpg"）
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 处理本地文件输入源，将上传的文件内容转移到本地临时文件
     *
     * @param inputSource 输入源，实际类型为MultipartFile（前端上传的文件对象）
     * @param file        本地临时文件对象，用于存储文件内容
     * @throws Exception 当文件转移失败（如磁盘写入错误）时抛出
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}
