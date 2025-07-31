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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerJoinListener  {
    private final DatabaseManager db = NetworkGuard.getDatabaseManager();
    private final Logger logger = NetworkGuard.getLogger();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<UUID> bannedPlayers = new ArrayList<>(); // Store all banned players in cache

    // Load all currently banned players uuids
    public PlayerJoinListener() {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT uuid FROM bans"
        )) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bannedPlayers.add(UUID.fromString(rs.getString("uuid")));
                }
            }
        } catch (SQLException ex) {
            logger.error("Failed to load banned players");
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        // Check if the player is banned
        if (!bannedPlayers.contains(player.getUniqueId())) return;

        // Get ban information and disconnect player
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT reason, expires_at, banned_by, username FROM bans WHERE uuid = ?;")) {
            stmt.setString(1, player.getUniqueId().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long expiresAt = rs.getLong("expires_at");
                    String reason = rs.getString("reason");
                    String bannedBy = rs.getString("banned_by");
                    String username = rs.getString("username");

                    if (System.currentTimeMillis() < expiresAt) {
                        // Deny player join with custom ban message
                        event.setResult(ResultedEvent.ComponentResult.denied(
                                ConfigManager.banMessage(username, reason, bannedBy, TimeUtil.formatDuration(expiresAt - System.currentTimeMillis()))
                        ));
                    } else {
                        try (PreparedStatement deleteStmt = db.getConnection().prepareStatement(
                                "DELETE FROM bans WHERE uuid = ?;")) {
                            deleteStmt.setString(1, player.getUniqueId().toString());
                            deleteStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to handle {} while joining the proxy. They were kicked.", player.getGameProfile().getName());
            event.setResult(ResultedEvent.ComponentResult.denied(mm.deserialize("<red>[Network-Guard] Failed to authenticate user.")));
        }
    }

}
