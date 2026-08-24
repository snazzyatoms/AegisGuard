package com.aegisguard.snapshots;

import com.aegisguard.data.Plot;
import java.util.*;

/**
 * Immutable snapshot of a Plot's state at a specific moment.
 * Used for rollback after risky operations (merge, expansion) and staff restore.
 */
public class ClaimSnapshot {
    
    public enum SnapshotType {
        PRE_EXPANSION,
        PRE_MERGE,
        PRE_LOCKDOWN,
        PRE_ALLIANCE_ACCESS,
        PRE_STAFF_DESTINATION,
        PRE_DESTRUCTIVE,
        PRE_RESTORE_RESCUE,
        MANUAL,
        AUTOMATIC_PLAYER,
        AUTOMATIC_SERVER_ZONE,
        SCHEDULED
    }
    
    private final UUID snapshotId;
    private final UUID plotId;
    private final UUID owner;
    private final String worldName;
    
    private final int x1, z1, x2, z2;
    
    private final long timestamp;
    private final SnapshotType type;
    private final String reason;
    private final UUID triggeredBy;
    private final String ownerName;
    private final String plotName;
    private final String description;
    private final String welcomeMessage;
    private final String farewellMessage;
    private final String entryTitle;
    private final String entrySubtitle;
    private final String customBiome;
    private final String plotStatus;
    private final boolean serverWarp;
    private final boolean groupPlot;
    private final double treasuryBalance;
    private final UUID groupId;
    private final String groupName;

    private final Map<String, Boolean> flags;
    private final Map<UUID, String> members;
    private final List<UUID> bannedPlayers;

    private final String guestPassesBlob;
    private final String noticeboardBlob;
    private final String allianceAccessBlob;
    private final String roleNicknamesBlob;
    private final String roleFlagsBlob;
    private final boolean lockdownActive;
    private final long lockdownActivatedAt;
    private final long lockdownExpiresAt;
    private final String lockdownMode;
    private final UUID lockdownActivatedBy;
    private final String lockdownActivatedByName;
    private final String extendedStateBlob;
    private final String territoryRentalStateBlob;
    
    public ClaimSnapshot(Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        this(plot, type, reason, triggeredBy, "");
    }

    ClaimSnapshot(Plot plot, SnapshotType type, String reason, UUID triggeredBy,
                  String territoryRentalStateBlob) {
        this.snapshotId = UUID.randomUUID();
        this.plotId = plot.getPlotId();
        this.owner = plot.getOwner();
        this.worldName = plot.getWorld();
        
        this.x1 = plot.getX1();
        this.z1 = plot.getZ1();
        this.x2 = plot.getX2();
        this.z2 = plot.getZ2();
        
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.reason = reason == null ? "" : reason;
        this.triggeredBy = triggeredBy;
        this.ownerName = plot.getOwnerName();
        this.plotName = plot.getPlotName();
        this.description = plot.getDescription();
        this.welcomeMessage = plot.getWelcomeMessage();
        this.farewellMessage = plot.getFarewellMessage();
        this.entryTitle = plot.getEntryTitle();
        this.entrySubtitle = plot.getEntrySubtitle();
        this.customBiome = plot.getCustomBiome();
        this.plotStatus = plot.getPlotStatus();
        this.serverWarp = plot.isServerWarp();
        this.groupPlot = plot.isGroupPlot();
        this.treasuryBalance = plot.getTreasuryBalance();
        this.groupId = plot.getGroupId();
        this.groupName = plot.getGroupName();

        this.flags = new HashMap<>(plot.getFlags());
        this.members = new HashMap<>(plot.getPlayerRoles());
        this.bannedPlayers = new ArrayList<>(plot.getBannedPlayers());

        this.guestPassesBlob = nullToEmpty(plot.serializeGuestPassesForSnapshot());
        this.noticeboardBlob = nullToEmpty(plot.serializeNoticeboard());
        this.allianceAccessBlob = nullToEmpty(plot.serializeAllianceAccess());
        this.roleNicknamesBlob = nullToEmpty(plot.serializeRoleNicknames());
        this.roleFlagsBlob = nullToEmpty(plot.serializeRoleFlags());
        this.lockdownActive = plot.isLockdownFlagSet();
        this.lockdownActivatedAt = plot.getLockdownActivatedAt();
        this.lockdownExpiresAt = plot.getLockdownExpiresAt();
        this.lockdownMode = plot.getLockdownMode();
        this.lockdownActivatedBy = plot.getLockdownActivatedBy();
        this.lockdownActivatedByName = plot.getLockdownActivatedByName();
        this.extendedStateBlob = PlotSnapshotState.capture(plot);
        this.territoryRentalStateBlob = nullToEmpty(territoryRentalStateBlob);
    }
    
