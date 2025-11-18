package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SQLDataStore
 * - An implementation of IDataStore that uses SQL (SQLite or MySQL).
 * - This is the "Ultimate" version, supporting all features.
 * - This is a "write-through cache" implementation.
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // --- In-Memory Cache ---
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
            roles TEXT,
            spawn_location TEXT,
            welcome_msg TEXT,
            farewell_msg TEXT,
            is_for_sale BOOLEAN DEFAULT false,
            sale_price DOUBLE DEFAULT 0.0,
            is_for_rent BOOLEAN DEFAULT false,
            rent_price DOUBLE DEFAULT 0.0,
            renter_uuid CHAR(36),
            rent_expires BIGINT DEFAULT 0,
            plot_status VARCHAR(16) DEFAULT 'ACTIVE',
            border_particle VARCHAR(64),
            ambient_particle VARCHAR(64),
            entry_effect VARCHAR(64),
            current_bid DOUBLE DEFAULT 0.0,
            current_bidder CHAR(36)
        );
        """;
    private static final String CREATE_INDEXES = "CREATE INDEX IF NOT EXISTS idx_owner_uuid ON aegis_plots (owner_uuid); CREATE INDEX IF NOT EXISTS idx_world ON aegis_plots (world);";
    
    // --- Wilderness Log Table ---
    private static final String CREATE_WILDERNESS_TABLE = """
        CREATE TABLE IF NOT EXISTS aegis_wilderness_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            world VARCHAR(64) NOT NULL,
            x INT NOT NULL,
            y INT NOT NULL,
            z INT NOT NULL,
            old_material VARCHAR(64) NOT NULL,
            new_material VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            player_uuid CHAR(36) NOT NULL
        );
        """;
    private static final String CREATE_WILDERNESS_INDEX = "CREATE INDEX IF NOT EXISTS idx_wilderness_timestamp ON aegis_wilderness_log (timestamp);";

    // --- Alter Table (for updates) ---
    private static final String[] ALTER_TABLES = {
        "ALTER TABLE aegis_plots ADD COLUMN spawn_location TEXT;",
        "ALTER TABLE aegis_plots ADD COLUMN welcome_msg TEXT;",
        "ALTER TABLE aegis_plots ADD COLUMN farewell_msg TEXT;",
        "ALTER TABLE aegis_plots ADD COLUMN is_for_sale BOOLEAN DEFAULT false;",
        "ALTER TABLE aegis_plots ADD COLUMN sale_price DOUBLE DEFAULT 0.0;",
        "ALTER TABLE aegis_plots ADD COLUMN is_for_rent BOOLEAN DEFAULT false;",
        "ALTER TABLE aegis_plots ADD COLUMN rent_price DOUBLE DEFAULT 0.0;",
        "ALTER TABLE aegis_plots ADD COLUMN renter_uuid CHAR(36);",
        "ALTER TABLE aegis_plots ADD COLUMN rent_expires BIGINT DEFAULT 0;",
        "ALTER TABLE aegis_plots ADD COLUMN plot_status VARCHAR(16) DEFAULT 'ACTIVE';",
        "ALTER TABLE aegis_plots ADD COLUMN border_particle VARCHAR(64);",
        "ALTER TABLE aegis_plots ADD COLUMN ambient_particle VARCHAR(64);",
        "ALTER TABLE aegis_plots ADD COLUMN entry_effect VARCHAR(64);",
        "ALTER TABLE aegis_plots ADD COLUMN current_bid DOUBLE DEFAULT 0.0;",
        "ALTER TABLE aegis_plots ADD COLUMN current_bidder CHAR(36);"
    };
    
    private static final String LOAD_PLOTS = "SELECT * FROM aegis_plots";
    
    private static final String UPSERT_PLOT = """
        INSERT INTO aegis_plots (
            plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, last_upkeep, flags, roles, 
            spawn_location, welcome_msg, farewell_msg,
            is_for_sale, sale_price, is_for_rent, rent_price, renter_uuid, rent_expires,
            plot_status, border_particle, ambient_particle, entry_effect,
            current_bid, current_bidder
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(plot_id) DO UPDATE SET
        owner_name = EXCLUDED.owner_name,
        x1 = EXCLUDED.x1, z1 = EXCLUDED.z1, x2 = EXCLUDED.x2, z2 = EXCLUDED.z2,
        last_upkeep = EXCLUDED.last_upkeep,
        flags = EXCLUDED.flags, roles = EXCLUDED.roles,
        spawn_location = EXCLUDED.spawn_location,
        welcome_msg = EXCLUDED.welcome_msg, farewell_msg = EXCLUDED.farewell_msg,
        is_for_sale = EXCLUDED.is_for_sale, sale_price = EXCLUDED.sale_price,
        is_for_rent = EXCLUDED.is_for_rent, rent_price = EXCLUDED.rent_price,
        renter_uuid = EXCLUDED.renter_uuid, rent_expires = EXCLUDED.rent_expires,
        plot_status = EXCLUDED.plot_status,
        border_particle = EXCLUDED.border_particle,
        ambient_particle = EXCLUDED.ambient_particle,
        entry_effect = EXCLUDED.entry_effect,
        current_bid = EXCLUDED.current_bid,
        current_bidder = EXCLUDED.current_bidder;
        """;
        
    private static final String DELETE_PLOT = "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_PLOTS_BY_OWNER = "DELETE FROM aegis_plots WHERE owner_uuid = ?";
    private static final String UPDATE_PLOT_OWNER = "UPDATE aegis_plots SET owner_uuid = ?, owner_name = ?, roles = ?, is_for_sale = ? WHERE plot_id = ?";

    // --- Wilderness Log Queries ---
    private static final String LOG_WILDERNESS_BLOCK = "INSERT INTO aegis_wilderness_log (world, x, y, z, old_material, new_material, timestamp, player_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String GET_REVERTABLE_BLOCKS = "SELECT id, world, x, y, z, old_material FROM aegis_wilderness_log WHERE timestamp < ? LIMIT ?";
    private static final String DELETE_REVERTED_BLOCKS = "DELETE FROM aegis_wilderness_log WHERE id = ?";


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
             Statement s = conn.createStatement()) {
            
            s.execute(CREATE_PLOTS_TABLE);
            s.execute(CREATE_INDEXES);
            s.execute(CREATE_WILDERNESS_TABLE);
            s.execute(CREATE_WILDERNESS_INDEX);
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }
        
        // Update tables (add new columns if they don't exist)
        for (String alterQuery : ALTER_TABLES) {
            try (Connection conn = hikari.getConnection();
                 Statement s = conn.createStatement()) {
                s.execute(alterQuery);
            } catch (SQLException e) {
                // This is (usually) okay. It just means the column already exists.
                // A better system would check metadata, but this is fine for SQLite/MySQL.
            }
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

                // --- Load ALL Ultimate Fields ---
                plot.setSpawnLocationFromString(rs.getString("spawn_location"));
                plot.setWelcomeMessage(rs.getString("welcome_msg"));
                plot.setFarewellMessage(rs.getString("farewell_msg"));
                
                plot.setForSale(rs.getBoolean("is_for_sale"), rs.getDouble("sale_price"));
                plot.setForRent(rs.getBoolean("is_for_rent"), rs.getDouble("rent_price"));
                plot.setRenter(rs.getString("renter_uuid") != null ? UUID.fromString(rs.getString("renter_uuid")) : null, rs.getLong("rent_expires"));

                plot.setPlotStatus(rs.getString("plot_status"));
                plot.setCurrentBid(rs.getDouble("current_bid"),
                                   rs.getString("current_bidder") != null ? UUID.fromString(rs.getString("current_bidder")) : null);

                plot.setBorderParticle(rs.getString("border_particle"));
                plot.setAmbientParticle(rs.getString("ambient_particle"));
                plot.setEntryEffect(rs.getString("entry_effect"));

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
        if (!isDirty) return;
        
        plugin.getLogger().info("Saving all plot data to database...");
        
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
    public Collection<Plot> getPlotsForSale() {
        return getAllPlots().stream()
                .filter(plot -> plot.isForSale() && "ACTIVE".equals(plot.getPlotStatus()))
                .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream()
                .filter(plot -> "AUCTION".equals(plot.getPlotStatus()))
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
            if (owned.isEmpty()) {
                plotsByOwner.remove(owner);
            }
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
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        UUID oldOwner = plot.getOwner();
        
        // 1. Update in-memory cache
        List<Plot> oldList = plotsByOwner.get(oldOwner);
        if (oldList != null) {
            oldList.remove(plot);
            if (oldList.isEmpty()) {
                plotsByOwner.remove(oldOwner);
            }
        }
        
        plot.internalSetOwner(newOwner, newOwnerName);
        plot.setForSale(false, 0); // No longer for sale
        
        plotsByOwner.computeIfAbsent(newOwner, k -> new ArrayList<>()).add(plot);
        
        // 2. Update database
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_PLOT_OWNER)) {
                
                ps.setString(1, newOwner.toString());
                ps.setString(2, newOwnerName);
                ps.setString(3, serializeRoles(plot)); // Save new roles (owner)
                ps.setBoolean(4, false); // Not for sale
                ps.setString(5, plot.getPlotId().toString());
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to change plot owner in database: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /* -----------------------------
     * Role Management API
     * ----------------------------- */

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

    /* -----------------------------
     * Admin Helpers
     * ----------------------------- */
     
    @Override
    public void removeBannedPlots() {
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
    
    /* -----------------------------
     * --- NEW: Wilderness Revert API ---
     * ----------------------------- */

    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        // This is called from an async task in ProtectionManager
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOG_WILDERNESS_BLOCK)) {
            
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
            plugin.getLogger().warning("Failed to log wilderness block: " + e.getMessage());
        }
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        // This runs on the WildernessRevertTask thread
        
        Map<Integer, Location> blocksToRevert = new HashMap<>();
        Map<Integer, Material> materialsToSet = new HashMap<>();
        
        // 1. Fetch blocks to revert
        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_REVERTABLE_BLOCKS)) {
            
            ps.setLong(1, timestamp);
            ps.setInt(2, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue;
                    
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    Material oldMat = Material.matchMaterial(rs.getString("old_material"));
                    
                    if (oldMat != null) {
                        blocksToRevert.put(id, loc);
                        materialsToSet.put(id, oldMat);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to query wilderness blocks for revert: " + e.getMessage());
            return;
        }
        
        if (blocksToRevert.isEmpty()) {
            return; // Nothing to do
        }
        
        plugin.getLogger().info("Reverting " + blocksToRevert.size() + " wilderness blocks...");

        // 2. Revert blocks (on main thread) and delete from log
        plugin.runMainGlobal(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement psDelete = conn.prepareStatement(DELETE_REVERTED_BLOCKS)) {

                for (Map.Entry<Integer, Location> entry : blocksToRevert.entrySet()) {
                    int id = entry.getKey();
                    Location loc = entry.getValue();
                    Material mat = materialsToSet.get(id);
                    
                    // Revert the block
                    if (loc.isWorldLoaded() && loc.getChunk().isLoaded()) {
                        Block b = loc.getBlock();
                        if (b.getType() != mat) {
                            b.setType(mat, true); // True to apply physics
                        }
                    }
                    
                    // Add to delete batch
                    psDelete.setInt(1, id);
                    psDelete.addBatch();
                }
                
                // 3. Delete all processed entries
                psDelete.executeBatch();
                
            } catch (SQLException e) {
                 plugin.getLogger().severe("Failed to delete reverted blocks from log: " + e.getMessage());
            }
        });
    }
}
