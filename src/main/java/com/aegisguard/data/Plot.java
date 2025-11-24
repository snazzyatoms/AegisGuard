package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material; // --- FIX: Added for Warp Icon ---
import org.bukkit.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plot (Data Class)
 * - This is the "Ultimate" version of the Plot class.
 * - Contains: Flags, Roles, Bans, Economy, Auctions, Cosmetics, Server Warps.
 */
public class Plot {
    
    // Special UUID for server-owned plots (Admin Zones)
    public static final UUID SERVER_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // --- Core Fields ---
    private final UUID plotId;
    private UUID owner; 
    private String ownerName;
    private final String world;
    private int x1, z1, x2, z2; 
    
    // --- Flags & Permissions ---
    private final Map<String, Boolean> flags = new HashMap<>();
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();
    private final Set<UUID> bannedPlayers = new HashSet<>(); 

    // --- Upkeep Field ---
    private long lastUpkeepPayment;

    // --- Welcome/TP Fields ---
    private Location spawnLocation;
    private String welcomeMessage;
    private String farewellMessage;
    
    // --- Marketplace Fields ---
    private boolean isForSale;
    private double salePrice;
    private boolean isForRent;
    private double rentPrice;
    private UUID currentRenter;
    private long rentExpires;
    
    // --- Auction/Expiry Fields ---
    private String plotStatus; // "ACTIVE", "EXPIRED", "AUCTION"
    private double currentBid; 
    private UUID currentBidder; 
    
    // --- Cosmetic Fields ---
    private String borderParticle;
    private String ambientParticle;
    private String entryEffect;

    // --- Server Warp Fields (NEW) ---
    private boolean isServerWarp;
    private String warpName;
    private Material warpIcon;

    // --- Constructor (Full) ---
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

        // --- Default Flags ---
        flags.put("pvp", true);
        flags.put("containers", true);
        flags.put("mobs", true);
        flags.put("pets", true);
        flags.put("entities", true);
        flags.put("farm", true);
        flags.put("safe_zone", true); 
        flags.put("tnt-damage", true);
        flags.put("fire-spread", true);
        flags.put("piston-use", true);
        flags.put("build", true);       
        flags.put("interact", true);
        
        // [NEW Features]
        flags.put("fly", false); 
        flags.put("entry", true); 

        // Initialize optional fields
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
        this.currentBid = 0.0; 
        this.currentBidder = null; 
        
        this.borderParticle = null;
        this.ambientParticle = null;
        this.entryEffect = null;

