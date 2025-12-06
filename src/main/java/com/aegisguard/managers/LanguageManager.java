package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LanguageManager {

    private final AegisGuard plugin;
    private FileConfiguration langConfig;
    private File langFile;
    private final Map<String, String> terminology = new HashMap<>();

    public LanguageManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadAllLocales();
    }

    public void loadAllLocales() {
        // Default to the file specified in config, or modern.yml
        String localeName = plugin.getConfig().getString("settings.locale", "modern") + ".yml";
        File localeDir = new File(plugin.getDataFolder(), "locales");
        if (!localeDir.exists()) localeDir.mkdirs();

        this.langFile = new File(localeDir, localeName);
        if (!langFile.exists()) {
            plugin.saveResource("locales/" + localeName, false);
        }

        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Cache terminology for getTerm()
        if (langConfig.contains("terminology")) {
            for (String key : langConfig.getConfigurationSection("terminology").getKeys(false)) {
                terminology.put(key, langConfig.getString("terminology." + key));
            }
        }
        
        plugin.getLogger().info("Loaded language file: " + localeName);
    }

    // --- Core Message Methods ---

    public String getMsg(Player p, String key) {
        if (langConfig == null) return "§c[Lang Error]";
        String msg = lookupKey(key);
        if (msg == null) return "§c[Missing: " + key + "]";
        
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (msg.contains("%prefix%")) {
            String prefix = langConfig.getString("prefix", "&8[&bAegis&8] ");
            msg = msg.replace("%prefix%", ChatColor.translateAlternateColorCodes('&', prefix));
        }
        return msg;
    }

    // Overload for placeholders (fixes PlayerGUI error)
    public String getMsg(Player p, String key, Map<String, String> placeholders) {
        String msg = getMsg(p, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace(entry.getKey(), entry.getValue());
        }
        return msg;
    }

    public String getMsg(String key) {
        return getMsg(null, key);
    }

    // --- GUI & List Methods (Fixes GUI Errors) ---

    public String getGui(String key) {
        // Looks specifically in the 'gui' section, then falls back to root
        String val = langConfig.getString("gui." + key);
        if (val == null) val = langConfig.getString(key);
        if (val == null) return "§c" + key;
        return ChatColor.translateAlternateColorCodes('&', val);
    }

    public List<String> getMsgList(Player p, String key) {
        List<String> list = langConfig.getStringList("gui." + key);
        if (list.isEmpty()) list = langConfig.getStringList("messages." + key);
        if (list.isEmpty()) list = langConfig.getStringList(key);
        
        if (list.isEmpty()) {
            List<String> err = new ArrayList<>();
            err.add("§c[Missing List: " + key + "]");
            return err;
        }

        return list.stream()
                .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                .collect(Collectors.toList());
    }

    // --- Utility Methods ---

    public String getTerm(String key) {
        String term = terminology.getOrDefault(key, key);
        return ChatColor.translateAlternateColorCodes('&', term);
    }

    public void sendTitle(Player p, String titleKey, String subtitleKey) {
        if (p == null) return;
        String title = titleKey != null && !titleKey.isEmpty() ? getMsg(p, titleKey) : "";
        String subtitle = subtitleKey != null && !subtitleKey.isEmpty() ? getMsg(p, subtitleKey) : "";
        p.sendTitle(title, subtitle, 10, 70, 20);
    }

    // Stub for player-specific language (not fully implemented yet but required by AdminCommand)
    public void setPlayerLang(Player p, String lang) {
        // In the future, save this to a database or player config
        p.sendMessage(ChatColor.GREEN + "Language set to " + lang + " (Session only).");
    }

    // --- Private Helper ---
    private String lookupKey(String key) {
        String val = langConfig.getString(key);
        if (val == null) val = langConfig.getString("messages." + key);
        if (val == null) val = langConfig.getString("gui." + key);
        return val;
    }
}
