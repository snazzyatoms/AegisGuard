package com.yourname.aegisguard.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LanguageManager {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> localeCache = new HashMap<>();
    private final Map<UUID, String> playerLocales = new HashMap<>();

    // CRITICAL UPDATE: Hardcoded default is now Old English
    private static final String DEFAULT_LANG = "en_old"; 

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAllLocales();
    }

    public void loadAllLocales() {
        localeCache.clear();
        File localeFolder = new File(plugin.getDataFolder(), "locales");
        if (!localeFolder.exists()) localeFolder.mkdirs();

        // Ensure all 3 exist, but prioritize en_old
        String[] defaults = {"en_old.yml", "en_hybrid.yml", "en_modern.yml"};

        for (String fileName : defaults) {
            File file = new File(localeFolder, fileName);
            if (!file.exists()) {
                try {
                    plugin.saveResource("locales/" + fileName, false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not create locale: " + fileName);
                }
            }
        }

        // Load them into memory
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

    public String getMsg(Player player, String key) {
        // 1. Check Player Preference -> Fallback to Config -> Fallback to DEFAULT_LANG (Old)
        String langKey = playerLocales.getOrDefault(player.getUniqueId(), 
                         plugin.getConfig().getString("settings.default_language_file", DEFAULT_LANG + ".yml").replace(".yml", ""));

        FileConfiguration config = localeCache.get(langKey);
        
        // Safety Fallback: If config is broken, load Old English
        if (config == null) config = localeCache.get(DEFAULT_LANG); 

        String message = config.getString("messages." + key);
        if (message == null) return ChatColor.RED + "Missing Key: " + key;

        String prefix = config.getString("prefix", "&8[&6Aegis&8] &7");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
}
