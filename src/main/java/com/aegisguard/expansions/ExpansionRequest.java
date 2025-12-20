package com.aegisguard.expansions;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * ExpansionRequest
 * - A small data object representing a player's request to expand a plot.
 * - Backwards-friendly: keeps the old approved/denied style methods,
 *   but internally uses a single Status field for correctness.
 */
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

    /** When the request was created (ms since epoch). */
    private final long timestamp;

    /** When it was approved/denied (ms since epoch). 0 = still pending. */
    private volatile long decisionTimestamp;

    /** Single source of truth for state. */
    private volatile Status status;

    // --------------------------------------------------
    // Constructors
    // --------------------------------------------------

    /** Creates a new request (PENDING). */
    public ExpansionRequest(UUID requester,
                            UUID plotOwner,
                            UUID plotId,
                            String worldName,
                            int currentRadius,
                            int requestedRadius,
                            double cost) {

        this(requester, plotOwner, plotId, worldName, currentRadius, requestedRadius, cost,
                System.currentTimeMillis(), Status.PENDING, 0L);
    }

    /**
     * Load constructor for datastore rehydration.
     * Use this when loading from SQL/YML so timestamps and status are preserved.
     */
    public ExpansionRequest(UUID requester,
                            UUID plotOwner,
                            UUID plotId,
                            String worldName,
                            int currentRadius,
                            int requestedRadius,
                            double cost,
                            long timestamp,
                            Status status,
                            long decisionTimestamp) {

        this.requester = Objects.requireNonNull(requester, "requester");
        this.plotOwner = Objects.requireNonNull(plotOwner, "plotOwner");
        this.plotId = Objects.requireNonNull(plotId, "plotId");
        this.worldName = (worldName == null) ? "" : worldName;

        this.currentRadius = currentRadius;
        this.requestedRadius = requestedRadius;
        this.cost = cost;

        this.timestamp = timestamp;
        this.status = (status == null) ? Status.PENDING : status;
        this.decisionTimestamp = Math.max(0L, decisionTimestamp);
    }

    // --------------------------------------------------
    // Getters
    // --------------------------------------------------

    public UUID getRequester() { return requester; }
    public UUID getPlotOwner() { return plotOwner; }
    public UUID getPlotId() { return plotId; }
    public String getWorldName() { return worldName; }
    public int getCurrentRadius() { return currentRadius; }
    public int getRequestedRadius() { return requestedRadius; }
    public double getCost() { return cost; }
    public long getTimestamp() { return timestamp; }

    /** 0 if still pending. */
    public long getDecisionTimestamp() { return decisionTimestamp; }

    /**
     * Stable identifier you can use in GUIs/logging.
     * Does not require adding a new stored UUID field.
     */
    public String getRequestKey() {
        return requester + ":" + plotId + ":" + timestamp;
    }

    // --------------------------------------------------
    // Convenience (Bukkit)
    // --------------------------------------------------

    public World getWorld() {
        if (worldName == null || worldName.isBlank()) return null;
        return Bukkit.getWorld(worldName);
    }

    public OfflinePlayer getRequesterPlayer() {
        return Bukkit.getOfflinePlayer(requester);
    }

    public OfflinePlayer getOwnerPlayer() {
        return Bukkit.getOfflinePlayer(plotOwner);
    }

    // --------------------------------------------------
    // Status Logic (backwards-friendly)
    // --------------------------------------------------

    public Status getStatus() {
        return status;
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isDenied() {
        return status == Status.DENIED;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public synchronized void approve() {
        this.status = Status.APPROVED;
        this.decisionTimestamp = System.currentTimeMillis();
    }

    public synchronized void deny() {
        this.status = Status.DENIED;
        this.decisionTimestamp = System.currentTimeMillis();
    }

    /** Optional helper if admin GUI wants to revert a decision. */
    public synchronized void setPending() {
        this.status = Status.PENDING;
        this.decisionTimestamp = 0L;
    }

    /**
     * Language-engine friendly key.
     * GUIs / managers can feed this into plugin.gui().tr(...)
     */
    public String getStatusLangKey() {
        return switch (status) {
            case APPROVED -> "expansion_status_approved";
            case DENIED   -> "expansion_status_denied";
            case PENDING  -> "expansion_status_pending";
        };
    }

    // --------------------------------------------------
    // Utility
    // --------------------------------------------------

    public long getAgeMillis() {
        return Math.max(0L, System.currentTimeMillis() - timestamp);
    }

    @Override
    public String toString() {
        return "ExpansionRequest{" +
                "key=" + getRequestKey() +
                ", requester=" + requester +
                ", plotOwner=" + plotOwner +
                ", plotId=" + plotId +
                ", worldName='" + worldName + '\'' +
                ", currentRadius=" + currentRadius +
                ", requestedRadius=" + requestedRadius +
                ", cost=" + cost +
                ", status=" + status +
                ", timestamp=" + timestamp +
                ", decisionTimestamp=" + decisionTimestamp +
                '}';
    }
}
