package com.aegisguard.groups;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * General-purpose player party: leader, members, timed invites.
 *
 * <p>Modeled on the arena party's proven concurrency shape: member and invite
 * sets are concurrent because on Folia a command thread can mutate membership
 * while the sidebar tick or another player's command iterates it. Member order
 * is unspecified; leadership is tracked separately via {@link #leaderId}.
 */
public final class Party {

    private final UUID partyId;
    private volatile UUID leaderId;
    private volatile String name;
    private volatile org.bukkit.Location rallyPoint;
    private volatile long rallyExpireAt;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Long> pendingInvites = new ConcurrentHashMap<>();

    public Party(UUID leaderId, String name) {
        this(leaderId, name, UUID.randomUUID());
    }

    /** Rehydrating constructor for persisted parties. */
    public Party(UUID leaderId, String name, UUID partyId) {
        this.partyId = partyId == null ? UUID.randomUUID() : partyId;
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.name = name;
        this.members.add(leaderId);
    }

    public UUID getPartyId() { return partyId; }
    public UUID getLeaderId() { return leaderId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setLeaderId(UUID leaderId) {
        if (leaderId != null && members.contains(leaderId)) {
            this.leaderId = leaderId;
        }
    }

    // ------------------------------------------------------------------
    // Rally point — leader plants it, members teleport in on their own call.

    public void rally(org.bukkit.Location location, long expireAt) {
        this.rallyPoint = location == null ? null : location.clone();
        this.rallyExpireAt = expireAt;
    }

    /** Live rally point, or null if none/expired (clears lazily). */
    public org.bukkit.Location rallyPoint() {
        if (rallyPoint == null) return null;
        if (System.currentTimeMillis() > rallyExpireAt) {
            rallyPoint = null;
            return null;
        }
        return rallyPoint.clone();
    }

    public Set<UUID> getMembers() { return Set.copyOf(members); }
    public int size() { return members.size(); }
    public boolean isEmpty() { return members.isEmpty(); }
    public boolean isMember(UUID id) { return id != null && members.contains(id); }
    public boolean isLeader(UUID id) { return id != null && id.equals(leaderId); }

    public boolean addMember(UUID id) {
        if (id == null) return false;
        pendingInvites.remove(id);
        return members.add(id);
    }

    /** Removes a member; leadership passes to an arbitrary remaining member. */
    public boolean removeMember(UUID id) {
        if (id == null) return false;
        pendingInvites.remove(id);
        boolean removed = members.remove(id);
        if (removed && id.equals(leaderId) && !members.isEmpty()) {
            leaderId = members.iterator().next();
        }
        return removed;
    }

    public void invite(UUID id, long expireAt) {
        if (id == null || members.contains(id)) return;
        pendingInvites.put(id, expireAt);
    }

    public boolean hasInvite(UUID id) {
        Long exp = id == null ? null : pendingInvites.get(id);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            pendingInvites.remove(id);
            return false;
        }
        return true;
    }

    public boolean acceptInvite(UUID id) {
        return hasInvite(id) && addMember(id);
    }

    public void declineInvite(UUID id) {
        if (id != null) pendingInvites.remove(id);
    }

    public void purgeExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(e -> e.getValue() < now);
    }
}
