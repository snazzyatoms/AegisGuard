package com.aegisguard.data;

import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * IDataStore (Interface) - v1.2.2
 *
 * Core contract for PLOT storage.
 * Implementations:
 *  - MySQLDataStore (remote database)
 *  - YMLDataStore   (flat-file)
 *
 * Responsibilities:
 *  - Load / save all Plot data (including flags, roles, zones, auctions, etc).
 *  - Keep SQL and YML behavior identical from the plugin’s perspective.
 *
 * NOTE:
 *  - Plots themselves (com.aegisguard.data.Plot) now include:
 *      * Server Zones (SERVER_OWNER_UUID)
 *      * Full flag map (with new flags like hunger/sleep)
 *      * Zones, leveling, market/auction, cosmetics, etc.
 *    The store must persist ALL of that, but the interface
 *    does not need new methods each time Plot gains a field.
 */
public interface IDataStore {

    // ----------------------------------------
    // --- LIFECYCLE / FLUSHING --------------
    // ----------------------------------------

    /**
     * Initializes the data store (creates tables / loads files).
     * Called during plugin startup.
     */
    void load();

    /**
     * Saves all pending changes to the data store.
     * Implementations may perform this asynchronously.
     */
    void save();

    /**
     * Saves all pending changes immediately on the main thread.
     * Used on server shutdown when async operations are no longer safe.
     */
    void saveSync();

    /**
     * Checks whether there are unsaved changes.
     */
    boolean isDirty();

    /**
     * Manually adjust the dirty flag.
     */
    void setDirty(boolean dirty);

    // ----------------------------------------
    // --- PLOT ACCESSORS ---------------------
    // ----------------------------------------

    /**
     * Gets all plots owned by a specific player.
     *
     * @param owner UUID of the player
     * @return List of plots (may be empty, never null)
     */
    List<Plot> getPlots(UUID owner);

    /**
     * Gets a single plot by its unique ID for a given owner.
     * (Legacy signature kept for backwards compatibility.)
     *
     * @param owner  UUID of the owner
     * @param plotId UUID of the plot
     * @return Plot or null if not found
     */
    Plot getPlot(UUID owner, UUID plotId);

    /**
     * Gets all plots from all owners (including server zones).
     */
    Collection<Plot> getAllPlots();

    /**
     * Gets all plots that are currently marked as "for sale".
     */
    Collection<Plot> getPlotsForSale();

    /**
     * Gets all plots that are currently in auction.
     */
    Collection<Plot> getPlotsForAuction();

    /**
     * Finds the first plot covering the given Bukkit Location.
     * (Uses the plot's 2D X/Z bounds with Bedrock-to-Sky semantics.)
     */
    Plot getPlotAt(Location loc);

    /**
     * Checks if a rectangular area (world + 2D coordinates) overlaps
     * with any existing plot, excluding an optional "self" plot.
     *
     * @param plotToIgnore The plot being resized/moved (may be null)
     * @param world        World name
     * @param x1           Lower X
     * @param z1           Lower Z
     * @param x2           Upper X
     * @param z2           Upper Z
     * @return true if an overlap exists, false otherwise
     */
    boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2);

    // ----------------------------------------
    // --- PLOT MODIFICATION ------------------
    // ----------------------------------------

    /**
     * Creates and stores a new plot from two corner Locations.
     * Implementations should:
     *  - Normalize min/max X/Z
     *  - Initialize default flags / roles as per Plot constructor
     *  - Mark data as dirty
     */
    void createPlot(UUID owner, Location c1, Location c2);

    /**
     * Adds a pre-existing Plot object to the store.
     * Used for loading from SQL/YML or importing dumps.
     */
    void addPlot(Plot plot);

    /**
     * Saves a specific plot's data immediately.
     * Implementation may be async or sync depending on design,
     * but must ensure the in-memory <-> persistent state matches.
     */
    void savePlot(Plot plot);

    /**
     * Removes a single plot by its ID for a given owner.
     */
    void removePlot(UUID owner, UUID plotId);

    /**
     * Removes all plots for a specific owner (used when wiping a player).
     */
    void removeAllPlots(UUID owner);

    /**
     * Adds or updates a player's role on a plot.
     * The Plot object itself should be updated AND persisted.
     */
    void addPlayerRole(Plot plot, UUID playerUUID, String role);

    /**
     * Removes a player's role from a plot.
     * The Plot object itself should be updated AND persisted.
     */
    void removePlayerRole(Plot plot, UUID playerUUID);

    /**
     * Atomically changes the owner of a plot.
     * Implementations should:
     *  - Update owner UUID/Name
     *  - Reset roles/bans/likes as per Plot.internalSetOwner()
     *  - Persist the changes
     */
    void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName);

    /**
     * Runs the admin maintenance task to remove all plots owned by banned players.
     * Implementations may:
     *  - Query the ban list
     *  - Delete plots for banned UUIDs
     *  - Skip server zones (SERVER_OWNER_UUID)
     */
    void removeBannedPlots();

    // ----------------------------------------
    // --- WILDERNESS REVERT ------------------
    // ----------------------------------------

    /**
     * Logs a block change in the wilderness (unclaimed land).
     * Implementations should record enough data to revert later:
     *  - World, XYZ
     *  - Old / new material
     *  - Player UUID (for auditing)
     */
    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);

    /**
     * Queries and reverts a batch of expired wilderness blocks.
     *
     * @param timestamp Only revert entries older than this timestamp
     * @param limit     Max number of changes to revert in this batch
     */
    void revertWildernessBlocks(long timestamp, int limit);
}
