package com.aegisguard.notify;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;

public class NotificationManager {

    private final AegisGuard plugin;

    public NotificationManager(AegisGuard plugin) {
        this.plugin = plugin;
        migrateLegacy();
    }

    private void migrateLegacy() {
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("notifications");
        if (legacy == null) return;

        for (String key : legacy.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (Exception ignored) {
                continue;
            }

            Object val = legacy.get(key);
            NotificationMode mode = NotificationMode.CHAT;
            boolean greetings = true;

            if (val instanceof Boolean) {
                greetings = (Boolean) val;
            } else if (val instanceof String) {
                mode = NotificationMode.fromString((String) val);
            }

            String base = "player_notifications.players." + uuid;
            plugin.getConfig().set(base + ".mode", mode.name());
            plugin.getConfig().set(base + ".greetings", greetings);
            plugin.getConfig().set(base + ".admin_updates", true);
        }

        plugin.getConfig().set("notifications", null);
        plugin.saveConfig();
    }

    public PlayerNotificationSettings get(Player player) {
        String base = "player_notifications.players." + player.getUniqueId();
        NotificationMode mode = NotificationMode.fromString(
                plugin.getConfig().getString(base + ".mode", "CHAT")
        );

        boolean greetings = plugin.getConfig().getBoolean(base + ".greetings", true);
        boolean admin = plugin.getConfig().getBoolean(base + ".admin_updates", true);

        return new PlayerNotificationSettings(mode, greetings, admin);
    }

    public void save(Player player, PlayerNotificationSettings settings) {
        String base = "player_notifications.players." + player.getUniqueId();
        plugin.getConfig().set(base + ".mode", settings.getMode().name());
        plugin.getConfig().set(base + ".greetings", settings.greetingsEnabled());
        plugin.getConfig().set(base + ".admin_updates", settings.adminUpdatesEnabled());
        plugin.saveConfig();
    }
}
