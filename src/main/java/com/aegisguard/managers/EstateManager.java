package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EstateManager {

    private final AegisGuard plugin;
    private final Map<UUID, Estate> estateMap = new HashMap<>();
    private final Map<Long, Set<UUID>> chunkCache = new HashMap<>();

    public EstateManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public Collection<Estate> getAllEstates() {
        return Collections.unmodifiableCollection(estateMap.values());
    }

    public Estate createEstate(OfflinePlayer owner, Cuboid region, String name, boolean isGuild) {
        if (isOverlapping(region)) {
            return null; 
        }

        UUID id = UUID.randomUUID();
        UUID ownerId = owner.getUniqueId();
        
        Estate newEstate = new Estate(id, name, ownerId, isGuild, region.getWorld(), region);

        registerEstate(newEstate);
        saveEstate(newEstate);

        return newEstate;
    }

    // --- THIS WAS THE MISSING METHOD ---
    /**
     * Registers an estate directly from storage without triggering a save.
     */
    public void registerEstateFromLoad(Estate estate) {
        estateMap.put(estate.getId(), estate);
        addToCache(estate);
    }
    // -----------------------------------

    public void deleteEstate(UUID estateId) {
        Estate estate = estateMap.remove(estateId);
        if (estate != null) {
            removeFromCache(estate);
            plugin.getDataStore().deleteEstate(estateId);
        }
    }
    
    public boolean resizeEstate(Estate estate, String direction, int amount) {
        // Placeholder logic for resize
        // In a full implementation, you would calculate new cuboid, check overlap, and update
        return true; 
    }

    public void transferOwnership(Estate estate, UUID newOwnerId, boolean isGuildOwner) {
        estate.setOwnerId(newOwnerId);
        estate.setIsGuild(isGuildOwner);
        saveEstate(estate);
    }

    public Estate getEstateAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        long chunkKey = getChunkKey(loc);
        Set<UUID> potentialEstates = chunkCache.get(chunkKey);

        if (potentialEstates == null || potentialEstates.isEmpty()) return null;

        for (UUID id : potentialEstates) {
            Estate estate = estateMap.get(id);
            if (estate != null && estate.getRegion().contains(loc)) {
                return estate;
            }
        }
        return null;
    }

    public Estate getEstate(UUID id) {
        return estateMap.get(id);
    }
    
    public List<Estate> getEstates(UUID ownerId) {
        List<Estate> list = new ArrayList<>();
        for (Estate e : estateMap.values()) {
            if (e.getOwnerId().equals(ownerId)) list.add(e);
        }
        return list;
    }

    private void registerEstate(Estate estate) {
        estateMap.put(estate.getId(), estate);
        addToCache(estate);
    }

    public boolean isOverlapping(Cuboid region) {
        return isOverlapping(region, null);
    }
    
    public boolean isOverlapping(Cuboid region, UUID ignoreEstateId) {
        for (Estate existing : estateMap.values()) {
            if (ignoreEstateId != null && existing.getId().equals(ignoreEstateId)) continue;
            if (existing.getRegion().overlaps(region)) return true;
        }
        return false;
    }

    private void addToCache(Estate estate) {
        for (Chunk chunk : getChunksInRegion(estate.getRegion())) {
            long key = getChunkKey(chunk);
            chunkCache.computeIfAbsent(key, k -> new HashSet<>()).add(estate.getId());
        }
    }

    private void removeFromCache(Estate estate) {
        for (Chunk chunk : getChunksInRegion(estate.getRegion())) {
            long key = getChunkKey(chunk);
            if (chunkCache.containsKey(key)) {
                chunkCache.get(key).remove(estate.getId());
                if (chunkCache.get(key).isEmpty()) chunkCache.remove(key);
            }
        }
    }

    private List<Chunk> getChunksInRegion(Cuboid region) {
        List<Chunk> chunks = new ArrayList<>();
        if (region.getWorld() == null) return chunks;
        
        int minX = region.getLowerNE().getBlockX() >> 4;
        int minZ = region.getLowerNE().getBlockZ() >> 4;
        int maxX = region.getUpperSW().getBlockX() >> 4;
        int maxZ = region.getUpperSW().getBlockZ() >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (region.getWorld().isChunkLoaded(x, z)) {
                    chunks.add(region.getWorld().getChunkAt(x, z));
                }
            }
        }
        return chunks;
    }

    private long getChunkKey(Location loc) { return getChunkKey(loc.getChunk()); }
    private long getChunkKey(Chunk chunk) { return (long) chunk.getX() & 0xffffffffL | ((long) chunk.getZ() & 0xffffffffL) << 32; }
    
    private void saveEstate(Estate e) {
        plugin.getDataStore().saveEstate(e);
    }
}
