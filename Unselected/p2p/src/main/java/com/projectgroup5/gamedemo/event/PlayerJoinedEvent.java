package com.projectgroup5.gamedemo.event;

/**
 * 玩家加入游戏事件
 */
public class PlayerJoinedEvent implements GameEvent {
    private final long roomId;
    private final String username;
    private final long timestamp;
    
    public PlayerJoinedEvent(long roomId, String username) {
        this.roomId = roomId;
        this.username = username;
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
}

