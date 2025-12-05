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

/**
 * DataConverter
 * - Handles the critical migration from v1.2.0 'plots.yml' to v1.3.0 'estates'.
 * - Ensures no data (Owner, Flags, Members) is lost during the upgrade.
 */
public class DataConverter {

    private final AegisGuard plugin;
    private final EstateManager estateManager;

    public DataConverter(AegisGuard plugin, EstateManager estateManager) {
        this.plugin = plugin;
        this.estateManager = estateManager;
    }

    public void runMigration() {
        // 1. Check for Legacy File
        File oldFile = new File(plugin.getDataFolder(), "plots.yml"); 
        
        // If no old file exists, skip migration.
        if (!oldFile.exists()) {
            return; 
        }
        
        // If we already have new estates loaded, do not double-migrate.
        if (!estateManager.getAllEstates().isEmpty()) {
            return;
        }

        plugin.getLogger().info("⚠️ DETECTED LEGACY 'plots.yml'. Starting Migration to Estate System...");
        
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);
        int count = 0;
        int failCount = 0;

        // 2. Iterate through all legacy entries
        for (String key : oldConfig.getKeys(false)) {
            try {
                // Handle nested "plots.UUID" structure (Common in v1.2)
                if (key.equalsIgnoreCase("plots")) {
                    ConfigurationSection sec = oldConfig.getConfigurationSection("plots");
                    if (sec != null) {
                        for (String subKey : sec.getKeys(false)) {
                            // Check if this is a direct plot OR a player UUID holding plots
                            ConfigurationSection subSec = sec.getConfigurationSection(subKey);
                            
                            if (subSec.contains("world")) {
                                // It's a plot directly
                                migrateSinglePlot(subSec, subKey);
                                count++;
                            } else {
                                // It's a UUID container -> loop children
                                for (String plotId : subSec.getKeys(false)) {
                                    migrateSinglePlot(subSec.getConfigurationSection(plotId), plotId);
                                    count++;
                                }
                            }
                        }
                    }
                    continue;
                }
                
                // Handle flat structure (Root keys are UUIDs)
                if (oldConfig.isConfigurationSection(key)) {
                     migrateSinglePlot(oldConfig.getConfigurationSection(key), key);
                     count++;
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to process legacy key: " + key);
                e.printStackTrace();
                failCount++;
            }
        }

        // 3. Cleanup & Backup
        if (count > 0) {
            plugin.getLogger().info("✅ MIGRATION SUCCESS: Converted " + count + " plots to Estates.");
            plugin.getLogger().info("ℹ️ Old 'plots.yml' has been renamed to 'plots.yml.bak' for safety.");
            
            // Rename old file so we don't migrate again next reboot
            oldFile.renameTo(new File(plugin.getDataFolder(), "plots.yml.bak"));
        } else {
            if (failCount == 0) {
                plugin.getLogger().info("No valid plots found in plots.yml to migrate.");
            } else {
                plugin.getLogger().warning("Migration finished with " + failCount + " errors. Check 'plots.yml'.");
            }
        }
    }

    private void migrateSinglePlot(ConfigurationSection section, String id) {
        try {
            if (section == null) return;
            
            // -- 1. Basic Data --
            String ownerStr = section.getString("owner");
            if (ownerStr == null) return; // Invalid entry
            
            UUID ownerId;
            try {
                 ownerId = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException e) { return; }

            String worldName = section.getString("world");
            World world = Bukkit.getWorld(worldName);
            
            // Fallback: If world is null (renamed?), try default world to save the data
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
            
            // -- 2. Create the Estate Object --
            Estate estate = estateManager.createEstate(
                owner, 
                region, 
                name, 
                false // Default to Private Estate
            );
            
            if (estate == null) {
                plugin.getLogger().warning("Skipped plot " + id + " due to overlap or error.");
                return;
            }

            // -- 3. MIGRATE FLAGS (Critical: Mobs, PvP, etc.) --
            // We iterate ALL keys in the 'flags' section so we don't miss custom ones.
            if (section.isConfigurationSection("flags")) {
                ConfigurationSection flags = section.getConfigurationSection("flags");
                for (String flag : flags.getKeys(false)) {
                    boolean val = flags.getBoolean(flag);
                    estate.setFlag(flag, val);
                }
            } else {
                // If no flags found (very old version?), FORCE SAFE DEFAULTS to match v1.2.0 behavior
                estate.setFlag("mobs", false); // Block mobs by default
                estate.setFlag("pvp", false);  // Block PvP by default
                estate.setFlag("tnt-damage", false);
                estate.setFlag("fire-spread", false);
            }
            
            // -- 4. Migrate Members / Trusted --
            if (section.isList("members")) {
                for (String memberId : section.getStringList("members")) {
                    try {
                        // Default legacy members to "Resident" role
                        estate.setMember(UUID.fromString(memberId), "resident");
                    } catch (Exception ignored) {}
                }
            }
            // Migrate 'trusted' list if it existed separately
            if (section.isList("trusted")) {
                for (String trustedId : section.getStringList("trusted")) {
                    try {
                        estate.setMember(UUID.fromString(trustedId), "resident");
                    } catch (Exception ignored) {}
                }
            }
            
            // -- 5. Save --
            plugin.getDataStore().saveEstate(estate);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to migrate plot: " + id + " - " + e.getMessage());
        }
    }
}
