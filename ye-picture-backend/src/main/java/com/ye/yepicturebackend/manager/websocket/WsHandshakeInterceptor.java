package com.ye.yepicturebackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.ye.yepicturebackend.manager.auth.SpaceUserAuthManager;
import com.ye.yepicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.SpaceTypeEnum;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.service.SpaceService;
import com.ye.yepicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手拦截器（Handshake Interceptor）
 * <p>
 * 作用：在 WebSocket 连接正式建立前（即 HTTP 升级为 WebSocket 之前），
 * 对客户端发起的连接请求进行安全校验和上下文初始化。
 * <p>
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 在 WebSocket 握手前执行的校验逻辑。
     *
     * @param request    当前握手请求（封装了原始 HTTP 请求）
     * @param response   握手响应（可用于设置 header，但通常不使用）
     * @param wsHandler  即将处理该连接的 WebSocketHandler 实例（此处为 PictureEditHandler）
     * @param attributes 用于在拦截器与 Handler 之间传递数据的共享 Map（线程安全）
     * @return true 允许连接，false 拒绝连接
     */
    @Override
    public boolean beforeHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Map<String, Object> attributes) {

        // 进行各个权限校验
        if (request instanceof ServletServerHttpRequest) {
            // 获取原始的 HttpServletRequest，以便读取 Cookie、Session、参数等
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

            // 1.从 URL 查询参数中获取 pictureId
            String pictureIdParam = servletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureIdParam)) {
                log.error("WebSocket 握手失败：缺少必需参数 'pictureId'");
                return false; // 拒绝连接
            }

            // 2. 判断是否登录
            User loginUser = userService.getLoginUser(servletRequest);
            if (ObjUtil.isEmpty(loginUser)) {
                log.error("WebSocket 握手失败：用户未登录或身份无效");
                return false; // 未登录用户禁止接入
            }

            // 3. 查询图片是否存在
            Picture picture = pictureService.getById(pictureIdParam);
            if (picture == null) {
                log.error("WebSocket 握手失败：图片不存在，pictureId={}", pictureIdParam);
                return false;
            }

            // 4. 获取图片所属的空间
            Long spaceId = picture.getSpaceId();
            Space space = null;

            // 如果图片属于某个空间（非个人图片）
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (space == null) {
                    log.error("WebSocket 握手失败：图片所属空间不存在，spaceId={}", spaceId);
                    return false;
                }

                // ⚠️ 业务规则：仅允许“团队空间”开启协作编辑
                // 个人空间（SpaceTypeEnum.PERSONAL）不支持多人协作
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.info("WebSocket 握手被拒绝：非团队空间不支持协作编辑，spaceId={}", spaceId);
                    return false;
                }
            }

            // 5. 权限校验：检查当前用户是否有 PICTURE_EDIT 权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("WebSocket 握手失败：用户无图片编辑权限，userId={}, pictureId={}",
                        loginUser.getId(), pictureIdParam);
                return false;
            }

            // ✅ 所有校验通过！将关键上下文信息存入 attributes
            // 这些数据将在 PictureEditHandler 的 afterConnectionEstablished 等方法中使用
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.valueOf(pictureIdParam));

            // 日志记录成功握手尝试
            log.info("WebSocket 握手成功：用户 {} 尝试连接图片 {}", loginUser.getUserName(), pictureIdParam);
        } else {
            // 非 Servlet 环境不支持此拦截器逻辑
            log.warn("不支持的 ServerHttpRequest 类型：{}", request.getClass().getName());
            return false;
        }

        // 返回 true 表示允许 WebSocket 连接升级
        return true;
    }

    /**
     * 握手完成后调用（无论成功或失败）。
     *
     * @param request   握手请求
     * @param response  握手响应
     * @param wsHandler 对应的 WebSocket 处理器
     * @param exception 如果握手过程中抛出异常，则此参数非 null
     */
    @Override
    public void afterHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            Exception exception) {
        // 可在此处记录连接失败原因（如 exception != null）
        if (exception != null) {
            log.warn("WebSocket 握手后发生异常", exception);
        }
    }
}