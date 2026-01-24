package com.aegisguard.notify;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
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
 * Storage:
 * - notifications.yml (authoritative, 1.2.6+)
 *
 * Migration (read-only, non-destructive):
 * - config.yml: notifications.<uuid> (String mode OR Boolean greetings)
 * - config.yml: player_notifications.players.<uuid>.{mode,greetings,admin_updates}
 *
 * @since 1.2.6
 */
public class NotificationManager {

    private final AegisGuard plugin;
    private final Map<UUID, PlayerNotificationSettings> settingsCache;

    private FileConfiguration notificationData;
    private final File dataFile;

    // Debounced async save
    private final Object saveLock = new Object();
    private volatile boolean saveQueued = false;
    private volatile boolean dirty = false;
    private int saveTaskId = -1;

    public NotificationManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.settingsCache = new ConcurrentHashMap<>();

        // Ensure folder exists
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

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
                //noinspection ResultOfMethodCallIgnored
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
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed reading notifications.yml for " + uuidString, t);
                }
            }
        }

        plugin.getLogger().info("Loaded " + settingsCache.size() + " notification preferences (notifications.yml)");
    }

    /**
     * Migrate older notification settings from config.yml.
     * Non-destructive: does not delete old keys.
     */
    private void migrateLegacyData() {
        FileConfiguration cfg = plugin.getConfig();
        int migrated = 0;

        // 1) Newer structured config path (your SettingsGUI was using this)
        // player_notifications.players.<uuid>.mode/greetings/admin_updates
        ConfigurationSection structured = cfg.getConfigurationSection("player_notifications.players");
        if (structured != null) {
            for (String uuidString : structured.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in player_notifications.players: " + uuidString);
                    continue;
                }

                if (settingsCache.containsKey(uuid)) continue;

                ConfigurationSection s = structured.getConfigurationSection(uuidString);
                if (s == null) continue;

                String mode = s.getString("mode", "ACTION_BAR");
                boolean greetings = s.getBoolean("greetings", true);
                boolean adminUpdates = s.getBoolean("admin_updates", true);

                PlayerNotificationSettings built = buildFromValues(uuid, mode, greetings, adminUpdates);
                settingsCache.put(uuid, built);
                migrated++;
            }
        }

        // 2) Legacy path (very old): notifications.<uuid> could be String OR Boolean
        // - String: treated as mode
        // - Boolean: treated as greetings toggle
        ConfigurationSection legacy = cfg.getConfigurationSection("notifications");
        if (legacy != null) {
            for (String uuidString : legacy.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in legacy notifications: " + uuidString);
                    continue;
                }

                if (settingsCache.containsKey(uuid)) continue;

                Object raw = legacy.get(uuidString);
                String mode = "ACTION_BAR";
                boolean greetings = true;
                boolean adminUpdates = true;

                if (raw instanceof String s) {
                    mode = s;
                } else if (raw instanceof Boolean b) {
                    greetings = b;
                } else if (raw != null) {
                    // Attempt string fallback
                    String s = legacy.getString(uuidString, null);
                    if (s != null && !s.isBlank()) mode = s;
                }

                PlayerNotificationSettings built = buildFromValues(uuid, mode, greetings, adminUpdates);
                settingsCache.put(uuid, built);
                migrated++;
            }
        }

        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " notification settings from config.yml -> notifications.yml");
            markDirtyAndQueueSave();
        }
    }

    private PlayerNotificationSettings buildFromValues(UUID uuid, String mode, boolean greetings, boolean adminUpdates) {
        // Build a temporary section matching what PlayerNotificationSettings expects.
        MemoryConfiguration mem = new MemoryConfiguration();
        ConfigurationSection cs = mem.createSection("tmp");
        cs.set("mode", mode == null ? "ACTION_BAR" : mode);
        cs.set("greetings", greetings);
        cs.set("admin_updates", adminUpdates);
        return new PlayerNotificationSettings(uuid, cs);
    }

    /**
     * Queue an async save (debounced).
     */
    private void markDirtyAndQueueSave() {
        dirty = true;

        synchronized (saveLock) {
            if (saveQueued) return;
            saveQueued = true;

            // Debounce a little so rapid GUI clicks don't spam disk
            // (2 seconds = 40 ticks)
            saveTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                synchronized (saveLock) {
                    saveQueued = false;
                    saveTaskId = -1;
                }
                saveDataNow();
            }, 40L).getTaskId();
        }
    }

    /**
     * Immediate save to disk (async-safe).
     */
    private void saveDataNow() {
        if (!dirty) return;

        if (notificationData == null) {
            notificationData = new YamlConfiguration();
        }

        // Clear existing data
        notificationData.set("players", null);

        // Serialize all settings
        ConfigurationSection playersSection = notificationData.createSection("players");

        // Snapshot iteration for safety
        for (Map.Entry<UUID, PlayerNotificationSettings> entry : new ConcurrentHashMap<>(settingsCache).entrySet()) {
            try {
                ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
                entry.getValue().serialize(playerSection);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to serialize notifications for " + entry.getKey(), t);
            }
        }

        try {
            notificationData.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save notifications.yml", e);
        }
    }

    /**
     * Public save entrypoint (debounced).
     */
    public void saveData() {
        markDirtyAndQueueSave();
    }

    /**
     * Force save immediately (recommended from onDisable()).
     */
    public void saveNow() {
        // Cancel queued delayed save and flush
        synchronized (saveLock) {
            if (saveTaskId != -1) {
                try { Bukkit.getScheduler().cancelTask(saveTaskId); } catch (Throwable ignored) {}
                saveTaskId = -1;
            }
            saveQueued = false;
        }
        saveDataNow();
    }

    // -------------------------
    // API
    // -------------------------

    public PlayerNotificationSettings getSettings(UUID playerUUID) {
        return settingsCache.computeIfAbsent(playerUUID, PlayerNotificationSettings::new);
    }

    public PlayerNotificationSettings getSettings(Player player) {
        return getSettings(player.getUniqueId());
    }

    public void updateSettings(PlayerNotificationSettings settings) {
        settingsCache.put(settings.getPlayerUUID(), settings);
        markDirtyAndQueueSave();
    }

    public boolean toggleGreetings(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        boolean newState = settings.toggleGreetings();
        updateSettings(settings);
        return newState;
    }

    public boolean toggleAdminUpdates(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        boolean newState = settings.toggleAdminUpdates();
        updateSettings(settings);
        return newState;
    }

    public NotificationMode cycleMode(UUID playerUUID) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        settings.cycleMode();
        updateSettings(settings);
        return settings.getMode();
    }

    public void setMode(UUID playerUUID, NotificationMode mode) {
        PlayerNotificationSettings settings = getSettings(playerUUID);
        settings.setMode(mode);
        updateSettings(settings);
    }

    public boolean hasGreetingsEnabled(UUID playerUUID) {
        return getSettings(playerUUID).isGreetingsEnabled();
    }

    public boolean hasAdminUpdatesEnabled(UUID playerUUID) {
        return getSettings(playerUUID).isAdminUpdatesEnabled();
    }

    public NotificationMode getMode(UUID playerUUID) {
        return getSettings(playerUUID).getMode();
    }

    public void removeSettings(UUID playerUUID) {
        settingsCache.remove(playerUUID);
        markDirtyAndQueueSave();
    }

    public int getCacheSize() {
        return settingsCache.size();
    }

    public void reload() {
        // cancel pending save
        synchronized (saveLock) {
            if (saveTaskId != -1) {
                try { Bukkit.getScheduler().cancelTask(saveTaskId); } catch (Throwable ignored) {}
                saveTaskId = -1;
            }
            saveQueued = false;
        }

        settingsCache.clear();
        dirty = false;

        loadData();
        migrateLegacyData();
    }
}
