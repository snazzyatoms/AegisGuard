package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public class DataConverter {

    private final AegisGuard plugin;
    private final EstateManager estateManager;

    public DataConverter(AegisGuard plugin, EstateManager estateManager) {
        this.plugin = plugin;
        this.estateManager = estateManager;
    }

    public void runMigration() {
        File oldFile = new File(plugin.getDataFolder(), "claims.yml"); 
        if (!oldFile.exists() || !plugin.getEstateManager().getAllEstates().isEmpty()) {
            return; 
        }

        plugin.getLogger().info("⚠️ DETECTED LEGACY PLOTS. Migrating to Estate System...");
        
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);
        int count = 0;

        for (String key : oldConfig.getKeys(false)) {
            try {
                UUID ownerId = UUID.fromString(oldConfig.getString(key + ".owner"));
                String world = oldConfig.getString(key + ".world");
                int x1 = oldConfig.getInt(key + ".x1");
                int z1 = oldConfig.getInt(key + ".z1");
                int x2 = oldConfig.getInt(key + ".x2");
                int z2 = oldConfig.getInt(key + ".z2");
                
                Location min = new Location(Bukkit.getWorld(world), x1, 0, z1);
                Location max = new Location(Bukkit.getWorld(world), x2, 255, z2);
                Cuboid region = new Cuboid(min, max);
                
                OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
                String name = (owner.getName() != null ? owner.getName() : "Unknown") + "'s Estate";
                
                // FIXED: Now passes OfflinePlayer correctly
                Estate estate = estateManager.createEstate(
                    owner, 
                    region, 
                    name, 
                    false 
                );
                
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate plot: " + key);
            }
        }

        plugin.getLogger().info("✅ MIGRATION SUCCESS: Converted " + count + " plots.");
        plugin.getConfig().set("sidebar.enabled", false);
        plugin.saveConfig();
        oldFile.renameTo(new File(plugin.getDataFolder(), "claims.yml.bak"));
    }
}
