package com.projectgroup5.gamedemo.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 事件总线 - 实现模块间解耦通信
 * Architecture A 的核心组件：所有模块通过事件总线通信
 */
@Component
public class EventBus {
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);
    
    // 事件类型 -> 处理器列表
    private final Map<Class<? extends GameEvent>, List<Consumer<? extends GameEvent>>> handlers = 
        new ConcurrentHashMap<>();
    
    /**
     * 订阅事件
     * @param eventType 事件类型
     * @param handler 处理器
     */
    public <T extends GameEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
               .add(handler);
        logger.debug("Subscribed to event: {}", eventType.getSimpleName());
    }
    
    /**
     * 发布事件（同步）
     * @param event 事件对象
     */
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void publish(T event) {
        List<Consumer<? extends GameEvent>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (Consumer<? extends GameEvent> handler : eventHandlers) {
                try {
                    ((Consumer<T>) handler).accept(event);
                } catch (Exception e) {
                    logger.error("Error handling event {}: {}", 
                        event.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 取消订阅所有事件
     */
    public void clear() {
        handlers.clear();
    }
}

