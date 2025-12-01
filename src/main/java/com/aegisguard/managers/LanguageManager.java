package com.yourname.aegisguard.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LanguageManager {

    private final JavaPlugin plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    public void loadLanguages() {
        // Creates lang.yml if it doesn't exist
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        plugin.getLogger().info("Language file loaded successfully.");
    }

    /**
     * THE HELPER METHOD
     * Automatically detects if server is using "modern", "old", or "hybrid"
     * and fetches the correct message string.
     */
    public String getMsg(String key) {
        // 1. Check which language is active in settings
        String activeLang = langConfig.getString("settings.active_language", "hybrid");

        // 2. Build the path (e.g., "hybrid.messages.claim_success")
        String path = activeLang + ".messages." + key;

        // 3. Get the string (or return error if missing)
        String message = langConfig.getString(path);
        if (message == null) {
            return ChatColor.RED + "Missing Language Key: " + path;
        }

        // 4. Get the Prefix
        String prefix = langConfig.getString("settings.prefix", "&8[&bAegis&8] &7");

        // 5. Return colored string with Prefix attached
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
    
    /**
     * Terminology Helper
     * Gets "Estate" or "Plot" based on language.
     */
    public String getTerm(String key) {
        String activeLang = langConfig.getString("settings.active_language", "hybrid");
        return ChatColor.translateAlternateColorCodes('&', 
               langConfig.getString(activeLang + ".terminology." + key, "Estate"));
    }
}
