package com.aegisguard.gatherings;

import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.guestpass.GuestPassPreset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Timed Open House listing on one plot. */
public final class Gathering {

    private final UUID plotId;
    private final UUID hostId;
    private final String hostName;
    private final String plotName;
    private final long startedAt;
    private volatile long endsAt;
    private volatile boolean grantGuestPass;
    /** Recipient and exact issue time, so ending a gathering never revokes a replacement pass. */
    private final Map<UUID, Long> issuedPasses = new ConcurrentHashMap<>();

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
        return Map.copyOf(issuedPasses);
    }

    public void recordIssued(UUID playerId, long issuedAt) {
        if (playerId != null && issuedAt > 0L) issuedPasses.put(playerId, issuedAt);
    }

    public void forgetIssued(UUID playerId) {
        issuedPasses.remove(playerId);
    }

    public boolean issuedByThisGathering(GuestPass pass) {
        return pass != null && pass.getPreset() == GuestPassPreset.VISITOR
                && pass.getIssuedAt() == issuedPasses.getOrDefault(pass.getPlayerId(), 0L);
    }

    public GuestPass renewedPass(GuestPass previous, long now, long passEnd) {
        if (!issuedByThisGathering(previous) || passEnd <= now) return null;
        return new GuestPass(previous.getPlayerId(), previous.getPlayerName(), GuestPassPreset.VISITOR,
                previous.getPermissions(), hostId, hostName, now, passEnd);
    }
}
