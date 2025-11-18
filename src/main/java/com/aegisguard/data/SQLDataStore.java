package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SQLDataStore
 * - An implementation of IDataStore that uses SQL (SQLite or MySQL).
 * - All data is loaded into memory on startup and saved periodically.
 * - This is a "write-through cache" implementation.
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // --- In-Memory Cache ---
    // These are identical to YMLDataStore. All operations happen
    // on this cache, and the database is updated.
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    // --- SQL Queries ---
    private static final String CREATE_PLOTS_TABLE = """
        CREATE TABLE IF NOT EXISTS aegis_plots (
            plot_id CHAR(36) NOT NULL PRIMARY KEY,
            owner_uuid CHAR(36) NOT NULL,
            owner_name VARCHAR(32) NOT NULL,
            world VARCHAR(64) NOT NULL,
            x1 INT NOT NULL,
            z1 INT NOT NULL,
            x2 INT NOT NULL,
            z2 INT NOT NULL,
            last_upkeep BIGINT NOT NULL,
            flags TEXT,
            roles TEXT
        );
        """;
    private static final String CREATE_INDEXES = "CREATE INDEX IF NOT EXISTS idx_owner_uuid ON aegis_plots (owner_uuid); CREATE INDEX IF NOT EXISTS idx_world ON aegis_plots (world);";
    
    private static final String LOAD_PLOTS = "SELECT * FROM aegis_plots";
    
    private static final String UPSERT_PLOT = """
        INSERT INTO aegis_plots (plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, last_upkeep, flags, roles)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(plot_id) DO UPDATE SET
        owner_name = EXCLUDED.owner_name,
        x1 = EXCLUDED.x1,
        z1 = EXCLUDED.z1,
        x2 = EXCLUDED.x2,
        z2 = EXCLUDED.z2,
        last_upkeep = EXCLUDED.last_upkeep,
        flags = EXCLUDED.flags,
        roles = EXCLUDED.roles;
        """;
        
    private static final String DELETE_PLOT = "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_PLOTS_BY_OWNER = "DELETE FROM aegis_plots WHERE owner_uuid = ?";

    public SQLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        ConfigurationSection dbConfig = plugin.cfg().raw().getConfigurationSection("storage.database");
        String type = plugin.cfg().raw().getString("storage.type", "sqlite").toLowerCase();

        hikari = new HikariDataSource();
        hikari.setPoolName("AegisGuard-Pool");
        hikari.setMaximumPoolSize(10);
        hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(30));

        try {
            if (type.equals("sqlite")) {
                File dbFile = new File(plugin.getDataFolder(), dbConfig.getString("file", "aegisguard.db"));
                if (!dbFile.exists()) dbFile.createNewFile();
                hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            } else if (type.equals("mysql")) {
                String host = dbConfig.getString("host", "localhost");
                int port = dbConfig.getInt("port", 3306);
                String dbName = dbConfig.getString("database", "aegisguard");
                String user = dbConfig.getString("username", "user");
                String pass = dbConfig.getString("password", "pass");
                
                hikari.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", host, port, dbName));
                hikari.setUsername(user);
                hikari.setPassword(pass);
                // Add MySQL specific properties
                hikari.addDataSourceProperty("useSSL", dbConfig.getBoolean("useSSL", false));
                hikari.addDataSourceProperty("cachePrepStmts", "true");
                hikari.addDataSourceProperty("prepStmtCacheSize", "250");
                hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            } else {
                 plugin.getLogger().severe("Invalid storage type '" + type + "'. Disabling plugin.");
                 Bukkit.getPluginManager().disablePlugin(plugin);
                 return;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create SQLite file: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        // Create tables
        try (Connection conn = hikari.getConnection();
             PreparedStatement psPlots = conn.prepareStatement(CREATE_PLOTS_TABLE);
             PreparedStatement psIndexes = conn.prepareStatement(CREATE_INDEXES)) {
            psPlots.execute();
            psIndexes.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    @Override
    public void load() {
        plotsByOwner.clear();
        plotsByChunk.clear();

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_PLOTS);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Plot plot = new Plot(
                    UUID.fromString(rs.getString("plot_id")),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("world"),
                    rs.getInt("x1"),
                    rs.getInt("z1"),
                    rs.getInt("x2"),
                    rs.getInt("z2"),
                    rs.getLong("last_upkeep")
                );

                // De-serialize flags and roles from TEXT
                deserializeFlags(plot, rs.getString("flags"));
                deserializeRoles(plot, rs.getString("roles"));

                plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
                indexPlot(plot);
            }
            plugin.getLogger().info("Loaded " + getAllPlots().size() + " plots from database.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load plots from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void save() {
        // This is now a "write-through" method.
        // It's called by the async auto-saver.
        if (!isDirty) return;
        
        plugin.getLogger().info("Saving all plot data to database...");
        
        // We only save plots that are still in memory
        Collection<Plot> plotsToSave = getAllPlots();
        
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {
            
            for (Plot plot : plotsToSave) {
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
                ps.addBatch();
            }
            ps.executeBatch();
            isDirty = false;
            plugin.getLogger().info("Database save complete.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save plots to database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void saveSync() {
        // In this model, save() is already synchronous *relative to the calling thread*.
        // The auto-saver calls it async, onDisable calls it sync.
        save();
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    // --- Plot Management (operates on cache) ---

    @Override
    public List<Plot> getPlots(UUID owner) {
        return plotsByOwner.getOrDefault(owner, Collections.emptyList());
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        return getPlots(owner).stream()
                .filter(p -> p.getPlotId().equals(plotId))
                .findFirst().orElse(null);
    }

    @Override
    public Collection<Plot> getAllPlots() {
        return plotsByOwner.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public Plot getPlotAt(Location loc) {
        String worldName = loc.getWorld().getName();
        String chunkKey = getChunkKey(loc);

        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(worldName);
        if (worldChunks == null) return null;

        Set<Plot> plotsInChunk = worldChunks.get(chunkKey);
        if (plotsInChunk == null) return null;

        for (Plot plot : plotsInChunk) {
            if (plot.isInside(loc)) {
                return plot;
            }
        }
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2) {
        Set<String> chunks = getChunksInArea(world, x1, z1, x2, z2);
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(world);
        if (worldChunks == null) return false;

        Set<Plot> plotsToTest = new HashSet<>();
        for (String chunkKey : chunks) {
            plotsToTest.addAll(worldChunks.getOrDefault(chunkKey, Collections.emptySet()));
        }

        if (plotToIgnore != null) {
            plotsToTest.remove(plotToIgnore);
        }
        
        if (plotsToTest.isEmpty()) return false;

        for (Plot existingPlot : plotsToTest) {
            boolean overlaps = !(x1 > existingPlot.getX2() || x2 < existingPlot.getX1() ||
                                 z1 > existingPlot.getZ2() || z2 < existingPlot.getZ1());
            if (overlaps) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        Plot plot = new Plot(
            UUID.randomUUID(),
            owner,
            op.getName() != null ? op.getName() : "Unknown",
            c1.getWorld().getName(),
            c1.getBlockX(), c1.getBlockZ(),
            c2.getBlockX(), c2.getBlockZ()
        );
        addPlot(plot); // Use the unified add method
    }

    @Override
    public void addPlot(Plot plot) {
        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
        indexPlot(plot);
        isDirty = true;
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        List<Plot> owned = plotsByOwner.get(owner);
        if (owned == null) return;
        
        Plot toRemove = null;
        for(Plot p : owned) {
            if(p.getPlotId().equals(plotId)) {
                toRemove = p;
                break;
            }
        }
        
        if (toRemove != null) {
            owned.remove(toRemove);
            deIndexPlot(toRemove);
            isDirty = true;
            // Also delete from DB immediately
            final String plotIdStr = toRemove.getPlotId().toString();
            plugin.runGlobalAsync(() -> {
                try (Connection conn = hikari.getConnection();
                     PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                    ps.setString(1, plotIdStr);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void removeAllPlots(UUID owner) {
        List<Plot> owned = plotsByOwner.remove(owner);
        if (owned != null && !owned.isEmpty()) {
            for (Plot plot : owned) {
                deIndexPlot(plot);
            }
            isDirty = true;
            // Also delete from DB immediately
            final String ownerIdStr = owner.toString();
            plugin.runGlobalAsync(() -> {
                try (Connection conn = hikari.getConnection();
                     PreparedStatement ps = conn.prepareStatement(DELETE_PLOTS_BY_OWNER)) {
                    ps.setString(1, ownerIdStr);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void addPlayerRole(Plot plot, UUID playerUUID, String role) {
        plot.setRole(playerUUID, role);
        isDirty = true;
    }

    @Override
    public void removePlayerRole(Plot plot, UUID playerUUID) {
        plot.removeRole(playerUUID);
        isDirty = true;
    }

    @Override
    public void removeBannedPlots() {
        // This logic is fine, as it operates on the cache.
        // The removeAllPlots method will trigger the DB delete.
        for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }
    
    // --- Caching/Indexing (Identical to YMLDataStore) ---
    
    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ";" + (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4);
    }

    private void indexPlot(Plot plot) {
        Map<String, Set<Plot>> worldChunks = plotsByChunk.computeIfAbsent(plot.getWorld(), k -> new ConcurrentHashMap<>());
        for (String chunkKey : getChunksInArea(plot.getWorld(), plot.getX1(), plot.getZ1(), plot.getX2(), plot.getZ2())) {
            worldChunks.computeIfAbsent(chunkKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(plot);
        }
    }
    
    private void deIndexPlot(Plot plot) {
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(plot.getWorld());
        if (worldChunks == null) return;
        for (String chunkKey : getChunksInArea(plot.getWorld(), plot.getX1(), plot.getZ1(), plot.getX2(), plot.getZ2())) {
            Set<Plot> plots = worldChunks.get(chunkKey);
            if (plots != null) {
                plots.remove(plot);
                if (plots.isEmpty()) {
                    worldChunks.remove(chunkKey);
                }
            }
        }
    }
    
    private Set<String> getChunksInArea(String world, int x1, int z1, int x2, int z2) {
        Set<String> keys = new HashSet<>();
        int cX1 = x1 >> 4;
        int cZ1 = z1 >> 4;
        int cX2 = x2 >> 4;
        int cZ2 = z2 >> 4;
        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                keys.add(world + ";" + x + ";" + z);
            }
        }
        return keys;
    }
    
    // --- SQL Serialization Helpers ---
    
    private String serializeFlags(Plot plot) {
        // Simple key:value,key:value serializer
        return plot.getFlags().entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private void deserializeFlags(Plot plot, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            for (String pair : data.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    plot.setFlag(parts[0], Boolean.parseBoolean(parts[1]));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse flags for plot " + plot.getPlotId());
        }
    }
    
    private String serializeRoles(Plot plot) {
        // uuid:role,uuid:role serializer
        return plot.getPlayerRoles().entrySet().stream()
                .filter(e -> !e.getKey().equals(plot.getOwner())) // Don't save owner
                .map(e -> e.getKey().toString() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }
    
    private void deserializeRoles(Plot plot, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            for (String pair : data.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    plot.setRole(UUID.fromString(parts[0]), parts[1]);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse roles for plot " + plot.getPlotId());
        }
    }
}
