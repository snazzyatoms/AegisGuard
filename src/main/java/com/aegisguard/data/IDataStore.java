package com.aegisguard.data;

import com.aegisguard.objects.Estate;
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

    // --- Estate Management ---
    void saveEstate(Estate estate);
    
    // FIXED: Renamed from deleteEstate to removeEstate to match implementation
    void removeEstate(UUID estateId);
    
    // FIXED: Renamed from changeEstateOwner to updateEstateOwner
    void updateEstateOwner(Estate estate, UUID newOwner, boolean isGuild);

    // --- Queries ---
    Estate getEstateAt(Location loc);
    Estate getEstate(UUID owner, UUID plotId);
    List<Estate> getEstates(UUID owner);
    Collection<Estate> getAllEstates();
    Collection<Estate> getEstatesForSale();
    Collection<Estate> getEstatesForAuction();

    // --- Logic ---
    boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID excludeId);
    
    // --- Legacy / Required Stubs ---
    void createEstate(UUID owner, Location c1, Location c2);
    void addEstate(Estate estate);
    void addPlayerRole(Estate estate, UUID uuid, String role);
    void removePlayerRole(Estate estate, UUID uuid);
    
    // --- Maintenance ---
    void removeBannedEstates();
    void removeAllPlots(UUID owner);
    
    // --- Wilderness Logging ---
    void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID);
    void revertWildernessBlocks(long timestamp, int limit);
}
