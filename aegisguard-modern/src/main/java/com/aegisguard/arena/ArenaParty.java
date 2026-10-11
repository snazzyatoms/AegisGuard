package com.aegisguard.arena;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight pre-run party. Leadership has no combat advantage.
 *
 * <p>Member/invite sets are concurrent: on Folia a member's command thread
 * mutates them while the arena tick thread iterates (matchmaking, invite
 * purge), and callers treat member order as unspecified (leader is tracked
 * separately via {@link #leaderId}).
 */
public final class ArenaParty {

    private final UUID partyId;
    private volatile UUID leaderId;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingInvites = ConcurrentHashMap.newKeySet();
    private volatile long inviteExpireAt;
    private volatile String selectedArenaId;

    public ArenaParty(UUID leaderId) {
        this.partyId = UUID.randomUUID();
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.members.add(leaderId);
    }

    public UUID getPartyId() { return partyId; }
    public UUID getLeaderId() { return leaderId; }
    public void setLeaderId(UUID leaderId) {
        if (leaderId != null && members.contains(leaderId)) {
            this.leaderId = leaderId;
        }
    }

    public Set<UUID> getMembers() { return Set.copyOf(members); }
    public int size() { return members.size(); }

    public boolean isMember(UUID id) { return id != null && members.contains(id); }
    public boolean isLeader(UUID id) { return id != null && id.equals(leaderId); }

    public boolean addMember(UUID id) {
        if (id == null) return false;
        pendingInvites.remove(id);
        return members.add(id);
    }

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
        pendingInvites.add(id);
        inviteExpireAt = expireAt;
    }

    public boolean hasInvite(UUID id) {
        return id != null && pendingInvites.contains(id) && System.currentTimeMillis() <= inviteExpireAt;
    }

    public boolean acceptInvite(UUID id) {
        if (!hasInvite(id)) return false;
        return addMember(id);
    }

    public void declineInvite(UUID id) {
        if (id != null) pendingInvites.remove(id);
    }

    public void purgeExpiredInvites() {
        if (System.currentTimeMillis() > inviteExpireAt) pendingInvites.clear();
    }

    public String getSelectedArenaId() { return selectedArenaId; }
    public void setSelectedArenaId(String selectedArenaId) { this.selectedArenaId = selectedArenaId; }
}
