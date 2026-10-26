package com.example.demo.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@EnableScheduling
public class ChatConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    public ChatConfig(ChatWebSocketHandler handler) { this.handler = handler; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 默认只接受同源浏览器连接；登录凭证通过首帧发送，不放进 URL。
        registry.addHandler(handler, "/ws/chat");
    }
}
