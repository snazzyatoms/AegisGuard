package com.aegisguard.groups;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlotGroup {

    private final UUID id;
    private String name;
    private String chatTitle;
    private UUID leader;
    private final Map<UUID, Long> members = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingInvites = new ConcurrentHashMap<>();
    private final long createdAt;
    private double treasuryBalance;
    private UUID linkedPlotId;
    private boolean starterClaimUsed;
    private long starterClaimedAt;
    private long starterRemovalLockUntil;

    public PlotGroup(UUID id, String name, UUID leader, long createdAt) {
        this(id, name, leader, createdAt, 0.0D, null, false, 0L, 0L);
    }

    public PlotGroup(UUID id, String name, UUID leader, long createdAt,
                     double treasuryBalance, UUID linkedPlotId,
                     boolean starterClaimUsed, long starterClaimedAt, long starterRemovalLockUntil) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.createdAt = createdAt;
        this.treasuryBalance = Math.max(0.0D, treasuryBalance);
        this.linkedPlotId = linkedPlotId;
        this.starterClaimUsed = starterClaimUsed;
        this.starterClaimedAt = starterClaimedAt;
        this.starterRemovalLockUntil = starterRemovalLockUntil;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getChatTitle() {
        return chatTitle;
    }

    public void setChatTitle(String chatTitle) {
        this.chatTitle = (chatTitle == null || chatTitle.isBlank()) ? null : chatTitle.trim();
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Map<UUID, Long> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public Set<UUID> getMemberIds() {
        return Collections.unmodifiableSet(members.keySet());
    }

    public boolean isMember(UUID uuid) {
        return uuid != null && members.containsKey(uuid);
    }

    public long getJoinedAt(UUID uuid) {
        if (uuid == null) return 0L;
        return members.getOrDefault(uuid, 0L);
    }

    public void addMember(UUID uuid, long joinedAt) {
        if (uuid == null) return;
        members.put(uuid, joinedAt);
    }

    public void removeMember(UUID uuid) {
        if (uuid == null) return;
        members.remove(uuid);
        pendingInvites.remove(uuid);
    }

    public int size() {
        return members.size();
    }

    public Map<UUID, Long> getPendingInvites() {
        return Collections.unmodifiableMap(pendingInvites);
    }

    public boolean hasInvite(UUID uuid) {
        return uuid != null && pendingInvites.containsKey(uuid);
    }

    public void addInvite(UUID uuid, long invitedAt) {
        if (uuid == null) return;
        pendingInvites.put(uuid, invitedAt);
    }

    public void removeInvite(UUID uuid) {
        if (uuid == null) return;
        pendingInvites.remove(uuid);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getTreasuryBalance() {
        return Math.max(0.0D, treasuryBalance);
    }

    public void setTreasuryBalance(double treasuryBalance) {
        this.treasuryBalance = Math.max(0.0D, treasuryBalance);
    }

    public void addTreasuryFunds(double amount) {
        if (amount <= 0.0D) return;
        this.treasuryBalance = Math.max(0.0D, this.treasuryBalance + amount);
    }

    public boolean withdrawTreasuryFunds(double amount) {
        if (amount <= 0.0D) return true;
        if (this.treasuryBalance + 0.000001D < amount) return false;
        this.treasuryBalance = Math.max(0.0D, this.treasuryBalance - amount);
        return true;
    }

    public UUID getLinkedPlotId() {
        return linkedPlotId;
    }

    public void setLinkedPlotId(UUID linkedPlotId) {
        this.linkedPlotId = linkedPlotId;
    }

    public boolean hasLinkedPlot() {
        return linkedPlotId != null;
    }

    public boolean isStarterClaimUsed() {
        return starterClaimUsed;
    }

    public void setStarterClaimUsed(boolean starterClaimUsed) {
        this.starterClaimUsed = starterClaimUsed;
    }

    public long getStarterClaimedAt() {
        return starterClaimedAt;
    }

    public void setStarterClaimedAt(long starterClaimedAt) {
        this.starterClaimedAt = starterClaimedAt;
    }

    public long getStarterRemovalLockUntil() {
        return starterRemovalLockUntil;
    }

    public void setStarterRemovalLockUntil(long starterRemovalLockUntil) {
        this.starterRemovalLockUntil = starterRemovalLockUntil;
    }

    public boolean isStarterRemovalLocked(long now) {
        return starterRemovalLockUntil > now;
    }
}
