package com.yourname.aegisguard.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LanguageManager {

    private final JavaPlugin plugin;
    private FileConfiguration localeConfig;
    private File localeFile;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadLocale();
    }

    public void loadLocale() {
        // 1. Get the desired language file name from config.yml
        // Default to "en_hybrid.yml" if not set
        String fileName = plugin.getConfig().getString("settings.language_file", "en_hybrid.yml");

        // 2. Create the "locales" folder if it doesn't exist
        File localeFolder = new File(plugin.getDataFolder(), "locales");
        if (!localeFolder.exists()) {
            localeFolder.mkdirs();
        }

        // 3. Check if the specific file exists. If not, save the default from resources.
        localeFile = new File(localeFolder, fileName);
        if (!localeFile.exists()) {
            try {
                // Tries to save the file from inside the .jar
                plugin.saveResource("locales/" + fileName, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("Could not find language file: " + fileName);
                plugin.getLogger().severe("Falling back to en_hybrid.yml");
                plugin.saveResource("locales/en_hybrid.yml", false);
                localeFile = new File(localeFolder, "en_hybrid.yml");
            }
        }

        // 4. Load the configuration
        localeConfig = YamlConfiguration.loadConfiguration(localeFile);
        plugin.getLogger().info("Loaded Language: " + fileName);
    }

    /**
     * Gets a message from the currently active locale file.
     */
    public String getMsg(String key) {
        // Get the string directly (No more "hybrid." prefix needed)
        String message = localeConfig.getString("messages." + key);
        
        if (message == null) {
            return ChatColor.RED + "Missing Key: " + key;
        }

        String prefix = localeConfig.getString("prefix", "&8[&bAegis&8] &7");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    /**
     * Gets a GUI title or Item Name
     */
    public String getGui(String key) {
        String text = localeConfig.getString("gui." + key, key);
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Gets Terminology (Estate vs Plot)
     */
    public String getTerm(String key) {
        String text = localeConfig.getString("terminology." + key, "Estate");
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
