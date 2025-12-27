package com.ye.yepicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ye.yepicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.ye.yepicturebackend.manager.websocket.model.*;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片协作编辑的 WebSocket 消息处理器
 * <p>
 * 负责管理所有连接到“图片编辑”端点的 WebSocket 会话，并根据客户端发送的消息类型（进入/操作/退出）
 * 执行相应的业务逻辑，同时向其他协作者广播状态变更。
 * <p>
 */
@Component
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    /**
     * ✅ 编辑状态管理：记录每张图片当前是否被某用户独占编辑
     * key: pictureId, value: userId（正在编辑该图片的用户 ID）
     * 实现“单人编辑”锁机制
     */
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    /**
     * ✅ 会话分组管理：按 pictureId 分组存储所有活跃的 WebSocket 连接
     * key: pictureId, value: Set<WebSocketSession>
     * 用于实现“向同一图片的所有协作者广播”
     */
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 向指定图片的所有协作者广播消息（可排除某个会话）
     *
     * @param pictureId                  目标图片 ID
     * @param pictureEditResponseMessage 要广播的响应消息
     * @param excludeSession             要排除的会话（通常为消息发送者，避免回显）
     * @throws Exception 序列化或发送失败时抛出
     */
    private void broadcastToPicture(
            Long pictureId,
            PictureEditResponseMessage pictureEditResponseMessage,
            WebSocketSession excludeSession) throws Exception {

        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // ⚠️ 长整型精度问题：前端 JS Number 最大安全整数为 2^53-1，
            // 若直接序列化 Java Long（64位），可能在前端丢失精度。
            // 解决方案：将 Long 转为字符串传输。
            ObjectMapper objectMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);

            String messageJson = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(messageJson);

            for (WebSocketSession session : sessionSet) {
                // 跳过排除的会话（如发送者自己）
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                // 只向仍处于打开状态的会话发送消息
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 全量广播：向指定图片的所有协作者发送消息（不排除任何人）
     *
     * @param pictureId                  图片 ID
     * @param pictureEditResponseMessage 响应消息
     * @throws Exception 发送异常
     */
    private void broadcastToPicture(
            Long pictureId,
            PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * WebSocket 连接成功建立后的回调
     * <p>
     * 此时 session 已包含 WsHandshakeInterceptor 设置的 attributes（user, pictureId）。
     *
     * @param session 新建立的 WebSocket 会话
     * @throws Exception 初始化失败
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // 初始化该图片的会话集合（若不存在）
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造“用户加入”通知
        PictureEditResponseMessage msg = new PictureEditResponseMessage();
        msg.setType(PictureEditMessageTypeEnum.INFO.getValue());
        msg.setMessage(String.format("%s加入编辑", user.getUserName()));
        msg.setUser(userService.getUserVO(user)); // 转换为前端友好的 VO

        // 广播给其他协作者（包括刚加入的用户自己）
        broadcastToPicture(pictureId, msg);
    }

    /**
     * 接收到客户端文本消息时的处理入口
     * <p>
     * 本方法不直接处理业务逻辑，而是将请求投递给 Disruptor 异步队列，
     * 由后台消费者线程执行具体操作（避免阻塞 WebSocket 线程池）。
     *
     * @param session 客户端会话
     * @param message 客户端发送的 JSON 消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 将 JSON 字符串反序列化为请求对象
        PictureEditRequestMessage reqMsg = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        // 从 session 属性中提取上下文（由拦截器注入）
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");

        // ✅ 关键设计：将消息投递到 Disruptor 队列，实现异步、高性能处理
        pictureEditEventProducer.publishEvent(reqMsg, session, user, pictureId);
    }

    /**
     * 处理“进入编辑”请求（由 Disruptor 消费者调用）
     * <p>
     * 业务规则：仅当无人编辑时，当前用户才能获得编辑权。
     *
     * @param pictureEditRequestMessage 请求消息（此处未使用内容）
     * @param session                   客户端会话
     * @param user                      当前用户
     * @param pictureId                 图片 ID
     * @throws Exception 广播失败
     */
    public void handleEnterEditMessage(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId) throws Exception {

        // 检查是否已有用户在编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 获取编辑锁
            pictureEditingUsers.put(pictureId, user.getId());

            // 通知其他协作者：“XX 开始编辑”
            PictureEditResponseMessage respMsg = new PictureEditResponseMessage();
            respMsg.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            respMsg.setMessage(String.format("%s开始编辑图片", user.getUserName()));
            respMsg.setUser(userService.getUserVO(user));

            broadcastToPicture(pictureId, respMsg);
        }
        // 若已有人编辑，则静默忽略（前端可自行提示）
    }

    /**
     * 处理“编辑操作”请求（画笔、移动图层等）
     * <p>
     * 仅允许当前持有编辑锁的用户执行操作。
     *
     * @param pictureEditRequestMessage 包含 editAction 字段
     * @param session                   客户端会话
     * @param user                      当前用户
     * @param pictureId                 图片 ID
     * @throws Exception 广播失败
     */
    public void handleEditActionMessage(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId) throws Exception {

        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();

        // 校验动作类型是否合法
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return; // 无效动作，忽略
        }

        // 仅编辑者可操作
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage respMsg = new PictureEditResponseMessage();
            respMsg.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            respMsg.setMessage(String.format("%s执行%s", user.getUserName(), actionEnum.getText()));
            respMsg.setEditAction(editAction); // 传递具体操作类型
            respMsg.setUser(userService.getUserVO(user));

            // 广播给其他人（排除自己，避免重复渲染）
            broadcastToPicture(pictureId, respMsg, session);
        }
    }

    /**
     * 处理“退出编辑”请求
     * <p>
     * 释放编辑锁，并通知其他协作者。
     *
     * @param pictureEditRequestMessage 请求消息
     * @param session                   客户端会话
     * @param user                      当前用户
     * @param pictureId                 图片 ID
     * @throws Exception 广播失败
     */
    public void handleExitEditMessage(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId) throws Exception {

        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 仅编辑者可主动退出（或被动断连时触发）
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            pictureEditingUsers.remove(pictureId);

            PictureEditResponseMessage respMsg = new PictureEditResponseMessage();
            respMsg.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            respMsg.setMessage(String.format("%s退出编辑图片", user.getUserName()));
            respMsg.setUser(userService.getUserVO(user));

            broadcastToPicture(pictureId, respMsg);
        }
    }

    /**
     * WebSocket 连接关闭后的清理逻辑
     * <p>
     * 无论正常关闭还是异常断开，都需：
     * 1. 释放编辑锁（如果是编辑者）；
     * 2. 从会话集合中移除；
     * 3. 通知其他协作者“XX 离开”。
     *
     * @param session 关闭的会话
     * @param status  关闭原因（如 1001 浏览器刷新）
     * @throws Exception 清理失败
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");

        // 如果该用户是编辑者，自动释放锁
        handleExitEditMessage(null, session, user, pictureId);

        // 从会话集合中移除
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId); // 清理空分组
            }
        }

        // 通知其他协作者
        PictureEditResponseMessage leaveMsg = new PictureEditResponseMessage();
        leaveMsg.setType(PictureEditMessageTypeEnum.INFO.getValue());
        leaveMsg.setMessage(String.format("%s离开编辑", user.getUserName()));
        leaveMsg.setUser(userService.getUserVO(user));

        broadcastToPicture(pictureId, leaveMsg);
    }
}