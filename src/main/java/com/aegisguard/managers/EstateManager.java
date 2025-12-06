package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EstateManager {

    private final AegisGuard plugin;
    private final Map<UUID, Estate> estates = new ConcurrentHashMap<>();

    public EstateManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void registerEstateFromLoad(Estate estate) {
        estates.put(estate.getId(), estate);
    }
    
    // Alias for register
    public void addEstate(Estate estate) {
        registerEstateFromLoad(estate);
    }

    // --- FIXED: Added removeEstate method ---
    public void removeEstate(UUID id) {
        estates.remove(id);
    }

    public Estate getEstate(UUID id) {
        return estates.get(id);
    }

    public Estate getEstateAt(Location loc) {
        if (loc == null) return null;
        for (Estate e : estates.values()) {
            if (e.getRegion().contains(loc)) return e;
        }
        return null;
    }

    public Collection<Estate> getAllEstates() {
        return estates.values();
    }

    public Estate createEstate(Player owner, Cuboid region, String name, boolean isGuild) {
        Estate estate = new Estate(
            UUID.randomUUID(),
            name,
            owner.getUniqueId(),
            isGuild,
            region.getWorld(),
            region
        );
        
        // Save to memory
        estates.put(estate.getId(), estate);
        
        // Save to DB
        plugin.getDataStore().saveEstate(estate);
        
        return estate;
    }
    
    // Used for Overlap Checks (Memory based for speed)
    public boolean isOverlapping(Cuboid region) {
        for (Estate e : estates.values()) {
            if (e.getWorld().equals(region.getWorld())) {
                if (e.getRegion().overlaps(region)) return true;
            }
        }
        return false;
    }

    public void transferOwnership(Estate estate, UUID newOwner, boolean isGuild) {
        // Logic handled by calling command/datastore update usually, 
        // but we can update the object here.
        // This requires setter on Estate if we want to mutate it, 
        // or we create a new one. For now, assuming you handle this in logic.
    }
}
