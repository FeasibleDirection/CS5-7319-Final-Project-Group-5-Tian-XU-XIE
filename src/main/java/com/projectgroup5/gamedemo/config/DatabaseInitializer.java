package com.projectgroup5.gamedemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Database Initializer
 * Automatically creates database file and initializes tables/data if they don't exist
 */
@Component
@Order(1) // Run early in the startup process
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing database...");

        // Extract file path from JDBC URL (jdbc:sqlite:path)
        String dbPath = datasourceUrl.replace("jdbc:sqlite:", "");
        Path dbFilePath = Paths.get(dbPath);

        // Create parent directories if they don't exist
        File dbFile = dbFilePath.toFile();
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
            logger.info("Created database directory: {}", dbFile.getParentFile().getAbsolutePath());
        }

        // Check if database file exists
        boolean dbExists = dbFile.exists();
        logger.info("Database file exists: {} at {}", dbExists, dbFile.getAbsolutePath());

        // Create database connection to ensure file is created
        if (!dbExists) {
            try (Connection conn = DriverManager.getConnection(datasourceUrl);
                 Statement stmt = conn.createStatement()) {
                // Create database file by executing a simple query
                stmt.execute("SELECT 1");
                logger.info("Database file created: {}", dbFile.getAbsolutePath());
            }
        }

        // Initialize tables
        initializeTables();

        // Initialize data
        initializeData();

        logger.info("Database initialization completed");
    }

    private void initializeTables() {
        logger.info("Creating tables if they don't exist...");

        // Create users table
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT    NOT NULL UNIQUE,
                email         TEXT,
                password_hash TEXT    NOT NULL,
                created_at    INTEGER NOT NULL,
                last_login_at INTEGER
            )
            """;

        // Create game_logs table
        String createGameLogsTable = """
            CREATE TABLE IF NOT EXISTS game_logs (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                room_id     INTEGER NOT NULL,
                started_at  INTEGER NOT NULL,
                ended_at    INTEGER NOT NULL,
                result_json TEXT    NOT NULL
            )
            """;

        try {
            jdbcTemplate.execute(createUsersTable);
            logger.info("✓ users table ready");

            jdbcTemplate.execute(createGameLogsTable);
            logger.info("✓ game_logs table ready");
        } catch (Exception e) {
            logger.error("Error creating tables", e);
            throw new RuntimeException("Failed to initialize database tables", e);
        }
    }

    private void initializeData() {
        logger.info("Initializing data...");

        try {
            // Check if users table has data
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

            if (userCount == null || userCount == 0) {
                logger.info("Inserting initial user data...");

                // Insert users with specific IDs (using INSERT OR IGNORE to avoid conflicts)
                String insertUsers = """
                    INSERT OR IGNORE INTO users (id, username, email, password_hash, created_at, last_login_at) 
                    VALUES 
                        (0, 'zhaoyuantian', 'zhaoyuantian@gmail.com', '123456', 1763467296, 1765323380),
                        (1, 'xushikuan', 'xushikuan@gmail.com', '123456', 1763467297, 1764577535),
                        (2, 'xiejing', 'xiejing@gmail.com', '123456', 1763467297, 1763603275)
                    """;

                jdbcTemplate.execute(insertUsers);

                // Verify insertion
                Integer newCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
                logger.info("✓ Inserted users. Total users: {}", newCount);
            } else {
                logger.info("Users table already has {} records, skipping user data insertion", userCount);
            }

            // Check if game_logs table has data
            Integer logCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_logs", Integer.class);

            if (logCount == null || logCount == 0) {
                logger.info("Inserting initial game log data...");

                // Insert game logs
                String insertGameLogs = """
                    INSERT INTO game_logs (id, room_id, started_at, ended_at, result_json) 
                    VALUES 
                        (33, 1, 1763589702982, 1763589709695, '{"players":[{"username":"zhaoyuantian","score":15,"hp":0,"alive":false,"elapsedMillis":6713}],"metadata":{"winner":"zhaoyuantian","mapName":"Nebula-01","winMode":"SCORE_50","maxPlayers":2,"architecture":"A","totalFrames":166}}'),
                        (34, 1, 1763589993703, 1763590012258, '{"players":[{"username":"xushikuan","score":20,"hp":0,"alive":false,"elapsedMillis":18555},{"username":"zhaoyuantian","score":10,"hp":0,"alive":false,"elapsedMillis":18555}],"metadata":{"winner":"xushikuan","mapName":"Nebula-01","winMode":"SCORE_50","maxPlayers":2,"architecture":"A","totalFrames":462}}'),
                        (35, 1, 1763598283172, 1763598311598, '{"players":[{"username":"xushikuan","score":25,"hp":0,"alive":false,"elapsedMillis":28426},{"username":"zhaoyuantian","score":0,"hp":0,"alive":false,"elapsedMillis":28426}],"metadata":{"winner":"xushikuan","mapName":"Nebula-01","winMode":"SCORE_50","maxPlayers":2,"architecture":"A","totalFrames":709}}')
                    """;

                jdbcTemplate.execute(insertGameLogs);
                logger.info("✓ Inserted {} game logs", 3);
            } else {
                logger.info("Game_logs table already has {} records, skipping game log data insertion", logCount);
            }

        } catch (Exception e) {
            logger.error("Error initializing data", e);
            // Don't throw exception - data might already exist
            logger.warn("Continuing despite data initialization error (data might already exist)");
        }
    }
}

