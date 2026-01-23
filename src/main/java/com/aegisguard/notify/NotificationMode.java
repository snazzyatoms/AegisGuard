package com.aegisguard.notify;

/**
 * Notification delivery modes for player messages.
 * Separates what notifications exist from how players receive them.
 *
 * @since 1.2.6
 */
public enum NotificationMode {
    /**
     * Standard chat messages (legacy default)
     */
    CHAT("CHAT", "Chat"),

    /**
     * Action bar messages (above hotbar)
     */
    ACTION_BAR("ACTION_BAR", "Action Bar"),

    /**
     * Title/subtitle messages (center screen)
     */
    TITLE("TITLE", "Title");

    private final String configValue;
    private final String displayName;

    NotificationMode(String configValue, String displayName) {
        this.configValue = configValue;
        this.displayName = displayName;
    }

    /**
     * Get the config.yml storage value
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * Get the user-friendly display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse from config string with safe fallback
     *
     * @param value Config value (case-insensitive)
     * @return Matching mode or ACTION_BAR default
     */
    public static NotificationMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return ACTION_BAR; // Safe default
        }

        String normalized = value.toUpperCase().trim();
        for (NotificationMode mode : values()) {
            if (mode.configValue.equals(normalized) || mode.name().equals(normalized)) {
                return mode;
            }
        }

        return ACTION_BAR; // Fallback for invalid values
    }

    /**
     * Cycle to the next notification mode
     *
     * @return Next mode in sequence (wraps around)
     */
    public NotificationMode next() {
        NotificationMode[] modes = values();
        int nextIndex = (this.ordinal() + 1) % modes.length;
        return modes[nextIndex];
    }

    /**
     * Cycle to the previous notification mode
     *
     * @return Previous mode in sequence (wraps around)
     */
    public NotificationMode previous() {
        NotificationMode[] modes = values();
        int prevIndex = (this.ordinal() - 1 + modes.length) % modes.length;
        return modes[prevIndex];
    }
}
