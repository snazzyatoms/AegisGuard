package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MessagesUtil (AegisGuard v1.2.4+)
 * - Compatibility bridge ONLY.
 * - DOES NOT read or create messages.yml.
 * - Uses CodexEngine for text lookups.
 * - Player language prefs live in CodexEngine (config.yml localization.player_styles);
 *   legacy playerdata.yml values are migrated forward on load.
 * - Supports hex colors (&#RRGGBB).
 */
public class MessagesUtil {

    private final AegisGuard plugin;

    private String defaultStyle = "old_english";

    // Hex Pattern
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessagesUtil(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        // Default language: prefer new config path, then legacy paths
        String cfgDefault = plugin.getConfig().getString("localization.default_language", null);
        if (cfgDefault == null) cfgDefault = plugin.getConfig().getString("localization.default_style", null);
        if (cfgDefault == null) cfgDefault = plugin.getConfig().getString("language_styles.default", null);
        if (cfgDefault == null) cfgDefault = "old_english";

        this.defaultStyle = (cfgDefault == null || cfgDefault.isBlank()) ? "old_english" : cfgDefault.trim();

        migrateLegacyPlayerPreferences();
        plugin.console().info("log_messages_util_loaded", "[AegisGuard] MessagesUtil compat loaded (NO messages.yml). Default style: {STYLE}", "STYLE", defaultStyle);
    }

    // ----------------------------
    // Accessors (Player-aware)
    // ----------------------------

    public String get(Player player, String key) {
        return format(getRawForPlayer(player, key));
    }

    public String get(Player player, String key, String... kv) {
        return format(applyPlaceholders(getRawForPlayer(player, key), kv));
    }

    public String get(Player player, String key, Map<String, String> placeholders) {
        return format(applyPlaceholders(getRawForPlayer(player, key), placeholders));
    }

    public List<String> getList(Player player, String key) {
        List<String> list = null;
        try {
            if (plugin.codex() != null) list = plugin.codex().trList(player, key);
        } catch (Throwable ignored) {}

        if (list == null || list.isEmpty()) return Collections.emptyList();

        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) colored.add(format(line));
        return colored;
    }

    // ----------------------------
    // Accessors (Console / Default)
    // ----------------------------

    public String get(String key) {
        return format(getRawForDefault(key));
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = getRawForDefault(key);
        raw = applyPlaceholders(raw, placeholders);
        return format(raw);
    }

    public String get(String key, String... kv) {
        String raw = getRawForDefault(key);
        raw = applyPlaceholders(raw, kv);
        return format(raw);
    }

    public List<String> getList(String key) {
        // Optional: implement console list lookups later if needed
        return Collections.emptyList();
    }

    public boolean has(String key) {
        // Best-effort: if Codex exists, we can attempt lookup
        return plugin.codex() != null;
    }

    public String color(String text) { return format(text); }

    public String prefix() {
        // ✅ Fix: prefer localization.prefix, then legacy prefix
        String raw = plugin.getConfig().getString("localization.prefix", null);
        if (raw == null) raw = plugin.getConfig().getString("prefix", "&8[&bAegisGuard&8]&r ");
        return format(raw);
    }

    // ----------------------------
    // Senders
    // ----------------------------

    public void send(CommandSender sender, String key) {
        String msg = (sender instanceof Player p) ? get(p, key) : get(key);
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(prefix() + msg);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = (sender instanceof Player p) ? get(p, key, placeholders) : get(key, placeholders);
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(prefix() + msg);
    }

    public void send(CommandSender sender, String key, String... kv) {
        String msg = (sender instanceof Player p) ? get(p, key, kv) : get(key, kv);
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(prefix() + msg);
    }

    // ----------------------------
    // Player Style System (delegates to CodexEngine)
    // ----------------------------

    public void setPlayerStyle(Player player, String style) {
        if (player == null || plugin.codex() == null) return;
        if (style == null) style = defaultStyle;
        plugin.codex().setPlayerStyle(player, style);
    }

    public String getPlayerStyle(Player player) {
        if (player == null || plugin.codex() == null) return defaultStyle;
        return plugin.codex().getPlayerStyle(player);
    }

    // ----------------------------
    // Legacy playerdata.yml migration
    // ----------------------------

    /**
     * Copies per-player language styles stored by very old versions in
     * {@code playerdata.yml} into CodexEngine's {@code localization.player_styles}
     * config store. Runs at startup/reload; never overwrites an existing choice.
     */
    private void migrateLegacyPlayerPreferences() {
        File legacyFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!legacyFile.isFile() || !legacyFile.canRead()) return;

        FileConfiguration legacy;
        try {
            legacy = YamlConfiguration.loadConfiguration(legacyFile);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not read legacy playerdata.yml for language migration: "
                    + (t.getMessage() == null ? "" : t.getMessage()));
            return;
        }

        ConfigurationSection section = legacy.getConfigurationSection("players");
        if (section == null) return;

        int migrated = 0;
        for (String uuidStr : section.getKeys(false)) {
            String style = section.getString(uuidStr + ".language_style", null);
            if (style == null || style.isBlank()) continue;

            String target = "localization.player_styles." + uuidStr;
            if (plugin.getConfig().isSet(target)) continue;
            try {
                UUID.fromString(uuidStr); // skip malformed keys
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            plugin.getConfig().set(target, style);
            migrated++;
        }

        if (migrated > 0) {
            try {
                plugin.saveConfig();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not save migrated language preferences: "
                        + (t.getMessage() == null ? "" : t.getMessage()));
            }
            final int count = migrated;
            plugin.console().info("log_player_prefs_loaded",
                    "[AegisGuard] Migrated {COUNT} legacy player language preferences into localization.player_styles.",
                    "COUNT", String.valueOf(count));
        }
    }

    // ----------------------------
    // Internal Helpers
    // ----------------------------

    private String getRawForPlayer(Player player, String key) {
        try {
            if (plugin.codex() != null) {
                String v = plugin.codex().tr(player, key);
                if (v != null && !v.trim().isEmpty() && !v.trim().equalsIgnoreCase(key)) {
                    return v;
                }
            }
        } catch (Throwable ignored) {}

        return "&c[Missing: " + key + "]";
    }

    private String getRawForDefault(String key) {
        try {
            if (plugin.codex() != null) {
                String v = plugin.codex().tr(key);
                if (v != null && !v.trim().isEmpty() && !v.trim().equalsIgnoreCase(key)) {
                    return v;
                }
            }
        } catch (Throwable ignored) {}

        return "&c[Missing: " + key + "]";
    }

    private String format(String msg) {
        if (msg == null) return "";

        // Hex Color Support (&#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String token = matcher.group(0); // "&#A1B2C3"
            String hex = matcher.group(1);   // "A1B2C3"
            msg = msg.replace(token, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
            matcher = HEX_PATTERN.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private String applyPlaceholders(String msg, String... kv) {
        if (msg == null || kv == null || kv.length == 0) return msg;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = kv[i] == null ? "" : kv[i];
            String v = kv[i + 1] == null ? "" : kv[i + 1];
            msg = msg.replace("{" + k + "}", v).replace("%" + k + "%", v);
        }
        return msg;
    }

    private String applyPlaceholders(String msg, Map<String, String> map) {
        if (msg == null || map == null || map.isEmpty()) return msg;
        for (Map.Entry<String, String> e : map.entrySet()) {
            String k = e.getKey();
            String v = e.getValue() == null ? "" : e.getValue();
            msg = msg.replace("{" + k + "}", v).replace("%" + k + "%", v);
        }
        return msg;
    }
}
