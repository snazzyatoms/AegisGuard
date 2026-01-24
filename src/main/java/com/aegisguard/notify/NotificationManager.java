package com.aegisguard.notify;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for player notification preferences.
 * Handles persistence, migration, and provides settings to other systems.
 *
 * @since 1.2.6
 */
public class NotificationManager {
    private final AegisGuard plugin;
    private final Map<UUID, PlayerNotificationSettings> settingsCache;
    private File dataFile;

    public NotificationManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.settingsCache = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "notifications.yml");

        loadData();
        migrateLegacyData();
    }

    /**
     * Load notification settings from disk into cache.
     */
    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create notifications.yml", e);
                return;
            }
        }

        FileConfiguration notificationData = YamlConfiguration.loadConfiguration(dataFile);

        // Load all settings into cache
        ConfigurationSection playersSection = notificationData.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuidString);
                    ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidString);
                    if (playerSection != null) {
                        PlayerNotificationSettings settings = new PlayerNotificationSettings(playerUUID, playerSection);
                        settingsCache.put(playerUUID, settings);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in notifications.yml: " + uuidString);
                }
            }
        }

        plugin.getLogger().info("Loaded " + settingsCache.size() + " notification preferences");
    }

    /**
     * Migrate notification settings from config.yml (multiple legacy formats).
     *
     * Legacy sources supported:
     * 1) notifications.<uuid> = "ACTION_BAR" (mode string)
     * 2) notifications.<uuid> = true/false (old greetings toggle collision)
     * 3) player_notifications.players.<uuid>.{mode,greetings,admin_updates}
     */
    private void migrateLegacyData() {
        FileConfiguration config = plugin.getConfig();
        int migrated = 0;

        // 1) Newer config-based structure some builds used
        ConfigurationSection pn = config.getConfigurationSection("player_notifications.players");
        if (pn != null) {
            for (String uuidString : pn.getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuidString);
                    if (settingsCache.containsKey(playerUUID)) continue;

                    ConfigurationSection ps = pn.getConfigurationSection(uuidString);
                    if (ps == null) continue;

                    PlayerNotificationSettings settings = new PlayerNotificationSettings(playerUUID);
                    settings.setGreetingsEnabled(ps.getBoolean("greetings", true));
                    settings.setAdminUpdatesEnabled(ps.getBoolean("admin_updates", true));
                    settings.setMode(NotificationMode.fromString(ps.getString("mode", "ACTION_BAR")));

                    settingsCache.put(playerUUID, settings);
                    migrated++;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in player_notifications.players: " + uuidString);
                }
            }
        }

        // 2) Older flat legacy section
        ConfigurationSection legacySection = config.getConfigurationSection("notifications");
        if (legacySection != null) {
            for (String uuidString : legacySection.getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuidString);
                    if (settingsCache.containsKey(playerUUID)) continue;

                    Object legacyValue = legacySection.get(uuidString);

                    PlayerNotificationSettings settings = new PlayerNotificationSettings(playerUUID);

                    if (legacyValue instanceof Boolean) {
                        // Old /aegis notify boolean collision: treat as greetings toggle
                        settings.setGreetingsEnabled((Boolean) legacyValue);
                        settings.setMode(NotificationMode.ACTION_BAR);
                    } else if (legacyValue instanceof String) {
                        settings.setMode(NotificationMode.fromString((String) legacyValue));
                        settings.setGreetingsEnabled(true);
                    }

                    settingsCache.put(playerUUID, settings);
                    migrated++;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in legacy notifications: " + uuidString);
                }
            }
        }

        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " legacy notification settings");
            saveData();
        }
    }

    /**
     * Save all notification settings to disk.
     */
    public void saveData() {
        if (dataFile == null) return;

        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection playersSection = out.createSection("players");

        // Serialize all settings
        for (Map.Entry<UUID, PlayerNotificationSettings> entry : settingsCache.entrySet()) {
            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            entry.getValue().serialize(playerSection);
        }

        try {
            out.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save notifications.yml", e);
        }
    }

    /**
     * Get settings for a player (creates defaults if missing)
     */
    public PlayerNotificationSettings getSettings(UUID playerUUID) {
        return settingsCache.computeIfAbsent(playerUUID, PlayerNotificationSettings::new);
    }

    /**
     * Get settings for a player (creates defaults if missing)
     */
    public PlayerNotificationSettings getSettings(Player player) {
        return getSettings(player.getUniqueId());
    }

    /**
     * Update settings for a player
     */
    public void updateSettings(PlayerNotificationSettings settings) {
        settingsCache.put(settings.getPlayerUUID(), settings);
        saveData();
    }

    /**
     * Toggle greetings for a player
     */
    public boolean toggleGreetings(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        boolean newState = settings.toggleGreetings();
        updateSettings(settings);
        return newState;
    }

    /**
     * Toggle admin updates for a player
     */
    public boolean toggleAdminUpdates(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        boolean newState = settings.toggleAdminUpdates();
        updateSettings(settings);
        return newState;
    }

    /**
     * Cycle notification mode for a player
     */
    public NotificationMode cycleMode(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        settings.cycleMode();
        updateSettings(settings);
        return settings.getMode();
    }

    /**
     * Set notification mode for a player
     */
    public void setMode(UUID playerUUID, NotificationMode mode) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        settings.setMode(mode);
        updateSettings(settings);
    }

    /**
     * Check if player has greetings enabled
     */
    public boolean hasGreetingsEnabled(UUID playerUUID) {
        return getSettings(playerUUID).isGreetingsEnabled();
    }

    /**
     * Check if player has admin updates enabled
     */
    public boolean hasAdminUpdatesEnabled(UUID playerUUID) {
        return getSettings(playerUUID).isAdminUpdatesEnabled();
    }

    /**
     * Get notification mode for a player
     */
    public NotificationMode getMode(UUID playerUUID) {
        return getSettings(playerUUID).getMode();
    }

    /**
     * Remove settings for a player (cleanup)
     */
    public void removeSettings(UUID playerUUID) {
        settingsCache.remove(playerUUID);
        saveData();
    }

    /**
     * Get cache size (for debugging)
     */
    public int getCacheSize() {
        return settingsCache.size();
    }

    /**
     * Reload notification data from disk
     */
    public void reload() {
        settingsCache.clear();
        loadData();
        migrateLegacyData();
    }
}
