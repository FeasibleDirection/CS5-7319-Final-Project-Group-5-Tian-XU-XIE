package com.projectgroup5.gamedemo.game;

import java.util.UUID;

/**
 * 石头实体（障碍物）
 * 从上往下掉落，玩家需要躲避或射击摧毁
 */
public class AsteroidEntity {
    public final String id;
    public double x;
    public double y;
    public double velocityY;  // 下落速度（正数向下）
    public double radius;
    public int hp;            // 血量（大石头需要2发子弹）
    public final boolean isBig;
    
    public AsteroidEntity(double x, double y, boolean isBig) {
        this.id = UUID.randomUUID().toString();
        this.x = x;
        this.y = y;
        this.isBig = isBig;
        
        // 大石头参数
        if (isBig) {
            this.radius = 26;
            this.hp = 2;
            this.velocityY = 80 + Math.random() * 40;  // 80-120 px/s
        } else {
            // 小石头参数
            this.radius = 16;
            this.hp = 1;
            this.velocityY = 100 + Math.random() * 60; // 100-160 px/s
        }
    }
    
    /**
     * 判断是否已经飞出屏幕下方
     */
    public boolean isOffScreen(double screenHeight) {
        return y - radius > screenHeight + 50;
    }
}


