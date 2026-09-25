package com.aegisguard.publicbeta;

import java.util.Locale;

/** Player-selected Simple Voice Chat scope for the isolated Public Beta server. */
public enum PublicBetaVoiceMode {
    PROXIMITY,
    GLOBAL,
    CURRENT_WORLD;

    public static PublicBetaVoiceMode parse(String value, PublicBetaVoiceMode fallback) {
        if (value == null || value.isBlank()) return fallback == null ? PROXIMITY : fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? PROXIMITY : fallback;
        }
    }
}
