package com.projectgroup5.gamedemo.event;

/**
 * 分数更新事件
 */
public class ScoreUpdatedEvent implements GameEvent {
    private final long roomId;
    private final String username;
    private final int delta;
    private final int newTotal;
    private final long timestamp;
    
    public ScoreUpdatedEvent(long roomId, String username, int delta, int newTotal) {
        this.roomId = roomId;
        this.username = username;
        this.delta = delta;
        this.newTotal = newTotal;
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
    
    public String getUsername() {
        return username;
    }
    
    public int getDelta() {
        return delta;
    }
    
    public int getNewTotal() {
        return newTotal;
    }
}

