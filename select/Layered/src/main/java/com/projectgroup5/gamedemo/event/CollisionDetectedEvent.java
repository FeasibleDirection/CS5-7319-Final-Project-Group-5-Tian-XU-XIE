package com.projectgroup5.gamedemo.event;

/**
 * 碰撞检测事件
 */
public class CollisionDetectedEvent implements GameEvent {
    private final long roomId;
    private final String entity1;
    private final String entity2;
    private final CollisionType type;
    private final long timestamp;
    
    public enum CollisionType {
        BULLET_HIT_PLAYER,
        PLAYER_COLLISION
    }
    
    public CollisionDetectedEvent(long roomId, String entity1, String entity2, CollisionType type) {
        this.roomId = roomId;
        this.entity1 = entity1;
        this.entity2 = entity2;
        this.type = type;
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
    
    public String getEntity1() {
        return entity1;
    }
    
    public String getEntity2() {
        return entity2;
    }
    
    public CollisionType getType() {
        return type;
    }
}

