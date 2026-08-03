package com.aegisguard.alliance;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 7 - a player alliance that is completely separate from plot ownership, money,
 * rentals, and administration. Membership alone grants nothing; each plot must opt in via
 * its Alliance Access toggles.
 */
public final class Alliance {

    private final UUID id;
    private String name;
    private UUID leaderId;
    private final long createdAt;
    private final Map<UUID, Long> members = new ConcurrentHashMap<>();
    private final Map<UUID, Long> invites = new ConcurrentHashMap<>();

    public Alliance(UUID id, String name, UUID leaderId, long createdAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = (name == null || name.isBlank()) ? "Alliance" : name.trim();
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.createdAt = createdAt <= 0 ? System.currentTimeMillis() : createdAt;
        this.members.put(this.leaderId, this.createdAt);
    }

    public static Alliance create(String name, UUID leaderId) {
        return new Alliance(UUID.randomUUID(), name, leaderId, System.currentTimeMillis());
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? this.name : name.trim();
    }
    public UUID getLeaderId() { return leaderId; }
    public long getCreatedAt() { return createdAt; }

    public Set<UUID> getMemberIds() { return Collections.unmodifiableSet(members.keySet()); }
    public Map<UUID, Long> getMembers() { return Collections.unmodifiableMap(members); }
    public Map<UUID, Long> getInvites() { return Collections.unmodifiableMap(invites); }

    public boolean isMember(UUID playerId) {
        return playerId != null && members.containsKey(playerId);
    }

    public boolean isLeader(UUID playerId) {
        return playerId != null && playerId.equals(leaderId);
    }

    public boolean isInvited(UUID playerId) {
        return playerId != null && invites.containsKey(playerId);
    }

    public void addMember(UUID playerId, long joinedAt) {
        if (playerId == null) return;
        members.put(playerId, joinedAt <= 0 ? System.currentTimeMillis() : joinedAt);
        invites.remove(playerId);
    }

    public boolean removeMember(UUID playerId) {
        if (playerId == null || playerId.equals(leaderId)) return false;
        return members.remove(playerId) != null;
    }

    public void addInvite(UUID playerId, long invitedAt) {
        if (playerId == null || isMember(playerId)) return;
        invites.put(playerId, invitedAt <= 0 ? System.currentTimeMillis() : invitedAt);
    }

    public boolean removeInvite(UUID playerId) {
        return playerId != null && invites.remove(playerId) != null;
    }

    public int size() { return members.size(); }
}
