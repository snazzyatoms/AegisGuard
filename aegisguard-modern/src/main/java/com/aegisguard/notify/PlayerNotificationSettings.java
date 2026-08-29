package com.aegisguard.notify;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
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
     * Milestone 5 - when {@code false}, repeated protection denials are throttled.
     * Defaults to {@code true} (unchanged behavior).
     */
    private boolean repeatNotifications;

    /**
     * Milestone 5 - whether this player has already seen the optional first-claim walkthrough.
     */
    private boolean walkthroughSeen;

    // Category toggles (1.3.0+). All default ON to preserve current delivery behavior.
    private boolean guestPassNotifications;
    private boolean allianceNotifications;
    private boolean lockdownNotifications;
    private boolean travelNotifications;
    private boolean plotNoticeNotifications;

    /** Travel Atlas: traveler arrival preference applied only when a plot allows overrides. */
    public enum ArrivalPreference {
        OWNER_DEFAULT, CLASSIC, BEACON;

        public ArrivalPreference next() {
            return switch (this) {
                case OWNER_DEFAULT -> CLASSIC;
                case CLASSIC -> BEACON;
                case BEACON -> OWNER_DEFAULT;
            };
        }

        public static ArrivalPreference parse(String raw) {
            if (raw == null || raw.isBlank()) return OWNER_DEFAULT;
            try {
                return ArrivalPreference.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return OWNER_DEFAULT;
            }
        }
    }

    private ArrivalPreference preferredArrival = ArrivalPreference.OWNER_DEFAULT;

    public PlayerNotificationSettings(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.greetingsEnabled = true;
        this.adminUpdatesEnabled = true;
        this.mode = NotificationMode.ACTION_BAR;
        this.repeatNotifications = true;
        this.walkthroughSeen = false;
        this.guestPassNotifications = true;
        this.allianceNotifications = true;
        this.lockdownNotifications = true;
        this.travelNotifications = true;
        this.plotNoticeNotifications = true;
        this.preferredArrival = ArrivalPreference.OWNER_DEFAULT;
    }

    public PlayerNotificationSettings(NotificationMode mode, boolean greetingsEnabled, boolean adminUpdatesEnabled) {
        this.playerUUID = new UUID(0L, 0L);
        this.greetingsEnabled = greetingsEnabled;
        this.adminUpdatesEnabled = adminUpdatesEnabled;
        this.mode = (mode == null) ? NotificationMode.ACTION_BAR : mode;
        this.repeatNotifications = true;
        this.walkthroughSeen = false;
        this.guestPassNotifications = true;
        this.allianceNotifications = true;
        this.lockdownNotifications = true;
        this.travelNotifications = true;
        this.plotNoticeNotifications = true;
        this.preferredArrival = ArrivalPreference.OWNER_DEFAULT;
    }

    public PlayerNotificationSettings(UUID playerUUID, ConfigurationSection section) {
        this.playerUUID = playerUUID;
        this.greetingsEnabled = section.getBoolean("greetings", true);
        this.adminUpdatesEnabled = section.getBoolean("admin_updates", true);
        this.repeatNotifications = section.getBoolean("repeat_notifications", true);
        this.walkthroughSeen = section.getBoolean("walkthrough_seen", false);
        this.guestPassNotifications = section.getBoolean("guest_pass", true);
        this.allianceNotifications = section.getBoolean("alliance", true);
        this.lockdownNotifications = section.getBoolean("lockdown", true);
        this.travelNotifications = section.getBoolean("travel", true);
        this.plotNoticeNotifications = section.getBoolean("plot_notices", true);
        this.preferredArrival = ArrivalPreference.parse(section.getString("preferred_arrival"));

        String modeString = section.getString("mode", "ACTION_BAR");
        this.mode = NotificationMode.fromString(modeString);
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public boolean isGreetingsEnabled() { return greetingsEnabled; }
    public boolean isAdminUpdatesEnabled() { return adminUpdatesEnabled; }
    public NotificationMode getMode() { return mode; }
    public boolean isRepeatNotificationsEnabled() { return repeatNotifications; }
    public boolean isWalkthroughSeen() { return walkthroughSeen; }
    public boolean isGuestPassNotificationsEnabled() { return guestPassNotifications; }
    public boolean isAllianceNotificationsEnabled() { return allianceNotifications; }
    public boolean isLockdownNotificationsEnabled() { return lockdownNotifications; }
    public boolean isTravelNotificationsEnabled() { return travelNotifications; }
    public boolean isPlotNoticeNotificationsEnabled() { return plotNoticeNotifications; }
    public ArrivalPreference getPreferredArrival() {
        return preferredArrival == null ? ArrivalPreference.OWNER_DEFAULT : preferredArrival;
    }

    public boolean greetingsEnabled() { return greetingsEnabled; }
    public boolean adminUpdatesEnabled() { return adminUpdatesEnabled; }

    public void setGreetingsEnabled(boolean enabled) { this.greetingsEnabled = enabled; }
    public void setAdminUpdatesEnabled(boolean enabled) { this.adminUpdatesEnabled = enabled; }
    public void setMode(NotificationMode mode) { this.mode = (mode == null) ? NotificationMode.ACTION_BAR : mode; }
    public void setRepeatNotifications(boolean enabled) { this.repeatNotifications = enabled; }
    public void setWalkthroughSeen(boolean seen) { this.walkthroughSeen = seen; }
    public void setGuestPassNotifications(boolean enabled) { this.guestPassNotifications = enabled; }
    public void setAllianceNotifications(boolean enabled) { this.allianceNotifications = enabled; }
    public void setLockdownNotifications(boolean enabled) { this.lockdownNotifications = enabled; }
    public void setTravelNotifications(boolean enabled) { this.travelNotifications = enabled; }
    public void setPlotNoticeNotifications(boolean enabled) { this.plotNoticeNotifications = enabled; }
    public void setPreferredArrival(ArrivalPreference preference) {
        this.preferredArrival = preference == null ? ArrivalPreference.OWNER_DEFAULT : preference;
    }

    public ArrivalPreference cyclePreferredArrival() {
        this.preferredArrival = getPreferredArrival().next();
        return this.preferredArrival;
    }

    public void cycleMode() { this.mode = this.mode.next(); }

    public boolean toggleGreetings() {
        this.greetingsEnabled = !this.greetingsEnabled;
        return this.greetingsEnabled;
    }

    public boolean toggleAdminUpdates() {
        this.adminUpdatesEnabled = !this.adminUpdatesEnabled;
        return this.adminUpdatesEnabled;
    }

    public boolean toggleRepeatNotifications() {
        this.repeatNotifications = !this.repeatNotifications;
        return this.repeatNotifications;
    }

    public boolean toggleGuestPassNotifications() {
        this.guestPassNotifications = !this.guestPassNotifications;
        return this.guestPassNotifications;
    }

    public boolean toggleAllianceNotifications() {
        this.allianceNotifications = !this.allianceNotifications;
        return this.allianceNotifications;
    }

    public boolean toggleLockdownNotifications() {
        this.lockdownNotifications = !this.lockdownNotifications;
        return this.lockdownNotifications;
    }

    public boolean toggleTravelNotifications() {
        this.travelNotifications = !this.travelNotifications;
        return this.travelNotifications;
    }

    public boolean togglePlotNoticeNotifications() {
        this.plotNoticeNotifications = !this.plotNoticeNotifications;
        return this.plotNoticeNotifications;
    }

    /**
     * Category gate used by event fan-out. Unknown categories default to allowed.
     */
    public boolean allowsCategory(String category) {
        if (category == null || category.isBlank()) return true;
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "guest_pass", "guestpass" -> guestPassNotifications;
            case "alliance" -> allianceNotifications;
            case "lockdown" -> lockdownNotifications;
            case "travel" -> travelNotifications;
            case "plot_notice", "plot_notices", "notice" -> plotNoticeNotifications;
            case "greetings", "greeting" -> greetingsEnabled;
            case "admin", "admin_updates" -> adminUpdatesEnabled;
            default -> true;
        };
    }

    public void serialize(ConfigurationSection section) {
        section.set("greetings", greetingsEnabled);
        section.set("admin_updates", adminUpdatesEnabled);
        section.set("mode", mode.getConfigValue());
        section.set("repeat_notifications", repeatNotifications);
        section.set("walkthrough_seen", walkthroughSeen);
        section.set("guest_pass", guestPassNotifications);
        section.set("alliance", allianceNotifications);
        section.set("lockdown", lockdownNotifications);
        section.set("travel", travelNotifications);
        section.set("plot_notices", plotNoticeNotifications);
        section.set("preferred_arrival", getPreferredArrival().name());
    }

    public static PlayerNotificationSettings fromLegacyConfig(UUID playerUUID, String legacyValue) {
        PlayerNotificationSettings settings = new PlayerNotificationSettings(playerUUID);
        settings.setMode(NotificationMode.fromString(legacyValue));
        settings.setGreetingsEnabled(true);
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
