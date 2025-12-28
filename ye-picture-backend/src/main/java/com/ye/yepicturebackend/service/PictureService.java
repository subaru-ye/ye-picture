package com.ye.yepicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.model.dto.picture.delete.DeletePictureResult;
import com.ye.yepicturebackend.model.dto.picture.edit.EditBatchRequest;
import com.ye.yepicturebackend.model.dto.picture.query.QueryPictureRequest;
import com.ye.yepicturebackend.model.dto.picture.task.AiExtendRequest;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadRequest;
import com.ye.yepicturebackend.model.vo.picture.PictureVO;
import com.ye.yepicturebackend.model.dto.picture.edit.UpdatePictureRequest;
import com.ye.yepicturebackend.model.dto.picture.review.ReviewPictureRequest;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadBatchRequest;
import com.ye.yepicturebackend.model.dto.picture.edit.EditPictureRequest;
import com.ye.yepicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ye.yepicturebackend.model.entity.User;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 图片服务接口
 */
public interface PictureService extends IService<Picture> {


    // region 上传照片核心

    /**
     * 上传或更新图片
     *
     * @param inputSource          图片输入源：支持两种类型，1. 本地文件相关对象，2. 图片URL字符串
     * @param uploadRequest 图片上传/更新请求参数
     * @param loginUser            当前登录用户对象
     * @return PictureVO 脱敏后的图片视图对象
     */
    PictureVO uploadPicture(Object inputSource,
                            UploadRequest uploadRequest,
                            User loginUser);
    // endregion

    // region 工具类

    /**
     * 校验图片实体数据的合法性
     *
     * @param picture 需要校验的图片实体对象：包含图片ID、URL、简介等待校验字段
     */
    void validPicture(Picture picture);

    /**
     * （Page<Picture>）转换为（Page<PictureVO>）
     *
     * @param picturePage 数据库查询得到的图片实体分页对象
     * @param request     HTTP请求对象
     * @return Page<PictureVO> 前端可直接渲染的图片VO分页对象
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * （Picture）转换为（PictureVO）
     *
     * @param picture 数据库中的图片实体对象
     * @param request HTTP请求对象
     * @return PictureVO 前端展示用的图片视图模
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 填充图片审核相关参数
     *
     * @param picture   待填充审核参数的图片实体对象
     * @param loginUser 当前登录用户对象，用于判断是否为管理员角色
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 清理图片文件（同步删除原图、压缩图、缩略图）
     *
     * @param oldPicture 待删除的图片实体
     * @return 图片文件删除结果DTO
     */
    DeletePictureResult clearPictureFile(Picture oldPicture);


    // endregion

    // region 管理员相关

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
    Map<String, Object> updatePicture(UpdatePictureRequest updatePictureRequest, User loginUser);

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
    Integer uploadPictureByBatch(UploadBatchRequest uploadBatchRequest, User loginUser);

    /**
     * 图片审核处理
     *
     * @param reviewPictureRequest 图片审核请求参数对象，包含：
     *                             - id：待审核图片ID
     *                             - reviewStatus：审核结果状态（通过/拒绝，不允许待审核）
     *                             - reviewMessage：审核备注信息（当前方法暂未使用）
     * @param loginUser            当前登录用户（审核人），用于记录审核人ID
     */
    void doPictureReview(ReviewPictureRequest reviewPictureRequest, User loginUser);

    // endregion

    // region 通用删改查

    /**
     * 图片删除
     *
     * @param deleteRequest 图片删除请求参数（含待删除图片ID）
     * @param loginUser     当前登录用户（用于权限校验）
     * @return Map<String, Object> 删除结果：含dbDeleted（数据库删除状态）、fileDeleted（文件删除状态）、message（结果描述）
     */
    Map<String, Object> deletePicture(DeleteRequest deleteRequest, User loginUser);

    /**
     * 图片编辑 (用户)
     *
     * @param editPictureRequest 图片编辑请求参数（含待编辑图片ID、新属性等）
     * @param loginUser          当前登录用户（用于权限校验）
     * @return Map<String, Object> 编辑结果：含editSuccess（编辑是否成功）、pictureId（编辑后的图片ID）、message（结果描述）
     */
    Map<String, Object> editPicture(EditPictureRequest editPictureRequest, User loginUser);

    /**
     * 图片查询请求参数转换
     *
     * @param queryPictureRequest 图片查询请求参数对象
     * @return LambdaQueryWrapper<Picture> 构建完成的查询条件封装器，可直接用于MyBatis-Plus的查询方法
     */
    LambdaQueryWrapper<Picture> getLambdaQueryWrapper(QueryPictureRequest queryPictureRequest);

    /**
     * 根据图片ID获取图片VO（视图对象）
     *
     * @param id      图片ID，用于查询具体图片（必须为正整数）
     * @param request HTTP请求对象，用于获取当前登录用户信息
     * @return PictureVO 图片的视图对象，包含前端展示所需的图片信息（如URL、名称、标签等）
     */
    PictureVO getPictureVOById(long id, HttpServletRequest request);

    /**
     * 分页查询图片VO列表
     *
     * @param queryPictureRequest 图片查询请求体，包含分页参数、空间ID等查询条件
     * @param request             HTTP请求对象，用于获取当前登录用户信息（私有空间校验时使用）
     * @return Page<PictureVO> 分页包装的图片视图对象列表
     */
    Page<PictureVO> getPictureVOByPage(QueryPictureRequest queryPictureRequest, HttpServletRequest request);


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
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 批量编辑图片信息（分类、标签、名称）
     *
     * @param editBatchRequest 批量编辑请求参数
     * @param loginUser                 当前登录用户
     */
    void editPictureByBatch(EditBatchRequest editBatchRequest, User loginUser);

    /**
     * AI扩图任务创建方法
     *
     * @param createOutPaintingTaskRequest 前端传递的图片扩展任务请求参数
     *                                     包含待扩展的图片ID、AI扩展参数（如扩展比例、旋转角度、像素填充等）
     * @param loginUser                    当前登录用户信息
     * @return CreateOutPaintingTaskResponse 阿里云AI返回的扩展任务创建结果
     * 包含任务ID（taskId）和初始任务状态（如PENDING/RUNNING），用于后续查询任务结果
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(AiExtendRequest createOutPaintingTaskRequest,
                                                               User loginUser);

    // endregion

    // region 标签和分类管理

    /**
     * 获取所有有效的标签列表（按排序顺序）
     *
     * @return 标签名称列表
     */
    List<String> getAllTagNames();

    /**
     * 获取所有有效的分类列表（按排序顺序）
     *
     * @return 分类名称列表
     */
    List<String> getAllCategoryNames();

    /**
     * 从公共图库中提取标签和分类，并同步到标签表和分类表中
     * 扫描所有公共图库（spaceId 为 null）的图片，提取其中的标签和分类信息
     *
     * @return Map<String, Object> 同步结果：
     * - tagCount: 新增的标签数量
     * - categoryCount: 新增的分类数量
     * - message: 操作结果描述
     */
    Map<String, Object> syncTagsAndCategoriesFromPublicPictures();

    // endregion

}










