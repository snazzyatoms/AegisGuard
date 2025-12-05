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
        // FIX: Look for 'plots.yml', not 'claims.yml'
        File oldFile = new File(plugin.getDataFolder(), "plots.yml"); 
        
        // Safety: If no old file exists, OR we already have new data (estates.yml), stop.
        if (!oldFile.exists()) {
            return; 
        }
        
        // If we already have estates loaded, don't double-migrate
        if (!plugin.getEstateManager().getAllEstates().isEmpty()) {
            return;
        }

        plugin.getLogger().info("⚠️ DETECTED LEGACY 'plots.yml'. Migrating to Estate System...");
        
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);
        int count = 0;

        for (String key : oldConfig.getKeys(false)) {
            // In some legacy versions, root keys were UUIDs directly.
            // In others, it was plots.UUID. We assume the file structure is flat or under 'plots'.
            
            String root = key;
            // Check if we need to dive deeper (e.g. if the key is "plots")
            if (key.equalsIgnoreCase("plots")) {
                ConfigurationSection sec = oldConfig.getConfigurationSection("plots");
                if (sec != null) {
                    for (String subKey : sec.getKeys(false)) {
                        migrateSinglePlot(sec.getConfigurationSection(subKey), subKey);
                        count++;
                    }
                }
                continue; // Skip the rest of the loop for this key
            }
            
            // Attempt to migrate flat keys if "plots" section didn't exist
            // (This handles older/different data store versions)
             if (oldConfig.isConfigurationSection(key)) {
                 migrateSinglePlot(oldConfig.getConfigurationSection(key), key);
                 count++;
             }
        }

        plugin.getLogger().info("✅ MIGRATION SUCCESS: Converted " + count + " plots to Estates.");
        
        // Disable Sidebar (Legacy cleanup)
        plugin.getConfig().set("visuals.sidebar_enabled", false);
        plugin.saveConfig();
        
        // Rename old file so we don't migrate again next reboot
        // We rename it to .bak to keep a safe backup
        oldFile.renameTo(new File(plugin.getDataFolder(), "plots.yml.bak"));
    }

    private void migrateSinglePlot(org.bukkit.configuration.ConfigurationSection section, String id) {
        try {
            if (section == null) return;
            
            String ownerStr = section.getString("owner");
            if (ownerStr == null) return;
            
            UUID ownerId = UUID.fromString(ownerStr);
            String world = section.getString("world");
            int x1 = section.getInt("x1");
            int z1 = section.getInt("z1");
            int x2 = section.getInt("x2");
            int z2 = section.getInt("z2");
            
            if (world == null || Bukkit.getWorld(world) == null) return;

            Location min = new Location(Bukkit.getWorld(world), x1, 0, z1);
            Location max = new Location(Bukkit.getWorld(world), x2, 255, z2);
            Cuboid region = new Cuboid(min, max);
            
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
            String name = (owner.getName() != null ? owner.getName() : "Unknown") + "'s Estate";
            
            // Create the Estate
            Estate estate = estateManager.createEstate(
                owner, 
                region, 
                name, 
                false 
            );
            
            // Migrate Members if list exists
            if (section.isList("members")) {
                for (String memberId : section.getStringList("members")) {
                    try {
                        estate.setMember(UUID.fromString(memberId), "resident");
                    } catch (Exception ignored) {}
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to migrate plot: " + id + " - " + e.getMessage());
        }
    }
}
