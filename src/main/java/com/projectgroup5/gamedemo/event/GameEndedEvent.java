package com.projectgroup5.gamedemo.event;

import java.util.Map;

/**
 * 游戏结束事件
 */
public class GameEndedEvent implements GameEvent {
    private final long roomId;
    private final Map<String, Integer> finalScores;
    private final String winner;
    private final long timestamp;
    
    public GameEndedEvent(long roomId, Map<String, Integer> finalScores, String winner) {
        this.roomId = roomId;
        this.finalScores = finalScores;
        this.winner = winner;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public long getRoomId() {
        return roomId;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    public Map<String, Integer> getFinalScores() {
        return finalScores;
    }
    
    public String getWinner() {
        return winner;
    }
}

