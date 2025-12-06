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

    private final Map<UUID, List<Estate>> estatesByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Estate>>> estatesByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    private static final String UPSERT_PLOT = "INSERT INTO aegis_plots (plot_id, owner_uuid, owner_name, world, x1, z1, x2, z2, flags) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE owner_uuid=?, owner_name=?, flags=?";
    private static final String SELECT_ALL  = "SELECT * FROM aegis_plots";
    private static final String DELETE_PLOT = "DELETE FROM aegis_plots WHERE plot_id = ?";
    private static final String DELETE_ALL  = "DELETE FROM aegis_plots WHERE owner_uuid = ?";

    public SQLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        ConfigurationSection db = plugin.cfg().raw().getConfigurationSection("storage.database");
        String type = plugin.cfg().raw().getString("storage.type", "sqlite");
        HikariConfig config = new HikariConfig();
        config.setPoolName("AegisGuard-Pool");

        if (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb")) {
            String host = db.getString("host", "localhost");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + db.getInt("port", 3306) + "/" + db.getString("database", "aegisguard") + "?useSSL=" + db.getBoolean("useSSL", false));
            config.setUsername(db.getString("username", "root"));
            config.setPassword(db.getString("password", ""));
        } else {
            File file = new File(plugin.getDataFolder(), "aegisguard.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        this.hikari = new HikariDataSource(config);
        try (Connection conn = hikari.getConnection(); Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS aegis_plots ( plot_id VARCHAR(36) PRIMARY KEY, owner_uuid VARCHAR(36), owner_name VARCHAR(32), world VARCHAR(32), x1 INT, z1 INT, x2 INT, z2 INT, flags TEXT )");
            s.execute("CREATE TABLE IF NOT EXISTS aegis_wilderness ( id BIGINT PRIMARY KEY, world VARCHAR(32), x INT, y INT, z INT, type VARCHAR(32), data BLOB )");
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error: " + e.getMessage());
        }
    }

    @Override
    public void load() {
        estatesByOwner.clear();
        estatesByChunk.clear();

        try (Connection conn = hikari.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID plotId = UUID.fromString(rs.getString("plot_id"));
                    String ownerStr = rs.getString("owner_uuid");
                    UUID ownerId = (ownerStr == null || ownerStr.isEmpty()) ? null : UUID.fromString(ownerStr);
                    String ownerName = rs.getString("owner_name");
                    String worldName = rs.getString("world");
                    int x1 = rs.getInt("x1");
                    int z1 = rs.getInt("z1");
                    int x2 = rs.getInt("x2");
                    int z2 = rs.getInt("z2");

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    Location min = new Location(world, x1, world.getMinHeight(), z1);
                    Location max = new Location(world, x2, world.getMaxHeight(), z2);
                    Cuboid region = new Cuboid(min, max);

                    Estate estate = new Estate(plotId, ownerName, ownerId, false, world, region);

                    String flagsRaw = rs.getString("flags");
                    if (flagsRaw != null && !flagsRaw.isEmpty()) {
                        for (String part : flagsRaw.split(",")) {
                            String[] kv = part.split(":");
                            if (kv.length == 2) estate.setFlag(kv[0], Boolean.parseBoolean(kv[1]));
                        }
                    }
                    addEstateToCache(estate);
                    plugin.getEstateManager().registerEstateFromLoad(estate);

                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped invalid plot: " + ex.getMessage());
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public void saveEstate(Estate estate) {
        addEstateToCache(estate);
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_PLOT)) {

                int x1 = estate.getRegion().getLowerNE().getBlockX();
                int z1 = estate.getRegion().getLowerNE().getBlockZ();
                int x2 = estate.getRegion().getUpperSW().getBlockX();
                int z2 = estate.getRegion().getUpperSW().getBlockZ();

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
                ps.setString(10, estate.getOwnerId() == null ? "" : estate.getOwnerId().toString());
                ps.setString(11, estate.getName());
                ps.setString(12, sb.toString());

                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    @Override
    public void updateEstateOwner(Estate estate, UUID newOwner, boolean isGuild) {
        saveEstate(estate);
    }
    
    @Override
    public void removeEstate(UUID plotId) {
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_PLOT)) {
                ps.setString(1, plotId.toString());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    // --- INTERFACE FIX: Added Alias deleteEstate ---
    @Override
    public void deleteEstate(UUID id) {
        removeEstate(id);
    }

    // --- INTERFACE FIX: Added removeAllPlots ---
    @Override
    public void removeAllPlots(UUID owner) {
        plugin.runGlobalAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_ALL)) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
        estatesByOwner.remove(owner);
    }

    // --- Cache & Interface Impls ---
    @Override public void revertWildernessBlocks(long timestamp, int limit) {}
    @Override public void logWildernessBlock(Location loc, String old, String newMat, UUID uuid) {}
    @Override public boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID excludeId) { return false; }
    
    private void addEstateToCache(Estate estate) {
        if (estate.getOwnerId() != null) {
            estatesByOwner.computeIfAbsent(estate.getOwnerId(), k -> new ArrayList<>()).add(estate);
        }
        int cX1 = estate.getRegion().getLowerNE().getBlockX() >> 4;
        int cZ1 = estate.getRegion().getLowerNE().getBlockZ() >> 4;
        int cX2 = estate.getRegion().getUpperSW().getBlockX() >> 4;
        int cZ2 = estate.getRegion().getUpperSW().getBlockZ() >> 4;

        Map<String, Set<Estate>> worldMap = estatesByChunk.computeIfAbsent(estate.getWorld().getName(), k -> new ConcurrentHashMap<>());
        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                worldMap.computeIfAbsent(x + ";" + z, k -> ConcurrentHashMap.newKeySet()).add(estate);
            }
        }
    }

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
    
    @Override public void save() {}
    @Override public void saveSync() {}
    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }

    // --- Missing Stubs ---
    @Override public List<Estate> getEstates(UUID owner) { return estatesByOwner.getOrDefault(owner, Collections.emptyList()); }
    @Override public Collection<Estate> getAllEstates() { return plugin.getEstateManager().getAllEstates(); }
    @Override public Collection<Estate> getEstatesForSale() { return new ArrayList<>(); }
    @Override public Collection<Estate> getEstatesForAuction() { return new ArrayList<>(); }
    @Override public Estate getEstate(UUID owner, UUID plotId) { return plugin.getEstateManager().getEstate(plotId); }
    @Override public void createEstate(UUID owner, Location c1, Location c2) {}
    @Override public void addEstate(Estate estate) { saveEstate(estate); }
    @Override public void addPlayerRole(Estate estate, UUID uuid, String role) { saveEstate(estate); }
    @Override public void removePlayerRole(Estate estate, UUID uuid) { saveEstate(estate); }
    @Override public void removeBannedEstates() {}
}
