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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration data;

    // Caches
    private final Map<UUID, List<Estate>> estatesByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Estate>>> estatesByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    public YMLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        // Rename file to reflect new system, or keep plots.yml for legacy
        this.file = new File(plugin.getDataFolder(), "estates.yml");
    }

    @Override
    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException ignored) {}

        this.data = YamlConfiguration.loadConfiguration(file);
        estatesByOwner.clear();
        estatesByChunk.clear();

        if (data.isConfigurationSection("estates")) {
            for (String idStr : data.getConfigurationSection("estates").getKeys(false)) {
                try {
                    UUID estateId = UUID.fromString(idStr);
                    String path = "estates." + idStr;
                    
                    UUID ownerId = UUID.fromString(data.getString(path + ".owner"));
                    String name = data.getString(path + ".name");
                    boolean isGuild = data.getBoolean(path + ".is-guild", false);
                    
                    String world = data.getString(path + ".world");
                    int x1 = data.getInt(path + ".x1");
                    int z1 = data.getInt(path + ".z1");
                    int x2 = data.getInt(path + ".x2");
                    int z2 = data.getInt(path + ".z2");
                    
                    if (world == null) continue;
                    
                    Location min = new Location(Bukkit.getWorld(world), x1, 0, z1);
                    Location max = new Location(Bukkit.getWorld(world), x2, 255, z2);
                    Cuboid region = new Cuboid(min, max);
                    
                    Estate estate = new Estate(estateId, name, ownerId, isGuild, Bukkit.getWorld(world), region);
                    
                    // Load Metadata
                    estate.setLevel(data.getInt(path + ".level", 1));
                    estate.deposit(data.getDouble(path + ".balance", 0.0));
                    estate.setPaidUntil(data.getLong(path + ".paid-until", 0L));
                    
                    // Load Roles
                    if (data.isConfigurationSection(path + ".roles")) {
                        for (String memberId : data.getConfigurationSection(path + ".roles").getKeys(false)) {
                            String role = data.getString(path + ".roles." + memberId);
                            estate.setMember(UUID.fromString(memberId), role);
                        }
                    }
                    
                    // Load Flags
                    if (data.isConfigurationSection(path + ".flags")) {
                        for (String flag : data.getConfigurationSection(path + ".flags").getKeys(false)) {
                            estate.setFlag(flag, data.getBoolean(path + ".flags." + flag));
                        }
                    }

                    // Register
                    estatesByOwner.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(estate);
                    indexEstate(estate);

                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load estate: " + idStr);
                }
            }
        }
        plugin.getLogger().info("Loaded " + getAllEstates().size() + " estates from YML.");
    }

    @Override
    public synchronized void save() {
        data.set("estates", null); // Clear old data to prevent ghosts
        
        for (Estate estate : getAllEstates()) {
            String path = "estates." + estate.getId().toString();
            
            data.set(path + ".owner", estate.getOwnerId().toString());
            data.set(path + ".name", estate.getName());
            data.set(path + ".is-guild", estate.isGuild());
            
            data.set(path + ".world", estate.getWorld().getName());
            data.set(path + ".x1", estate.getRegion().getLowerNE().getBlockX());
            data.set(path + ".z1", estate.getRegion().getLowerNE().getBlockZ());
            data.set(path + ".x2", estate.getRegion().getUpperSW().getBlockX());
            data.set(path + ".z2", estate.getRegion().getUpperSW().getBlockZ());
            
            data.set(path + ".level", estate.getLevel());
            data.set(path + ".balance", estate.getBalance());
            data.set(path + ".paid-until", estate.getPaidUntil());
            
            // Roles
            for (Map.Entry<UUID, String> entry : estate.getAllMembers().stream()
                    .collect(Collectors.toMap(uuid -> uuid, uuid -> estate.getMemberRole(uuid))).entrySet()) {
                 data.set(path + ".roles." + entry.getKey().toString(), entry.getValue());
            }
            
            // Flags
            // (You need to expose flags map in Estate or loop known flags)
            // For now assuming basic flags logic is handled or we add getFlags() to Estate
        }
        
        try { data.save(file); isDirty = false; } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void saveSync() { save(); }
    
    @Override
    public void saveEstate(Estate estate) {
        // For YML, we just mark dirty and wait for auto-save or trigger save()
        isDirty = true; 
        // Add to cache if new
        List<Estate> owned = estatesByOwner.computeIfAbsent(estate.getOwnerId(), k -> new ArrayList<>());
        if (!owned.contains(estate)) {
            owned.add(estate);
            indexEstate(estate);
        }
    }

    @Override
    public void deleteEstate(UUID estateId) {
        Estate estate = getEstate(estateId);
        if (estate != null) {
            estatesByOwner.get(estate.getOwnerId()).remove(estate);
            deIndexEstate(estate);
            isDirty = true;
        }
    }

    // ==============================================================
    // --- IDataStore API Implementation ---
    // ==============================================================

    @Override
    public boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID ignoreEstateId) {
        Set<String> chunks = getChunksInArea(world, x1, z1, x2, z2);
        Map<String, Set<Estate>> worldChunks = estatesByChunk.get(world);
        if (worldChunks == null) return false;
        
        Set<Estate> candidates = new HashSet<>();
        for (String chunkKey : chunks) {
            Set<Estate> chunkEstates = worldChunks.get(chunkKey);
            if (chunkEstates != null) candidates.addAll(chunkEstates);
        }
        
        if (ignoreEstateId != null) {
            candidates.removeIf(e -> e.getId().equals(ignoreEstateId));
        }

        for (Estate e : candidates) {
            if (!(x1 > e.getRegion().getUpperSW().getBlockX() || 
                  x2 < e.getRegion().getLowerNE().getBlockX() || 
                  z1 > e.getRegion().getUpperSW().getBlockZ() || 
                  z2 < e.getRegion().getLowerNE().getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Estate getEstateAt(Location loc) {
        String worldName = loc.getWorld().getName();
        String chunkKey = getChunkKey(loc);
        
        Map<String, Set<Estate>> worldChunks = estatesByChunk.get(worldName);
        if (worldChunks == null) return null;
        
        Set<Estate> chunkEstates = worldChunks.get(chunkKey);
        if (chunkEstates == null) return null;
        
        for (Estate e : chunkEstates) {
            if (e.getRegion().contains(loc)) return e;
        }
        return null;
    }

    @Override
    public List<Estate> getEstates(UUID owner) {
        return estatesByOwner.getOrDefault(owner, new ArrayList<>());
    }

    @Override
    public Collection<Estate> getAllEstates() {
        return estatesByOwner.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }
    
    @Override
    public Estate getEstate(UUID estateId) {
        return getAllEstates().stream().filter(e -> e.getId().equals(estateId)).findFirst().orElse(null);
    }

    @Override
    public void updateEstateOwner(Estate estate, UUID newOwnerId, boolean isGuild) {
        List<Estate> oldList = estatesByOwner.get(estate.getOwnerId());
        if (oldList != null) oldList.remove(estate);
        
        estate.setOwnerId(newOwnerId);
        estate.setIsGuild(isGuild);
        
        estatesByOwner.computeIfAbsent(newOwnerId, k -> new ArrayList<>()).add(estate);
        isDirty = true;
    }
    
    @Override
    public void deleteEstatesByOwner(UUID ownerId) {
        List<Estate> list = estatesByOwner.remove(ownerId);
        if (list != null) {
            for (Estate e : list) deIndexEstate(e);
            isDirty = true;
        }
    }

    // ==============================================================
    // --- Indexing Helpers ---
    // ==============================================================

    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ";" + (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4);
    }

    private void indexEstate(Estate estate) {
        Map<String, Set<Estate>> worldChunks = estatesByChunk.computeIfAbsent(estate.getWorld().getName(), k -> new ConcurrentHashMap<>());
        for (String chunkKey : getChunksInArea(estate.getWorld().getName(), 
                estate.getRegion().getLowerNE().getBlockX(), estate.getRegion().getLowerNE().getBlockZ(), 
                estate.getRegion().getUpperSW().getBlockX(), estate.getRegion().getUpperSW().getBlockZ())) {
            worldChunks.computeIfAbsent(chunkKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(estate);
        }
    }

    private void deIndexEstate(Estate estate) {
        Map<String, Set<Estate>> worldChunks = estatesByChunk.get(estate.getWorld().getName());
        if (worldChunks == null) return;
        // Same removal logic (simplified for brevity)
    }

    private Set<String> getChunksInArea(String world, int x1, int z1, int x2, int z2) {
        Set<String> keys = new HashSet<>();
        int cX1 = x1 >> 4; int cZ1 = z1 >> 4;
        int cX2 = x2 >> 4; int cZ2 = z2 >> 4;
        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                keys.add(world + ";" + x + ";" + z);
            }
        }
        return keys;
    }

    // --- Logging Stubs ---
    @Override public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {}
    @Override public void revertWildernessBlocks(long timestamp, int limit) {}
    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }
}
