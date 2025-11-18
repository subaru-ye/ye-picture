package com.ye.yepicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.manager.auth.StpKit;
import com.ye.yepicturebackend.model.vo.LoginUserVO;
import com.ye.yepicturebackend.model.vo.UserVO;
import com.ye.yepicturebackend.model.dto.space.SpaceAddRequest;
import com.ye.yepicturebackend.model.dto.user.UserQueryRequest;
import com.ye.yepicturebackend.model.dto.user.UserRegisterRequest;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.SpaceLevelEnum;
import com.ye.yepicturebackend.model.enums.UserRoleEnum;
import com.ye.yepicturebackend.service.SpaceService;
import com.ye.yepicturebackend.service.UserService;
import com.ye.yepicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.ye.yepicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Resource
    private BeanFactory beanFactory;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 参数校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号过短");
        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8,
                ErrorCode.PARAMS_ERROR, "用户密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword),
                ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");

        // 2. 检查账号是否重复
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper
                .eq(User::getUserAccount, userAccount);
        long count = this.baseMapper.selectCount(lambdaQueryWrapper);
        ThrowUtils.throwIf(count > 0,
                ErrorCode.PARAMS_ERROR, "账号重复");

        // 3. 对密码进行加密储存
        String encryptPassword = getEncryptPassword(userPassword);

        // 4. 实体参数设置
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());

        // 5. 执行数据库插入
        boolean saveResult = this.save(user);
        ThrowUtils.throwIf(!saveResult,
                ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");

        // 6. 返回新用户ID
        return user.getId();
    }

    /**
     * 组合操作：用户注册 + 自动创建私人空间
     *
     * @param registerRequest 用户注册请求体
     * @return 新注册用户ID
     */
    @Override
    public long registerAndCreateSpace(UserRegisterRequest registerRequest) {
        // 1. 提取注册参数
        String userAccount = registerRequest.getUserAccount();
        String userPassword = registerRequest.getUserPassword();
        String checkPassword = registerRequest.getCheckPassword();

        // 2. 执行用户注册
        UserService userServiceProxy = beanFactory.getBean(UserService.class);
        long userId = userServiceProxy.userRegister(userAccount, userPassword, checkPassword);

        // 3. 查询新用户信息
        User newUser = this.getById(userId);
        ThrowUtils.throwIf(newUser == null, ErrorCode.SYSTEM_ERROR, "用户注册成功但查询不到信息");

        // 4. 构建空间创建请求
        SpaceAddRequest spaceAddRequest = new SpaceAddRequest();
        spaceAddRequest.setSpaceName(userAccount + "的私人空间");
        spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());

        // 5. 执行空间创建
        long spaceId = spaceService.addSpace(spaceAddRequest, newUser);
        ThrowUtils.throwIf(spaceId <= 0, ErrorCode.OPERATION_ERROR, "空间创建失败");

        return userId;
    }


    /**
     * 密码加密储存
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        final String SALT = "ye"; // 加盐
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      http请求
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4,
                ErrorCode.PARAMS_ERROR, "账号错误");
        ThrowUtils.throwIf(userPassword.length() < 8,
                ErrorCode.PARAMS_ERROR, "密码错误");
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 查询用户是否存在
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper
                .eq(User::getUserAccount, userAccount)
                .eq(User::getUserPassword, encryptPassword);
        User user = this.baseMapper.selectOne(lambdaQueryWrapper);
        // 用户不存在
        ThrowUtils.throwIf(user == null,
                ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        // 3. 设置用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 4. 记录用户登录态到 Sa-token
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    /**
     * 获取脱敏的已登录用户信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获取当前登录用户
     *
     * @param request http请求
     * @return 当前登录用户
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        ThrowUtils.throwIf(currentUser == null || currentUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
        // 从数据库查询
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        ThrowUtils.throwIf(currentUser == null,
                ErrorCode.NOT_LOGIN_ERROR);
        return currentUser;
    }

    /**
     * 用户注销
     *
     * @param request http请求
     * @return 注销结果(是否成功)
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        ThrowUtils.throwIf(userObj == null,
                ErrorCode.OPERATION_ERROR, "未登录");
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取脱敏后的单个用户信息
     *
     * @param user 原始用户实体对象
     * @return UserVO 脱敏后的用户视图对象
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏的用户信息列表
     *
     * @param userList 原始用户实体对象列表
     * @return List<UserVO> 脱敏后的用户视图对象列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

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
    @Override
    public LambdaQueryWrapper<User> getLambdaQueryWrapper(UserQueryRequest userQueryRequest) {
        // 参数校验
        ThrowUtils.throwIf(userQueryRequest == null,
                ErrorCode.PARAMS_ERROR, "请求参数为空");
        // 获取参数
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        // 构造查询
        return new LambdaQueryWrapper<User>()
                .eq(ObjUtil.isNotNull(id), User::getId, id)
                .eq(StrUtil.isNotBlank(userRole), User::getUserRole, userRole)
                .like(StrUtil.isNotBlank(userAccount), User::getUserAccount, userAccount)
                .like(StrUtil.isNotBlank(userName), User::getUserName, userName)
                .like(StrUtil.isNotBlank(userProfile), User::getUserProfile, userProfile)
                .orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), User::getCreateTime);
    }

    /**
     * 判断用户是否为管理员角色
     *
     * @param user 待判断的用户实体对象
     * @return boolean 若用户不为null且角色为管理员（userRole = "admin"），返回true；
     * 若用户为null或角色为普通用户（userRole = "user"），返回false
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

}




