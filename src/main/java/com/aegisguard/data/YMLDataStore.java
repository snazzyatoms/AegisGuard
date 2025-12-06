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
import java.util.*;
import java.util.stream.Collectors;

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
                String ownerStr = sec.getString("owner");
                UUID owner = (ownerStr != null && !ownerStr.isEmpty()) ? UUID.fromString(ownerStr) : null;
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
                
                // Add to memory
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
            String id = estate.getPlotId().toString();
            ConfigurationSection sec = config.createSection(id);
            
            sec.set("name", estate.getDisplayName());
            sec.set("owner", estate.getOwnerId() != null ? estate.getOwnerId().toString() : "");
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
            estate.getFlags().forEach(flags::set);

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
    
    // --- FIXED: Added missing getEstateAt ---
    @Override
    public Estate getEstateAt(Location loc) {
        if (loc == null) return null;
        // In YML mode, all estates are in memory via EstateManager.
        // We iterate memory to find it.
        if (plugin.getEstateManager() != null) {
            for (Estate e : plugin.getEstateManager().getAllEstates()) {
                if (e.isInside(loc)) return e;
            }
        }
        return null;
    }

    @Override
    public void saveEstate(Estate estate) {
        isDirty = true;
        save();
    }

    // --- FIXED: Renamed from updateEstateOwner to match Interface ---
    @Override
    public void changeEstateOwner(Estate estate, UUID newOwner, String newName) {
        saveEstate(estate);
    }

    // --- FIXED: Renamed from deleteEstate to match Interface ---
    @Override
    public void removeEstate(UUID id) {
        if (config != null) {
            config.set(id.toString(), null);
            save();
        }
    }

    // --- FIXED: Signature matched to Interface ---
    @Override
    public boolean isAreaOverlapping(Estate ignore, String world, int x1, int z1, int x2, int z2) {
        if (config == null) return false;

        for (String key : config.getKeys(false)) {
            if (ignore != null && key.equals(ignore.getPlotId().toString())) continue;

            String w = config.getString(key + ".world");
            if (w == null || !w.equals(world)) continue;

            int ex1 = config.getInt(key + ".region.x1");
            int ex2 = config.getInt(key + ".region.x2");
            int ez1 = config.getInt(key + ".region.z1");
            int ez2 = config.getInt(key + ".region.z2");

            // AABB Check
            if (x1 <= ex2 && x2 >= ex1 && z1 <= ez2 && z2 >= ez1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isDirty() { return isDirty; }

    @Override
    public void setDirty(boolean dirty) { this.isDirty = dirty; }

    @Override
    public void saveSync() { save(); }
    
    // --- STUBS (Required to satisfy IDataStore interface) ---
    @Override public void revertWildernessBlocks(long timestamp, int limit) {}
    @Override public void logWildernessBlock(Location loc, String old, String newMat, UUID uuid) {}
    @Override public List<Estate> getEstates(UUID owner) { 
        return plugin.getEstateManager().getAllEstates().stream()
            .filter(e -> e.getOwnerId() != null && e.getOwnerId().equals(owner))
            .collect(Collectors.toList());
    }
    @Override public Collection<Estate> getAllEstates() { return plugin.getEstateManager().getAllEstates(); }
    @Override public Collection<Estate> getEstatesForSale() { return new ArrayList<>(); }
    @Override public Collection<Estate> getEstatesForAuction() { return new ArrayList<>(); }
    @Override public Estate getEstate(UUID owner, UUID plotId) { return plugin.getEstateManager().getEstate(plotId); }
    @Override public void createEstate(UUID owner, Location c1, Location c2) {}
    @Override public void addEstate(Estate estate) { saveEstate(estate); }
    @Override public void addPlayerRole(Estate estate, UUID uuid, String role) { saveEstate(estate); }
    @Override public void removePlayerRole(Estate estate, UUID uuid) { saveEstate(estate); }
    @Override public void removeBannedEstates() {}
    @Override public void removeAllPlots(UUID owner) { deleteEstatesByOwner(owner); }
    
    public void deleteEstatesByOwner(UUID ownerId) {
         if (config == null) return;
         List<String> toRemove = new ArrayList<>();
         for (String key : config.getKeys(false)) {
             String owner = config.getString(key + ".owner");
             if (owner != null && owner.equals(ownerId.toString())) {
                 toRemove.add(key);
             }
         }
         for (String key : toRemove) config.set(key, null);
         if (!toRemove.isEmpty()) save();
    }
}
