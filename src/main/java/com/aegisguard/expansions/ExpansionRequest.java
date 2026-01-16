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
 * - Audit trail: tracks whether a decision was made by AUTO or ADMIN (or NONE).
 */
public class ExpansionRequest {

    public enum Status {
        PENDING,
        APPROVED,
        DENIED
    }

    /**
     * Optional audit trail for approvals/denials.
     * NONE = no decision yet (pending) or decision source unknown.
     * AUTO = auto-approval/auto-denial path
     * ADMIN = approved/denied by an admin (or legacy behavior)
     */
    public enum DecisionBy {
        NONE,
        AUTO,
        ADMIN
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

    /** Audit trail source for decision. */
    private volatile DecisionBy decisionBy;

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

        this(
                requester,
                plotOwner,
                plotId,
                worldName,
                currentRadius,
                requestedRadius,
                cost,
                System.currentTimeMillis(),
                Status.PENDING,
                0L,
                DecisionBy.NONE
        );
    }

    /**
     * Legacy load constructor (backwards compatible).
     * If you're loading older saved data that doesn't include decisionBy,
     * we infer ADMIN for decided requests, NONE for pending.
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

        this(
                requester,
                plotOwner,
                plotId,
                worldName,
                currentRadius,
                requestedRadius,
                cost,
                timestamp,
                status,
                decisionTimestamp,
                (status == null || status == Status.PENDING) ? DecisionBy.NONE : DecisionBy.ADMIN
        );
    }

    /**
     * Full load constructor (recommended for new datastore writes).
     * Use this when loading from SQL/YML so timestamps, status, and decisionBy are preserved.
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
                            long decisionTimestamp,
                            DecisionBy decisionBy) {

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

        // Keep it sane:
        // - Pending requests should always be NONE (unless you intentionally track "pending created by X", which we don't).
        // - Non-pending defaults to ADMIN if null.
        if (this.status == Status.PENDING) {
            this.decisionBy = DecisionBy.NONE;
            this.decisionTimestamp = 0L;
        } else {
            this.decisionBy = (decisionBy == null) ? DecisionBy.ADMIN : decisionBy;
        }
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

    public DecisionBy getDecisionBy() { return decisionBy; }

    public boolean isAutoDecision() {
        return decisionBy == DecisionBy.AUTO;
    }

    public boolean isAdminDecision() {
        return decisionBy == DecisionBy.ADMIN;
    }

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

    /**
     * Backwards-friendly: approving via legacy call is treated as ADMIN approval.
     * If you want AUTO, use approveAuto().
     */
    public synchronized void approve() {
        setDecision(Status.APPROVED, DecisionBy.ADMIN);
    }

    /**
     * Backwards-friendly: denying via legacy call is treated as ADMIN denial.
     * If you want AUTO, use denyAuto().
     */
    public synchronized void deny() {
        setDecision(Status.DENIED, DecisionBy.ADMIN);
    }

    /** Auto-approval helper (Instant Mode). */
    public synchronized void approveAuto() {
        setDecision(Status.APPROVED, DecisionBy.AUTO);
    }

    /** Auto-denial helper (Instant Mode). */
    public synchronized void denyAuto() {
        setDecision(Status.DENIED, DecisionBy.AUTO);
    }

    /** Admin explicit helpers (nice for clarity in manager/GUI code). */
    public synchronized void approveAdmin() {
        setDecision(Status.APPROVED, DecisionBy.ADMIN);
    }

    public synchronized void denyAdmin() {
        setDecision(Status.DENIED, DecisionBy.ADMIN);
    }

    /** Optional helper if admin GUI wants to revert a decision. */
    public synchronized void setPending() {
        this.status = Status.PENDING;
        this.decisionTimestamp = 0L;
        this.decisionBy = DecisionBy.NONE;
    }

    private void setDecision(Status newStatus, DecisionBy by) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
        this.decisionBy = (by == null) ? DecisionBy.ADMIN : by;
        this.decisionTimestamp = System.currentTimeMillis();
    }

    /**
     * Language-engine friendly key for status.
     * GUIs / managers can feed this into plugin.gui().tr(...)
     */
    public String getStatusLangKey() {
        return switch (status) {
            case APPROVED -> "expansion_status_approved";
            case DENIED   -> "expansion_status_denied";
            case PENDING  -> "expansion_status_pending";
        };
    }

    /**
     * Language-engine friendly key for decision source.
     * (Add these to your language packs so the Admin GUI can show "Approved by: Auto", etc.)
     */
    public String getDecisionByLangKey() {
        return switch (decisionBy) {
            case AUTO  -> "expansion_decision_by_auto";
            case ADMIN -> "expansion_decision_by_admin";
            case NONE  -> "expansion_decision_by_none";
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
                ", decisionBy=" + decisionBy +
                ", timestamp=" + timestamp +
                ", decisionTimestamp=" + decisionTimestamp +
                '}';
    }
}
