package com.ye.yepicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户编辑请求
 */
@Data
public class UserEditRequest implements Serializable {


    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    private static final long serialVersionUID = 2973991595757692690L;

}
