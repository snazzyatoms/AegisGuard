package com.aegisguard.gatherings;

import com.aegisguard.guestpass.GuestPass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Timed Open House listing on one plot. */
public final class Gathering {

    private final UUID plotId;
    private final UUID hostId;
    private final String hostName;
    private final String plotName;
    private final long startedAt;
    private volatile long endsAt;
    private volatile boolean grantGuestPass;
    private final Map<UUID, Long> issuedPasses = Collections.synchronizedMap(new LinkedHashMap<>());

    public Gathering(UUID plotId, UUID hostId, String hostName, String plotName,
                     long startedAt, long endsAt, boolean grantGuestPass) {
        this.plotId = plotId;
        this.hostId = hostId;
        this.hostName = hostName == null || hostName.isBlank() ? "Unknown" : hostName;
        this.plotName = plotName == null || plotName.isBlank() ? "Plot" : plotName;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.grantGuestPass = grantGuestPass;
    }

    public UUID plotId() { return plotId; }
    public UUID hostId() { return hostId; }
    public String hostName() { return hostName; }
    public String plotName() { return plotName; }
    public long startedAt() { return startedAt; }
    public long endsAt() { return endsAt; }
    public boolean grantGuestPass() { return grantGuestPass; }

    public void setEndsAt(long endsAt) { this.endsAt = endsAt; }
    public void setGrantGuestPass(boolean grantGuestPass) { this.grantGuestPass = grantGuestPass; }

    public boolean isLive(long now) {
        return endsAt > 0L && now < endsAt;
    }

    public Map<UUID, Long> issuedPasses() {
        return issuedPasses;
    }

    public Set<UUID> issuedVisitors() {
        return Set.copyOf(issuedPasses.keySet());
    }

    public boolean markIssued(UUID playerId) {
        return recordIssued(playerId, System.currentTimeMillis());
    }

    public boolean recordIssued(UUID playerId, long timestamp) {
        if (playerId == null) return false;
        issuedPasses.put(playerId, timestamp);
        return true;
    }

    public boolean issuedByThisGathering(GuestPass pass) {
        if (pass == null) return false;
        Long issuedAt = issuedPasses.get(pass.getPlayerId());
        return issuedAt != null && issuedAt == pass.getIssuedAt();
    }

    public GuestPass renewedPass(GuestPass pass, long newIssuedAt, long newExpiresAt) {
        if (pass == null || !issuedByThisGathering(pass)) return null;
        return new GuestPass(
                pass.getPlayerId(),
                pass.getPlayerName(),
                pass.getPreset(),
                pass.getPermissions(),
                hostId,
                hostName,
                newIssuedAt,
                newExpiresAt
        );
    }
}
