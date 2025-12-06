package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // Simple in-memory caches for convenience lookups
    private final Map<UUID, List<Estate>> estatesByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Estate>>> estatesByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    // Core queries
    private static final String UPSERT_PLOT =
            "INSERT INTO aegis_plots (plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, flags) " +
            "VALUES (?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE owner_uuid=?, owner_name=?, flags=?";

    private static final String SELECT_ALL  = "SELECT * FROM aegis_plots";
    private static final String DELETE_PLOT = "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_ALL  = "DELETE FROM aegis_plots WHERE owner_uuid = ?";

    public SQLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    // ------------------------------------------------------------------------
    // Connection & schema
    // ------------------------------------------------------------------------

    private void connect() {
        ConfigurationSection db = plugin.cfg().raw().getConfigurationSection("storage.database");
        String type = plugin.cfg().raw().getString("storage.type", "sqlite");

        HikariConfig config = new HikariConfig();
        config.setPoolName("AegisGuard-Pool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setIdleTimeout(600_000L);    // 10 minutes
        config.setMaxLifetime(1_800_000L);  // 30 minutes
        config.setLeakDetectionThreshold(10_000L);

        if (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb")) {
            if (db == null) {
                plugin.getLogger().severe("MySQL/MariaDB selected but storage.database section is missing!");
                return;
            }
            String host = db.getString("host", "localhost");
            int port = db.getInt("port", 3306);
            String database = db.getString("database", "aegisguard");
            boolean useSSL = db.getBoolean("useSSL", false);

            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=" + useSSL + "&useUnicode=true&characterEncoding=utf8";
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(db.getString("username", "root"));
            config.setPassword(db.getString("password", ""));
        } else {
            // SQLite
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        this.hikari = new HikariDataSource(config);

        // Basic schema: keep compatible with existing installs
        try (Connection conn = hikari.getConnection(); Statement s = conn.createStatement()) {
            s.execute(
                "CREATE TABLE IF NOT EXISTS aegis_plots (" +
                " plot_id VARCHAR(36) PRIMARY KEY," +
                " owner_uuid VARCHAR(36)," +
                " owner_name VARCHAR(64)," +
                " world VARCHAR(64)," +
                " x1 INT, z1 INT, x2 INT, z2 INT," +
                " flags TEXT" +
                ")"
            );
            s.execute(
                "CREATE TABLE IF NOT EXISTS aegis_wilderness (" +
                " id BIGINT PRIMARY KEY," +
                " world VARCHAR(64)," +
                " x INT, y INT, z INT," +
                " type VARCHAR(64)," +
                " data BLOB" +
                ")"
            );
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error while creating tables: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Core lifecycle
    // ------------------------------------------------------------------------

    @Override
    public void load() {
        estatesByOwner.clear();
        estatesByChunk.clear();

        if (hikari == null) {
            plugin.getLogger().severe("Hikari not initialized; SQL datastore load aborted.");
            return;
        }

        int count = 0;

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    String ownerStr = rs.getString("owner_uuid");
                    UUID ownerId = (ownerStr == null || ownerStr.isEmpty()) ? null : UUID.fromString(ownerStr);
                    String name = rs.getString("owner_name");  // this actually stores plot/estate name
                    String worldName = rs.getString("world");

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("Skipping plot " + plotId + ": world '" + worldName + "' is missing.");
                        continue;
                    }

                    int x1 = rs.getInt("x1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int z2 = rs.getInt("z2");

                    // Full vertical column from min to max height
                    Location min = new Location(world, x1, world.getMinHeight(), z1);
                    Location max = new Location(world, x2, world.getMaxHeight(), z2);
                    Cuboid region = new Cuboid(min, max);

                    // isGuild isn't persisted in this schema, default to false
                    Estate estate = new Estate(plotId, name, ownerId, false, world, region);

                    // Rehydrate flags from serialized string
                    String flagsRaw = rs.getString("flags");
                    if (flagsRaw != null && !flagsRaw.isEmpty()) {
                        for (String part : flagsRaw.split(",")) {
                            String trimmed = part.trim();
                            if (trimmed.isEmpty()) continue;
                            String[] kv = trimmed.split(":");
                            if (kv.length == 2) {
                                estate.setFlag(kv[0], Boolean.parseBoolean(kv[1]));
                            }
                        }
                    }

                    addEstateToCache(estate);
                    plugin.getEstateManager().registerEstateFromLoad(estate);
                    count++;

                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped invalid plot row: " + ex.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading plots from SQL: " + e.getMessage());
            e.printStackTrace();
        }

        plugin.getLogger().info("Loaded " + count + " estates from SQL datastore.");
        isDirty = false;
    }

    // NOTE: For SQL, each operation writes directly to the DB, so save()/saveSync()
    // are effectively no-ops but kept for interface parity.
    @Override
    public void save() {
        // All saves are done per-operation via saveEstate/removeEstate/etc.
        isDirty = false;
    }

    @Override
    public void saveSync() {
        // Nothing to flush; DB writes are already committed.
        isDirty = false;
    }

    // ------------------------------------------------------------------------
    // Estate persistence
    // ------------------------------------------------------------------------

    @Override
    public void saveEstate(Estate estate) {
        if (estate == null || hikari == null) return;

        addEstateToCache(estate);
        isDirty = true;

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {

                int x1 = estate.getRegion().getLowerNE().getBlockX();
                int z1 = estate.getRegion().getLowerNE().getBlockZ();
                int x2 = estate.getRegion().getUpperSW().getBlockX();
                int z2 = estate.getRegion().getUpperSW().getBlockZ();

                // INSERT section
                ps.setString(1, estate.getId().toString());
                ps.setString(2, estate.getOwnerId() == null ? "" : estate.getOwnerId().toString());
                ps.setString(3, estate.getName());
                ps.setString(4, estate.getWorld().getName());
                ps.setInt(5, x1);
                ps.setInt(6, z1);
                ps.setInt(7, x2);
                ps.setInt(8, z2);

                StringBuilder sb = new StringBuilder();
                if (estate.getFlags() != null) {
                    estate.getFlags().forEach((k, v) -> sb.append(k).append(":").append(v).append(","));
                }
                ps.setString(9, sb.toString());

                // UPDATE section
                ps.setString(10, estate.getOwnerId() == null ? "" : estate.getOwnerId().toString());
                ps.setString(11, estate.getName());
                ps.setString(12, sb.toString());

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving plot " + estate.getId() + " to SQL: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void updateEstateOwner(Estate estate, UUID newOwner, boolean isGuild) {
        // EstateManager should have already updated the Estate object.
        // Here we just persist the new state.
        saveEstate(estate);
    }

    @Override
    public void removeEstate(UUID plotId) {
        if (plotId == null || hikari == null) return;

        // Remove from in-memory caches & manager
        Estate estate = plugin.getEstateManager().getEstate(plotId);
        if (estate != null) {
            removeEstateFromCache(estate);
            plugin.getEstateManager().removeEstate(plotId);
        }

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                ps.setString(1, plotId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting plot " + plotId + " from SQL: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Legacy alias for older code paths; not part of IDataStore.
     */
    public void deleteEstate(UUID id) {
        removeEstate(id);
    }

    @Override
    public void removeAllPlots(UUID owner) {
        if (owner == null || hikari == null) return;

        // Clear in-memory
        List<Estate> estates = estatesByOwner.getOrDefault(owner, Collections.emptyList());
        for (Estate estate : estates) {
            removeEstateFromCache(estate);
            plugin.getEstateManager().removeEstate(estate.getId());
        }
        estatesByOwner.remove(owner);

        // Clear DB
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_ALL)) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting all plots for " + owner + " from SQL: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // ------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------

    @Override
    public Estate getEstateAt(Location loc) {
        if (loc == null) return null;
        Map<String, Set<Estate>> worldMap = estatesByChunk.get(loc.getWorld().getName());
        if (worldMap == null) return null;

        String key = (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4);
        Set<Estate> candidates = worldMap.get(key);
        if (candidates != null) {
            for (Estate e : candidates) {
                if (e.getRegion().contains(loc)) return e;
            }
        }
        return null;
    }

    @Override
    public Estate getEstate(UUID owner, UUID plotId) {
        if (plotId == null) return null;
        return plugin.getEstateManager().getEstate(plotId);
    }

    @Override
    public List<Estate> getEstates(UUID owner) {
        if (owner == null) return Collections.emptyList();
        return estatesByOwner.getOrDefault(owner, Collections.emptyList());
    }

    @Override
    public Collection<Estate> getAllEstates() {
        return plugin.getEstateManager().getAllEstates();
    }

    @Override
    public Collection<Estate> getEstatesForSale() {
        // Uses in-memory state; if you later persist sale state to SQL, this will still work.
        return plugin.getEstateManager().getAllEstates().stream()
                .filter(Estate::isForSale)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Estate> getEstatesForAuction() {
        // Wire auction support via a flag, same idea as in YMLDataStore.
        return plugin.getEstateManager().getAllEstates().stream()
                .filter(e -> e.getFlag("auction"))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID excludeId) {
        // Use the in-memory estate list as the single source of truth
        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            if (excludeId != null && estate.getId().equals(excludeId)) continue;
            if (!estate.getWorld().getName().equalsIgnoreCase(world)) continue;

            Cuboid r = estate.getRegion();
            int ex1 = r.getLowerNE().getBlockX();
            int ez1 = r.getLowerNE().getBlockZ();
            int ex2 = r.getUpperSW().getBlockX();
            int ez2 = r.getUpperSW().getBlockZ();

            if (x1 <= ex2 && x2 >= ex1 && z1 <= ez2 && z2 >= ez1) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Roles, bans, wilderness logging
    // ------------------------------------------------------------------------

    @Override
    public void addEstate(Estate estate) {
        saveEstate(estate);
    }

    @Override
    public void createEstate(UUID owner, Location c1, Location c2) {
        // Typically handled via EstateManager#createEstate; no-op here.
    }

    @Override
    public void addPlayerRole(Estate estate, UUID uuid, String role) {
        if (estate == null || uuid == null) return;
        estate.setMember(uuid, role);
        saveEstate(estate);
    }

    @Override
    public void removePlayerRole(Estate estate, UUID uuid) {
        if (estate == null || uuid == null) return;
        estate.removeMember(uuid);
        saveEstate(estate);
    }

    @Override
    public void removeBannedEstates() {
        // Treat a "banned" flag as the signal for removal, consistent with YMLDataStore.
        List<UUID> toRemove = plugin.getEstateManager().getAllEstates().stream()
                .filter(e -> e.getFlag("banned"))
                .map(Estate::getId)
                .collect(Collectors.toList());

        for (UUID id : toRemove) {
            removeEstate(id);
        }
    }

    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID uuid) {
        // Stub: actual wilderness logging implementation can be added here
        // when WildernessRevertTask requirements are finalized.
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        // Stub: this can query aegis_wilderness and restore blocks if/when needed.
    }

    // ------------------------------------------------------------------------
    // Dirty tracking
    // ------------------------------------------------------------------------

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    // ------------------------------------------------------------------------
    // Internal caching helpers
    // ------------------------------------------------------------------------

    private void addEstateToCache(Estate estate) {
        if (estate.getOwnerId() != null) {
            estatesByOwner
                    .computeIfAbsent(estate.getOwnerId(), k -> new ArrayList<>())
                    .add(estate);
        }

        int cX1 = estate.getRegion().getLowerNE().getBlockX() >> 4;
        int cZ1 = estate.getRegion().getLowerNE().getBlockZ() >> 4;
        int cX2 = estate.getRegion().getUpperSW().getBlockX() >> 4;
        int cZ2 = estate.getRegion().getUpperSW().getBlockZ() >> 4;

        Map<String, Set<Estate>> worldMap =
                estatesByChunk.computeIfAbsent(estate.getWorld().getName(), k -> new ConcurrentHashMap<>());

        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                worldMap.computeIfAbsent(x + ";" + z, k -> ConcurrentHashMap.newKeySet()).add(estate);
            }
        }
    }

    private void removeEstateFromCache(Estate estate) {
        if (estate.getOwnerId() != null) {
            List<Estate> list = estatesByOwner.get(estate.getOwnerId());
            if (list != null) {
                list.removeIf(e -> e.getId().equals(estate.getId()));
                if (list.isEmpty()) {
                    estatesByOwner.remove(estate.getOwnerId());
                }
            }
        }

        Map<String, Set<Estate>> worldMap = estatesByChunk.get(estate.getWorld().getName());
        if (worldMap != null) {
            int cX1 = estate.getRegion().getLowerNE().getBlockX() >> 4;
            int cZ1 = estate.getRegion().getLowerNE().getBlockZ() >> 4;
            int cX2 = estate.getRegion().getUpperSW().getBlockX() >> 4;
            int cZ2 = estate.getRegion().getUpperSW().getBlockZ() >> 4;

            for (int x = cX1; x <= cX2; x++) {
                for (int z = cZ1; z <= cZ2; z++) {
                    String key = x + ";" + z;
                    Set<Estate> set = worldMap.get(key);
                    if (set != null) {
                        set.removeIf(e -> e.getId().equals(estate.getId()));
                        if (set.isEmpty()) {
                            worldMap.remove(key);
                        }
                    }
                }
            }

            if (worldMap.isEmpty()) {
                estatesByChunk.remove(estate.getWorld().getName());
            }
        }
    }
}
