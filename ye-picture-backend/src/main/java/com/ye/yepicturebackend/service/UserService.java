package com.ye.yepicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.yepicturebackend.model.vo.LoginUserVO;
import com.ye.yepicturebackend.model.vo.UserVO;
import com.ye.yepicturebackend.model.dto.user.UserQueryRequest;
import com.ye.yepicturebackend.model.dto.user.UserRegisterRequest;
import com.ye.yepicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 组合操作：用户注册 + 自动创建私人空间
     *
     * @param registerRequest 用户注册请求体
     * @return 新注册用户ID
     */
    long registerAndCreateSpace(UserRegisterRequest registerRequest);

    /**
     * 密码加密储存
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      http请求
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取当前登录用户
     *
     * @param request http请求
     * @return 当前登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request http请求
     * @return 注销结果(是否成功)
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏后的单个用户信息
     *
     * @param user 原始用户实体对象
     * @return UserVO 脱敏后的用户视图对象
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息列表
     *
     * @param userList 原始用户实体对象列表
     * @return List<UserVO> 脱敏后的用户视图对象列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 构建用户查询的 LambdaQueryWrapper
     *
     * @param userQueryRequest 用户查询请求参数对象，包含：
     *                         - id：用户ID（精准匹配）
     *                         - userAccount：用户账号（模糊匹配）
     *                         - userName：用户昵称（模糊匹配）
     *                         - userProfile：用户简介（模糊匹配）
     *                         - userRole：用户角色（精准匹配）
     *                         - sortField：排序字段（非空时生效）
     *                         - sortOrder：排序方向
     * @return LambdaQueryWrapper<User> 构建好的查询条件包装器，可直接用于 MyBatis-Plus 的查询方法
     */
    LambdaQueryWrapper<User> getLambdaQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 判断用户是否为管理员角色
     *
     * @param user 待判断的用户实体对象
     * @return boolean 若用户不为null且角色为管理员（userRole = "admin"），返回true；
     * 若用户为null或角色为普通用户（userRole = "user"），返回false
     */
    boolean isAdmin(User user);

}
