package com.aegisguard.snapshots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Atomic, per-snapshot build-backup manifest persistence. File access must run asynchronously. */
final class BuildBackupMetadataStore {
    private final File folder;

    BuildBackupMetadataStore(File folder) {
        this.folder = folder;
    }

    File file(UUID snapshotId) {
        return new File(folder, snapshotId + ".meta.yml");
    }

    synchronized BuildBackupMetadata load(UUID snapshotId) {
        if (snapshotId == null) return null;
        File file = file(snapshotId);
        if (!file.isFile()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            List<BuildBackupMetadata.BuildFile> files = new ArrayList<>();
            ConfigurationSection section = yaml.getConfigurationSection("files");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String path = "files." + key;
                    String fileName = yaml.getString(path + ".name", "");
                    files.add(new BuildBackupMetadata.BuildFile(fileName,
                            yaml.getInt(path + ".min_x"), yaml.getInt(path + ".min_z"),
                            yaml.getInt(path + ".max_x"), yaml.getInt(path + ".max_z"),
                            yaml.getLong(path + ".bytes"), yaml.getString(path + ".sha256", "")));
                }
            }
            BuildBackupMetadata metadata = new BuildBackupMetadata(
                    yaml.getInt("schema_version", 0), uuid(yaml.getString("snapshot_id")),
                    uuid(yaml.getString("plot_id")), yaml.getString("world", ""),
                    yaml.getInt("bounds.x1"), yaml.getInt("bounds.z1"),
                    yaml.getInt("bounds.x2"), yaml.getInt("bounds.z2"),
                    yaml.getInt("bounds.min_y"), yaml.getInt("bounds.max_y"),
                    yaml.getLong("created_at"), yaml.getString("snapshot_type", "UNKNOWN"),
                    yaml.getString("format", "SPONGE_SCHEMATIC"),
                    yaml.getString("integration.name", "Unknown"),
                    yaml.getString("integration.version", "Unknown"), files,
                    uuid(yaml.getString("capture_operation_id")),
                    uuid(yaml.getString("last_restore_operation_id")));
            String declaredChecksum = yaml.getString("aggregate_sha256", "");
            long declaredBytes = yaml.getLong("total_bytes", -1L);
            if (declaredChecksum.isBlank()
                    || !declaredChecksum.equalsIgnoreCase(metadata.aggregateChecksum())
                    || declaredBytes != metadata.totalBytes()) return null;
            return metadata;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    synchronized void save(BuildBackupMetadata metadata) throws IOException {
        if (metadata == null || metadata.snapshotId() == null) throw new IllegalArgumentException("metadata");
        Files.createDirectories(folder.toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema_version", metadata.schemaVersion());
        yaml.set("snapshot_id", string(metadata.snapshotId()));
        yaml.set("plot_id", string(metadata.plotId()));
        yaml.set("world", metadata.worldName());
        yaml.set("bounds.x1", metadata.x1()); yaml.set("bounds.z1", metadata.z1());
        yaml.set("bounds.x2", metadata.x2()); yaml.set("bounds.z2", metadata.z2());
        yaml.set("bounds.min_y", metadata.minY()); yaml.set("bounds.max_y", metadata.maxY());
        yaml.set("created_at", metadata.createdAt());
        yaml.set("snapshot_type", metadata.snapshotType());
        yaml.set("format", metadata.format());
        yaml.set("integration.name", metadata.integrationName());
        yaml.set("integration.version", metadata.integrationVersion());
        yaml.set("aggregate_sha256", metadata.aggregateChecksum());
        yaml.set("total_bytes", metadata.totalBytes());
        yaml.set("capture_operation_id", string(metadata.captureOperationId()));
        yaml.set("last_restore_operation_id", string(metadata.lastRestoreOperationId()));
        int index = 0;
        for (BuildBackupMetadata.BuildFile entry : metadata.files()) {
            String path = "files." + index++;
            yaml.set(path + ".name", entry.fileName());
            yaml.set(path + ".min_x", entry.minX()); yaml.set(path + ".min_z", entry.minZ());
            yaml.set(path + ".max_x", entry.maxX()); yaml.set(path + ".max_z", entry.maxZ());
            yaml.set(path + ".bytes", entry.bytes()); yaml.set(path + ".sha256", entry.sha256());
        }
        File target = file(metadata.snapshotId());
        File temp = new File(folder, target.getName() + ".tmp");
        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    synchronized void delete(UUID snapshotId) throws IOException {
        if (snapshotId != null) Files.deleteIfExists(file(snapshotId).toPath());
    }

    synchronized List<UUID> listSnapshotIds() {
        if (!folder.isDirectory()) return List.of();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".meta.yml"));
        if (files == null) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            try { ids.add(UUID.fromString(name.substring(0, name.length() - ".meta.yml".length()))); }
            catch (RuntimeException ignored) { }
        }
        return List.copyOf(ids);
    }

    private static UUID uuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private static String string(UUID value) {
        return value == null ? null : value.toString();
    }
}
