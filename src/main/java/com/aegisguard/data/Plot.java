package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plot (Data Class) - v1.1.2
 * Represents a land claim.
 */
public class Plot {
    
    // Special UUID for server-owned plots
    public static final UUID SERVER_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    
    // Default Flag State (To keep constructor clean)
    private static final Map<String, Boolean> DEFAULT_FLAGS = Map.ofEntries(
        Map.entry("pvp", false), 
        Map.entry("containers", true),
        Map.entry("mobs", true),
        Map.entry("pets", true),
        Map.entry("entities", true),
        Map.entry("farm", true),
        Map.entry("tnt-damage", false),
        Map.entry("fire-spread", false),
        Map.entry("piston-use", true),
        Map.entry("build", true),
        Map.entry("interact", true),
        Map.entry("fly", false),
        Map.entry("entry", true),
        Map.entry("safe_zone", false)
    );

    // --- Core Identity ---
    private final UUID plotId;
    private UUID owner; 
    private String ownerName;
    private final String world;
    private int x1, z1, x2, z2; 
    
    // --- Data Containers ---
    private final Map<String, Boolean> flags = new HashMap<>();
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();
    private final Set<UUID> bannedPlayers = new HashSet<>(); 
    private final List<Zone> zones = new ArrayList<>(); 
    private final Set<UUID> likedBy = new HashSet<>();

    // --- Progression (v1.1.0) ---
    private int level = 1;
    private double xp = 0;

    // --- Economy & Upkeep ---
    private long lastUpkeepPayment;
    private boolean isForSale;
    private double salePrice;
    private boolean isForRent;
    private double rentPrice;
    private UUID currentRenter;
    private long rentExpires;
    
    // --- Auction ---
    private String plotStatus = "ACTIVE"; 
    private double currentBid; 
    private UUID currentBidder; 
    
    // --- Visuals & Identity (v1.1.1) ---
    private Location spawnLocation;
    private String welcomeMessage;
    private String farewellMessage;
    private String entryTitle;    
    private String entrySubtitle; 
    private String description;   
    private String customBiome;   

    // --- Cosmetics ---
    private String borderParticle;
    private String ambientParticle;
    private String entryEffect;

    // --- Server Warps ---
    private boolean isServerWarp;
    private String warpName;
    private Material warpIcon;

    // --- CONSTRUCTORS ---

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
        
