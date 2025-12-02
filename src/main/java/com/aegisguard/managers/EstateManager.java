package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.objects.Cuboid;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EstateManager {

    private final JavaPlugin plugin;
    
    // Main Storage: Estate UUID -> Estate Object
    private final Map<UUID, Estate> estateMap = new HashMap<>();

    // ⚡ ZERO LAG CACHE: Chunk Key -> List of Estates in that chunk
    // This allows us to find claims instantly without checking the whole server.
    private final Map<Long, Set<UUID>> chunkCache = new HashMap<>();

    public EstateManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // In a real scenario, you would call loadEstatesFromDatabase() here.
    }

    /**
     * Create a new Estate and register it.
     */
    public Estate createEstate(Player owner, Cuboid region, String name, boolean isGuild) {
        // 1. Check for Overlaps (Critical!)
        if (isOverlapping(region)) {
            return null; // Logic in command handler should warn user
        }

        // 2. Create Object
        UUID id = UUID.randomUUID();
        UUID ownerId = isGuild ? getGuildId(owner) : owner.getUniqueId(); // Placeholder for Guild logic
        
        Estate newEstate = new Estate(id, name, ownerId, isGuild, region.getWorld(), region);

        // 3. Register in Memory
        registerEstate(newEstate);
        
        // 4. Save to DB (Async in production)
        saveEstate(newEstate);

        return newEstate;
    }

    /**
     * Deletes an estate permanently.
     */
    public void deleteEstate(UUID estateId) {
        Estate estate = estateMap.remove(estateId);
        if (estate != null) {
            removeFromCache(estate);
            // deleteFromDatabase(estateId);
        }
    }

    /**
     * The most important method: "Is there a claim here?"
     * Uses the Chunk Cache for instant lookup.
     */
    public Estate getEstateAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        long chunkKey = getChunkKey(loc);
        Set<UUID> potentialEstates = chunkCache.get(chunkKey);

        if (potentialEstates == null || potentialEstates.isEmpty()) {
            return null; // Nothing in this chunk! Fast fail.
        }

        // Only check estates known to be in this chunk
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

    // ==========================================================
    // 🧩 INTERNAL LOGIC & CACHING
    // ==========================================================

    private void registerEstate(Estate estate) {
        estateMap.put(estate.getId(), estate);
        addToCache(estate);
    }

    private boolean isOverlapping(Cuboid region) {
        // Loop through all chunks this new region touches
        // If any existing estate is there, check strict overlap.
        // (Simplified check for now - loop all is safer for creation since it happens rarely)
        for (Estate existing : estateMap.values()) {
            if (existing.getWorld().equals(region.getWorld())) {
                // Simple AABB overlap check could go here
                // For now, checking if corners are inside is a basic test
                if (existing.getRegion().contains(region.getLowerNE()) || 
                    existing.getRegion().contains(region.getUpperSW())) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- Chunk Cache Helpers ---

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
    
    // --- Placeholder Helpers ---
    private UUID getGuildId(Player p) {
        // You will hook this into your AllianceManager later
        return UUID.randomUUID(); 
    }
    
    private void saveEstate(Estate e) {
        // Hook into StorageManager
    }
}
