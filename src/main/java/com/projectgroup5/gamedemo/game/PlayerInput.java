package com.projectgroup5.gamedemo.game;

/**
 * 玩家输入（客户端发送的唯一数据）
 * Architecture A: 客户端只发送输入，不发送位置/状态
 */
public class PlayerInput {
    private String username;
    private long clientFrameNumber;
    private boolean moveUp;      // W
    private boolean moveDown;    // S
    private boolean moveLeft;    // A
    private boolean moveRight;   // D
    private boolean fire;        // J or Space
    private long timestamp;
    
    // Getters and Setters
    public String getUsername() { 
        return username; 
    }
    
    public void setUsername(String username) { 
        this.username = username; 
    }
    
    public long getClientFrameNumber() { 
        return clientFrameNumber; 
    }
    
    public void setClientFrameNumber(long clientFrameNumber) { 
        this.clientFrameNumber = clientFrameNumber; 
    }
    
    public boolean isMoveUp() { 
        return moveUp; 
    }
    
    public void setMoveUp(boolean moveUp) { 
        this.moveUp = moveUp; 
    }
    
    public boolean isMoveDown() { 
        return moveDown; 
    }
    
    public void setMoveDown(boolean moveDown) { 
        this.moveDown = moveDown; 
    }
    
    public boolean isMoveLeft() { 
        return moveLeft; 
    }
    
    public void setMoveLeft(boolean moveLeft) { 
        this.moveLeft = moveLeft; 
    }
    
    public boolean isMoveRight() { 
        return moveRight; 
    }
    
    public void setMoveRight(boolean moveRight) { 
        this.moveRight = moveRight; 
    }
    
    public boolean isFire() { 
        return fire; 
    }
    
    public void setFire(boolean fire) { 
        this.fire = fire; 
    }
    
    public long getTimestamp() { 
        return timestamp; 
    }
    
    public void setTimestamp(long timestamp) { 
        this.timestamp = timestamp; 
    }
}

