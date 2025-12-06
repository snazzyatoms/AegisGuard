package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SQLDataStore (v1.2.2+)
 * - Supports MySQL, MariaDB, and SQLite transparently.
 * - Automatically saves changes immediately (Async) to prevent data loss.
 * - Uses REPLACE INTO for universal compatibility.
 * - Stores advanced plot data in 'settings' blob (progression, market, warps, cosmetics, likes, bans...).
 * - UPDATED: Adds full Zone (sub-claim) persistence via aegis_zones table.
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // --- CACHES ---
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();

    private volatile boolean isDirty = false;

    // --- QUERIES ---
    private static final String CREATE_PLOTS_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_plots (" +
                    " plot_id VARCHAR(36) PRIMARY KEY," +
                    " owner_uuid VARCHAR(36)," +
                    " owner_name VARCHAR(16)," +
                    " world VARCHAR(32)," +
                    " x1 INT, z1 INT," +
                    " x2 INT, z2 INT," +
                    " level INT," +
                    " xp DOUBLE," +
                    " last_upkeep BIGINT," +
                    " flags TEXT," +
                    " roles TEXT," +
                    " settings TEXT" +
                    " )";

    // 3D zones: x1,y1,z1,x2,y2,z2 + rent info
    private static final String CREATE_ZONES_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_zones (" +
                    " zone_id VARCHAR(36)," +
                    " plot_id VARCHAR(36)," +
                    " name VARCHAR(32)," +
                    " x1 INT, y1 INT, z1 INT," +
                    " x2 INT, y2 INT, z2 INT," +
                    " renter VARCHAR(36)," +
                    " price DOUBLE," +
                    " expires BIGINT," +
                    " PRIMARY KEY (zone_id)" +
                    " )";

    // REPLACE INTO for cross-DB upsert of plots
    private static final String UPSERT_PLOT =
            "REPLACE INTO aegis_plots " +
                    "(plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, level, xp, last_upkeep, flags, roles, settings) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String DELETE_PLOT =
            "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_PLOTS_BY_OWNER =
            "DELETE FROM aegis_plots WHERE owner_uuid = ?";

    // Zones maintenance
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
        String type = plugin.cfg().raw().getString("storage.type", "sqlite");

        HikariConfig config = new HikariConfig();
        config.setPoolName("AegisGuard-Pool");
        config.setConnectionTimeout(30000);
        config.setLeakDetectionThreshold(10000);

        if (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb")) {
            String host = db.getString("host", "localhost");
            int port = db.getInt("port", 3306);
            String database = db.getString("database", "aegisguard");
            boolean useSSL = db.getBoolean("useSSL", false);

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&autoReconnect=true");
            config.setUsername(db.getString("username", "root"));
            config.setPassword(db.getString("password", ""));

            // Performance optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            plugin.getLogger().info("Connecting to SQL Database (" + host + ")...");
        } else {
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            plugin.getLogger().info("Using local SQLite database file.");
        }

        this.hikari = new HikariDataSource(config);

        try (Connection conn = hikari.getConnection();
             Statement s = conn.createStatement()) {
            s.execute(CREATE_PLOTS_TABLE);
            s.execute(CREATE_ZONES_TABLE);
            s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness_log ( " +
                    "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                    "world VARCHAR(32), x INT, y INT, z INT, " +
                    "old_material VARCHAR(32), new_material VARCHAR(32), " +
                    "timestamp BIGINT, player_uuid VARCHAR(36) )");
        } catch (SQLException e) {
            plugin.getLogger().severe("Database Error: " + e.getMessage());
        }
    }

    @Override
    public void load() {
        plotsByOwner.clear();
        plotsByChunk.clear();

        int count = 0;

        // Temp index to allow attaching zones after plots are loaded
        Map<UUID, Plot> plotsById = new HashMap<>();

        // 1) Load plots
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_plots")) {

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                    String ownerName = rs.getString("owner_name");
                    String worldName = rs.getString("world");

                    if (Bukkit.getWorld(worldName) == null) continue;

                    int x1 = rs.getInt("x1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int z2 = rs.getInt("z2");

                    Plot plot = new Plot(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2);

                    plot.setLevel(rs.getInt("level"));
                    plot.setXp(rs.getDouble("xp"));
                    plot.setLastUpkeepPayment(rs.getLong("last_upkeep"));

                    // Restore Flags
                    String flagsStr = rs.getString("flags");
                    if (flagsStr != null && !flagsStr.isEmpty()) {
                        for (String part : flagsStr.split(",")) {
                            String[] kv = part.split(":", 2);
                            if (kv.length == 2) {
                                plot.setFlag(kv[0], Boolean.parseBoolean(kv[1]));
                            }
                        }
                    }

                    // Restore Roles
                    String rolesStr = rs.getString("roles");
                    if (rolesStr != null && !rolesStr.isEmpty()) {
                        for (String part : rolesStr.split(",")) {
                            String[] kv = part.split(":", 2);
                            if (kv.length == 2) {
                                try {
                                    UUID u = UUID.fromString(kv[0]);
                                    plot.setRole(u, kv[1]);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }

                    // Restore Advanced Settings from 'settings'
                    String settings = rs.getString("settings");
                    if (settings != null && !settings.isEmpty()) {
                        applySettings(plot, settings);
                    }

                    cachePlot(plot);
                    plotsById.put(plotId, plot);
                    count++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped invalid plot in DB.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 2) Load zones and attach to plots
        int zoneCount = 0;
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_zones")) {

            ResultSet rs = ps.executeQuery();
            long now = System.currentTimeMillis();

            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    Plot parent = plotsById.get(plotId);
                    if (parent == null) continue; // Orphaned zone; plot was deleted

                    String name = rs.getString("name");

                    int x1 = rs.getInt("x1");
                    int y1 = rs.getInt("y1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int y2 = rs.getInt("y2");
                    int z2 = rs.getInt("z2");

                    Zone zone = new Zone(parent, name, x1, y1, z1, x2, y2, z2);

                    double price = rs.getDouble("price");
                    zone.setRentPrice(price);

                    String renterStr = rs.getString("renter");
                    long expires = rs.getLong("expires");

                    if (renterStr != null && !renterStr.isEmpty() && expires > now) {
                        try {
                            UUID renter = UUID.fromString(renterStr);
                            // Keep the same absolute expiration using remaining duration
                            zone.rentTo(renter, expires - now);
                        } catch (IllegalArgumentException ignored) {}
                    }

                    parent.addZone(zone);
                    zoneCount++;
                } catch (Exception ignored) {
                    // Corrupt zone row; skip
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        plugin.getLogger().info("Loaded " + count + " plots and " + zoneCount + " zones from Database.");
    }

    @Override
    public void save() {
        // Bulk save routine (e.g. on auto-save task)
        for (Collection<Plot> plots : plotsByOwner.values()) {
            for (Plot p : plots) {
                savePlot(p);
            }
        }
    }

    @Override
    public void saveSync() {
        // Called on shutdown
        save();
        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
    }

    @Override
    public void savePlot(Plot plot) {
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                // 1) Upsert plot row
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

                    // Serialize Flags & Roles
                    ps.setString(12, plot.serializeFlags());
                    ps.setString(13, plot.serializeRoles());

                    // Advanced settings blob
                    ps.setString(14, serializeSettings(plot));

                    ps.executeUpdate();
                }

                // 2) Refresh zones for this plot
                try (PreparedStatement del = conn.prepareStatement(DELETE_ZONES_BY_PLOT)) {
                    del.setString(1, plot.getPlotId().toString());
                    del.executeUpdate();
                }

                if (!plot.getZones().isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(INSERT_ZONE)) {
                        for (Zone zone : plot.getZones()) {
                            String zoneId = UUID.randomUUID().toString();

                            ins.setString(1, zoneId);
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

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save plot " + plot.getPlotId() + ": " + e.getMessage());
            }
        });
    }

    // --- Settings Serialization Helpers ---

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

        // Likes
        if (!plot.getLikedBy().isEmpty()) {
            String liked = plot.getLikedBy().stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
            add.accept("likedBy", liked);
        }

        // Bans
        if (!plot.getBannedPlayers().isEmpty()) {
            String banned = plot.getBannedPlayers().stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
            add.accept("banned", banned);
        }

        // Cosmetics
        add.accept("borderParticle", plot.getBorderParticle());
        add.accept("ambientParticle", plot.getAmbientParticle());
        add.accept("entryEffect", plot.getEntryEffect());

        // Warp
        add.accept("isServerWarp", String.valueOf(plot.isServerWarp()));
        add.accept("warpName", plot.getWarpName());
        add.accept("warpIcon", plot.getWarpIcon() != null ? plot.getWarpIcon().name() : null);

        // Zones are stored in aegis_zones, not in settings.

        return sb.toString();
    }

    private void applySettings(Plot plot, String settings) {
        if (settings == null || settings.isEmpty()) return;

        String[] parts = settings.split(";");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0];
            String value = kv[1];

            try {
                switch (key) {
                    case "maxMembers":
                        plot.setMaxMembers(Integer.parseInt(value));
                        break;
                    case "spawn":
                        plot.setSpawnLocationFromString(value);
                        break;
                    case "welcome":
                        plot.setWelcomeMessage(value);
                        break;
                    case "farewell":
                        plot.setFarewellMessage(value);
                        break;
                    case "entryTitle":
                        plot.setEntryTitle(value);
                        break;
                    case "entrySubtitle":
                        plot.setEntrySubtitle(value);
                        break;
                    case "description":
                        plot.setDescription(value);
                        break;
                    case "customBiome":
                        plot.setCustomBiome(value);
                        break;

                    case "plotStatus":
                        plot.setPlotStatus(value);
                        break;

                    case "isForSale": {
                        boolean fs = Boolean.parseBoolean(value);
                        plot.setForSale(fs, fs ? plot.getSalePrice() : 0.0D);
                        break;
                    }
                    case "salePrice": {
                        double price = Double.parseDouble(value);
                        plot.setForSale(plot.isForSale(), price);
                        break;
                    }

                    case "isForRent": {
                        boolean fr = Boolean.parseBoolean(value);
                        plot.setForRent(fr, fr ? plot.getRentPrice() : 0.0D);
                        break;
                    }
                    case "rentPrice": {
                        double rp = Double.parseDouble(value);
                        plot.setForRent(plot.isForRent(), rp);
                        break;
                    }
                    case "rentExpires":
                        plot.setRenter(plot.getCurrentRenter(), Long.parseLong(value));
                        break;
                    case "currentRenter": {
                        UUID renter = UUID.fromString(value);
                        plot.setRenter(renter, plot.getRentExpires());
                        break;
                    }

                    case "currentBid": {
                        double bid = Double.parseDouble(value);
                        plot.setCurrentBid(bid, plot.getCurrentBidder());
                        break;
                    }
                    case "currentBidder": {
                        UUID bidder = UUID.fromString(value);
                        plot.setCurrentBid(plot.getCurrentBid(), bidder);
                        break;
                    }

                    case "likedBy": {
                        String[] uuids = value.split(",");
                        for (String uStr : uuids) {
                            if (uStr.isEmpty()) continue;
                            try {
                                plot.toggleLike(UUID.fromString(uStr));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        break;
                    }

                    case "banned": {
                        String[] uuids = value.split(",");
                        for (String uStr : uuids) {
                            if (uStr.isEmpty()) continue;
                            try {
                                plot.addBan(UUID.fromString(uStr));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        break;
                    }

                    case "borderParticle":
                        plot.setBorderParticle(value);
                        break;
                    case "ambientParticle":
                        plot.setAmbientParticle(value);
                        break;
                    case "entryEffect":
                        plot.setEntryEffect(value);
                        break;

                    case "isServerWarp": {
                        boolean isWarp = Boolean.parseBoolean(value);
                        plot.setServerWarp(isWarp, plot.getWarpName(), plot.getWarpIcon());
                        break;
                    }
                    case "warpName":
                        plot.setServerWarp(plot.isServerWarp(), value, plot.getWarpIcon());
                        break;
                    case "warpIcon":
                        if (!value.isEmpty()) {
                            try {
                                Material icon = Material.valueOf(value);
                                plot.setServerWarp(plot.isServerWarp(), plot.getWarpName(), icon);
                            } catch (IllegalArgumentException ignored) {}
                        }
                        break;
                }
            } catch (Exception ignored) {
                // Avoid hard-crashing loads because of malformed settings
            }
        }
    }

    // --- HELPER: Cache Management ---
    private void cachePlot(Plot plot) {
        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
        indexPlot(plot);
    }

    // --- INTERFACE METHODS ---

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
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
        cachePlot(plot);
        savePlot(plot);
        isDirty = true;
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        List<Plot> list = plotsByOwner.get(owner);
        if (list != null) {
            list.removeIf(p -> p.getPlotId().equals(plotId));
        }

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
    }

    @Override
    public void removeAllPlots(UUID owner) {
        List<Plot> owned = plotsByOwner.remove(owner);
        if (owned != null) {
            for (Plot plot : owned) deIndexPlot(plot);
        }

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(DELETE_PLOTS_BY_OWNER)) {
                    ps.setString(1, owner.toString());
                    ps.executeUpdate();
                }
                // Clean up zones for all plots owned by this UUID (brute-force if needed)
                // Optional: if you also store owner_uuid in zones, you could target more precisely.
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        List<Plot> oldList = plotsByOwner.get(plot.getOwner());
        if (oldList != null) oldList.remove(plot);

        plot.internalSetOwner(newOwner, newOwnerName);
        plotsByOwner.computeIfAbsent(newOwner, k -> new ArrayList<>()).add(plot);

        savePlot(plot);
    }

    @Override
    public void addPlayerRole(Plot plot, UUID uuid, String role) {
        plot.setRole(uuid, role);
        savePlot(plot);
    }

    @Override
    public void removePlayerRole(Plot plot, UUID uuid) {
        plot.removeRole(uuid);
        savePlot(plot);
    }

    @Override
    public void removeBannedPlots() {
        for (org.bukkit.OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }

    // ==============================================================    
    // --- Indexing Helpers ---
    // ==============================================================    

    private void indexPlot(Plot plot) {
        String w = plot.getWorld();
        int minX = plot.getX1() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxX = plot.getX2() >> 4;
        int maxZ = plot.getZ2() >> 4;

        Map<String, Set<Plot>> worldChunks = plotsByChunk.computeIfAbsent(w, k -> new ConcurrentHashMap<>());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String key = x + "," + z;
                worldChunks.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(plot);
            }
        }
    }

    private void deIndexPlot(Plot plot) {
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(plot.getWorld());
        if (worldChunks == null) return;

        int minX = plot.getX1() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxX = plot.getX2() >> 4;
        int maxZ = plot.getZ2() >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String key = x + "," + z;
                Set<Plot> set = worldChunks.get(key);
                if (set != null) {
                    set.remove(plot);
                    if (set.isEmpty()) worldChunks.remove(key);
                }
            }
        }
    }

    private Set<String> getChunksInArea(String world, int x1, int z1, int x2, int z2) {
        return Collections.emptySet();
    }

    // ==============================================================    
    // --- Wilderness Logging ---
    // ==============================================================    

    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        if (loc == null || loc.getWorld() == null) return;

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
            } catch (SQLException e) {
                // Suppress minor logging errors
            }
        });
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        // TODO: implement rollback logic if you want SQL-driven wilderness revert
    }

    // ==============================================================    
    // --- Basic Accessors ---
    // ==============================================================    

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    @Override
    public List<Plot> getPlots(UUID owner) {
        return plotsByOwner.getOrDefault(owner, Collections.emptyList());
    }

    @Override
    public Collection<Plot> getAllPlots() {
        return plotsByOwner.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    @Override
    public Collection<Plot> getPlotsForSale() {
        return getAllPlots().stream().filter(Plot::isForSale).collect(Collectors.toList());
    }

    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream().filter(p -> "AUCTION".equals(p.getPlotStatus())).collect(Collectors.toList());
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        return getPlots(owner).stream().filter(p -> p.getPlotId().equals(plotId)).findFirst().orElse(null);
    }

    @Override
    public Plot getPlotAt(Location loc) {
        String worldName = loc.getWorld().getName();
        String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);

        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(worldName);
        if (worldChunks == null) return null;

        Set<Plot> chunkPlots = worldChunks.get(key);
        if (chunkPlots == null) return null;

        for (Plot p : chunkPlots) {
            if (p.isInside(loc)) return p;
        }
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2) {
        for (Plot p : getAllPlots()) {
            if (!p.getWorld().equals(world)) continue;
            if (plotToIgnore != null && p.getPlotId().equals(plotToIgnore.getPlotId())) continue;

            if (x1 <= p.getX2() && x2 >= p.getX1() && z1 <= p.getZ2() && z2 >= p.getZ1()) {
                return true;
            }
        }
        return false;
    }
}
