package com.aegisguard.audit;

/**
 * High-impact staff/admin action categories recorded by {@link AuditService}.
 *
 * Milestone 1 scope rule: only these categories are recorded. Ordinary player actions
 * (claiming, trusting, chatting, etc.) are deliberately NOT logged here.
 */
public enum AuditCategory {
    SNAPSHOT_RESTORE,
    DOCTOR_REPAIR,
    MIGRATION,
    ADMIN_BYPASS,
    CLAIM_BLOCK_ADJUST,

    /** Milestone 2 (Temporary Guest Passes): creation, revocation, and expiry. */
    GUEST_PASS,

    /** Milestone 3 (Emergency Plot Lockdown): activation and deactivation. */
    LOCKDOWN,

    /** Role/claim protection: grant, revoke, lock, and undo. */
    ROLE_CHANGE,

    /** Guardian Succession: ownership transfer, assume, and rollback. */
    OWNERSHIP_TRANSFER,

    /** Caravans & Trade Routes: dispatch, arrival, fail, and cancel. */
    CARAVAN,

    /** Milestone 7 (Alliance Access): join/leave/disband and per-plot access toggles. */
    ALLIANCE
}
