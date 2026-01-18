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

    /**
     * Actor type for tracking who made a decision on the expansion request.
     * Used by GUIs and admin tools to display decision source.
     */
    public enum DecisionActorType {
        /** No decision has been made yet (request is pending) */
        NONE,
        /** Decision was made by the system automatically (auto-approval/denial) */
        SYSTEM,
        /** Decision was made by an admin player */
        ADMIN,
        /** Decision was made by auto-approval feature */
        AUTO
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

    /** UUID of the actor who made the decision (null if SYSTEM/AUTO or pending). */
    private volatile UUID decisionActor;

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
                DecisionBy.NONE,
                null
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
                (status == null || status == Status.PENDING) ? DecisionBy.NONE : DecisionBy.ADMIN,
                null
        );
    }

    /**
     * Load constructor with DecisionBy (backwards compatible).
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
                decisionBy,
                null
        );
    }

    /**
     * Full load constructor (recommended for new datastore writes).
     * Use this when loading from SQL/YML so timestamps, status, decisionBy, and decisionActor are preserved.
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
                            DecisionBy decisionBy,
                            UUID decisionActor) {

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
            this.decisionActor = null;
        } else {
            this.decisionBy = (decisionBy == null) ? DecisionBy.ADMIN : decisionBy;
            this.decisionActor = decisionActor;
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

    /**
     * Returns the UUID of the actor who made the decision.
     * May be null if the decision was made by SYSTEM/AUTO or if still pending.
     *
     * @return UUID of the decision maker, or null
     */
    public UUID getDecisionActor() {
        return decisionActor;
    }

    /**
     * Returns the type of actor that made the decision.
     * Maps DecisionBy to DecisionActorType for GUI compatibility.
     *
     * @return DecisionActorType indicating who/what made the decision
     */
    public DecisionActorType getDecisionActorType() {
        if (status == Status.PENDING || decisionBy == null) {
            return DecisionActorType.NONE;
        }

        return switch (decisionBy) {
            case NONE -> DecisionActorType.NONE;
            case AUTO -> DecisionActorType.AUTO;
            case ADMIN -> DecisionActorType.ADMIN;
        };
    }

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

    /**
     * Returns the OfflinePlayer who made the decision, if available.
     *
     * @return OfflinePlayer of the decision maker, or null if not applicable
     */
    public OfflinePlayer getDecisionActorPlayer() {
        if (decisionActor == null) return null;
        return Bukkit.getOfflinePlayer(decisionActor);
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
        setDecision(Status.APPROVED, DecisionBy.ADMIN, null);
    }

    /**
     * Backwards-friendly: denying via legacy call is treated as ADMIN denial.
     * If you want AUTO, use denyAuto().
     */
    public synchronized void deny() {
        setDecision(Status.DENIED, DecisionBy.ADMIN, null);
    }

    /** Auto-approval helper (Instant Mode). */
    public synchronized void approveAuto() {
        setDecision(Status.APPROVED, DecisionBy.AUTO, null);
    }

    /** Auto-denial helper (Instant Mode). */
    public synchronized void denyAuto() {
        setDecision(Status.DENIED, DecisionBy.AUTO, null);
    }

    /** Admin explicit helpers (nice for clarity in manager/GUI code). */
    public synchronized void approveAdmin() {
        setDecision(Status.APPROVED, DecisionBy.ADMIN, null);
    }

    public synchronized void denyAdmin() {
        setDecision(Status.DENIED, DecisionBy.ADMIN, null);
    }

    /**
     * Approve with a specific admin actor for audit trail.
     *
     * @param adminUuid UUID of the admin who approved the request
     */
    public synchronized void approveAdmin(UUID adminUuid) {
        setDecision(Status.APPROVED, DecisionBy.ADMIN, adminUuid);
    }

    /**
     * Deny with a specific admin actor for audit trail.
     *
     * @param adminUuid UUID of the admin who denied the request
     */
    public synchronized void denyAdmin(UUID adminUuid) {
        setDecision(Status.DENIED, DecisionBy.ADMIN, adminUuid);
    }

    /** Optional helper if admin GUI wants to revert a decision. */
    public synchronized void setPending() {
        this.status = Status.PENDING;
        this.decisionTimestamp = 0L;
        this.decisionBy = DecisionBy.NONE;
        this.decisionActor = null;
    }

    private void setDecision(Status newStatus, DecisionBy by, UUID actor) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
        this.decisionBy = (by == null) ? DecisionBy.ADMIN : by;
        this.decisionTimestamp = System.currentTimeMillis();
        this.decisionActor = actor;
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

    /**
     * Language-engine friendly key for decision actor type.
     */
    public String getDecisionActorTypeLangKey() {
        return switch (getDecisionActorType()) {
            case NONE   -> "expansion_actor_type_none";
            case SYSTEM -> "expansion_actor_type_system";
            case ADMIN  -> "expansion_actor_type_admin";
            case AUTO   -> "expansion_actor_type_auto";
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
                ", decisionActor=" + decisionActor +
                ", timestamp=" + timestamp +
                ", decisionTimestamp=" + decisionTimestamp +
                '}';
    }
}
