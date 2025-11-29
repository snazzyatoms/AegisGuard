package com.aegisguard.data;

import com.aegisguard.AegisGuard;
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
 * SQLDataStore (v1.1.2)
 * - High-performance database storage for AegisGuard.
 * - Implements all IDataStore methods, including wilderness logging + revert.
 */
public class SQLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private HikariDataSource hikari;

    // Caches
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Set<Plot>> plotsByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    // --- QUERIES (place your real DDL/queries where the ... are) ---
    private static final String CREATE_PLOTS_TABLE   = "CREATE TABLE IF NOT EXISTS aegis_plots ( ... )";
    private static final String CREATE_ZONES_TABLE   = "CREATE TABLE IF NOT EXISTS aegis_zones ( ... )";
    private static final String UPSERT_PLOT          = "INSERT INTO aegis_plots ( ... ) ON DUPLICATE KEY UPDATE ...";
    private static final String DELETE_ZONES         = "DELETE FROM aegis_zones WHERE plot_id=?";
    private static final String INSERT_ZONE          = "INSERT INTO aegis_zones VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    // Wilderness logging
    private static final String LOG_WILDERNESS       =
            "INSERT INTO aegis_wilderness_log " +
            "(world, x, y, z, old_material, new_material, timestamp, player_uuid) " +
            "VALUES (?,?,?,?,?,?,?,?)";

    private static final String GET_REVERTABLE_BLOCKS =
            "SELECT id, world, x, y, z, old_material " +
            "FROM aegis_wilderness_log WHERE timestamp < ? LIMIT ?";

    private static final String DELETE_WILDERNESS_BY_ID =
            "DELETE FROM aegis_wilderness_log WHERE id = ?";

    // Plot queries
    private static final String DELETE_PLOT           = "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_PLOTS_BY_OWNER = "DELETE FROM aegis_plots WHERE owner_uuid = ?";
    private static final String UPDATE_PLOT_OWNER     =
            "UPDATE aegis_plots SET owner_uuid = ?, owner_name = ?, roles = ?, is_for_sale = ?, sale_price = ? WHERE plot_id = ?";
    private static final String LOAD_PLOTS            = "SELECT * FROM aegis_plots";
    private static final String LOAD_ZONES            = "SELECT * FROM aegis_zones WHERE plot_id = ?";

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

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL);
            config.setUsername(db.getString("username", "root"));
            config.setPassword(db.getString("password", ""));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        this.hikari = new HikariDataSource(config);

        try (Connection conn = hikari.getConnection();
             Statement s = conn.createStatement()) {

            s.execute(CREATE_PLOTS_TABLE);
            s.execute(CREATE_ZONES_TABLE);
            // any extra ALTER TABLE statements etc...
        } catch (SQLException e) {
            plugin.getLogger().severe("Database Error: " + e.getMessage());
        }
    }

    @Override
    public void load() {
        // your existing load logic here (LOAD_PLOTS + LOAD_ZONES etc.)
    }

    private void loadZones(Connection conn) throws SQLException {
        // your existing loadZones logic here
    }

    @Override
    public void save() {
        // your existing save logic here
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

    // ==============================================================
    // --- IDataStore API Implementation ---
    // ==============================================================

    @Override
    public boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2) {
        Set<String> intersectingKeys = getChunksInArea(world, x1, z1, x2, z2);

        for (String chunkKey : intersectingKeys) {
            Set<Plot> plots = plotsByChunk.get(chunkKey);
            if (plots == null) continue;

            for (Plot p : plots) {
                if (plotToIgnore != null && p.equals(plotToIgnore)) continue;

                // Simple AABB overlap check
                if (!(x1 > p.getX2() || x2 < p.getX1() || z1 > p.getZ2() || z2 < p.getZ1())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        Plot p = getPlot(owner, plotId);
        if (p != null) {
            List<Plot> list = plotsByOwner.get(owner);
            if (list != null) {
                list.remove(p);
            }

            deIndexPlot(p);
            isDirty = true;

            plugin.runGlobalAsync(() -> {
                try (Connection conn = hikari.getConnection();
                     PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                    ps.setString(1, plotId.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to delete plot " + plotId + " : " + e.getMessage());
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

            plugin.runGlobalAsync(() -> {
                try (Connection conn = hikari.getConnection();
                     PreparedStatement ps = conn.prepareStatement(DELETE_PLOTS_BY_OWNER)) {
                    ps.setString(1, owner.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to delete all plots for owner " + owner + " : " + e.getMessage());
                }
            });
        }
    }

    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        // your existing implementation here (update caches + DB using UPDATE_PLOT_OWNER)
    }

    @Override
    public void addPlayerRole(Plot plot, UUID uuid, String role) {
        plot.setRole(uuid, role);
        isDirty = true;
    }

    @Override
    public void removePlayerRole(Plot plot, UUID uuid) {
        plot.removeRole(uuid);
        isDirty = true;
    }

    @Override
    public void removeBannedPlots() {
        // your existing logic (if any)
    }

    // ==============================================================
    // --- Wilderness Logging + Revert (NEW IMPLEMENTATIONS) ---
    // ==============================================================

    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        final String worldName = loc.getWorld().getName();
        final int x = loc.getBlockX();
        final int y = loc.getBlockY();
        final int z = loc.getBlockZ();
        final long timestamp = System.currentTimeMillis();
        final String playerId = (playerUUID != null ? playerUUID.toString() : null);

        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LOG_WILDERNESS)) {

                ps.setString(1, worldName);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setString(5, oldMat);
                ps.setString(6, newMat);
                ps.setLong(7, timestamp);
                ps.setString(8, playerId);

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[AegisGuard] Failed to log wilderness block: " + e.getMessage());
            }
        });
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        plugin.getLogger().info("[AegisGuard] Wilderness revert task triggered (SQL)");

        plugin.runGlobalAsync(() -> {
            List<WildernessRecord> records = new ArrayList<>();

            // 1) Load revertable blocks from DB
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(GET_REVERTABLE_BLOCKS)) {

                ps.setLong(1, timestamp);
                ps.setInt(2, limit);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String world = rs.getString("world");
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        String oldMat = rs.getString("old_material");

                        records.add(new WildernessRecord(id, world, x, y, z, oldMat));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[AegisGuard] Failed to load wilderness revert records: " + e.getMessage());
                return;
            }

            if (records.isEmpty()) {
                return;
            }

            // 2) Apply block changes on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (WildernessRecord rec : records) {
                    World world = Bukkit.getWorld(rec.world);
                    if (world == null) continue;

                    Material mat = Material.matchMaterial(rec.oldMaterial);
                    if (mat == null) continue;

                    world.getBlockAt(rec.x, rec.y, rec.z).setType(mat, false);
                }
            });

            // 3) Delete processed records from DB (async)
            plugin.runGlobalAsync(() -> {
                try (Connection conn = hikari.getConnection();
                     PreparedStatement ps = conn.prepareStatement(DELETE_WILDERNESS_BY_ID)) {

                    for (WildernessRecord rec : records) {
                        ps.setLong(1, rec.id);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                } catch (SQLException e) {
                    plugin.getLogger().warning("[AegisGuard] Failed to delete wilderness records: " + e.getMessage());
                }
            });
        });
    }

    private static class WildernessRecord {
        final long id;
        final String world;
        final int x, y, z;
        final String oldMaterial;

        WildernessRecord(long id, String world, int x, int y, int z, String oldMaterial) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldMaterial = oldMaterial;
        }
    }

    // ==============================================================
    // --- Indexing Helpers (same as your existing ones) ---
    // ==============================================================

    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ";" + (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4);
    }

    private void indexPlot(Plot plot) {
        // your existing indexing logic here
    }

    private void deIndexPlot(Plot plot) {
        // your existing de-index logic here
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

    // ==============================================================
    // --- Basic Accessors / Mutators (as in your class) ---
    // ==============================================================

    @Override
    public List<Plot> getPlots(UUID owner) {
        return plotsByOwner.getOrDefault(owner, Collections.emptyList());
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
                .filter(Plot::isForSale)
                .toList();
    }

    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream()
                .filter(p -> "AUCTION".equals(p.getPlotStatus()))
                .toList();
    }

    @Override
    public Plot getPlotAt(Location loc) {
        Set<Plot> chunkPlots = plotsByChunk.get(getChunkKey(loc));
        if (chunkPlots == null) return null;
        for (Plot p : chunkPlots) {
            if (p.isInside(loc)) return p;
        }
        return null;
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        return getPlots(owner).stream()
                .filter(p -> p.getPlotId().equals(plotId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        // your existing createPlot logic here
    }

    @Override
    public void addPlot(Plot plot) {
        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
        indexPlot(plot);
        isDirty = true;
    }

    // ...rest of your methods (if any)...
}
