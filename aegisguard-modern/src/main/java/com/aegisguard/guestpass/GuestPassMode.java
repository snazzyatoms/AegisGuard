package com.aegisguard.guestpass;

/**
 * How a Guest Pass consumes its duration.
 *
 * {@link #REAL_TIME} is the historical default: the pass expires at a fixed wall-clock time and
 * continues ticking while the recipient is offline or the server is stopped.
 *
 * {@link #ACTIVE_PLAYTIME} only decreases remaining duration while the recipient is online; it
 * pauses while they are offline and across server downtime, then resumes on their next join.
 */
public enum GuestPassMode {

    REAL_TIME,
    ACTIVE_PLAYTIME;

    public static GuestPassMode fromSerialized(String raw) {
        if (raw == null || raw.isBlank()) return REAL_TIME;
        try {
            return GuestPassMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return REAL_TIME;
        }
    }

    public String fallbackLabel() {
        return switch (this) {
            case REAL_TIME -> "Real-time";
            case ACTIVE_PLAYTIME -> "Active playtime";
        };
    }
}