    public ClaimSnapshot(UUID snapshotId, UUID plotId, UUID owner, String worldName,
                         int x1, int z1, int x2, int z2, long timestamp,
                         SnapshotType type, String reason, UUID triggeredBy,
                         String ownerName, String plotName, String description,
                         String welcomeMessage, String farewellMessage,
                         String entryTitle, String entrySubtitle,
                         String customBiome, String plotStatus, boolean serverWarp,
                         boolean groupPlot, double treasuryBalance, UUID groupId, String groupName,
                         Map<String, Boolean> flags, Map<UUID, String> members, List<UUID> bannedPlayers) {
        this(snapshotId, plotId, owner, worldName, x1, z1, x2, z2, timestamp, type, reason, triggeredBy,
                ownerName, plotName, description, welcomeMessage, farewellMessage, entryTitle, entrySubtitle,
                customBiome, plotStatus, serverWarp, groupPlot, treasuryBalance, groupId, groupName,
                flags, members, bannedPlayers,
                "", "", "", "", "", false, 0L, 0L, "FULL", null, "Unknown");
    }

    public ClaimSnapshot(UUID snapshotId, UUID plotId, UUID owner, String worldName,
                         int x1, int z1, int x2, int z2, long timestamp,
                         SnapshotType type, String reason, UUID triggeredBy,
                         String ownerName, String plotName, String description,
                         String welcomeMessage, String farewellMessage,
                         String entryTitle, String entrySubtitle,
                         String customBiome, String plotStatus, boolean serverWarp,
                         boolean groupPlot, double treasuryBalance, UUID groupId, String groupName,
                         Map<String, Boolean> flags, Map<UUID, String> members, List<UUID> bannedPlayers,
                         String guestPassesBlob, String noticeboardBlob, String allianceAccessBlob,
                         String roleNicknamesBlob, String roleFlagsBlob,
                         boolean lockdownActive, long lockdownActivatedAt, long lockdownExpiresAt,
                         String lockdownMode, UUID lockdownActivatedBy, String lockdownActivatedByName) {
        this(snapshotId, plotId, owner, worldName, x1, z1, x2, z2, timestamp, type, reason, triggeredBy,
                ownerName, plotName, description, welcomeMessage, farewellMessage, entryTitle, entrySubtitle,
                customBiome, plotStatus, serverWarp, groupPlot, treasuryBalance, groupId, groupName,
                flags, members, bannedPlayers, guestPassesBlob, noticeboardBlob, allianceAccessBlob,
                roleNicknamesBlob, roleFlagsBlob, lockdownActive, lockdownActivatedAt, lockdownExpiresAt,
                lockdownMode, lockdownActivatedBy, lockdownActivatedByName, "", "");
    }

    public ClaimSnapshot(UUID snapshotId, UUID plotId, UUID owner, String worldName,
                         int x1, int z1, int x2, int z2, long timestamp,
                         SnapshotType type, String reason, UUID triggeredBy,
                         String ownerName, String plotName, String description,
                         String welcomeMessage, String farewellMessage,
                         String entryTitle, String entrySubtitle,
                         String customBiome, String plotStatus, boolean serverWarp,
                         boolean groupPlot, double treasuryBalance, UUID groupId, String groupName,
                         Map<String, Boolean> flags, Map<UUID, String> members, List<UUID> bannedPlayers,
                         String guestPassesBlob, String noticeboardBlob, String allianceAccessBlob,
                         String roleNicknamesBlob, String roleFlagsBlob,
                         boolean lockdownActive, long lockdownActivatedAt, long lockdownExpiresAt,
                         String lockdownMode, UUID lockdownActivatedBy, String lockdownActivatedByName,
                         String extendedStateBlob) {
        this(snapshotId, plotId, owner, worldName, x1, z1, x2, z2, timestamp, type, reason, triggeredBy,
                ownerName, plotName, description, welcomeMessage, farewellMessage, entryTitle, entrySubtitle,
                customBiome, plotStatus, serverWarp, groupPlot, treasuryBalance, groupId, groupName,
                flags, members, bannedPlayers, guestPassesBlob, noticeboardBlob, allianceAccessBlob,
                roleNicknamesBlob, roleFlagsBlob, lockdownActive, lockdownActivatedAt, lockdownExpiresAt,
                lockdownMode, lockdownActivatedBy, lockdownActivatedByName, extendedStateBlob, "");
    }

