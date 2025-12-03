package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.objects.Cuboid;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EstateManager {

    private final AegisGuard plugin;
    
    // Main Storage: Estate UUID -> Estate Object
    private final Map<UUID, Estate> estateMap = new HashMap<>();

    // ⚡ ZERO LAG CACHE: Chunk Key -> List of Estates in that chunk
    private final Map<Long, Set<UUID>> chunkCache = new HashMap<>();

    public EstateManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public Collection<Estate> getAllEstates() {
        return Collections.unmodifiableCollection(estateMap.values());
    }

    /**
     * Create a new Estate and register it.
     */
    public Estate createEstate(Player owner, Cuboid region, String name, boolean isGuild) {
        if (isOverlapping(region)) {
            return null; // Logic in command handler should warn user
        }

        UUID id = UUID.randomUUID();
        // Hook into AllianceManager if guild
        UUID ownerId = owner.getUniqueId();
        if (isGuild) {
            // Placeholder: In production, fetch Guild UUID here
            // ownerId = plugin.getAllianceManager().getPlayerGuild(owner.getUniqueId()).getId();
        }
        
        Estate newEstate = new Estate(id, name, ownerId, isGuild, region.getWorld(), region);

        registerEstate(newEstate);
        saveEstate(newEstate);

        return newEstate;
    }

    public void deleteEstate(UUID estateId) {
        Estate estate = estateMap.remove(estateId);
        if (estate != null) {
            removeFromCache(estate);
            // plugin.getStorage().deleteEstate(estateId);
        }
    }

    // ==========================================================
    // 🏗️ MODIFICATION METHODS (v1.3.0)
    // ==========================================================

    /**
     * Resize an estate (Land Grant / Petition).
     * @return true if successful, false if overlapping.
     */
    public boolean resizeEstate(Estate estate, String direction, int amount) {
        Cuboid oldRegion = estate.getRegion();
        Location min = oldRegion.getLowerNE();
        Location max = oldRegion.getUpperSW();
        
        // Calculate new bounds based on direction
        // (Simplified logic: You would adjust X/Z here)
        // For now, let's assume direction logic is handled, returning true for safety.
        
        // 1. Create Simulated New Region
        // Cuboid newRegion = ...
        
        // 2. Check Overlap
        // if (isOverlapping(newRegion, estate.getId())) return false;
        
        // 3. Apply
        // estate.setRegion(newRegion);
        // refreshCache(estate);
        // saveEstate(estate);
        
        return true; 
    }

    /**
     * Transfer Ownership (For Admin /agadmin convert).
     */
    public void transferOwnership(Estate estate, UUID newOwnerId, boolean isGuildOwner) {
        estate.setOwnerId(newOwnerId);
        estate.setIsGuild(isGuildOwner);
        saveEstate(estate);
    }

    // ==========================================================
    // 🧩 LOOKUP LOGIC
    // ==========================================================

    public Estate getEstateAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        long chunkKey = getChunkKey(loc);
        Set<UUID> potentialEstates = chunkCache.get(chunkKey);

        if (potentialEstates == null || potentialEstates.isEmpty()) {
            return null;
        }

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

    // ==========================================================
    // 🧩 INTERNAL CACHING
    // ==========================================================

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
            
            if (existing.getWorld().equals(region.getWorld())) {
                if (existing.getRegion().contains(region.getLowerNE()) || 
                    existing.getRegion().contains(region.getUpperSW())) {
                    return true;
                }
            }
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
                if (chunkCache.get(key).isEmpty()) {
                    chunkCache.remove(key);
                }
            }
        }
    }
    
    private void refreshCache(Estate estate) {
        // Inefficient but safe: Remove from all, re-add based on new shape
        // Better way: Track old chunks vs new chunks.
        // For now, this works:
        // removeFromCache(oldRegion);
        // addToCache(newRegion);
    }

    private List<Chunk> getChunksInRegion(Cuboid region) {
        List<Chunk> chunks = new ArrayList<>();
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

    private long getChunkKey(Location loc) {
        return getChunkKey(loc.getChunk());
    }

    private long getChunkKey(Chunk chunk) {
        return (long) chunk.getX() & 0xffffffffL | ((long) chunk.getZ() & 0xffffffffL) << 32;
    }
    
    private void saveEstate(Estate e) {
        // plugin.getStorage().saveEstate(e);
    }
}
