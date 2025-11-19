package com.projectgroup5.gamedemo.dao;

import com.projectgroup5.gamedemo.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setUsername(rs.getString("username"));
            u.setEmail(rs.getString("email"));
            u.setPasswordHash(rs.getString("password_hash"));
            u.setCreatedAt(rs.getLong("created_at"));
            long lastLogin = rs.getLong("last_login_at");
            if (!rs.wasNull()) {
                u.setLastLoginAt(lastLogin);
            }
            return u;
        }
    }

    public Optional<User> findByUsernameAndPassword(String username, String password) {
        String sql = "SELECT id, username, email, password_hash, created_at, last_login_at " +
                "FROM main.users WHERE username = ? AND password_hash = ?";
        return jdbcTemplate.query(sql, new UserRowMapper(), username, password).stream().findFirst();
    }

    public void updateLastLogin(long userId, long timestamp) {
        String sql = "UPDATE main.users SET last_login_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, timestamp, userId);
    }
}
