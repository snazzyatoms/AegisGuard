package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import com.aegisguard.language.CodexEngine;
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
 *
 * ✅ Codex-backed compatibility layer:
 * - Provides old msg().get(...) style API so you don't have to refactor EVERYTHING at once.
 * - Reads ALL text from CodexEngine bundles (guis.yml, system.yml, upgrades.yml, expansions.yml).
 *
 * ✅ Player preference persistence:
 * - Stores per-player selected language style in playerdata.yml
 *
 * ❌ No dependency on messages.yml (safe to delete once AegisGuard.java stops re-installing it).
 */
public class MessagesUtil implements Listener {

    private final AegisGuard plugin;

    // Player Language Cache (persistent)
    private final Map<UUID, String> playerStyles = new ConcurrentHashMap<>();
    private String defaultStyle;

    // Persistence
    private File playerDataFile;
    private FileConfiguration playerData;
    private volatile boolean isPlayerDataDirty = false;

    // Hex Pattern (&#RRGGBB)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessagesUtil(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        CodexEngine codex = plugin.codex();

        // Default style comes from Codex first, then config fallback.
        if (codex != null) {
            this.defaultStyle = safeLower(codex.getDefaultStyle(), "old_english");
        } else {
            this.defaultStyle = safeLower(plugin.getConfig().getString("localization.default_style"), "old_english");
        }

        plugin.getLogger().info("[AegisGuard] MessagesUtil initialized (Codex-backed). Default style: " + defaultStyle);

