package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.notify.NotificationMode;
import com.aegisguard.notify.PlayerNotificationSettings;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
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
        // Toggles ONLY plot greetings (enter/leave), NOT admin decisions.
        if (args.length == 0) {
            settings.setGreetingsEnabled(!settings.greetingsEnabled());
            persist(player, settings);

            plugin.msg().send(
                    player,
                    settings.greetingsEnabled()
                            ? "notify_greetings_enabled"
                            : "notify_greetings_disabled"
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            // ------------------------------------------------------------
            // greetings [on|off]
            // ------------------------------------------------------------
            case "greetings": {
                if (args.length >= 2) {
                    String val = args[1].toLowerCase(Locale.ROOT);
                    if (val.equals("on") || val.equals("true") || val.equals("enable") || val.equals("enabled")) {
                        settings.setGreetingsEnabled(true);
                    } else if (val.equals("off") || val.equals("false") || val.equals("disable") || val.equals("disabled")) {
                        settings.setGreetingsEnabled(false);
                    } else {
                        plugin.msg().send(player, "notify_usage");
                        return true;
                    }
                } else {
                    settings.setGreetingsEnabled(!settings.greetingsEnabled());
                }

                persist(player, settings);
                plugin.msg().send(
                        player,
                        settings.greetingsEnabled()
                                ? "notify_greetings_enabled"
                                : "notify_greetings_disabled"
                );
                return true;
            }

            // ------------------------------------------------------------
            // admin [on|off]
            // ------------------------------------------------------------
            case "admin": {
                if (args.length >= 2) {
                    String val = args[1].toLowerCase(Locale.ROOT);
                    if (val.equals("on") || val.equals("true") || val.equals("enable") || val.equals("enabled")) {
                        settings.setAdminUpdatesEnabled(true);
                    } else if (val.equals("off") || val.equals("false") || val.equals("disable") || val.equals("disabled")) {
                        settings.setAdminUpdatesEnabled(false);
                    } else {
                        plugin.msg().send(player, "notify_usage");
                        return true;
                    }
                } else {
                    settings.setAdminUpdatesEnabled(!settings.adminUpdatesEnabled());
                }

                persist(player, settings);
                plugin.msg().send(
                        player,
                        settings.adminUpdatesEnabled()
                                ? "notify_admin_enabled"
                                : "notify_admin_disabled"
                );
                return true;
            }

            // ------------------------------------------------------------
            // mode [actionbar|chat|title]  OR  mode (cycles)
            // ------------------------------------------------------------
            case "mode": {
                // If no arg, cycle modes (QoL)
                if (args.length < 2) {
                    NotificationMode next = switch (settings.getMode()) {
                        case ACTION_BAR -> NotificationMode.CHAT;
                        case CHAT -> NotificationMode.TITLE;
                        default -> NotificationMode.ACTION_BAR;
                    };

                    settings.setMode(next);
                    persist(player, settings);
                    plugin.msg().send(player, "notify_mode_set", next.name());
                    return true;
                }

                // Set directly
                NotificationMode mode = NotificationMode.fromString(args[1]);
                settings.setMode(mode);
                persist(player, settings);

                plugin.msg().send(player, "notify_mode_set", mode.name());
                return true;
            }

            // ------------------------------------------------------------
            // status
            // ------------------------------------------------------------
            case "status": {
                // Avoid requiring new lang keys. This is safe and always visible.
                player.sendMessage(color("&8[&bAegisGuard&8] &7Notification Settings:"));
                player.sendMessage(color(" &7- Plot Greetings: " + (settings.greetingsEnabled() ? "&aON" : "&cOFF")));
                player.sendMessage(color(" &7- Admin Updates: " + (settings.adminUpdatesEnabled() ? "&aON" : "&cOFF")));
                player.sendMessage(color(" &7- Mode: &b" + settings.getMode().name()));
                return true;
            }

            // ------------------------------------------------------------
            // all  (toggle both)
            // ------------------------------------------------------------
            case "all": {
                boolean newState = !(settings.greetingsEnabled() && settings.adminUpdatesEnabled());
                settings.setGreetingsEnabled(newState);
                settings.setAdminUpdatesEnabled(newState);

                persist(player, settings);

                // Reuse existing keys where possible
                plugin.msg().send(player, newState ? "notify_greetings_enabled" : "notify_greetings_disabled");
                plugin.msg().send(player, newState ? "notify_admin_enabled" : "notify_admin_disabled");
                return true;
            }

            // ------------------------------------------------------------
            // on/off  (set both)
            // ------------------------------------------------------------
            case "on":
            case "enable":
            case "enabled":
            case "true": {
                settings.setGreetingsEnabled(true);
                settings.setAdminUpdatesEnabled(true);
                persist(player, settings);

                plugin.msg().send(player, "notify_greetings_enabled");
                plugin.msg().send(player, "notify_admin_enabled");
                return true;
            }

            case "off":
            case "disable":
            case "disabled":
            case "false": {
                settings.setGreetingsEnabled(false);
                settings.setAdminUpdatesEnabled(false);
                persist(player, settings);

                plugin.msg().send(player, "notify_greetings_disabled");
                plugin.msg().send(player, "notify_admin_disabled");
                return true;
            }

            default:
                plugin.msg().send(player, "notify_usage");
                return true;
        }
    }

    // ---------------------------------------------------------------------
    // Persistence (1.2.6 unified keys + legacy collision safety)
    // ---------------------------------------------------------------------
    private void persist(Player player, PlayerNotificationSettings settings) {
        // Save using your notifications manager (authoritative)
        plugin.notifications().save(player, settings);

        // Also mirror to config keys used by SettingsGUI + PlotGreetingListener (safe + consistent)
        UUID uuid = player.getUniqueId();
        String base = "player_notifications.players." + uuid;

        plugin.getConfig().set(base + ".greetings", settings.greetingsEnabled());
        plugin.getConfig().set(base + ".admin_updates", settings.adminUpdatesEnabled());
        plugin.getConfig().set(base + ".mode", settings.getMode().name());

        // Legacy key becomes MODE STRING (prevents old boolean collision)
        plugin.getConfig().set("notifications." + uuid, settings.getMode().name());

        plugin.saveConfig();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
