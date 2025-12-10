package com.projectgroup5.gamedemo.game;

/**
 * 子弹实体（服务器权威）
 */
public class BulletEntity {
    public String id;
    public String owner;
    public double x, y;
    public double velocityX, velocityY;
    public int damage = 10;
    public long createdTime;
    
    public static final double RADIUS = 4;
    public static final double SPEED = 400; // pixels/second
    
    public BulletEntity(String owner, double x, double y) {
        this.id = owner + "_" + System.currentTimeMillis();
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.velocityX = 0;
        this.velocityY = -SPEED; // 向上发射
        this.createdTime = System.currentTimeMillis();
    }
}

