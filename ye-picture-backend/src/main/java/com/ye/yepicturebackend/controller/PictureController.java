package com.ye.yepicturebackend.controller;


import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ye.yepicturebackend.annotation.AuthCheck;
import com.ye.yepicturebackend.api.aliyunai.AliYunAiApi;
import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ye.yepicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.ye.yepicturebackend.api.imageSearch.ImageSearchApiFacade;
import com.ye.yepicturebackend.api.imageSearch.model.ImageSearchResult;
import com.ye.yepicturebackend.common.BaseResponse;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.common.ResultUtils;
import com.ye.yepicturebackend.constant.UserConstant;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.ye.yepicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.ye.yepicturebackend.model.dto.picture.edit.EditBatchRequest;
import com.ye.yepicturebackend.model.dto.picture.query.QueryPictureRequest;
import com.ye.yepicturebackend.model.dto.picture.query.SearchColorRequest;
import com.ye.yepicturebackend.model.dto.picture.query.SearchPictureRequest;
import com.ye.yepicturebackend.api.aliyunai.model.AiExtendRequest;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadRequest;
import com.ye.yepicturebackend.model.vo.picture.PictureTagCategory;
import com.ye.yepicturebackend.model.vo.picture.PictureVO;
import com.ye.yepicturebackend.model.dto.picture.review.ReviewPictureRequest;
import com.ye.yepicturebackend.model.dto.picture.edit.UpdatePictureRequest;
import com.ye.yepicturebackend.model.dto.picture.upload.UploadBatchRequest;
import com.ye.yepicturebackend.model.dto.picture.edit.EditPictureRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceLevel;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.PictureReviewStatusEnum;
import com.ye.yepicturebackend.model.enums.SpaceLevelEnum;
import com.ye.yepicturebackend.service.CosUrlService;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private CosUrlService cosUrlService;

    // region 上传照片核心

    /**
     * 图片上传
     *
     * @param multipartFile        前端上传的图片文件
     * @param uploadRequest 图片上传/更新请求参数：
     *                             - 可选：id（更新时必传，指定待更新图片的ID）
     *                             - 可选：picName（自定义图片名称，不传则使用默认名称）
     * @param request              HTTP请求对象，用于获取当前登录用户的会话信息（验证登录状态）
     * @return BaseResponse<PictureVO> 上传结果响应
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            UploadRequest uploadRequest,
            HttpServletRequest request
    ) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 执行service
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, uploadRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过URL上传图片
     *
     * @param uploadRequest 请求体参数，包含：
     *                             - fileUrl：图片的网络URL地址（必填）
     *                             - 其他图片相关信息（如名称、分类、标签等，根据业务需求定义）
     * @param request              HTTP请求对象，用于获取当前登录用户信息（权限验证、记录上传人等）
     * @return BaseResponse<PictureVO> 接口响应对象：
     * - 成功：{success: true, data: PictureVO对象}，包含图片URL、尺寸、格式等信息
     * - 失败：{success: false, code: 错误码, message: 错误信息}（如URL无效、图片格式错误等）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody UploadRequest uploadRequest,
            HttpServletRequest request
    ) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        String fileUrl = uploadRequest.getFileUrl();
        // 2. 执行service
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, uploadRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(pictureVO);
    }


    /**
     * 批量抓取并创建图片
     *
     * @param uploadBatchRequest 请求体参数，包含：
     *                                    - searchText：图片搜索关键词
     *                                    - count：需要批量创建的图片数量
     *                                    - namePrefix：图片名称前缀
     *                                    - category：图片分类（可选，统一应用到所有图片）
     *                                    - tags：图片标签列表（可选，统一应用到所有图片）
     * @param request                     HTTP请求对象，用于获取当前登录用户信息
     * @return BaseResponse<Integer> 接口响应对象：
     * - 成功：{success: true, data: 成功创建的图片数量}
     * - 失败：{success: false, code: 错误码, message: 错误信息}（如参数为空、无权限、抓取失败等）
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody UploadBatchRequest uploadBatchRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 执行service
        int uploadCount = pictureService.uploadPictureByBatch(uploadBatchRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(uploadCount);
    }

    // endregion

    // region 管理员操作

    /**
     * 更新图片（管理员）
     *
     * @param updatePictureRequest 图片更新请求体：包含待更新的图片ID、标签、简介等字段
     * @param request              HTTP请求对象：用于获取当前登录的管理员用户信息
     * @return BaseResponse<Boolean> 接口响应对象：
     * - 成功：返回{success: true, data: true}，表示图片信息更新成功
     * - 失败：返回包含错误码（如PARAMS_ERROR、NOT_FOUND_ERROR等）和错误信息的响应
     */
    @PostMapping("update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> updatePicture(
            @RequestBody UpdatePictureRequest updatePictureRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 执行Service
        Map<String, Object> updateResult = pictureService.updatePicture(updatePictureRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(updateResult);
    }

    /**
     * 根据ID获取图片（管理员）
     *
     * @param id 图片ID
     * @return BaseResponse<Picture> 接口响应对象：
     * - 成功：返回{success: true, data: Picture对象}，包含图片的完整信息
     * - 失败：返回包含错误码（如PARAMS_ERROR、NOT_FOUND_ERROR等）和错误信息的响应
     */
    @GetMapping("get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id) {
        // 参数校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 执行service
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 返回结果
        return ResultUtils.success(picture);
    }


    /**
     * 分页获取图片列表（管理员）
     *
     * @param queryPictureRequest 图片查询请求体：包含分页参数（当前页、页大小）和查询条件（如URL、标签、用户ID等）
     * @return BaseResponse<Page < Picture>> 接口响应对象：
     * - 成功：返回{success: true, data: Page<Picture>}，包含分页元数据和图片实体列表
     * - 失败：返回包含错误码（如PARAMS_ERROR）和错误信息的响应
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(
            @RequestBody QueryPictureRequest queryPictureRequest) {
        // 参数校验
        ThrowUtils.throwIf(queryPictureRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        int current = queryPictureRequest.getCurrent();
        int size = queryPictureRequest.getPageSize();
        // 执行service
        Page<Picture> picturePage = pictureService.page(
                new Page<>(current, size),
                pictureService.getLambdaQueryWrapper(queryPictureRequest)
        );
        return ResultUtils.success(picturePage);
    }

    /**
     * 图片审核
     *
     * @param reviewPictureRequest 审核请求参数体，包含：
     *                             - id：待审核图片的ID
     *                             - reviewStatus：审核状态（通过/拒绝等枚举值）
     *                             - reviewMessage：审核备注信息（可选，如拒绝原因）
     * @param request              HTTP请求对象，用于获取当前登录用户信息
     * @return BaseResponse<Boolean> 接口响应对象：
     * - 成功：{success: true, data: true}
     * - 失败：{success: false, code: 错误码, message: 错误信息}
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(
            @RequestBody ReviewPictureRequest reviewPictureRequest,
            HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(reviewPictureRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        User loginUser = userService.getLoginUser(request);
        // 执行service
        pictureService.doPictureReview(reviewPictureRequest, loginUser);
        return ResultUtils.success(true);
    }

    // endregion

    // region 前端使用

    /**
     * 空间等级列表查询接口
     *
     * @return BaseResponse<List < SpaceLevel>> 接口响应：
     * 成功：{success: true, data: [SpaceLevel 对象列表]}，每个 SpaceLevel 包含：
     * level: 空间等级值
     * levelName: 等级显示文本
     * maxPictureCount: 该等级允许的最大图片数量
     * maxStorageSize: 该等级允许的最大存储容量
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

    /**
     * 获取图片基础标签与分类列表
     * 从数据库动态获取标签和分类数据
     *
     * @return BaseResponse<PictureTagCategory> 接口响应对象：
     * - 成功：返回{success: true, data: PictureTagCategory对象}，包含两个列表：
     * 1. tagList：基础标签列表（从数据库查询，按排序顺序）
     * 2. categoryList：分类列表（从数据库查询，按排序顺序）
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        // 从数据库查询标签列表（按排序顺序）
        List<String> tagList = pictureService.getAllTagNames();
        // 从数据库查询分类列表（按排序顺序）
        List<String> categoryList = pictureService.getAllCategoryNames();
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 从公共图库同步标签和分类到数据库表
     * 扫描所有公共图库（spaceId 为 null）的图片，提取其中的标签和分类信息并写入到标签表和分类表中
     *
     * @return BaseResponse<Map<String, Object>> 接口响应对象：
     * - 成功：返回{success: true, data: {tagCount: 新增标签数, categoryCount: 新增分类数, message: "..."}}
     */
    @PostMapping("/sync/tags_categories")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> syncTagsAndCategoriesFromPublicPictures() {
        Map<String, Object> result = pictureService.syncTagsAndCategoriesFromPublicPictures();
        return ResultUtils.success(result);
    }

    // endregion

    // region 缓存相关

    /**
     * 本地缓存实例（基于Caffeine实现）
     * 使用方式：
     * - 存：LOCAL_CACHE.put(key, value)
     * - 取：LOCAL_CACHE.getIfPresent(key)
     * - 删：LOCAL_CACHE.invalidate(key)
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    /**
     * 分页获取图片列表VO（多级缓存）
     *
     * @param queryPictureRequest 分页查询请求参数，包含：
     *                            - current：页码（从1开始）
     *                            - pageSize：每页条数（最大20条，防止爬虫过度请求）
     *                            - 其他查询条件（如分类、标签、审核状态等，普通用户默认仅查询"已通过"状态）
     * @param request             HTTP请求对象，用于获取当前登录用户信息（影响数据权限过滤）
     * @return BaseResponse<Page < PictureVO>> 接口响应对象：
     * - 成功：返回分页的PictureVO列表（包含图片URL、尺寸、格式等前端所需信息）
     * - 失败：返回错误码和错误信息（如参数为空、分页大小超限等）
     */
    @Deprecated
    @PostMapping("/list/page/vo/cathe")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCathe(
            @RequestBody QueryPictureRequest queryPictureRequest,
            HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(queryPictureRequest == null, ErrorCode.PARAMS_ERROR, "无参数");
        // 2. 提取参数
        int current = queryPictureRequest.getCurrent();
        int size = queryPictureRequest.getPageSize();
        // 限制爬虫处理
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 3. 权限控制: 普通用户只能看到审核通过的数据
        queryPictureRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 4. 构建缓存key
        String queryCondition = JSONUtil.toJsonStr(queryPictureRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // 拼接缓存前缀
        String cacheKey = "yepicture:listPictureVOByPage:" + hashKey;
        // 5. 一级缓存：查询本地缓存（Caffeine）
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            Page<PictureVO> cachedPage = JSONUtil.toBean(
                    cachedValue,
                    new TypeReference<>() {
                    },
                    false
            );
            return ResultUtils.success(cachedPage);
        }
        // 6. 二级缓存：查询分布式缓存（Redis）
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cachedValue = valueOps.get(cacheKey);
        if (cachedValue != null) {
            // 缓存命中: 更新本地缓存
            LOCAL_CACHE.put(cacheKey, cachedValue);
            // 返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(
                    cachedValue,
                    new TypeReference<>() {
                    },
                    false
            );
            return ResultUtils.success(cachedPage);
        }
        // 7. 缓存未命中：查询数据库
        Page<Picture> picturePage = pictureService.page(
                new Page<>(current, size),
                pictureService.getLambdaQueryWrapper(queryPictureRequest)
        );
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 8. 更新缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 更新本地缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);
        // 更新 Redis 缓存：设置5-10分钟随机过期时间（防止缓存雪崩）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
        // 9. 返回结果
        return ResultUtils.success(pictureVOPage);
    }

    // endregion

    // region 通用删除与修改

    /**
     * 图片删除
     *
     * @param deleteRequest 包含待删除图片ID的请求体：通过id字段指定需要删除的图片
     * @param request       HTTP请求对象：用于用于获取当前登录用户信息
     * @return BaseResponse<Map < String, Object>> 接口响应对象：
     * - 成功：返回{success: true, data: {dbDeleted: true, fileDeleted: true, message: "..."}}
     * - 失败：返回包含错误码和错误信息的响应
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Map<String, Object>> deletePicture(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 执行Service
        Map<String, Object> deleteResult = pictureService.deletePicture(deleteRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(deleteResult);
    }

    /**
     * 根据ID获取图片（VO）
     *
     * @param id      图片ID：用于定位待查询的图片记录，必须为大于0的有效数值
     * @param request HTTP请求对象：可用于获取当前登录用户信息（如判断权限、关联用户数据），
     *                具体用途由service层的getPictureVO方法决定
     * @return BaseResponse<PictureVO> 接口响应对象：
     * - 成功：返回{success: true, data: PictureVO对象}，包含前端所需的图片展示数据
     * - 失败：返回包含错误码和错误信息的响应
     */
    @GetMapping("get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        // 执行service
        PictureVO pictureVO = pictureService.getPictureVOById(id, request);
        // 返回结果
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（VO）
     *
     * @param queryPictureRequest 图片查询请求体：包含分页参数（当前页、页大小）和查询条件
     * @param request             HTTP请求对象：用于获取用户上下文信息，支持后续业务扩展（如用户权限判断）
     * @return BaseResponse<Page < PictureVO>> 接口响应对象：
     * - 成功：返回{success: true, data: Page<PictureVO>}，包含分页信息和VO列表
     * - 失败：返回包含错误码和错误信息的响应
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(
            @RequestBody QueryPictureRequest queryPictureRequest,
            HttpServletRequest request) {
        return ResultUtils.success(pictureService.getPictureVOByPage(queryPictureRequest, request));
    }

    /**
     * 编辑图片
     *
     * @param editPictureRequest 图片编辑请求体：包含待编辑的图片ID、标签、简介等字段
     * @param request            HTTP请求对象：用于获取当前登录用户信息（用于权限校验）
     * @return BaseResponse<Boolean> 接口响应对象：
     * - 成功：返回{success: true, data: true}，表示图片编辑成功
     * - 失败：返回包含错误码（如PARAMS_ERROR、NO_AUTH_ERROR等）和错误信息的响应
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Map<String, Object>> editPicture(
            @RequestBody EditPictureRequest editPictureRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 调用Service
        Map<String, Object> editResult = pictureService.editPicture(editPictureRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(editResult);
    }

    // endregion

    // region 扩展功能

    /**
     * 以图搜图
     *
     * @param searchPictureRequest 以图搜图请求体，需包含待搜索图片的ID
     * @return 包含图像搜索结果列表的基础响应体
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureRequest searchPictureRequest) {
        // 1. 参数校验
        ThrowUtils.throwIf(searchPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 查询图片是否存在
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 获取 originKey 并生成临时可访问 URL
        String originKey = picture.getOriginKey();
        ThrowUtils.throwIf(StrUtil.isBlank(originKey), ErrorCode.SYSTEM_ERROR, "图片存储路径缺失");

        // 生成较短有效期的签名 URL
        String temporaryImageUrl = cosUrlService.generateSignedUrl(originKey, 5 * 60 * 1000L);
        ThrowUtils.throwIf(temporaryImageUrl == null, ErrorCode.SYSTEM_ERROR, "无法生成图片访问链接");

        // 4. 调用图像搜索 API
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(temporaryImageUrl);

        // 5. 返回结果
        return ResultUtils.success(resultList);
    }

    /**
     * 根据颜色搜索图片
     *
     * @param searchColorRequest 请求体参数封装对象，包含：
     *                                    - picColor：目标颜色的十六进制字符串（支持腾讯COS返回的缩略格式，如0x8、0xA100等）
     *                                    - spaceId：图片所在的空间ID（用于限定查询范围）
     * @param request                     HttpServletRequest对象，用于获取当前登录用户的会话信息
     * @return BaseResponse<List < PictureVO>> 统一响应对象
     */
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchColorRequest searchColorRequest, HttpServletRequest request) {
        // 1. 校验并获取参数
        ThrowUtils.throwIf(searchColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchColorRequest.getPicColor();
        Long spaceId = searchColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);

        // 2. 调用service
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);

        // 3. 返回结果
        return ResultUtils.success(result);
    }

    /**
     * 批量编辑图片信息
     *
     * @param editBatchRequest 前端传递的批量编辑请求体
     * @param request                   HttpServletRequest对象
     * @return BaseResponse<Boolean> 统一响应体，成功时返回true，失败时返回错误信息
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(
            @RequestBody EditBatchRequest editBatchRequest,
            HttpServletRequest request) {
        // 1. 提取并校验参数
        ThrowUtils.throwIf(editBatchRequest == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        // 2. 调用Service
        pictureService.editPictureByBatch(editBatchRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(true);
    }

    /**
     * 创建AI图像扩展任务
     *
     * @param aiExtendRequest 前端传递的AI扩图任务请求体
     *                                            必须包含待扩展的图片ID，可选包含扩图参数
     * @param request                             HttpServletRequest对象
     * @return BaseResponse<CreateOutPaintingTaskResponse> 统一格式的接口响应
     * 成功时：响应体为阿里云返回的任务创建结果（含taskId、初始任务状态）；
     * 失败时：抛出BusinessException，返回对应的错误码和错误信息
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody AiExtendRequest aiExtendRequest,
            HttpServletRequest request) {
        // 1. 基础参数校验
        ThrowUtils.throwIf(aiExtendRequest == null ||
                        aiExtendRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        // 2. 调用Service
        CreateOutPaintingTaskResponse response = pictureService
                .createPictureOutPaintingTask(aiExtendRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(response);
    }

    /**
     * 查询AI图像扩展任务状态
     *
     * @param taskId AI扩图任务的唯一标识
     *               从"创建AI扩图任务"接口的返回结果中获取，是查询任务的核心索引
     * @return BaseResponse<GetOutPaintingTaskResponse> 统一格式的接口响应
     * 成功时：响应体为任务完整信息（含当前状态、时间节点、扩展后图片URL（若任务成功）、错误信息（若任务失败））；
     * 失败时：抛出BusinessException，返回对应的错误码和错误信息
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        // 1. 基础参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);

        // 2. 调用阿里云AI工具类
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);

        // 3. 返回结果
        return ResultUtils.success(task);
    }

    // endregion


}
