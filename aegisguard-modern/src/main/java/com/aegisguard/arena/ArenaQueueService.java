package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arena matchmaking queue. Players queue per-arena (or {@code any} = the
 * arena closest to filling); whole parties queue as one unit via their
 * leader. When a queue holds enough members for an arena's minimum, the
 * service composes a party from the front of the queue and starts a run —
 * which lands everyone in the normal lobby/combat flow.
 *
 * <p>Queue entries are transient (in-memory) and pruned every tick of
 * offline players, players who entered a run, and stale parties.
 */
public final class ArenaQueueService {

    /** One queue slot: a whole party (leader uuid + member set at join time). */
    private record QueuedGroup(UUID leaderId, List<UUID> memberIds, long queuedAt) {}

    private final AegisGuard plugin;
    /** arenaId -> ordered queued groups. */
    private final Map<String, List<QueuedGroup>> queues = new ConcurrentHashMap<>();
    /** playerId -> arenaId they're queued for (any member, not just leaders). */
    private final Map<UUID, String> queuedPlayers = new ConcurrentHashMap<>();

    public ArenaQueueService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.arena() != null && plugin.arena().isEnabled()
                && plugin.getConfig().getBoolean("arena.queue.enabled", true);
    }

    public boolean isQueued(UUID playerId) {
        return queuedPlayers.containsKey(playerId);
    }

    public String queuedArena(UUID playerId) {
        return queuedPlayers.get(playerId);
    }

    /**
     * Queue a player for an arena — or, when {@code arenaId} is "any", the
     * enabled arena with the largest existing queue. Party leaders queue the
     * whole party; a non-leader in a party cannot queue alone.
     */
    public String joinQueue(Player player, String arenaId) {
        if (player == null) return fail("arena_invalid_player");
        if (!isEnabled()) return fail("arena_queue_disabled");
        if (plugin.arena().getRunForPlayer(player.getUniqueId()) != null) {
            return fail("arena_already_in_run");
        }
        if (queuedPlayers.containsKey(player.getUniqueId())) {
            return fail("arena_queue_already");
        }

        ArenaParty party = plugin.arena().getParty(player.getUniqueId());
        List<UUID> members;
        if (party != null && party.size() > 1) {
            if (!party.isLeader(player.getUniqueId())) {
                return fail("arena_queue_leader_only");
            }
            // getMembers() has no guaranteed order — the matched run's leader
            // is roster[0], so the party leader must head the list.
            members = new ArrayList<>();
            members.add(party.getLeaderId());
            for (UUID mid : party.getMembers()) {
                if (!mid.equals(party.getLeaderId())) members.add(mid);
            }
        } else {
            members = List.of(player.getUniqueId());
        }

        ArenaDefinition def = resolveArena(arenaId);
        if (def == null) return fail("arena_queue_none_open");
        if (members.size() > def.getMaxPlayers()) {
            return fail("arena_party_exceeds_max", "MAX", String.valueOf(def.getMaxPlayers()));
        }
        for (UUID mid : members) {
            Player p = Bukkit.getPlayer(mid);
            if (p == null || !p.isOnline()) return fail("arena_members_must_be_online");
            if (queuedPlayers.containsKey(mid)) return fail("arena_queue_member_busy");
            if (plugin.arena().getRunForPlayer(mid) != null) return fail("arena_member_in_run");
        }

        // List mutations everywhere else hold the queue's monitor (tick prune,
        // leave, drop, drain) — the join add must too, or a Folia command
        // thread racing the matchmaking tick corrupts the ArrayList.
        List<QueuedGroup> queue = queues.computeIfAbsent(def.getId(), k -> new ArrayList<>());
        synchronized (queue) {
            queue.add(new QueuedGroup(members.get(0), List.copyOf(members), System.currentTimeMillis()));
        }
        for (UUID mid : members) queuedPlayers.put(mid, def.getId());
        return null;
    }

    /** Remove a player (and their queued group) from queues. Returns true if removed. */
    public boolean leaveQueue(Player player) {
        if (player == null) return false;
        String arenaId = queuedPlayers.remove(player.getUniqueId());
        if (arenaId == null) return false;
        List<QueuedGroup> queue = queues.get(arenaId);
        if (queue == null) return true;
        synchronized (queue) {
            QueuedGroup hit = null;
            for (QueuedGroup g : queue) {
                if (g.memberIds().contains(player.getUniqueId())) { hit = g; break; }
            }
            if (hit != null) {
                queue.remove(hit);
                // If a whole party was queued, drop all its members too.
                for (UUID mid : hit.memberIds()) {
                    if (mid.equals(player.getUniqueId())) continue;
                    ArenaParty party = plugin.arena().getParty(mid);
                    if (party != null && party.isMember(player.getUniqueId())) {
                        queuedPlayers.remove(mid);
                    }
                }
            }
            if (queue.isEmpty()) queues.remove(arenaId);
        }
        return true;
    }

    /**
     * Matchmaking tick — runs on the arena scheduler. For each arena queue,
     * take groups off the front until {@code min_players} members are
     * collected (never exceeding max_players), compose a party, and start a
     * run. Remaining queued groups wait for the next match.
     */
    private long lastTickAt;

    public void tick() {
        if (!isEnabled()) return;
        // The host tick runs every second; matchmaking itself is throttled to
        // arena.queue.tick_seconds so queue churn doesn't need per-second work.
        long now = System.currentTimeMillis();
        long interval = Math.max(1L,
                plugin.getConfig().getLong("arena.queue.tick_seconds", 3L)) * 1000L;
        if (now - lastTickAt < interval) return;
        lastTickAt = now;
        for (Map.Entry<String, List<QueuedGroup>> e : new ArrayList<>(queues.entrySet())) {
            ArenaDefinition def = plugin.arena().getArena(e.getKey());
            List<QueuedGroup> queue = e.getValue();
            if (def == null || !def.isEnabled()) {
                // Arena deleted/disabled while players queued — drop them with notice.
                drainQueue(e.getKey(), queue, "arena_queue_closed");
                continue;
            }
            if (plugin.arena().countActiveRuns(def.getId()) >= def.getMaxActiveRuns()) {
                continue;
            }
            synchronized (queue) {
                pruneLocked(queue);
                List<QueuedGroup> picked = new ArrayList<>();
                int members = 0;
                for (QueuedGroup g : queue) {
                    if (members + g.memberIds().size() > def.getMaxPlayers()) continue;
                    picked.add(g);
                    members += g.memberIds().size();
                    if (members >= def.getMinPlayers()) break;
                }
                if (members < def.getMinPlayers()) continue;
                queue.removeAll(picked);
                if (queue.isEmpty()) queues.remove(e.getKey());
                List<UUID> roster = new ArrayList<>();
                for (QueuedGroup g : picked) {
                    roster.addAll(g.memberIds());
                    for (UUID mid : g.memberIds()) queuedPlayers.remove(mid);
                }
                startMatchedRun(def, roster);
            }
        }
    }

    /** Clear every queue (disable/shutdown path). */
    public void clearAll() {
        for (Map.Entry<String, List<QueuedGroup>> e : queues.entrySet()) {
            drainQueue(e.getKey(), e.getValue(), "arena_queue_closed");
        }
        queues.clear();
        queuedPlayers.clear();
    }

    /** Called when a player quits or enters a run — silently dequeues them. */
    public void dropPlayer(UUID playerId) {
        String arenaId = queuedPlayers.remove(playerId);
        if (arenaId == null) return;
        List<QueuedGroup> queue = queues.get(arenaId);
        if (queue == null) return;
        synchronized (queue) {
            queue.removeIf(g -> {
                if (!g.memberIds().contains(playerId)) return false;
                for (UUID mid : g.memberIds()) queuedPlayers.remove(mid);
                return true;
            });
            if (queue.isEmpty()) queues.remove(arenaId);
        }
    }

    // ------------------------------------------------------------ internals

    private void startMatchedRun(ArenaDefinition def, List<UUID> roster) {
        Player leader = Bukkit.getPlayer(roster.get(0));
        if (leader == null || !leader.isOnline()) {
            // Leader went offline between tick and dispatch — re-queue the rest.
            for (UUID mid : roster) queuedPlayers.put(mid, def.getId());
            List<QueuedGroup> queue = queues.computeIfAbsent(def.getId(), k -> new ArrayList<>());
            synchronized (queue) {
                queue.add(0, new QueuedGroup(roster.get(0), List.copyOf(roster), System.currentTimeMillis()));
            }
            return;
        }
        // Compose a synthetic party: first member leads, the rest join directly.
        ArenaParty party = plugin.arena().createParty(leader);
        for (int i = 1; i < roster.size(); i++) {
            party.addMember(roster.get(i));
            plugin.arena().putPlayerInParty(roster.get(i), party);
        }
        for (UUID mid : roster) {
            Player p = Bukkit.getPlayer(mid);
            if (p != null && p.isOnline()) {
                plugin.msg().send(p, "arena_queue_matched",
                        Map.of("ARENA", def.getDisplayName(), "COUNT", String.valueOf(roster.size())));
            }
        }
        String err = plugin.arena().tryStart(leader, def.getId());
        if (err != null) {
            plugin.msg().send(leader, err, plugin.arena().takeFailVars());
        }
    }

    private void pruneLocked(List<QueuedGroup> queue) {
        queue.removeIf(g -> {
            for (UUID mid : g.memberIds()) {
                Player p = Bukkit.getPlayer(mid);
                if (p == null || !p.isOnline()) {
                    for (UUID x : g.memberIds()) queuedPlayers.remove(x);
                    return true;
                }
                if (plugin.arena().getRunForPlayer(mid) != null) {
                    for (UUID x : g.memberIds()) queuedPlayers.remove(x);
                    return true;
                }
            }
            return false;
        });
    }

    private void drainQueue(String arenaId, List<QueuedGroup> queue, String msgKey) {
        if (queue == null) return;
        synchronized (queue) {
            for (QueuedGroup g : queue) {
                for (UUID mid : g.memberIds()) {
                    queuedPlayers.remove(mid);
                    Player p = Bukkit.getPlayer(mid);
                    if (p != null && p.isOnline()) plugin.msg().send(p, msgKey);
                }
            }
            queue.clear();
        }
        queues.remove(arenaId);
    }

    private ArenaDefinition resolveArena(String arenaId) {
        if (arenaId != null && !arenaId.isBlank() && !"any".equalsIgnoreCase(arenaId)) {
            ArenaDefinition def = plugin.arena().getArena(arenaId);
            return def != null && def.isEnabled() ? def : null;
        }
        // "any": the enabled arena with the longest queue (closest to filling).
        ArenaDefinition best = null;
        int bestDepth = -1;
        for (ArenaDefinition def : plugin.arena().allArenas()) {
            if (def == null || !def.isEnabled()) continue;
            List<QueuedGroup> q = queues.get(def.getId());
            int depth = q == null ? 0 : q.size();
            if (depth > bestDepth) { bestDepth = depth; best = def; }
        }
        return best;
    }

    private String fail(String key) {
        return plugin.arena().failKey(key);
    }

    private String fail(String key, String var, String value) {
        // Reuse the service's fail-var channel so the command can surface vars.
        return plugin.arena().failKey(key, var, value);
    }
}
