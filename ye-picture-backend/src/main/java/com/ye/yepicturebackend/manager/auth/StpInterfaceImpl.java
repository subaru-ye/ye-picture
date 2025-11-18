package com.ye.yepicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.SpaceUser;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.SpaceRoleEnum;
import com.ye.yepicturebackend.model.enums.SpaceTypeEnum;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.service.SpaceService;
import com.ye.yepicturebackend.service.SpaceUserService;
import com.ye.yepicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.ye.yepicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    /**
     * 获取用户在当前场景下的权限列表
     *
     * @param loginId   登录用户标识
     * @param loginType 登录类型（仅支持"space"）
     * @return 权限key列表，若无权限返回空列表
     */
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1：校验登录类型：不是 "space"，直接返回空权限列表
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }

        // 2：管理员权限兜底
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        // 3：获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 公共图库操作，返回管理员权限列表
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }

        // 4：校验登录状态
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();

        // 5：从上下文中优先获取 SpaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }

        // 6：通过 spaceUserId 获取空间用户信息
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            // 查询对应的 spaceUser 数据
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 校验当前登录用户权限
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 根据登录用户在该空间的角色，返回相应的权限码列表
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }

        // 7：通过 spaceId 或 pictureId 获取空间或图片信息
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            // 使用 pictureId 查询图片信息
            Long pictureId = authContext.getPictureId();
            // 如果 pictureId 和 spaceId 均为空，默认视为管理员权限
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();

            // 对于公共图库：如果图片是当前用户上传的，或者当前用户为管理员，返回管理员权限列表
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }

        // 8：获取 space 对象并判断空间类型
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 私有空间：仅空间所有者和管理员有权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        }
        // 团队空间：查询登录用户在该空间的角色，并返回对应的权限码列表
        else {
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    /**
     * 判断对象所有字段是否为空
     * 用于识别“公共图库操作”场景（上下文中无任何资源信息）
     *
     * @param object 待判断对象
     * @return true-所有字段为空；false-存在非空字段
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 反射获取所有字段，判断每个字段值是否为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                .map(field -> ReflectUtil.getFieldValue(object, field))
                .allMatch(ObjectUtil::isEmpty);
    }

    /**
     * 本项目不用
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return null;
    }

    /**
     * 从请求中解析上下文对象
     * 功能：解析HTTP请求参数，智能映射资源ID（图片、空间、空间用户），构建完整的授权上下文
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 1. 获取当前HTTP请求对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getRequest();

        // 2. 判断请求参数格式
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;

        // 3. 兼容GET和POST请求，解析请求参数为上下文对象
        if (ContentType.JSON.getValue().equals(contentType)) {
            // POST请求（JSON格式）：解析请求体
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            // GET请求（表单/路径参数）：解析参数映射
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 4. 动态映射资源 ID
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI(); // 示例：/yepicture/picture/123
            String partUri = requestUri.replace(contextPath + "/", ""); // 剔除上下文路径：picture/123
            String moduleName = StrUtil.subBefore(partUri, "/", false); // 提取模块名：picture

            // 根据模块名映射id为对应资源ID
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
                    // 若为新模块，可在此扩展
            }
        }
        return authRequest;
    }

}
