package me.kalbskinder.networkGuard.listeners;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import me.kalbskinder.networkGuard.NetworkGuard;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import me.kalbskinder.networkGuard.util.ConfigManager;
import me.kalbskinder.networkGuard.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerJoinListener {
    private final DatabaseManager db = NetworkGuard.getDatabaseManager();
    private final Logger logger = NetworkGuard.getLogger();
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT `reason`, `expires_at`, `banned_by`, `name` FROM bans WHERE `uuid` = ?;")) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return;
                }

                long expiresAt = rs.getLong("expires_at");
                String reason = rs.getString("reason");
                String bannedBy = rs.getString("banned_by");
                String name = rs.getString("name");

                if (expiresAt == 0 || System.currentTimeMillis() < expiresAt) {
                    String duration = expiresAt == 0 ? "permanent" : TimeUtil.formatDuration(expiresAt - System.currentTimeMillis());
                    event.setResult(ResultedEvent.ComponentResult.denied(
                            ConfigManager.banMessage(name, reason, bannedBy, duration)
                    ));
                } else {
                    try (PreparedStatement deleteStmt = db.getConnection().prepareStatement(
                            "DELETE FROM bans WHERE `uuid` = ?;")) {
                        deleteStmt.setString(1, uuid.toString());
                        deleteStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to handle player {} (UUID: {}) while joining the proxy: {}", playerName, uuid, e.getMessage(), e);
            event.setResult(ResultedEvent.ComponentResult.denied(
                    mm.deserialize("<red>[Network-Guard] Failed to authenticate user.")
            ));
        }
    }
}