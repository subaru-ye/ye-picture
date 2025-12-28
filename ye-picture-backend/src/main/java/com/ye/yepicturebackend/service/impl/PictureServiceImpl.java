package com.ye.yepicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ye.yepicturebackend.api.aliyunai.AliYunAiApi;
import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ye.yepicturebackend.api.hunyuan.HunyuanImageAnalysis;
import com.ye.yepicturebackend.api.hunyuan.model.ImageAnalysisResult;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.constant.RabbitMQConstant;
import com.ye.yepicturebackend.constant.UserConstant;
import com.ye.yepicturebackend.model.dto.picture.delete.DeletePictureResult;
import com.ye.yepicturebackend.model.dto.picture.edit.EditBatchRequest;
import com.ye.yepicturebackend.model.dto.picture.query.QueryPictureRequest;
import com.ye.yepicturebackend.model.dto.picture.task.AiExtendRequest;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadRequest;
import com.ye.yepicturebackend.utils.PictureVoConverter;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.manager.upload.CosManager;
import com.ye.yepicturebackend.manager.auth.SpaceUserAuthManager;
import com.ye.yepicturebackend.manager.auth.StpKit;
import com.ye.yepicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.ye.yepicturebackend.manager.upload.FilePictureUpload;
import com.ye.yepicturebackend.manager.upload.PictureUploadTemplate;
import com.ye.yepicturebackend.manager.upload.UrlPictureUpload;
import com.ye.yepicturebackend.model.dto.picture.review.ReviewNoticeMessage;
import com.ye.yepicturebackend.model.vo.picture.PictureVO;
import com.ye.yepicturebackend.model.vo.user.UserVO;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadResult;
import com.ye.yepicturebackend.model.dto.picture.edit.UpdatePictureRequest;
import com.ye.yepicturebackend.model.dto.picture.review.ReviewPictureRequest;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadBatchRequest;
import com.ye.yepicturebackend.model.dto.picture.edit.EditPictureRequest;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.PictureReviewStatusEnum;
import com.ye.yepicturebackend.service.CosUrlService;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.mapper.PictureMapper;
import com.ye.yepicturebackend.mapper.PictureTagMapper;
import com.ye.yepicturebackend.mapper.PictureCategoryMapper;
import com.ye.yepicturebackend.model.entity.PictureTag;
import com.ye.yepicturebackend.model.entity.PictureCategory;
import com.ye.yepicturebackend.service.SpaceService;
import com.ye.yepicturebackend.service.UserService;
import com.ye.yepicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片服务实现类
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private SpaceService spaceService;

    @Resource
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private PictureTagMapper pictureTagMapper;

    @Resource
    private PictureCategoryMapper pictureCategoryMapper;

    @Resource
    private HunyuanImageAnalysis hunyuanImageAnalysis;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private PictureVoConverter pictureVoConverter;

    @Resource
    private CosUrlService cosUrlService;

    // region 上传照片核心

    /**
     * 上传或更新图片
     *
     * @param inputSource          图片输入源：支持两种类型，1. 本地文件相关对象，2. 图片URL字符串
     * @param uploadRequest 图片上传/更新请求参数
     * @param loginUser            当前登录用户对象
     * @return PictureVO 脱敏后的图片视图对象
     */
    @Override
    public PictureVO uploadPicture(Object inputSource,
                                   UploadRequest uploadRequest,
                                   User loginUser) {
        // 1. 权限校验
        ThrowUtils.throwIf(inputSource == null,
                ErrorCode.PARAMS_ERROR, "图片为空");
        ThrowUtils.throwIf(loginUser == null,
                ErrorCode.NO_AUTH_ERROR, "无操作权限");
        Long pictureId = uploadRequest != null ? uploadRequest.getId() : null;
        // 校验空间是否存在
        Long spaceId = uploadRequest != null ? uploadRequest.getSpaceId() : null;
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null,
                    ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不够");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 更新权限校验
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null,
                    ErrorCode.NOT_FOUND_ERROR, "操作的原图片不存在");
            // 校验空间是否一致
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间id不一致");
                }
            }
        }
        // 2. 上传图片
        // 按照用户 id 划分存储目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 选择上传模板并上传
        PictureUploadTemplate pictureUploadTemplate = (inputSource instanceof String)
                ? urlPictureUpload
                : filePictureUpload;
        UploadResult uploadResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造图片实体
        Picture picture = getPicture(loginUser, uploadResult, pictureId, uploadRequest);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 执行入库,使用事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result,
                    ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update,
                        ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        return pictureVoConverter.toVo(picture);
    }

    /**
     * 构造图片实体对象的辅助方法（用于数据库存储）
     *
     * @param loginUser            当前登录用户对象
     * @param uploadResult  图片上传结果对象
     * @param pictureId            图片ID
     * @param uploadRequest 图片上传/更新请求参数
     * @return Picture 组装完成的图片实体对象
     */
    private static Picture getPicture(User loginUser,
                                      UploadResult uploadResult,
                                      Long pictureId,
                                      UploadRequest uploadRequest) {
        Picture picture = new Picture();
        // 地址赋值
        picture.setOriginKey(uploadResult.getOriginKey());
        picture.setCompressKey(uploadResult.getCompressKey());
        picture.setThumbnailKey(uploadResult.getThumbnailKey());
        // 元信息赋值
        picture.setName((uploadRequest != null
                && StrUtil.isNotBlank(uploadRequest.getPicName()))
                ? uploadRequest.getPicName()
                : uploadResult.getPicName());
        picture.setPicSize(uploadResult.getPicSize());
        picture.setPicWidth(uploadResult.getPicWidth());
        picture.setPicHeight(uploadResult.getPicHeight());
        picture.setPicScale(uploadResult.getPicScale());
        picture.setPicFormat(uploadResult.getPicFormat());
        picture.setPicColor(uploadResult.getPicColor());

        // 处理分类、标签和简介
        if (uploadRequest != null) {
            if (StrUtil.isNotBlank(uploadRequest.getCategory())) {
                picture.setCategory(uploadRequest.getCategory());
            }
            if (StrUtil.isNotBlank(uploadRequest.getIntroduction())) {
                picture.setIntroduction(uploadRequest.getIntroduction());
            }
            if (CollUtil.isNotEmpty(uploadRequest.getTags())) {
                picture.setTags(JSONUtil.toJsonStr(uploadRequest.getTags()));
            }
        }

        // 其他信息
        picture.setUserId(loginUser.getId());
        Long spaceId = uploadRequest != null ? uploadRequest.getSpaceId() : null;
        picture.setSpaceId(spaceId);
        // 处理更新操作的特殊字段
        picture.setId(pictureId);
        picture.setEditTime(new Date());
        if (pictureId == null) {
            picture.setCreateTime(new Date());
        }
        return picture;
    }

    // endregion

    // region 工具类

    /**
     * 填充图片审核相关参数
     *
     * @param picture   待填充审核参数的图片实体对象
     * @param loginUser 当前登录用户对象，用于判断是否为管理员角色
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员转为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * （Picture）转换为（PictureVO）
     *
     * @param picture 数据库中的图片实体对象
     * @param request HTTP请求对象
     * @return PictureVO 前端展示用的图片视图模
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        PictureVO pictureVO = pictureVoConverter.toVo(picture);
        // 关联查询用户信息
        Long id = picture.getId();
        if (id != null && id > 0) {
            User user = userService.getById(id);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * （Page<Picture>）转换为（Page<PictureVO>）
     *
     * @param picturePage 数据库查询得到的图片实体分页对象
     * @param request     HTTP请求对象
     * @return Page<PictureVO> 前端可直接渲染的图片VO分页对象
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> records = picturePage.getRecords();
        Page<PictureVO> voPage = new Page<>(
                picturePage.getCurrent(),
                picturePage.getSize(),
                picturePage.getTotal()
        );
        if (records.isEmpty()) {
            return voPage;
        }

        // 转换 VO
        List<PictureVO> voList = records.stream()
                .map(pictureVoConverter::toVo)
                .collect(Collectors.toList());

        // 批量查用户
        Set<Long> userIds = voList.stream()
                .map(PictureVO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, UserVO> userVOMap = userService.batchGetUserVOMap(userIds);

        // 填充用户（使用常量兜底）
        voList.forEach(vo ->
                vo.setUser(userVOMap.getOrDefault(vo.getUserId(), UserConstant.UNKNOWN_USER_VO))
        );

        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 校验图片实体数据的合法性
     *
     * @param picture 需要校验的图片实体对象：包含图片ID、URL、简介等待校验字段
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 取值
        Long id = picture.getId();
        String originKey = picture.getOriginKey();
        String introduction = picture.getIntroduction();
        // 校验
        ThrowUtils.throwIf(ObjUtil.isNull(id),
                ErrorCode.PARAMS_ERROR, "id不能为空");
        ThrowUtils.throwIf(StrUtil.isNotBlank(originKey) && originKey.length() > 512,
                ErrorCode.PARAMS_ERROR, "url过长");
        ThrowUtils.throwIf(StrUtil.isNotBlank(introduction) && introduction.length() > 800,
                ErrorCode.PARAMS_ERROR, "简介过长");
    }

    /**
     * 图片审核处理
     *
     * @param reviewPictureRequest 图片审核请求参数对象，包含：
     *                             - id：待审核图片ID
     *                             - reviewStatus：审核结果状态（通过/拒绝，不允许待审核）
     *                             - reviewMessage：审核备注信息（当前方法暂未使用）
     * @param loginUser            当前登录用户（审核人），用于记录审核人ID
     */
    @Override
    public void doPictureReview(ReviewPictureRequest reviewPictureRequest, User loginUser) {
        ThrowUtils.throwIf(reviewPictureRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验参数
        Long id = reviewPictureRequest.getId();
        Integer reviewStatus = reviewPictureRequest.getReviewStatus();
        String reviewMessage = reviewPictureRequest.getReviewMessage();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        ThrowUtils.throwIf(id <= 0,
                ErrorCode.PARAMS_ERROR, "图片ID非法");
        ThrowUtils.throwIf(reviewStatusEnum == null,
                ErrorCode.PARAMS_ERROR, "审核状态无效");
        ThrowUtils.throwIf(PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum),
                ErrorCode.PARAMS_ERROR, "审核状态不能回退为待审核");
        ThrowUtils.throwIf(StrUtil.isBlank(reviewMessage) &&
                        PictureReviewStatusEnum.REJECT.equals(reviewStatusEnum),
                ErrorCode.PARAMS_ERROR, "拒绝审核需填写拒绝原因");
        // 获取原图片
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null,
                ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(oldPicture.getReviewStatus().equals(reviewStatus),
                ErrorCode.PARAMS_ERROR, "请勿重复审核");
        // 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(reviewPictureRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        updatePicture.setReviewMessage(reviewMessage);
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 审核成功后，发送异步通知消息
        try {
            ReviewNoticeMessage message = new ReviewNoticeMessage();
            message.setPictureId(id);
            message.setUserId(oldPicture.getUserId());
            message.setReviewStatus(reviewStatus);
            message.setReviewMessage(reviewMessage);
            message.setReviewerId(loginUser.getId());

            rabbitTemplate.convertAndSend(
                    RabbitMQConstant.REVIEW_NOTICE_EXCHANGE,
                    RabbitMQConstant.REVIEW_NOTICE_ROUTING_KEY,
                    message
            );
            log.info("审核通知消息已发送, pictureId={}, userId={}", id, oldPicture.getUserId());
        } catch (Exception e) {
            log.warn("发送审核通知消息失败，pictureId={}", id, e);
        }
    }

    /**
     * 根据指定命名规则，批量填充图片名称
     *
     * @param pictureList 待批量重命名的图片列表
     * @param nameRule    批量命名规则
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        // 1. 前置校验
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }

        long count = 1;
        try {
            // 2. 遍历图片列表，为每张图片生成新名称
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("批量填充图片名称时发生解析错误，命名规则：{}", nameRule, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    // endregion

    // region 通用删改查

    /**
     * 图片删除
     *
     * @param deleteRequest 图片删除请求参数（含待删除图片ID）
     * @param loginUser     当前登录用户（用于权限校验）
     * @return Map<String, Object> 删除结果：含dbDeleted（数据库删除状态）、fileDeleted（文件删除状态）、message（结果描述）
     */
    @Override
    public Map<String, Object> deletePicture(DeleteRequest deleteRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片ID不合法");
        long pictureId = deleteRequest.getId();
        // 2. 校验图片存在性
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null,
                ErrorCode.NOT_FOUND_ERROR, "图片不存在或已被删除");
        // 开启事务
        transactionTemplate.execute(status -> {
            // 3. 执行数据库删除
            boolean dbDeleted = this.removeById(pictureId);
            ThrowUtils.throwIf(!dbDeleted,
                    ErrorCode.OPERATION_ERROR, "数据库删除失败");
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 4. 执行对象存储清理
        DeletePictureResult fileDeleteResult = this.clearPictureFile(oldPicture);
        // 5. 构建删除结果Map
        Map<String, Object> resultMap = new HashMap<>(3);
        resultMap.put("dbDeleted", true);
        resultMap.put("fileDeleted", fileDeleteResult.isDeleted());
        resultMap.put("message", fileDeleteResult.getMessage());
        return resultMap;
    }

    /**
     * 清理图片文件（同步删除原图、压缩图、缩略图）
     *
     * @param oldPicture 待删除的图片实体
     * @return 图片文件删除结果DTO
     */
    @Override
    public DeletePictureResult clearPictureFile(Picture oldPicture) {
        // 参数校验：必须有 originKey
        String originKey = oldPicture.getOriginKey();
        String compressKey = oldPicture.getCompressKey();
        String thumbnailKey = oldPicture.getThumbnailKey();

        ThrowUtils.throwIf(StrUtil.isBlank(originKey),
                ErrorCode.PARAMS_ERROR, "待删除的图片缺少 originKey，无法执行删除");

        // 初始化结果（返回安全的 Key）
        DeletePictureResult deleteResult = new DeletePictureResult();
        deleteResult.setOriginKey(originKey);
        deleteResult.setCompressKey(compressKey);
        deleteResult.setThumbnailKey(thumbnailKey);
        deleteResult.setDeleted(false);

        StringBuilder resultMsg = new StringBuilder();

        try {
            // 基于 originKey 查询引用次数（唯一标识）
            long originReferenceCount = this.lambdaQuery()
                    .eq(Picture::getOriginKey, originKey)
                    .count();

            if (originReferenceCount > 1) {
                String msg = String.format("原图被 %d 条记录引用，不执行删除（originKey：%s）",
                        originReferenceCount, originKey);
                deleteResult.setMessage(msg);
                log.info(msg);
                return deleteResult;
            }

            // 执行删除
            cosManager.deleteObject(originKey);
            resultMsg.append("原图删除成功；");

            if (StrUtil.isNotBlank(compressKey)) {
                cosManager.deleteObject(compressKey);
                resultMsg.append("压缩图删除成功；");
            } else {
                resultMsg.append("无压缩图可删除；");
            }

            if (StrUtil.isNotBlank(thumbnailKey)) {
                cosManager.deleteObject(thumbnailKey);
                resultMsg.append("缩略图删除成功");
            } else {
                resultMsg.append("无缩略图可删除");
            }

            deleteResult.setDeleted(true);
            deleteResult.setMessage(resultMsg.toString().trim().replaceAll("；$", ""));

        } catch (Exception e) {
            String errorMsg = String.format("图片删除失败（originKey：%s）：%s", originKey, e.getMessage());
            deleteResult.setMessage(errorMsg);
            log.error(errorMsg, e);
        }

        return deleteResult;
    }

    /**
     * 图片编辑
     *
     * @param editPictureRequest 图片编辑请求参数（含待编辑图片ID、新属性等）
     * @param loginUser          当前登录用户（用于权限校验）
     * @return Map<String, Object> 编辑结果：含editSuccess（编辑是否成功）、pictureId（编辑后的图片ID）、message（结果描述）
     */
    @Override
    public Map<String, Object> editPicture(EditPictureRequest editPictureRequest, User loginUser) {
        // 1. 基础参数校验
        ThrowUtils.throwIf(editPictureRequest == null,
                ErrorCode.PARAMS_ERROR, "编辑请求不能为空");
        ThrowUtils.throwIf(editPictureRequest.getId() == null || editPictureRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片ID不合法");

        // 2. 校验图片存在性
        Long pictureId = editPictureRequest.getId();
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null,
                ErrorCode.NOT_FOUND_ERROR, "待编辑图片不存在或已被删除");

        // 3. 构建更新对象
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(editPictureRequest, updatePicture);
        updatePicture.setTags(JSONUtil.toJsonStr(editPictureRequest.getTags()));
        updatePicture.setEditTime(new Date());
        this.validPicture(updatePicture);
        this.fillReviewParams(updatePicture, loginUser);

        // 4. 执行数据库更新
        boolean updateSuccess = this.updateById(updatePicture);
        ThrowUtils.throwIf(!updateSuccess,
                ErrorCode.OPERATION_ERROR, "图片编辑失败，数据库更新异常");
        Map<String, Object> resultMap = new HashMap<>(3);
        resultMap.put("editSuccess", true);
        resultMap.put("pictureId", pictureId);
        resultMap.put("message", "图片编辑成功");

        // 5. 构建编辑结果
        return resultMap;
    }

    /**
     * 图片查询请求参数转换
     *
     * @param queryPictureRequest 图片查询请求参数对象
     * @return LambdaQueryWrapper<Picture> 构建完成的查询条件封装器，可直接用于MyBatis-Plus的查询方法
     */
    @Override
    public LambdaQueryWrapper<Picture> getLambdaQueryWrapper(QueryPictureRequest queryPictureRequest) {
        LambdaQueryWrapper<Picture> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (queryPictureRequest == null) {
            return lambdaQueryWrapper;
        }
        // 提取参数
        Long id = queryPictureRequest.getId();
        String name = queryPictureRequest.getName();
        String introduction = queryPictureRequest.getIntroduction();
        String category = queryPictureRequest.getCategory();
        List<String> tags = queryPictureRequest.getTags();
        Long picSize = queryPictureRequest.getPicSize();
        Integer picWidth = queryPictureRequest.getPicWidth();
        Integer picHeight = queryPictureRequest.getPicHeight();
        Double picScale = queryPictureRequest.getPicScale();
        String picFormat = queryPictureRequest.getPicFormat();
        String searchText = queryPictureRequest.getSearchText();
        Long userId = queryPictureRequest.getUserId();
        Long spaceId = queryPictureRequest.getSpaceId();
        boolean nullSpaceId = queryPictureRequest.isNullSpaceId();
        Integer reviewStatus = queryPictureRequest.getReviewStatus();
        String reviewMessage = queryPictureRequest.getReviewMessage();
        Long reviewerId = queryPictureRequest.getReviewerId();
        String sortField = queryPictureRequest.getSortField();
        String sortOrder = queryPictureRequest.getSortOrder();
        Date startEditTime = queryPictureRequest.getStartEditTime();
        Date endEditTime = queryPictureRequest.getEndEditTime();


        // 关键词搜索 - 支持按图片名称、简介、分类进行模糊搜索
        if (StrUtil.isNotBlank(searchText)) {
            log.info("执行关键词搜索，搜索词: {}", searchText);
            lambdaQueryWrapper.and(lqw -> lqw
                    .like(Picture::getName, searchText)
                    .or()
                    .like(Picture::getIntroduction, searchText)
                    .or()
                    .like(Picture::getCategory, searchText)
            );
        }
        // 精准匹配
        lambdaQueryWrapper
                .eq(ObjUtil.isNotEmpty(id), Picture::getId, id)
                .eq(ObjUtil.isNotEmpty(userId), Picture::getUserId, userId)
                .eq(ObjUtil.isNotEmpty(spaceId), Picture::getSpaceId, spaceId)
                .isNull(nullSpaceId, Picture::getSpaceId)
                .eq(StrUtil.isNotBlank(category), Picture::getCategory, category)
                .eq(ObjUtil.isNotEmpty(picWidth), Picture::getPicWidth, picWidth)
                .eq(ObjUtil.isNotEmpty(picHeight), Picture::getPicHeight, picHeight)
                .eq(ObjUtil.isNotEmpty(picSize), Picture::getPicSize, picSize)
                .eq(ObjUtil.isNotEmpty(picScale), Picture::getPicScale, picScale)
                .eq(ObjUtil.isNotEmpty(reviewStatus), Picture::getReviewStatus, reviewStatus)
                .eq(ObjUtil.isNotEmpty(reviewerId), Picture::getReviewerId, reviewerId)
                .ge(ObjUtil.isNotEmpty(startEditTime), Picture::getEditTime, startEditTime)
                .lt(ObjUtil.isNotEmpty(endEditTime), Picture::getEditTime, endEditTime)
        ;
        // 模糊匹配
        lambdaQueryWrapper
                .like(StrUtil.isNotBlank(name), Picture::getName, name)
                .like(StrUtil.isNotBlank(introduction), Picture::getIntroduction, introduction)
                .like(StrUtil.isNotBlank(picFormat), Picture::getPicFormat, picFormat)
                .like(StrUtil.isNotBlank(reviewMessage), Picture::getReviewMessage, reviewMessage)
        ;
        // JSON标签匹配
        if (CollUtil.isNotEmpty(tags)) {
            String tagsJson = JSONUtil.toJsonStr(tags);
            lambdaQueryWrapper.apply("JSON_OVERLAPS(tags, {0})", tagsJson);
        }
        // 合法排序字段映射表
        Map<String, SFunction<Picture, ?>> sortFieldMap = new HashMap<>();
        sortFieldMap.put("createTime", Picture::getCreateTime);
        sortFieldMap.put("picSize", Picture::getPicSize);
        sortFieldMap.put("picWidth", Picture::getPicWidth);
        sortFieldMap.put("name", Picture::getName);
        // 校验并应用排序条件
        if (StrUtil.isNotEmpty(sortField) && sortFieldMap.containsKey(sortField)) {
            boolean isAsc = "ascend".equals(sortOrder);
            lambdaQueryWrapper.orderBy(true, isAsc, sortFieldMap.get(sortField));
        } else {
            // 默认按创建时间降序
            lambdaQueryWrapper.orderByDesc(Picture::getCreateTime);
        }
        return lambdaQueryWrapper;
    }

    /**
     * 根据图片ID获取图片VO
     *
     * @param id      图片ID，用于查询具体图片（必须为正整数）
     * @param request HTTP请求对象，用于获取当前登录用户信息
     * @return PictureVO 图片的视图对象，包含前端展示所需的图片信息（如URL、名称、标签等）
     */
    @Override
    public PictureVO getPictureVOById(long id, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(id <= 0,
                ErrorCode.PARAMS_ERROR, "图片ID非法");
        // 查询数据库
        Picture picture = this.getById(id);
        ThrowUtils.throwIf(picture == null,
                ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 获取权限列表
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = this.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);

        return this.getPictureVO(picture, request);
    }

    /**
     * 分页查询图片VO列表
     *
     * @param queryPictureRequest 图片查询请求体，包含分页参数、空间ID等查询条件
     * @param request             HTTP请求对象，用于获取当前登录用户信息（私有空间校验时使用）
     * @return Page<PictureVO> 分页包装的图片视图对象列表
     */
    @Override
    public Page<PictureVO> getPictureVOByPage(QueryPictureRequest queryPictureRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(queryPictureRequest == null, ErrorCode.PARAMS_ERROR, "无参数");
        // 获取参数
        int current = queryPictureRequest.getCurrent();
        int size = queryPictureRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = queryPictureRequest.getSpaceId();
        // 公开图库
        if (spaceId == null) {
            // 普通用户默认只能查看已过审的公开数据
            queryPictureRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            queryPictureRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR, "没有空间权限");
        }
        // 执行数据库
        Page<Picture> picturePage = this.page(
                new Page<>(current, size),
                this.getLambdaQueryWrapper(queryPictureRequest)
        );
        return this.getPictureVOPage(picturePage, request);
    }

    // endregion

    // region 管理员相关

    /**
     * 批量抓取网络图片
     *
     * @param uploadBatchRequest 批量上传请求参数，包含：
     *                                    - searchText：搜索关键词
     *                                    - count：需要创建的图片数量
     *                                    - namePrefix：图片名称前缀
     * @param loginUser                   当前登录用户对象，用于记录图片上传者信息
     * @return Integer 实际成功上传并创建的图片数量
     */
    @Override
    public Integer uploadPictureByBatch(
            UploadBatchRequest uploadBatchRequest,
            User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(uploadBatchRequest == null,
                ErrorCode.PARAMS_ERROR);
        String searchText = uploadBatchRequest.getSearchText();
        Integer count = uploadBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多一次性创建30条");
        // 名称前缀默认为搜索关键词
        String namePrefix = uploadBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // 获取批量上传的统一分类和标签
        String batchCategory = uploadBatchRequest.getCategory();
        List<String> batchTags = uploadBatchRequest.getTags();

        log.info("开始批量上传图片，搜索词: {}, 数量: {}, 分类: {}, 标签: {}",
                searchText, count, batchCategory, batchTags);

        // 2. 构建抓取链接，获取网页内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%smmasync=1", searchText);
        Document document;
        try {
            // Jsoup解析HTML页面
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }

        // 3. 解析网页，提取图片 URL
        // 提取页面中的图片元素
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片容器元素失败");
        }
        // 从容器中筛选图片标签
        Elements imgElementList = div.select(".iusc");

        // 4. 遍历图片，批量上传并AI分析
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            // 获取data-m属性中的JSON字符串
            String dataM = imgElement.attr("m");
            String fileUrl;
            try {
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片URL）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }
            if (StrUtil.isBlank(fileUrl)) {
                log.info("获取当前链接为空,已跳过:{}", fileUrl);
                continue;
            }
            // 处理图片上传地址
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 构建单张图片的上传请求参数
            UploadRequest uploadRequest = new UploadRequest();
            uploadRequest.setFileUrl(fileUrl);
            // 设置图片名称，序号连续递增
            if (StrUtil.isNotBlank(namePrefix)) {
                uploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            // 调用AI分析图片内容
            try {
                ImageAnalysisResult aiResult = hunyuanImageAnalysis.analyzeImage(fileUrl);

                uploadRequest.setCategory(aiResult.getCategory());
                uploadRequest.setTags(Arrays.asList(aiResult.getTags().split("，")));
                uploadRequest.setIntroduction(aiResult.getDescription());

                log.info("图片AI分析成功: 分类={}, 标签={}", aiResult.getCategory(), aiResult.getTags());

            } catch (Exception e) {
                // AI分析失败的处理
                uploadRequest.setCategory("其他");
                uploadRequest.setTags(Arrays.asList(searchText, "网络图片"));
                uploadRequest.setIntroduction("一张来自网络的图片");
                log.warn("图片AI分析失败: {}", e.getMessage());
            }
            // 调用单张图片上传方法，处理上传逻辑
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, uploadRequest, loginUser);
                log.info("图片上传成功,id={}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 图片更新 (管理员)
     *
     * @param updatePictureRequest 图片更新请求体，包含待更新的图片ID及相关字段（如名称、标签等）
     * @param loginUser            当前登录用户信息，用于权限校验和审核参数填充
     * @return Map<String, Object> 更新结果映射：
     * - success: Boolean 表示更新是否成功
     * - pictureId: Long 被更新的图片ID
     * - updateTime: LocalDateTime 实际更新时间
     * - message: String 操作提示信息（如"更新成功"）
     */
    @Override
    public Map<String, Object> updatePicture(UpdatePictureRequest updatePictureRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(updatePictureRequest == null || updatePictureRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        // 2. 原图片校验
        Long id = updatePictureRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null,
                ErrorCode.NOT_FOUND_ERROR, "待更新的图片不存在或已被删除");

        // 3. 构造新实体图片 (实体类转换)
        Picture picture = new Picture();
        BeanUtils.copyProperties(updatePictureRequest, picture);
        List<String> tags = updatePictureRequest.getTags();
        picture.setTags(CollectionUtil.isEmpty(tags) ? null : JSONUtil.toJsonStr(tags));
        picture.setUpdateTime(new Date());
        // 4. 图片数据校验
        this.validPicture(picture);
        // 5. 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 6. 执行数据库更新
        boolean updateResult = this.updateById(picture);
        ThrowUtils.throwIf(!updateResult,
                ErrorCode.OPERATION_ERROR, "数据库操作失败");
        // 7. 构建返回结果
        Map<String, Object> result = new HashMap<>(4);
        result.put("success", true);
        result.put("pictureId", id);
        result.put("updateTime", picture.getUpdateTime());
        result.put("message", "图片更新成功");
        return result;
    }

    // endregion

    // region 扩展功能

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId   图片所在的空间ID（用于限定查询范围）
     * @param picColor  目标颜色的十六进制字符串（支持腾讯COS返回的缩略格式，如0x8、0xA100等）
     * @param loginUser 当前登录用户对象（用于权限校验）
     * @return 按颜色相似度降序排列的图片VO列表（最多12条，相似度越高越靠前）
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 参数合法性校验
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor),
                ErrorCode.PARAMS_ERROR, "参数有误");
        ThrowUtils.throwIf(loginUser == null,
                ErrorCode.NO_AUTH_ERROR, "无操作权限");

        // 2. 空间存在性校验（权限校验由 Controller 层的 @SaSpaceCheckPermission 注解处理）
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null,
                ErrorCode.NOT_FOUND_ERROR, "空间不存在");

        // 3. 查询符合条件的图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)       // 限定空间ID
                .isNotNull(Picture::getPicColor)       // 必须有主色调信息
                .list();
        // 若查询结果为空，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        // 4. 颜色格式转换：支持 #RRGGBB 和 0xRRGGBB 两种格式
        String normalizedColor = picColor;
        if (picColor.startsWith("#")) {
            // 将 #RRGGBB 格式转换为 0xRRGGBB 格式（Color.decode 需要）
            normalizedColor = "0x" + picColor.substring(1);
        } else if (!picColor.startsWith("0x") && !picColor.startsWith("0X")) {
            // 如果没有前缀，添加 0x 前缀
            normalizedColor = "0x" + picColor;
        }

        // 5. 将目标颜色的十六进制字符串解析为Color对象
        Color targetColor;
        try {
            targetColor = Color.decode(normalizedColor);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "颜色格式错误，请使用 #RRGGBB 或 0xRRGGBB 格式");
        }
        // 6. 定义相似度阈值
        final double SIMILARITY_THRESHOLD = 0.6;

        // 7. 过滤并排序
        List<Picture> filteredAndSortedPictures = pictureList.stream()
                // 计算相似度并过滤
                .map(picture -> {
                    Color pictureColor = Color.decode(picture.getPicColor());
                    double similarity = ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                    return new AbstractMap.SimpleEntry<>(picture, similarity);
                })
                .filter(entry -> entry.getValue() >= SIMILARITY_THRESHOLD) // 阈值过滤
                // 按相似度降序排序
                .sorted((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()))
                // 提取图片对象
                .map(AbstractMap.SimpleEntry::getKey)
                // 保留最多12条
                .limit(12)
                .collect(Collectors.toList());

        // 8. 转换为VO返回
        return filteredAndSortedPictures.stream()
                .map(pictureVoConverter::toVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片信息（分类、标签、名称）
     *
     * @param editBatchRequest 批量编辑请求参数
     * @param loginUser                 当前登录用户
     */
    @Override
    public void editPictureByBatch(EditBatchRequest editBatchRequest, User loginUser) {
        // 1. 提取并校验参数
        List<Long> pictureIdList = editBatchRequest.getPictureIdList();
        Long spaceId = editBatchRequest.getSpaceId();
        String category = editBatchRequest.getCategory();
        List<String> tags = editBatchRequest.getTags();
        // 校验
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList) || spaceId == null,
                ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null,
                ErrorCode.NO_AUTH_ERROR);

        // 2. 校验空间的操作权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null,
                ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!loginUser.getId().equals(space.getUserId()),
                ErrorCode.NO_AUTH_ERROR, "非本人操作无权限");

        // 3. 查询目标空间下的指定图片
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId) // 仅查询必要字段
                .eq(Picture::getSpaceId, spaceId)            // 确保图片属于目标空间
                .in(Picture::getId, pictureIdList)           // 仅查询待编辑的图片
                .list();
        if (pictureList.isEmpty()) {
            return;
        }

        // 4. 遍历图片列表
        pictureList.forEach(picture -> {
            // 分类
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            // 标签列表
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
            // 提取批量命名规则
            String nameRule = editBatchRequest.getNameRule();
            fillPictureWithNameRule(pictureList, nameRule);
        });

        // 5. 执行批量更新操作
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result,
                ErrorCode.OPERATION_ERROR);
    }

    /**
     * AI扩图任务创建方法
     *
     * @param aiExtendRequest 前端传递的图片扩展任务请求参数
     *                                            包含待扩展的图片ID、AI扩展参数（如扩展比例、旋转角度、像素填充等）
     * @param loginUser                           当前登录用户信息
     * @return CreateOutPaintingTaskResponse 阿里云AI返回的扩展任务创建结果
     * 包含任务ID（taskId）和初始任务状态（如PENDING/RUNNING），用于后续查询任务结果
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(
            AiExtendRequest aiExtendRequest,
            User loginUser) {

        log.info("进入图片扩图任务创建方法，请求参数：{}", JSONUtil.toJsonStr(aiExtendRequest));

        // 1. 获取并校验待扩展的图片信息
        Long pictureId = aiExtendRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));

        // 2. 确保 originKey 存在
        String originKey = picture.getOriginKey();
        ThrowUtils.throwIf(StrUtil.isBlank(originKey),
                ErrorCode.PARAMS_ERROR, "图片缺少存储路径（originKey），无法用于扩图");

        // 3. 生成临时可访问 URL（阿里云需要公网可读）
        String imageUrl = cosUrlService.generateSignedUrl(originKey ,3600 * 1000L);
        ThrowUtils.throwIf(StrUtil.isBlank(imageUrl),
                ErrorCode.SYSTEM_ERROR, "生成图片访问链接失败");

        // 4. 组装阿里云AI图像扩展接口的请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(imageUrl); // 使用临时 URL
        taskRequest.setInput(input);

        // 5. 复制前端请求中的扩展参数
        BeanUtils.copyProperties(aiExtendRequest, taskRequest);

        log.info("组装阿里云请求参数：model={}，imageUrl={}，x_scale={}，y_scale={}",
                taskRequest.getModel(),
                imageUrl,
                taskRequest.getParameters().getXScale(),
                taskRequest.getParameters().getYScale());

        // 6. 调用阿里云 AI 接口
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }

    // endregion

    // region 标签和分类管理

    @Override
    public List<String> getAllTagNames() {
        // 查询所有未删除的标签，按排序顺序升序排列
        LambdaQueryWrapper<PictureTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(PictureTag::getSortOrder)
                .orderByAsc(PictureTag::getCreateTime);
        List<PictureTag> tagList = pictureTagMapper.selectList(queryWrapper);

        // 提取标签名称列表
        return tagList.stream()
                .map(PictureTag::getTagName)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAllCategoryNames() {
        // 查询所有未删除的分类，按排序顺序升序排列
        LambdaQueryWrapper<PictureCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(PictureCategory::getSortOrder)
                .orderByAsc(PictureCategory::getCreateTime);
        List<PictureCategory> categoryList = pictureCategoryMapper.selectList(queryWrapper);

        // 提取分类名称列表
        return categoryList.stream()
                .map(PictureCategory::getCategoryName)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> syncTagsAndCategoriesFromPublicPictures() {
        log.info("开始从公共图库同步标签和分类到数据库表");

        // 1. 查询所有公共图库的图片（spaceId 为 null）
        LambdaQueryWrapper<Picture> pictureQueryWrapper = new LambdaQueryWrapper<>();
        pictureQueryWrapper.isNull(Picture::getSpaceId)
                .select(Picture::getId, Picture::getCategory, Picture::getTags);
        List<Picture> publicPictures = this.list(pictureQueryWrapper);

        // 过滤掉 null 对象，确保列表中没有 null 元素
        publicPictures = publicPictures.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(publicPictures)) {
            log.info("公共图库中没有图片，无需同步");
            Map<String, Object> result = new HashMap<>();
            result.put("tagCount", 0);
            result.put("categoryCount", 0);
            result.put("message", "公共图库中没有图片，无需同步");
            return result;
        }

        // 2. 提取所有唯一的分类
        Set<String> categorySet = new HashSet<>();
        for (Picture picture : publicPictures) {
            // 添加 null 检查，防止空指针异常
            if (picture == null) {
                continue;
            }
            if (StrUtil.isNotBlank(picture.getCategory())) {
                categorySet.add(picture.getCategory().trim());
            }
        }

        // 3. 提取所有唯一的标签
        Set<String> tagSet = new HashSet<>();
        for (Picture picture : publicPictures) {
            // 添加 null 检查，防止空指针异常
            if (picture == null) {
                continue;
            }
            if (StrUtil.isNotBlank(picture.getTags())) {
                try {
                    // 解析 JSON 数组格式的标签
                    List<String> tags = JSONUtil.toList(picture.getTags(), String.class);
                    if (CollUtil.isNotEmpty(tags)) {
                        for (String tag : tags) {
                            if (StrUtil.isNotBlank(tag)) {
                                tagSet.add(tag.trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析图片标签失败，图片ID：{}，标签内容：{}",
                            picture.getId(),
                            picture.getTags(), e);
                }
            }
        }

        // 4. 查询数据库中已存在的标签和分类
        List<PictureTag> existingTags = pictureTagMapper.selectList(null);
        Set<String> existingTagNames = existingTags.stream()
                .map(PictureTag::getTagName)
                .collect(Collectors.toSet());

        List<PictureCategory> existingCategories = pictureCategoryMapper.selectList(null);
        Set<String> existingCategoryNames = existingCategories.stream()
                .map(PictureCategory::getCategoryName)
                .collect(Collectors.toSet());

        // 5. 插入新的标签
        int newTagCount = 0;
        int sortOrder = 0;
        for (String tagName : tagSet) {
            if (!existingTagNames.contains(tagName)) {
                PictureTag pictureTag = new PictureTag();
                pictureTag.setTagName(tagName);
                pictureTag.setSortOrder(sortOrder++);
                pictureTagMapper.insert(pictureTag);
                newTagCount++;
                log.info("新增标签：{}", tagName);
            }
        }

        // 6. 插入新的分类
        int newCategoryCount = 0;
        sortOrder = 0;
        for (String categoryName : categorySet) {
            if (!existingCategoryNames.contains(categoryName)) {
                PictureCategory pictureCategory = new PictureCategory();
                pictureCategory.setCategoryName(categoryName);
                pictureCategory.setSortOrder(sortOrder++);
                pictureCategoryMapper.insert(pictureCategory);
                newCategoryCount++;
                log.info("新增分类：{}", categoryName);
            }
        }

        // 7. 返回同步结果
        Map<String, Object> result = new HashMap<>();
        result.put("tagCount", newTagCount);
        result.put("categoryCount", newCategoryCount);
        result.put("message", String.format("同步完成，新增标签 %d 个，新增分类 %d 个", newTagCount, newCategoryCount));
        log.info("标签和分类同步完成，新增标签：{} 个，新增分类：{} 个", newTagCount, newCategoryCount);
        return result;
    }

    // endregion

}




