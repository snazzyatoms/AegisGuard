package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class LanguageManager {

    private final AegisGuard plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(AegisGuard plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        // Force load the 'modern.yml' or whatever is set in config
        String localeName = plugin.getConfig().getString("settings.locale", "modern") + ".yml";
        
        // Ensure folder exists
        File localeDir = new File(plugin.getDataFolder(), "locales");
        if (!localeDir.exists()) localeDir.mkdirs();
        
        this.langFile = new File(localeDir, localeName);
        
        // If file doesn't exist, create it from resources or default
        if (!langFile.exists()) {
            plugin.saveResource("locales/" + localeName, false);
        }
        
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
        plugin.getLogger().info("Loaded language file: " + localeName);
    }

    public String getMsg(Player p, String key) {
        if (langConfig == null) return "§c[Lang Error]";

        // 1. Direct Lookup
        String msg = langConfig.getString(key);

        // 2. Fallback: Check inside 'messages.' section
        if (msg == null) msg = langConfig.getString("messages." + key);

        // 3. Fallback: Check inside 'gui.' section (Fixes Codex/Menu issues)
        if (msg == null) msg = langConfig.getString("gui." + key);

        // 4. Fallback: Not found
        if (msg == null) return "§c[Missing: " + key + "]";

        // 5. Placeholders & Color
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        
        // Global Prefix (Optional)
        if (msg.contains("%prefix%")) {
            String prefix = langConfig.getString("prefix", "&8[&bAegis&8] ");
            msg = msg.replace("%prefix%", ChatColor.translateAlternateColorCodes('&', prefix));
        }

        return msg;
    }

    // Helper for non-player contexts
    public String getMsg(String key) {
        return getMsg(null, key);
    }
}
