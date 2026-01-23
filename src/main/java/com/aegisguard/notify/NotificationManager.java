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
    private FileConfiguration notificationData;
    private File dataFile;

    public NotificationManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.settingsCache = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "notifications.yml");

        loadData();
        migrateLegacyData();
    }

    /**
     * Load notification settings from disk
     */
    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create notifications.yml", e);
            }
        }

        notificationData = YamlConfiguration.loadConfiguration(dataFile);

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
     * Migrate old notification settings from config.yml
     * Old format: notifications.<uuid> = "ACTION_BAR"
     */
    private void migrateLegacyData() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection legacySection = config.getConfigurationSection("notifications");

        if (legacySection == null) {
            return; // No legacy data to migrate
        }

        int migrated = 0;
        for (String uuidString : legacySection.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidString);

                // Skip if already migrated
                if (settingsCache.containsKey(playerUUID)) {
                    continue;
                }

                // Migrate legacy setting
                String legacyMode = legacySection.getString(uuidString);
                PlayerNotificationSettings settings = PlayerNotificationSettings.fromLegacyConfig(playerUUID, legacyMode);
                settingsCache.put(playerUUID, settings);
                migrated++;

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in legacy notifications: " + uuidString);
            }
        }

        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " legacy notification settings");
            saveData(); // Persist migrated data
        }
    }

    /**
     * Save all notification settings to disk
     */
    public void saveData() {
        if (notificationData == null || dataFile == null) {
            return;
        }

        // Clear existing data
        notificationData.set("players", null);

        // Serialize all settings
        ConfigurationSection playersSection = notificationData.createSection("players");
        for (Map.Entry<UUID, PlayerNotificationSettings> entry : settingsCache.entrySet()) {
            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            entry.getValue().serialize(playerSection);
        }

        // Write to disk
        try {
            notificationData.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save notifications.yml", e);
        }
    }

    /**
     * Get settings for a player (creates defaults if missing)
     *
     * @param playerUUID Player UUID
     * @return Player notification settings
     */
    public PlayerNotificationSettings getSettings(UUID playerUUID) {
        return settingsCache.computeIfAbsent(playerUUID, PlayerNotificationSettings::new);
    }

    /**
     * Get settings for a player (creates defaults if missing)
     *
     * @param player Player
     * @return Player notification settings
     */
    public PlayerNotificationSettings getSettings(Player player) {
        return getSettings(player.getUniqueId());
    }

    /**
     * Update settings for a player
     *
     * @param settings Updated settings
     */
    public void updateSettings(PlayerNotificationSettings settings) {
        settingsCache.put(settings.getPlayerUUID(), settings);
        saveData();
    }

    /**
     * Toggle greetings for a player
     *
     * @param playerUUID Player UUID
     * @return New state (true = enabled)
     */
    public boolean toggleGreetings(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        boolean newState = settings.toggleGreetings();
        updateSettings(settings);
        return newState;
    }

    /**
     * Toggle admin updates for a player
     *
     * @param playerUUID Player UUID
     * @return New state (true = enabled)
     */
    public boolean toggleAdminUpdates(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        boolean newState = settings.toggleAdminUpdates();
        updateSettings(settings);
        return newState;
    }

    /**
     * Cycle notification mode for a player
     *
     * @param playerUUID Player UUID
     * @return New notification mode
     */
    public NotificationMode cycleMode(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        settings.cycleMode();
        updateSettings(settings);
        return settings.getMode();
    }

    /**
     * Set notification mode for a player
     *
     * @param playerUUID Player UUID
     * @param mode       New mode
     */
    public void setMode(UUID playerUUID, NotificationMode mode) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        settings.setMode(mode);
        updateSettings(settings);
    }

    /**
     * Check if player has greetings enabled (backwards compatible)
     *
     * @param playerUUID Player UUID
     * @return True if greetings enabled
     */
    public boolean hasGreetingsEnabled(UUID playerUUID) {
        return getSettings(playerUUID).isGreetingsEnabled();
    }

    /**
     * Check if player has admin updates enabled
     *
     * @param playerUUID Player UUID
     * @return True if admin updates enabled
     */
    public boolean hasAdminUpdatesEnabled(UUID playerUUID) {
        return getSettings(playerUUID).isAdminUpdatesEnabled();
    }

    /**
     * Get notification mode for a player
     *
     * @param playerUUID Player UUID
     * @return Notification mode
     */
    public NotificationMode getMode(UUID playerUUID) {
        return getSettings(playerUUID).getMode();
    }

    /**
     * Remove settings for a player (cleanup)
     *
     * @param playerUUID Player UUID
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
