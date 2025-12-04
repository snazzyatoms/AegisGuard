package com.aegisguard.objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import java.util.*;

public class Guild {

    private final UUID guildId;
    private String name;
    private String tag; // e.g. [IRON]
    private UUID leaderUUID; // The Grandmaster
    
    private int level;
    private double treasuryBalance;
    private long creationDate;
    
    // Member Storage: UUID -> Role ID (e.g., "commander")
    private final Map<UUID, String> members = new HashMap<>();
    
    // Relations: Guild UUID -> Relation (e.g., "ALLY", "ENEMY")
    private final Map<UUID, String> relations = new HashMap<>();

    public Guild(UUID guildId, String name, UUID leaderUUID) {
        this.guildId = guildId;
        this.name = name;
        this.leaderUUID = leaderUUID;
        this.level = 1;
        this.treasuryBalance = 0.0;
        this.creationDate = System.currentTimeMillis();
        
        // Add leader automatically
        this.members.put(leaderUUID, "grandmaster");
    }

    // ==========================================================
    // 🧱 CORE DATA
    // ==========================================================
    public UUID getId() { return guildId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    
    public UUID getLeader() { return leaderUUID; }
    public void setLeader(UUID uuid) { this.leaderUUID = uuid; }

    // ==========================================================
    // 💰 TREASURY MANAGEMENT
    // ==========================================================
    public double getBalance() { return treasuryBalance; }
    
    public void deposit(double amount) {
        this.treasuryBalance += amount;
    }
    
    public boolean withdraw(double amount) {
        if (this.treasuryBalance >= amount) {
            this.treasuryBalance -= amount;
            return true;
        }
        return false;
    }

    // ==========================================================
    // 👥 ROSTER MANAGEMENT
    // ==========================================================
    public void addMember(UUID player, String role) {
        members.put(player, role.toLowerCase());
    }
    
    public void removeMember(UUID player) {
        members.remove(player);
    }
    
    public String getMemberRole(UUID player) {
        return members.get(player);
    }
    
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members.keySet());
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }
}
