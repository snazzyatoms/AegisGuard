package com.aegisguard.snapshots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;

/** Atomic YAML persistence for restore lifecycle state. File access must be scheduled async. */
final class RestoreOperationStore {
    private final File file;

    RestoreOperationStore(File file) {
        this.file = file;
    }

    synchronized Map<UUID, RestoreOperation> load() {
        Map<UUID, RestoreOperation> loaded = new LinkedHashMap<>();
        if (!file.isFile()) return loaded;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("operations");
        if (root == null) return loaded;
        for (String key : root.getKeys(false)) {
            try {
                String path = "operations." + key;
                UUID operationId = UUID.fromString(key);
                UUID snapshotId = uuid(yaml.getString(path + ".snapshot_id"));
                UUID plotId = uuid(yaml.getString(path + ".plot_id"));
                UUID actorId = uuid(yaml.getString(path + ".actor_id"));
                UUID rescueId = uuid(yaml.getString(path + ".rescue_snapshot_id"));
                RestoreOperation.Status status = RestoreOperation.Status.valueOf(
                        yaml.getString(path + ".status", "PAUSED_REVIEW"));
                EnumSet<RestoreScope> scopes = EnumSet.noneOf(RestoreScope.class);
                for (String raw : yaml.getStringList(path + ".scopes")) {
                    try { scopes.add(RestoreScope.valueOf(raw)); } catch (IllegalArgumentException ignored) { }
                }
                RestoreOperation operation = new RestoreOperation(
                        operationId, snapshotId, plotId, actorId, scopes, status, rescueId,
                        yaml.getLong(path + ".started_at"), yaml.getLong(path + ".updated_at"),
                        yaml.getString(path + ".detail", ""),
                        yaml.getBoolean(path + ".data_restored", false),
                        yaml.getString(path + ".data_result", "PENDING"),
                        yaml.getString(path + ".build_result", "PENDING"),
                        new LinkedHashSet<>(yaml.getStringList(path + ".completed_build_tiles")),
                        new LinkedHashSet<>(yaml.getStringList(path + ".pending_build_tiles")),
                        new LinkedHashSet<>(yaml.getStringList(path + ".failed_build_tiles")));
                loaded.put(operationId, operation);
            } catch (IllegalArgumentException ignored) {
                // Malformed entries remain on disk for manual recovery but never enter live state.
            }
        }
        return loaded;
    }

    synchronized void save(Collection<RestoreOperation> operations) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        if (operations != null) {
            for (RestoreOperation operation : operations) {
                if (operation == null) continue;
                String path = "operations." + operation.operationId();
                yaml.set(path + ".snapshot_id", string(operation.snapshotId()));
                yaml.set(path + ".plot_id", string(operation.plotId()));
                yaml.set(path + ".actor_id", string(operation.actorId()));
                yaml.set(path + ".rescue_snapshot_id", string(operation.rescueSnapshotId()));
                yaml.set(path + ".status", operation.status().name());
                List<String> scopes = new ArrayList<>();
                for (RestoreScope scope : operation.scopes()) scopes.add(scope.name());
                yaml.set(path + ".scopes", scopes);
                yaml.set(path + ".started_at", operation.startedAt());
                yaml.set(path + ".updated_at", operation.updatedAt());
                yaml.set(path + ".detail", operation.detail());
                yaml.set(path + ".data_restored", operation.dataRestored());
                yaml.set(path + ".data_result", operation.dataResult());
                yaml.set(path + ".build_result", operation.buildResult());
                yaml.set(path + ".completed_build_tiles", new ArrayList<>(operation.completedBuildTiles()));
                yaml.set(path + ".pending_build_tiles", new ArrayList<>(operation.pendingBuildTiles()));
                yaml.set(path + ".failed_build_tiles", new ArrayList<>(operation.failedBuildTiles()));
            }
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static UUID uuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private static String string(UUID value) {
        return value == null ? null : value.toString();
    }
}