        if (!isServerZone()) {
            this.playerRoles.put(owner, "owner");
        }
        this.flags.putAll(DEFAULT_FLAGS);
    }
    
    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
        this(plotId, owner, ownerName, world, x1, z1, x2, z2, System.currentTimeMillis());
    }

    // --- CORE LOGIC ---

    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }
    
    public boolean isServerZone() {
        return SERVER_OWNER_UUID.equals(owner);
    }
    
    public Location getCenter(@Nullable AegisGuard plugin) {
        World w = Bukkit.getWorld(this.world);
        if (w == null) return null;
        double cX = (x1 + x2) / 2.0 + 0.5;
        double cZ = (z1 + z2) / 2.0 + 0.5;
        int y = w.getHighestBlockYAt((int)cX, (int)cZ) + 1;
        return new Location(w, cX, y, cZ); 
    }

    // --- PERMISSIONS SYSTEM ---

    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        if (owner.equals(playerUUID)) return true; 
        if (isBanned(playerUUID)) return false; 
        
        if (currentRenter != null && currentRenter.equals(playerUUID)) {
            if (System.currentTimeMillis() < rentExpires) {
                Set<String> perms = plugin.cfg().getRolePermissions("member"); 
                return perms.contains(permission.toUpperCase());
            } else {
                this.currentRenter = null;
                this.rentExpires = 0;
            }
        }
        
        String role = getRole(playerUUID);
        Set<String> permissions = plugin.cfg().getRolePermissions(role);
        return permissions.contains(permission.toUpperCase());
    }

    public String getRole(UUID playerUUID) { 
        return playerRoles.getOrDefault(playerUUID, "default"); 
    }
    
    public void setRole(UUID playerUUID, String role) {
        if (role == null || role.equalsIgnoreCase("default") || role.equalsIgnoreCase("none")) {
            playerRoles.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase());
            bannedPlayers.remove(playerUUID);
        }
    }

    // --- RESTORED ACCESSORS (Fixes Logic Errors) ---
    public Map<UUID, String> getPlayerRoles() { 
        return playerRoles; 
    }
    
    public void removeRole(UUID playerUUID) { 
        playerRoles.remove(playerUUID); 
    }
    // ----------------------------------------------

    // --- GETTERS & SETTERS (Existing) ---
    public UUID getPlotId() { return plotId; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public String getWorld() { return world; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    
    public void setX1(int x) { this.x1 = x; }
    public void setZ1(int z) { this.z1 = z; }
    public void setX2(int x) { this.x2 = x; }
    public void setZ2(int z) { this.z2 = z; }
    
    public void internalSetOwner(UUID newOwner, String newOwnerName) {
        this.owner = newOwner;
        this.ownerName = newOwnerName;
        this.playerRoles.clear();
        this.playerRoles.put(newOwner, "owner");
        this.bannedPlayers.clear(); 
        this.likedBy.clear();
        this.entryTitle = null;
        this.description = null;
    }

    // Flags
    public boolean getFlag(String key, boolean def) { return flags.getOrDefault(key, def); }
    public void setFlag(String key, boolean value) { flags.put(key, value); }
    public Map<String, Boolean> getFlags() { return Collections.unmodifiableMap(flags); }

    // Zones
    public List<Zone> getZones() { return zones; }
    public void addZone(Zone zone) { zones.add(zone); }
    public void removeZone(Zone zone) { zones.remove(zone); }
    public Zone getZoneAt(Location loc) {
        for (Zone z : zones) {
            if (z.isInside(loc)) return z;
        }
        return null;
    }

    // Leveling
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public double getXp() { return xp; }
    public void setXp(double xp) { this.xp = xp; }
    public void addXp(double amount) { this.xp += amount; }

    // Social
    public Set<UUID> getLikedBy() { return likedBy; }
    public int getLikes() { return likedBy.size(); }
    public boolean hasLiked(UUID player) { return likedBy.contains(player); }
    public void toggleLike(UUID player) {
        if (likedBy.contains(player)) likedBy.remove(player);
        else likedBy.add(player);
    }

    // Bans
    public boolean isBanned(UUID playerUUID) { return bannedPlayers.contains(playerUUID); }
    public void addBan(UUID playerUUID) { 
        playerRoles.remove(playerUUID); 
        bannedPlayers.add(playerUUID); 
    }
    public void removeBan(UUID playerUUID) { bannedPlayers.remove(playerUUID); }

    // Upkeep & Economy
    public long getLastUpkeepPayment() { return lastUpkeepPayment; }
    public void setLastUpkeepPayment(long time) { this.lastUpkeepPayment = time; }
    public boolean isForSale() { return isForSale; }
    public void setForSale(boolean forSale, double price) { this.isForSale = forSale; this.salePrice = price; }
    public double getSalePrice() { return salePrice; }
    public boolean isForRent() { return isForRent; }
    public double getRentPrice() { return rentPrice; }
    public void setForRent(boolean forRent, double price) { this.isForRent = forRent; this.rentPrice = price; }
    public UUID getCurrentRenter() { return currentRenter; }
    public long getRentExpires() { return rentExpires; }
    public void setRenter(UUID renter, long expirationTime) { this.currentRenter = renter; this.rentExpires = expirationTime; }

    // Auction
    public String getPlotStatus() { return plotStatus; }
    public void setPlotStatus(String status) { this.plotStatus = status; }
    public double getCurrentBid() { return currentBid; }
    public UUID getCurrentBidder() { return currentBidder; }
    public void setCurrentBid(double bid, UUID bidder) { this.currentBid = bid; this.currentBidder = bidder; }

    // Visuals
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location loc) { this.spawnLocation = loc; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String msg) { this.welcomeMessage = msg; }
    public String getFarewellMessage() { return farewellMessage; }
    public void setFarewellMessage(String msg) { this.farewellMessage = msg; }
    
    // Identity
    public String getEntryTitle() { return entryTitle; }
    public void setEntryTitle(String title) { this.entryTitle = title; }
    public String getEntrySubtitle() { return entrySubtitle; }
    public void setEntrySubtitle(String sub) { this.entrySubtitle = sub; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCustomBiome() { return customBiome; }
    public void setCustomBiome(String biome) { this.customBiome = biome; }

    // Cosmetics
    public String getBorderParticle() { return borderParticle; }
    public void setBorderParticle(String particle) { this.borderParticle = particle; }
    public String getAmbientParticle() { return ambientParticle; }
    public void setAmbientParticle(String particle) { this.ambientParticle = particle; }
    public String getEntryEffect() { return entryEffect; }
    public void setEntryEffect(String effect) { this.entryEffect = effect; }

    // Server Warps
    public boolean isServerWarp() { return isServerWarp; }
    public String getWarpName() { return warpName; }
    public Material getWarpIcon() { return warpIcon; }
    public void setServerWarp(boolean isWarp, String name, Material icon) { 
        this.isServerWarp = isWarp; 
        this.warpName = name; 
        this.warpIcon = icon; 
    }

    // --- SERIALIZATION HELPERS ---
    
    public String getSpawnLocationString() {
        if (spawnLocation == null) return null;
        return String.format("%s:%.2f:%.2f:%.2f:%.2f:%.2f", 
            spawnLocation.getWorld().getName(), 
            spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(), 
            spawnLocation.getYaw(), spawnLocation.getPitch());
    }

    public void setSpawnLocationFromString(String s) {
        if (s == null || s.isEmpty()) { this.spawnLocation = null; return; }
        try {
            String[] parts = s.split(":");
            if (parts.length < 4) return;
            World world = Bukkit.getWorld(parts[0]);
            if (world != null) {
                this.spawnLocation = new Location(world, 
                    Double.parseDouble(parts[1]), 
                    Double.parseDouble(parts[2]), 
                    Double.parseDouble(parts[3]), 
                    parts.length > 4 ? Float.parseFloat(parts[4]) : 0f, 
                    parts.length > 5 ? Float.parseFloat(parts[5]) : 0f
                );
            }
        } catch (Exception e) { this.spawnLocation = null; }
    }

    // --- OVERRIDES (Critical for Collections) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Plot plot = (Plot) o;
        return plotId.equals(plot.plotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plotId);
    }

    @Override
    public String toString() {
        return "Plot{id=" + plotId + ", owner=" + ownerName + ", world=" + world + "}";
    }
}
