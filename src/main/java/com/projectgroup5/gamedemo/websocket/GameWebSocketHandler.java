package com.projectgroup5.gamedemo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectgroup5.gamedemo.event.EventBus;
import com.projectgroup5.gamedemo.event.InputReceivedEvent;
import com.projectgroup5.gamedemo.game.*;
import com.projectgroup5.gamedemo.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket核心处理器 - Architecture A 的网络层
 * 职责：
 * 1. 管理WebSocket连接
 * 2. 接收客户端输入
 * 3. 广播服务器状态
 * 4. 房间权限验证
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameRoomManager roomManager;
    private final PhysicsEngine physicsEngine;
    private final AuthService authService;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    // sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // sessionId -> PlayerConnection
    private final Map<String, PlayerConnection> connections = new ConcurrentHashMap<>();

    // roomId -> Set<sessionId>
    private final Map<Long, Set<String>> roomSessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(
            GameRoomManager roomManager,
            PhysicsEngine physicsEngine,
            AuthService authService,
            EventBus eventBus,
            ObjectMapper objectMapper
    ) {
        this.roomManager = roomManager;
        this.physicsEngine = physicsEngine;
        this.authService = authService;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        session.sendMessage(new TextMessage(json));
    }

    /** 新连接建立 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        logger.info("WebSocket connected: {}", sessionId);

        sendMessage(session, Map.of("type", "CONNECTED", "sessionId", sessionId));
    }

    /** WebSocket 收到消息 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");

            switch (type) {
                case "JOIN_GAME":
                    handleJoinGame(session, msg);
                    break;

                case "PLAYER_INPUT":
                    handlePlayerInput(session, msg);
                    break;

                case "LEAVE_GAME":
                    handleLeaveGame(session);
                    break;

                default:
                    logger.warn("Unknown message type: {}", type);
            }

        } catch (Exception e) {
            logger.error("Error handling message from {}", sessionId, e);
            sendMessage(session, Map.of("type", "ERROR", "message", e.getMessage()));
        }
    }

    /** 玩家加入游戏 */
    private void handleJoinGame(WebSocketSession session, Map<String, Object> msg) throws IOException {
        String username = (String) msg.get("username");
        String token = (String) msg.get("token");
        Long roomId = ((Number) msg.get("roomId")).longValue();

        // token 验证
        boolean validToken = authService.getUserByToken(token)
                .map(u -> u.getUsername().equals(username))
                .orElse(false);

        if (!validToken) {
            sendMessage(session, Map.of("type", "ERROR", "message", "Invalid token"));
            session.close();
            return;
        }

        // 必须在房间
        if (!roomManager.isPlayerInRoom(roomId, username)) {
            sendMessage(session, Map.of("type", "NOT_IN_ROOM", "message", "Not in room"));
            session.close();
            return;
        }

        // 注册连接
        String sessionId = session.getId();
        connections.put(sessionId, new PlayerConnection(roomId, username));
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        logger.info("Player {} joined room {} (WebSocket OK)", username, roomId);

        // 获取 GameWorld
        Optional<GameWorld> worldOpt = roomManager.getGameRoom(roomId);

        if (worldOpt.isEmpty()) {
            logger.error("GameWorld does NOT exist for room {}", roomId);
            sendMessage(session, Map.of("type", "ERROR", "message", "Game not started"));
            return;
        }

        GameWorld world = worldOpt.get();

        // 避免重复添加
        if (!world.getPlayers().containsKey(username)) {
            world.addPlayer(username);
            logger.info("Player {} added to GameWorld room {}", username, roomId);
        }

        // 如果是 WAITING → 开始倒计时
        if (world.getPhase() == GameWorld.GamePhase.WAITING) {
            world.setGameStartTime(System.currentTimeMillis() + 3000); // 3 秒倒计时
            world.setPhase(GameWorld.GamePhase.COUNTDOWN);
        }

        // 回复前端
        sendMessage(session, Map.of(
                "type", "JOINED",
                "roomId", roomId,
                "username", username,
                "architecture", "A"
        ));
    }

    /** 玩家输入 */
    private void handlePlayerInput(WebSocketSession session, Map<String, Object> msg) {
        PlayerConnection conn = connections.get(session.getId());
        if (conn == null) return;

        Optional<GameWorld> worldOpt = roomManager.getGameRoom(conn.roomId);
        if (worldOpt.isEmpty()) return;

        GameWorld world = worldOpt.get();
        if (world.getPhase() != GameWorld.GamePhase.IN_PROGRESS) return;

        PlayerEntity player = world.getPlayers().get(conn.username);
        if (player == null || !player.alive) return;

        PlayerInput input = new PlayerInput();
        input.setUsername(conn.username);
        input.setMoveUp((Boolean) msg.getOrDefault("moveUp", false));
        input.setMoveDown((Boolean) msg.getOrDefault("moveDown", false));
        input.setMoveLeft((Boolean) msg.getOrDefault("moveLeft", false));
        input.setMoveRight((Boolean) msg.getOrDefault("moveRight", false));
        input.setFire((Boolean) msg.getOrDefault("fire", false));
        input.setTimestamp(System.currentTimeMillis());

        eventBus.publish(new InputReceivedEvent(conn.roomId, conn.username, input));

        physicsEngine.applyPlayerInput(player, input);

        // 射击
        if (input.isFire()) {
            long now = System.currentTimeMillis();
            if (physicsEngine.canFire(player, now)) {
                BulletEntity bullet = physicsEngine.createBullet(
                        player.username,
                        player.x,
                        player.y - PlayerEntity.HEIGHT / 2
                );
                world.getBullets().put(bullet.id, bullet);
                player.lastFireTime = now;
            }
        }
    }

    /** 玩家离开 */
    private void handleLeaveGame(WebSocketSession session) {
        cleanupConnection(session.getId());
    }

    /** 断开连接 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanupConnection(session.getId());
        logger.info("WebSocket disconnected: {}", session.getId());
    }

    private void cleanupConnection(String sessionId) {
        PlayerConnection conn = connections.remove(sessionId);
        if (conn != null) {
            Set<String> set = roomSessions.get(conn.roomId);
            if (set != null) set.remove(sessionId);
        }
        sessions.remove(sessionId);
    }

    /** 广播到房间所有 WebSocket 客户端 */
    public void broadcastToRoom(long roomId, String message) {
        Set<String> set = roomSessions.get(roomId);
        if (set == null) return;

        for (String sid : set) {
            WebSocketSession session = sessions.get(sid);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    logger.error("Send fail session {}", sid, e);
                }
            }
        }
    }

    /** 将 GameState 推送给玩家 */
    public void broadcastGameState(GameWorld world) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "GAME_STATE",
                    "players", world.getPlayers().values(),
                    "bullets", world.getBullets().values(),
                    "asteroids", world.getAsteroids().values(),
                    "phase", world.getPhase().name(),
                    "frame", world.getCurrentFrameNumber()
            );

            String json = objectMapper.writeValueAsString(msg);
            broadcastToRoom(world.getRoomId(), json);

        } catch (Exception e) {
            logger.error("Failed to broadcast game state", e);
        }
    }

    /** 玩家连接信息 */
    private static class PlayerConnection {
        final long roomId;
        final String username;
        PlayerConnection(long roomId, String username) {
            this.roomId = roomId;
            this.username = username;
        }
    }
}