        loadPlayerPreferences();
    }

    // ---------------------------------------------------------------------
    // Player-aware accessors (Codex-backed)
    // ---------------------------------------------------------------------

    public String get(Player player, String key) {
        return format(resolveForPlayer(player, key, Collections.emptyMap(), null));
    }

    public String get(Player player, String key, String... kv) {
        return format(resolveForPlayer(player, key, kvToMap(kv), null));
    }

    public String get(Player player, String key, Map<String, String> placeholders) {
        return format(resolveForPlayer(player, key, placeholders, null));
    }

    /**
     * Overload used by some GUIs:
     * get(player, key, defaultValue, placeholdersMap)
     */
    public String get(Player player, String key, String def, Map<String, String> placeholders) {
        return format(resolveForPlayer(player, key, placeholders, def));
    }

    public List<String> getList(Player player, String key) {
        return getList(player, key, null);
    }

    public List<String> getList(Player player, String key, List<String> fallback) {
        CodexEngine codex = plugin.codex();
        if (codex == null || player == null) {
            return fallback != null ? fallback : Collections.emptyList();
        }

        List<String> list = codex.trList(player, key);
        if (list == null || list.isEmpty()) {
            return fallback != null ? fallback : Collections.emptyList();
        }

        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) colored.add(format(line));
        return colored;
    }

    // ---------------------------------------------------------------------
    // Console / default accessors
    // ---------------------------------------------------------------------

    public String get(String key) {
        String raw = resolveForSender(null, key, Collections.emptyMap(), null);
        return format(raw);
    }

    public String get(String key, String... kv) {
        String raw = resolveForSender(null, key, kvToMap(kv), null);
        return format(raw);
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = resolveForSender(null, key, placeholders, null);
        return format(raw);
    }

    public List<String> getList(String key) {
        CodexEngine codex = plugin.codex();
        if (codex == null) return Collections.emptyList();

        List<String> list = codex.trList((CommandSender) null, key);
        if (list == null || list.isEmpty()) return Collections.emptyList();

        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) colored.add(format(line));
        return colored;
    }

    /**
     * Best-effort "has" for legacy call sites.
     * (Codex returns the key itself if missing.)
     */
    public boolean has(String key) {
        CodexEngine codex = plugin.codex();
        if (codex == null) return false;
        String raw = codex.tr(key);
        return raw != null && !raw.equals(key);
    }

    public String color(String text) {
        return format(text);
    }

    public String prefix() {
        CodexEngine codex = plugin.codex();
        String raw = (codex != null) ? codex.tr("prefix") : null;

        if (raw == null || raw.isEmpty() || raw.equals("prefix")) {
            raw = "&8[&bAegisGuard&8]&r ";
        }
        return format(raw);
    }

    // ---------------------------------------------------------------------
    // Sending helpers
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Player Style System (persistent) + Codex sync
    // ---------------------------------------------------------------------

    public void setPlayerStyle(Player player, String style) {
        if (player == null || style == null) return;

        style = style.toLowerCase(Locale.ROOT);

        List<String> valid = getValidStyles();
        if (valid == null || valid.isEmpty() || !valid.contains(style)) {
            player.sendMessage(ChatColor.RED + "⚠ Invalid language style: " + style);
            return;
        }

        playerStyles.put(player.getUniqueId(), style);

        // Sync into CodexEngine (so any codex.tr(player, ...) uses correct style)
        CodexEngine codex = plugin.codex();
        if (codex != null) codex.setPlayerStyle(player, style);

        savePlayerPreference(player, style);

        String styleName = style.replace("_", " ");
        if (!styleName.isEmpty()) styleName = styleName.substring(0, 1).toUpperCase() + styleName.substring(1);
        player.sendMessage(ChatColor.GOLD + "🕮 Language set to: " + ChatColor.AQUA + styleName);

        // Live GUI Refresh (if open)
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

        // Prefer Codex (source of truth for runtime lookups)
        CodexEngine codex = plugin.codex();
        if (codex != null) return codex.getPlayerStyle(player);

        return playerStyles.getOrDefault(player.getUniqueId(), defaultStyle);
    }

    private List<String> getValidStyles() {
        CodexEngine codex = plugin.codex();
        if (codex != null) return codex.getAvailableStyles();

        // Fallback to config list if codex isn't available
        List<String> cfgList = plugin.getConfig().getStringList("localization.available_languages");
        if (cfgList == null) return Collections.emptyList();

        List<String> lowered = new ArrayList<>(cfgList.size());
        for (String s : cfgList) lowered.add(s == null ? "" : s.toLowerCase(Locale.ROOT));
        return lowered;
    }

    // ---------------------------------------------------------------------
    // Persistence (playerdata.yml)
    // ---------------------------------------------------------------------

    public synchronized void loadPlayerPreferences() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        playerData = YamlConfiguration.loadConfiguration(playerDataFile);

        // Keep defaultStyle updated (in case /reload changes codex default)
        CodexEngine codex = plugin.codex();
        if (codex != null) {
            this.defaultStyle = safeLower(codex.getDefaultStyle(), defaultStyle);
        }

        ConfigurationSection section = playerData.getConfigurationSection("players");
        if (section != null) {
            List<String> valid = getValidStyles();
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String style = section.getString(uuidStr + ".language_style", defaultStyle);
                    style = safeLower(style, defaultStyle);

                    // Validate style; if invalid, clamp to default
                    if (valid != null && !valid.isEmpty() && !valid.contains(style)) {
                        style = defaultStyle;
                    }

                    playerStyles.put(uuid, style);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Push preferences into Codex runtime map for online players
        if (codex != null) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                String style = playerStyles.getOrDefault(p.getUniqueId(), defaultStyle);
                codex.setPlayerStyle(p, style);
            }
        }

        plugin.getLogger().info("[AegisGuard] Loaded " + playerStyles.size() + " player language preferences.");
    }

    private synchronized void savePlayerPreference(Player player, String style) {
        if (playerDataFile == null || playerData == null || player == null) return;

        playerData.set("players." + player.getUniqueId() + ".language_style", style);
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

    public boolean isPlayerDataDirty() {
        return isPlayerDataDirty;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        playerStyles.putIfAbsent(p.getUniqueId(), defaultStyle);

        CodexEngine codex = plugin.codex();
        if (codex != null) {
            String style = playerStyles.getOrDefault(p.getUniqueId(), defaultStyle);
            codex.setPlayerStyle(p, style);
        }
    }

    // ---------------------------------------------------------------------
    // Internal resolution + formatting
    // ---------------------------------------------------------------------

    private String resolveForPlayer(Player player, String key, Map<String, String> placeholders, String def) {
        if (key == null || key.isEmpty()) return "";

        CodexEngine codex = plugin.codex();
        if (codex == null || player == null) {
            return missingOrDefault(key, def);
        }

        // Ensure codex has player style set (in case something bypassed setPlayerStyle)
        String style = playerStyles.getOrDefault(player.getUniqueId(), defaultStyle);
        codex.setPlayerStyle(player, style);

        String raw = codex.tr(player, key, placeholders == null ? Collections.emptyMap() : placeholders);

        // Codex returns key if missing
        if (raw == null || raw.isEmpty() || raw.equals(key)) {
            return missingOrDefault(key, def);
        }
        return raw;
    }

    private String resolveForSender(CommandSender sender, String key, Map<String, String> placeholders, String def) {
        if (key == null || key.isEmpty()) return "";

        CodexEngine codex = plugin.codex();
        if (codex == null) return missingOrDefault(key, def);

        String raw = codex.tr(sender, key, placeholders == null ? Collections.emptyMap() : placeholders);
        if (raw == null || raw.isEmpty() || raw.equals(key)) {
            return missingOrDefault(key, def);
        }
        return raw;
    }

    private String missingOrDefault(String key, String def) {
        if (def != null) return def;
        return "&c[Missing: " + key + "]";
    }

    private String format(String msg) {
        if (msg == null) return "";

        // Hex Color Support (&#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String token = msg.substring(matcher.start(), matcher.end()); // e.g. "&#FFFFFF"
            msg = msg.replace(token, net.md_5.bungee.api.ChatColor.of(token.substring(1)).toString()); // "#FFFFFF"
            matcher = HEX_PATTERN.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private Map<String, String> kvToMap(String... kv) {
        if (kv == null || kv.length == 0) return Collections.emptyMap();
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = kv[i];
            String v = kv[i + 1];
            if (k == null || k.isEmpty()) continue;
            out.put(k, v == null ? "" : v);
        }
        return out;
    }

    private String safeLower(String in, String fallback) {
        if (in == null || in.trim().isEmpty()) return fallback;
        return in.trim().toLowerCase(Locale.ROOT);
    }
}
