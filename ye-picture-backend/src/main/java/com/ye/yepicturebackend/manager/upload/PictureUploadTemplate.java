package com.ye.yepicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;


/**
 * 文件图片上传
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片到腾讯云COS（重构：明确原图/压缩图/缩略图地址映射）
     *
     * @param inputSource      本地图片输入源对象（支持URL、File等）
     * @param uploadPathPrefix 图片在COS中的存储路径前缀（如"user/123"）
     * @return UploadPictureResult 图片上传结果封装对象（含原图+压缩图+缩略图完整信息）
     */
    public UploadResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片合法性
        validPicture(inputSource);
        // 2. 构建上传文件名和图片存储路径
        String uuid = RandomUtil.randomString(8);
        String originFilename = getOriginFilename(inputSource);
        // 清洗文件名中的非法参数
        originFilename = cleanFilename(originFilename);
        String fileSuffix = FileUtil.getSuffix(originFilename);
        // 上传文件名
        String originUploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, fileSuffix);
        // COS的完整存储路径
        String originUploadPath = String.format("/%s/origin/%s", uploadPathPrefix, originUploadFilename);
        File tempFile = null;
        try {
            // 3. 创建临时文件
            tempFile = File.createTempFile("origin_pic_", fileSuffix);
            processFile(inputSource, tempFile);
            // 4. 调用COS管理工具上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(originUploadPath, tempFile);
            // 5. 提取COS返回的元信息（原图+压缩图+缩略图）
            ImageInfo originImageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> ciObjectList = processResults.getObjectList();
            // 6. 构建完整结果（原图信息 + 压缩图/缩略图信息）
            UploadResult result = new UploadResult();
            // 原图片
            fillOriginPictureInfo(result, originImageInfo, originFilename, tempFile, originUploadPath);
            // 压缩图和缩略图
            fillCompressAndThumbnailInfo(result, ciObjectList);
            return result;
        } catch (Exception e) {
            log.error("图片上传到腾讯云COS失败（输入源：{}，路径前缀：{}）", inputSource, uploadPathPrefix, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败：" + e.getMessage());
        } finally {
            // 7. 清理临时文件
            deleteTempFile(tempFile);
        }
    }
    /**
     * 【新增】清洗文件名，剔除URL查询参数和非法字符
     */
    private String cleanFilename(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "default_pic";
        }
        // 剔除URL查询参数（如 ?OSSAccessKeyId=...）
        int paramIndex = filename.indexOf("?");
        if (paramIndex > 0) {
            filename = filename.substring(0, paramIndex);
        }
        // 剔除非法字符（如 \ / * ? : " < > | 等）
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 填充原图片基础信息
     *
     * @param result           上传结果对象（待填充）
     * @param originImageInfo  COS返回的原图片元信息
     * @param originFilename   原始文件名（用于提取图片名称）
     * @param tempFile         原图片临时文件（用于获取文件大小）
     * @param originUploadPath 原图片在COS的存储路径（用于构建访问URL）
     */
    private void fillOriginPictureInfo(
            UploadResult result, ImageInfo originImageInfo,
            String originFilename, File tempFile, String originUploadPath) {
        // 图片宽高
        int originWidth = originImageInfo.getWidth();
        int originHeight = originImageInfo.getHeight();
        double picScale = NumberUtil.round(originWidth * 1.0 / originHeight, 2).doubleValue();
        // 设置结果属性
        result.setPicName(FileUtil.mainName(originFilename));
        result.setPicFormat(originImageInfo.getFormat());
        result.setPicWidth(originWidth);
        result.setPicHeight(originHeight);
        result.setPicScale(picScale);
        result.setPicColor(originImageInfo.getAve());
        try {
            result.setPicSize(FileUtil.size(tempFile));
        } catch (Exception e) {
            log.warn("获取原图片大小失败（文件名：{}），使用默认值0", originFilename, e);
            result.setPicSize(0L);
        }

        // 存储标准化的 COS Key
        String normalizedKey = originUploadPath.startsWith("/") ? originUploadPath.substring(1) : originUploadPath;
        result.setOriginKey(normalizedKey);
    }

    /**
     * 填充压缩图和缩略图信息
     *
     * @param result       上传结果对象（待填充）
     * @param ciObjectList COS返回的CI处理对象列表（含压缩图、缩略图）
     */
    private void fillCompressAndThumbnailInfo(UploadResult result, List<CIObject> ciObjectList) {
        if (CollUtil.isEmpty(ciObjectList)) {
            log.warn("COS未返回压缩图/缩略图信息，仅返回原图片");
            return;
        }
        CIObject compressCiObj = ciObjectList.get(0);
        CIObject thumbnailCiObj = ciObjectList.size() > 1 ? ciObjectList.get(1) : compressCiObj;

        // 直接存储 COS Key
        result.setCompressKey(compressCiObj.getKey());
        result.setThumbnailKey(thumbnailCiObj.getKey());
    }

    /**
     * 校验输入源的合法性
     *
     * @param inputSource 图片输入源对象
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     *
     * @param inputSource 图片输入源对象
     * @return String 原始文件名，若无法获取可返回默认名称
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     *
     * @param inputSource 图片输入源对象
     * @param file        目标本地临时文件对象
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 删除临时文件
     *
     * @param file 待删除的临时文件对象，可为null
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 执行删除操作
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}

