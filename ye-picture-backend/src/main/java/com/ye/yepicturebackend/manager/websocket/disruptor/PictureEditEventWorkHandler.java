package com.ye.yepicturebackend.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.ye.yepicturebackend.manager.websocket.PictureEditHandler;
import com.ye.yepicturebackend.manager.websocket.model.*;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * Disruptor 事件消费者（工作处理器）
 */
@Slf4j
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    @Lazy
    private PictureEditHandler pictureEditHandler;

    @Resource
    private UserService userService;

    /**
     * Disruptor 消费者核心方法：处理一个图片编辑事件
     * <p>
     * 此方法由 Disruptor 的工作线程池调用，运行在后台线程中，
     * 因此可以安全地执行可能阻塞的操作（如数据库访问、复杂计算、广播等），
     * 而不会影响 WebSocket 主 I/O 线程的吞吐量。
     *
     * @param event 从生产者发布的事件对象，包含完整上下文
     * @throws Exception 业务处理异常（会被 Disruptor 捕获并记录）
     */
    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        // 1. 从事件中提取上下文信息
        PictureEditRequestMessage requestMsg = event.getPictureEditRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();

        // 2. 解析消息类型（客户端发送的 type 字段）
        String typeStr = requestMsg.getType();
        PictureEditMessageTypeEnum msgType;

        try {
            // 尝试将字符串转换为枚举（注意：valueOf 对大小写敏感）
            msgType = PictureEditMessageTypeEnum.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            // 如果类型非法，构造错误响应并直接返回给客户端
            log.warn("收到非法消息类型: {}, userId={}, pictureId={}", typeStr, user.getId(), pictureId);

            PictureEditResponseMessage errorMsg = new PictureEditResponseMessage();
            errorMsg.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            errorMsg.setMessage("不支持的消息类型: " + typeStr);
            errorMsg.setUser(userService.getUserVO(user));

            if (session.isOpen()) {
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(errorMsg)));
            }
            return; // 不再继续处理
        }

        // 3. 根据消息类型分发到具体处理逻辑
        switch (msgType) {
            case ENTER_EDIT:
                // 用户请求进入编辑模式（尝试获取编辑锁）
                pictureEditHandler.handleEnterEditMessage(requestMsg, session, user, pictureId);
                break;

            case EDIT_ACTION:
                // 用户执行具体编辑操作（画笔、移动、缩放等）
                pictureEditHandler.handleEditActionMessage(requestMsg, session, user, pictureId);
                break;

            case EXIT_EDIT:
                // 用户主动退出编辑（释放编辑锁）
                pictureEditHandler.handleExitEditMessage(requestMsg, session, user, pictureId);
                break;

            default:
                // 理论上不会走到这里（因枚举已覆盖），但保留防御性编程
                log.warn("未处理的消息类型: {}", msgType);
                PictureEditResponseMessage unknownMsg = new PictureEditResponseMessage();
                unknownMsg.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                unknownMsg.setMessage("服务器暂不支持该操作");
                unknownMsg.setUser(userService.getUserVO(user));

                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(JSONUtil.toJsonStr(unknownMsg)));
                }
        }
    }
}