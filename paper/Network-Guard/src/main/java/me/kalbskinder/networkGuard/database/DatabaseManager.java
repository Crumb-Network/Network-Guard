package me.kalbskinder.networkGuard.database;

import me.kalbskinder.networkGuard.NetworkGuard;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

public class DatabaseManager {
    private final Connection connection;
    private final Logger logger = NetworkGuard.getInstance().getLogger();
    private static final DatabaseManager db = NetworkGuard.getDatabaseManager();

    public DatabaseManager(String host, int port, String database, String username, String password, boolean useSSL, Logger logger) throws SQLException, ClassNotFoundException {
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&autoReconnect=true", host, port, database, useSSL);
        Class.forName("com.mysql.cj.jdbc.Driver");
        this.connection = DriverManager.getConnection(url, username, password);
        logger.info("Successfully connected to MySQL database.");
    }

    public Connection getConnection() {
        return connection;
    }

    // Common used data






}
