package com.aegisguard.territory;

import com.aegisguard.AegisGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Encodes territory activity detail templates so player/staff viewers can resolve
 * them in their own language at display time. Legacy plain-English details remain
 * readable as-is. Activity TYPE tokens stay as program IDs; labels are localized.
 */
public final class ActivityText {

    private static final String PREFIX = "@lang:";

    private ActivityText() {}

    public static String encode(String detailsKey, Map<String, String> placeholders, String englishFallback) {
        if (detailsKey == null || detailsKey.isBlank()) {
            return englishFallback == null ? "" : englishFallback;
        }
        StringBuilder sb = new StringBuilder(PREFIX).append(detailsKey.trim());
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                sb.append('|').append(entry.getKey().trim()).append('=')
                        .append(escape(entry.getValue() == null ? "" : entry.getValue()));
            }
        }
        // Keep a human-readable English payload after the encoded block for older tools.
        if (englishFallback != null && !englishFallback.isBlank()) {
            sb.append(" :: ").append(englishFallback);
        }
        return sb.toString();
    }

    public static String resolveDetails(AegisGuard plugin, CommandSender viewer, String details) {
        if (details == null || details.isBlank()) return "";
        if (!details.startsWith(PREFIX)) return details;

        String payload = details.substring(PREFIX.length());
        int legacySep = payload.indexOf(" :: ");
        String encoded = legacySep >= 0 ? payload.substring(0, legacySep) : payload;
        String legacy = legacySep >= 0 ? payload.substring(legacySep + 4) : "";

        String[] parts = encoded.split("\\|", -1);
        if (parts.length == 0 || parts[0].isBlank()) return legacy.isBlank() ? details : legacy;

        String key = parts[0].trim();
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            placeholders.put(part.substring(0, eq).trim(), unescape(part.substring(eq + 1)));
        }

        String resolved = lookup(plugin, viewer, key, placeholders);
        if (resolved == null || resolved.isBlank() || resolved.equalsIgnoreCase(key)) {
            return legacy.isBlank() ? details : legacy;
        }
        return resolved;
    }

    public static String resolveTypeLabel(AegisGuard plugin, CommandSender viewer, String type) {
        if (type == null || type.isBlank()) return "";
        String key = "activity_type_" + type.trim().toLowerCase(Locale.ROOT);
        String resolved = lookup(plugin, viewer, key, Map.of("TYPE", type.trim()));
        if (resolved == null || resolved.isBlank() || resolved.equalsIgnoreCase(key)) {
            return type.trim();
        }
        return resolved;
    }

    private static String lookup(AegisGuard plugin, CommandSender viewer,
                                 String key, Map<String, String> placeholders) {
        if (plugin == null || plugin.codex() == null || key == null || key.isBlank()) return null;
        Map<String, String> ph = placeholders == null ? Collections.emptyMap() : placeholders;
        try {
            if (viewer instanceof Player player) {
                return plugin.codex().tr(player, key, ph);
            }
            return plugin.codex().tr(key, ph);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=");
    }

    private static String unescape(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }
}
