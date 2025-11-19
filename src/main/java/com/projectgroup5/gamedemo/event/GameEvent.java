package com.projectgroup5.gamedemo.event;

/**
 * 游戏事件基类 - 所有游戏事件的标记接口
 * 用于事件总线的类型安全
 */
public interface GameEvent {
    /**
     * 事件发生的时间戳
     */
    long getTimestamp();
    
    /**
     * 事件关联的房间ID
     */
    long getRoomId();
}

