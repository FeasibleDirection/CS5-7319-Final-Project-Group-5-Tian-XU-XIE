package com.projectgroup5.gamedemo.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectgroup5.gamedemo.dao.GameLogRepository;
import com.projectgroup5.gamedemo.entity.GameLog;
import com.projectgroup5.gamedemo.event.EventBus;
import com.projectgroup5.gamedemo.event.GameEndedEvent;
import com.projectgroup5.gamedemo.service.LobbyService;
import com.projectgroup5.gamedemo.websocket.GameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏主循环调度器 - Architecture A 核心
 * 25Hz (每40ms一帧) 的固定时间步长
 * 
 * 数据流: 
 * Client Input → WebSocket → Event:InputReceived → Physics Tick → 
 * Collision Detection → Event:Collision/Score → State Snapshot → 
 * WebSocket → Clients (Render)
 */
@Component
public class GameTickScheduler {
    private static final Logger logger = LoggerFactory.getLogger(GameTickScheduler.class);
    private static final double TICK_RATE = 25.0; // Hz
    private static final double DELTA_TIME = 1.0 / TICK_RATE; // 0.04秒
    
    private final GameRoomManager roomManager;
    private final PhysicsEngine physicsEngine;
    private final GameWebSocketHandler webSocketHandler;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;
    private final GameLogRepository gameLogRepository;
    private final LobbyService lobbyService;
    
    public GameTickScheduler(GameRoomManager roomManager,
                            PhysicsEngine physicsEngine,
                            GameWebSocketHandler webSocketHandler,
                            EventBus eventBus,
                            ObjectMapper objectMapper,
                            GameLogRepository gameLogRepository,
                            LobbyService lobbyService) {
        this.roomManager = roomManager;
        this.physicsEngine = physicsEngine;
        this.webSocketHandler = webSocketHandler;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
        this.gameLogRepository = gameLogRepository;
        this.lobbyService = lobbyService;
    }
    
    /**
     * 主游戏循环 - 每40ms执行一次 (25 FPS)
     */
    @Scheduled(fixedRate = 40)
    public void tick() {
        roomManager.getAllActiveGames().forEach((roomId, world) -> {
            try {
                processGameWorld(world);
            } catch (Exception e) {
                logger.error("Error processing game world {}", roomId, e);
            }
        });
    }
    
    /**
     * 处理单个游戏世界的一帧
     */
    private void processGameWorld(GameWorld world) {
        long now = System.currentTimeMillis();
        
        switch (world.getPhase()) {
            case COUNTDOWN:
                // 倒计时阶段
                if (now >= world.getGameStartTime()) {
                    world.setPhase(GameWorld.GamePhase.IN_PROGRESS);
                    logger.info("Game {} started!", world.getRoomId());
                }
                // 即使在倒计时也广播状态（让客户端显示倒计时）
                broadcastGameState(world);
                break;
                
            case IN_PROGRESS:
                // 游戏进行中
                // 1. 应用输入（已在WebSocket中处理，这里只是更新位置）
                
                // 2. 更新物理（移动所有实体）
                physicsEngine.updatePositions(world, DELTA_TIME);
                
                // 3. 碰撞检测（会发布事件）
                physicsEngine.detectCollisions(world);
                
                // 4. 检查胜利条件
                if (checkWinCondition(world)) {
                    finishGame(world);
                }
                
                // 5. 广播游戏状态快照
                broadcastGameState(world);
                
                // 6. 增加帧号
                world.incrementFrame();
                break;
                
            case FINISHED:
                // 游戏结束，不再处理（但保留world用于结算）
                break;
        }
    }
    
    /**
     * 检查胜利条件
     */
    private boolean checkWinCondition(GameWorld world) {
        String winMode = world.getWinMode();
        
        // 分数模式
        if (winMode.startsWith("SCORE_")) {
            int targetScore = Integer.parseInt(winMode.substring(6));
            return world.getPlayers().values().stream()
                .anyMatch(p -> p.score >= targetScore);
        }
        
        // 时间模式
        if (winMode.startsWith("TIME_")) {
            String timeStr = winMode.substring(5);
            int minutes = Integer.parseInt(timeStr.substring(0, timeStr.length() - 1));
            long elapsed = System.currentTimeMillis() - world.getGameStartTime();
            return elapsed >= minutes * 60 * 1000L;
        }
        
        // 存活模式（只剩一人）
        long aliveCount = world.getPlayers().values().stream()
            .filter(p -> p.alive).count();
        return aliveCount <= 1;
    }
    
