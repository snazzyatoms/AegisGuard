package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Location;
import org.bukkit.World;
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
            if (e.getRegion() != null && e.getRegion().contains(loc)) {
                return e;
            }
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

    /**
     * Resize an estate region in a given direction by a number of blocks.
     * Direction is treated as a string (e.g. "NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN").
     */
    public boolean resizeEstate(Estate estate, String direction, int amount) {
        if (estate == null || estate.getRegion() == null || amount == 0) {
            return false;
        }

        Cuboid oldRegion = estate.getRegion();
        Cuboid newRegion = expandRegion(oldRegion, direction, amount);
        if (newRegion == null) {
            return false;
        }

        // Overlap Check (Ignore self)
        for (Estate other : getAllEstates()) {
            if (other.equals(estate)) continue;
            if (other.getWorld() != null
                    && other.getWorld().equals(estate.getWorld())
                    && other.getRegion() != null
                    && other.getRegion().overlaps(newRegion)) {
                return false;
            }
        }

        // Apply new region to the estate
        // Assumes Estate has a setRegion(Cuboid) method.
        estate.setRegion(newRegion);
        plugin.getDataStore().saveEstate(estate);
        return true;
    }

    /**
     * Helper used by SelectionService and others:
     * Check if a given region overlaps any existing estate.
     */
    public boolean isOverlapping(Cuboid region) {
        if (region == null) return false;
        for (Estate estate : estates.values()) {
            if (estate.getRegion() != null
                    && estate.getWorld() != null
                    && estate.getWorld().equals(region.getWorld())
                    && estate.getRegion().overlaps(region)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Internal helper: expand a Cuboid by direction & amount into a NEW Cuboid.
     * This replaces the old Cuboid#expand(String,int) + setBounds(...) calls.
     */
    private Cuboid expandRegion(Cuboid original, String direction, int amount) {
        if (original == null || direction == null || amount == 0) return original;

        World world = original.getWorld();
        if (world == null) return original;

        Location l1 = original.getLowerNE();
        Location l2 = original.getUpperSW();

        double minX = Math.min(l1.getX(), l2.getX());
        double maxX = Math.max(l1.getX(), l2.getX());
        double minY = Math.min(l1.getY(), l2.getY());
        double maxY = Math.max(l1.getY(), l2.getY());
        double minZ = Math.min(l1.getZ(), l2.getZ());
        double maxZ = Math.max(l1.getZ(), l2.getZ());

        String dir = direction.toUpperCase(Locale.ROOT);

        switch (dir) {
            case "NORTH":
                // negative Z
                minZ -= amount;
                break;
            case "SOUTH":
                // positive Z
                maxZ += amount;
                break;
            case "WEST":
                // negative X
                minX -= amount;
                break;
            case "EAST":
                // positive X
                maxX += amount;
                break;
            case "UP":
                maxY += amount;
                break;
            case "DOWN":
                minY -= amount;
                break;
            default:
                // Unknown direction, no change
                return original;
        }

        Location newMin = new Location(world, minX, minY, minZ);
        Location newMax = new Location(world, maxX, maxY, maxZ);

        // Assumes Cuboid has a constructor taking two Locations.
        return new Cuboid(newMin, newMax);
    }
}
