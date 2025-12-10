package com.projectgroup5.gamedemo.config;

import com.projectgroup5.gamedemo.websocket.GameWebSocketHandlerB;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket Configuration - Architecture B: P2P Lockstep
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandlerB gameWebSocketHandlerB;

    public WebSocketConfig(GameWebSocketHandlerB gameWebSocketHandlerB) {
        this.gameWebSocketHandlerB = gameWebSocketHandlerB;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandlerB, "/ws/game")
                .setAllowedOrigins("*");
    }
}
