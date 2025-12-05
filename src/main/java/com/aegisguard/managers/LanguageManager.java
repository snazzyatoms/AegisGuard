package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    private final AegisGuard plugin;
    private final Map<String, FileConfiguration> localeCache = new HashMap<>();
    private final Map<UUID, String> playerLocales = new ConcurrentHashMap<>();
    private static final String DEFAULT_LANG = "en_old";
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public LanguageManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadAllLocales();
    }

    public void loadAllLocales() {
        localeCache.clear();
        File localeFolder = new File(plugin.getDataFolder(), "locales");
        if (!localeFolder.exists()) localeFolder.mkdirs();

        String[] defaults = {"en_old.yml", "en_hybrid.yml", "en_modern.yml"};
        for (String fileName : defaults) {
            File file = new File(localeFolder, fileName);
            if (!file.exists()) {
                try { plugin.saveResource("locales/" + fileName, false); } 
                catch (Exception ignored) {}
            }
        }

        for (File file : localeFolder.listFiles()) {
            if (file.getName().endsWith(".yml")) {
                String key = file.getName().replace(".yml", "");
                localeCache.put(key, YamlConfiguration.loadConfiguration(file));
                plugin.getLogger().info("Loaded Language: " + key);
            }
        }
    }

    public void setPlayerLang(Player player, String langKey) {
        if (localeCache.containsKey(langKey)) {
            playerLocales.put(player.getUniqueId(), langKey);
            player.sendMessage(ChatColor.GREEN + "Language changed to: " + langKey);
        }
    }

    // --- GETTERS ---

    public String getMsg(Player player, String key) {
        String langKey = getPlayerLangKey(player);
        FileConfiguration config = localeCache.getOrDefault(langKey, localeCache.get(DEFAULT_LANG));
        
        if (config == null) return "§c[Config Error: No Locales Loaded]";
        
        String msg = config.getString("messages." + key);
        // Fallback check in root if not in messages section (legacy support)
        if (msg == null) msg = config.getString(key);
        if (msg == null) return "§c[Missing: " + key + "]";
        
        String prefix = config.getString("prefix", "&8[&bAegis&8] &7");
        return format(prefix + msg);
    }

    /**
     * THIS IS THE METHOD THAT WAS MISSING.
     * It handles the replacement of {PLOT_NAME}, {OWNER}, etc.
     */
    public String getMsg(Player player, String key, Map<String, String> placeholders) {
        String msg = getMsg(player, key);
        
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                // Support both {KEY} and %KEY% formats
                msg = msg.replace("{" + k + "}", v).replace("%" + k + "%", v);
            }
        }
        return msg;
    }

    public String getGui(String key) {
        FileConfiguration config = localeCache.get(DEFAULT_LANG);
        if (config == null && !localeCache.isEmpty()) config = localeCache.values().iterator().next();
        
        if (config == null) return "§cGUI Error";
        
        String val = config.getString("gui." + key);
        // Fallback to root if not found
        if (val == null) val = config.getString(key);
        return format(val != null ? val : key);
    }

    public List<String> getMsgList(Player player, String key) {
        String langKey = getPlayerLangKey(player);
        FileConfiguration config = localeCache.getOrDefault(langKey, localeCache.get(DEFAULT_LANG));

        if (config == null) return new ArrayList<>();
        
        List<String> list = config.getStringList("messages." + key);
        if (list.isEmpty()) list = config.getStringList("gui." + key); 
        if (list.isEmpty()) list = config.getStringList(key);
        
        List<String> formatted = new ArrayList<>();
        for (String s : list) formatted.add(format(s));
        return formatted;
    }
    
    public String getTerm(String key) {
        FileConfiguration config = localeCache.get(DEFAULT_LANG);
        if (config == null) return key;
        return format(config.getString("terminology." + key, key));
    }

    // --- HELPERS ---

    private String getPlayerLangKey(Player player) {
        if (player == null) return DEFAULT_LANG;
        return playerLocales.getOrDefault(player.getUniqueId(), 
               plugin.getConfig().getString("settings.default_language_file", DEFAULT_LANG + ".yml").replace(".yml", ""));
    }

    private String format(String msg) {
        if (msg == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String color = msg.substring(matcher.start(), matcher.end());
            msg = msg.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)).toString());
            matcher = HEX_PATTERN.matcher(msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
    
    public void sendTitle(Player p, String titleKey, String subtitleInput) {
        String title = getMsg(p, titleKey);
        if (title.contains("Missing")) title = "";
        p.sendTitle(title, format(subtitleInput), 10, 70, 20);
    }
}
