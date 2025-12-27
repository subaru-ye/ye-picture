package com.ye.yepicturebackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 全局配置类
 * <p>
 * 该类用于注册 WebSocket 端点（Endpoint）、绑定消息处理器（Handler），
 * 并配置握手拦截器（Interceptor）和跨域策略（CORS），是整个 WebSocket 功能的入口配置。
 * <p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    /**
     * 注册 WebSocket 处理器和路径映射
     * <p>
     * 此方法由 Spring 在启动时自动调用，用于声明 WebSocket 端点。
     *
     * @param registry WebSocket 处理器注册中心，用于添加 Handler 和 Interceptor
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册一个 WebSocket 端点：/ws/picture/edit
        // 客户端可通过 ws://<host>/ws/picture/edit 建立连接
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                // 为该端点添加握手拦截器，在连接建立前进行权限校验
                .addInterceptors(wsHandshakeInterceptor)
                // ⚠️ 允许所有来源跨域访问（开发便利，但生产环境存在安全风险）
                // 建议在生产中替换为具体的前端域名，如 "https://your-frontend.com"
                .setAllowedOrigins("*");
    }
}