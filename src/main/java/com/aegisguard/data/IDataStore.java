package com.aegisguard.data;

import com.aegisguard.flags.TriState;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IDataStore {

    void load();
    void save();
    void saveSync();

    boolean isDirty();
    void setDirty(boolean dirty);

    List<Plot> getPlots(UUID owner);
    Plot getPlot(UUID owner, UUID plotId);

    Collection<Plot> getAllPlots();
    Collection<Plot> getPlotsForSale();
    Collection<Plot> getPlotsForAuction();

    Plot getPlotAt(Location loc);
    boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2);

    void createPlot(UUID owner, Location c1, Location c2);
    void addPlot(Plot plot);

    void savePlot(Plot plot);

    /**
     * NEW: Crash-safe plot save.
     * Default falls back to savePlot() for stores that already save sync (YML).
     * SQLDataStore should override and commit immediately.
     */
    default void savePlotSync(Plot plot) {
        savePlot(plot);
    }

    void removePlot(UUID owner, UUID plotId);
    void removeAllPlots(UUID owner);

    void addPlayerRole(Plot plot, UUID playerUUID, String role);
    void removePlayerRole(Plot plot, UUID playerUUID);

    void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName);
    void removeBannedPlots();

    TriState getRoleFlagState(Plot plot, String roleName, String flagKey);
    void setRoleFlagState(Plot plot, String roleName, String flagKey, TriState state);

    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);
    void revertWildernessBlocks(long timestamp, int limit);
}
