package com.aegisguard.data;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * IDataStore (Interface)
 * - This is a "contract" that all data storage systems must follow.
 * - This allows AegisGuard to switch between YML and SQL seamlessly.
 *
 * --- UPGRADE NOTES (Ultimate) ---
 * - Added getPlotsForSale() for the marketplace.
 * - Added getPlotsForAuction() for the auction system.
 */
public interface IDataStore {

    /**
     * Initializes the data store (e.g., creates tables, loads YML).
     */
    void load();

    /**
     * Saves all pending changes to the data store.
     * This is intended to be run ASYNCHRONOUSLY.
     */
    void save();

    /**
     * Saves all pending changes immediately on the current thread.
     * Used ONLY for onDisable().
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
     * --- NEW ---
     * Gets all plots that are currently for sale.
     * @return A collection of plots.
     */
    Collection<Plot> getPlotsForSale();

    /**
     * --- NEW ---
     * Gets all plots that are currently for auction.
     * @return A collection of plots.
     */
    Collection<Plot> getPlotsForAuction();

    /**
     * Finds the plot at a specific Bukkit Location.
     * This MUST be fast (using a spatial hash or SQL query).
     */
    Plot getPlotAt(Location loc);

    /**
     * Checks if a new area overlaps with any existing plots.
     */
    boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2);
    
    /**
     * Creates and stores a new plot.
     */
    void createPlot(UUID owner, Location c1, Location c2);

    /**
     * Adds a pre-made plot object to the store.
     * Used by the resize command.
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
     * Runs the admin task to remove all plots owned by banned players.
     */
    void removeBannedPlots();
}
