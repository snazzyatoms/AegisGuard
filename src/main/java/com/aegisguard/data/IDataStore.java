package com.aegisguard.data;

import com.aegisguard.flags.TriState;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IDataStore {

    // --------------------------------------------------
    // LIFECYCLE
    // --------------------------------------------------

    /** Load all plots and related state into memory/caches. */
    void load();

    /**
     * Save the entire datastore state.
     * SQL implementations may queue async writes, but must ensure correctness.
     */
    void save();

    /**
     * Save the entire datastore state synchronously.
     * When this returns, data must be persisted to disk/DB.
     */
    void saveSync();

    // --------------------------------------------------
    // PLOT SAVING
    // --------------------------------------------------

    /**
     * Save a single plot.
     * SQL implementations may queue async writes.
     * YML implementations typically save immediately (sync).
     */
    void savePlot(Plot plot);

    /**
     * Save a single plot synchronously.
     * When this returns, the plot must be persisted to disk/DB.
     */
    void savePlotSync(Plot plot);

    // --------------------------------------------------
    // PLOTS
    // --------------------------------------------------

    void createPlot(UUID owner, Location c1, Location c2);
    void addPlot(Plot plot);
    void removePlot(UUID owner, UUID plotId);
    void removeAllPlots(UUID owner);

    /**
     * Transfer ownership of a plot to a new owner.
     * Implementations must ensure caches stay in sync and prior owner privileges do not persist.
     */
    void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName);

    void removeBannedPlots();

    // --------------------------------------------------
    // ROLES
    // --------------------------------------------------

    void addPlayerRole(Plot plot, UUID uuid, String role);
    void removePlayerRole(Plot plot, UUID uuid);

    // --------------------------------------------------
    // ROLE FLAG OVERRIDES
    // --------------------------------------------------

    TriState getRoleFlagState(Plot plot, String roleId, String flagKey);
    void setRoleFlagState(Plot plot, String roleId, String flagKey, TriState state);

    // --------------------------------------------------
    // WILDERNESS
    // --------------------------------------------------

    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);
    void revertWildernessBlocks(long timestamp, int limit);

    // --------------------------------------------------
    // DIRTY FLAG
    // --------------------------------------------------

    boolean isDirty();
    void setDirty(boolean dirty);

    // --------------------------------------------------
    // QUERIES
    // --------------------------------------------------

    List<Plot> getPlots(UUID owner);
    Plot getPlot(UUID owner, UUID plotId);

    Collection<Plot> getAllPlots();
    Collection<Plot> getPlotsForSale();
    Collection<Plot> getPlotsForAuction();

    Plot getPlotAt(Location loc);
    boolean isAreaOverlapping(Plot ignore, String world, int x1, int z1, int x2, int z2);

    /**
     * Optional shutdown hook.
     * SQL implementations should flush pending async DB tasks and close pools safely.
     * YML implementations can simply saveSync().
     *
     * IMPORTANT: After this returns, no queued writes should still be pending.
     */
    default void shutdown() { }
}
