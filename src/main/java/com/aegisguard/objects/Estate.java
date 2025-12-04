package com.aegisguard.objects;

import com.aegisguard.objects.Cuboid; // Ensure correct package for Cuboid
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a single plot of land (Estate).
 * Replaces the legacy 'Plot' class.
 * Supports Private Ownership (Solo) and Guild Ownership (Alliance).
 */
public class Estate {

    // --- CONSTANTS ---
    // The special UUID used for Admin/Server zones (Spawn, Warzone, etc.)
    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final UUID estateId;
    private String name;
    private UUID ownerId; 
    private boolean isGuildEstate;
    
    private final World world;
    private final Cuboid region;
    private long creationDate;

    // --- ROLES & MEMBERS ---
    private final Map<UUID, String> members = new HashMap<>();
    private final Set<UUID> bannedPlayers = new HashSet<>();
    private final Set<UUID> likedBy = new HashSet<>();
    
    // --- FLAGS ---
    private final Map<String, Boolean> flags = new HashMap<>();

    // --- SUB-ZONES ---
    private final List<Zone> zones = new ArrayList<>();

    // --- ECONOMY ---
    private double ledgerBalance;
    private long paidUntil;
    
    // --- MARKET ---
    private boolean isForSale;
    private double salePrice;
    private boolean isForRent;
    private double rentPrice;
    private UUID currentRenter;
    private long rentExpires;

    // --- AUCTION ---
    private double currentBid;
    private UUID currentBidder;

    // --- PROGRESSION ---
    private int level = 1;
    private double xp = 0;
    private int maxMembers = 2;

    // --- VISUALS ---
    private Location spawnLocation;
    private String welcomeMessage;
    private String farewellMessage;
    private String description;
    private String borderParticle;
    private String customBiome; 

    public Estate(UUID estateId, String name, UUID ownerId, boolean isGuildEstate, World world, Cuboid region) {
        this.estateId = estateId;
        this.name = name;
        this.ownerId = ownerId;
        this.isGuildEstate = isGuildEstate;
        this.world = world;
        this.region = region;
        this.creationDate = System.currentTimeMillis();
        
        // Default Flags
        this.flags.put("pvp", false);
        this.flags.put("mobs", false);
        this.flags.put("build", true);
        this.flags.put("interact", true);
        // v1.3.0 Defaults
        this.flags.put("hunger", true);
        this.flags.put("sleep", true);
    }

    // ==========================================================
    // 👑 IDENTIFICATION
    // ==========================================================
    
    /**
     * Checks if this is a Server Zone (Admin owned).
     */
    public boolean isServerZone() {
        return ownerId.equals(SERVER_UUID) || getFlag("safe_zone");
    }

    // ==========================================================
    // 🧱 CORE GETTERS
    // ==========================================================
    public UUID getId() { return estateId; }
    public String getName() { return name; }
    public UUID getOwnerId() { return ownerId; }
    public boolean isGuild() { return isGuildEstate; }
    public boolean isPrivate() { return !isGuildEstate; }
    public World getWorld() { return world; }
    public Cuboid getRegion() { return region; }
    public double getBalance() { return ledgerBalance; }
    public long getCreationDate() { return creationDate; }

    // ==========================================================
    // 🧱 SETTERS
    // ==========================================================
    public void setName(String name) { this.name = name; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public void setIsGuild(boolean isGuild) { this.isGuildEstate = isGuild; }

    // ==========================================================
    // 👥 MEMBER MANAGEMENT
    // ==========================================================
    public void setMember(UUID playerUUID, String roleId) {
        if (roleId == null) members.remove(playerUUID);
        else members.put(playerUUID, roleId.toLowerCase());
    }

    public void removeMember(UUID playerUUID) { members.remove(playerUUID); }
    public String getMemberRole(UUID playerUUID) { return members.get(playerUUID); }
    public boolean isMember(UUID playerUUID) { return members.containsKey(playerUUID); }
    public Set<UUID> getAllMembers() { return Collections.unmodifiableSet(members.keySet()); }

    public boolean isBanned(UUID uuid) { return bannedPlayers.contains(uuid); }
    public void addBan(UUID uuid) { 
        removeMember(uuid); // Kick if banned
        bannedPlayers.add(uuid); 
    }
    public void removeBan(UUID uuid) { bannedPlayers.remove(uuid); }

    // ==========================================================
    // ⚙️ FLAGS & SETTINGS
    // ==========================================================
    public void setFlag(String flag, boolean value) { flags.put(flag.toLowerCase(), value); }
    
    public boolean getFlag(String flag) { 
        // Default to false if unset, unless specific logic overrides later
        return flags.getOrDefault(flag.toLowerCase(), false); 
    }

    // ==========================================================
    // 🏗️ SUB-ZONES
    // ==========================================================
    public List<Zone> getZones() { return zones; }
    public void addZone(Zone zone) { zones.add(zone); }
    public void removeZone(Zone zone) { zones.remove(zone); }
    
    public Zone getZoneAt(Location loc) {
        for (Zone z : zones) {
            if (z.isInside(loc)) return z;
        }
        return null;
    }

    // ==========================================================
    // 💰 ECONOMY
    // ==========================================================
    public void deposit(double amount) { this.ledgerBalance += amount; }
    public boolean withdraw(double amount) {
        if (ledgerBalance >= amount) {
            ledgerBalance -= amount;
            return true;
        }
        return false;
    }
    public void setPaidUntil(long timestamp) { this.paidUntil = timestamp; }
    public long getPaidUntil() { return paidUntil; }
    
    // Market
    public boolean isForSale() { return isForSale; }
    public double getSalePrice() { return salePrice; }
    public void setForSale(boolean forSale, double price) { this.isForSale = forSale; this.salePrice = price; }
    public boolean isForRent() { return isForRent; }
    public double getRentPrice() { return rentPrice; } // Placeholder for future Rent logic

    // Auction
    public double getCurrentBid() { return currentBid; }
    public void setCurrentBid(double bid) { this.currentBid = bid; }
    public UUID getCurrentBidder() { return currentBidder; }
    public void setBidder(UUID bidder) { this.currentBidder = bidder; }

    // ==========================================================
    // 📈 PROGRESSION
    // ==========================================================
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int max) { this.maxMembers = max; }

    // ==========================================================
    // 🎨 VISUALS
    // ==========================================================
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location loc) { this.spawnLocation = loc; }
    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }
    
    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String msg) { this.welcomeMessage = msg; }
    public String getFarewellMessage() { return farewellMessage; }
    public void setFarewellMessage(String msg) { this.farewellMessage = msg; }
    
    public String getCustomBiome() { return customBiome; }
    public void setCustomBiome(String biome) { this.customBiome = biome; }
    
    public String getBorderParticle() { return borderParticle; }
    public void setBorderParticle(String p) { this.borderParticle = p; }
    
    // --- Likes ---
    public int getLikes() { return likedBy.size(); }
    public boolean hasLiked(UUID uuid) { return likedBy.contains(uuid); }
    public void toggleLike(UUID uuid) {
        if (likedBy.contains(uuid)) likedBy.remove(uuid);
        else likedBy.add(uuid);
    }

    // ==========================================================
    // 🧩 UTILS
    // ==========================================================
    public Location getCenter() { return region.getCenter(); }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Estate estate = (Estate) o;
        return estateId.equals(estate.estateId);
    }

    @Override
    public int hashCode() { return Objects.hash(estateId); }
}
