package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate; // Assuming you renamed Plot to Estate or using Estate object
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // Caches
    private final Map<UUID, List<Estate>> estatesByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Estate>>> estatesByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    // SQL Queries
    private static final String CREATE_PLOTS_TABLE   = "CREATE TABLE IF NOT EXISTS aegis_plots ( plot_id VARCHAR(36) PRIMARY KEY, owner_uuid VARCHAR(36), owner_name VARCHAR(32), world VARCHAR(32), x1 INT, z1 INT, x2 INT, z2 INT, created_at BIGINT, flags TEXT, members TEXT )";
    private static final String UPSERT_PLOT          = "INSERT INTO aegis_plots (plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, created_at, flags, members) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE owner_uuid=?, owner_name=?, flags=?, members=?";
    private static final String SELECT_ALL_PLOTS     = "SELECT * FROM aegis_plots";
    private static final String DELETE_PLOT          = "DELETE FROM aegis_plots WHERE plot_id = ?";

    public SQLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        // ... (Connection logic remains the same, ensure this matches your previous file) ...
        ConfigurationSection db = plugin.cfg().raw().getConfigurationSection("storage.database");
        String type = plugin.cfg().raw().getString("storage.type", "sqlite");

        HikariConfig config = new HikariConfig();
        config.setPoolName("AegisGuard-Pool");

        if (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb")) {
            String host = db.getString("host", "localhost");
            int port = db.getInt("port", 3306);
            String database = db.getString("database", "aegisguard");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + db.getBoolean("useSSL", false));
            config.setUsername(db.getString("username", "root"));
            config.setPassword(db.getString("password", ""));
        } else {
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        this.hikari = new HikariDataSource(config);

        try (Connection conn = hikari.getConnection(); Statement s = conn.createStatement()) {
            s.execute(CREATE_PLOTS_TABLE);
        } catch (SQLException e) {
            plugin.getLogger().severe("Database Error: " + e.getMessage());
        }
    }

    @Override
    public void load() {
        estatesByOwner.clear();
        estatesByChunk.clear();

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_PLOTS);
             ResultSet rs = ps.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                try {
                    // 1. Read Data
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    String ownerStr = rs.getString("owner_uuid");
                    UUID ownerId = (ownerStr == null || ownerStr.isEmpty()) ? null : UUID.fromString(ownerStr);
                    String ownerName = rs.getString("owner_name");
                    String worldName = rs.getString("world");
                    int x1 = rs.getInt("x1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int z2 = rs.getInt("z2");

                    // 2. Reconstruct Object
                    // NOTE: Ensure your Estate constructor matches this signature!
                    Estate estate = new Estate(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2);
                    
                    // 3. Load Flags (Stored as JSON or comma-separated string)
                    String flagsRaw = rs.getString("flags");
                    if (flagsRaw != null && !flagsRaw.isEmpty()) {
                        // Simple parser: "pvp:true,mobs:false"
                        for (String part : flagsRaw.split(",")) {
                            String[] kv = part.split(":");
                            if (kv.length == 2) {
                                estate.setFlag(kv[0], Boolean.parseBoolean(kv[1]));
                            }
                        }
                    }

                    // 4. Cache it
                    addEstateToCache(estate);
                    count++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped invalid plot record: " + ex.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + count + " estates from SQL database.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load plots: " + e.getMessage());
        }
    }

    @Override
    public void saveEstate(Estate estate) {
        // Sync cache first
        addEstateToCache(estate);
        
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {

                ps.setString(1, estate.getPlotId().toString());
                ps.setString(2, estate.getOwnerId() == null ? "" : estate.getOwnerId().toString());
                ps.setString(3, estate.getOwnerName());
                ps.setString(4, estate.getWorld());
                ps.setInt(5, estate.getX1());
                ps.setInt(6, estate.getZ1());
                ps.setInt(7, estate.getX2());
                ps.setInt(8, estate.getZ2());
                ps.setLong(9, System.currentTimeMillis());
                
                // Serialize Flags
                StringBuilder sb = new StringBuilder();
                estate.getFlags().forEach((k, v) -> sb.append(k).append(":").append(v).append(","));
                ps.setString(10, sb.toString());
                
                ps.setString(11, ""); // Placeholder for members serialization

                // Update clause params
                ps.setString(12, estate.getOwnerId() == null ? "" : estate.getOwnerId().toString());
                ps.setString(13, estate.getOwnerName());
                ps.setString(14, sb.toString());
                ps.setString(15, "");

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save plot " + estate.getPlotId() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void removeEstate(UUID plotId) {
        // Remove from cache...
        // (Implementation needed: iterate cache and remove)
        
        // Remove from DB
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                ps.setString(1, plotId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // --- Cache Helper ---
    private void addEstateToCache(Estate estate) {
        if (estate.getOwnerId() != null) {
            estatesByOwner.computeIfAbsent(estate.getOwnerId(), k -> new ArrayList<>()).add(estate);
        }
        // Spatial Hashing
        Map<String, Set<Estate>> worldMap = estatesByChunk.computeIfAbsent(estate.getWorld(), k -> new ConcurrentHashMap<>());
        
        int cX1 = estate.getX1() >> 4;
        int cZ1 = estate.getZ1() >> 4;
        int cX2 = estate.getX2() >> 4;
        int cZ2 = estate.getZ2() >> 4;

        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                String key = x + ";" + z;
                worldMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(estate);
            }
        }
    }

    // ... (Keep existing interface methods like getEstateAt, isDirty, etc.) ...
    // Note: Ensure getEstateAt looks up the 'estatesByChunk' map we populated above.
    
    // --- STUBS for Interface compliance ---
    @Override public void save() {} // Bulk save if needed
    @Override public void saveSync() {}
    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }
    
    // Implement getEstateAt, getEstates(owner), etc. using the maps above.
    @Override
    public Estate getEstateAt(Location loc) {
        if (loc == null) return null;
        Map<String, Set<Estate>> worldMap = estatesByChunk.get(loc.getWorld().getName());
        if (worldMap == null) return null;
        
        String chunkKey = (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4);
        Set<Estate> candidates = worldMap.get(chunkKey);
        
        if (candidates != null) {
            for (Estate e : candidates) {
                if (e.isInside(loc)) return e;
            }
        }
        return null;
    }
}
