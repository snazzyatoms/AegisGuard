package com.aegisguard.data;

import com.aegisguard.objects.Estate; // v1.3.0 Object
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

    /**
     * Initializes the data store (creates tables/files).
     */
    void load();

    /**
     * Saves all pending changes (Async safe).
     */
    void save();

    /**
     * Saves immediately on the main thread (for shutdown).
     */
    void saveSync();

    boolean isDirty();
    void setDirty(boolean dirty);

    // ----------------------------------------
    // --- ESTATE ACCESSORS (v1.3.0) ---
    // ----------------------------------------

    /**
     * Gets all estates owned by a specific player.
     */
    List<Estate> getEstates(UUID owner);

    /**
     * Gets a single estate by its unique ID.
     */
    Estate getEstate(UUID estateId);

    /**
     * Gets all estates currently loaded.
     */
    Collection<Estate> getAllEstates();

    /**
     * Finds the estate at a specific location.
     */
    Estate getEstateAt(Location loc);

    /**
     * Checks if a region overlaps with existing estates.
     * @param ignoreEstateId The ID of the estate being resized (to skip self-check).
     */
    boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID ignoreEstateId);
    
    // ----------------------------------------
    // --- MODIFICATION ---
    // ----------------------------------------

    /**
     * Saves a new or updated estate to the database.
     */
    void saveEstate(Estate estate);
    
    /**
     * Deletes an estate permanently.
     */
    void deleteEstate(UUID estateId);
    
    /**
     * Deletes all estates owned by a specific player.
     */
    void deleteEstatesByOwner(UUID ownerId);

    /**
     * Updates the owner of an estate (e.g., Player -> Guild).
     */
    void updateEstateOwner(Estate estate, UUID newOwnerId, boolean isGuild);

    // ----------------------------------------
    // --- WILDERNESS REVERT ---
    // ----------------------------------------
    
    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);
    
    void revertWildernessBlocks(long timestamp, int limit);
    
    // ----------------------------------------
    // --- LEGACY SUPPORT (Optional) ---
    // ----------------------------------------
    // These methods mimic the old v1.2 API to prevent compile errors
    // in old classes you haven't fully deleted yet.
    // Ideally, you should delete these once the conversion is 100% done.
    
    default void addPlot(Estate e) { saveEstate(e); }
    default void removePlot(UUID owner, UUID id) { deleteEstate(id); }
}
