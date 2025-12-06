package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration config;
    private boolean isDirty = false;

    public YMLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "estates.yml");
    }

    @Override
    public void load() {
        if (!file.exists()) return;
        config = YamlConfiguration.loadConfiguration(file);
        
        int count = 0;
        for (String idStr : config.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idStr);
                ConfigurationSection sec = config.getConfigurationSection(idStr);
                
                String name = sec.getString("name");
                UUID owner = UUID.fromString(sec.getString("owner"));
                boolean isGuild = sec.getBoolean("is_guild");
                
                String worldName = sec.getString("world");
                if (Bukkit.getWorld(worldName) == null) continue; 

                int x1 = sec.getInt("region.x1");
                int x2 = sec.getInt("region.x2");
                int y1 = sec.getInt("region.y1");
                int y2 = sec.getInt("region.y2");
                int z1 = sec.getInt("region.z1");
                int z2 = sec.getInt("region.z2");
                
                Location l1 = new Location(Bukkit.getWorld(worldName), x1, y1, z1);
                Location l2 = new Location(Bukkit.getWorld(worldName), x2, y2, z2);
                Cuboid region = new Cuboid(l1, l2);
                
                Estate estate = new Estate(id, name, owner, isGuild, Bukkit.getWorld(worldName), region);
                
                if (sec.isConfigurationSection("flags")) {
                    ConfigurationSection flags = sec.getConfigurationSection("flags");
                    for (String f : flags.getKeys(false)) {
                        estate.setFlag(f, flags.getBoolean(f));
                    }
                }

                if (sec.isConfigurationSection("members")) {
                    ConfigurationSection mems = sec.getConfigurationSection("members");
                    for (String mUuid : mems.getKeys(false)) {
                        estate.setMember(UUID.fromString(mUuid), mems.getString(mUuid));
                    }
                }
                
                plugin.getEstateManager().registerEstateFromLoad(estate);
                count++;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load estate: " + idStr);
            }
        }
        plugin.getLogger().info("Loaded " + count + " estates from YML.");
    }

    @Override
    public void save() {
        if (config == null) config = new YamlConfiguration();
        
        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            String id = estate.getId().toString();
            ConfigurationSection sec = config.createSection(id);
            
            sec.set("name", estate.getName());
            sec.set("owner", estate.getOwnerId().toString());
            sec.set("is_guild", estate.isGuild());
            sec.set("world", estate.getWorld().getName());
            
            Cuboid r = estate.getRegion();
            sec.set("region.x1", r.getLowerNE().getBlockX());
            sec.set("region.y1", r.getLowerNE().getBlockY());
            sec.set("region.z1", r.getLowerNE().getBlockZ());
            sec.set("region.x2", r.getUpperSW().getBlockX());
            sec.set("region.y2", r.getUpperSW().getBlockY());
            sec.set("region.z2", r.getUpperSW().getBlockZ());
            
            ConfigurationSection flags = sec.createSection("flags");
            flags.set("pvp", estate.getFlag("pvp"));
            flags.set("mobs", estate.getFlag("mobs"));
            flags.set("build", estate.getFlag("build"));
            flags.set("interact", estate.getFlag("interact"));
            flags.set("safe_zone", estate.getFlag("safe_zone"));
            flags.set("hunger", estate.getFlag("hunger"));
            flags.set("sleep", estate.getFlag("sleep"));

            ConfigurationSection mems = sec.createSection("members");
            for (UUID uid : estate.getAllMembers()) {
                mems.set(uid.toString(), estate.getMemberRole(uid));
            }
        }
        
        try {
            config.save(file);
            isDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void saveEstate(Estate estate) {
        isDirty = true;
        save();
    }

    @Override
    public void updateEstateOwner(Estate estate, UUID newOwner, boolean isGuild) {
        // YML saves everything on save(), so this alias ensures logic consistency
        saveEstate(estate);
    }

    @Override
    public void deleteEstate(UUID id) {
        if (config != null) {
            config.set(id.toString(), null);
            save();
        }
    }

    /**
     * NEW: Implements the missing deleteEstatesByOwner method required by IDataStore.
     * Used for banning players and wiping their data.
     */
    @Override
    public void deleteEstatesByOwner(UUID ownerId) {
        if (config == null) return;

        List<String> toRemove = new ArrayList<>();
        String targetOwner = ownerId.toString();

        for (String key : config.getKeys(false)) {
            String owner = config.getString(key + ".owner");
            if (owner != null && owner.equals(targetOwner)) {
                toRemove.add(key);
            }
        }

        for (String key : toRemove) {
            config.set(key, null);
        }
        
        if (!toRemove.isEmpty()) {
            save();
        }
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void saveSync() {
        save();
    }
    
    @Override
    public void revertWildernessBlocks(long checkTime, int limit) {
        // YML does not support block logging.
    }

    @Override
    public void logWildernessBlock(org.bukkit.Location loc, String type, String data, java.util.UUID player) {
        // No-op for YML
    }
}
