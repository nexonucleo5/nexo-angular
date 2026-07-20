package com.nexo.config;

import com.nexo.web.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/** Registra o endpoint WebSocket do chat em tempo real. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(ChatWebSocketHandler chatHandler,
                           @Value("${nexo.cors.allowed-origins}") List<String> allowedOrigins) {
        this.chatHandler = chatHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
