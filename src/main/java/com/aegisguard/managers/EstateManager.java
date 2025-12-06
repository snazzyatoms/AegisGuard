package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EstateManager {

    private final AegisGuard plugin;
    private final Map<UUID, Estate> estates = new ConcurrentHashMap<>();

    public EstateManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void registerEstateFromLoad(Estate estate) {
        estates.put(estate.getId(), estate);
    }
    
    public void addEstate(Estate estate) {
        registerEstateFromLoad(estate);
    }

    public void removeEstate(UUID id) {
        estates.remove(id);
    }
    
    // Alias to fix "cannot find symbol deleteEstate"
    public void deleteEstate(UUID id) {
        removeEstate(id);
    }

    public Estate getEstate(UUID id) {
        return estates.get(id);
    }

    // Fixed: Added getEstates(UUID)
    public List<Estate> getEstates(UUID ownerId) {
        return estates.values().stream()
                .filter(e -> e.getOwnerId() != null && e.getOwnerId().equals(ownerId))
                .collect(Collectors.toList());
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
        estates.put(estate.getId(), estate);
        plugin.getDataStore().saveEstate(estate);
        return estate;
    }

    // Fixed: Added resizeEstate logic
    public boolean resizeEstate(Estate estate, String direction, int amount) {
        Cuboid oldRegion = estate.getRegion();
        Cuboid newRegion = oldRegion.expand(direction, amount); 
        
        if (newRegion == null) return false;
        
        // Overlap Check (Ignore self)
        for (Estate other : getAllEstates()) {
            if (other.equals(estate)) continue;
            if (other.getWorld().equals(estate.getWorld())) {
                if (other.getRegion().overlaps(newRegion)) return false;
            }
        }
        
        // Apply (Note: You need a setRegion method or recreate estate, assuming Cuboid is mutable or swapped)
        // Since Estate.region is final, we usually do internal mutation or reflection. 
        // For now, let's assume Cuboid has mutable bounds or we cheat:
        // Ideally: estate.setRegion(newRegion);
        // If Region is final, we have to recreate the Estate or update the Cuboid internal values.
        
        // Temporary fix for compilation:
        oldRegion.setBounds(newRegion.getLowerNE(), newRegion.getUpperSW());
        
        plugin.getDataStore().saveEstate(estate);
        return true;
    }
}
