package com.aegisguard.notify;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.groups.PlotGroup;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
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
    private volatile boolean dirty;

    public NotificationManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.settingsCache = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "notifications.yml");
        this.dirty = false;

        loadData();
        migrateLegacyData();
    }

    /**
     * Load notification settings from disk into cache.
     */
    private void loadData() {
        settingsCache.clear();

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
        dirty = false;
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
    public synchronized void saveData() {
        if (dataFile == null) return;
        if (!dirty && dataFile.exists()) return;

        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection playersSection = out.createSection("players");

        // Serialize all settings
        for (Map.Entry<UUID, PlayerNotificationSettings> entry : settingsCache.entrySet()) {
            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            entry.getValue().serialize(playerSection);
        }

        try {
            out.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save notifications.yml", e);
        }
    }

    public void saveDataAsync() {
        dirty = true;
        plugin.runGlobalAsync(this::saveData);
    }

    /**
     * Get settings for a player (creates defaults if missing)
     */
    public PlayerNotificationSettings getSettings(UUID playerUUID) {
        return settingsCache.computeIfAbsent(playerUUID, uuid -> {
            dirty = true;
            return new PlayerNotificationSettings(uuid);
        });
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
        dirty = true;
        saveDataAsync();
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
        if (settingsCache.remove(playerUUID) != null) {
            dirty = true;
            saveDataAsync();
        }
    }

    /**
     * Get cache size (for debugging)
     */
    public int getCacheSize() {
        return settingsCache.size();
    }

    public PlayerNotificationSettings get(Player player) {
        return getSettings(player);
    }

    public void save(Player player, PlayerNotificationSettings settings) {
        if (player == null || settings == null) return;
        updateSettings(settings);
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Reload notification data from disk
     */
    public synchronized void reload() {
        if (dirty) saveData();
        loadData();
        migrateLegacyData();
    }

    public void notifyPlayer(UUID playerUUID, String messageKey, String fallback, Map<String, String> placeholders) {
        notifyPlayer(playerUUID, null, null, messageKey, fallback, placeholders);
    }

    public void notifyPlayer(UUID playerUUID, String titleKey, String titleFallback,
                             String messageKey, String fallback, Map<String, String> placeholders) {
        if (playerUUID == null || messageKey == null || messageKey.isBlank()) return;

        Player online = Bukkit.getPlayer(playerUUID);
        if (online == null || !online.isOnline()) return;

        String message = translate(online, messageKey, fallback, placeholders);
        if (message == null || message.isBlank()) return;

        String title = null;
        if (titleKey != null && !titleKey.isBlank()) {
            title = translate(online, titleKey, titleFallback == null ? "" : titleFallback, placeholders);
        } else if (titleFallback != null && !titleFallback.isBlank()) {
            title = applyPlaceholders(titleFallback, placeholders);
        }

        dispatch(online, title, message);
    }

    public void notifyPlayers(Collection<UUID> players, String messageKey, String fallback, Map<String, String> placeholders) {
        notifyPlayers(players, null, null, messageKey, fallback, placeholders);
    }

    public void notifyPlayers(Collection<UUID> players, String titleKey, String titleFallback,
                              String messageKey, String fallback, Map<String, String> placeholders) {
        if (players == null || players.isEmpty()) return;
        for (UUID playerId : new LinkedHashSet<>(players)) {
            notifyPlayer(playerId, titleKey, titleFallback, messageKey, fallback, placeholders);
        }
    }

    public void notifyGroupMembers(PlotGroup group, UUID excludePlayer,
                                   String titleKey, String titleFallback,
                                   String messageKey, String fallback,
                                   Map<String, String> placeholders) {
        if (group == null) return;
        LinkedHashSet<UUID> targets = new LinkedHashSet<>(group.getMemberIds());
        if (excludePlayer != null) targets.remove(excludePlayer);
        notifyPlayers(targets, titleKey, titleFallback, messageKey, fallback, placeholders);
    }

    public void notifyPlotMembers(Plot plot, UUID excludePlayer,
                                  String titleKey, String titleFallback,
                                  String messageKey, String fallback,
                                  Map<String, String> placeholders) {
        if (plot == null) return;

        LinkedHashSet<UUID> targets = new LinkedHashSet<>();
        if (plot.getOwner() != null) {
            targets.add(plot.getOwner());
        }
        if (plot.getPlayerRoles() != null) {
            targets.addAll(plot.getPlayerRoles().keySet());
        }

        if (plot.isGroupPlot() && plot.getGroupId() != null && plugin.groups() != null) {
            PlotGroup group = plugin.groups().getGroup(plot.getGroupId());
            if (group != null) {
                targets.addAll(group.getMemberIds());
            }
        }

        if (excludePlayer != null) {
            targets.remove(excludePlayer);
        }

        notifyPlayers(targets, titleKey, titleFallback, messageKey, fallback, placeholders);
    }

    public void notifyAdmins(String permission, String message) {
        if (message == null || message.isBlank()) return;

        String requiredPermission = (permission == null || permission.isBlank()) ? "aegis.admin" : permission;
        String colored = ChatColor.translateAlternateColorCodes('&', message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            if (!online.hasPermission(requiredPermission) && !plugin.isAdmin(online)) continue;
            if (!hasAdminUpdatesEnabled(online.getUniqueId())) continue;

            dispatch(online, ChatColor.GOLD + "Admin Update", colored, true);
        }
    }

    private void dispatch(Player player, String title, String message) {
        dispatch(player, title, message, false);
    }

    private void dispatch(Player player, String title, String message, boolean alreadyColored) {
        if (player == null || message == null || message.isBlank()) return;

        String coloredMessage = alreadyColored ? message : ChatColor.translateAlternateColorCodes('&', message);
        String coloredTitle = title == null || title.isBlank()
                ? null
                : (alreadyColored ? title : ChatColor.translateAlternateColorCodes('&', title));

        plugin.runMain(player, () -> dispatchNow(player, coloredTitle, coloredMessage));
    }

    private void dispatchNow(Player player, String coloredTitle, String coloredMessage) {
        if (player == null || !player.isOnline()) return;
        NotificationMode mode = getMode(player.getUniqueId());
        switch (mode) {
            case CHAT -> player.sendMessage(plugin.msg().prefix() + coloredMessage);
            case ACTION_BAR -> {
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(coloredMessage));
                } catch (Throwable ignored) {
                    player.sendMessage(plugin.msg().prefix() + coloredMessage);
                }
            }
            case TITLE -> player.sendTitle(
                    coloredTitle == null ? ChatColor.GOLD + "AegisGuard" : coloredTitle,
                    coloredMessage,
                    10,
                    50,
                    10
            );
        }
    }

    private String translate(Player player, String key, String fallback, Map<String, String> placeholders) {
        String value = null;
        try {
            if (plugin.codex() != null) {
                value = plugin.codex().tr(player, key, placeholders == null ? Map.of() : placeholders);
            }
        } catch (Throwable ignored) {
        }

        if (value == null || value.isBlank() || key.equalsIgnoreCase(value.trim())) {
            value = applyPlaceholders(fallback, placeholders);
        }
        return value;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isBlank() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }

        String out = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) continue;
            String value = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace("{" + key.toUpperCase(Locale.ROOT) + "}", value);
            out = out.replace("{" + key + "}", value);
        }
        return out;
    }
}
