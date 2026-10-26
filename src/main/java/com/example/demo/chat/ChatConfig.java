package com.example.demo.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;
import javax.sql.DataSource;

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

    @Bean
    @ConditionalOnProperty(name = "chat.initialize-schema", havingValue = "true", matchIfMissing = true)
    public DataSourceInitializer chatSchemaInitializer(DataSource dataSource) {
        var initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("db/chat-schema.sql")));
        return initializer;
    }
}
