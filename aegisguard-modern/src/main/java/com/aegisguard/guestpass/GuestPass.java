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

    public GuestPass(UUID playerId, String playerName, GuestPassPreset preset, Set<String> permissions,
                      UUID issuerId, String issuerName, long issuedAt, long expiresAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = (playerName == null || playerName.isBlank()) ? "Unknown" : playerName;
        this.preset = Objects.requireNonNull(preset, "preset");
        this.permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
        this.issuerId = issuerId;
        this.issuerName = (issuerName == null || issuerName.isBlank()) ? "Unknown" : issuerName;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static GuestPass issue(UUID playerId, String playerName, GuestPassPreset preset,
                                   UUID issuerId, String issuerName, long durationMillis) {
        long now = System.currentTimeMillis();
        long expires = durationMillis <= 0 ? 0L : now + durationMillis;
        return new GuestPass(playerId, playerName, preset, preset.getPermissions(), issuerId, issuerName, now, expires);
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public GuestPassPreset getPreset() { return preset; }
    public Set<String> getPermissions() { return permissions; }
    public UUID getIssuerId() { return issuerId; }
    public String getIssuerName() { return issuerName; }
    public long getIssuedAt() { return issuedAt; }

    /** Absolute expiry timestamp (epoch millis). {@code 0} means "never expires". */
    public long getExpiresAt() { return expiresAt; }

    /** A pass with {@code expiresAt <= 0} never expires; otherwise expires once {@code now} reaches it. */
    public boolean isExpired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public long getRemainingMillis(long now) {
        if (expiresAt <= 0) return Long.MAX_VALUE;
        return Math.max(0L, expiresAt - now);
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
