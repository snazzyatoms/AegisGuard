package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Localizes staff-visible console / operational log lines using the server
 * default language (localization.default_language), with modern_english fallback
 * via CodexEngine resolution. Exception stack traces remain attached separately.
 */
public final class ConsoleMessages {

    private final AegisGuard plugin;

    public ConsoleMessages(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void info(String key, String fallback) {
        info(key, fallback, Collections.emptyMap());
    }

    public void info(String key, String fallback, Map<String, String> placeholders) {
        plugin.getLogger().info(plain(key, fallback, placeholders));
    }

    public void info(String key, String fallback, String... kv) {
        info(key, fallback, mapOf(kv));
    }

    public void warning(String key, String fallback) {
        warning(key, fallback, Collections.emptyMap());
    }

    public void warning(String key, String fallback, Map<String, String> placeholders) {
        plugin.getLogger().warning(plain(key, fallback, placeholders));
    }

    public void warning(String key, String fallback, String... kv) {
        warning(key, fallback, mapOf(kv));
    }

    public void warning(String key, String fallback, Throwable error, Map<String, String> placeholders) {
        String message = plain(key, fallback, placeholders);
        if (error == null) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().log(Level.WARNING, message, error);
        }
    }

    public void warning(String key, String fallback, Throwable error, String... kv) {
        warning(key, fallback, error, mapOf(kv));
    }

    public void severe(String key, String fallback) {
        severe(key, fallback, Collections.emptyMap());
    }

    public void severe(String key, String fallback, Map<String, String> placeholders) {
        plugin.getLogger().severe(plain(key, fallback, placeholders));
    }

    public void severe(String key, String fallback, String... kv) {
        severe(key, fallback, mapOf(kv));
    }

    public void severe(String key, String fallback, Throwable error, Map<String, String> placeholders) {
        String message = plain(key, fallback, placeholders);
        if (error == null) {
            plugin.getLogger().severe(message);
        } else {
            plugin.getLogger().log(Level.SEVERE, message, error);
        }
    }

    public void severe(String key, String fallback, Throwable error, String... kv) {
        severe(key, fallback, error, mapOf(kv));
    }

    /**
     * Localized plain text for Discord embeds / console (no Minecraft color codes).
     */
    public String plain(String key, String fallback) {
        return plain(key, fallback, Collections.emptyMap());
    }

    public String plain(String key, String fallback, Map<String, String> placeholders) {
        Map<String, String> ph = placeholders == null ? Collections.emptyMap() : placeholders;
        String raw = resolveRaw(null, key, fallback, ph);
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw)).trim();
    }

    public String plain(String key, String fallback, String... kv) {
        return plain(key, fallback, mapOf(kv));
    }

    /**
     * Localized text for a specific viewer (keeps color codes).
     */
    public String text(CommandSender viewer, String key, String fallback, Map<String, String> placeholders) {
        Map<String, String> ph = placeholders == null ? Collections.emptyMap() : placeholders;
        return resolveRaw(viewer, key, fallback, ph);
    }

    private String resolveRaw(CommandSender viewer, String key, String fallback, Map<String, String> placeholders) {
        String resolved = null;
        try {
            if (plugin.codex() != null && key != null && !key.isBlank()) {
                if (viewer instanceof Player player) {
                    resolved = plugin.codex().tr(player, key, placeholders);
                } else {
                    resolved = plugin.codex().tr(key, placeholders);
                }
                if (resolved != null && (resolved.isBlank() || resolved.equalsIgnoreCase(key))) {
                    resolved = null;
                }
            }
        } catch (Throwable ignored) {
            resolved = null;
        }
        if (resolved != null) return resolved;
        String out = fallback == null ? "" : fallback;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace("{" + entry.getKey() + "}", value);
        }
        return out;
    }

    private static Map<String, String> mapOf(String... kv) {
        if (kv == null || kv.length == 0) return Collections.emptyMap();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i] == null ? "" : kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return map;
    }
}
