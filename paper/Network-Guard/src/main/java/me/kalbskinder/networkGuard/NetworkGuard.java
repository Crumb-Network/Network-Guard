package me.kalbskinder.networkGuard;

import com.sun.tools.javac.Main;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.kalbskinder.networkGuard.commands.BanCommand;
import me.kalbskinder.networkGuard.commands.MainCommand;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.units.qual.N;

import java.sql.SQLException;
import java.util.logging.Logger;

public final class NetworkGuard extends JavaPlugin {
    private static final Logger logger = Logger.getLogger("Network-Guard");
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

        LifecycleEventManager<?> lifecycle = this.getLifecycleManager();
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            BanCommand banCommand = new BanCommand();
            commands.register(banCommand.getCommand().build(), "Ban a player from the server");

            MainCommand mainCommand = new MainCommand();
            commands.register(mainCommand.getCommand().build(), "Main plugin command");
        });
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static NetworkGuard getInstance() {
        return instance;
    }
}
