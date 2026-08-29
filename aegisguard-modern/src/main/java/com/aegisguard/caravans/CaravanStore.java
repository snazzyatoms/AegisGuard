package com.aegisguard.caravans;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.SQLDataStore;
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
 * YAML is canonical ({@code caravans.yml}). SQL servers also dual-write a copy.
 */
public final class CaravanStore {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS aegis_caravans (" +
                    " caravan_id VARCHAR(36) PRIMARY KEY," +
                    " owner_id VARCHAR(36)," +
                    " owner_name VARCHAR(64)," +
                    " origin_beacon VARCHAR(36)," +
                    " dest_beacon VARCHAR(36)," +
                    " origin_name VARCHAR(64)," +
                    " dest_name VARCHAR(64)," +
                    " dest_plot_owner VARCHAR(36)," +
                    " escort_id VARCHAR(36)," +
                    " cargo_value DOUBLE," +
                    " fee DOUBLE," +
                    " insurance_premium DOUBLE," +
                    " charged_vault DOUBLE," +
                    " insured BOOLEAN," +
                    " status VARCHAR(24)," +
                    " last_event VARCHAR(24)," +
                    " dispatched_at BIGINT," +
                    " eta_at BIGINT," +
                    " arrived_at BIGINT," +
                    " delivered_value DOUBLE," +
                    " toll_paid DOUBLE," +
                    " escort_paid DOUBLE," +
                    " fail_reason VARCHAR(128)," +
                    " notified BOOLEAN" +
                    " )";

    private final AegisGuard plugin;
    private final File file;
    private final Map<UUID, Caravan> caravans = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirty;

    public CaravanStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "caravans.yml");
    }

    public Collection<Caravan> all() {
        return List.copyOf(caravans.values());
    }

    public Caravan get(UUID id) {
        return id == null ? null : caravans.get(id);
    }

    public List<Caravan> forOwner(UUID ownerId) {
        if (ownerId == null) return List.of();
        List<Caravan> list = new ArrayList<>();
        for (Caravan caravan : caravans.values()) {
            if (caravan != null && ownerId.equals(caravan.getOwnerId())) list.add(caravan);
        }
        return list;
    }

    public List<Caravan> inFlight() {
        List<Caravan> list = new ArrayList<>();
        for (Caravan caravan : caravans.values()) {
            if (caravan != null && caravan.inFlight()) list.add(caravan);
        }
        return list;
    }

    public int activeCount(UUID ownerId) {
        if (ownerId == null) return 0;
        int count = 0;
        for (Caravan caravan : caravans.values()) {
            if (caravan != null && ownerId.equals(caravan.getOwnerId()) && caravan.inFlight()) count++;
        }
        return count;
    }

    public void put(Caravan caravan) {
        if (caravan == null) return;
        caravans.put(caravan.getId(), caravan);
        dirty = true;
    }

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }

    public void load() {
        caravans.clear();
        synchronized (ioLock) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("caravans");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    Caravan caravan = readSection(root.getConfigurationSection(key), key);
                    if (caravan != null) caravans.put(caravan.getId(), caravan);
                }
            }
        }
        if (caravans.isEmpty()) loadSqlFallback();
        dirty = false;
    }

    public void save() {
        synchronized (ioLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Caravan caravan : caravans.values()) {
                if (caravan == null) continue;
                String path = "caravans." + caravan.getId();
                yaml.set(path + ".owner", caravan.getOwnerId() == null ? null : caravan.getOwnerId().toString());
                yaml.set(path + ".owner_name", caravan.getOwnerName());
                yaml.set(path + ".origin", caravan.getOriginBeaconId() == null ? null : caravan.getOriginBeaconId().toString());
                yaml.set(path + ".dest", caravan.getDestBeaconId() == null ? null : caravan.getDestBeaconId().toString());
                yaml.set(path + ".origin_name", caravan.getOriginName());
                yaml.set(path + ".dest_name", caravan.getDestName());
                yaml.set(path + ".dest_plot_owner", caravan.getDestPlotOwner() == null ? null : caravan.getDestPlotOwner().toString());
                yaml.set(path + ".escort", caravan.getEscortId() == null ? null : caravan.getEscortId().toString());
                yaml.set(path + ".cargo", caravan.getCargoValue());
                yaml.set(path + ".fee", caravan.getFee());
                yaml.set(path + ".insurance_premium", caravan.getInsurancePremium());
                yaml.set(path + ".charged", caravan.getChargedVault());
                yaml.set(path + ".insured", caravan.isInsured());
                yaml.set(path + ".status", caravan.getStatus().name());
                yaml.set(path + ".last_event", caravan.getLastEvent().name());
                yaml.set(path + ".dispatched_at", caravan.getDispatchedAt());
                yaml.set(path + ".eta_at", caravan.getEtaAt());
                yaml.set(path + ".arrived_at", caravan.getArrivedAt());
                yaml.set(path + ".delivered", caravan.getDeliveredValue());
                yaml.set(path + ".toll", caravan.getTollPaid());
                yaml.set(path + ".escort_paid", caravan.getEscortPaid());
                yaml.set(path + ".fail_reason", caravan.getFailReason());
                yaml.set(path + ".notified", caravan.isNotified());
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                yaml.save(file);
                dirty = false;
            } catch (IOException error) {
                plugin.getLogger().log(Level.WARNING, "Could not save caravans.yml", error);
            }
            saveSqlAll();
        }
    }

    private Caravan readSection(ConfigurationSection section, String key) {
        if (section == null) return null;
        UUID id;
        try {
            id = UUID.fromString(key);
        } catch (Exception ignored) {
            return null;
        }
        Caravan caravan = new Caravan(id);
        caravan.setOwnerId(parseUuid(section.getString("owner")));
        caravan.setOwnerName(section.getString("owner_name", "Unknown"));
        caravan.setOriginBeaconId(parseUuid(section.getString("origin")));
        caravan.setDestBeaconId(parseUuid(section.getString("dest")));
        caravan.setOriginName(section.getString("origin_name", "Origin"));
        caravan.setDestName(section.getString("dest_name", "Destination"));
        caravan.setDestPlotOwner(parseUuid(section.getString("dest_plot_owner")));
        caravan.setEscortId(parseUuid(section.getString("escort")));
        caravan.setCargoValue(section.getDouble("cargo", 0.0D));
        caravan.setFee(section.getDouble("fee", 0.0D));
        caravan.setInsurancePremium(section.getDouble("insurance_premium", 0.0D));
        caravan.setChargedVault(section.getDouble("charged", 0.0D));
        caravan.setInsured(section.getBoolean("insured", false));
        caravan.setStatus(Caravan.Status.parse(section.getString("status")));
        caravan.setLastEvent(CaravanRules.Event.parse(section.getString("last_event")));
        caravan.setDispatchedAt(section.getLong("dispatched_at", System.currentTimeMillis()));
        caravan.setEtaAt(section.getLong("eta_at", 0L));
        caravan.setArrivedAt(section.getLong("arrived_at", 0L));
        caravan.setDeliveredValue(section.getDouble("delivered", 0.0D));
        caravan.setTollPaid(section.getDouble("toll", 0.0D));
        caravan.setEscortPaid(section.getDouble("escort_paid", 0.0D));
        caravan.setFailReason(section.getString("fail_reason", ""));
        caravan.setNotified(section.getBoolean("notified", false));
        return caravan;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Connection sqlConnection() {
        if (!(plugin.store() instanceof SQLDataStore sql)) return null;
        return sql.borrowCaravanConnection();
    }

    private void loadSqlFallback() {
        try (Connection conn = sqlConnection()) {
            if (conn == null) return;
            try (Statement create = conn.createStatement()) {
                create.execute(CREATE_TABLE);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM aegis_caravans");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Caravan caravan = fromSql(rs);
                    if (caravan != null) caravans.put(caravan.getId(), caravan);
                }
            }
            if (!caravans.isEmpty()) {
                dirty = true;
                save();
            }
        } catch (Exception ignored) {}
    }

    private void saveSqlAll() {
        try (Connection conn = sqlConnection()) {
            if (conn == null) return;
            boolean restoreAuto = true;
            try {
                restoreAuto = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try (Statement create = conn.createStatement()) {
                    create.execute(CREATE_TABLE);
                }
                try (PreparedStatement wipe = conn.prepareStatement("DELETE FROM aegis_caravans")) {
                    wipe.executeUpdate();
                }
                String insert = "INSERT INTO aegis_caravans (" +
                        "caravan_id,owner_id,owner_name,origin_beacon,dest_beacon,origin_name,dest_name," +
                        "dest_plot_owner,escort_id,cargo_value,fee,insurance_premium,charged_vault,insured," +
                        "status,last_event,dispatched_at,eta_at,arrived_at,delivered_value,toll_paid," +
                        "escort_paid,fail_reason,notified) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (Caravan caravan : caravans.values()) {
                        bind(ps, caravan);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (Exception inner) {
                try { conn.rollback(); } catch (Exception ignored) {}
                throw inner;
            } finally {
                try { conn.setAutoCommit(restoreAuto); } catch (Exception ignored) {}
            }
        } catch (Exception error) {
            plugin.getLogger().log(Level.FINE, "Caravan SQL dual-write skipped: " + error.getMessage());
        }
    }

    private void bind(PreparedStatement ps, Caravan caravan) throws Exception {
        ps.setString(1, caravan.getId().toString());
        ps.setString(2, caravan.getOwnerId() == null ? null : caravan.getOwnerId().toString());
        ps.setString(3, caravan.getOwnerName());
        ps.setString(4, caravan.getOriginBeaconId() == null ? null : caravan.getOriginBeaconId().toString());
        ps.setString(5, caravan.getDestBeaconId() == null ? null : caravan.getDestBeaconId().toString());
        ps.setString(6, caravan.getOriginName());
        ps.setString(7, caravan.getDestName());
        ps.setString(8, caravan.getDestPlotOwner() == null ? null : caravan.getDestPlotOwner().toString());
        ps.setString(9, caravan.getEscortId() == null ? null : caravan.getEscortId().toString());
        ps.setDouble(10, caravan.getCargoValue());
        ps.setDouble(11, caravan.getFee());
        ps.setDouble(12, caravan.getInsurancePremium());
        ps.setDouble(13, caravan.getChargedVault());
        ps.setBoolean(14, caravan.isInsured());
        ps.setString(15, caravan.getStatus().name());
        ps.setString(16, caravan.getLastEvent().name());
        ps.setLong(17, caravan.getDispatchedAt());
        ps.setLong(18, caravan.getEtaAt());
        ps.setLong(19, caravan.getArrivedAt());
        ps.setDouble(20, caravan.getDeliveredValue());
        ps.setDouble(21, caravan.getTollPaid());
        ps.setDouble(22, caravan.getEscortPaid());
        ps.setString(23, caravan.getFailReason());
        ps.setBoolean(24, caravan.isNotified());
    }

    private Caravan fromSql(ResultSet rs) throws Exception {
        UUID id = UUID.fromString(rs.getString("caravan_id"));
        Caravan caravan = new Caravan(id);
        caravan.setOwnerId(parseUuid(rs.getString("owner_id")));
        caravan.setOwnerName(rs.getString("owner_name"));
        caravan.setOriginBeaconId(parseUuid(rs.getString("origin_beacon")));
        caravan.setDestBeaconId(parseUuid(rs.getString("dest_beacon")));
        caravan.setOriginName(rs.getString("origin_name"));
        caravan.setDestName(rs.getString("dest_name"));
        caravan.setDestPlotOwner(parseUuid(rs.getString("dest_plot_owner")));
        caravan.setEscortId(parseUuid(rs.getString("escort_id")));
        caravan.setCargoValue(rs.getDouble("cargo_value"));
        caravan.setFee(rs.getDouble("fee"));
        caravan.setInsurancePremium(rs.getDouble("insurance_premium"));
        caravan.setChargedVault(rs.getDouble("charged_vault"));
        caravan.setInsured(rs.getBoolean("insured"));
        caravan.setStatus(Caravan.Status.parse(rs.getString("status")));
        caravan.setLastEvent(CaravanRules.Event.parse(rs.getString("last_event")));
        caravan.setDispatchedAt(rs.getLong("dispatched_at"));
        caravan.setEtaAt(rs.getLong("eta_at"));
        caravan.setArrivedAt(rs.getLong("arrived_at"));
        caravan.setDeliveredValue(rs.getDouble("delivered_value"));
        caravan.setTollPaid(rs.getDouble("toll_paid"));
        caravan.setEscortPaid(rs.getDouble("escort_paid"));
        caravan.setFailReason(rs.getString("fail_reason"));
        caravan.setNotified(rs.getBoolean("notified"));
        return caravan;
    }
}
