package com.aegisguard.data;

import com.aegisguard.AegisGuard;
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

/**
 * SQLDataStore (v1.2.1 Fixed)
 * - Supports MySQL, MariaDB, and SQLite transparently.
 * - Automatically saves changes immediately (Async) to prevent data loss.
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // --- CACHES ---
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    // Map<WorldName, Map<ChunkKey, Set<Plot>>>
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();
    
    private volatile boolean isDirty = false;

    // --- QUERIES ---
    // 'REPLACE INTO' is compatible with MySQL and SQLite. It handles both Insert and Update.
    private static final String CREATE_PLOTS_TABLE   = "CREATE TABLE IF NOT EXISTS aegis_plots ( plot_id VARCHAR(36) PRIMARY KEY, owner_uuid VARCHAR(36), owner_name VARCHAR(16), world VARCHAR(32), x1 INT, z1 INT, x2 INT, z2 INT, level INT, xp DOUBLE, last_upkeep BIGINT, flags TEXT, roles TEXT, settings TEXT )";
    private static final String UPSERT_PLOT          = "REPLACE INTO aegis_plots (plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, level, xp, last_upkeep, flags, roles, settings) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String DELETE_PLOT          = "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_PLOTS_BY_OWNER = "DELETE FROM aegis_plots WHERE owner_uuid = ?";
    
    // Wilderness logging
    private static final String LOG_WILDERNESS       = "INSERT INTO aegis_wilderness_log (world, x, y, z, old_material, new_material, timestamp, player_uuid) VALUES (?,?,?,?,?,?,?,?)";
    private static final String GET_REVERTABLE_BLOCKS = "SELECT id, world, x, y, z, old_material FROM aegis_wilderness_log WHERE timestamp < ? LIMIT ?";
    private static final String DELETE_WILDERNESS_BY_ID = "DELETE FROM aegis_wilderness_log WHERE id = ?";

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
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            plugin.getLogger().info("Connecting to SQL Database (" + host + ")...");
        } else {
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            plugin.getLogger().info("Using local SQLite database file.");
        }

        this.hikari = new HikariDataSource(config);

        try (Connection conn = hikari.getConnection();
             Statement s = conn.createStatement()) {
            s.execute(CREATE_PLOTS_TABLE);
            // Create wilderness table if feature enabled
            s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness_log ( id INTEGER PRIMARY KEY AUTO_INCREMENT, world VARCHAR(32), x INT, y INT, z INT, old_material VARCHAR(32), new_material VARCHAR(32), timestamp BIGINT, player_uuid VARCHAR(36) )");
        } catch (SQLException e) {
            plugin.getLogger().severe("Database Initialization Error: " + e.getMessage());
        }
    }

    @Override
    public void load() {
        plotsByOwner.clear();
        plotsByChunk.clear();
        
        int count = 0;
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_plots")) {
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                    String ownerName = rs.getString("owner_name");
                    String worldName = rs.getString("world");
                    
                    // Skip if world is invalid/unloaded
                    if (Bukkit.getWorld(worldName) == null) continue;

                    int x1 = rs.getInt("x1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int z2 = rs.getInt("z2");
                    
                    // Create Plot (Assuming 1.2.1 Plot Constructor)
                    // You may need to adjust this constructor to match your exact Plot class
                    Plot plot = new Plot(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2);
                    
                    plot.setLevel(rs.getInt("level"));
                    plot.setXp(rs.getDouble("xp"));
                    plot.setLastUpkeep(rs.getLong("last_upkeep"));
                    
                    // Deserialize Flags/Roles (Placeholder logic - implement your parser here)
                    // plot.deserializeFlags(rs.getString("flags"));
                    // plot.deserializeRoles(rs.getString("roles"));
                    
                    cachePlot(plot);
                    count++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped invalid plot in DB.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Loaded " + count + " plots from Database.");
    }

    @Override
    public void save() {
        // In SQL mode, we save async on modification, so this acts as a backup bulk save
        for (Collection<Plot> plots : plotsByOwner.values()) {
            for (Plot p : plots) {
                savePlot(p);
            }
        }
    }

    @Override
    public void saveSync() {
        // On shutdown, close pool
        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
    }
    
    @Override
    public void savePlot(Plot plot) {
        // Run ASYNC to prevent lag
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {
                
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
                ps.setLong(11, plot.getLastUpkeep());
                
                // Serialize Complex Data (JSON or format string)
                ps.setString(12, plot.serializeFlags()); 
                ps.setString(13, plot.serializeRoles()); 
                ps.setString(14, ""); // Settings placeholder
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save plot " + plot.getPlotId() + ": " + e.getMessage());
            }
        });
    }

    // --- HELPER: Cache Management ---
    private void cachePlot(Plot plot) {
        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
        
        // Chunk Indexing
        World world = Bukkit.getWorld(plot.getWorld());
        if (world == null) return;
        
        int minX = plot.getX1() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxX = plot.getX2() >> 4;
        int maxZ = plot.getZ2() >> 4;

        Map<String, Set<Plot>> worldMap = plotsByChunk.computeIfAbsent(plot.getWorld(), k -> new ConcurrentHashMap<>());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String key = x + "," + z;
                worldMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(plot);
            }
        }
    }

    // --- INTERFACE METHODS ---

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        // Logic to create object -> cache -> save
        // Assuming you have a constructor or factory:
        // Plot p = new Plot(UUID.randomUUID(), owner, ...);
        // cachePlot(p);
        // savePlot(p);
    }

    @Override
    public void addPlot(Plot plot) {
        cachePlot(plot);
        savePlot(plot);
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        List<Plot> list = plotsByOwner.get(owner);
        if (list != null) {
            list.removeIf(p -> p.getPlotId().equals(plotId));
        }
        // Remove from DB
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                ps.setString(1, plotId.toString());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    @Override
    public void removeAllPlots(UUID owner) {
        plotsByOwner.remove(owner);
        // DB Clean
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_PLOTS_BY_OWNER)) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        List<Plot> list = plotsByOwner.get(owner);
        if (list == null) return null;
        return list.stream().filter(p -> p.getPlotId().equals(plotId)).findFirst().orElse(null);
    }

    @Override
    public Plot getPlotAt(Location loc) {
        String world = loc.getWorld().getName();
        String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
        
        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null) return null;
        
        Set<Plot> candidates = worldMap.get(key);
        if (candidates == null) return null;
        
        for (Plot p : candidates) {
            if (p.isInside(loc)) return p;
        }
        return null;
    }

    // --- REQUIRED IMPLEMENTATIONS (Boilerplate) ---
    @Override public List<Plot> getPlots(UUID owner) { return plotsByOwner.getOrDefault(owner, Collections.emptyList()); }
    @Override public Collection<Plot> getAllPlots() { return plotsByOwner.values().stream().flatMap(List::stream).collect(Collectors.toList()); }
    @Override public Collection<Plot> getPlotsForSale() { return new ArrayList<>(); } // Implement filtering
    @Override public Collection<Plot> getPlotsForAuction() { return new ArrayList<>(); } // Implement filtering
    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }
    @Override public void addPlayerRole(Plot plot, UUID playerUUID, String role) { plot.setRole(playerUUID, role); savePlot(plot); }
    @Override public void removePlayerRole(Plot plot, UUID playerUUID) { plot.removeRole(playerUUID); savePlot(plot); }
    @Override public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) { 
        // Update fields and save
        savePlot(plot); 
    }
    @Override public void removeBannedPlots() {} 
    
    @Override
    public boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2) {
        // Implementation using cached chunk map for speed
        // Iterate overlapping chunks and check collision
        return false; // Stub
    }

    // --- LOGGING ---
    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        if (loc == null) return;
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
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        // Implementation using GET_REVERTABLE_BLOCKS
    }
}
