package com.aegisguard.snapshots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Atomic persistence for automatic player-plot backup progress and fingerprints. */
final class AutomaticBackupStateStore {
    private final File file;

    AutomaticBackupStateStore(File file) {
        this.file = file;
    }

    synchronized Map<UUID, AutomaticBackupState> load() {
        Map<UUID, AutomaticBackupState> loaded = new LinkedHashMap<>();
        if (!file.isFile()) return loaded;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("plots");
        if (root == null) return loaded;
        for (String key : root.getKeys(false)) {
            try {
                UUID plotId = UUID.fromString(key);
                String path = "plots." + key;
                loaded.put(plotId, new AutomaticBackupState(plotId,
                        yaml.getString(path + ".fingerprint", ""),
                        yaml.getLong(path + ".last_backup_at", 0L),
                        yaml.getLong(path + ".last_checked_at", 0L),
                        yaml.getString(path + ".outcome", "")));
            } catch (IllegalArgumentException ignored) {
                // Leave malformed entries on disk for staff inspection, but do not schedule them.
            }
        }
        return loaded;
    }

    synchronized void save(Collection<AutomaticBackupState> states) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        if (states != null) {
            for (AutomaticBackupState state : states) {
                if (state == null || state.plotId() == null) continue;
                String path = "plots." + state.plotId();
                yaml.set(path + ".fingerprint", state.fingerprint());
                yaml.set(path + ".last_backup_at", state.lastBackupAt());
                yaml.set(path + ".last_checked_at", state.lastCheckedAt());
                yaml.set(path + ".outcome", state.outcome());
            }
        }
        File temp = new File(parent, file.getName() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
