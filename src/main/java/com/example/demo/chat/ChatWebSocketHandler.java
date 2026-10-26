package com.example.demo.chat;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ChatService service;
    private final ChatDeliveryService delivery;
    private final ObjectMapper json;
    private final com.example.demo.system.rbac.RbacService rbac;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    // 序列化本实例的订阅和即时广播，数据库写入不在此锁中执行。
    private final Object[] groupLocks = new Object[64];

    public ChatWebSocketHandler(ChatService service, ChatDeliveryService delivery, ObjectMapper json, com.example.demo.system.rbac.RbacService rbac) {
        this.service = service;
        this.delivery = delivery;
        this.json = json;
        this.rbac = rbac;
        for (int i = 0; i < groupLocks.length; i++) groupLocks[i] = new Object();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(16384);
        clients.put(session.getId(), new Client(new ConcurrentWebSocketSessionDecorator(session, 5000, 262144)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage frame) {
        Client client = clients.get(session.getId());
        if (client == null) return;
        String requestId = "";
        try {
            JsonNode body;
            try { body = json.readTree(frame.getPayload()); }
            catch (RuntimeException exception) { throw badRequest("无效的 JSON 消息"); }
            if (body == null || !body.isObject()) throw badRequest("消息必须是 JSON 对象");
            requestId = body.path("requestId").asText("");
            if (requestId.length() > 64) throw badRequest("requestId 过长");
            String type = body.path("type").asText("");
            if (client.token == null) {
                if (!"auth".equals(type)) throw unauthorized();
                String token = body.path("token").asText("");
                if (token.isBlank() || token.length() > 256) throw unauthorized();
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId == null) throw unauthorized();
                int userId = Integer.parseInt(loginId.toString());
                var user = service.requireUser(userId);
                rbac.check(userId,"chat:access");
                client.userId = userId;
                client.senderName = user.getName() == null || user.getName().isBlank() ? user.getAccount() : user.getName();
                client.token = token;
                write(client, Map.of("type", "ready", "userId", userId));
                return;
            }
            checkLogin(client);
            if ("ping".equals(type)) {
                write(client, Map.of("type", "pong"));
                return;
            }
            long groupId = positiveLong(body, "groupId");
            synchronized (groupLocks[(int) (groupId % groupLocks.length)]) {
                switch (type) {
                    case "subscribe" -> {
                        service.requireMember(groupId, client.userId);
                        client.groupId = groupId;
                        // 先订阅，再由客户端读取分页历史。重叠部分按消息 ID 去重，不留收发间隙。
                        write(client, Map.of("type", "subscribed", "groupId", groupId, "requestId", requestId));
                        for (var pending : delivery.pendingMessages(groupId))
                            write(client, Map.of("type", "message", "message", pending));
                    }
                    case "send" -> {
                        String content = textField(body, "content");
                        String clientMessageId = textField(body, "clientMessageId");
                        // 已订阅的群在订阅时完成成员校验；当前没有退出/移除成员接口。
                        if (client.groupId != groupId) service.requireMember(groupId, client.userId);
                        var draft = service.prepare(groupId, client.userId, client.senderName, content, clientMessageId);
                        String correlation = requestId;
                        delivery.send(draft, accepted -> {
                            broadcast(groupId, "message", accepted);
                            reply(client, Map.of("type", "ack", "requestId", correlation, "message", accepted));
                        }, result -> {
                            String resultType = "saved".equals(result.getDeliveryStatus()) ? "saved" : "save_failed";
                            broadcast(groupId, resultType, result);
                            if (client.groupId != groupId)
                                reply(client, Map.of("type", resultType, "requestId", correlation, "message", result));
                        });
                    }
                    default -> throw badRequest("不支持的消息类型");
                }
            }
        } catch (ResponseStatusException exception) {
            write(client, Map.of("type", "error", "requestId", requestId,
                    "status", exception.getStatusCode().value(), "message", exception.getReason()));
            if (exception.getStatusCode().value() == 401) close(client, CloseStatus.POLICY_VIOLATION);
        } catch (RuntimeException exception) {
            // 不记录消息帧、Token 或异常详情中的 SQL 参数。
            log.warn("Chat request failed: {}", exception.getClass().getSimpleName());
            write(client, Map.of("type", "error", "requestId", requestId,
                    "status", 500, "message", "消息处理失败，可使用相同消息编号重试"));
        }
    }

    private void checkLogin(Client client) {
        Object loginId = StpUtil.getLoginIdByToken(client.token);
        if (loginId == null || !loginId.toString().equals(Integer.toString(client.userId))) throw unauthorized();
        rbac.check(client.userId,"chat:access");
    }

    private void broadcast(long groupId, String type, ChatModels.Message message) {
        for (Client recipient : clients.values()) {
            if (recipient.groupId == groupId && recipient.token != null)
                reply(recipient, Map.of("type", type, "message", message));
        }
    }

    private void reply(Client client, Object body) {
        try { checkLogin(client); write(client, body); }
        catch (RuntimeException exception) { close(client, CloseStatus.POLICY_VIOLATION); }
    }

    @Scheduled(fixedDelay = 5000)
    public void expireConnections() {
        for (Client client : clients.values()) {
            try {
                if (client.token == null) {
                    if (System.currentTimeMillis() - client.connectedAt > 10000) close(client, CloseStatus.POLICY_VIOLATION);
                } else checkLogin(client);
            } catch (ResponseStatusException exception) {
                close(client, CloseStatus.POLICY_VIOLATION);
            } catch (RuntimeException exception) {
                close(client, CloseStatus.SERVER_ERROR);
            }
        }
    }

    private void write(Client client, Object body) {
        try {
            if (client.session.isOpen()) client.session.sendMessage(new TextMessage(json.writeValueAsString(body)));
        } catch (IOException | RuntimeException exception) {
            close(client, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(Client client, CloseStatus status) {
        clients.remove(client.session.getId(), client);
        try { client.session.close(status); } catch (IOException ignored) { }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        clients.remove(session.getId());
        session.close(CloseStatus.SERVER_ERROR);
    }

    private static long positiveLong(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0)
            throw badRequest(field + " 必须为正整数");
        return value.asLong();
    }

    private static String textField(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) throw badRequest(field + " 必须为字符串");
        return value.asText();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
    }

    private static final class Client {
        final WebSocketSession session;
        final long connectedAt = System.currentTimeMillis();
        volatile String token;
        int userId;
        String senderName;
        volatile long groupId;
        Client(WebSocketSession session) { this.session = session; }
    }
}
