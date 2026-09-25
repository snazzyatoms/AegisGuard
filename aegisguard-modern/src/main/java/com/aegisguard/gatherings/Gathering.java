package com.aegisguard.gatherings;

import java.util.Collections;
import java.util.LinkedHashSet;
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
    private final Set<UUID> issuedPasses = Collections.synchronizedSet(new LinkedHashSet<>());

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
        return endsAt <= 0L || now < endsAt;
    }

    public Set<UUID> issuedPasses() {
        return issuedPasses;
    }

    public boolean markIssued(UUID playerId) {
        return playerId != null && issuedPasses.add(playerId);
    }
}
