package com.ye.yepicturebackend.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.yepicturebackend.annotation.AuthCheck;
import com.ye.yepicturebackend.common.BaseResponse;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.common.ResultUtils;
import com.ye.yepicturebackend.constant.UserConstant;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.model.vo.LoginUserVO;
import com.ye.yepicturebackend.model.vo.UserVO;
import com.ye.yepicturebackend.model.dto.user.*;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    // region 通用增删改查

    /**
     * 用户注册
     *
     * @param userRegisterRequest 注册请求参数对象，包含：
     *                            - userAccount：用户账号（长度不小于4位）
     *                            - userPassword：用户密码（长度不小于8位）
     *                            - checkPassword：确认密码（需与密码一致）
     * @return BaseResponse<Long> 注册成功返回包含用户 ID 的成功响应；
     * 若参数无效、账号重复等情况，返回对应错误码的响应
     */
    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        // 2. 调用组合服务，完成“注册+创建空间”
        long userId = userService.registerAndCreateSpace(userRegisterRequest);
        // 3. 返回结果
        return ResultUtils.success(userId);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 登录请求参数对象，包含：
     *                         - userAccount：用户账号（已注册账号，长度不小于4位）
     *                         - userPassword：用户密码（与注册时密码一致，长度不小于8位）
     * @param request          HTTP请求对象，用于存储用户登录状态（会话信息）
     * @return BaseResponse<LoginUserVO> 登录成功返回包含脱敏用户信息（ID、昵称、头像、角色等）的响应；
     * 若参数无效、账号不存在、密码错误等情况，返回对应错误码的响应
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1. 校验并获取参数
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        // 2. 执行service
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        // 3. 返回结果
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 编辑个人信息
     *
     * @param userEditRequest 用户编辑请求参数对象，包含：
     *                        - userName：用户昵称（可选，需符合长度限制）
     *                        - userAvatar：用户头像URL（可选）
     *                        - userProfile：用户简介（可选）
     *                        - （注：无需传入id，系统自动从登录态获取当前用户ID）
     * @param request         HTTP请求对象，用于获取当前登录用户的会话信息（确定用户身份）
     * @return BaseResponse<Boolean> 编辑成功返回true；
     * 若参数无效、用户未登录或数据库操作失败，返回对应错误码的响应
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> userEdit(@RequestBody UserEditRequest userEditRequest, HttpServletRequest request) {
        // 1. 校验并获取参数
        ThrowUtils.throwIf(userEditRequest == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        // 2. 构建用户实体
        User user = new User();
        user.setId(userId);
        BeanUtils.copyProperties(userEditRequest, user);
        // 2.1单独处理密码加密
        String newPassword = userEditRequest.getUserPassword();
        if (StrUtil.isNotBlank(newPassword)) {
            String encryptPassword = userService.getEncryptPassword(newPassword);
            user.setUserPassword(encryptPassword);
        }
        // 3. 执行service
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 4. 返回结果
        return ResultUtils.success(true);
    }

    /**
     * 用户注销
     *
     * @param request HTTP请求对象，需包含当前登录用户的会话信息（用于清除登录状态）
     * @return BaseResponse<Boolean> 注销成功返回true；
     * 若用户未登录或会话已失效，返回对应错误码的响应
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 2. 执行service
        boolean result = userService.userLogout(request);
        // 3. 返回结果
        return ResultUtils.success(result);
    }

    /**
     * 根据ID获取脱敏后的用户信息
     *
     * @param id 用户ID
     * @return BaseResponse<UserVO> 查询成功返回包含脱敏用户信息（如ID、昵称、头像等）的响应；
     * 若ID无效、用户不存在或权限不足，返回对应错误码的响应
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        // 1. 获取用户信息
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        // 2. 返回脱敏后的结果
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 获取当前登录用户信息
     *
     * @param request HTTP HTTP请求对象，需包含已登录用户的会话信息（用于从会话中获取当前登录用户）
     * @return BaseResponse<LoginUserVO> 成功登录时返回包含脱敏用户的脱敏信息（包含ID、昵称、头像、角色等）；
     * 若用户未登录或会话已失效，返回未授权错误码的响应
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        // 1. 执行service
        User loginUser = userService.getLoginUser(request);
        // 2. 返回结果
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    // endregion

    // region 管理员相关

    /**
     * 创建用户（管理员）
     *
     * @param userAddRequest 用户创建请求参数对象，包含：
     *                       - userAccount：用户账号
     *                       - userName：用户昵称
     *                       - userAvatar：用户头像URL
     *                       - userProfile：用户简介
     *                       - userRole：用户角色
     * @return BaseResponse<Long> 创建成功返回包含新用户ID的成功响应；
     * 若参数无效、账号已存在或数据库操作失败，返回对应错误码的响应
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        // 1. 校验参数
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 2. 构建实体
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        String DEFAULT_PASSWORD = UserConstant.USER_DEFAULT_PASSWORD;
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        // 3. 执行数据库插入
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 4. 返回结果
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据ID获取用户完整信息（管理员）
     *
     * @param id 用户ID
     * @return BaseResponse<User> 查询成功返回包含用户完整信息的响应；
     * 若ID无效（<=0）返回参数错误响应，若用户不存在返回未找到错误响应
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        // 1. 参数校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 2. 执行service
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 返回结果
        return ResultUtils.success(user);
    }

    /**
     * 删除用户（管理员）
     *
     * @param deleteRequest 删除请求参数对象，包含：
     *                      - id：待删除用户的ID
     * @return BaseResponse<Boolean> 删除成功返回true；
     * 若请求参数无效（ID<=0或请求对象为null）返回参数错误响应，删除失败返回操作错误响应
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        // 1. 参数校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        // 2. 执行service
        boolean result = userService.removeById(deleteRequest.getId());
        // 3. 返回结果
        return ResultUtils.success(result);
    }

    /**
     * 更新用户信息（管理员）
     *
     * @param userUpdateRequest 用户更新请求参数对象，包含：
     *                          - id：待更新用户的ID（必传，不可为null）
     *                          - userName：用户昵称（可选，需符合长度限制）
     *                          - userAvatar：用户头像URL（可选）
     *                          - userProfile：用户简介（可选）
     *                          - userRole：用户角色（可选，如"user"/"admin"）
     * @return BaseResponse<Boolean> 更新成功返回true；
     * 若请求参数无效（ID为null或请求对象为null）返回参数错误响应，更新失败返回操作错误响应
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        // 1. 获取并校验参数
        ThrowUtils.throwIf(userUpdateRequest == null || userUpdateRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        // 2. 构建实体
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        // 3. 执行service
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 4. 返回结果
        return ResultUtils.success(true);
    }

    /**
     * 分页获取脱敏后的用户列表（管理员）
     *
     * @param userQueryRequest 用户分页查询请求参数对象，包含：
     *                         - current：当前页码（必传，需大于0）
     *                         - pageSize：每页条数（必传，需大于0）
     *                         - id：用户ID（可选，精准匹配）
     *                         - userAccount：用户账号（可选，模糊匹配）
     *                         - userName：用户昵称（可选，模糊匹配）
     *                         - userRole：用户角色（可选，精准匹配）
     *                         - sortField：排序字段（可选，如"createTime"）
     *                         - sortOrder：排序方向（可选，"ascend"为升序，其他为降序）
     * @return BaseResponse<Page < UserVO>> 查询成功返回包含分页信息（总条数、当前页数据）的脱敏用户列表响应；
     * 若请求参数无效（如页码/条数<=0）返回参数错误响应
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        // 1. 校验并获取参数
        ThrowUtils.throwIf(userQueryRequest == null,
                ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        // 2. 执行service
        Page<User> userPage = userService.page(
                new Page<>(current, pageSize),
                userService.getLambdaQueryWrapper(userQueryRequest)
        );
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        // 3. 返回结果
        return ResultUtils.success(userVOPage);
    }

    // endregion

}

