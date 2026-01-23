package com.aegisguard.notify;

import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * Per-player notification preferences.
 * Stored separately from config.yml to prevent data loss.
 *
 * @since 1.2.6
 */
public class PlayerNotificationSettings {
    private final UUID playerUUID;
    private boolean greetingsEnabled;
    private boolean adminUpdatesEnabled;
    private NotificationMode mode;

    /**
     * Create with safe defaults
     */
    public PlayerNotificationSettings(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.greetingsEnabled = true; // Default: greetings ON (backwards compatible)
        this.adminUpdatesEnabled = true; // Default: admin updates ON
        this.mode = NotificationMode.ACTION_BAR; // Default: action bar
    }

    /**
     * Create from config section
     *
     * @param playerUUID Player UUID
     * @param section    Config section containing settings
     */
    public PlayerNotificationSettings(UUID playerUUID, ConfigurationSection section) {
        this.playerUUID = playerUUID;

        // Safe deserialization with fallbacks
        this.greetingsEnabled = section.getBoolean("greetings", true);
        this.adminUpdatesEnabled = section.getBoolean("admin_updates", true);

        String modeString = section.getString("mode", "ACTION_BAR");
        this.mode = NotificationMode.fromString(modeString);
    }

    // === GETTERS ===

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public boolean isGreetingsEnabled() {
        return greetingsEnabled;
    }

    public boolean isAdminUpdatesEnabled() {
        return adminUpdatesEnabled;
    }

    public NotificationMode getMode() {
        return mode;
    }

    // === SETTERS ===

    public void setGreetingsEnabled(boolean enabled) {
        this.greetingsEnabled = enabled;
    }

    public void setAdminUpdatesEnabled(boolean enabled) {
        this.adminUpdatesEnabled = enabled;
    }

    public void setMode(NotificationMode mode) {
        if (mode == null) {
            this.mode = NotificationMode.ACTION_BAR;
        } else {
            this.mode = mode;
        }
    }

    /**
     * Cycle to the next notification mode
     */
    public void cycleMode() {
        this.mode = this.mode.next();
    }

    /**
     * Toggle greetings and return new state
     */
    public boolean toggleGreetings() {
        this.greetingsEnabled = !this.greetingsEnabled;
        return this.greetingsEnabled;
    }

    /**
     * Toggle admin updates and return new state
     */
    public boolean toggleAdminUpdates() {
        this.adminUpdatesEnabled = !this.adminUpdatesEnabled;
        return this.adminUpdatesEnabled;
    }

    // === SERIALIZATION ===

    /**
     * Serialize to config section
     *
     * @param section Target config section
     */
    public void serialize(ConfigurationSection section) {
        section.set("greetings", greetingsEnabled);
        section.set("admin_updates", adminUpdatesEnabled);
        section.set("mode", mode.getConfigValue());
    }

    /**
     * Legacy migration: Read old config format
     * Old format: notifications.<uuid> = "ACTION_BAR"
     *
     * @param playerUUID  Player UUID
     * @param legacyValue Old config value (mode string only)
     * @return Migrated settings
     */
    public static PlayerNotificationSettings fromLegacyConfig(UUID playerUUID, String legacyValue) {
        PlayerNotificationSettings settings = new PlayerNotificationSettings(playerUUID);
        settings.setMode(NotificationMode.fromString(legacyValue));
        settings.setGreetingsEnabled(true); // Assume enabled if they had config entry
        return settings;
    }

    @Override
    public String toString() {
        return "PlayerNotificationSettings{" +
                "player=" + playerUUID +
                ", greetings=" + greetingsEnabled +
                ", adminUpdates=" + adminUpdatesEnabled +
                ", mode=" + mode +
                '}';
    }
}
