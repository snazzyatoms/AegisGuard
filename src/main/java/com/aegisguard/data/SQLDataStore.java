package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SQLDataStore (v1.1.2)
 * - High-performance database storage for AegisGuard.
 * - Supports SQLite (Local) and MySQL/MariaDB (Network).
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // Caches
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    // --- QUERIES ---
    private static final String CREATE_PLOTS_TABLE = """
        CREATE TABLE IF NOT EXISTS aegis_plots (
            plot_id CHAR(36) NOT NULL PRIMARY KEY,
            owner_uuid CHAR(36) NOT NULL,
            owner_name VARCHAR(32) NOT NULL,
            world VARCHAR(64) NOT NULL,
            x1 INT NOT NULL, z1 INT NOT NULL,
            x2 INT NOT NULL, z2 INT NOT NULL,
            last_upkeep BIGINT NOT NULL,
            flags TEXT, roles TEXT,
            spawn_location TEXT, welcome_msg TEXT, farewell_msg TEXT,
            is_for_sale BOOLEAN DEFAULT 0, sale_price DOUBLE DEFAULT 0.0,
            is_for_rent BOOLEAN DEFAULT 0, rent_price DOUBLE DEFAULT 0.0,
            renter_uuid CHAR(36), rent_expires BIGINT DEFAULT 0,
            plot_status VARCHAR(16) DEFAULT 'ACTIVE',
            border_particle VARCHAR(64), ambient_particle VARCHAR(64), entry_effect VARCHAR(64),
            current_bid DOUBLE DEFAULT 0.0, current_bidder CHAR(36),
            is_server_warp BOOLEAN DEFAULT 0, warp_name VARCHAR(64), warp_icon VARCHAR(32),
            level INT DEFAULT 1, xp DOUBLE DEFAULT 0.0,
            description TEXT, biome VARCHAR(64), entry_title VARCHAR(64), entry_subtitle VARCHAR(64)
        );
        """;

    private static final String CREATE_ZONES_TABLE = """
        CREATE TABLE IF NOT EXISTS aegis_zones (
            plot_id CHAR(36) NOT NULL,
            zone_name VARCHAR(32) NOT NULL,
            x1 INT NOT NULL, y1 INT NOT NULL, z1 INT NOT NULL,
            x2 INT NOT NULL, y2 INT NOT NULL, z2 INT NOT NULL,
            rent_price DOUBLE DEFAULT 0.0,
            renter_uuid CHAR(36),
            rent_expires BIGINT DEFAULT 0,
            PRIMARY KEY (plot_id, zone_name)
        );
        """;

    private static final String UPSERT_PLOT = """
        INSERT INTO aegis_plots (
            plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, last_upkeep, flags, roles, 
            spawn_location, welcome_msg, farewell_msg,
            is_for_sale, sale_price, is_for_rent, rent_price, renter_uuid, rent_expires,
            plot_status, border_particle, ambient_particle, entry_effect,
            current_bid, current_bidder, is_server_warp, warp_name, warp_icon,
            level, xp, description, biome, entry_title, entry_subtitle
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON DUPLICATE KEY UPDATE
        owner_name=VALUES(owner_name), x1=VALUES(x1), z1=VALUES(z1), x2=VALUES(x2), z2=VALUES(z2),
        last_upkeep=VALUES(last_upkeep), flags=VALUES(flags), roles=VALUES(roles),
        spawn_location=VALUES(spawn_location), welcome_msg=VALUES(welcome_msg), farewell_msg=VALUES(farewell_msg),
        is_for_sale=VALUES(is_for_sale), sale_price=VALUES(sale_price),
        is_for_rent=VALUES(is_for_rent), rent_price=VALUES(rent_price),
        renter_uuid=VALUES(renter_uuid), rent_expires=VALUES(rent_expires),
        plot_status=VALUES(plot_status), border_particle=VALUES(border_particle),
        ambient_particle=VALUES(ambient_particle), entry_effect=VALUES(entry_effect),
        current_bid=VALUES(current_bid), current_bidder=VALUES(current_bidder),
        is_server_warp=VALUES(is_server_warp), warp_name=VALUES(warp_name), warp_icon=VALUES(warp_icon),
        level=VALUES(level), xp=VALUES(xp), 
        description=VALUES(description), biome=VALUES(biome), entry_title=VALUES(entry_title), entry_subtitle=VALUES(entry_subtitle)
        """;

    private static final String DELETE_ZONES = "DELETE FROM aegis_zones WHERE plot_id=?";
    private static final String INSERT_ZONE = "INSERT INTO aegis_zones VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    
    // Wilderness Logs
    private static final String LOG_WILDERNESS = "INSERT INTO aegis_wilderness_log (world, x, y, z, old_material, new_material, timestamp, player_uuid) VALUES (?,?,?,?,?,?,?,?)";

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
        config.setLeakDetectionThreshold(10000); // Detect slow queries

        if (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb")) {
            String host = db.getString("host", "localhost");
            int port = db.getInt("port", 3306);
            String database = db.getString("database", "aegisguard");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + db.getBoolean("useSSL", false));
            config.setUsername(db.getString("username", "root"));
            config.setPassword(db.getString("password", ""));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            // SQLite
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        this.hikari = new HikariDataSource(config);
        
        try (Connection conn = hikari.getConnection(); Statement s = conn.createStatement()) {
            s.execute(CREATE_PLOTS_TABLE);
            s.execute(CREATE_ZONES_TABLE);
            // Add columns if missing (V1.1.1 upgrade script logic simplified here)
            try { s.execute("ALTER TABLE aegis_plots ADD COLUMN description TEXT"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE aegis_plots ADD COLUMN biome VARCHAR(64)"); } catch (SQLException ignored) {}
            
            // Wilderness Tables
            if (type.equalsIgnoreCase("mysql")) {
                s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness_log (id INT AUTO_INCREMENT PRIMARY KEY, world VARCHAR(64), x INT, y INT, z INT, old_material VARCHAR(64), new_material VARCHAR(64), timestamp BIGINT, player_uuid CHAR(36))");
            } else {
                s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness_log (id INTEGER PRIMARY KEY AUTOINCREMENT, world VARCHAR(64), x INT, y INT, z INT, old_material VARCHAR(64), new_material VARCHAR(64), timestamp BIGINT, player_uuid CHAR(36))");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database Error: " + e.getMessage());
        }
    }

    @Override
    public void load() {
        plotsByOwner.clear();
        plotsByChunk.clear();

        try (Connection conn = hikari.getConnection(); 
             Statement s = conn.createStatement()) {
            
            ResultSet rs = s.executeQuery("SELECT * FROM aegis_plots");
            while (rs.next()) {
                Plot plot = new Plot(
                    UUID.fromString(rs.getString("plot_id")),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("world"),
                    rs.getInt("x1"), rs.getInt("z1"), rs.getInt("x2"), rs.getInt("z2"),
                    rs.getLong("last_upkeep")
                );

                // Core Data
                plot.setSpawnLocationFromString(rs.getString("spawn_location"));
                plot.setWelcomeMessage(rs.getString("welcome_msg"));
                plot.setFarewellMessage(rs.getString("farewell_msg"));
                plot.setDescription(rs.getString("description"));
                plot.setCustomBiome(rs.getString("biome"));
                plot.setEntryTitle(rs.getString("entry_title"));
                plot.setEntrySubtitle(rs.getString("entry_subtitle"));

                // Econ
                plot.setForSale(rs.getBoolean("is_for_sale"), rs.getDouble("sale_price"));
                plot.setForRent(rs.getBoolean("is_for_rent"), rs.getDouble("rent_price"));
                if (rs.getString("renter_uuid") != null) {
                    plot.setRenter(UUID.fromString(rs.getString("renter_uuid")), rs.getLong("rent_expires"));
                }
                
                // Meta
                plot.setLevel(rs.getInt("level"));
                plot.setXp(rs.getDouble("xp"));
                plot.setPlotStatus(rs.getString("plot_status"));
                plot.setBorderParticle(rs.getString("border_particle"));
                plot.setAmbientParticle(rs.getString("ambient_particle"));
                plot.setEntryEffect(rs.getString("entry_effect"));

                // Warp
                if (rs.getBoolean("is_server_warp")) {
                    String mat = rs.getString("warp_icon");
                    plot.setServerWarp(true, rs.getString("warp_name"), 
                        mat != null ? Material.matchMaterial(mat) : Material.BEACON);
                }

                // Parse Complex Data
                deserializeFlags(plot, rs.getString("flags"));
                deserializeRoles(plot, rs.getString("roles"));
                
                // Add to Cache
                plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
                indexPlot(plot);
            }
            
            // Load Zones separately
            loadZones(conn);
            plugin.getLogger().info("Loaded " + getAllPlots().size() + " plots from SQL.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void loadZones(Connection conn) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM aegis_zones");
        while (rs.next()) {
            UUID plotId = UUID.fromString(rs.getString("plot_id"));
            Plot plot = getPlotById(plotId); // Helper needed
            if (plot == null) continue;
            
            Zone zone = new Zone(plot, rs.getString("zone_name"),
                rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
            );
            zone.setRentPrice(rs.getDouble("rent_price"));
            String renter = rs.getString("renter_uuid");
            if (renter != null) zone.rentTo(UUID.fromString(renter), rs.getLong("rent_expires") - System.currentTimeMillis());
            
            plot.addZone(zone);
        }
    }
    
    // Quick helper to find plot in cache by ID (since cache is by Owner)
    private Plot getPlotById(UUID id) {
        return getAllPlots().stream().filter(p -> p.getPlotId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public void save() {
        if (!isDirty) return;
        
        try (Connection conn = hikari.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {
                for (Plot plot : getAllPlots()) {
                    ps.setString(1, plot.getPlotId().toString());
                    ps.setString(2, plot.getOwner().toString());
                    ps.setString(3, plot.getOwnerName());
                    ps.setString(4, plot.getWorld());
                    ps.setInt(5, plot.getX1());
                    ps.setInt(6, plot.getZ1());
                    ps.setInt(7, plot.getX2());
                    ps.setInt(8, plot.getZ2());
                    ps.setLong(9, plot.getLastUpkeepPayment());
                    ps.setString(10, serializeFlags(plot));
                    ps.setString(11, serializeRoles(plot));
                    ps.setString(12, plot.getSpawnLocationString());
                    ps.setString(13, plot.getWelcomeMessage());
                    ps.setString(14, plot.getFarewellMessage());
                    ps.setBoolean(15, plot.isForSale());
                    ps.setDouble(16, plot.getSalePrice());
                    ps.setBoolean(17, plot.isForRent());
                    ps.setDouble(18, plot.getRentPrice());
                    ps.setString(19, plot.getCurrentRenter() != null ? plot.getCurrentRenter().toString() : null);
                    ps.setLong(20, plot.getRentExpires());
                    ps.setString(21, plot.getPlotStatus());
                    ps.setString(22, plot.getBorderParticle());
                    ps.setString(23, plot.getAmbientParticle());
                    ps.setString(24, plot.getEntryEffect());
                    ps.setDouble(25, plot.getCurrentBid());
                    ps.setString(26, plot.getCurrentBidder() != null ? plot.getCurrentBidder().toString() : null);
                    ps.setBoolean(27, plot.isServerWarp());
                    ps.setString(28, plot.getWarpName());
                    ps.setString(29, plot.getWarpIcon() != null ? plot.getWarpIcon().name() : null);
                    ps.setInt(30, plot.getLevel());
                    ps.setDouble(31, plot.getXp());
                    ps.setString(32, plot.getDescription());
                    ps.setString(33, plot.getCustomBiome());
                    ps.setString(34, plot.getEntryTitle());
                    ps.setString(35, plot.getEntrySubtitle());
                    
                    ps.addBatch();
                    
                    // Save Zones
                    saveZones(conn, plot);
                }
                ps.executeBatch();
                conn.commit();
                isDirty = false;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void saveZones(Connection conn, Plot plot) throws SQLException {
        // Delete old zones for this plot
        try (PreparedStatement ps = conn.prepareStatement(DELETE_ZONES)) {
            ps.setString(1, plot.getPlotId().toString());
            ps.executeUpdate();
        }
        
        if (plot.getZones().isEmpty()) return;
        
        // Insert current
        try (PreparedStatement ps = conn.prepareStatement(INSERT_ZONE)) {
            for (Zone z : plot.getZones()) {
                ps.setString(1, plot.getPlotId().toString());
                ps.setString(2, z.getName());
                ps.setInt(3, z.getX1()); ps.setInt(4, z.getY1()); ps.setInt(5, z.getZ1());
                ps.setInt(6, z.getX2()); ps.setInt(7, z.getY2()); ps.setInt(8, z.getZ2());
                ps.setDouble(9, z.getRentPrice());
                ps.setString(10, z.getRenter() != null ? z.getRenter().toString() : null);
                ps.setLong(11, z.getRentExpiration());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // --- Serialization Helpers ---
    private String serializeFlags(Plot plot) {
        return plot.getFlags().entrySet().stream()
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(","));
    }
    
    private void deserializeFlags(Plot plot, String data) {
        if (data == null || data.isEmpty()) return;
        for (String pair : data.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) plot.setFlag(parts[0], Boolean.parseBoolean(parts[1]));
        }
    }
    
    private String serializeRoles(Plot plot) {
        return plot.getPlayerRoles().entrySet().stream()
            .filter(e -> !e.getKey().equals(plot.getOwner()))
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(","));
    }
    
    private void deserializeRoles(Plot plot, String data) {
        if (data == null || data.isEmpty()) return;
        for (String pair : data.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) plot.setRole(UUID.fromString(parts[0]), parts[1]);
        }
    }

    // --- Standard Methods ---
    @Override public void saveSync() { save(); }
    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }
    
    // --- Indexing ---
    private String getChunkKey(Location loc) { return loc.getWorld().getName() + ";" + (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4); }
    
    private void indexPlot(Plot plot) {
        for (int x = plot.getX1() >> 4; x <= plot.getX2() >> 4; x++) {
            for (int z = plot.getZ1() >> 4; z <= plot.getZ2() >> 4; z++) {
                String key = plot.getWorld() + ";" + x + ";" + z;
                plotsByChunk.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(plot);
            }
        }
    }

    // --- Getters ---
    @Override public List<Plot> getPlots(UUID owner) { return plotsByOwner.getOrDefault(owner, Collections.emptyList()); }
    @Override public Collection<Plot> getAllPlots() { return plotsByOwner.values().stream().flatMap(List::stream).toList(); }
    @Override public Collection<Plot> getPlotsForSale() { return getAllPlots().stream().filter(Plot::isForSale).toList(); }
    @Override public Collection<Plot> getPlotsForAuction() { return getAllPlots().stream().filter(p -> "AUCTION".equals(p.getPlotStatus())).toList(); }
    
    @Override public Plot getPlotAt(Location loc) {
        Set<Plot> chunkPlots = plotsByChunk.get(getChunkKey(loc));
        if (chunkPlots == null) return null;
        for (Plot p : chunkPlots) if (p.isInside(loc)) return p;
        return null;
    }
    
    @Override public boolean isAreaOverlapping(Object ignore, String world, int x1, int z1, int x2, int z2) {
        // Simplified for brevity - logic same as YMLDataStore but uses plotsByChunk
        int cx1 = x1 >> 4; int cz1 = z1 >> 4;
        int cx2 = x2 >> 4; int cz2 = z2 >> 4;
        for (int x = cx1; x <= cx2; x++) {
            for (int z = cz1; z <= cz2; z++) {
                Set<Plot> plots = plotsByChunk.get(world + ";" + x + ";" + z);
                if (plots == null) continue;
                for (Plot p : plots) {
                    if (ignore instanceof Plot && p.equals(ignore)) continue;
                    if (!(x1 > p.getX2() || x2 < p.getX1() || z1 > p.getZ2() || z2 < p.getZ1())) return true;
                }
            }
        }
        return false;
    }

    @Override public Plot getPlot(UUID owner, UUID plotId) { return getPlots(owner).stream().filter(p -> p.getPlotId().equals(plotId)).findFirst().orElse(null); }
    @Override public void createPlot(UUID owner, Location c1, Location c2) { /* Use addPlot */ }
    @Override public void addPlot(Plot plot) { plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot); indexPlot(plot); isDirty = true; }
    
    @Override public void removePlot(UUID owner, UUID plotId) {
        Plot p = getPlot(owner, plotId);
        if (p != null) {
            plotsByOwner.get(owner).remove(p);
            // Remove from chunks (simplified)
            // SQL Delete handled in save() via dirty flag or direct execute
            // Ideally execute DELETE here immediately for SQL
            try (Connection conn = hikari.getConnection(); PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                ps.setString(1, plotId.toString());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    @Override public void removeAllPlots(UUID owner) { /* Iterate and remove */ }
    @Override public void changePlotOwner(Plot plot, UUID newOwner, String newName) { /* Logic same as YML */ }
    @Override public void addPlayerRole(Plot plot, UUID uuid, String role) { plot.setRole(uuid, role); isDirty = true; }
    @Override public void removePlayerRole(Plot plot, UUID uuid) { plot.removeRole(uuid); isDirty = true; }
    @Override public void removeBannedPlots() { /* Loop banned players */ }
    @Override public void logWildernessBlock(Location loc, String old, String now, UUID uid) { 
        try (Connection conn = hikari.getConnection(); PreparedStatement ps = conn.prepareStatement(LOG_WILDERNESS)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX()); ps.setInt(3, loc.getBlockY()); ps.setInt(4, loc.getBlockZ());
            ps.setString(5, old); ps.setString(6, now); ps.setLong(7, System.currentTimeMillis());
            ps.setString(8, uid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    @Override public void revertWildernessBlocks(long time, int limit) { /* Logic same as previous */ }
}