    /**
     * 结束游戏
     */
    private void finishGame(GameWorld world) {
        world.setPhase(GameWorld.GamePhase.FINISHED);
        
        long now = System.currentTimeMillis();
        long elapsedMs = now - world.getGameStartTime();
        
        // 收集最终分数
        Map<String, Integer> finalScores = world.getPlayers().values().stream()
            .collect(Collectors.toMap(p -> p.username, p -> p.score));
        
        // 找出赢家
        String winner = world.getPlayers().values().stream()
            .max(Comparator.comparingInt(p -> p.score))
            .map(p -> p.username)
            .orElse("无");
        
        // 发布游戏结束事件
        eventBus.publish(new GameEndedEvent(world.getRoomId(), finalScores, winner));
        
        logger.info("Game {} finished! Winner: {}, Scores: {}", 
            world.getRoomId(), winner, finalScores);
        
        // 广播最终状态
        broadcastGameState(world);
        
        // ★ 保存游戏结果到数据库
        try {
            // 构造详细的结果JSON
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (PlayerEntity player : world.getPlayers().values()) {
                Map<String, Object> playerResult = new LinkedHashMap<>();
                playerResult.put("username", player.username);
                playerResult.put("score", player.score);
                playerResult.put("hp", player.hp);
                playerResult.put("alive", player.alive);
                playerResult.put("elapsedMillis", elapsedMs);
                playerResult.put("finished", true);
                resultList.add(playerResult);
            }
            
            // 添加游戏元数据
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("winner", winner);
            metadata.put("mapName", world.getMapName());
            metadata.put("winMode", world.getWinMode());
            metadata.put("maxPlayers", world.getMaxPlayers());
            metadata.put("architecture", "A"); // 标记使用的架构
            metadata.put("totalFrames", world.getCurrentFrameNumber());
            
            Map<String, Object> fullResult = new LinkedHashMap<>();
            fullResult.put("players", resultList);
            fullResult.put("metadata", metadata);
            
            String resultJson = objectMapper.writeValueAsString(fullResult);
            
            // 保存到GameLog
            GameLog gameLog = new GameLog();
            gameLog.setRoomId(world.getRoomId());
            gameLog.setStartedAt(world.getGameStartTime());
            gameLog.setEndedAt(now);
            gameLog.setResultJson(resultJson);
            
            gameLogRepository.insert(gameLog);
            logger.info("Game log saved for room {}", world.getRoomId());
            
        } catch (Exception e) {
            logger.error("Failed to save game log for room {}", world.getRoomId(), e);
        }
        
        // ★ 通知LobbyService重置房间状态
        lobbyService.resetRoomAfterGame(world.getRoomId());
        
        // ★ 5秒后清理GameWorld（给客户端时间显示结果）
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                roomManager.removeGameRoom(world.getRoomId());
                logger.info("GameWorld removed for room {}", world.getRoomId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * 广播游戏状态到所有玩家
     * Architecture A: 服务器推送权威状态
     */
    private void broadcastGameState(GameWorld world) {
        try {
            Map<String, Object> stateSnapshot = new HashMap<>();
            stateSnapshot.put("type", "GAME_STATE");
            stateSnapshot.put("roomId", world.getRoomId());
            stateSnapshot.put("frame", world.getCurrentFrameNumber());
            stateSnapshot.put("phase", world.getPhase().name());
            
            // 倒计时剩余时间
            if (world.getPhase() == GameWorld.GamePhase.COUNTDOWN) {
                long remainingMs = world.getGameStartTime() - System.currentTimeMillis();
                stateSnapshot.put("countdownMs", Math.max(0, remainingMs));
            }
            
            // 游戏已运行时间
            if (world.getPhase() == GameWorld.GamePhase.IN_PROGRESS) {
                long elapsedMs = System.currentTimeMillis() - world.getGameStartTime();
                stateSnapshot.put("elapsedMs", elapsedMs);
            }
            
            // 玩家状态
            List<Map<String, Object>> playersData = new ArrayList<>();
            world.getPlayers().forEach((username, player) -> {
                Map<String, Object> p = new HashMap<>();
                p.put("username", username);
                p.put("x", player.x);
                p.put("y", player.y);
                p.put("hp", player.hp);
                p.put("score", player.score);
                p.put("alive", player.alive);
                playersData.add(p);
            });
            stateSnapshot.put("players", playersData);
            
            // 子弹状态
            List<Map<String, Object>> bulletsData = new ArrayList<>();
            world.getBullets().forEach((id, bullet) -> {
                Map<String, Object> b = new HashMap<>();
                b.put("id", id);
                b.put("owner", bullet.owner);
                b.put("x", bullet.x);
                b.put("y", bullet.y);
                bulletsData.add(b);
            });
            stateSnapshot.put("bullets", bulletsData);
            
            String json = objectMapper.writeValueAsString(stateSnapshot);
            webSocketHandler.broadcastToRoom(world.getRoomId(), json);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast game state", e);
        }
    }
}

