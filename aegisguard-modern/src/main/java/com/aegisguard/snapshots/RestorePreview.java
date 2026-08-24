package com.aegisguard.snapshots;

import java.util.EnumSet;
import java.util.UUID;

/** Immutable preflight summary displayed before staff confirms a restore. */
public record RestorePreview(
        UUID snapshotId,
        UUID plotId,
        long snapshotTimestamp,
        ClaimSnapshot.SnapshotType snapshotType,
        String reason,
        UUID triggeredBy,
        UUID currentOwner,
        String currentOwnerName,
        UUID snapshotOwner,
        String snapshotOwnerName,
        String worldName,
        int x1,
        int z1,
        int x2,
        int z2,
        EnumSet<RestoreScope> scopes,
        boolean worldLoaded,
        boolean buildRequested,
        boolean buildBackupPresent,
        long buildBackupBytes,
        int buildBackupFiles,
        PlotBuildBackup.IntegrityStatus buildIntegrity,
        boolean buildCompatible,
        boolean buildDestinationSafe,
        String buildChecksum,
        String buildFormat,
        String buildIntegration,
        String buildIntegrationVersion,
        int estimatedChunks,
        String preflightMessage
) {
    public boolean ready() {
        return worldLoaded && (!buildRequested || (buildBackupPresent
                && buildIntegrity == PlotBuildBackup.IntegrityStatus.VALID
                && buildCompatible && buildDestinationSafe));
    }
}
