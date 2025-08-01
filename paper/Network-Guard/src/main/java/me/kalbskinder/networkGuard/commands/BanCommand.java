package me.kalbskinder.networkGuard.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.kalbskinder.networkGuard.NetworkGuard;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import me.kalbskinder.networkGuard.util.ConfigManager;
import me.kalbskinder.networkGuard.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BanCommand {
    private static final DatabaseManager db = NetworkGuard.getDatabaseManager();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<String> DURATIONS = Arrays.asList("1h", "24h", "30d", "365d");
    private final List<String> REASONS = Arrays.asList("Hacking", "Griefing", "Toxicity", "Spamming", "Advertising");

    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("ban")
                .requires(source -> source.getExecutor() != null && source.getExecutor().hasPermission("nwguard.ban"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    DURATIONS.forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            REASONS.forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeBan(ctx, true, true))
                                )
                                .executes(ctx -> executeBan(ctx, true, false))
                        )
                        .executes(ctx -> executeBan(ctx, false, false))
                )
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(mm.deserialize("<yellow>Usage: /ban <player> [duration] [reason]"));
                    return 0;
                });
    }

    private int executeBan(CommandContext<CommandSourceStack> ctx, boolean hasDuration, boolean hasReason) {
        CommandSender source = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");

        String duration = hasDuration ? StringArgumentType.getString(ctx, "duration") : "30d";
        String reason = hasReason ? StringArgumentType.getString(ctx, "reason") : "No reason specified";

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer == null || (offlinePlayer.getName() == null && !offlinePlayer.hasPlayedBefore())) {
            source.sendMessage(mm.deserialize("<red>Player not found or never joined before!"));
            return 0;
        }

        UUID targetUUID = offlinePlayer.getUniqueId();

        if (source instanceof Player playerSource) {
            int sourceLevel = getPermissionLevel(playerSource.getUniqueId());
            int targetLevel = getPermissionLevel(targetUUID); // Auch für Offline-Spieler
            if (sourceLevel <= targetLevel) {
                source.sendMessage(mm.deserialize("<red>You cannot ban a player with equal or higher permission level!"));
                return 0;
            }
        }

        String actorName = source instanceof Player p ? p.getName() : "Console";
        banPlayer(targetUUID, playerName, reason, actorName, TimeUtil.parseDuration(duration));

        Player onlineTarget = Bukkit.getPlayer(targetUUID);
        if (onlineTarget != null) {
            Component banMessage = ConfigManager.banMessage(playerName, reason, actorName, duration);
            onlineTarget.kick(banMessage);
        }

        source.sendMessage(mm.deserialize("<green>Successfully banned " + playerName + "."));
        return Command.SINGLE_SUCCESS;
    }



    private static void banPlayer(UUID uuid, String name, String reason, String bannedBy, long durationMillis) {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "REPLACE INTO bans (uuid, name, reason, banned_by, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?);")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, reason);
            stmt.setString(4, bannedBy);
            stmt.setLong(5, System.currentTimeMillis() + durationMillis);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to ban player " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int getPermissionLevel(UUID uuid) {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT `permission_level` FROM staff_levels WHERE `uuid` = ?;")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("permission_level");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get permission level for UUID " + uuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0; // Default level for players not in staff_levels
    }
}