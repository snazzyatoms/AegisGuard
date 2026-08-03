package com.aegisguard.routes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Milestone 6 - a named, ordered sequence of checkpoints staff publish for players to browse
 * and discover. Never alters claim boundaries and never forces teleports.
 */
public final class Route {

    private final UUID id;
    private String name;
    private String description;
    private boolean enabled;
    private final List<Checkpoint> checkpoints;
    private double rewardMoney;
    private int rewardClaimBlocks;

    public Route(UUID id, String name, String description, boolean enabled,
                 List<Checkpoint> checkpoints, double rewardMoney, int rewardClaimBlocks) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = (name == null || name.isBlank()) ? "Route" : name.trim();
        this.description = description == null ? "" : description.trim();
        this.enabled = enabled;
        this.checkpoints = new ArrayList<>(checkpoints == null ? List.of() : checkpoints);
        this.rewardMoney = Math.max(0.0D, rewardMoney);
        this.rewardClaimBlocks = Math.max(0, rewardClaimBlocks);
    }

    public static Route create(String name) {
        return new Route(UUID.randomUUID(), name, "", true, List.of(), 0.0D, 0);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? this.name : name.trim();
    }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<Checkpoint> getCheckpoints() { return Collections.unmodifiableList(checkpoints); }
    public double getRewardMoney() { return rewardMoney; }
    public void setRewardMoney(double rewardMoney) { this.rewardMoney = Math.max(0.0D, rewardMoney); }
    public int getRewardClaimBlocks() { return rewardClaimBlocks; }
    public void setRewardClaimBlocks(int rewardClaimBlocks) {
        this.rewardClaimBlocks = Math.max(0, rewardClaimBlocks);
    }

    public int size() { return checkpoints.size(); }

    public Checkpoint getCheckpoint(int index) {
        if (index < 0 || index >= checkpoints.size()) return null;
        return checkpoints.get(index);
    }

    public Checkpoint getCheckpoint(UUID checkpointId) {
        if (checkpointId == null) return null;
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpointId.equals(checkpoint.getId())) return checkpoint;
        }
        return null;
    }

    public int indexOf(UUID checkpointId) {
        if (checkpointId == null) return -1;
        for (int i = 0; i < checkpoints.size(); i++) {
            if (checkpointId.equals(checkpoints.get(i).getId())) return i;
        }
        return -1;
    }

    public void addCheckpoint(Checkpoint checkpoint) {
        if (checkpoint == null) return;
        checkpoints.add(checkpoint);
    }

    public boolean removeCheckpoint(UUID checkpointId) {
        return checkpoints.removeIf(c -> c != null && Objects.equals(c.getId(), checkpointId));
    }

    public void clearCheckpoints() {
        checkpoints.clear();
    }

    /** Next checkpoint after {@code discoveredCount} discoveries (0 = first stop). */
    public Checkpoint nextAfter(int discoveredCount) {
        if (discoveredCount < 0) discoveredCount = 0;
        if (discoveredCount >= checkpoints.size()) return null;
        return checkpoints.get(discoveredCount);
    }

    public boolean isComplete(int discoveredCount) {
        return !checkpoints.isEmpty() && discoveredCount >= checkpoints.size();
    }
}
