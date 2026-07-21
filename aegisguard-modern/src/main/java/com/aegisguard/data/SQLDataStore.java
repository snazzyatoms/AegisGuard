package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotDeleteEvent;
import com.aegisguard.flags.TriState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * SQLDataStore (v1.2.4 hardened)
 * - MySQL/MariaDB/SQLite support.
 * - Immediate commits per plot save (crash resistant).
 * - Dedicated DB executor (NOT Bukkit scheduler) so writes survive disable/reload flow.
 * - shutdown() flushes pending work + sync saves + closes pool.
 *
 * HARDENING:
 * - NEVER NULL collections
 * - Set-backed owner cache
 * - Chunk index overlap checks
 * - Deduplicate by plotId to prevent ghosts
 * - "Stopping" mode runs saves inline (no dropped async tasks)
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    private final Map<UUID, Set<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();

    private volatile boolean isDirty = false;
    private String storageType = "sqlite";

    // Hardcore async-survival layer
    private final ExecutorService dbExecutor;
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final Object pendingLock = new Object();
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final Set<Long> wildernessRevertsInFlight = ConcurrentHashMap.newKeySet();

    private record WildernessRevertRow(long id, String worldName, int x, int y, int z, String materialName) { }

    // --- TABLES ---
    private static final String CREATE_PLOTS_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_plots (" +
                    " plot_id VARCHAR(36) PRIMARY KEY," +
                    " owner_uuid VARCHAR(36)," +
                    " owner_name VARCHAR(16)," +
                    " world VARCHAR(64)," +
                    " x1 INT, z1 INT," +
                    " x2 INT, z2 INT," +
                    " level INT," +
                    " xp DOUBLE," +
                    " last_upkeep BIGINT," +
                    " flags TEXT," +
                    " roles TEXT," +
                    " settings TEXT" +
                    " )";

    private static final String CREATE_ZONES_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_zones (" +
                    " zone_id VARCHAR(36) PRIMARY KEY," +
                    " plot_id VARCHAR(36)," +
                    " name VARCHAR(32)," +
                    " x1 INT, y1 INT, z1 INT," +
                    " x2 INT, y2 INT, z2 INT," +
                    " renter VARCHAR(36)," +
                    " price DOUBLE," +
                    " expires BIGINT" +
                    " )";

    private static final String CREATE_STALLS_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_stalls (" +
                    " stall_id VARCHAR(96) PRIMARY KEY," +
                    " plot_id VARCHAR(36)," +
                    " owner_uuid VARCHAR(36)," +
                    " owner_name VARCHAR(32)," +
                    " title VARCHAR(64)," +
                    " zone_name VARCHAR(32)," +
                    " chest_x INT, chest_y INT, chest_z INT," +
                    " sign_x INT, sign_y INT, sign_z INT," +
                    " created_at BIGINT" +
                    " )";

    private static final String CREATE_STALL_LISTINGS_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_stall_listings (" +
                    " stall_id VARCHAR(96)," +
                    " chest_slot INT," +
                    " price DOUBLE," +
                    " currency VARCHAR(24)," +
                    " bundle_amount INT," +
                    " PRIMARY KEY (stall_id, chest_slot)" +
                    " )";

    private static final String CREATE_ZONE_META_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_zone_meta (" +
                    " zone_key VARCHAR(160) PRIMARY KEY," +
                    " spawn_x DOUBLE," +
                    " spawn_y DOUBLE," +
                    " spawn_z DOUBLE," +
                    " hotel_mode BOOLEAN," +
                    " guest_visit BOOLEAN," +
                    " guest_interact BOOLEAN," +
                    " guest_containers BOOLEAN," +
                    " guest_build BOOLEAN" +
                    " )";

    private static final String CREATE_ZONE_GUESTS_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_zone_guests (" +
                    " zone_key VARCHAR(160)," +
                    " guest_uuid VARCHAR(36)," +
                    " PRIMARY KEY (zone_key, guest_uuid)" +
                    " )";

    private static final String UPSERT_PLOT =
            "REPLACE INTO aegis_plots " +
                    "(plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, level, xp, last_upkeep, flags, roles, settings) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String DELETE_PLOT =
            "DELETE FROM aegis_plots WHERE plot_id = ?";

    private static final String DELETE_PLOTS_BY_OWNER =
            "DELETE FROM aegis_plots WHERE owner_uuid = ?";

    private static final String SELECT_PLOT_IDS_BY_OWNER =
            "SELECT plot_id FROM aegis_plots WHERE owner_uuid = ?";

    private static final String DELETE_ZONES_BY_PLOT =
            "DELETE FROM aegis_zones WHERE plot_id = ?";

    private static final String INSERT_ZONE =
            "INSERT INTO aegis_zones " +
                    "(zone_id, plot_id, name, x1, y1, z1, x2, y2, z2, renter, price, expires) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String DELETE_STALLS_BY_PLOT =
            "DELETE FROM aegis_stalls WHERE plot_id = ?";

    private static final String INSERT_STALL =
            "INSERT INTO aegis_stalls " +
                    "(stall_id, plot_id, owner_uuid, owner_name, title, zone_name, chest_x, chest_y, chest_z, sign_x, sign_y, sign_z, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String DELETE_STALL_LISTINGS_BY_PLOT =
            "DELETE FROM aegis_stall_listings WHERE stall_id LIKE ?";

    private static final String INSERT_STALL_LISTING =
            "INSERT INTO aegis_stall_listings " +
                    "(stall_id, chest_slot, price, currency, bundle_amount) VALUES (?,?,?,?,?)";

    private static final String DELETE_ZONE_META_BY_PLOT =
            "DELETE FROM aegis_zone_meta WHERE zone_key LIKE ?";

    private static final String DELETE_ZONE_GUESTS_BY_PLOT =
            "DELETE FROM aegis_zone_guests WHERE zone_key LIKE ?";

    private static final String INSERT_ZONE_META =
            "INSERT INTO aegis_zone_meta " +
                    "(zone_key, spawn_x, spawn_y, spawn_z, hotel_mode, guest_visit, guest_interact, guest_containers, guest_build) VALUES (?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_ZONE_GUEST =
            "INSERT INTO aegis_zone_guests (zone_key, guest_uuid) VALUES (?,?)";

    // Wilderness logging
    private static final String LOG_WILDERNESS =
            "INSERT INTO aegis_wilderness_log (world, x, y, z, old_material, new_material, timestamp, player_uuid) VALUES (?,?,?,?,?,?,?,?)";
    private static final String GET_REVERTABLE_BLOCKS =
            "SELECT id, world, x, y, z, old_material FROM aegis_wilderness_log "
                    + "WHERE timestamp < ? ORDER BY timestamp DESC, id DESC LIMIT ?";
    private static final String DELETE_WILDERNESS_BY_ID =
            "DELETE FROM aegis_wilderness_log WHERE id = ?";

    public SQLDataStore(AegisGuard plugin) {
        this.plugin = plugin;

        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AegisGuard-DB");
            t.setDaemon(false);
            return t;
        });

        connect();
    }

    private void queueDb(Runnable job) {
        if (job == null) return;

        // During shutdown, run inline so nothing gets dropped.
        if (stopping.get()) {
            try { job.run(); } catch (Throwable ignored) {}
            return;
        }

        pendingTasks.incrementAndGet();
        try {
            dbExecutor.execute(() -> {
                try {
                    job.run();
                } catch (Throwable ignored) {
                } finally {
                    int left = pendingTasks.decrementAndGet();
                    synchronized (pendingLock) {
                        if (left <= 0) pendingLock.notifyAll();
                    }
                }
            });
        } catch (RejectedExecutionException rex) {
            // Executor is shutting down unexpectedly: run inline and fix the counter.
            try { job.run(); } catch (Throwable ignored) {}
            int left = pendingTasks.decrementAndGet();
            synchronized (pendingLock) {
                if (left <= 0) pendingLock.notifyAll();
            }
        }
    }

    private void flushPending(long maxWaitMillis) {
        long end = System.currentTimeMillis() + maxWaitMillis;
        synchronized (pendingLock) {
            while (pendingTasks.get() > 0 && System.currentTimeMillis() < end) {
                try {
                    pendingLock.wait(50L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void connect() {
        ConfigurationSection db = plugin.cfg().raw().getConfigurationSection("storage.database");
        storageType = resolveStorageType(plugin.cfg().raw());

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("AegisGuard-Pool");
        cfg.setConnectionTimeout(30000);
        cfg.setLeakDetectionThreshold(10000);

        if (storageType.equalsIgnoreCase("mysql") || storageType.equalsIgnoreCase("mariadb")) {
            String host = db != null ? db.getString("host", "localhost") : "localhost";
            int port = db != null ? db.getInt("port", 3306) : 3306;
            String database = db != null ? db.getString("database", "aegisguard") : "aegisguard";
            boolean useSSL = db != null && db.getBoolean("useSSL", false);

            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true&autoReconnect=true");
            cfg.setUsername(db != null ? db.getString("username", "root") : "root");
            cfg.setPassword(db != null ? db.getString("password", "") : "");

            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            plugin.getLogger().info("Connecting to MySQL/MariaDB (" + host + ":" + port + ", DB=" + database + ")...");
        } else {
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

            cfg.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            cfg.setDriverClassName("org.sqlite.JDBC");
            cfg.setMaximumPoolSize(1);

            plugin.getLogger().info("Using local SQLite database file: " + file.getName());
        }

        this.hikari = new HikariDataSource(cfg);

        try (Connection conn = hikari.getConnection();
             Statement s = conn.createStatement()) {

            s.execute(CREATE_PLOTS_TABLE);
            s.execute(CREATE_ZONES_TABLE);
            s.execute(CREATE_STALLS_TABLE);
            s.execute(CREATE_STALL_LISTINGS_TABLE);
            s.execute(CREATE_ZONE_META_TABLE);
            s.execute(CREATE_ZONE_GUESTS_TABLE);

            if (storageType.equalsIgnoreCase("mysql") || storageType.equalsIgnoreCase("mariadb")) {
                s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness_log ( " +
                        "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                        "world VARCHAR(64), x INT, y INT, z INT, " +
                        "old_material VARCHAR(64), new_material VARCHAR(64), " +
                        "timestamp BIGINT, player_uuid VARCHAR(36) )");
            } else {
                s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness_log ( " +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "world TEXT, x INTEGER, y INTEGER, z INTEGER, " +
                        "old_material TEXT, new_material TEXT, " +
                        "timestamp INTEGER, player_uuid TEXT )");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Database Error: " + e.getMessage());
        }
    }

    private String resolveStorageType(org.bukkit.configuration.file.FileConfiguration config) {
        String configured = config.getString("storage.type");
        if (configured == null || configured.isBlank()) {
            configured = config.getString("storage.backend", "sqlite");
        }

        String normalized = configured == null ? "sqlite" : configured.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("sql") || normalized.equals("yml")) {
            return "sqlite";
        }
        return normalized;
    }

    @Override
    public void load() {
        plotsByOwner.clear();
        plotsByChunk.clear();

        int plotCount = 0;
        Map<UUID, Plot> plotsById = new HashMap<>();

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_plots");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                    String ownerName = rs.getString("owner_name");
                    String worldName = rs.getString("world");
                    if (worldName == null || worldName.isEmpty()) continue;

                    int x1 = rs.getInt("x1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int z2 = rs.getInt("z2");

                    Plot plot = new Plot(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2, rs.getLong("last_upkeep"));
                    plot.setLevel(rs.getInt("level"));
                    plot.setXp(rs.getDouble("xp"));

                    String flagsStr = rs.getString("flags");
                    if (flagsStr != null && !flagsStr.isEmpty()) {
                        for (String part : flagsStr.split(",")) {
                            String[] kv = part.split(":", 2);
                            if (kv.length == 2) plot.setFlag(kv[0], Boolean.parseBoolean(kv[1]));
                        }
                    }

                    String rolesStr = rs.getString("roles");
                    if (rolesStr != null && !rolesStr.isEmpty()) {
                        for (String part : rolesStr.split(",")) {
                            String[] kv = part.split(":", 2);
                            if (kv.length == 2) {
                                try { plot.setRole(UUID.fromString(kv[0]), kv[1]); } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }

                    String settings = rs.getString("settings");
                    if (settings != null && !settings.isEmpty()) applySettings(plot, settings);

                    cachePlot(plot);
                    plotsById.put(plotId, plot);
                    plotCount++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped invalid plot in DB.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        int zoneCount = 0;
        Map<String, Zone> zonesByKey = new HashMap<>();
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_zones");
             ResultSet rs = ps.executeQuery()) {

            long now = System.currentTimeMillis();

            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    Plot parent = plotsById.get(plotId);
                    if (parent == null) continue;

                    String name = rs.getString("name");

                    Zone zone = new Zone(
                            parent,
                            name,
                            rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                            rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
                    );

                    zone.setRentPrice(rs.getDouble("price"));

                    String renterStr = rs.getString("renter");
                    long expires = rs.getLong("expires");
                    if (renterStr != null && !renterStr.isEmpty() && expires > now) {
                        try {
                            UUID renter = UUID.fromString(renterStr);
                            zone.rentTo(renter, expires - now);
                        } catch (IllegalArgumentException ignored) {}
                    }

                    parent.addZone(zone);
                    zonesByKey.put(zoneStorageKey(parent, zone), zone);
                    zoneCount++;
                } catch (Exception ignored) {}
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_zone_meta");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    Zone zone = zonesByKey.get(rs.getString("zone_key"));
                    if (zone == null) continue;

                    World world = Bukkit.getWorld(zone.getParent().getWorld());
                    if (world != null) {
                        double x = rs.getDouble("spawn_x");
                        boolean hasSpawn = !rs.wasNull();
                        double y = rs.getDouble("spawn_y");
                        double z = rs.getDouble("spawn_z");
                        if (hasSpawn) {
                            zone.setSpawnLocation(new Location(world, x, y, z));
                        }
                    }

                    zone.setFlag("hotel_mode", rs.getBoolean("hotel_mode"));
                    zone.setFlag("guest_visit", rs.getBoolean("guest_visit"));
                    zone.setFlag("guest_interact", rs.getBoolean("guest_interact"));
                    zone.setFlag("guest_containers", rs.getBoolean("guest_containers"));
                    zone.setFlag("guest_build", rs.getBoolean("guest_build"));
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_zone_guests");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    Zone zone = zonesByKey.get(rs.getString("zone_key"));
                    if (zone == null) continue;
                    String guest = rs.getString("guest_uuid");
                    if (guest == null || guest.isBlank()) continue;
                    zone.addGuest(UUID.fromString(guest));
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int stallCount = 0;
        Map<String, MarketStall> stallsById = new HashMap<>();
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_stalls");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    Plot parent = plotsById.get(plotId);
                    if (parent == null) continue;

                    UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                    MarketStall stall = new MarketStall(
                            ownerId,
                            rs.getString("owner_name"),
                            parent.getWorld(),
                            rs.getInt("chest_x"),
                            rs.getInt("chest_y"),
                            rs.getInt("chest_z"),
                            rs.getInt("sign_x"),
                            rs.getInt("sign_y"),
                            rs.getInt("sign_z"),
                            rs.getString("title"),
                            rs.getString("zone_name"),
                            rs.getLong("created_at")
                    );

                    parent.addStall(stall);
                    stallsById.put(plotId + ":" + stall.getStorageKey(), stall);
                    stallCount++;
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_stall_listings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    MarketStall stall = stallsById.get(rs.getString("stall_id"));
                    if (stall == null) continue;
                    stall.setListing(
                            rs.getInt("chest_slot"),
                            new MarketStall.StallListing(
                                    rs.getDouble("price"),
                                    parseCurrencyType(rs.getString("currency")),
                                    rs.getInt("bundle_amount")
                            )
                    );
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        plugin.getLogger().info("Loaded " + plotCount + " plots, " + zoneCount + " zones, and " + stallCount + " stalls from Database.");
        isDirty = false;
    }

    @Override
    public void save() {
        for (Plot p : getAllPlots()) {
            if (p != null) savePlot(p);
        }
    }

    @Override
    public void saveSync() {
        for (Plot p : getAllPlots()) {
            if (p != null) savePlotInternal(p);
        }
        isDirty = false;
    }

    @Override
    public void savePlot(Plot plot) {
        if (plot == null) return;
        queueDb(() -> savePlotInternal(plot));
    }

    @Override
    public void savePlotSync(Plot plot) {
        if (plot == null) return;
        savePlotInternal(plot);
    }

    private void savePlotInternal(Plot plot) {
        if (hikari == null || hikari.isClosed()) return;

        try (Connection conn = hikari.getConnection()) {
            boolean auto = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {
                    ps.setString(1, plot.getPlotId().toString());
                    ps.setString(2, plot.getOwner().toString());
                    ps.setString(3, plot.getOwnerName());
                    ps.setString(4, plot.getWorld());
                    ps.setInt(5, plot.getX1());
                    ps.setInt(6, plot.getZ1());
                    ps.setInt(7, plot.getX2());
                    ps.setInt(8, plot.getZ2());
                    ps.setInt(9, plot.getLevel());
                    ps.setDouble(10, plot.getXp());
                    ps.setLong(11, plot.getLastUpkeepPayment());
                    ps.setString(12, plot.serializeFlags());
                    ps.setString(13, plot.serializeRoles());
                    ps.setString(14, serializeSettings(plot));
                    ps.executeUpdate();
                }

                // Zones: wipe + insert
                try (PreparedStatement del = conn.prepareStatement(DELETE_ZONES_BY_PLOT)) {
                    del.setString(1, plot.getPlotId().toString());
                    del.executeUpdate();
                }

                try (PreparedStatement del = conn.prepareStatement(DELETE_ZONE_META_BY_PLOT)) {
                    del.setString(1, plot.getPlotId().toString() + ":%");
                    del.executeUpdate();
                }

                try (PreparedStatement del = conn.prepareStatement(DELETE_ZONE_GUESTS_BY_PLOT)) {
                    del.setString(1, plot.getPlotId().toString() + ":%");
                    del.executeUpdate();
                }

                try (PreparedStatement del = conn.prepareStatement(DELETE_STALLS_BY_PLOT)) {
                    del.setString(1, plot.getPlotId().toString());
                    del.executeUpdate();
                }

                try (PreparedStatement del = conn.prepareStatement(DELETE_STALL_LISTINGS_BY_PLOT)) {
                    del.setString(1, plot.getPlotId().toString() + ":%");
                    del.executeUpdate();
                }

                if (!plot.getZones().isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(INSERT_ZONE)) {
                        for (Zone zone : plot.getZones()) {
                            ins.setString(1, UUID.randomUUID().toString());
                            ins.setString(2, plot.getPlotId().toString());
                            ins.setString(3, zone.getName());
                            ins.setInt(4, zone.getX1());
                            ins.setInt(5, zone.getY1());
                            ins.setInt(6, zone.getZ1());
                            ins.setInt(7, zone.getX2());
                            ins.setInt(8, zone.getY2());
                            ins.setInt(9, zone.getZ2());

                            UUID renter = zone.getRenter();
                            ins.setString(10, renter != null ? renter.toString() : null);
                            ins.setDouble(11, zone.getRentPrice());
                            ins.setLong(12, zone.getRentExpiration());

                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }

                    try (PreparedStatement ins = conn.prepareStatement(INSERT_ZONE_META)) {
                        for (Zone zone : plot.getZones()) {
                            String zoneKey = zoneStorageKey(plot, zone);
                            Location spawn = zone.getSpawnLocation();
                            ins.setString(1, zoneKey);
                            if (spawn != null) {
                                ins.setDouble(2, spawn.getX());
                                ins.setDouble(3, spawn.getY());
                                ins.setDouble(4, spawn.getZ());
                            } else {
                                ins.setNull(2, Types.DOUBLE);
                                ins.setNull(3, Types.DOUBLE);
                                ins.setNull(4, Types.DOUBLE);
                            }
                            ins.setBoolean(5, zone.isHotelMode());
                            ins.setBoolean(6, zone.getFlag("guest_visit", true));
                            ins.setBoolean(7, zone.getFlag("guest_interact", true));
                            ins.setBoolean(8, zone.getFlag("guest_containers", true));
                            ins.setBoolean(9, zone.getFlag("guest_build", false));
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }

                    try (PreparedStatement ins = conn.prepareStatement(INSERT_ZONE_GUEST)) {
                        for (Zone zone : plot.getZones()) {
                            String zoneKey = zoneStorageKey(plot, zone);
                            for (UUID guestId : zone.getGuestAccess().keySet()) {
                                if (guestId == null) continue;
                                ins.setString(1, zoneKey);
                                ins.setString(2, guestId.toString());
                                ins.addBatch();
                            }
                        }
                        ins.executeBatch();
                    }
                }

                if (!plot.getStalls().isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(INSERT_STALL)) {
                        for (MarketStall stall : plot.getStalls()) {
                            if (stall == null || stall.getOwnerId() == null) continue;

                            ins.setString(1, plot.getPlotId() + ":" + stall.getStorageKey());
                            ins.setString(2, plot.getPlotId().toString());
                            ins.setString(3, stall.getOwnerId().toString());
                            ins.setString(4, stall.getOwnerName());
                            ins.setString(5, stall.getTitle());
                            ins.setString(6, stall.getZoneName());
                            ins.setInt(7, stall.getChestX());
                            ins.setInt(8, stall.getChestY());
                            ins.setInt(9, stall.getChestZ());
                            ins.setInt(10, stall.getSignX());
                            ins.setInt(11, stall.getSignY());
                            ins.setInt(12, stall.getSignZ());
                            ins.setLong(13, stall.getCreatedAt());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }

                    try (PreparedStatement ins = conn.prepareStatement(INSERT_STALL_LISTING)) {
                        for (MarketStall stall : plot.getStalls()) {
                            if (stall == null || stall.getOwnerId() == null) continue;
                            String stallId = plot.getPlotId() + ":" + stall.getStorageKey();
                            for (Map.Entry<Integer, MarketStall.StallListing> entry : stall.getListings().entrySet()) {
                                if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isValid()) continue;
                                ins.setString(1, stallId);
                                ins.setInt(2, entry.getKey());
                                ins.setDouble(3, entry.getValue().getPrice());
                                ins.setString(4, entry.getValue().getCurrency().name());
                                ins.setInt(5, entry.getValue().getBundleAmount());
                                ins.addBatch();
                            }
                        }
                        ins.executeBatch();
                    }
                }

                conn.commit();

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().severe("Failed to save plot " + plot.getPlotId() + ": " + e.getMessage());
            } finally {
                try { conn.setAutoCommit(auto); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save plot " + plot.getPlotId() + ": " + e.getMessage());
        }
    }

    private String serializeSettings(Plot plot) {
        StringBuilder sb = new StringBuilder();
        java.util.function.BiConsumer<String, String> add = (k, v) -> {
            if (v == null) return;
            if (sb.length() > 0) sb.append(";");
            sb.append(k).append("=").append(v);
        };

        add.accept("maxMembers", String.valueOf(plot.getMaxMembers()));
        add.accept("horizonRank", String.valueOf(plot.getHorizonRank()));
        add.accept("horizonExpansionRank", String.valueOf(plot.getHorizonExpansionRank()));
        add.accept("horizonRenown", String.valueOf(plot.getHorizonRenown()));
        add.accept("horizonClimate", plot.getHorizonClimate());
        add.accept("ascensionFocus", plot.getAscensionFocus());
        add.accept("ascensionFocusChangedAt", String.valueOf(plot.getAscensionFocusChangedAt()));
        add.accept("spawn", plot.getSpawnLocationString());
        add.accept("welcome", plot.getWelcomeMessage());
        add.accept("farewell", plot.getFarewellMessage());
        add.accept("entryTitle", plot.getEntryTitle());
        add.accept("entrySubtitle", plot.getEntrySubtitle());
        add.accept("description", plot.getDescription());
        add.accept("customBiome", plot.getCustomBiome());

        add.accept("plotStatus", plot.getPlotStatus());
        add.accept("isForSale", String.valueOf(plot.isForSale()));
        add.accept("salePrice", String.valueOf(plot.getSalePrice()));
        add.accept("isForRent", String.valueOf(plot.isForRent()));
        add.accept("rentPrice", String.valueOf(plot.getRentPrice()));
        add.accept("rentExpires", String.valueOf(plot.getRentExpires()));
        UUID renter = plot.getCurrentRenter();
        add.accept("currentRenter", renter != null ? renter.toString() : null);

        add.accept("currentBid", String.valueOf(plot.getCurrentBid()));
        UUID bidder = plot.getCurrentBidder();
        add.accept("currentBidder", bidder != null ? bidder.toString() : null);

        if (!plot.getLikedBy().isEmpty()) {
            add.accept("likedBy", plot.getLikedBy().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
        if (!plot.getBannedPlayers().isEmpty()) {
            add.accept("banned", plot.getBannedPlayers().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }

        add.accept("borderParticle", plot.getBorderParticle());
        add.accept("ambientParticle", plot.getAmbientParticle());
        add.accept("entryEffect", plot.getEntryEffect());

        add.accept("isServerWarp", String.valueOf(plot.isServerWarp()));
        add.accept("warpName", plot.getWarpName());
        add.accept("warpIcon", plot.getWarpIcon() != null ? plot.getWarpIcon().name() : null);

        String roleFlags = plot.serializeRoleFlags();
        if (!roleFlags.isEmpty()) add.accept("roleFlags", roleFlags);

        return sb.toString();
    }

    private void applySettings(Plot plot, String settings) {
        if (settings == null || settings.isEmpty()) return;

        for (String part : settings.split(";")) {
            if (part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;

            String key = kv[0];
            String value = kv[1];

            try {
                switch (key) {
                    case "maxMembers" -> plot.setMaxMembers(Integer.parseInt(value));
                    case "horizonRank" -> plot.setHorizonRank(Integer.parseInt(value));
                    case "horizonExpansionRank" -> plot.setHorizonExpansionRank(Integer.parseInt(value));
                    case "horizonRenown" -> plot.setHorizonRenown(Long.parseLong(value));
                    case "horizonClimate" -> plot.setHorizonClimate(value);
                    case "ascensionFocus" -> plot.setAscensionFocus(value);
                    case "ascensionFocusChangedAt" -> plot.setAscensionFocusChangedAt(Long.parseLong(value));
                    case "spawn" -> plot.setSpawnLocationFromString(value);
                    case "welcome" -> plot.setWelcomeMessage(value);
                    case "farewell" -> plot.setFarewellMessage(value);
                    case "entryTitle" -> plot.setEntryTitle(value);
                    case "entrySubtitle" -> plot.setEntrySubtitle(value);
                    case "description" -> plot.setDescription(value);
                    case "customBiome" -> plot.setCustomBiome(value);
                    case "plotStatus" -> plot.setPlotStatus(value);

                    case "isForSale" -> {
                        boolean fs = Boolean.parseBoolean(value);
                        plot.setForSale(fs, fs ? plot.getSalePrice() : 0.0D);
                    }
                    case "salePrice" -> plot.setForSale(plot.isForSale(), Double.parseDouble(value));

                    case "isForRent" -> {
                        boolean fr = Boolean.parseBoolean(value);
                        plot.setForRent(fr, fr ? plot.getRentPrice() : 0.0D);
                    }
                    case "rentPrice" -> plot.setForRent(plot.isForRent(), Double.parseDouble(value));
                    case "rentExpires" -> plot.setRenter(plot.getCurrentRenter(), Long.parseLong(value));
                    case "currentRenter" -> plot.setRenter(UUID.fromString(value), plot.getRentExpires());

                    case "currentBid" -> plot.setCurrentBid(Double.parseDouble(value), plot.getCurrentBidder());
                    case "currentBidder" -> plot.setCurrentBid(plot.getCurrentBid(), UUID.fromString(value));

                    case "likedBy" -> {
                        for (String uStr : value.split(",")) {
                            if (!uStr.isEmpty()) {
                                try { plot.toggleLike(UUID.fromString(uStr)); } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                    case "banned" -> {
                        for (String uStr : value.split(",")) {
                            if (!uStr.isEmpty()) {
                                try { plot.addBan(UUID.fromString(uStr)); } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }

                    case "borderParticle" -> plot.setBorderParticle(value);
                    case "ambientParticle" -> plot.setAmbientParticle(value);
                    case "entryEffect" -> plot.setEntryEffect(value);

                    case "isServerWarp" -> plot.setServerWarp(Boolean.parseBoolean(value), plot.getWarpName(), plot.getWarpIcon());
                    case "warpName" -> plot.setServerWarp(plot.isServerWarp(), value, plot.getWarpIcon());
                    case "warpIcon" -> {
                        if (!value.isEmpty()) {
                            try { plot.setServerWarp(plot.isServerWarp(), plot.getWarpName(), Material.valueOf(value)); }
                            catch (IllegalArgumentException ignored) {}
                        }
                    }

                    case "roleFlags" -> plot.deserializeRoleFlags(value);
                }
            } catch (Exception ignored) {}
        }
    }

    private void cachePlot(Plot plot) {
        // Deduplicate by id to prevent ghosts
        removePlotByIdEverywhere(plot.getPlotId());

        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> ConcurrentHashMap.newKeySet()).add(plot);
        indexPlot(plot);
    }

    private void removePlotByIdEverywhere(UUID plotId) {
        if (plotId == null) return;

        for (Map.Entry<UUID, Set<Plot>> entry : plotsByOwner.entrySet()) {
            Set<Plot> set = entry.getValue();
            if (set == null || set.isEmpty()) continue;

            Plot found = null;
            for (Plot p : set) {
                if (p != null && plotId.equals(p.getPlotId())) { found = p; break; }
            }
            if (found != null) {
                set.remove(found);
                deIndexPlot(found);
            }
        }
    }

    private void indexPlot(Plot plot) {
        String w = plot.getWorld();
        int minX = plot.getX1() >> 4;
        int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxZ = plot.getZ2() >> 4;

        Map<String, Set<Plot>> worldChunks = plotsByChunk.computeIfAbsent(w, k -> new ConcurrentHashMap<>());
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                String key = cx + "," + cz;
                worldChunks.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(plot);
            }
        }
    }

    private void deIndexPlot(Plot plot) {
        if (plot == null) return;
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(plot.getWorld());
        if (worldChunks == null) return;

        int minX = plot.getX1() >> 4;
        int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxZ = plot.getZ2() >> 4;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                String key = cx + "," + cz;
                Set<Plot> set = worldChunks.get(key);
                if (set != null) {
                    set.remove(plot);
                    if (set.isEmpty()) worldChunks.remove(key);
                }
            }
        }
        if (worldChunks.isEmpty()) plotsByChunk.remove(plot.getWorld());
    }

    // --- IDataStore: Modifications ---

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        if (owner == null || c1 == null || c2 == null || c1.getWorld() == null || c2.getWorld() == null) return;

        UUID id = UUID.randomUUID();
        String ownerName = Bukkit.getOfflinePlayer(owner).getName();

        int x1 = Math.min(c1.getBlockX(), c2.getBlockX());
        int x2 = Math.max(c1.getBlockX(), c2.getBlockX());
        int z1 = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int z2 = Math.max(c1.getBlockZ(), c2.getBlockZ());

        Plot plot = new Plot(id, owner, ownerName, c1.getWorld().getName(), x1, z1, x2, z2, System.currentTimeMillis());
        addPlot(plot);
    }

    @Override
    public void addPlot(Plot plot) {
        if (plot == null) return;
        cachePlot(plot);
        savePlot(plot);
        isDirty = true;
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        if (owner == null || plotId == null) return;

        Plot removedPlot = getAllPlots().stream()
                .filter(plot -> plot != null && plotId.equals(plot.getPlotId()))
                .findFirst()
                .orElse(null);

        // Hard dedupe kill-switch: removes plotId from any cached owner set + chunk index
        removePlotByIdEverywhere(plotId);

        if (removedPlot != null) {
            Bukkit.getPluginManager().callEvent(new PlotDeleteEvent(removedPlot));
        }

        queueDb(() -> {
            try (Connection conn = hikari.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                    ps.setString(1, plotId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_ZONES_BY_PLOT)) {
                    ps.setString(1, plotId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_ZONE_META_BY_PLOT)) {
                    ps.setString(1, plotId.toString() + ":%");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_ZONE_GUESTS_BY_PLOT)) {
                    ps.setString(1, plotId.toString() + ":%");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_STALLS_BY_PLOT)) {
                    ps.setString(1, plotId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_STALL_LISTINGS_BY_PLOT)) {
                    ps.setString(1, plotId.toString() + ":%");
                    ps.executeUpdate();
                }
            } catch (SQLException error) {
                plugin.getLogger().warning("Failed to remove plot " + plotId + " from SQL storage: "
                        + error.getMessage());
            }
        });

        isDirty = true;
    }

    @Override
    public void removeAllPlots(UUID owner) {
        if (owner == null) return;

        Set<Plot> owned = plotsByOwner.remove(owner);
        if (owned != null) {
            for (Plot plot : owned) {
                if (plot != null) {
                    deIndexPlot(plot);
                    Bukkit.getPluginManager().callEvent(new PlotDeleteEvent(plot));
                }
            }
        }

        queueDb(() -> {
            try (Connection conn = hikari.getConnection()) {
                List<String> plotIds = new ArrayList<>();
                try (PreparedStatement sel = conn.prepareStatement(SELECT_PLOT_IDS_BY_OWNER)) {
                    sel.setString(1, owner.toString());
                    try (ResultSet rs = sel.executeQuery()) {
                        while (rs.next()) {
                            String pid = rs.getString(1);
                            if (pid != null) plotIds.add(pid);
                        }
                    }
                }

                if (!plotIds.isEmpty()) {
                    try (PreparedStatement delZones = conn.prepareStatement(DELETE_ZONES_BY_PLOT)) {
                        for (String pid : plotIds) {
                            delZones.setString(1, pid);
                            delZones.addBatch();
                        }
                        delZones.executeBatch();
                    }
                    try (PreparedStatement delZoneMeta = conn.prepareStatement(DELETE_ZONE_META_BY_PLOT)) {
                        for (String pid : plotIds) {
                            delZoneMeta.setString(1, pid + ":%");
                            delZoneMeta.addBatch();
                        }
                        delZoneMeta.executeBatch();
                    }
                    try (PreparedStatement delZoneGuests = conn.prepareStatement(DELETE_ZONE_GUESTS_BY_PLOT)) {
                        for (String pid : plotIds) {
                            delZoneGuests.setString(1, pid + ":%");
                            delZoneGuests.addBatch();
                        }
                        delZoneGuests.executeBatch();
                    }
                    try (PreparedStatement delStalls = conn.prepareStatement(DELETE_STALLS_BY_PLOT)) {
                        for (String pid : plotIds) {
                            delStalls.setString(1, pid);
                            delStalls.addBatch();
                        }
                        delStalls.executeBatch();
                    }
                    try (PreparedStatement delStallListings = conn.prepareStatement(DELETE_STALL_LISTINGS_BY_PLOT)) {
                        for (String pid : plotIds) {
                            delStallListings.setString(1, pid + ":%");
                            delStallListings.addBatch();
                        }
                        delStallListings.executeBatch();
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(DELETE_PLOTS_BY_OWNER)) {
                    ps.setString(1, owner.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException ignored) {}
        });

        isDirty = true;
    }

    @Override
    public void addPlayerRole(Plot plot, UUID uuid, String role) {
        if (plot == null || uuid == null) return;
        plot.setRole(uuid, role);
        savePlot(plot);
        isDirty = true;
    }

    @Override
    public void removePlayerRole(Plot plot, UUID uuid) {
        if (plot == null || uuid == null) return;
        plot.removeRole(uuid);
        savePlot(plot);
        isDirty = true;
    }

    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        if (plot == null || newOwner == null) return;

        UUID plotId = plot.getPlotId();
        UUID oldOwner = plot.getOwner();

        // Remove ANY cached copies (ghost-killer) before changing state
        removePlotByIdEverywhere(plotId);

        // Update the plot itself
        plot.internalSetOwner(newOwner, newOwnerName);

        // Safety: ensure old owner doesn't keep privileges via a leftover role mapping
        if (oldOwner != null) {
            try { plot.removeRole(oldOwner); } catch (Throwable ignored) {}
        }

        // Re-cache under the correct owner + re-index into chunks
        cachePlot(plot);

        savePlot(plot);
        isDirty = true;
    }

    @Override
    public void updatePlotBounds(Plot plot, int x1, int z1, int x2, int z2) {
        if (plot == null) return;
        removePlotByIdEverywhere(plot.getPlotId());
        plot.setBounds(x1, z1, x2, z2);
        cachePlot(plot);
        savePlotSync(plot);
        isDirty = true;
    }

    @Override
    public void removeBannedPlots() {
        for (org.bukkit.OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }

    // --- Role Flag State ---

    @Override
    public TriState getRoleFlagState(Plot plot, String roleId, String flagKey) {
        if (plot == null || roleId == null || flagKey == null) return TriState.INHERIT;
        return plot.getRoleFlagState(roleId, flagKey);
    }

    @Override
    public void setRoleFlagState(Plot plot, String roleId, String flagKey, TriState state) {
        if (plot == null || roleId == null || flagKey == null) return;
        plot.setRoleFlagState(roleId, flagKey, state == null ? TriState.INHERIT : state);
        savePlot(plot);
        isDirty = true;
    }

    // --- Wilderness ---

    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        if (loc == null || loc.getWorld() == null || playerUUID == null) return;

        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        queueDb(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LOG_WILDERNESS)) {
                ps.setString(1, worldName);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setString(5, oldMat);
                ps.setString(6, newMat);
                ps.setLong(7, System.currentTimeMillis());
                ps.setString(8, playerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException error) {
                plugin.getLogger().warning("Failed to persist wilderness restoration record at "
                        + worldName + " " + x + "," + y + "," + z + ": " + error.getMessage());
            }
        });
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        if (limit <= 0) return;

        queueDb(() -> {
            List<WildernessRevertRow> rows = new ArrayList<>();
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(GET_REVERTABLE_BLOCKS)) {

                ps.setLong(1, timestamp);
                ps.setInt(2, limit);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        if (!wildernessRevertsInFlight.add(id)) continue;

                        String worldName = rs.getString("world");
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        String oldMat = rs.getString("old_material");

                        if (worldName == null || oldMat == null) {
                            wildernessRevertsInFlight.remove(id);
                            continue;
                        }

                        rows.add(new WildernessRevertRow(id, worldName, x, y, z, oldMat));
                    }
                }

            } catch (SQLException error) {
                for (WildernessRevertRow row : rows) wildernessRevertsInFlight.remove(row.id());
                plugin.getLogger().warning("Failed to read wilderness restoration records: " + error.getMessage());
                return;
            }

            if (!rows.isEmpty()) {
                plugin.runSync(() -> rows.forEach(this::scheduleWildernessRevert));
            }
        });
    }

    private void scheduleWildernessRevert(WildernessRevertRow row) {
        World world = Bukkit.getWorld(row.worldName());
        Material material = Material.matchMaterial(row.materialName());
        if (world == null || material == null) {
            wildernessRevertsInFlight.remove(row.id());
            return;
        }

        Location location = new Location(world, row.x(), row.y(), row.z());
        plugin.runAt(location, () -> {
            try {
                location.getBlock().setType(material, false);
                acknowledgeWildernessRevert(row.id());
            } catch (Throwable error) {
                wildernessRevertsInFlight.remove(row.id());
                plugin.getLogger().warning("Wilderness restoration failed at "
                        + row.worldName() + " " + row.x() + "," + row.y() + "," + row.z()
                        + ": " + error.getMessage());
            }
        });
    }

    private void acknowledgeWildernessRevert(long id) {
        queueDb(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement delete = conn.prepareStatement(DELETE_WILDERNESS_BY_ID)) {
                delete.setLong(1, id);
                delete.executeUpdate();
            } catch (SQLException error) {
                plugin.getLogger().warning("Restored wilderness block but could not acknowledge record "
                        + id + ": " + error.getMessage());
            } finally {
                wildernessRevertsInFlight.remove(id);
            }
        });
    }

    // --- Accessors (NEVER NULL) ---

    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }

    @Override
    public List<Plot> getPlots(UUID owner) {
        if (owner == null) return Collections.emptyList();
        Set<Plot> set = plotsByOwner.get(owner);
        if (set == null || set.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(set);
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        if (owner == null || plotId == null) return null;
        Set<Plot> set = plotsByOwner.get(owner);
        if (set == null || set.isEmpty()) return null;
        for (Plot p : set) if (p != null && plotId.equals(p.getPlotId())) return p;
        return null;
    }

    @Override
    public Collection<Plot> getAllPlots() {
        Map<UUID, Plot> byId = new HashMap<>();
        for (Set<Plot> set : plotsByOwner.values()) {
            if (set == null || set.isEmpty()) continue;
            for (Plot p : set) if (p != null) byId.put(p.getPlotId(), p);
        }
        return byId.values();
    }

    @Override
    public Collection<Plot> getPlotsForSale() {
        Collection<Plot> all = getAllPlots();
        if (all.isEmpty()) return Collections.emptyList();
        return all.stream().filter(Plot::isForSale).collect(Collectors.toList());
    }

    @Override
    public Collection<Plot> getPlotsForAuction() {
        Collection<Plot> all = getAllPlots();
        if (all.isEmpty()) return Collections.emptyList();
        return all.stream().filter(p -> "AUCTION".equals(p.getPlotStatus())).collect(Collectors.toList());
    }

    @Override
    public Plot getPlotAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        String worldName = loc.getWorld().getName();
        String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);

        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(worldName);
        if (worldChunks == null) return null;

        Set<Plot> chunkPlots = worldChunks.get(key);
        if (chunkPlots == null || chunkPlots.isEmpty()) return null;

        for (Plot p : chunkPlots) if (p != null && p.isInside(loc)) return p;
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Plot ignore, String world, int x1, int z1, int x2, int z2) {
        if (world == null || world.isEmpty()) return false;

        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null || worldMap.isEmpty()) return false;

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        int cMinX = minX >> 4, cMaxX = maxX >> 4;
        int cMinZ = minZ >> 4, cMaxZ = maxZ >> 4;

        Set<Plot> candidates = new HashSet<>();
        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                Set<Plot> set = worldMap.get(cx + "," + cz);
                if (set != null && !set.isEmpty()) candidates.addAll(set);
            }
        }

        for (Plot p : candidates) {
            if (p == null) continue;
            if (!world.equals(p.getWorld())) continue;
            if (ignore != null && ignore.getPlotId().equals(p.getPlotId())) continue;

            if (minX <= p.getX2() && maxX >= p.getX1() && minZ <= p.getZ2() && maxZ >= p.getZ1()) {
                return true;
            }
        }
        return false;
    }

    private com.aegisguard.economy.CurrencyType parseCurrencyType(String raw) {
        if (raw == null || raw.isBlank()) return com.aegisguard.economy.CurrencyType.VAULT;
        try {
            return com.aegisguard.economy.CurrencyType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return com.aegisguard.economy.CurrencyType.VAULT;
        }
    }

    private String zoneStorageKey(Plot plot, Zone zone) {
        if (plot == null || zone == null || zone.getName() == null) return "";
        return plot.getPlotId() + ":" + zone.getName().trim().toLowerCase(Locale.ROOT);
    }

    // --- shutdown helper (HARDCORE EDITION) ---
    @Override
    public void shutdown() {
        try {
            stopping.set(true);

            // Let queued DB work finish
            flushPending(5000L);

            // Final sync save-all (belt + suspenders)
            try { saveSync(); } catch (Throwable ignored) {}

            // Stop executor
            dbExecutor.shutdown();
            try { dbExecutor.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

        } catch (Throwable ignored) {
        } finally {
            if (hikari != null && !hikari.isClosed()) {
                try { hikari.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
