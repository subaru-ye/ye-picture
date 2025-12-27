package com.ye.yepicturebackend.constant;

import com.ye.yepicturebackend.model.vo.user.UserVO;

/**
 * 用户相关常量工具类
 */
public final class UserConstant {

    private UserConstant() {} // 工具类禁止实例化

    public static final String USER_LOGIN_STATE = "user_login";
    public static final String USER_DEFAULT_PASSWORD = "12345678";

    public static final String DEFAULT_ROLE = "user";
    public static final String ADMIN_ROLE = "admin";

    /**
     * 默认的“未知用户”VO（不可变）
     */
    public static final UserVO UNKNOWN_USER_VO = createUnknownUserVO();

    private static UserVO createUnknownUserVO() {
        UserVO userVO = new UserVO();
        userVO.setId(-1L);
        userVO.setUserName("未知用户");
        return userVO;
    }
}