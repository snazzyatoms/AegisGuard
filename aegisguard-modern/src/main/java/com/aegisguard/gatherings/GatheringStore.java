package com.aegisguard.gatherings;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** YAML store for live Open House gatherings ({@code gatherings.yml}). */
public final class GatheringStore {

    private final AegisGuard plugin;
    private final Logger logger;
    private final File file;
    private final Map<UUID, Gathering> byPlot = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirty;

    public GatheringStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.logger = plugin == null ? Logger.getAnonymousLogger() : plugin.getLogger();
        this.file = plugin == null ? null : new File(plugin.getDataFolder(), "gatherings.yml");
    }

    public GatheringStore(File file, Logger logger) {
        this.plugin = null;
        this.logger = logger == null ? Logger.getAnonymousLogger() : logger;
        this.file = file;
    }

    public Collection<Gathering> all() {
        return List.copyOf(byPlot.values());
    }

    public Gathering get(UUID plotId) {
        return plotId == null ? null : byPlot.get(plotId);
    }

    public void put(Gathering gathering) {
        if (gathering == null || gathering.plotId() == null) return;
        byPlot.put(gathering.plotId(), gathering);
        dirty = true;
    }

    public Gathering remove(UUID plotId) {
        if (plotId == null) return null;
        Gathering removed = byPlot.remove(plotId);
        if (removed != null) dirty = true;
        return removed;
    }

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }

    public void load() {
        byPlot.clear();
        synchronized (ioLock) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("gatherings");
            if (root == null) return;
            for (String key : root.getKeys(false)) {
                Gathering gathering = read(root.getConfigurationSection(key), key);
                if (gathering != null) byPlot.put(gathering.plotId(), gathering);
            }
        }
        dirty = false;
    }

    public boolean save() {
        if (!dirty && file.exists()) return true;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Gathering gathering : byPlot.values()) {
            if (gathering == null || gathering.plotId() == null) continue;
            String path = "gatherings." + gathering.plotId();
            yaml.set(path + ".host", gathering.hostId() == null ? null : gathering.hostId().toString());
            yaml.set(path + ".host-name", gathering.hostName());
            yaml.set(path + ".plot-name", gathering.plotName());
            yaml.set(path + ".started-at", gathering.startedAt());
            yaml.set(path + ".ends-at", gathering.endsAt());
            yaml.set(path + ".grant-guest-pass", gathering.grantGuestPass());
            List<String> issued = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : gathering.issuedPasses().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                issued.add(entry.getKey() + ":" + entry.getValue());
            }
            yaml.set(path + ".issued-passes", issued);
        }
        synchronized (ioLock) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    logger.warning("Could not create gatherings data folder.");
                    return false;
                }
                Path target = file.toPath();
                Path temp = target.resolveSibling(file.getName() + ".tmp");
                Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                dirty = false;
                return true;
            } catch (IOException error) {
                logger.log(Level.WARNING, "Could not save gatherings.yml", error);
                return false;
            }
        }
    }

    private static Gathering read(ConfigurationSection section, String key) {
        if (section == null || key == null) return null;
        UUID plotId;
        try {
            plotId = UUID.fromString(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        UUID hostId = null;
        String hostRaw = section.getString("host", "");
        if (hostRaw != null && !hostRaw.isBlank()) {
            try {
                hostId = UUID.fromString(hostRaw.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Gathering gathering = new Gathering(
                plotId,
                hostId,
                section.getString("host-name", "Unknown"),
                section.getString("plot-name", "Plot"),
                section.getLong("started-at", System.currentTimeMillis()),
                section.getLong("ends-at", 0L),
                section.getBoolean("grant-guest-pass", true)
        );
        for (String raw : section.getStringList("issued-passes")) {
            if (raw == null || raw.isBlank()) continue;
            String[] parts = raw.trim().split(":");
            try {
                UUID visitorId = UUID.fromString(parts[0]);
                long issuedAt = parts.length > 1 ? Long.parseLong(parts[1]) : System.currentTimeMillis();
                gathering.recordIssued(visitorId, issuedAt);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return gathering;
    }
}
