package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

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
                if (Bukkit.getWorld(worldName) == null) continue; // Skip invalid worlds

                // Load Region
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
                
                // Load Flags
                if (sec.isConfigurationSection("flags")) {
                    ConfigurationSection flags = sec.getConfigurationSection("flags");
                    for (String f : flags.getKeys(false)) {
                        estate.setFlag(f, flags.getBoolean(f));
                    }
                }

                // Load Members
                if (sec.isConfigurationSection("members")) {
                    ConfigurationSection mems = sec.getConfigurationSection("members");
                    for (String mUuid : mems.getKeys(false)) {
                        estate.setMember(UUID.fromString(mUuid), mems.getString(mUuid));
                    }
                }
                
                // Register to Manager
                // We access the map directly or via a register method that doesn't trigger a save loop
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
        
        // Loop all estates in memory
        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            String id = estate.getId().toString();
            ConfigurationSection sec = config.createSection(id);
            
            sec.set("name", estate.getName());
            sec.set("owner", estate.getOwnerId().toString());
            sec.set("is_guild", estate.isGuild());
            sec.set("world", estate.getWorld().getName());
            
            // Region
            Cuboid r = estate.getRegion();
            sec.set("region.x1", r.getLowerNE().getBlockX());
            sec.set("region.y1", r.getLowerNE().getBlockY());
            sec.set("region.z1", r.getLowerNE().getBlockZ());
            sec.set("region.x2", r.getUpperSW().getBlockX());
            sec.set("region.y2", r.getUpperSW().getBlockY());
            sec.set("region.z2", r.getUpperSW().getBlockZ());
            
            // Flags
            ConfigurationSection flags = sec.createSection("flags");
            // We need to iterate known flags or store them in a way we can loop
            // For now, save common ones or if you added a getFlags() map to Estate, use that.
            // Assuming you added `public Map<String, Boolean> getFlags()` to Estate.java:
            // for (Map.Entry<String, Boolean> entry : estate.getFlags().entrySet()) {
            //    flags.set(entry.getKey(), entry.getValue());
            // }
            // Fallback manual save for now:
            flags.set("pvp", estate.getFlag("pvp"));
            flags.set("mobs", estate.getFlag("mobs"));
            flags.set("build", estate.getFlag("build"));
            flags.set("interact", estate.getFlag("interact"));
            flags.set("safe_zone", estate.getFlag("safe_zone"));
            flags.set("hunger", estate.getFlag("hunger"));
            flags.set("sleep", estate.getFlag("sleep"));

            // Members
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
        // Mark dirty to save on auto-save, or save immediately
        // Ideally, update the specific section in 'config' object in memory
        isDirty = true;
        // For safety in development, force save:
        save();
    }

    @Override
    public void deleteEstate(UUID id) {
        if (config != null) {
            config.set(id.toString(), null);
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
        // YML does not support block logging. This feature is SQL only.
    }
}
```

### 3. 🛠️ `EstateManager.java` (Add Register Method)
I added `registerEstateFromLoad` so the DataStore can populate the map without triggering a "New Estate" save loop.

**Location:** `src/main/java/com/aegisguard/managers/EstateManager.java`

```java
    // Add this method to EstateManager.java
    public void registerEstateFromLoad(Estate estate) {
        estateMap.put(estate.getId(), estate);
        addToCache(estate);
    }
