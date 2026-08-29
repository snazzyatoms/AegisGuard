package com.aegisguard.snapshots;

import com.aegisguard.alliance.AllianceAccess;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.data.MarketStall;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.guestpass.GuestPassPreset;
import com.aegisguard.profile.PlotNotice;
import org.junit.jupiter.api.Test;
import org.bukkit.Material;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 0 (release safety): proves that rolling a plot back to a snapshot restores every
 * tracked field exactly, so a bad expansion/merge/admin action can be safely undone.
 *
 * Exercises {@link SnapshotManager#restorePlotState(Plot, ClaimSnapshot)} directly (package-visible,
 * static, and independent of a live plugin/data-store instance) rather than the full
 * {@link SnapshotManager#rollback(UUID)} path, since that path additionally needs a running
 * {@code AegisGuard} plugin and {@code IDataStore} that this test suite does not stand up.
 */
class SnapshotManagerTest {

    @Test
    void automaticRetentionAppliesAgeFirstThenKeepsNewestSurvivors() {
        long now = 10L * 86_400_000L;
        UUID plotId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        List<ClaimSnapshot> snapshots = List.of(
                automaticSnapshot(plotId, owner, now - 5L * 86_400_000L),
                automaticSnapshot(plotId, owner, now - 3_000L),
                automaticSnapshot(plotId, owner, now - 2_000L),
                automaticSnapshot(plotId, owner, now - 1_000L));

        List<ClaimSnapshot> removed = SnapshotManager.selectAutomaticSnapshotsToPrune(
                snapshots, 2, 2L, now);
        assertEquals(2, removed.size());
        assertTrue(removed.contains(snapshots.get(0)), "age-expired snapshot must be removed");
        assertTrue(removed.contains(snapshots.get(1)), "oldest survivor beyond the cap must be removed");
        assertFalse(removed.contains(snapshots.get(2)));
        assertFalse(removed.contains(snapshots.get(3)));
    }

    @Test
    void selectiveRestoreChangesOnlyRequestedCategories() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 10, 10);
        plot.setFlag("pvp", false);
        plot.setRole(member, "trusted");
        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "before", owner);

        plot.setFlag("pvp", true);
        plot.removeRole(member);
        SnapshotManager.restorePlotState(plot, snapshot, EnumSet.of(RestoreScope.FLAGS));

        assertFalse(plot.getFlags().get("pvp"));
        assertFalse(plot.getPlayerRoles().containsKey(member),
                "Unselected member data must remain untouched");
    }

    @Test
    void fullRestoreIncludesEconomyProgressionSocialZonesStallsAndSettings() {
        UUID owner = UUID.randomUUID();
        UUID renter = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID liker = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setMaxMembers(12);
        plot.setBorderParticle("END_ROD");
        plot.setAmbientParticle("HAPPY_VILLAGER");
        plot.setEntryEffect("HERO_OF_THE_VILLAGE");
        plot.setServerWarp(true, "Market", Material.COMPASS);
        plot.setWarpCategory("shops");
        plot.setForSale(true, 250D);
        plot.setForRent(true, 40D);
        plot.setRenter(renter, 123456789L);
        plot.setForAuction(true);
        plot.setAuctionStartPrice(500D);
        plot.setCurrentBid(650D, guest);
        plot.setAuctionEndTime(987654321L);
        plot.setLevel(8);
        plot.setXp(175);
        plot.setHorizonRank(4);
        plot.setHorizonExpansionRank(3);
        plot.setHorizonRenown(900L);
        plot.setHorizonClimate("SUNSET");
        plot.setAscensionFocus("BUILDER");
        plot.setAscensionFocusChangedAt(555L);
        plot.setLastUpkeepPayment(777L);
        plot.addLike(liker);

        Zone zone = new Zone(plot, "Inn", 1, 2, 3, 8, 9, 10);
        zone.setRentPrice(15D);
        zone.setDeposit(25D);
        zone.setHeldDeposit(20D);
        zone.setRentState(renter, 222222222L);
        zone.addGuest(guest);
        zone.setFlag("guest_build", true);
        plot.addZone(zone);

        MarketStall stall = new MarketStall(renter, "Renter", "world",
                4, 5, 6, 4, 6, 6, "Supplies", "Inn", 333L);
        stall.setListing(2, new MarketStall.StallListing(9.5D, CurrencyType.CLAIM_BLOCKS, 4));
        plot.getStalls().add(stall);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "complete", owner);

        plot.setMaxMembers(1);
        plot.setBorderParticle(null);
        plot.setServerWarp(false, null, null);
        plot.setForSale(false);
        plot.setForRent(false);
        plot.setForAuction(false);
        plot.setLevel(1);
        plot.setHorizonRenown(0L);
        plot.getLikedBy().clear();
        plot.setLikes(0);
        plot.getZones().clear();
        plot.getStalls().clear();

        SnapshotManager.restorePlotState(plot, snapshot);

        assertEquals(12, plot.getMaxMembers());
        assertEquals("END_ROD", plot.getBorderParticle());
        assertTrue(plot.isServerWarp());
        assertEquals("Market", plot.getWarpName());
        assertEquals(Material.COMPASS, plot.getWarpIcon());
        assertEquals("SHOPS", plot.getWarpCategory());
        assertTrue(plot.isForSale());
        assertEquals(250D, plot.getSalePrice());
        assertTrue(plot.isForRent());
        assertEquals(renter, plot.getCurrentRenter());
        assertTrue(plot.isForAuction());
        assertEquals(650D, plot.getCurrentBid());
        assertEquals(8, plot.getLevel());
        assertEquals(900L, plot.getHorizonRenown());
        assertTrue(plot.getLikedBy().contains(liker));
        assertEquals(1, plot.getZones().size());
        assertEquals(renter, plot.getZones().get(0).getStoredRenter());
        assertTrue(plot.getZones().get(0).hasGuest(guest));
        assertEquals(1, plot.getStalls().size());
        assertEquals(CurrencyType.CLAIM_BLOCKS, plot.getStalls().get(0).getListing(2).getCurrency());
    }

    @Test
    void selectiveEconomyRestoreDoesNotOverwriteProgressionOrSettings() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 10, 10);
        plot.setForSale(true, 100D);
        plot.setLevel(5);
        plot.setMaxMembers(9);
        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "selective", owner);

        plot.setForSale(false);
        plot.setLevel(2);
        plot.setMaxMembers(3);
        SnapshotManager.restorePlotState(plot, snapshot, EnumSet.of(RestoreScope.ECONOMY));

        assertTrue(plot.isForSale());
        assertEquals(100D, plot.getSalePrice());
        assertEquals(2, plot.getLevel());
        assertEquals(3, plot.getMaxMembers());
    }

    @Test
    void corruptVersionedStateIsRejectedBeforeAnyLiveFieldChanges() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 10, 10);
        plot.setPlotName("live");
        ClaimSnapshot corrupt = new ClaimSnapshot(
                UUID.randomUUID(), plot.getPlotId(), owner, "world", 0, 0, 10, 10,
                System.currentTimeMillis(), ClaimSnapshot.SnapshotType.MANUAL, "corrupt", owner,
                "Owner", "snapshot-name", "", "", "", "", "", "", "PRIVATE",
                false, false, 0D, null, "", Map.of(), Map.of(), List.of(),
                "", "", "", "", "", false, 0L, 0L, "FULL", null, "Unknown",
                "not-base64");

        assertThrows(IllegalArgumentException.class, () -> SnapshotManager.restorePlotState(plot, corrupt));
        assertEquals("live", plot.getPlotName());
    }

    @Test
    void fullDataScopeExpandsToEveryDataCategoryButNotBuild() {
        EnumSet<RestoreScope> scopes = RestoreScope.normalize(EnumSet.of(RestoreScope.FULL_DATA));
        assertTrue(scopes.containsAll(RestoreScope.fullData()));
        assertFalse(scopes.contains(RestoreScope.BUILD));
    }

    @Test
    void buildPreflightRefusalNeverClaimsThatDataWasRestored() {
        SnapshotManager.RestoreResult result = new SnapshotManager.RestoreResult(
                UUID.randomUUID(), UUID.randomUUID(), SnapshotManager.RestoreStatus.BUILD_UNAVAILABLE,
                PlotBuildBackup.RestoreQueueResult.NO_BACKUP, "preflight refused");
        assertFalse(result.dataRestored());
    }

    @Test
    void pruningAppliesCountOnlyToSnapshotsThatSurviveAgeRetention() {
        long now = 10_000_000L;
        ClaimSnapshot expired = snapshotAt(now - 120_000L);
        ClaimSnapshot oldestSurvivor = snapshotAt(now - 50_000L);
        ClaimSnapshot newestSurvivor = snapshotAt(now - 10_000L);

        List<ClaimSnapshot> selected = SnapshotManager.selectSnapshotsToPrune(
                List.of(expired, oldestSurvivor, newestSurvivor), 1, 1, now);

        assertEquals(2, selected.size(), "Age and count limits must not double-count expired snapshots");
        assertTrue(selected.contains(expired));
        assertTrue(selected.contains(oldestSurvivor));
        assertFalse(selected.contains(newestSurvivor));
    }

    @Test
    void restoringASnapshotUndoesEveryChangeMadeAfterItWasTaken() {
        UUID owner = UUID.randomUUID();
        UUID trustedMember = UUID.randomUUID();
        UUID laterBannedPlayer = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();

        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setPlotName("Original Homestead");
        plot.setDescription("A quiet farm.");
        plot.setFlag("pvp", false);
        plot.setRole(trustedMember, "trusted");
        plot.setTreasuryBalance(100.0D);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Before risky change", owner);

        // Simulate destructive changes made after the snapshot was captured.
        plot.setPlotName("Corrupted");
        plot.setDescription("");
        plot.setFlag("pvp", true);
        plot.getPlayerRoles().clear();
        plot.setRole(intruder, "co-owner");
        plot.addBan(laterBannedPlayer);
        plot.setTreasuryBalance(0.0D);

        SnapshotManager.restorePlotState(plot, snapshot, RestoreScope.fullData(), true);

        assertEquals(owner, plot.getOwner());
        assertEquals("Original Homestead", plot.getPlotName());
        assertEquals("A quiet farm.", plot.getDescription());
        assertEquals(Boolean.FALSE, plot.getFlags().get("pvp"));
        assertEquals("trusted", plot.getPlayerRoles().get(trustedMember));
        assertFalse(plot.getPlayerRoles().containsKey(intruder), "Roles granted after the snapshot must not survive rollback");
        assertFalse(plot.isBanned(laterBannedPlayer), "Bans added after the snapshot must not survive rollback");
        assertEquals(100.0D, plot.getTreasuryBalance());
    }

    @Test
    void restoringASnapshotReinstatesABanThatWasLiftedAfterwards() {
        UUID owner = UUID.randomUUID();
        UUID bannedAtSnapshotTime = UUID.randomUUID();

        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.addBan(bannedAtSnapshotTime);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.PRE_MERGE, "Before merge", owner);

        // The ban is lifted after the snapshot was taken.
        plot.removeBan(bannedAtSnapshotTime);
        assertFalse(plot.isBanned(bannedAtSnapshotTime));

        SnapshotManager.restorePlotState(plot, snapshot);

        assertTrue(plot.isBanned(bannedAtSnapshotTime), "Rollback must reinstate bans that existed at snapshot time");
    }

    @Test
    void restoringASnapshotReinstatesGuestPassLockdownAllianceAndNoticeboard() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID allianceId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.addGuestPass(GuestPass.issue(guest, "Guest", GuestPassPreset.VISITOR, owner, "OwnerName", 3_600_000L));
        plot.restoreLockdown(true, owner, "OwnerName", now, now + 3_600_000L, "SOFT");
        plot.postNotice(PlotNotice.post(owner, "OwnerName", "Keep off the wheat."), 8);
        plot.setAllianceId(allianceId);
        plot.getAllianceAccess().setEnter(true);
        plot.getAllianceAccess().setInteract(true);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Before wipe", owner);

        plot.revokeGuestPass(guest);
        plot.setLockdown(false, owner, "OwnerName");
        plot.clearNoticeboard();
        plot.deserializeAllianceAccess("");

        SnapshotManager.restorePlotState(plot, snapshot);

        assertNotNull(plot.getGuestPass(guest), "Guest passes must survive rollback");
        assertTrue(plot.isLockdownActive());
        assertEquals("SOFT", plot.getLockdownMode());
        assertEquals(1, plot.getNoticeboard().size());
        assertEquals("Keep off the wheat.", plot.getNoticeboard().get(0).getText());
        assertEquals(allianceId, plot.getAllianceId());
        AllianceAccess access = plot.getAllianceAccess();
        assertTrue(access.isEnter());
        assertTrue(access.isInteract());
    }

    @Test
    void takingASnapshotDoesNotAutoLiftAnExpiredTimedLockdown() {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.restoreLockdown(true, owner, "OwnerName", now - 60_000L, now - 1L, "FULL");
        assertTrue(plot.isLockdownFlagSet());

        new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Safety copy", owner);

        assertTrue(plot.isLockdownFlagSet(), "Capturing a snapshot must not clear live lockdown as a side effect");
    }

    @Test
    void restoringASnapshotClearsRoleNicknamesAddedAfterwards() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setRole(member, "trusted");

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Before nick", owner);

        plot.setRoleNickname(member, "BuilderBob");
        assertEquals("BuilderBob", plot.getRoleNicknames().get(member));

        SnapshotManager.restorePlotState(plot, snapshot, RestoreScope.fullData(), true);

        assertTrue(plot.getRoleNicknames().isEmpty(), "Nicknames added after the snapshot must not survive rollback");
        assertEquals("trusted", plot.getPlayerRoles().get(member));
    }

    @Test
    void restoringASnapshotReinstatesRoleNicknamesAndRoleFlags() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setRole(member, "trusted");
        plot.setRoleNickname(member, "BuilderBob");
        plot.setRoleFlagState("trusted", "build", com.aegisguard.flags.TriState.DENY);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Before wipe", owner);

        plot.clearRoleNickname(member);
        plot.setRoleFlagState("trusted", "build", com.aegisguard.flags.TriState.ALLOW);

        SnapshotManager.restorePlotState(plot, snapshot);

        assertEquals("BuilderBob", plot.getRoleNickname(member));
        assertEquals(com.aegisguard.flags.TriState.DENY, plot.getRoleFlagState("trusted", "build"));
    }

    @Test
    void snapshotDoesNotRefundActivePlaytimeAlreadyConsumedThisSession() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        GuestPass pass = GuestPass.issue(guest, "Guest", GuestPassPreset.VISITOR, owner, "OwnerName",
                60_000L, com.aegisguard.guestpass.GuestPassMode.ACTIVE_PLAYTIME);
        long now = System.currentTimeMillis();
        pass.resumeSession(now - 5_000L);
        plot.addGuestPass(pass);
        assertTrue(pass.getSessionStartedAt() > 0L);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Mid-session", owner);
        assertTrue(pass.getSessionStartedAt() > 0L, "Capturing a snapshot must not pause the live guest pass");

        SnapshotManager.restorePlotState(plot, snapshot);
        GuestPass restored = plot.getGuestPass(guest);
        assertNotNull(restored);
        assertEquals(0L, restored.getSessionStartedAt());
        assertTrue(restored.getStoredRemainingMillis() <= 55_000L,
                "Rollback must keep playtime already spent in the live session");
        assertTrue(restored.getStoredRemainingMillis() >= 50_000L);
    }

    private static ClaimSnapshot snapshotAt(long timestamp) {
        UUID owner = UUID.randomUUID();
        return new ClaimSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), owner, "world",
                0, 0, 10, 10, timestamp,
                ClaimSnapshot.SnapshotType.SCHEDULED, "retention test", null,
                "Owner", "Plot", "", "", "", "", "",
                "", "PRIVATE", false, false, 0.0D, null, "",
                Map.of(), Map.of(), List.of());
    }

    private static ClaimSnapshot automaticSnapshot(UUID plotId, UUID owner, long timestamp) {
        return new ClaimSnapshot(
                UUID.randomUUID(), plotId, owner, "world",
                0, 0, 10, 10, timestamp,
                ClaimSnapshot.SnapshotType.AUTOMATIC_PLAYER, "automatic", null,
                "Owner", "Plot", "", "", "", "", "",
                "", "PRIVATE", false, false, 0.0D, null, "",
                Map.of(), Map.of(), List.of());
    }

    @Test
    void defaultRestoreMergesRolesInsteadOfClobberingCurrentTrust() {
        UUID owner = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        UUID laterTrusted = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setRole(original, "trusted");
        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "base", owner);

        plot.setRole(laterTrusted, "builder");
        SnapshotManager.restorePlotState(plot, snapshot);

        assertEquals("trusted", plot.getPlayerRoles().get(original));
        assertEquals("builder", plot.getPlayerRoles().get(laterTrusted),
                "Default restore must keep members granted after the snapshot");
    }

    @Test
    void lockedMembersSurviveOverwriteRestoreAndRefuseSetRole() {
        UUID owner = UUID.randomUUID();
        UUID locked = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setRole(locked, "trusted");
        plot.lockMember(locked);
        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "before", owner);

        assertFalse(plot.setRole(locked, "builder", false));
        assertEquals("trusted", plot.getRole(locked));
        SnapshotManager.restorePlotState(plot, snapshot, RestoreScope.fullData(), true);

        assertEquals("trusted", plot.getPlayerRoles().get(locked));
        assertTrue(plot.isMemberLocked(locked));
    }

    @Test
    void malformedRoleBlobKeepsLastGoodValue() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setRole(member, "trusted");
        plot.deserializeRoles("not-a-uuid-blob");
        assertEquals("trusted", plot.getPlayerRoles().get(member));
        plot.deserializeRoleFlags("totally|broken");
        assertTrue(plot.getRoleFlagStates().isEmpty() || plot.getRoleFlagState("trusted", "build") == null
                || plot.getRoleFlagStates().isEmpty());
        plot.setRoleFlagState("trusted", "build", com.aegisguard.flags.TriState.DENY);
        plot.deserializeRoleFlags("%%%not-flags%%%");
        assertEquals(com.aegisguard.flags.TriState.DENY, plot.getRoleFlagState("trusted", "build"));
    }
}
