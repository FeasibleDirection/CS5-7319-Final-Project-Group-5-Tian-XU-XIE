package com.projectgroup5.gamedemo.config;

import com.projectgroup5.gamedemo.websocket.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket Configuration - Architecture A: Server-Authoritative (Layered)
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandlerA;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandlerA) {
        this.gameWebSocketHandlerA = gameWebSocketHandlerA;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandlerA, "/ws/game")
                .setAllowedOrigins("*");
    }
}

