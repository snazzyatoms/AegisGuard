package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.notify.NotificationMode;
import com.aegisguard.notify.PlayerNotificationSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class NotifyCommand implements CommandExecutor {

    private final AegisGuard plugin;

    public NotifyCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        PlayerNotificationSettings settings = plugin.notifications().get(player);

        // --- Legacy behavior: /aegis notify ---
        if (args.length == 0) {
            settings.setGreetings(!settings.greetingsEnabled());
            plugin.notifications().save(player, settings);

            plugin.msg().send(
                    player,
                    settings.greetingsEnabled()
                            ? "notify_greetings_enabled"
                            : "notify_greetings_disabled"
            );
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "greetings":
                settings.setGreetings(!settings.greetingsEnabled());
                plugin.notifications().save(player, settings);
                plugin.msg().send(
                        player,
                        settings.greetingsEnabled()
                                ? "notify_greetings_enabled"
                                : "notify_greetings_disabled"
                );
                return true;

            case "admin":
                settings.setAdminUpdates(!settings.adminUpdatesEnabled());
                plugin.notifications().save(player, settings);
                plugin.msg().send(
                        player,
                        settings.adminUpdatesEnabled()
                                ? "notify_admin_enabled"
                                : "notify_admin_disabled"
                );
                return true;

            case "mode":
                if (args.length < 2) {
                    plugin.msg().send(player, "notify_mode_usage");
                    return true;
                }

                NotificationMode mode = NotificationMode.fromString(args[1]);
                settings.setMode(mode);
                plugin.notifications().save(player, settings);

                plugin.msg().send(player, "notify_mode_set", mode.name());
                return true;

            default:
                plugin.msg().send(player, "notify_usage");
                return true;
        }
    }
}
