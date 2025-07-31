package me.kalbskinder.networkGuard.util;

import dev.dejvokep.boostedyaml.YamlDocument;
import me.kalbskinder.networkGuard.NetworkGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class ConfigManager {
    private static final YamlDocument config = NetworkGuard.getConfig();
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static Component banMessage(String username, String reason, String issuer, String duration) {
        List<String> lines = config.getStringList("ban-message");
        String message = String.join("<newline>", lines);
        return mm.deserialize(
                message.replace("%username%", username)
                        .replace("%reason%", reason)
                        .replace("%issuer%", issuer)
                        .replace("%time%", duration)
        );
    }

    public static Component muteMessage(String username, String reason, String issuer, String duration) {
        List<String> lines = config.getStringList("mute-message");
        String message = String.join("<newline>", lines);
        return mm.deserialize(
                message.replace("%username%", username)
                        .replace("%reason%", reason)
                        .replace("%issuer%", issuer)
                        .replace("%time%", duration)
        );
    }
}
