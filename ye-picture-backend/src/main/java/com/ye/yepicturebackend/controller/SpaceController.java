package com.ye.yepicturebackend.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.annotation.AuthCheck;
import com.ye.yepicturebackend.common.BaseResponse;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.common.ResultUtils;
import com.ye.yepicturebackend.constant.UserConstant;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.manager.auth.SpaceUserAuthManager;
import com.ye.yepicturebackend.model.vo.SpaceVO;

import com.ye.yepicturebackend.model.dto.space.SpaceAddRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceEditRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceQueryRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceUpdateRequest;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.service.SpaceService;
import com.ye.yepicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    // region 通用增删改查

    /**
     * 创建用户私人空间
     *
     * @param spaceAddRequest 空间创建请求体，包含空间名称、空间描述（可选）等创建所需字段
     * @param request         HTTP请求对象，用于从请求中提取当前登录用户信息
     * @return BaseResponse<Long> 接口响应结果：
     * - 成功：{success: true, data: 新创建空间的ID（Long类型）}，供后续操作关联空间使用
     * - 失败：{success: false, code: 错误码, message: 错误详情}，如参数非法、未登录、创建失败等
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(
            @RequestBody SpaceAddRequest spaceAddRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 执行service
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(newId);
    }

    /**
     * 删除空间
     *
     * @param deleteRequest 包含待删除空间ID的请求体：通过id字段指定需要删除的空间
     * @param request       HTTP请求对象：用于用于获取当前登录用户信息
     * @return BaseResponse<Map < String, Object>> 接口响应对象：
     * - 成功：返回{success: true, data: {dbDeleted: true, fileDeleted: true, message: "..."}}
     * - 失败：返回包含错误码和错误信息的响应
     */
    @PostMapping("/delete")
    @Transactional
    public BaseResponse<Map<String, Object>> deleteSpace(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);
        // 2. 调用Service
        Map<String, Object> resultMap = spaceService.deleteSpace(deleteRequest, loginUser);
        // 3. 返回结果
        return ResultUtils.success(resultMap);
    }

    /**
     * 编辑空间
     *
     * @param spaceEditRequest 空间编辑请求体，包含待编辑的空间ID、标签、简介等字段
     * @param request          HTTP请求对象，用于获取当前登录用户信息
     * @return BaseResponse<Map < String, Object>> 接口响应对象，包含编辑结果详情
     */
    @PostMapping("/edit")
    public BaseResponse<Map<String, Object>> editSpace(
            @RequestBody SpaceEditRequest spaceEditRequest,
            HttpServletRequest request) {
        // 1. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 2. 调用Service
        Map<String, Object> resultMap = spaceService.editSpace(spaceEditRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(resultMap);
    }

    /**
     * 根据ID获取空间（VO）
     *
     * @param id      空间ID：用于定位待查询的空间记录，必须为大于0的有效数值
     * @param request HTTP请求对象：可用于获取当前登录用户信息（如判断权限、关联用户数据），
     *                具体用途由service层的getSpaceVO方法决定
     * @return BaseResponse<SpaceVO> 接口响应对象：
     * - 成功：返回{success: true, data: SpaceVO对象}，包含前端所需的空间展示数据
     * - 失败：返回包含错误码和错误信息的响应
     */
    @GetMapping("get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(id <= 0,
                ErrorCode.PARAMS_ERROR, "空间ID非法");

        // 2. 查询原始空间数据
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null,
                ErrorCode.NOT_FOUND_ERROR, "空间不存在");

        // 基础转换
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
        // 计算权限列表
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        spaceVO.setPermissionList(permissionList);

        // 3. 返回结果
        return ResultUtils.success(spaceVO);
    }

    /**
     * 分页获取空间列表（VO）
     *
     * @param spaceQueryRequest 空间查询请求体：包含分页参数（当前页、页大小）和查询条件
     * @param request           HTTP请求对象：用于获取用户上下文信息，支持后续业务扩展（如用户权限判断）
     * @return BaseResponse<Page < SpaceVO>> 接口响应对象：
     * - 成功：返回{success: true, data: Page<SpaceVO>}，包含分页信息和VO列表
     * - 失败：返回包含错误码和错误信息的响应
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(
            @RequestBody SpaceQueryRequest spaceQueryRequest,
            HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR, "无参数");

        // 2. 获取参数
        int current = spaceQueryRequest.getCurrent();
        int size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 3. 执行service
        Page<Space> spacePage = spaceService.page(
                new Page<>(current, size),
                spaceService.getLambdaQueryWrapper(spaceQueryRequest)
        );

        // 4. 返回结果
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    // endregion

    // region 管理员

    /**
     * 更新空间（管理员）
     *
     * @param spaceUpdateRequest 空间更新请求体：包含待更新的空间ID、标签、简介等字段
     * @return BaseResponse<Boolean> 接口响应对象：
     * - 成功：返回{success: true, data: true}，表示空间信息更新成功
     * - 失败：返回包含错误码（如PARAMS_ERROR、NOT_FOUND_ERROR等）和错误信息的响应
     */
    @PostMapping("update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        // 1. 调用service
        Map<String, Object> updateResult = spaceService.updateSpace(spaceUpdateRequest);
        // 2. 返回结果
        return ResultUtils.success(updateResult);
    }

    /**
     * 根据ID获取空间（管理员）
     *
     * @param id 空间ID
     * @return BaseResponse<Space> 接口响应对象：
     * - 成功：返回{success: true, data: Space对象}，包含空间的完整信息
     * - 失败：返回包含错误码（如PARAMS_ERROR、NOT_FOUND_ERROR等）和错误信息的响应
     */
    @GetMapping("get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        // 参数校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 执行service
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 返回结果
        return ResultUtils.success(space);
    }

    /**
     * 分页获取空间列表（管理员）
     *
     * @param spaceQueryRequest 空间查询请求体：包含分页参数（当前页、页大小）和查询条件（如URL、标签、用户ID等）
     * @return BaseResponse<Page < Space>> 接口响应对象：
     * - 成功：返回{success: true, data: Page<Space>}，包含分页元数据和空间实体列表
     * - 失败：返回包含错误码（如PARAMS_ERROR）和错误信息的响应
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(
            @RequestBody SpaceQueryRequest spaceQueryRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        int current = spaceQueryRequest.getCurrent();
        int size = spaceQueryRequest.getPageSize();
        // 执行service
        Page<Space> spacePage = spaceService.page(
                new Page<>(current, size),
                spaceService.getLambdaQueryWrapper(spaceQueryRequest)
        );
        return ResultUtils.success(spacePage);
    }

    // endregion

}
