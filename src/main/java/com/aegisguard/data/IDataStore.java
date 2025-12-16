package com.aegisguard.data;

import com.aegisguard.AegisGuard;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SQLDataStore (v1.2.2+)
 * - Supports MySQL, MariaDB, and SQLite transparently.
 * - Immediate async saves to prevent data loss.
 * - REPLACE INTO for universal compatibility.
 * - Advanced plot data stored in 'settings' blob.
 * - Zones stored in aegis_zones.
 * - Role flag overrides persisted via Plot.serializeRoleFlags()/deserializeRoleFlags().
 *
 * HARDENING:
 * - NEVER NULL collections (emptyList/emptySet)
 * - Thread-safe owner cache (Set-backed)
 * - Overlap checks use chunk index (scales on large servers)
 * - savePlotSync() commits immediately (crash-safe)
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // Thread-safe caches
    private final Map<UUID, Set<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();

    private volatile boolean isDirty = false;
    private String storageType = "sqlite";

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

    // Wilderness logging
    private static final String LOG_WILDERNESS =
            "INSERT INTO aegis_wilderness_log (world, x, y, z, old_material, new_material, timestamp, player_uuid) VALUES (?,?,?,?,?,?,?,?)";
    private static final String GET_REVERTABLE_BLOCKS =
            "SELECT id, world, x, y, z, old_material FROM aegis_wilderness_log WHERE timestamp < ? LIMIT ?";
    private static final String DELETE_WILDERNESS_BY_ID =
            "DELETE FROM aegis_wilderness_log WHERE id = ?";

    public SQLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        ConfigurationSection db = plugin.cfg().raw().getConfigurationSection("storage.database");
        storageType = plugin.cfg().raw().getString("storage.type", "sqlite");

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("AegisGuard-Pool");
        cfg.setConnectionTimeout(30000);
        cfg.setLeakDetectionThreshold(10000);

        if (storageType.equalsIgnoreCase("mysql") || storageType.equalsIgnoreCase("mariadb")) {
            String host = db != null ? db.getString("host", "localhost") : "localhost";
            int port = db != null ? db.getInt("port", 3306) : 3306;
            String database = db != null ? db.getString("database", "aegisguard") : "aegisguard";
            boolean useSSL = db != null && db.getBoolean("useSSL", false);

            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&autoReconnect=true");
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
                    zoneCount++;
                } catch (Exception ignored) {}
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        plugin.getLogger().info("Loaded " + plotCount + " plots and " + zoneCount + " zones from Database.");
        isDirty = false;
    }

    @Override
    public void save() {
        for (Set<Plot> set : plotsByOwner.values()) {
            if (set == null || set.isEmpty()) continue;
            for (Plot p : new ArrayList<>(set)) {
                if (p != null) savePlot(p);
            }
        }
    }

    @Override
    public void saveSync() {
        save();
    }

    @Override
    public void savePlot(Plot plot) {
        if (plot == null) return;
        plugin.runGlobalAsync(() -> savePlotInternal(plot));
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
                }

                conn.commit();
                isDirty = false;

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
                            try {
                                plot.setServerWarp(plot.isServerWarp(), plot.getWarpName(), Material.valueOf(value));
                            } catch (IllegalArgumentException ignored) {}
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

        Plot removed = null;
        Set<Plot> set = plotsByOwner.get(owner);
        if (set != null) {
            for (Plot p : set) {
                if (p != null && plotId.equals(p.getPlotId())) { removed = p; break; }
            }
            if (removed != null) set.remove(removed);
            if (set.isEmpty()) plotsByOwner.remove(owner);
        }

        if (removed != null) deIndexPlot(removed);

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                    ps.setString(1, plotId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_ZONES_BY_PLOT)) {
                    ps.setString(1, plotId.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        isDirty = true;
    }

    @Override
    public void removeAllPlots(UUID owner) {
        if (owner == null) return;

        Set<Plot> owned = plotsByOwner.remove(owner);
        if (owned != null) {
            for (Plot plot : owned) if (plot != null) deIndexPlot(plot);
        }

        plugin.runGlobalAsync(() -> {
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
                }

                try (PreparedStatement ps = conn.prepareStatement(DELETE_PLOTS_BY_OWNER)) {
                    ps.setString(1, owner.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
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

        if (oldOwner != null) {
            Set<Plot> oldSet = plotsByOwner.get(oldOwner);
            if (oldSet != null) {
                Plot found = null;
                for (Plot p : oldSet) {
                    if (p != null && plotId.equals(p.getPlotId())) { found = p; break; }
                }
                if (found != null) oldSet.remove(found);
                if (oldSet.isEmpty()) plotsByOwner.remove(oldOwner);
            }
        }

        deIndexPlot(plot);
        plot.internalSetOwner(newOwner, newOwnerName);

        removePlotByIdEverywhere(plotId);
        plotsByOwner.computeIfAbsent(newOwner, k -> ConcurrentHashMap.newKeySet()).add(plot);
        indexPlot(plot);

        savePlot(plot);
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

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LOG_WILDERNESS)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, oldMat);
                ps.setString(6, newMat);
                ps.setLong(7, System.currentTimeMillis());
                ps.setString(8, playerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        if (limit <= 0) return;

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(GET_REVERTABLE_BLOCKS)) {

                ps.setLong(1, timestamp);
                ps.setInt(2, limit);

                List<Long> ids = new ArrayList<>();

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String worldName = rs.getString("world");
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        String oldMat = rs.getString("old_material");

                        if (worldName == null || oldMat == null) continue;

                        World w = Bukkit.getWorld(worldName);
                        if (w == null) continue;

                        Material mat;
                        try { mat = Material.valueOf(oldMat); }
                        catch (IllegalArgumentException ignored) { continue; }

                        Location l = new Location(w, x, y, z);
                        plugin.runMainGlobal(() -> {
                            try { l.getBlock().setType(mat, false); } catch (Throwable ignored2) {}
                        });

                        ids.add(id);
                    }
                }

                if (!ids.isEmpty()) {
                    try (PreparedStatement del = conn.prepareStatement(DELETE_WILDERNESS_BY_ID)) {
                        for (Long id : ids) {
                            del.setLong(1, id);
                            del.addBatch();
                        }
                        del.executeBatch();
                    }
                }

            } catch (SQLException ignored) {}
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
        List<Plot> all = new ArrayList<>();
        for (Set<Plot> set : plotsByOwner.values()) if (set != null && !set.isEmpty()) all.addAll(set);
        return all;
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

    // --- shutdown helper ---
    public void shutdown() {
        try { saveSync(); } catch (Throwable ignored) {}
        if (hikari != null && !hikari.isClosed()) hikari.close();
    }
}
