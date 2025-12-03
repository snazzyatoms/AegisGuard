package com.yourname.aegisguard.objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents a single plot of land (Estate).
 * Can be a Private Estate (Solo) or a Guild Estate (Alliance).
 */
public class Estate {

    private final UUID estateId;
    private String name;
    
    // Remove 'final' so we can transfer ownership later
    private UUID ownerId; // Player UUID (Private) or Guild UUID (Guild)
    private boolean isGuildEstate;
    
    private final World world;
    private final Cuboid region;
    private long creationDate;

    // Stores members: Player UUID -> Role ID (e.g., "viceroy", "resident")
    private final Map<UUID, String> members = new HashMap<>();
    
    // Stores flags: "pvp" -> false, "explosions" -> true
    private final Map<String, Boolean> flags = new HashMap<>();

    // Upkeep / Economy Data
    private double ledgerBalance;
    private long paidUntil; // Timestamp

    public Estate(UUID estateId, String name, UUID ownerId, boolean isGuildEstate, World world, Cuboid region) {
        this.estateId = estateId;
        this.name = name;
        this.ownerId = ownerId;
        this.isGuildEstate = isGuildEstate;
        this.world = world;
        this.region = region;
        this.creationDate = System.currentTimeMillis();
        this.ledgerBalance = 0.0;
        this.paidUntil = System.currentTimeMillis();
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

    // ==========================================================
    // 🧱 CORE SETTERS (For Admin/Transfer Logic)
    // ==========================================================
    public void setName(String name) { this.name = name; }
    
    /**
     * Update the Owner (Player or Guild UUID).
     */
    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * Toggle between Guild Estate and Private Estate.
     */
    public void setIsGuild(boolean isGuild) {
        this.isGuildEstate = isGuild;
    }

    // ==========================================================
    // 👥 MEMBER MANAGEMENT
    // ==========================================================
    
    /**
     * Set a player's role in this estate.
     * @param playerUUID The player to update.
     * @param roleId The ID from roles.yml (e.g., "architect").
     */
    public void setMember(UUID playerUUID, String roleId) {
        members.put(playerUUID, roleId.toLowerCase());
    }

    public void removeMember(UUID playerUUID) {
        members.remove(playerUUID);
    }

    /**
     * Get the Role ID string for a player.
     * Returns null if they are not added.
     */
    public String getMemberRole(UUID playerUUID) {
        return members.get(playerUUID);
    }

    public boolean isMember(UUID playerUUID) {
        return members.containsKey(playerUUID);
    }
    
    public Set<UUID> getAllMembers() {
        return Collections.unmodifiableSet(members.keySet());
    }

    // ==========================================================
    // ⚙️ FLAGS & SETTINGS
    // ==========================================================
    public void setFlag(String flag, boolean value) {
        flags.put(flag.toLowerCase(), value);
    }

    public boolean getFlag(String flag) {
        // Default logic: if flag missing, return true (permissive) or false (restrictive)
        // For common flags like "pvp" or "mobs", we usually default to FALSE or TRUE based on config.
        // Here we return the stored value, or false if unset.
        return flags.getOrDefault(flag.toLowerCase(), false);
    }

    // ==========================================================
    // 💰 ECONOMY LOGIC
    // ==========================================================
    public void deposit(double amount) {
        this.ledgerBalance += amount;
    }

    public boolean withdraw(double amount) {
        if (ledgerBalance >= amount) {
            ledgerBalance -= amount;
            return true;
        }
        return false;
    }
    
    public void setPaidUntil(long timestamp) {
        this.paidUntil = timestamp;
    }
    
    public long getPaidUntil() {
        return paidUntil;
    }
}
