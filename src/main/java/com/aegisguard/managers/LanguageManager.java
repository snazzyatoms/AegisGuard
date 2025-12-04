package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class LanguageManager {
    private final AegisGuard plugin;
    // ... map logic ...

    public LanguageManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadAllLocales();
    }
    
    public void loadAllLocales() { /* ... */ }
    public void setPlayerLang(Player p, String file) { /* ... */ }

    public String getMsg(Player player, String key) {
        // ... logic ...
        return "Message"; 
    }
    
    // ADDED
    public String getGui(String key) {
        // Simplify: Return from default or fallback
        return getMsg(null, key);
    }
    
    // ADDED
    public List<String> getMsgList(Player player, String key) {
        // Implement fetching list from config
        return Collections.emptyList(); 
    }
}
