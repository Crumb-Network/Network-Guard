package me.kalbskinder.networkGuard.database;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Connection connection;

    public DatabaseManager(String host, int port, String database, String username, String password, boolean useSSL, Logger logger) throws SQLException, ClassNotFoundException {
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&autoReconnect=true", host, port, database, useSSL);
        Class.forName("com.mysql.cj.jdbc.Driver");
        this.connection = DriverManager.getConnection(url, username, password);
        logger.info("Successfully connected to MySQL database.");
        createTables(logger);
    }

    private void createTables(Logger logger) {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bans (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(50),
                    reason TEXT,
                    banned_by VARCHAR(50),
                    expires_at BIGINT,
                    created_at BIGINT
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mutes (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(50),
                    reason TEXT,
                    muted_by VARCHAR(50),
                    expires_at BIGINT,
                    created_at BIGINT
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS warns (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36),
                    name VARCHAR(50),
                    reason TEXT,
                    warned_by VARCHAR(50),
                    created_at BIGINT
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS staff_levels (
                    uuid VARCHAR(36) PRIMARY KEY,
                    permission_level INTEGER NOT NULL
                );
            """);

            logger.info("Database tables created or already exist.");
        } catch (SQLException e) {
            logger.error("Failed to create tables in the database", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
