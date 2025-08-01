package me.kalbskinder.networkGuard;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.kalbskinder.networkGuard.commands.*;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import me.kalbskinder.networkGuard.listeners.ChatListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class NetworkGuard extends JavaPlugin {
    private static final Logger logger = Logger.getLogger("Network-Guard");
    private static final Map<UUID, Long> muteCache = new ConcurrentHashMap<>();
    private static NetworkGuard instance;
    FileConfiguration config = this.getConfig();
    private static DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        saveDefaultConfig();

        // Try connecting to the mysql database
        try {
            String host = config.getString("mysql.host");
            int port = config.getInt("mysql.port");
            String database = config.getString("mysql.database");
            String username = config.getString("mysql.username");
            String password = config.getString("mysql.password");
            boolean useSSL = config.getBoolean("mysql.useSSL");

            databaseManager = new DatabaseManager(host, port, database, username, password, useSSL, logger);
        } catch (SQLException e) {
            logger.severe("Failed to connect to MySQL database");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        getServer().getPluginManager().registerEvents(new ChatListener(muteCache), this);

        // Initialize mute cache
        initializeMuteCache();

        // Schedule cache cleanup every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanMuteCache();
            }
        }.runTaskTimerAsynchronously(this, 0L, 5 * 60 * 20L); // 5 minutes in ticks

        LifecycleEventManager<?> lifecycle = this.getLifecycleManager();
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            BanCommand banCommand = new BanCommand();
            commands.register(banCommand.getCommand().build(), "Ban a player from the server");

            MainCommand mainCommand = new MainCommand();
            commands.register(mainCommand.getCommand().build(), "Main plugin command");

            UnbanCommand unbanCommand = new UnbanCommand();
            commands.register(unbanCommand.getCommand().build(), "Unban a player from the server");

            MuteCommand muteCommand = new MuteCommand();
            commands.register(muteCommand.getCommand().build(), "Mute a player on the server");

            UnmuteCommand unmuteCommand = new UnmuteCommand();
            commands.register(unmuteCommand.getCommand().build(), "Unmute a player on the server");
        });
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    // ===============
    // Mute cache
    // ===============
    private void initializeMuteCache() {
        try (var stmt = databaseManager.getConnection().prepareStatement(
                "SELECT uuid, expires_at FROM mutes;")) {
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long expiresAt = rs.getLong("expires_at");
                    if (expiresAt == 0 || expiresAt > System.currentTimeMillis()) {
                        muteCache.put(uuid, expiresAt);
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize mute cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanMuteCache() {
        muteCache.entrySet().removeIf(entry -> {
            Long expiresAt = entry.getValue();
            return expiresAt != 0 && expiresAt <= System.currentTimeMillis();
        });
    }

    public static NetworkGuard getInstance() {
        return instance;
    }

    public static Map<UUID, Long> getMuteCache() {
        return muteCache;
    }
}
