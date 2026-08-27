package com.aegisguard.beacon;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.SQLDataStore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * YAML is canonical ({@code beacons.yml}). SQL servers also dual-write a copy.
 */
public final class BeaconStore {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_teleport_beacons (" +
                    " beacon_id VARCHAR(36) PRIMARY KEY," +
                    " plot_id VARCHAR(36)," +
                    " world VARCHAR(64)," +
                    " x INT, y INT, z INT," +
                    " yaw FLOAT, pitch FLOAT," +
                    " material VARCHAR(48)," +
                    " name VARCHAR(64)," +
                    " purpose VARCHAR(24)," +
                    " linked_id VARCHAR(36)," +
                    " custom_model_data INT," +
                    " enabled BOOLEAN," +
                    " owners BOOLEAN, members BOOLEAN, trusted BOOLEAN, guests BOOLEAN," +
                    " alliance_flag BOOLEAN, public_access BOOLEAN, staff_only BOOLEAN," +
                    " require_confirm BOOLEAN, allow_combat BOOLEAN," +
                    " vault_cost DOUBLE, claim_block_cost BIGINT," +
                    " extra_cooldown INT, created_at BIGINT" +
                    " )";

    private final AegisGuard plugin;
    private final File file;
    private final Map<UUID, TeleportBeacon> beacons = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirty;

    public BeaconStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "beacons.yml");
    }

    public Collection<TeleportBeacon> all() {
        return List.copyOf(beacons.values());
    }

    public TeleportBeacon get(UUID id) {
        return id == null ? null : beacons.get(id);
    }

    public List<TeleportBeacon> forPlot(UUID plotId) {
        if (plotId == null) return List.of();
        List<TeleportBeacon> list = new ArrayList<>();
        for (TeleportBeacon beacon : beacons.values()) {
            if (beacon != null && plotId.equals(beacon.getPlotId())) list.add(beacon);
        }
        return list;
    }

    public TeleportBeacon atBlock(String world, int x, int y, int z) {
        if (world == null) return null;
        for (TeleportBeacon beacon : beacons.values()) {
            if (beacon != null
                    && world.equalsIgnoreCase(beacon.getWorldName())
                    && beacon.getX() == x && beacon.getY() == y && beacon.getZ() == z) {
                return beacon;
            }
        }
        return null;
    }

    public void put(TeleportBeacon beacon) {
        if (beacon == null) return;
        beacons.put(beacon.getId(), beacon);
        dirty = true;
        save();
    }

    public boolean remove(UUID id) {
        if (id == null || beacons.remove(id) == null) return false;
        for (TeleportBeacon other : beacons.values()) {
            if (other != null && id.equals(other.getLinkedBeaconId())) {
                other.setLinkedBeaconId(null);
            }
        }
        dirty = true;
        save();
        deleteSql(id);
        return true;
    }

    public boolean isDirty() { return dirty; }

    public void load() {
        beacons.clear();
        synchronized (ioLock) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("beacons");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    TeleportBeacon beacon = readSection(root.getConfigurationSection(key), key);
                    if (beacon != null) beacons.put(beacon.getId(), beacon);
                }
            }
        }
        if (beacons.isEmpty()) loadSqlFallback();
        dirty = false;
    }

    public void save() {
        synchronized (ioLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (TeleportBeacon beacon : beacons.values()) {
                if (beacon == null) continue;
                String path = "beacons." + beacon.getId();
                yaml.set(path + ".plot", beacon.getPlotId() == null ? null : beacon.getPlotId().toString());
                yaml.set(path + ".world", beacon.getWorldName());
                yaml.set(path + ".x", beacon.getX());
                yaml.set(path + ".y", beacon.getY());
                yaml.set(path + ".z", beacon.getZ());
                yaml.set(path + ".yaw", beacon.getYaw());
                yaml.set(path + ".pitch", beacon.getPitch());
                yaml.set(path + ".material", beacon.getPadMaterial().name());
                yaml.set(path + ".name", beacon.getName());
                yaml.set(path + ".purpose", beacon.getPurpose().name());
                yaml.set(path + ".linked", beacon.getLinkedBeaconId() == null ? null : beacon.getLinkedBeaconId().toString());
                yaml.set(path + ".custom_model_data", beacon.getCustomModelData());
                yaml.set(path + ".enabled", beacon.isEnabled());
                yaml.set(path + ".owners", beacon.isOwners());
                yaml.set(path + ".members", beacon.isMembers());
                yaml.set(path + ".trusted", beacon.isTrusted());
                yaml.set(path + ".guests", beacon.isGuests());
                yaml.set(path + ".alliance", beacon.isAlliance());
                yaml.set(path + ".public", beacon.isPublicAccess());
                yaml.set(path + ".staff_only", beacon.isStaffOnly());
                yaml.set(path + ".require_confirm", beacon.isRequireConfirm());
                yaml.set(path + ".allow_combat", beacon.isAllowCombat());
                yaml.set(path + ".vault_cost", beacon.getVaultCost());
                yaml.set(path + ".claim_block_cost", beacon.getClaimBlockCost());
                yaml.set(path + ".extra_cooldown_seconds", beacon.getExtraCooldownSeconds());
                yaml.set(path + ".created_at", beacon.getCreatedAt());
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                yaml.save(file);
                dirty = false;
            } catch (IOException error) {
                plugin.getLogger().log(Level.WARNING, "Could not save beacons.yml", error);
            }
        }
        saveSqlAll();
    }

    private TeleportBeacon readSection(ConfigurationSection section, String key) {
        if (section == null) return null;
        UUID id;
        try {
            id = UUID.fromString(key);
        } catch (Exception ignored) {
            return null;
        }
        TeleportBeacon beacon = new TeleportBeacon(id);
        try {
            String plot = section.getString("plot");
            if (plot != null && !plot.isBlank()) beacon.setPlotId(UUID.fromString(plot));
        } catch (Exception ignored) {}
        applyCoords(beacon, section.getString("world"),
                section.getInt("x"), section.getInt("y"), section.getInt("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        Material mat = Material.matchMaterial(section.getString("material", "LODESTONE"));
        beacon.setPadMaterial(mat == null ? Material.LODESTONE : mat);
        beacon.setName(section.getString("name", "Beacon"));
        beacon.setPurpose(TeleportBeacon.Purpose.parse(section.getString("purpose")));
        try {
            String linked = section.getString("linked");
            if (linked != null && !linked.isBlank()) beacon.setLinkedBeaconId(UUID.fromString(linked));
        } catch (Exception ignored) {}
        beacon.setCustomModelData(section.getInt("custom_model_data", 0));
        beacon.setEnabled(section.getBoolean("enabled", true));
        beacon.setOwners(section.getBoolean("owners", true));
        beacon.setMembers(section.getBoolean("members", false));
        beacon.setTrusted(section.getBoolean("trusted", false));
        beacon.setGuests(section.getBoolean("guests", false));
        beacon.setAlliance(section.getBoolean("alliance", false));
        beacon.setPublicAccess(section.getBoolean("public", false));
        beacon.setStaffOnly(section.getBoolean("staff_only", false));
        beacon.setRequireConfirm(section.getBoolean("require_confirm", true));
        beacon.setAllowCombat(section.getBoolean("allow_combat", false));
        beacon.setVaultCost(section.getDouble("vault_cost", 0.0D));
        beacon.setClaimBlockCost(section.getLong("claim_block_cost", 0L));
        beacon.setExtraCooldownSeconds(section.getInt("extra_cooldown_seconds", 0));
        beacon.setCreatedAt(section.getLong("created_at", System.currentTimeMillis()));
        return beacon;
    }

    private void applyCoords(TeleportBeacon beacon, String world, int x, int y, int z, float yaw, float pitch) {
        beacon.setWorldName(world);
        beacon.setCoordinates(x, y, z, yaw, pitch);
    }

    private Connection sqlConnection() {
        if (!(plugin.store() instanceof SQLDataStore sql)) return null;
        return sql.borrowBeaconConnection();
    }

    private void loadSqlFallback() {
        try (Connection conn = sqlConnection()) {
            if (conn == null) return;
            try (Statement create = conn.createStatement()) {
                create.execute(CREATE_TABLE);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_teleport_beacons");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TeleportBeacon beacon = fromSql(rs);
                    if (beacon != null) beacons.put(beacon.getId(), beacon);
                }
            }
            if (!beacons.isEmpty()) {
                dirty = true;
                save();
            }
        } catch (Exception ignored) {}
    }

    private void saveSqlAll() {
        try (Connection conn = sqlConnection()) {
            if (conn == null) return;
            try (Statement create = conn.createStatement()) {
                create.execute(CREATE_TABLE);
            }
            try (PreparedStatement wipe = conn.prepareStatement("DELETE FROM aegis_teleport_beacons")) {
                wipe.executeUpdate();
            }
            String insert = "INSERT INTO aegis_teleport_beacons (" +
                    "beacon_id,plot_id,world,x,y,z,yaw,pitch,material,name,purpose,linked_id," +
                    "custom_model_data,enabled,owners,members,trusted,guests,alliance_flag," +
                    "public_access,staff_only,require_confirm,allow_combat,vault_cost," +
                    "claim_block_cost,extra_cooldown,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (TeleportBeacon beacon : beacons.values()) {
                    bind(ps, beacon);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception error) {
            plugin.getLogger().log(Level.FINE, "Beacon SQL dual-write skipped: " + error.getMessage());
        }
    }

    private void deleteSql(UUID id) {
        try (Connection conn = sqlConnection()) {
            if (conn == null || id == null) return;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM aegis_teleport_beacons WHERE beacon_id=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    private void bind(PreparedStatement ps, TeleportBeacon beacon) throws Exception {
        ps.setString(1, beacon.getId().toString());
        ps.setString(2, beacon.getPlotId() == null ? null : beacon.getPlotId().toString());
        ps.setString(3, beacon.getWorldName());
        ps.setInt(4, beacon.getX());
        ps.setInt(5, beacon.getY());
        ps.setInt(6, beacon.getZ());
        ps.setFloat(7, beacon.getYaw());
        ps.setFloat(8, beacon.getPitch());
        ps.setString(9, beacon.getPadMaterial().name());
        ps.setString(10, beacon.getName());
        ps.setString(11, beacon.getPurpose().name());
        ps.setString(12, beacon.getLinkedBeaconId() == null ? null : beacon.getLinkedBeaconId().toString());
        ps.setInt(13, beacon.getCustomModelData());
        ps.setBoolean(14, beacon.isEnabled());
        ps.setBoolean(15, beacon.isOwners());
        ps.setBoolean(16, beacon.isMembers());
        ps.setBoolean(17, beacon.isTrusted());
        ps.setBoolean(18, beacon.isGuests());
        ps.setBoolean(19, beacon.isAlliance());
        ps.setBoolean(20, beacon.isPublicAccess());
        ps.setBoolean(21, beacon.isStaffOnly());
        ps.setBoolean(22, beacon.isRequireConfirm());
        ps.setBoolean(23, beacon.isAllowCombat());
        ps.setDouble(24, beacon.getVaultCost());
        ps.setLong(25, beacon.getClaimBlockCost());
        ps.setInt(26, beacon.getExtraCooldownSeconds());
        ps.setLong(27, beacon.getCreatedAt());
    }

    private TeleportBeacon fromSql(ResultSet rs) throws Exception {
        UUID id = UUID.fromString(rs.getString("beacon_id"));
        TeleportBeacon beacon = new TeleportBeacon(id);
        String plot = rs.getString("plot_id");
        if (plot != null && !plot.isBlank()) beacon.setPlotId(UUID.fromString(plot));
        applyCoords(beacon, rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"));
        Material mat = Material.matchMaterial(rs.getString("material"));
        beacon.setPadMaterial(mat == null ? Material.LODESTONE : mat);
        beacon.setName(rs.getString("name"));
        beacon.setPurpose(TeleportBeacon.Purpose.parse(rs.getString("purpose")));
        String linked = rs.getString("linked_id");
        if (linked != null && !linked.isBlank()) beacon.setLinkedBeaconId(UUID.fromString(linked));
        beacon.setCustomModelData(rs.getInt("custom_model_data"));
        beacon.setEnabled(rs.getBoolean("enabled"));
        beacon.setOwners(rs.getBoolean("owners"));
        beacon.setMembers(rs.getBoolean("members"));
        beacon.setTrusted(rs.getBoolean("trusted"));
        beacon.setGuests(rs.getBoolean("guests"));
        beacon.setAlliance(rs.getBoolean("alliance_flag"));
        beacon.setPublicAccess(rs.getBoolean("public_access"));
        beacon.setStaffOnly(rs.getBoolean("staff_only"));
        beacon.setRequireConfirm(rs.getBoolean("require_confirm"));
        beacon.setAllowCombat(rs.getBoolean("allow_combat"));
        beacon.setVaultCost(rs.getDouble("vault_cost"));
        beacon.setClaimBlockCost(rs.getLong("claim_block_cost"));
        beacon.setExtraCooldownSeconds(rs.getInt("extra_cooldown"));
        beacon.setCreatedAt(rs.getLong("created_at"));
        return beacon;
    }
}
