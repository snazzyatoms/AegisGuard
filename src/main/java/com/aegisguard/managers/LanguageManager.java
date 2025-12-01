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
    // Cache all loaded languages: "en_modern" -> Config Object
    private final Map<String, FileConfiguration> localeCache = new HashMap<>();
    // Cache player preferences: UUID -> "en_modern"
    private final Map<UUID, String> playerLocales = new HashMap<>();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAllLocales();
    }

    public void loadAllLocales() {
        localeCache.clear();
        File localeFolder = new File(plugin.getDataFolder(), "locales");
        if (!localeFolder.exists()) localeFolder.mkdirs();

        // The 3 Core Languages to ensure exist
        String[] defaults = {"en_hybrid.yml", "en_modern.yml", "en_old.yml"};

        for (String fileName : defaults) {
            File file = new File(localeFolder, fileName);
            if (!file.exists()) {
                try {
                    plugin.saveResource("locales/" + fileName, false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not create default locale: " + fileName);
                }
            }
        }

        // Load EVERYTHING in the folder (allows for custom languages like es_ES.yml)
        for (File file : localeFolder.listFiles()) {
            if (file.getName().endsWith(".yml")) {
                String key = file.getName().replace(".yml", ""); // "en_modern"
                localeCache.put(key, YamlConfiguration.loadConfiguration(file));
                plugin.getLogger().info("Loaded Language: " + key);
            }
        }
    }

    /**
     * Set a player's language preference.
     * In a real plugin, you would save this to player_data.yml or MySQL async.
     */
    public void setPlayerLang(Player player, String langKey) {
        if (localeCache.containsKey(langKey)) {
            playerLocales.put(player.getUniqueId(), langKey);
            // TODO: Save to database here
            player.sendMessage(ChatColor.GREEN + "Language changed to: " + langKey);
        }
    }

    /**
     * THE NEW GET MESSAGE METHOD
     * Context-Aware: Checks who is asking for the message.
     */
    public String getMsg(Player player, String key) {
        // 1. Check Player Preference -> Fallback to Server Default
        String langKey = playerLocales.getOrDefault(player.getUniqueId(), 
                         plugin.getConfig().getString("settings.language_file", "en_hybrid").replace(".yml", ""));

        // 2. Get the Config from Cache
        FileConfiguration config = localeCache.get(langKey);
        if (config == null) config = localeCache.get("en_hybrid"); // Safety fallback

        // 3. Get String
        String message = config.getString("messages." + key);
        if (message == null) return ChatColor.RED + "Missing Key: " + key;

        String prefix = config.getString("prefix", "&8[&bAegis&8] &7");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
}
