package com.projectgroup5.gamedemo.event;

import com.projectgroup5.gamedemo.game.PlayerInput;

/**
 * 玩家输入事件
 */
public class InputReceivedEvent implements GameEvent {
    private final long roomId;
    private final String username;
    private final PlayerInput input;
    private final long timestamp;
    
    public InputReceivedEvent(long roomId, String username, PlayerInput input) {
        this.roomId = roomId;
        this.username = username;
        this.input = input;
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
    
    public PlayerInput getInput() {
        return input;
    }
}

