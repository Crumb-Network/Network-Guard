package me.kalbskinder.networkGuard.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.kalbskinder.networkGuard.NetworkGuard;
import me.kalbskinder.networkGuard.util.ConfigManager;
import me.kalbskinder.networkGuard.util.TimeUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {
    private final Map<UUID, Long> muteCache;

    public ChatListener(Map<UUID, Long> muteCache) {
        this.muteCache = muteCache;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Long expiresAt = muteCache.get(uuid);

        // Check cache first
        if (expiresAt != null) {
            if (expiresAt == 0 || System.currentTimeMillis() < expiresAt) {
                // Player is muted
                event.setCancelled(true);
                event.getPlayer().sendMessage(ConfigManager.muteMessage(
                        event.getPlayer().getName(),
                        getMuteReason(uuid),
                        getMutedBy(uuid),
                        expiresAt == 0 ? "permanent" : TimeUtil.formatDuration(expiresAt - System.currentTimeMillis())
                ));
            } else {
                // Mute has expired, remove from cache
                muteCache.remove(uuid);
            }
        }
    }

    private String getMuteReason(UUID uuid) {
        try (var stmt = NetworkGuard.getDatabaseManager().getConnection().prepareStatement(
                "SELECT reason FROM mutes WHERE uuid = ?;")) {
            stmt.setString(1, uuid.toString());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("reason");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "No reason specified";
    }

    private String getMutedBy(UUID uuid) {
        try (var stmt = NetworkGuard.getDatabaseManager().getConnection().prepareStatement(
                "SELECT muted_by FROM mutes WHERE uuid = ?;")) {
            stmt.setString(1, uuid.toString());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("muted_by");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
}