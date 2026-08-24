package com.aegisguard.snapshots;

import java.util.UUID;

/** Persisted change-detection state for one automatically backed-up player plot. */
final class AutomaticBackupState {
    private final UUID plotId;
    private volatile String fingerprint;
    private volatile long lastBackupAt;
    private volatile long lastCheckedAt;
    private volatile String outcome;

    AutomaticBackupState(UUID plotId, String fingerprint, long lastBackupAt,
                         long lastCheckedAt, String outcome) {
        this.plotId = plotId;
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.lastBackupAt = Math.max(0L, lastBackupAt);
        this.lastCheckedAt = Math.max(0L, lastCheckedAt);
        this.outcome = outcome == null ? "" : outcome;
    }

    synchronized void checked(long now, String outcome) {
        lastCheckedAt = now;
        this.outcome = outcome == null ? "" : outcome;
    }

    synchronized void backedUp(String fingerprint, long now, String outcome) {
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        lastBackupAt = now;
        lastCheckedAt = now;
        this.outcome = outcome == null ? "" : outcome;
    }

    UUID plotId() { return plotId; }
    String fingerprint() { return fingerprint; }
    long lastBackupAt() { return lastBackupAt; }
    long lastCheckedAt() { return lastCheckedAt; }
    String outcome() { return outcome; }
}
