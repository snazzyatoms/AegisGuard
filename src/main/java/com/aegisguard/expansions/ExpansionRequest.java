package com.aegisguard.expansions;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

/**
 * ExpansionRequest
 * ---------------------------------------
 * Represents a pending request to expand a player's plot.
 *
 * - Supports Vault or Item-based cost models
 * - Tracks requesting player, target plot, world context
 * - Used by ExpansionRequestManager to store and process active requests
 */
public class ExpansionRequest {

    private final UUID requester;     // Player requesting the expansion
    private final UUID plotOwner;     // Owner of the plot (for approval)
    private final UUID plotId;        // --- NEW --- The specific plot being expanded
    private final String worldName;     // Target world
    private final int currentRadius;    // Current radius of the plot
    private final int requestedRadius;  // Requested new radius
    private final double cost;          // Calculated Vault cost (if applicable)
    private final long timestamp;       // Creation timestamp

    private boolean approved;           // True if approved
    private boolean denied;             // True if denied

    public ExpansionRequest(UUID requester, UUID plotOwner, UUID plotId, String worldName,
                            int currentRadius, int requestedRadius, double cost) {
        this.requester = requester;
        this.plotOwner = plotOwner;
        this.plotId = plotId; // --- NEW ---
        this.worldName = worldName;
        this.currentRadius = currentRadius;
        this.requestedRadius = requestedRadius;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.approved = false;
        this.denied = false;
    }

    /* -----------------------------
     * Getters
     * ----------------------------- */

    public UUID getRequester() {
        return requester;
    }

    public UUID getPlotOwner() {
        return plotOwner;
    }

    /** --- NEW ---
     * Gets the unique ID of the plot being expanded.
     */
    public UUID getPlotId() {
        return plotId;
    }

    public String getWorldName() {
        return worldName;
    }
// ... existing code ...
    public World getWorld() {
// ... existing code ...
    }

    public int getCurrentRadius() {
// ... existing code ...
    }

    public int getRequestedRadius() {
// ... existing code ...
    }

// ... existing code ...
    public double getCost() {
// ... existing code ...
    }

    public long getTimestamp() {
// ... existing code ...
    }
// ... existing code ...
    public OfflinePlayer getRequesterPlayer() {
// ... existing code ...
    }

    public OfflinePlayer getOwnerPlayer() {
// ... existing code ...
    }

    public boolean isApproved() {
// ... existing code ...
    }

    public boolean isDenied() {
// ... existing code ...
    }

    /* -----------------------------
     * State Management
     * ----------------------------- */

    /** --- MODIFIED --- Added 'synchronized' for thread-safety */
    public synchronized void approve() {
        this.approved = true;
        this.denied = false;
    }

    /** --- MODIFIED --- Added 'synchronized' for thread-safety */
    public synchronized void deny() {
        this.denied = true;
        this.approved = false;
    }

    /** --- MODIFIED --- Added 'synchronized' for thread-safety */
    public synchronized boolean isPending() {
        return !approved && !denied;
    }

    /* -----------------------------
     * Utility
     * ----------------------------- */
// ... existing code ...
    public String getStatus() {
// ... existing code ...
    }

    @Override
    public String toString() {
        return "ExpansionRequest{" +
                "requester=" + requester +
                ", plotOwner=" + plotOwner +
                ", plotId=" + plotId + // --- NEW ---
                ", worldName='" + worldName + '\'' +
                ", currentRadius=" + currentRadius +
                ", requestedRadius=" + requestedRadius +
                ", cost=" + cost +
                ", status=" + getStatus() +
                '}';
    }
}
