package me.kalbskinder.networkGuard.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.kalbskinder.networkGuard.NetworkGuard;
import me.kalbskinder.networkGuard.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainCommand {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private static final DatabaseManager db = NetworkGuard.getDatabaseManager();

    private final LiteralArgumentBuilder<CommandSourceStack> baseCommand = Commands.literal("network-guard")
            .requires(source -> source.getExecutor() != null && source.getExecutor().hasPermission("nwguard.help"))
            .executes(ctx -> {
                sendHelpMessage(ctx.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendHelpMessage(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    })
            )
            .then(Commands.literal("show-permissions")
                    .requires(source -> source.getExecutor() != null && source.getExecutor().hasPermission("nwguard.showpermissions"))
                    .executes(ctx -> {
                        showPermissions(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    })
            )
            .then(Commands.literal("set-permission")
                    .requires(source -> source.getExecutor() != null && source.getExecutor().hasPermission("nwguard.setpermission"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("permission-level", IntegerArgumentType.integer(0, 100))
                                    .executes(ctx -> {
                                        String playerName = StringArgumentType.getString(ctx, "player");
                                        int permissionLevel = IntegerArgumentType.getInteger(ctx, "permission-level");
                                        setPermission(ctx.getSource().getSender(), playerName, permissionLevel);
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                    )
            );

    private void sendHelpMessage(CommandSender sender) {
        List<Component> helpMessages = new ArrayList<>();
        helpMessages.add(mm.deserialize("<gold>=== NetworkGuard Commands ==="));
        if (sender.hasPermission("nwguard.help")) {
            helpMessages.add(mm.deserialize("<yellow>/network-guard [help] <gray>- Shows this help message"));
        }
        if (sender.hasPermission("nwguard.showpermissions")) {
            helpMessages.add(mm.deserialize("<yellow>/network-guard show-permissions <gray>- Lists all staff permission levels"));
        }
        if (sender.hasPermission("nwguard.setpermission")) {
            helpMessages.add(mm.deserialize("<yellow>/network-guard set-permission <player> <level> <gray>- Sets a player's permission level (0-100)"));
        }
        helpMessages.forEach(sender::sendMessage);
    }

    private void showPermissions(CommandSender sender) {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT `uuid`, `permission_level` FROM staff_levels ORDER BY `permission_level` DESC;")) {
            List<Component> messages = new ArrayList<>();
            messages.add(mm.deserialize("<gold>=== Staff Permission Levels ==="));

            try (ResultSet rs = stmt.executeQuery()) {
                boolean hasEntries = false;
                while (rs.next()) {
                    hasEntries = true;
                    String uuid = rs.getString("uuid");
                    int level = rs.getInt("permission_level");
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                    String playerName = player.getName() != null ? player.getName() : "Unknown (" + uuid + ")";
                    messages.add(mm.deserialize("<yellow>" + playerName + ": <green>Level " + level));
                }
                if (!hasEntries) {
                    messages.add(mm.deserialize("<gray>No staff permissions set."));
                }
            }
            messages.forEach(sender::sendMessage);
        } catch (SQLException e) {
            sender.sendMessage(mm.deserialize("<red>Error retrieving permissions: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void setPermission(CommandSender sender, String playerName, int permissionLevel) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(mm.deserialize("<red>Player '" + playerName + "' has never played on this server!"));
            return;
        }

        UUID uuid = target.getUniqueId();
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "REPLACE INTO staff_levels (`uuid`, `permission_level`) VALUES (?, ?);")) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, permissionLevel);
            stmt.executeUpdate();
            sender.sendMessage(mm.deserialize("<green>Permission level for <yellow>" + playerName + "<green> set to <yellow>" + permissionLevel));
        } catch (SQLException e) {
            sender.sendMessage(mm.deserialize("<red>Error setting permission: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return baseCommand;
    }
}
