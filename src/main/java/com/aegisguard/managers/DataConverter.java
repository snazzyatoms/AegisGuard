package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
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
        File oldFile = new File(plugin.getDataFolder(), "plots.yml"); 
        
        // Safety: If no old file exists, stop.
        if (!oldFile.exists()) {
            return; 
        }
        
        // If we already have estates loaded, don't double-migrate
        if (!estateManager.getAllEstates().isEmpty()) {
            plugin.getLogger().info("[Migration] Estates already loaded. Skipping migration.");
            return;
        }

        plugin.getLogger().info("⚠️ DETECTED LEGACY 'plots.yml'. Starting Migration...");
        
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);
        int count = 0;

        // Loop through keys
        for (String key : oldConfig.getKeys(false)) {
            // Handle "plots.UUID" structure
            if (key.equalsIgnoreCase("plots")) {
                ConfigurationSection sec = oldConfig.getConfigurationSection("plots");
                if (sec != null) {
                    for (String subKey : sec.getKeys(false)) {
                        // Inside plots, there might be OWNER UUIDs
                        ConfigurationSection ownerSec = sec.getConfigurationSection(subKey);
                        if (ownerSec != null) {
                            // Check if this section IS a plot, or contains plots
                            if (ownerSec.contains("world")) {
                                migrateSinglePlot(ownerSec, subKey); // Flat structure
                                count++;
                            } else {
                                // It's an owner grouping -> iterate plots inside
                                for (String plotId : ownerSec.getKeys(false)) {
                                    migrateSinglePlot(ownerSec.getConfigurationSection(plotId), plotId);
                                    count++;
                                }
                            }
                        }
                    }
                }
                continue;
            }
            
            // Handle flat structure (root keys are UUIDs)
             if (oldConfig.isConfigurationSection(key)) {
                 migrateSinglePlot(oldConfig.getConfigurationSection(key), key);
                 count++;
             }
        }

        plugin.getLogger().info("✅ MIGRATION SUCCESS: Converted " + count + " plots to Estates.");
        
        // Rename old file so we don't migrate again
        File backup = new File(plugin.getDataFolder(), "plots.yml.bak");
        if (oldFile.renameTo(backup)) {
            plugin.getLogger().info("Renamed plots.yml to plots.yml.bak");
        }
    }

    private void migrateSinglePlot(ConfigurationSection section, String id) {
        try {
            if (section == null) return;
            
            String ownerStr = section.getString("owner");
            if (ownerStr == null) return; // Skip invalid entries
            
            UUID ownerId;
            try {
                 ownerId = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException e) { return; }

            String worldName = section.getString("world");
            World world = Bukkit.getWorld(worldName);
            
            // Fallback: If world is null (renamed?), try default world
            if (world == null) {
                world = Bukkit.getWorlds().get(0);
                plugin.getLogger().warning("Migrating plot " + id + ": World '" + worldName + "' not found. Moved to '" + world.getName() + "'.");
            }

            int x1 = section.getInt("x1");
            int z1 = section.getInt("z1");
            int x2 = section.getInt("x2");
            int z2 = section.getInt("z2");
            
            Location min = new Location(world, x1, 0, z1);
            Location max = new Location(world, x2, 255, z2);
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
            
            if (estate == null) {
                plugin.getLogger().warning("Skipped plot " + id + " due to overlap or error.");
                return;
            }

            // --- MIGRATE FLAGS (Critical for Protection) ---
            if (section.isConfigurationSection("flags")) {
                ConfigurationSection flags = section.getConfigurationSection("flags");
                for (String flag : flags.getKeys(false)) {
                    boolean val = flags.getBoolean(flag);
                    estate.setFlag(flag, val);
                }
            } else {
                // If no flags found, FORCE SAFE DEFAULTS
                estate.setFlag("mobs", false); // No mobs
                estate.setFlag("pvp", false);  // No PvP
            }
            
            // Migrate Members
            if (section.isList("members")) {
                for (String memberId : section.getStringList("members")) {
                    try {
                        estate.setMember(UUID.fromString(memberId), "resident");
                    } catch (Exception ignored) {}
                }
            }
            // Migrate Roles map (if existed)
            if (section.isConfigurationSection("roles")) {
                 ConfigurationSection roles = section.getConfigurationSection("roles");
                 for (String uuidKey : roles.getKeys(false)) {
                     String role = roles.getString(uuidKey);
                     try { estate.setMember(UUID.fromString(uuidKey), role); } catch (Exception ignored) {}
                 }
            }
            
            // Save immediately to lock it in
            plugin.getDataStore().saveEstate(estate);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to migrate plot: " + id + " - " + e.getMessage());
        }
    }
}
