package com.aegisguard.routes;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Milestone 6 - per-player discovery progress for a single route.
 */
public final class RouteProgress {

    private final UUID playerId;
    private final UUID routeId;
    private final Set<UUID> discoveredCheckpointIds = new LinkedHashSet<>();
    private boolean rewardClaimed;

    public RouteProgress(UUID playerId, UUID routeId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.routeId = Objects.requireNonNull(routeId, "routeId");
    }

    public UUID getPlayerId() { return playerId; }
    public UUID getRouteId() { return routeId; }

    public Set<UUID> getDiscoveredCheckpointIds() {
        return Collections.unmodifiableSet(discoveredCheckpointIds);
    }

    public int getDiscoveredCount() {
        return discoveredCheckpointIds.size();
    }

    public boolean hasDiscovered(UUID checkpointId) {
        return checkpointId != null && discoveredCheckpointIds.contains(checkpointId);
    }

    /** Returns true if this was a newly discovered checkpoint. */
    public boolean discover(UUID checkpointId) {
        if (checkpointId == null) return false;
        return discoveredCheckpointIds.add(checkpointId);
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean rewardClaimed) {
        this.rewardClaimed = rewardClaimed;
    }

    public void replaceDiscovered(Set<UUID> ids) {
        discoveredCheckpointIds.clear();
        if (ids != null) discoveredCheckpointIds.addAll(ids);
    }
}
