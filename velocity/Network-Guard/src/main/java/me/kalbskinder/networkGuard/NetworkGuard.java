package me.kalbskinder.networkGuard;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import me.kalbskinder.networkGuard.listeners.PlayerJoinListener;
import org.slf4j.Logger;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;

@Plugin(id = "network-guard", name = "Network-Guard", version = BuildConstants.VERSION)
public class NetworkGuard {

    // Getters for important values
    @Getter
    private static Logger logger;

    @Getter
    private final ProxyServer proxy;

    @Getter
    private static YamlDocument config;

    @Getter
    private static DatabaseManager databaseManager;

    // Register event listeners
    private static void registerListeners(ProxyServer server, NetworkGuard ts) {
        server.getEventManager().register(ts, new PlayerJoinListener());
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Save default config.yml
        try {
            File configFile = new File(dataDirectory.toFile(), "config.yml");
            config = YamlDocument.create(
                    configFile,
                    Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.DEFAULT,
                    DumperSettings.DEFAULT,
                    UpdaterSettings.DEFAULT
            );
            config.update();
            config.save();
            logger.info("Loaded or created config.yml.");
        } catch (IOException e) {
            logger.error("Failed to load or create config.yml", e);
            return;
        }

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
            logger.error("Failed to connect to MySQL database", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Call to register events
        registerListeners(proxy, this);
    }

    @Inject
    public NetworkGuard(@DataDirectory Path dataDirectory, ProxyServer proxy, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.proxy = proxy;
        NetworkGuard.logger = logger;
    }

    private final Path dataDirectory;
}