    public ClaimSnapshot(UUID snapshotId, UUID plotId, UUID owner, String worldName,
                         int x1, int z1, int x2, int z2, long timestamp,
                         SnapshotType type, String reason, UUID triggeredBy,
                         String ownerName, String plotName, String description,
                         String welcomeMessage, String farewellMessage,
                         String entryTitle, String entrySubtitle,
                         String customBiome, String plotStatus, boolean serverWarp,
                         boolean groupPlot, double treasuryBalance, UUID groupId, String groupName,
                         Map<String, Boolean> flags, Map<UUID, String> members, List<UUID> bannedPlayers,
                         String guestPassesBlob, String noticeboardBlob, String allianceAccessBlob,
                         String roleNicknamesBlob, String roleFlagsBlob,
                         boolean lockdownActive, long lockdownActivatedAt, long lockdownExpiresAt,
                         String lockdownMode, UUID lockdownActivatedBy, String lockdownActivatedByName,
                         String extendedStateBlob, String territoryRentalStateBlob) {
        this.snapshotId = snapshotId;
        this.plotId = plotId;
        this.owner = owner;
        this.worldName = worldName;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.timestamp = timestamp;
        this.type = type;
        this.reason = reason;
        this.triggeredBy = triggeredBy;
        this.ownerName = ownerName;
        this.plotName = plotName;
        this.description = description;
        this.welcomeMessage = welcomeMessage;
        this.farewellMessage = farewellMessage;
        this.entryTitle = entryTitle;
        this.entrySubtitle = entrySubtitle;
        this.customBiome = customBiome;
        this.plotStatus = plotStatus;
        this.serverWarp = serverWarp;
        this.groupPlot = groupPlot;
        this.treasuryBalance = treasuryBalance;
        this.groupId = groupId;
        this.groupName = groupName;
        this.flags = flags == null ? new HashMap<>() : new HashMap<>(flags);
        this.members = members == null ? new HashMap<>() : new HashMap<>(members);
        this.bannedPlayers = bannedPlayers == null ? new ArrayList<>() : new ArrayList<>(bannedPlayers);
        this.guestPassesBlob = nullToEmpty(guestPassesBlob);
        this.noticeboardBlob = nullToEmpty(noticeboardBlob);
        this.allianceAccessBlob = nullToEmpty(allianceAccessBlob);
        this.roleNicknamesBlob = nullToEmpty(roleNicknamesBlob);
        this.roleFlagsBlob = nullToEmpty(roleFlagsBlob);
        this.lockdownActive = lockdownActive;
        this.lockdownActivatedAt = lockdownActivatedAt;
        this.lockdownExpiresAt = lockdownExpiresAt;
        this.lockdownMode = lockdownMode == null || lockdownMode.isBlank() ? "FULL" : lockdownMode;
        this.lockdownActivatedBy = lockdownActivatedBy;
        this.lockdownActivatedByName = lockdownActivatedByName == null || lockdownActivatedByName.isBlank()
                ? "Unknown" : lockdownActivatedByName;
        this.extendedStateBlob = nullToEmpty(extendedStateBlob);
        this.territoryRentalStateBlob = nullToEmpty(territoryRentalStateBlob);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
    
    public UUID getSnapshotId() { return snapshotId; }
    public UUID getPlotId() { return plotId; }
    public UUID getOwner() { return owner; }
    public String getWorldName() { return worldName; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    public long getTimestamp() { return timestamp; }
    public SnapshotType getType() { return type; }
    public String getReason() { return reason; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public String getOwnerName() { return ownerName; }
    public String getPlotName() { return plotName; }
    public String getDescription() { return description; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public String getFarewellMessage() { return farewellMessage; }
    public String getEntryTitle() { return entryTitle; }
    public String getEntrySubtitle() { return entrySubtitle; }
    public String getCustomBiome() { return customBiome; }
    public String getPlotStatus() { return plotStatus; }
    public boolean isServerWarp() { return serverWarp; }
    public boolean isGroupPlot() { return groupPlot; }
    public double getTreasuryBalance() { return treasuryBalance; }
    public UUID getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public Map<String, Boolean> getFlags() { return new HashMap<>(flags); }
    public Map<UUID, String> getMembers() { return new HashMap<>(members); }
    public List<UUID> getBannedPlayers() { return new ArrayList<>(bannedPlayers); }
    public String getGuestPassesBlob() { return guestPassesBlob; }
    public String getNoticeboardBlob() { return noticeboardBlob; }
    public String getAllianceAccessBlob() { return allianceAccessBlob; }
    public String getRoleNicknamesBlob() { return roleNicknamesBlob; }
    public String getRoleFlagsBlob() { return roleFlagsBlob; }
    public boolean isLockdownActive() { return lockdownActive; }
    public long getLockdownActivatedAt() { return lockdownActivatedAt; }
    public long getLockdownExpiresAt() { return lockdownExpiresAt; }
    public String getLockdownMode() { return lockdownMode; }
    public UUID getLockdownActivatedBy() { return lockdownActivatedBy; }
    public String getLockdownActivatedByName() { return lockdownActivatedByName; }
    public String getExtendedStateBlob() { return extendedStateBlob; }
    public String getTerritoryRentalStateBlob() { return territoryRentalStateBlob; }
    
    public int getRadius() {
        return Math.max(0, (x2 - x1) / 2);
    }
    
    public long getAgeMillis() {
        return System.currentTimeMillis() - timestamp;
    }
    
    @Override
    public String toString() {
        return "ClaimSnapshot{" +
                "id=" + snapshotId +
                ", plotId=" + plotId +
                ", owner=" + owner +
                ", type=" + type +
                ", timestamp=" + timestamp +
                ", bounds=[" + x1 + "," + z1 + " -> " + x2 + "," + z2 + "]" +
                '}';
    }
}
