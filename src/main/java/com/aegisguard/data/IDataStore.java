package com.aegisguard.data;

import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * IDataStore (Interface)
 * - This is a "contract" that all data storage systems must follow.
 * - This allows AegisGuard to switch between YML and SQL seamlessly.
 */
public interface IDataStore {

    /**
     * Initializes the data store (e.g., creates tables, loads YML).
     */
    void load();

    /**
     * Saves all pending changes to the data store.
     */
    void save();

    /**
     * Saves all pending changes immediately on the current thread.
     */
    void saveSync();

    /**
     * Checks if there are pending changes to be saved.
     */
    boolean isDirty();

    /**
     * Manually sets the dirty flag.
     */
    void setDirty(boolean dirty);

    // ----------------------------------------
    // --- PLOT ACCESSORS ---
    // ----------------------------------------

    /**
     * Gets all plots owned by a specific player.
     */
    List<Plot> getPlots(UUID owner);

    /**
     * Gets a single plot by its unique ID.
     */
    Plot getPlot(UUID owner, UUID plotId);

    /**
     * Gets all plots from all owners.
     */
    Collection<Plot> getAllPlots();

    /**
     * Gets all plots that are currently for sale.
     */
    Collection<Plot> getPlotsForSale();

    /**
     * Gets all plots that are currently for auction.
     */
    Collection<Plot> getPlotsForAuction();

    /**
     * Finds the plot at a specific Bukkit Location.
     */
    Plot getPlotAt(Location loc);

    /**
     * Checks if a new area overlaps with any existing plots.
     * * @param plotToIgnore The plot being checked (e.g., the current plot being resized).
     * The implementation must ignore this plot during the overlap check.
     */
    boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2);
    
    // ----------------------------------------
    // --- PLOT MODIFICATION ---
    // ----------------------------------------
    
    /**
     * Creates and stores a new plot.
     */
    void createPlot(UUID owner, Location c1, Location c2);

    /**
     * Adds a pre-made plot object to the store.
     */
    void addPlot(Plot plot);
    
    /**
     * Removes a single plot by its ID.
     */
    void removePlot(UUID owner, UUID plotId);
    
    /**
     * Removes all plots for a specific owner.
     */
    void removeAllPlots(UUID owner);
    
    /**
     * Adds or updates a player's role on a plot.
     */
    void addPlayerRole(Plot plot, UUID playerUUID, String role);
    
    /**
     * Removes a player's role from a plot.
     */
    void removePlayerRole(Plot plot, UUID playerUUID);

    /**
     * Atomically changes the owner of a plot.
     */
    void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName);

    /**
     * Runs the admin task to remove all plots owned by banned players.
     */
    void removeBannedPlots();
    
    // ----------------------------------------
    // --- WILDERNESS REVERT ---
    // ----------------------------------------
    
    /**
     * Logs a block change in the wilderness to the database.
     */
    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);
    
    /**
     * Queries and reverts a batch of expired wilderness blocks.
     */
    void revertWildernessBlocks(long timestamp, int limit);
}
