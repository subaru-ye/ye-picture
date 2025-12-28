package com.ye.yepicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传图片文件到腾讯云COS并自动处理
     *
     * @param key  原图在COS中的唯一存储标识
     * @param file 待上传的本地图片文件
     * @return PutObjectResult 腾讯云COS返回的上传结果，包含：
     * - 原图的存储信息（ETag、版本号等）
     * - 图片处理结果（通过getPicInfo()获取原图信息及处理后文件的存储路径）
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        // 对图片进行处理
        PicOperations picOperations = new PicOperations();
        List<PicOperations.Rule> rules = new ArrayList<>();
        picOperations.setIsPicInfo(1);  // 1 返回原图信息
        // 图片压缩 (转成webp格式)
        PicOperations.Rule compressRule = new PicOperations.Rule();
        String webKey = FileUtil.mainName(key) + ".webp";
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setFileId(webKey);
        rules.add(compressRule);
        // 缩略图处理
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            thumbnailRule.setFileId(thumbnailKey);
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    public void deleteObject(String key) throws CosClientException {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

}