        // Initialize Warp defaults
        this.isServerWarp = false;
        this.warpName = null;
        this.warpIcon = null;
    }
    
    // --- Constructor (New Plot Shortcut) ---
    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
        this(plotId, owner, ownerName, world, x1, z1, x2, z2, System.currentTimeMillis());
    }

    // --- Core Getters ---
    public UUID getPlotId() { return plotId; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public String getWorld() { return world; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    
    // --- Resize Setters ---
    public void setX1(int x) { this.x1 = x; }
    public void setZ1(int z) { this.z1 = z; }
    public void setX2(int x) { this.x2 = x; }
    public void setZ2(int z) { this.z2 = z; }
    
    public void setOwnerName(String name) { this.ownerName = name; }

    // --- Location Helper ---
    public Location getCenter(AegisGuard plugin) {
        World world = Bukkit.getWorld(this.world);
        if (world == null) return null;
        double cX = (x1 + x2) / 2.0;
        double cZ = (z1 + z2) / 2.0;
        return new Location(world, cX, 64, cZ); 
    }
    
    public boolean isServerZone() {
        return owner.equals(SERVER_OWNER_UUID);
    }

    // --- Marketplace Logic (Critical for Transfers) ---
    public void internalSetOwner(UUID newOwner, String newOwnerName) {
        this.owner = newOwner;
        this.ownerName = newOwnerName;
        // Wipe old data
        this.playerRoles.clear();
        this.playerRoles.put(newOwner, "owner");
        this.bannedPlayers.clear(); // Unban everyone for the new owner
    }
    
    // --- Role Logic ---
    public Map<UUID, String> getPlayerRoles() { return playerRoles; }
    
    public String getRole(UUID playerUUID) {
        return playerRoles.getOrDefault(playerUUID, "default");
    }
    
    public void setRole(UUID playerUUID, String role) {
        if (role == null || role.equalsIgnoreCase("default")) {
            playerRoles.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase());
            bannedPlayers.remove(playerUUID); 
        }
    }
    
    public void removeRole(UUID playerUUID) {
        playerRoles.remove(playerUUID);
    }

    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        if (owner.equals(playerUUID)) return true; 
        if (isBanned(playerUUID)) return false; 
        
        // Renter Logic
        if (currentRenter != null && currentRenter.equals(playerUUID) && System.currentTimeMillis() < rentExpires) {
            if (plugin.cfg().getRolePermissions("member").contains(permission.toUpperCase())) {
                return true;
            }
        }
        
        String role = getRole(playerUUID);
        Set<String> permissions = new HashSet<>(plugin.cfg().getRolePermissions(role));
        
        return permissions.contains(permission.toUpperCase());
    }

    // --- Ban Logic ---
    public Set<UUID> getBannedPlayers() { return bannedPlayers; }
    
    public boolean isBanned(UUID playerUUID) { 
        return bannedPlayers.contains(playerUUID); 
    }
    
    public void addBan(UUID playerUUID) {
        playerRoles.remove(playerUUID); 
        bannedPlayers.add(playerUUID);
    }
    
    public void removeBan(UUID playerUUID) { 
        bannedPlayers.remove(playerUUID); 
    }

    // --- Flag Logic ---
    public boolean getFlag(String key, boolean def) { return flags.getOrDefault(key, def); }
    public void setFlag(String key, boolean value) { flags.put(key, value); }
    public Map<String, Boolean> getFlags() { return Collections.unmodifiableMap(flags); }
    
    // --- Upkeep Logic ---
    public long getLastUpkeepPayment() { return lastUpkeepPayment; }
    public void setLastUpkeepPayment(long time) { this.lastUpkeepPayment = time; }
    
    // --- Spawn Logic ---
    public Location getSpawnLocation() { return spawnLocation; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public String getFarewellMessage() { return farewellMessage; }
    
    public void setSpawnLocation(Location loc) { this.spawnLocation = loc; }
    public void setWelcomeMessage(String msg) { this.welcomeMessage = msg; }
    public void setFarewellMessage(String msg) { this.farewellMessage = msg; }

    public String getSpawnLocationString() {
        if (spawnLocation == null) return null;
        return String.format("%s:%.2f:%.2f:%.2f:%.2f:%.2f",
                spawnLocation.getWorld().getName(),
                spawnLocation.getX(),
                spawnLocation.getY(),
                spawnLocation.getZ(),
                spawnLocation.getYaw(),
                spawnLocation.getPitch()
        );
    }

    public void setSpawnLocationFromString(String s) {
        if (s == null || s.isEmpty()) { this.spawnLocation = null; return; }
        try {
            String[] parts = s.split(":");
            if (parts.length != 6) return;
            World world = Bukkit.getWorld(parts[0]);
            if (world != null) {
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                float yaw = Float.parseFloat(parts[4]);
                float pitch = Float.parseFloat(parts[5]);
                this.spawnLocation = new Location(world, x, y, z, yaw, pitch);
            }
        } catch (Exception e) { this.spawnLocation = null; }
    }
    
    // --- Marketplace Logic ---
    public boolean isForSale() { return isForSale; }
    public void setForSale(boolean forSale, double price) { this.isForSale = forSale; this.salePrice = price; }
    public double getSalePrice() { return salePrice; }
    
    public boolean isForRent() { return isForRent; }
    public double getRentPrice() { return rentPrice; }
    public void setForRent(boolean forRent, double price) { this.isForRent = forRent; this.rentPrice = price; }
    
    public UUID getCurrentRenter() { return currentRenter; }
    public long getRentExpires() { return rentExpires; }
    public void setRenter(UUID renter, long expirationTime) { this.currentRenter = renter; this.rentExpires = expirationTime; }
    
    // --- Auction Logic ---
    public String getPlotStatus() { return plotStatus; }
    public void setPlotStatus(String status) { this.plotStatus = status; }
    public double getCurrentBid() { return currentBid; }
    public UUID getCurrentBidder() { return currentBidder; }
    public void setCurrentBid(double bid, UUID bidder) { this.currentBid = bid; this.currentBidder = bidder; }

    // --- Cosmetic Logic ---
    public String getBorderParticle() { return borderParticle; }
    public void setBorderParticle(String particle) { this.borderParticle = particle; }
    public String getAmbientParticle() { return ambientParticle; }
    public void setAmbientParticle(String particle) { this.ambientParticle = particle; }
    public String getEntryEffect() { return entryEffect; }
    public void setEntryEffect(String effect) { this.entryEffect = effect; }

    // --- NEW: Server Warp Logic ---
    public boolean isServerWarp() { return isServerWarp; }
    public String getWarpName() { return warpName; }
    public Material getWarpIcon() { return warpIcon; }

    public void setServerWarp(boolean isWarp, String name, Material icon) {
        this.isServerWarp = isWarp;
        this.warpName = name;
        this.warpIcon = icon;
    }

    // --- Utility ---
    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }
}
