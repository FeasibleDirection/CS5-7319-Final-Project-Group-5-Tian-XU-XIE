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
 * WebSocket 核心处理器
 * - Architecture A: Server-authoritative
 * - Architecture B: P2P Host-authoritative + Server Relay
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

    // --------- Architecture B: P2P host 记录 ---------
    // roomId -> hostUsername（P2P 模式下的临时“房主”）
    private final Map<Long, String> p2pHosts = new ConcurrentHashMap<>();

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

    // ==================== 连接建立 / 关闭 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        logger.info("WebSocket connected: {}", sessionId);

        sendMessage(session, Map.of("type", "CONNECTED", "sessionId", sessionId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanupConnection(session.getId());
        logger.info("WebSocket disconnected: {}, status: {}", session.getId(), status);
    }

    private void cleanupConnection(String sessionId) {
        PlayerConnection conn = connections.remove(sessionId);
        if (conn != null) {
            Set<String> set = roomSessions.get(conn.roomId);
            if (set != null) {
                set.remove(sessionId);
            }
            logger.info("Player {} left room {} (arch={})", conn.username, conn.roomId, conn.arch);

            // 如果是 P2P host 离开，可以在这里做 host 迁移（当前版本先简单清空）
            String host = p2pHosts.get(conn.roomId);
            if (host != null && host.equals(conn.username)) {
                logger.info("P2P host {} left room {}, clear host", host, conn.roomId);
                p2pHosts.remove(conn.roomId);
            }
        }
        sessions.remove(sessionId);
    }

    // ==================== 消息分发 ====================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");
            String arch = (String) msg.getOrDefault("arch", "A"); // 默认 A

            switch (type) {
                case "JOIN_GAME":
                    if ("B".equalsIgnoreCase(arch)) {
                        handleJoinGameP2P(session, msg);
                    } else {
                        handleJoinGameArchA(session, msg);
                    }
                    break;

                // Architecture A: 服务器权威
                case "PLAYER_INPUT":
                    handlePlayerInputArchA(session, msg);
                    break;
                case "LEAVE_GAME":
                    handleLeaveGame(session);
                    break;

                // Architecture B: P2P
                case "P2P_INPUT":
                    handleP2PInput(session, msg);
                    break;
                case "P2P_STATE":
                    handleP2PState(session, msg);
                    break;

                default:
                    // 🔥 如果是架构B的消息类型，提示应该使用GameWebSocketHandlerB
                    if (type != null && (type.contains("POSITION") || type.contains("SPAWN") || 
                        type.equals("GAME_END_VOTE") || type.equals("JOIN_GAME_B"))) {
                        logger.warn("Unknown message type: {} - This looks like Architecture B message. " +
                                "Please connect to /ws/game-b endpoint instead of /ws/game", type);
                    } else {
                        logger.warn("Unknown message type: {}", type);
                    }
            }

        } catch (Exception e) {
            logger.error("Error handling message from {}", sessionId, e);
            sendMessage(session, Map.of("type", "ERROR", "message", e.getMessage()));
        }
    }

    // ==================== Architecture A ====================

    private void handleJoinGameArchA(WebSocketSession session, Map<String, Object> msg) throws IOException {
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
        connections.put(sessionId, new PlayerConnection(roomId, username, "A"));
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        logger.info("Player {} joined room {} (Arch A, WebSocket OK)", username, roomId);

        // 获取 GameWorld（Arch A 由服务器模拟）
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

    private void handlePlayerInputArchA(WebSocketSession session, Map<String, Object> msg) {
        PlayerConnection conn = connections.get(session.getId());
        if (conn == null || !"A".equals(conn.arch)) return;

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

        // 事件总线（可做日志 / 统计）
        eventBus.publish(new InputReceivedEvent(conn.roomId, conn.username, input));

        // 服务器权威移动
        physicsEngine.applyPlayerInput(player, input);

        // 服务器权威射击
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

    private void handleLeaveGame(WebSocketSession session) {
        cleanupConnection(session.getId());
    }

    // ==================== Architecture B: P2P ====================

    /**
     * P2P 加入房间：
     * - 第一个加入的玩家成为 host（在浏览器里跑物理）
     * - 服务端只负责转发 P2P_INPUT / P2P_STATE
     */
    private void handleJoinGameP2P(WebSocketSession session, Map<String, Object> msg) throws IOException {
        String username = (String) msg.get("username");
        String token = (String) msg.get("token");
        Long roomId = ((Number) msg.get("roomId")).longValue();

        boolean validToken = authService.getUserByToken(token)
                .map(u -> u.getUsername().equals(username))
                .orElse(false);

        if (!validToken) {
            sendMessage(session, Map.of("type", "ERROR", "message", "Invalid token"));
            session.close();
            return;
        }

        if (!roomManager.isPlayerInRoom(roomId, username)) {
            sendMessage(session, Map.of("type", "NOT_IN_ROOM", "message", "Not in room"));
            session.close();
            return;
        }

        String sessionId = session.getId();
        connections.put(sessionId, new PlayerConnection(roomId, username, "B"));
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        // 选 host：如果当前房间没有 host，就把这个人设为 host
        String host = p2pHosts.computeIfAbsent(roomId, id -> username);
        boolean isHost = host.equals(username);

        logger.info("Player {} joined room {} (Arch B, isHost={})", username, roomId, isHost);

        sendMessage(session, Map.of(
                "type", "JOINED_B",
                "roomId", roomId,
                "username", username,
                "host", host,
                "isHost", isHost,
                "architecture", "B"
        ));
    }

    /**
     * P2P 输入（所有 peer 都会发；host 用这些输入来驱动本地模拟）
     */
    private void handleP2PInput(WebSocketSession session, Map<String, Object> msg) {
        PlayerConnection conn = connections.get(session.getId());
        if (conn == null || !"B".equals(conn.arch)) return;

        Long roomId = conn.roomId;

        // 直接转发给房间内所有玩家（包括 host）
        Map<String, Object> relay = new LinkedHashMap<>();
        relay.put("type", "P2P_INPUT");
        relay.put("roomId", roomId);
        relay.put("from", conn.username);
        relay.put("moveUp", msg.getOrDefault("moveUp", false));
        relay.put("moveDown", msg.getOrDefault("moveDown", false));
        relay.put("moveLeft", msg.getOrDefault("moveLeft", false));
        relay.put("moveRight", msg.getOrDefault("moveRight", false));
        relay.put("fire", msg.getOrDefault("fire", false));
        relay.put("ts", System.currentTimeMillis());

        try {
            String json = objectMapper.writeValueAsString(relay);
            broadcastToRoom(roomId, json);
        } catch (Exception e) {
            logger.error("Failed to relay P2P_INPUT", e);
        }
    }

    /**
     * P2P 状态同步（只有 host 会发；服务器只负责广播）
     */
    private void handleP2PState(WebSocketSession session, Map<String, Object> msg) {
        PlayerConnection conn = connections.get(session.getId());
        if (conn == null || !"B".equals(conn.arch)) return;

        Long roomId = conn.roomId;
        String host = p2pHosts.get(roomId);
        if (host == null || !host.equals(conn.username)) {
            // 只有 host 可以发 P2P_STATE
            logger.warn("Non-host {} tried to send P2P_STATE for room {}", conn.username, roomId);
            return;
        }

        try {
            // 保留原始 JSON 结构，只加 roomId
            @SuppressWarnings("unchecked")
            Map<String, Object> stateMsg = (Map<String, Object>) msg;
            stateMsg.put("roomId", roomId);
            stateMsg.put("type", "GAME_STATE"); // 前端沿用 GAME_STATE 处理逻辑

            String json = objectMapper.writeValueAsString(stateMsg);
            broadcastToRoom(roomId, json);
        } catch (Exception e) {
            logger.error("Failed to relay P2P_STATE", e);
        }
    }

    // ==================== 工具方法 ====================

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

    private void sendMessage(WebSocketSession session, Map<String, Object> data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        session.sendMessage(new TextMessage(json));
    }

    /** 玩家连接信息 */
    private static class PlayerConnection {
        final long roomId;
        final String username;
        final String arch; // "A" or "B"

        PlayerConnection(long roomId, String username, String arch) {
            this.roomId = roomId;
            this.username = username;
            this.arch = arch;
        }
    }
}

