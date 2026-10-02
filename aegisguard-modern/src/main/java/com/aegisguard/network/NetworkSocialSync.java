package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import com.aegisguard.alliance.Alliance;
import com.aegisguard.groups.PlotGroup;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Alliance / group roster sync. When networked, the shared SQL tables are
 * authoritative for membership so a member on any backend can speak on the
 * channel; local YAML files stay as the offline cache.
 *
 * <p>Merge rule at load: pull every network alliance/group into memory, then
 * re-push any local-only entities (one-time import for pre-network installs —
 * a kicked member resurrected this way can simply be kicked again).</p>
 */
public final class NetworkSocialSync {

    private final AegisGuard plugin;

    public NetworkSocialSync(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private NetworkService net() {
        return plugin.network();
    }

    private boolean ready() {
        NetworkService n = net();
        return n != null && n.isNetworked();
    }

    // ------------------------------------------------------------------
    // Write-through (call after local mutations; async)
    // ------------------------------------------------------------------

    public void pushAlliance(Alliance alliance) {
        if (!ready() || alliance == null) return;
        var store = net().store();
        var id = alliance.getId();
        var name = alliance.getName();
        var leader = alliance.getLeaderId();
        var title = alliance.rawChatTitle();
        var created = alliance.getCreatedAt();
        var members = Set.copyOf(alliance.getMemberIds());
        plugin.scheduler().runAsync(() -> store.syncAlliance(id, name, leader, title, created, members));
    }

    public void deleteAlliance(UUID allianceId) {
        if (!ready() || allianceId == null) return;
        var store = net().store();
        plugin.scheduler().runAsync(() -> store.deleteAlliance(allianceId));
    }

    public void pushGroup(PlotGroup group) {
        if (!ready() || group == null) return;
        var store = net().store();
        var id = group.getId();
        var name = group.getName();
        var leader = group.getLeader();
        var title = group.getChatTitle();
        var created = group.getCreatedAt();
        var members = Set.copyOf(group.getMembers().keySet());
        plugin.scheduler().runAsync(() -> store.syncGroup(id, name, leader, title, created, members));
    }

    public void deleteGroup(UUID groupId) {
        if (!ready() || groupId == null) return;
        var store = net().store();
        plugin.scheduler().runAsync(() -> store.deleteGroup(groupId));
    }

    // ------------------------------------------------------------------
    // Pull on boot (after local YAML load)
    // ------------------------------------------------------------------

    /** Merge network alliances into memory; re-push local-only ones. */
    public void pullAlliances(com.aegisguard.alliance.AllianceManager manager) {
        if (!ready() || manager == null) return;
        plugin.scheduler().runAsync(() -> {
            try {
                var rows = net().store().loadAlliances();
                for (Object[] row : rows) {
                    UUID id = (UUID) row[0];
                    String name = (String) row[1];
                    UUID leader = (UUID) row[2];
                    String title = (String) row[3];
                    long created = (Long) row[4];
                    Set<UUID> members = net().store().loadAllianceMembers(id);
                    manager.mergeNetworkAlliance(id, name, leader, title, created, members);
                }
                // Re-push locals that never made it to the network table.
                for (Alliance alliance : manager.alliances()) {
                    if (alliance == null) continue;
                    boolean known = false;
                    for (Object[] row : rows) {
                        if (alliance.getId().equals(row[0])) { known = true; break; }
                    }
                    if (!known) pushAlliance(alliance);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.FINE, "[NetworkSocial] alliance pull failed: " + t.getMessage());
            }
        });
    }

    public void pullGroups(com.aegisguard.groups.GroupManager manager) {
        if (!ready() || manager == null) return;
        plugin.scheduler().runAsync(() -> {
            try {
                var rows = net().store().loadGroups();
                for (Object[] row : rows) {
                    UUID id = (UUID) row[0];
                    String name = (String) row[1];
                    UUID leader = (UUID) row[2];
                    String title = (String) row[3];
                    long created = (Long) row[4];
                    Set<UUID> members = net().store().loadGroupMembers(id);
                    manager.mergeNetworkGroup(id, name, leader, title, created, members);
                }
                for (PlotGroup group : manager.groups()) {
                    if (group == null) continue;
                    boolean known = false;
                    for (Object[] row : rows) {
                        if (group.getId().equals(row[0])) { known = true; break; }
                    }
                    if (!known) pushGroup(group);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.FINE, "[NetworkSocial] group pull failed: " + t.getMessage());
            }
        });
    }
}
