package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plot (Data Class)
 * - This class is now extracted from PlotStore to be used by all
 * data storage types (YML, SQL, etc).
 * - It represents a single protected plot.
 */
public class Plot {
    private final UUID plotId;
    private final UUID owner;
    private String ownerName;
    private final String world;
    private final int x1, z1, x2, z2;
    private final Map<String, Boolean> flags = new HashMap<>();
    private long lastUpkeepPayment;

    // Map<PlayerUUID, RoleName>
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();

    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2, long lastUpkeepPayment) {
        this.plotId = plotId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
        this.lastUpkeepPayment = lastUpkeepPayment;
        
        // Set the owner's role internally
        this.playerRoles.put(owner, "owner");

        // Default protections ON
        flags.put("pvp", true);
        flags.put("containers", true);
        flags.put("mobs", true);
        flags.put("pets", true);
        flags.put("entities", true);
        flags.put("farm", true);
        flags.put("safe_zone", true); // Default to full protection
        flags.put("tnt-damage", true);
        flags.put("fire-spread", true);
        flags.put("piston-use", true);
    }
    
    // Overloaded constructor for new plots
    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
        this(plotId, owner, ownerName, world, x1, z1, x2, z2, System.currentTimeMillis());
    }

    // --- Getters ---
    public UUID getPlotId() { return plotId; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String name) { this.ownerName = name; }
    public String getWorld() { return world; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }

    public Location getCenter(AegisGuard plugin) {
        World world = Bukkit.getWorld(this.world);
        if (world == null) return null;
        
        double cX = (x1 + x2) / 2.0;
        double cZ = (z1 + z2) / 2.0;
        return new Location(world, cX, 64, cZ); // Y-level is arbitrary
    }

    // --- Role Methods ---
    public Map<UUID, String> getPlayerRoles() { return playerRoles; }
    
    public String getRole(UUID playerUUID) {
        return playerRoles.getOrDefault(playerUUID, "default");
    }
    
    public void setRole(UUID playerUUID, String role) {
        if (role == null || role.equalsIgnoreCase("default")) {
            playerRoles.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase());
        }
    }
    
    public void removeRole(UUID playerUUID) {
        playerRoles.remove(playerUUID);
    }

    /**
     * Checks if a player has a specific permission on this plot.
     * @param playerUUID The UUID of the player
     * @param permission The permission to check (e.g., "BUILD", "CONTAINERS")
     * @return true if the player has the permission, false otherwise
     */
    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        if (owner.equals(playerUUID)) return true; // Owner bypasses
        
        String role = getRole(playerUUID);
        Set<String> permissions = plugin.cfg().getRolePermissions(role);
        
        return permissions.contains(permission.toUpperCase());
    }

    // --- Flag Methods ---
    public boolean getFlag(String key, boolean def) { return flags.getOrDefault(key, def); }
    public void setFlag(String key, boolean value) { flags.put(key, value); }
    public Map<String, Boolean> getFlags() { return Collections.unmodifiableMap(flags); }
    
    // --- Upkeep Methods ---
    public long getLastUpkeepPayment() { return lastUpkeepPayment; }
    public void setLastUpkeepPayment(long time) { this.lastUpkeepPayment = time; }
    
    // --- Util Methods ---
    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }
}
