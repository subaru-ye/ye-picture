package com.ye.yepicturebackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ye.yepicturebackend.manager.auth.model.SpaceUserAuthConfig;
import com.ye.yepicturebackend.manager.auth.model.SpaceUserRole;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.SpaceUser;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.SpaceRoleEnum;
import com.ye.yepicturebackend.model.enums.SpaceTypeEnum;
import com.ye.yepicturebackend.service.SpaceUserService;
import com.ye.yepicturebackend.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 空间用户权限管理
 */
@Component
public class SpaceUserAuthManager {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    /**
     * 权限配置加载
     */
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /**
     * 角色 - 权限映射查询
     * 根据角色名称获取对应的权限列表
     *
     * @param spaceUserRole 角色标识
     * @return 权限字符串列表，如果角色不存在或参数为空则返回空列表
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        // 检查输入参数
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        // 角色匹配
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> spaceUserRole.equals(r.getKey()))
                .findFirst()
                .orElse(null);
        // 权限提取
        if (role == null) {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }

    /**
     * 获取用户在空间中的权限列表
     *
     * @param space     空间对象
     * @param loginUser 登录用户对象
     * @return 权限字符串列表
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }

}
