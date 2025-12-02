package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.SettingsGUI;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MessagesUtil (AegisGuard v1.1.1)
 * - Handles multi-language support and message formatting.
 * - Supports Hex Colors (&#RRGGBB).
 */
public class MessagesUtil implements Listener {

    private final AegisGuard plugin;
    private FileConfiguration messages;
    
    // Player Language Cache
    private final Map<UUID, String> playerStyles = new ConcurrentHashMap<>();
    private String defaultStyle;

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
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);

        // --- AUTO-UPDATER LOGIC ---
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.messages.setDefaults(defConfig);
            this.messages.options().copyDefaults(true);
        }
        
        try {
            this.messages.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not auto-update messages.yml: " + e.getMessage());
        }

        this.defaultStyle = messages.getString("language_styles.default", "old_english");
        plugin.getLogger().info("[AegisGuard] Messages loaded. Default style: " + defaultStyle);
        
        loadPlayerPreferences(); 
    }

    // --- Accessors (Player-aware) ---
    
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
        return getList(player, key, null);
    }

    public List<String> getList(Player player, String key, List<String> fallback) {
        String style = playerStyles.getOrDefault(player.getUniqueId(), defaultStyle);
        String path = style + "." + key;
        List<String> list = messages.getStringList(path);
        
        // Fallback to default language if missing in current style
        if (list.isEmpty()) list = messages.getStringList(defaultStyle + "." + key);
        if (list.isEmpty()) return fallback != null ? fallback : Collections.emptyList();
        
        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) colored.add(format(line));
        return colored;
    }

    // --- Accessors (Console / Default) ---
    
    public String get(String key) {
        return format(messages.getString(defaultStyle + "." + key, "&c[Missing: " + key + "]"));
    }
    
    public String get(String key, String... kv) {
        String raw = messages.getString(defaultStyle + "." + key, "&c[Missing: " + key + "]");
        return format(applyPlaceholders(raw, kv));
    }
    
    public String get(String key, Map<String, String> placeholders) {
        String raw = messages.getString(defaultStyle + "." + key, "&c[Missing: " + key + "]");
        return format(applyPlaceholders(raw, placeholders));
    }
    
    public List<String> getList(String key) {
        List<String> list = messages.getStringList(defaultStyle + "." + key);
        if (list.isEmpty()) return Collections.emptyList();
        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) colored.add(format(line));
        return colored;
    }
    
    public boolean has(String key) {
        return messages.contains(defaultStyle + "." + key);
    }
    
    public String color(String text) { return format(text); }
    public String prefix() { return format(messages.getString("prefix", "&8[&bAegisGuard&8]&r ")); }

    // --- Senders ---
    
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

    // --- Player Style System ---
    
    public void setPlayerStyle(Player player, String style) {
        List<String> valid = messages.getStringList("language_styles.available");
        if (valid == null || !valid.contains(style)) {
            player.sendMessage(ChatColor.RED + "⚠ Invalid language style: " + style);
            return;
        }
        playerStyles.put(player.getUniqueId(), style);
        savePlayerPreference(player, style);
        
        String styleName = style.replace("_", " ");
        // Capitalize first letter
        if (styleName.length() > 0) styleName = styleName.substring(0, 1).toUpperCase() + styleName.substring(1);
        
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
        return playerStyles.getOrDefault(player.getUniqueId(), defaultStyle);
    }

    // --- Persistence (Async Save/Load) ---
    
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

    private synchronized void savePlayerPreference(Player player, String style) {
        if (playerDataFile == null) return;
        playerData.set("players." + player.getUniqueId() + ".language_style", style);
        isPlayerDataDirty = true; 
    }

    public synchronized void savePlayerData() {
        if (playerDataFile == null || !isPlayerDataDirty) return;
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

    // --- Internal Helpers ---
    
    private String getRawForPlayer(Player player, String key) {
        String style = playerStyles.getOrDefault(player.getUniqueId(), defaultStyle);
        String path = style + "." + key;
        if (!messages.contains(path)) path = defaultStyle + "." + key;
        return messages.getString(path, "&c[Missing: " + key + "]");
    }

    private String format(String msg) {
        if (msg == null) return "";
        
        // Hex Color Support (&#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String color = msg.substring(matcher.start(), matcher.end());
            msg = msg.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)).toString());
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
