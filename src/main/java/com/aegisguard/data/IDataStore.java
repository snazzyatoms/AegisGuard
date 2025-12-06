package com.aegisguard.data;

import com.aegisguard.objects.Estate;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * IDataStore (Interface)
 * - The contract for saving/loading ESTATES.
 * - Updated for v1.3.0 (Replaced 'Plot' with 'Estate').
 */
public interface IDataStore {

    void load();
    void save();
    void saveSync();
    boolean isDirty();
    
    // --- READS ---
    List<Estate> getEstates(UUID owner);
    Estate getEstate(UUID estateId);
    Collection<Estate> getAllEstates();
    Estate getEstateAt(Location loc);
    boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID ignoreEstateId);

    // --- WRITES ---
    void saveEstate(Estate estate);
    void deleteEstate(UUID estateId);
    void deleteEstatesByOwner(UUID ownerId);
    void updateEstateOwner(Estate estate, UUID newOwnerId, boolean isGuild);

    // --- LOGGING ---
    void logWildernessBlock(Location loc, String type, String data, UUID playerUUID);
    void revertWildernessBlocks(long timestamp, int limit);
}
