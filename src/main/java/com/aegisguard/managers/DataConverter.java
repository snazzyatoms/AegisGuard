package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public class DataConverter {

    private final AegisGuard plugin;

    public DataConverter(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void runMigration() {
        // 1. Check for old v1.2.0 data file
        File oldFile = new File(plugin.getDataFolder(), "claims.yml"); 
        
        // Safety: If no old file exists, or we already have new data, stop.
        if (!oldFile.exists() || !plugin.getEstateManager().getAllEstates().isEmpty()) {
            return; 
        }

        plugin.getLogger().info("⚠️ DETECTED LEGACY PLOTS. Migrating to Estate System...");
        
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);
        int count = 0;

        for (String key : oldConfig.getKeys(false)) {
            try {
                // Extract Old Data
                UUID ownerId = UUID.fromString(oldConfig.getString(key + ".owner"));
                String world = oldConfig.getString(key + ".world");
                int x1 = oldConfig.getInt(key + ".x1");
                int z1 = oldConfig.getInt(key + ".z1");
                int x2 = oldConfig.getInt(key + ".x2");
                int z2 = oldConfig.getInt(key + ".z2");
                
                // Construct New Objects
                Location min = new Location(Bukkit.getWorld(world), x1, 0, z1);
                Location max = new Location(Bukkit.getWorld(world), x2, 255, z2);
                Cuboid region = new Cuboid(min, max);
                
                // Create "Private Estate" (isGuild = false)
                String name = Bukkit.getOfflinePlayer(ownerId).getName() + "'s Estate";
                Estate estate = plugin.getEstateManager().createEstate(
                    Bukkit.getOfflinePlayer(ownerId), 
                    region, 
                    name, 
                    false 
                );
                
                // Migrate Members (Map old 'trusted' list to 'Resident' role)
                for (String memberId : oldConfig.getStringList(key + ".members")) {
                    estate.setMember(UUID.fromString(memberId), "resident");
                }
                
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate plot: " + key);
            }
        }

        plugin.getLogger().info("✅ MIGRATION SUCCESS: Converted " + count + " plots.");
        
        // Disable Sidebar (Legacy cleanup)
        plugin.getConfig().set("sidebar.enabled", false);
        plugin.saveConfig();
        
        // Rename old file so we don't migrate again next reboot
        oldFile.renameTo(new File(plugin.getDataFolder(), "claims.yml.bak"));
    }
}
