package com.aegisguard.snapshots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Durable identity and integrity manifest for one full plot-build backup. */
public record BuildBackupMetadata(
        int schemaVersion,
        UUID snapshotId,
        UUID plotId,
        String worldName,
        int x1,
        int z1,
        int x2,
        int z2,
        int minY,
        int maxY,
        long createdAt,
        String snapshotType,
        String format,
        String integrationName,
        String integrationVersion,
        List<BuildFile> files,
        UUID captureOperationId,
        UUID lastRestoreOperationId) {

    public static final int CURRENT_SCHEMA = 1;

    public BuildBackupMetadata {
        worldName = worldName == null ? "" : worldName;
        snapshotType = snapshotType == null ? "UNKNOWN" : snapshotType;
        format = format == null ? "SPONGE_SCHEMATIC" : format;
        integrationName = integrationName == null ? "Unknown" : integrationName;
        integrationVersion = integrationVersion == null ? "Unknown" : integrationVersion;
        List<BuildFile> safe = files == null ? List.of() : files.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(BuildFile::fileName))
                .toList();
        files = List.copyOf(safe);
    }

    public long totalBytes() {
        return files.stream().mapToLong(BuildFile::bytes).sum();
    }

    public String aggregateChecksum() {
        StringBuilder value = new StringBuilder();
        for (BuildFile file : files) {
            value.append(file.fileName()).append(':').append(file.sha256()).append('\n');
        }
        return PlotBuildBackup.sha256(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public BuildBackupMetadata withCaptureOperation(UUID operationId) {
        return new BuildBackupMetadata(schemaVersion, snapshotId, plotId, worldName,
                x1, z1, x2, z2, minY, maxY, createdAt, snapshotType, format,
                integrationName, integrationVersion, files, operationId, lastRestoreOperationId);
    }

    public BuildBackupMetadata withLastRestoreOperation(UUID operationId) {
        return new BuildBackupMetadata(schemaVersion, snapshotId, plotId, worldName,
                x1, z1, x2, z2, minY, maxY, createdAt, snapshotType, format,
                integrationName, integrationVersion, files, captureOperationId, operationId);
    }

    public record BuildFile(String fileName, int minX, int minZ, int maxX, int maxZ,
                            long bytes, String sha256) {
        public BuildFile {
            if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName");
            if (bytes < 0L) throw new IllegalArgumentException("bytes");
            sha256 = sha256 == null ? "" : sha256;
        }
    }

    public List<String> fileNames() {
        List<String> names = new ArrayList<>(files.size());
        for (BuildFile file : files) names.add(file.fileName());
        return List.copyOf(names);
    }
}
