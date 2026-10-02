package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.network.NetworkModels.NetworkArrival;
import com.aegisguard.network.NetworkModels.NetworkEvent;
import com.aegisguard.network.NetworkModels.NetworkServer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQL access for the aegis_network_* tables. Every method borrows a pooled
 * connection from {@link SQLDataStore}; callers run these off the main thread
 * via the plugin scheduler's async helpers.
 */
public final class NetworkStore {

    private final AegisGuard plugin;

    public NetworkStore(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private Connection conn() throws SQLException {
        if (!(plugin.store() instanceof SQLDataStore sql)) {
            throw new SQLException("Network store requires SQLDataStore");
        }
        return sql.getConnection();
    }

    // ------------------------------------------------------------------
    // Server registry
    // ------------------------------------------------------------------

    public void heartbeat(String serverName, String displayName, String version, int onlinePlayers) {
        String sql = "REPLACE INTO aegis_network_servers " +
                "(server_name, display_name, plugin_version, online_players, last_seen) VALUES (?,?,?,?,?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serverName);
            ps.setString(2, displayName);
            ps.setString(3, version);
            ps.setInt(4, onlinePlayers);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log("heartbeat", e);
        }
    }

    public List<NetworkServer> servers() {
        List<NetworkServer> out = new ArrayList<>();
        try (Connection c = conn();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM aegis_network_servers")) {
            while (rs.next()) {
                out.add(new NetworkServer(
                        rs.getString("server_name"),
                        rs.getString("display_name"),
                        rs.getString("plugin_version"),
                        rs.getInt("online_players"),
                        rs.getLong("last_seen")));
            }
        } catch (SQLException e) {
            log("list servers", e);
        }
        return out;
    }

