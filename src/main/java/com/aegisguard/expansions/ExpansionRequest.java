package com.aegisguard.expansions;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

public class ExpansionRequest {

    public enum Status {
        PENDING,
        APPROVED,
        DENIED
    }

    private final UUID requester;
    private final UUID plotOwner;
    private final UUID plotId;
    private final String worldName;
    private final int currentRadius;
    private final int requestedRadius;
    private final double cost;
    private final long timestamp;

    private boolean approved;
    private boolean denied;

    public ExpansionRequest(UUID requester,
                            UUID plotOwner,
                            UUID plotId,
                            String worldName,
                            int currentRadius,
                            int requestedRadius,
                            double cost) {
        this.requester = requester;
        this.plotOwner = plotOwner;
        this.plotId = plotId;
        this.worldName = worldName;
        this.currentRadius = currentRadius;
        this.requestedRadius = requestedRadius;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.approved = false;
        this.denied = false;
    }

    // --- Getters ---
    public UUID getRequester() { return requester; }
    public UUID getPlotOwner() { return plotOwner; }
    public UUID getPlotId() { return plotId; }
    public String getWorldName() { return worldName; }
    public int getCurrentRadius() { return currentRadius; }
    public int getRequestedRadius() { return requestedRadius; }
    public double getCost() { return cost; }
    public long getTimestamp() { return timestamp; }

    // --- Helper Methods ---
    public World getWorld() { return Bukkit.getWorld(worldName); }
    public OfflinePlayer getRequesterPlayer() { return Bukkit.getOfflinePlayer(requester); }
    public OfflinePlayer getOwnerPlayer() { return Bukkit.getOfflinePlayer(plotOwner); }

    // --- Status Logic ---
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

    /**
     * Internal status enum for logic.
     * Use this in managers instead of comparing raw strings.
     */
    public synchronized Status getStatus() {
        if (approved) return Status.APPROVED;
        if (denied) return Status.DENIED;
        return Status.PENDING;
    }

    /**
     * Language-engine friendly key.
     * GUIs / managers can feed this into plugin.msg().get/send().
     *
     * Example usage in a GUI:
     *   line(player, request.getStatusLangKey(), "§7Pending");
     */
    public synchronized String getStatusLangKey() {
        return switch (getStatus()) {
            case APPROVED -> "expansion_status_approved";
            case DENIED   -> "expansion_status_denied";
            case PENDING  -> "expansion_status_pending";
        };
    }
}
