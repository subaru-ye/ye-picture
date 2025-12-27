package com.ye.yepicturebackend.manager.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户维度的 WebSocket 会话管理器（用于系统通知、消息等）
 */
@Component
@Slf4j
public class UserWebSocketManager {

    // userId -> 该用户的所有 WebSocket 会话（支持多端登录）
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /**
     * 添加用户会话（在连接建立时调用）
     */
    public void addSession(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("用户 {} 建立 WebSocket 连接，当前会话数: {}", userId, userSessions.get(userId).size());
    }

    /**
     * 移除用户会话（在连接关闭时调用）
     */
    public void removeSession(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
            log.debug("用户 {} 关闭 WebSocket 连接，剩余会话数: {}", userId, sessions.size());
        }
    }

    /**
     * 向指定用户推送消息（JSON 字符串）
     *
     * @param userId      用户ID
     * @param jsonMessage JSON格式的消息内容
     * @return 是否至少成功发送到一个会话
     */
    public boolean sendMessageToUser(Long userId, String jsonMessage) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("用户 {} 不在线，无法推送 WebSocket 通知", userId);
            return false;
        }

        boolean sent = false;
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                    sent = true; // 至少有一个成功就算推送成功
                }
            } catch (IOException e) {
                log.warn("向用户 {} 推送 WebSocket 消息失败", userId, e);
                // 可选：移除失效会话
                sessions.remove(session);
            }
        }
        return sent;
    }
}