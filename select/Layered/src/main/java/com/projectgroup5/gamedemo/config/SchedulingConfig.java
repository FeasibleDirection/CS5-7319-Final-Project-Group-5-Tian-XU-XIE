package com.projectgroup5.gamedemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用定时任务（用于游戏Tick循环）
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

