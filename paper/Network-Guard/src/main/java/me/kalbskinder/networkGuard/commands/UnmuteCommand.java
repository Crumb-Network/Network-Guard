package me.kalbskinder.networkGuard.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.kalbskinder.networkGuard.NetworkGuard;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UnmuteCommand {
    private static final DatabaseManager db = NetworkGuard.getDatabaseManager();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("unmute")
                .requires(source -> source.getExecutor() != null && source.getExecutor().hasPermission("nwguard.unmute"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                            return builder.buildFuture();
                        })
                        .executes(this::executeUnmute)
                );
    }

    private int executeUnmute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender source = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        if (playerName == null) {
            source.sendMessage("/unban <player>");
            return 0;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            source.sendMessage(mm.deserialize("<red>Player '" + playerName + "' has never played on this server!"));
            return 0;
        }

        UUID targetUUID = target.getUniqueId();

        // Check if the player is muted
        if (!isMuted(targetUUID)) {
            source.sendMessage(mm.deserialize("<red>Player '" + playerName + "' is not muted!"));
            return 0;
        }

        // Permission level check (skip for Console)
        if (source instanceof Player playerSource) {
            int sourceLevel = getPermissionLevel(playerSource.getUniqueId());
            int targetLevel = getPermissionLevel(targetUUID);
            if (sourceLevel <= targetLevel) {
                source.sendMessage(mm.deserialize("<red>You cannot unmute a player with equal or higher permission level!"));
                return 0;
            }
        }

        // Unmute the player
        unmutePlayer(targetUUID, playerName);
        source.sendMessage(mm.deserialize("<green>Player <yellow>" + playerName + "<green> has been unmuted."));

        return Command.SINGLE_SUCCESS;
    }

    private void unmutePlayer(UUID uuid, String name) {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "DELETE FROM mutes WHERE `uuid` = ?;")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            // Remove from mute cache
            NetworkGuard.getMuteCache().remove(uuid);
        } catch (SQLException e) {
            System.err.println("Failed to unmute player " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isMuted(UUID uuid) {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT `uuid` FROM mutes WHERE `uuid` = ?;")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Failed to check mute status for UUID " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
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