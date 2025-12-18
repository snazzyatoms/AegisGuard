package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MessagesUtil (AegisGuard v1.2.4+)
 * - Compatibility bridge ONLY.
 * - DOES NOT read or create messages.yml.
 * - Uses CodexEngine for text lookups.
 * - Stores player language prefs in playerdata.yml (until fully migrated into Codex profiles).
 * - Supports hex colors (&#RRGGBB).
 */
public class MessagesUtil implements Listener {

    private final AegisGuard plugin;

    // Player Language Cache
    private final Map<UUID, String> playerStyles = new ConcurrentHashMap<>();
    private String defaultStyle = "old_english";

    // Persistence
    private File playerDataFile;
    private FileConfiguration playerData;
    private volatile boolean isPlayerDataDirty = false;

    // Hex Pattern
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessagesUtil(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        // Default language: prefer new config path, then legacy paths
        String cfgDefault = plugin.getConfig().getString("localization.default_language", null);
        if (cfgDefault == null) cfgDefault = plugin.getConfig().getString("localization.default_style", null);
        if (cfgDefault == null) cfgDefault = plugin.getConfig().getString("language_styles.default", null);
        if (cfgDefault == null) cfgDefault = "old_english";

        this.defaultStyle = (cfgDefault == null || cfgDefault.isBlank()) ? "old_english" : cfgDefault.trim();

        loadPlayerPreferences();
        plugin.getLogger().info("[AegisGuard] MessagesUtil compat loaded (NO messages.yml). Default style: " + defaultStyle);
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
    // Player Style System (Prefs)
    // ----------------------------

    public void setPlayerStyle(Player player, String style) {
        if (player == null) return;
        if (style == null) style = defaultStyle;

        List<String> valid = getAvailableStyles();
        if (valid != null && !valid.isEmpty() && !valid.contains(style)) {
            player.sendMessage(ChatColor.RED + "⚠ Invalid language style: " + style);
            return;
        }

        playerStyles.put(player.getUniqueId(), style);
        savePlayerPreference(player.getUniqueId(), style);

        String styleName = style.replace("_", " ");
        if (!styleName.isEmpty()) styleName = styleName.substring(0, 1).toUpperCase() + styleName.substring(1);
        player.sendMessage(ChatColor.GOLD + "🕮 Language set to: " + ChatColor.AQUA + styleName);

        // Live GUI Refresh (if Settings open)
        plugin.runMain(player, () -> {
            if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder != null && holder.getClass().getName().contains("SettingsGUI")) {
                    plugin.gui().settings().open(player);
                }
            }
        });
    }

    public String getPlayerStyle(Player player) {
        if (player == null) return defaultStyle;
        return playerStyles.getOrDefault(player.getUniqueId(), defaultStyle);
    }

    private List<String> getAvailableStyles() {
        List<String> styles = plugin.getConfig().getStringList("localization.available_languages");
        if (styles == null || styles.isEmpty()) {
            styles = plugin.getConfig().getStringList("language_styles.available");
        }
        if (styles == null || styles.isEmpty()) {
            styles = Arrays.asList("old_english", "hybrid_english", "modern_english", "spanish_mx", "spanish_ar");
        }
        return styles;
    }

    // ----------------------------
    // Persistence
    // ----------------------------

    public synchronized void loadPlayerPreferences() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try { playerDataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);

        ConfigurationSection section = playerData.getConfigurationSection("players");
        if (section != null) {
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String style = section.getString(uuidStr + ".language_style", defaultStyle);
                    playerStyles.put(uuid, style);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("[AegisGuard] Loaded " + playerStyles.size() + " player language preferences.");
    }

    private synchronized void savePlayerPreference(UUID uuid, String style) {
        if (playerData == null) {
            loadPlayerPreferences();
            if (playerData == null) return;
        }
        playerData.set("players." + uuid + ".language_style", style);
        isPlayerDataDirty = true;
    }

    public synchronized void savePlayerData() {
        if (playerDataFile == null || playerData == null || !isPlayerDataDirty) return;
        try {
            playerData.save(playerDataFile);
            isPlayerDataDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isPlayerDataDirty() { return isPlayerDataDirty; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        playerStyles.putIfAbsent(e.getPlayer().getUniqueId(), defaultStyle);
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
