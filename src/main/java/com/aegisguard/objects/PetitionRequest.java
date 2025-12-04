package com.aegisguard.objects;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.UUID;

/**
 * Represents a pending request from a player to expand their Private Estate.
 * (Renamed from ExpansionRequest to match v1.3.0 terminology).
 */
public class PetitionRequest {

    private final UUID requester;
    private final UUID estateOwner; // Usually same as requester
    private final UUID estateId;
    private final String worldName;
    private final int currentRadius;
    private final int requestedRadius;
    private final double cost;
    private final long timestamp;

    private boolean approved;
    private boolean denied;

    public PetitionRequest(UUID requester, UUID estateOwner, UUID estateId, String worldName,
                           int currentRadius, int requestedRadius, double cost) {
        this.requester = requester;
        this.estateOwner = estateOwner;
        this.estateId = estateId;
        this.worldName = worldName;
        this.currentRadius = currentRadius;
        this.requestedRadius = requestedRadius;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.approved = false;
        this.denied = false;
    }

    // ==========================================================
    // 🧱 DATA GETTERS
    // ==========================================================
    public UUID getRequester() { return requester; }
    public UUID getEstateOwner() { return estateOwner; }
    public UUID getEstateId() { return estateId; }
    public String getWorldName() { return worldName; }
    public int getCurrentRadius() { return currentRadius; }
    public int getRequestedRadius() { return requestedRadius; }
    public double getCost() { return cost; }
    public long getTimestamp() { return timestamp; }

    // ==========================================================
    // 🧩 HELPER METHODS
    // ==========================================================
    public World getWorld() { 
        return Bukkit.getWorld(worldName); 
    }
    
    public OfflinePlayer getRequesterPlayer() { 
        return Bukkit.getOfflinePlayer(requester); 
    }
    
    public OfflinePlayer getOwnerPlayer() { 
        return Bukkit.getOfflinePlayer(estateOwner); 
    }

    // ==========================================================
    // 🚦 STATUS LOGIC
    // ==========================================================
    public synchronized boolean isApproved() { return approved; }
    public synchronized boolean isDenied() { return denied; }
    public synchronized boolean isPending() { return !approved && !denied; }

    public synchronized void approve() {
        this.approved = true;
        this.denied = false;
    }

    public synchronized void deny() {
        this.denied = true;
        this.approved = false;
    }

    public synchronized String getStatus() {
        if (approved) return "APPROVED";
        if (denied) return "DENIED";
        return "PENDING";
    }
}