    /** Remove this backend from the registry on clean shutdown. */
    public void deregisterServer(String serverName) {
        if (serverName == null || serverName.isBlank()) return;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM aegis_network_servers WHERE server_name = ?")) {
            ps.setString(1, serverName);
            ps.executeUpdate();
        } catch (SQLException e) {
            log("deregister server", e);
        }
    }

    // ------------------------------------------------------------------
    // Pending arrivals
    // ------------------------------------------------------------------

    public void writeArrival(NetworkArrival arrival) {
        String sql = "REPLACE INTO aegis_network_arrivals " +
                "(player_uuid, target_server, kind, plot_id, beacon_id, world, x, y, z, yaw, pitch, issued_at, expires_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arrival.playerUuid().toString());
            ps.setString(2, arrival.targetServer());
            ps.setString(3, arrival.kind().name());
            ps.setString(4, arrival.plotId() != null ? arrival.plotId().toString() : null);
            ps.setString(5, arrival.beaconId() != null ? arrival.beaconId().toString() : null);
            ps.setString(6, arrival.world());
            ps.setDouble(7, arrival.x());
            ps.setDouble(8, arrival.y());
            ps.setDouble(9, arrival.z());
            ps.setFloat(10, arrival.yaw());
            ps.setFloat(11, arrival.pitch());
            ps.setLong(12, arrival.issuedAt());
            ps.setLong(13, arrival.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            log("write arrival", e);
        }
    }

    /** Read-and-delete the pending arrival for a joining player (one-shot). */
    public NetworkArrival consumeArrival(UUID playerUuid) {
        NetworkArrival found = null;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM aegis_network_arrivals WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        found = readArrival(rs);
                    }
                }
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_arrivals WHERE player_uuid = ?")) {
                del.setString(1, playerUuid.toString());
                del.executeUpdate();
            }
        } catch (SQLException e) {
            log("consume arrival", e);
        }
        return found;
    }

    public int countPendingArrivals() {
        try (Connection c = conn();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM aegis_network_arrivals")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log("count arrivals", e);
        }
        return 0;
    }

    public void pruneExpiredArrivals() {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM aegis_network_arrivals WHERE expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log("prune arrivals", e);
        }
    }

    private NetworkArrival readArrival(ResultSet rs) throws SQLException {
        String plot = rs.getString("plot_id");
        String beacon = rs.getString("beacon_id");
        NetworkArrival.ArrivalKind kind;
        try {
            kind = NetworkArrival.ArrivalKind.valueOf(rs.getString("kind"));
        } catch (Throwable t) {
            kind = NetworkArrival.ArrivalKind.PLOT_SPAWN;
        }
        return new NetworkArrival(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("target_server"),
                kind,
                plot != null ? UUID.fromString(plot) : null,
                beacon != null ? UUID.fromString(beacon) : null,
                rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"),
                rs.getLong("issued_at"), rs.getLong("expires_at"));
    }

    // ------------------------------------------------------------------
    // Event bus (chat relay + future network signals)
    // ------------------------------------------------------------------

    public void publishEvent(String originServer, String kind, String payload) {
        String sql = "INSERT INTO aegis_network_events (origin_server, kind, payload, created_at) VALUES (?,?,?,?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, originServer);
            ps.setString(2, kind);
            ps.setString(3, payload);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log("publish event", e);
        }
    }

    /** Events with id &gt; cursor, oldest first. */
    public List<NetworkEvent> eventsAfter(long cursor) {
        List<NetworkEvent> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM aegis_network_events WHERE id > ? ORDER BY id ASC LIMIT 500")) {
            ps.setLong(1, cursor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new NetworkEvent(
                            rs.getLong("id"),
                            rs.getString("origin_server"),
                            rs.getString("kind"),
                            rs.getString("payload"),
                            rs.getLong("created_at")));
                }
            }
        } catch (SQLException e) {
            log("poll events", e);
        }
        return out;
    }

    public void pruneEvents(long olderThanMs) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM aegis_network_events WHERE created_at < ?")) {
            ps.setLong(1, System.currentTimeMillis() - Math.max(60_000L, olderThanMs));
            ps.executeUpdate();
        } catch (SQLException e) {
            log("prune events", e);
        }
    }

    // ------------------------------------------------------------------
    // Shared player data (claim blocks + settings blob)
    // ------------------------------------------------------------------

    public String loadPlayerData(UUID playerUuid) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT data FROM aegis_player_data WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("data");
            }
        } catch (SQLException e) {
            log("load player data", e);
        }
        return null;
    }

    public void savePlayerData(UUID playerUuid, String data) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "REPLACE INTO aegis_player_data (player_uuid, data, updated_at) VALUES (?,?,?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, data);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log("save player data", e);
        }
    }

    // ------------------------------------------------------------------
    // Alliance / group roster sync
    // ------------------------------------------------------------------

    /** Sync a full alliance meta+roster snapshot (insert-or-replace members). */
    public void syncAlliance(UUID allianceId, String name, UUID leader, String chatTitle,
                             long createdAt, Set<UUID> members) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO aegis_network_alliances (alliance_id, name, leader_uuid, chat_title, created_at, updated_at) " +
                    "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, allianceId.toString());
                ps.setString(2, name);
                ps.setString(3, leader != null ? leader.toString() : null);
                ps.setString(4, chatTitle);
                ps.setLong(5, createdAt);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_alliance_members WHERE alliance_id = ?")) {
                del.setString(1, allianceId.toString());
                del.executeUpdate();
            }
            if (members != null) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO aegis_network_alliance_members (alliance_id, member_uuid, joined_at) VALUES (?,?,?)")) {
                    for (UUID member : members) {
                        if (member == null) continue;
                        ins.setString(1, allianceId.toString());
                        ins.setString(2, member.toString());
                        ins.setLong(3, System.currentTimeMillis());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
        } catch (SQLException e) {
            log("sync alliance", e);
        }
    }

    public void deleteAlliance(UUID allianceId) {
        try (Connection c = conn()) {
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_alliance_members WHERE alliance_id = ?")) {
                del.setString(1, allianceId.toString());
                del.executeUpdate();
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_alliances WHERE alliance_id = ?")) {
                del.setString(1, allianceId.toString());
                del.executeUpdate();
            }
        } catch (SQLException e) {
            log("delete alliance", e);
        }
    }

    /** All alliance meta rows: [id, name, leaderUuid, chatTitle, createdAt]. */
    public List<Object[]> loadAlliances() {
        List<Object[]> out = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM aegis_network_alliances")) {
            while (rs.next()) {
                out.add(new Object[]{
                        UUID.fromString(rs.getString("alliance_id")),
                        rs.getString("name"),
                        parseUuid(rs.getString("leader_uuid")),
                        rs.getString("chat_title"),
                        rs.getLong("created_at")});
            }
        } catch (SQLException e) {
            log("load alliances", e);
        }
        return out;
    }

    public Set<UUID> loadAllianceMembers(UUID allianceId) {
        Set<UUID> out = new LinkedHashSet<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT member_uuid FROM aegis_network_alliance_members WHERE alliance_id = ?")) {
            ps.setString(1, allianceId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        } catch (SQLException e) {
            log("load alliance members", e);
        }
        return out;
    }

    public void syncGroup(UUID groupId, String name, UUID leader, String chatTitle,
                          long createdAt, Set<UUID> members) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO aegis_network_groups (group_id, name, leader_uuid, chat_title, created_at, updated_at) " +
                    "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, groupId.toString());
                ps.setString(2, name);
                ps.setString(3, leader != null ? leader.toString() : null);
                ps.setString(4, chatTitle);
                ps.setLong(5, createdAt);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_group_members WHERE group_id = ?")) {
                del.setString(1, groupId.toString());
                del.executeUpdate();
            }
            if (members != null) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO aegis_network_group_members (group_id, member_uuid, joined_at) VALUES (?,?,?)")) {
                    for (UUID member : members) {
                        if (member == null) continue;
                        ins.setString(1, groupId.toString());
                        ins.setString(2, member.toString());
                        ins.setLong(3, System.currentTimeMillis());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
        } catch (SQLException e) {
            log("sync group", e);
        }
    }

    public void deleteGroup(UUID groupId) {
        try (Connection c = conn()) {
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_group_members WHERE group_id = ?")) {
                del.setString(1, groupId.toString());
                del.executeUpdate();
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM aegis_network_groups WHERE group_id = ?")) {
                del.setString(1, groupId.toString());
                del.executeUpdate();
            }
        } catch (SQLException e) {
            log("delete group", e);
        }
    }

    public List<Object[]> loadGroups() {
        List<Object[]> out = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM aegis_network_groups")) {
            while (rs.next()) {
                out.add(new Object[]{
                        UUID.fromString(rs.getString("group_id")),
                        rs.getString("name"),
                        parseUuid(rs.getString("leader_uuid")),
                        rs.getString("chat_title"),
                        rs.getLong("created_at")});
            }
        } catch (SQLException e) {
            log("load groups", e);
        }
        return out;
    }

    public Set<UUID> loadGroupMembers(UUID groupId) {
        Set<UUID> out = new LinkedHashSet<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT member_uuid FROM aegis_network_group_members WHERE group_id = ?")) {
            ps.setString(1, groupId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        } catch (SQLException e) {
            log("load group members", e);
        }
        return out;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    private void log(String phase, SQLException e) {
        plugin.getLogger().log(Level.FINE, "[NetworkStore] " + phase + " failed: " + e.getMessage(), e);
    }
}
