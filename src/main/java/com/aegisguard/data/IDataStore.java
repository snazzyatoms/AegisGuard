package com.aegisguard.data;

import com.aegisguard.flags.TriState;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IDataStore {

    // lifecycle
    void load();
    void save();
    void saveSync();

    // plot saving
    void savePlot(Plot plot);
    void savePlotSync(Plot plot);

    // plots
    void createPlot(UUID owner, Location c1, Location c2);
    void addPlot(Plot plot);
    void removePlot(UUID owner, UUID plotId);
    void removeAllPlots(UUID owner);
    void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName);
    void removeBannedPlots();

    // roles
    void addPlayerRole(Plot plot, UUID uuid, String role);
    void removePlayerRole(Plot plot, UUID uuid);

    // role flag overrides
    TriState getRoleFlagState(Plot plot, String roleId, String flagKey);
    void setRoleFlagState(Plot plot, String roleId, String flagKey, TriState state);

    // wilderness
    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);
    void revertWildernessBlocks(long timestamp, int limit);

    // dirty flag
    boolean isDirty();
    void setDirty(boolean dirty);

    // queries
    List<Plot> getPlots(UUID owner);
    Plot getPlot(UUID owner, UUID plotId);

    Collection<Plot> getAllPlots();
    Collection<Plot> getPlotsForSale();
    Collection<Plot> getPlotsForAuction();

    Plot getPlotAt(Location loc);
    boolean isAreaOverlapping(Plot ignore, String world, int x1, int z1, int x2, int z2);

    // optional shutdown hook (keeps impls happy)
    default void shutdown() { }
}
