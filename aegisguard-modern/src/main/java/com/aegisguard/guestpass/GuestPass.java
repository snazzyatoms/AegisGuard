package com.aegisguard.guestpass;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Milestone 2 (Temporary Guest Passes) - a single, time-limited grant of access to one plot for
 * one player.
 *
 * Guest Passes are deliberately stored separately from {@code Plot#getPlayerRoles()} (permanent
 * trust). They are purely additive: losing a pass (by revocation or expiry) never removes a
 * player's permanent role, and holding a pass never grants management rights over the plot.
 *
 * The permission token snapshot is captured at issue time so a pass keeps behaving the way the
 * owner explicitly chose, even if a future release changes what a preset grants by default.
 *
 * Duration can be {@link GuestPassMode#REAL_TIME} (wall-clock expiry, the compatible default) or
 * {@link GuestPassMode#ACTIVE_PLAYTIME} (remaining millis that only decrement while the recipient
 * is online). Active-playtime session fields are mutable so join/quit/sweep can pause and resume
 * without replacing the whole pass object.
 */
public final class GuestPass {

    private final UUID playerId;
    private final String playerName;
    private final GuestPassPreset preset;
    private final Set<String> permissions;
    private final UUID issuerId;
    private final String issuerName;
    private final long issuedAt;
    private final long expiresAt;
    private final GuestPassMode mode;

    /** ACTIVE_PLAYTIME: frozen remaining when paused; {@code < 0} means never expires. */
    private volatile long remainingMillis;

    /** ACTIVE_PLAYTIME: epoch millis when the current online session started counting; {@code 0} = paused. */
    private volatile long sessionStartedAt;

    public GuestPass(UUID playerId, String playerName, GuestPassPreset preset, Set<String> permissions,
                      UUID issuerId, String issuerName, long issuedAt, long expiresAt) {
        this(playerId, playerName, preset, permissions, issuerId, issuerName, issuedAt, expiresAt,
                GuestPassMode.REAL_TIME, 0L, 0L);
    }

    public GuestPass(UUID playerId, String playerName, GuestPassPreset preset, Set<String> permissions,
                      UUID issuerId, String issuerName, long issuedAt, long expiresAt,
                      GuestPassMode mode, long remainingMillis, long sessionStartedAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = (playerName == null || playerName.isBlank()) ? "Unknown" : playerName;
        this.preset = Objects.requireNonNull(preset, "preset");
        this.permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
        this.issuerId = issuerId;
        this.issuerName = (issuerName == null || issuerName.isBlank()) ? "Unknown" : issuerName;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.mode = (mode == null) ? GuestPassMode.REAL_TIME : mode;
        this.remainingMillis = remainingMillis;
        this.sessionStartedAt = Math.max(0L, sessionStartedAt);
    }

    public static GuestPass issue(UUID playerId, String playerName, GuestPassPreset preset,
                                   UUID issuerId, String issuerName, long durationMillis) {
        return issue(playerId, playerName, preset, issuerId, issuerName, durationMillis, GuestPassMode.REAL_TIME);
    }

    public static GuestPass issue(UUID playerId, String playerName, GuestPassPreset preset,
                                   UUID issuerId, String issuerName, long durationMillis, GuestPassMode mode) {
        long now = System.currentTimeMillis();
        GuestPassMode resolved = (mode == null) ? GuestPassMode.REAL_TIME : mode;

        if (resolved == GuestPassMode.ACTIVE_PLAYTIME) {
            long remaining = durationMillis <= 0 ? -1L : durationMillis;
            // Start paused; GuestPassService resumes if the recipient is currently online.
            return new GuestPass(playerId, playerName, preset, preset.getPermissions(),
                    issuerId, issuerName, now, 0L, GuestPassMode.ACTIVE_PLAYTIME, remaining, 0L);
        }

        long expires = durationMillis <= 0 ? 0L : now + durationMillis;
        return new GuestPass(playerId, playerName, preset, preset.getPermissions(),
                issuerId, issuerName, now, expires, GuestPassMode.REAL_TIME, 0L, 0L);
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public GuestPassPreset getPreset() { return preset; }
    public Set<String> getPermissions() { return permissions; }
    public UUID getIssuerId() { return issuerId; }
    public String getIssuerName() { return issuerName; }
    public long getIssuedAt() { return issuedAt; }
    public GuestPassMode getMode() { return mode; }

    /** Absolute expiry timestamp (epoch millis) for REAL_TIME. {@code 0} means "never expires". */
    public long getExpiresAt() { return expiresAt; }

    /** Frozen remaining for ACTIVE_PLAYTIME (may be mid-session; prefer {@link #getRemainingMillis(long)}). */
    public long getStoredRemainingMillis() { return remainingMillis; }

    /** {@code 0} when paused / offline; otherwise the epoch millis when counting started. */
    public long getSessionStartedAt() { return sessionStartedAt; }

    public boolean isActivePlaytime() {
        return mode == GuestPassMode.ACTIVE_PLAYTIME;
    }

    public boolean isSessionActive() {
        return isActivePlaytime() && sessionStartedAt > 0L;
    }

    /** A pass with no expiry never expires; otherwise expires once remaining time reaches zero. */
    public boolean isExpired(long now) {
        if (mode == GuestPassMode.ACTIVE_PLAYTIME) {
            if (remainingMillis < 0L) return false;
            return getRemainingMillis(now) <= 0L;
        }
        return expiresAt > 0 && now >= expiresAt;
    }

    public long getRemainingMillis(long now) {
        if (mode == GuestPassMode.ACTIVE_PLAYTIME) {
            if (remainingMillis < 0L) return Long.MAX_VALUE;
            if (remainingMillis == 0L) return 0L;
            if (sessionStartedAt <= 0L) return remainingMillis;
            long elapsed = Math.max(0L, now - sessionStartedAt);
            return Math.max(0L, remainingMillis - elapsed);
        }
        if (expiresAt <= 0) return Long.MAX_VALUE;
        return Math.max(0L, expiresAt - now);
    }

    /**
     * Begins counting active playtime from {@code now}. No-op for real-time passes or if already
     * counting / already exhausted / never-expiring with nothing to track.
     *
     * @return {@code true} if session state changed
     */
    public synchronized boolean resumeSession(long now) {
        if (mode != GuestPassMode.ACTIVE_PLAYTIME) return false;
        if (remainingMillis < 0L) return false;
        if (remainingMillis <= 0L) return false;
        if (sessionStartedAt > 0L) return false;
        sessionStartedAt = now;
        return true;
    }

    /**
     * Freezes remaining playtime at {@code now} and clears the active session. Safe across quit,
     * server stop, and load recovery. No-op when already paused or not active-playtime.
     *
     * @return {@code true} if session state changed
     */
    public synchronized boolean freezeSession(long now) {
        if (mode != GuestPassMode.ACTIVE_PLAYTIME) return false;
        if (sessionStartedAt <= 0L) return false;
        if (remainingMillis >= 0L) {
            remainingMillis = getRemainingMillisUnlocked(now);
        }
        sessionStartedAt = 0L;
        return true;
    }

    /**
     * Checkpoints an online active-playtime session so a crash loses at most one sweep interval of
     * consumption. Remaining is rewritten and the session start is moved to {@code now}.
     *
     * @return {@code true} if state was updated
     */
    public synchronized boolean checkpointSession(long now) {
        if (mode != GuestPassMode.ACTIVE_PLAYTIME) return false;
        if (sessionStartedAt <= 0L) return false;
        if (remainingMillis < 0L) return false;
        long rem = getRemainingMillisUnlocked(now);
        remainingMillis = rem;
        sessionStartedAt = rem > 0L ? now : 0L;
        return true;
    }

    /**
     * After deserialize / server load: never count downtime as playtime. Clears any mid-session
     * marker without subtracting the offline gap from remaining.
     */
    public synchronized void pauseAfterLoad() {
        if (mode != GuestPassMode.ACTIVE_PLAYTIME) return;
        sessionStartedAt = 0L;
    }

    private long getRemainingMillisUnlocked(long now) {
        if (remainingMillis < 0L) return Long.MAX_VALUE;
        if (remainingMillis == 0L) return 0L;
        if (sessionStartedAt <= 0L) return remainingMillis;
        long elapsed = Math.max(0L, now - sessionStartedAt);
        return Math.max(0L, remainingMillis - elapsed);
    }

    public boolean hasPermission(String token) {
        if (token == null) return false;
        String needle = token.toUpperCase(java.util.Locale.ROOT);
        for (String p : permissions) {
            if (p == null) continue;
            String up = p.toUpperCase(java.util.Locale.ROOT);
            if ("ALL".equals(up) || up.equals(needle)) return true;
        }
        return false;
    }
}
