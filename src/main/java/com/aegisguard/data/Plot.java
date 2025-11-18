package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Collections; // --- NEW IMPORT ---
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
 *
 * --- UPGRADE NOTES (Ultimate) ---
 * - Added fields for Plot Marketplace (sale/rent).
 * - Added fields for Plot Auctions (status).
 * - Added fields for Plot Cosmetics (particles/effects).
 */
public class Plot {
    // --- Core Fields ---
    private final UUID plotId;
    private final UUID owner;
    private String ownerName;
    private final String world;
    private final int x1, z1, x2, z2;
    
    // --- Flags & Roles ---
    private final Map<String, Boolean> flags = new HashMap<>();
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();
    
    // --- Upkeep Field ---
    private long lastUpkeepPayment;

    // --- Welcome/TP Fields ---
    private Location spawnLocation;
    private String welcomeMessage;
    private String farewellMessage;
    
    // --- NEW: Marketplace Fields ---
    private boolean isForSale;
    private double salePrice;
    private boolean isForRent;
    private double rentPrice;
    private UUID currentRenter;
    private long rentExpires;
    
    // --- NEW: Auction/Expiry Field ---
    private String plotStatus; // "ACTIVE", "EXPIRED", "AUCTION"
    
    // --- NEW: Cosmetic Fields ---
    private String borderParticle;
    private String ambientParticle;
    private String entryEffect;


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
        
        // --- NEW: Initialize new fields ---
        this.spawnLocation = null;
        this.welcomeMessage = null;
        this.farewellMessage = null;
        
        this.isForSale = false;
        this.salePrice = 0.0;
        this.isForRent = false;
        this.rentPrice = 0.0;
        this.currentRenter = null;
        this.rentExpires = 0L;
        
        this.plotStatus = "ACTIVE";
        
        this.borderParticle = null;
        this.ambientParticle = null;
        this.entryEffect = null;
    }
    
    // Overloaded constructor for new plots
    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
        this(plotId, owner, ownerName, world, x1, z1, x2, z2, System.currentTimeMillis());
    }

    // --- Getters ---
    public UUID getPlotId() { return plotId; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public String getWorld() { return world; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    public void setOwnerName(String name) { this.ownerName = name; }

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

    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        if (owner.equals(playerUUID)) return true; // Owner bypasses
        
        // --- NEW: Check for Renter ---
        if (currentRenter != null && currentRenter.equals(playerUUID) && System.currentTimeMillis() < rentExpires) {
            // Renter gets "member" permissions
            if (plugin.cfg().getRolePermissions("member").contains(permission.toUpperCase())) {
                return true;
            }
        }
        
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
    
    // --- Welcome/TP Getters & Setters ---
    public Location getSpawnLocation() { return spawnLocation; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public String getFarewellMessage() { return farewellMessage; }
    
    public void setSpawnLocation(Location loc) { this.spawnLocation = loc; }
    public void setWelcomeMessage(String msg) { this.welcomeMessage = msg; }
    public void setFarewellMessage(String msg) { this.farewellMessage = msg; }

    public String getSpawnLocationString() {
        if (spawnLocation == null) return null;
        return String.format("%.2f:%.2f:%.2f:%.2f:%.2f",
                spawnLocation.getX(),
                spawnLocation.getY(),
                spawnLocation.getZ(),
                spawnLocation.getYaw(),
                spawnLocation.getPitch()
        );
    }

    public void setSpawnLocationFromString(String s) {
        if (s == null || s.isEmpty()) {
            this.spawnLocation = null;
            return;
        }
        try {
            String[] parts = s.split(":");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);
            World plotWorld = Bukkit.getWorld(this.world);
            if (plotWorld != null) {
                this.spawnLocation = new Location(plotWorld, x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            this.spawnLocation = null;
        }
    }
    
    // --- NEW: Marketplace Getters/Setters ---
    public boolean isForSale() { return isForSale; }
    public void setForSale(boolean forSale, double price) {
        this.isForSale = forSale;
        this.salePrice = price;
    }
    public double getSalePrice() { return salePrice; }
    
    public boolean isForRent() { return isForRent; }
    public double getRentPrice() { return rentPrice; }
    public void setForRent(boolean forRent, double price) {
        this.isForRent = forRent;
        this.rentPrice = price;
    }
    
    public UUID getCurrentRenter() { return currentRenter; }
    public long getRentExpires() { return rentExpires; }
    public void setRenter(UUID renter, long expirationTime) {
        this.currentRenter = renter;
        this.rentExpires = expirationTime;
    }
    
    // --- NEW: Auction Getters/Setters ---
    public String getPlotStatus() { return plotStatus; }
    public void setPlotStatus(String status) { this.plotStatus = status; }

    // --- NEW: Cosmetic Getters/Setters ---
    public String getBorderParticle() { return borderParticle; }
    public void setBorderParticle(String particle) { this.borderParticle = particle; }
    public String getAmbientParticle() { return ambientParticle; }
    public void setAmbientParticle(String particle) { this.ambientParticle = particle; }
    public String getEntryEffect() { return entryEffect; }
    public void setEntryEffect(String effect) { this.entryEffect = effect; }

    
    // --- Util Methods ---
    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }
}
