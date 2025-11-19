package com.projectgroup5.gamedemo.dao;

import com.projectgroup5.gamedemo.entity.GameLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GameLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public GameLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(GameLog log) {
        String sql = "INSERT INTO game_logs (room_id, started_at, ended_at, result_json) " +
                     "VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                log.getRoomId(),
                log.getStartedAt(),
                log.getEndedAt(),
                log.getResultJson()
        );
    }
}
