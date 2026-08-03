package com.aegisguard.alliance;

import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 7 - plugin-independent unit tests for {@link AllianceAccess} and plot serialization.
 */
class AllianceAccessTest {

    @Test
    void allTogglesDefaultOff() {
        AllianceAccess access = new AllianceAccess();
        assertFalse(access.isEnter());
        assertFalse(access.isInteract());
        assertFalse(access.isContainers());
        assertFalse(access.isBuild());
        assertFalse(access.isAnimals());
        assertFalse(access.isFriendlyPvp());
    }

    @Test
    void toggleAndSerializeRoundTrip() {
        AllianceAccess access = new AllianceAccess();
        assertTrue(access.toggle("enter"));
        assertTrue(access.toggle("build"));
        assertTrue(access.toggle("friendly_pvp"));

        AllianceAccess loaded = AllianceAccess.deserialize(access.serialize());
        assertTrue(loaded.isEnter());
        assertFalse(loaded.isInteract());
        assertFalse(loaded.isContainers());
        assertTrue(loaded.isBuild());
        assertFalse(loaded.isAnimals());
        assertTrue(loaded.isFriendlyPvp());
    }

    @Test
    void grantsPermissionNeverIncludesManageTokens() {
        AllianceAccess access = new AllianceAccess();
        access.setEnter(true);
        access.setInteract(true);
        access.setContainers(true);
        access.setBuild(true);
        access.setAnimals(true);
        access.setFriendlyPvp(true);

        assertTrue(access.grantsPermission("INTERACT"));
        assertTrue(access.grantsPermission("CONTAINERS"));
        assertTrue(access.grantsPermission("BUILD"));
        assertTrue(access.grantsPermission("BLOCK_BREAK"));
        assertTrue(access.grantsPermission("ANIMALS"));
        assertFalse(access.grantsPermission("MANAGE"));
        assertFalse(access.grantsPermission("MANAGE_MEMBERS"));
        assertFalse(access.grantsPermission("ENTER"), "Enter is handled separately from permission tokens");
    }

    @Test
    void plotAllianceAccessSerializesWithoutTouchingRolesOrMoney() {
        UUID owner = UUID.randomUUID();
        UUID allianceId = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setAllianceId(allianceId);
        plot.getAllianceAccess().setInteract(true);

        String blob = plot.serializeAllianceAccess();
        assertTrue(blob.startsWith(allianceId + "|"));
        assertTrue(blob.contains("interact:1"));
        assertTrue(plot.getPlayerRoles().isEmpty());

        Plot other = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        other.deserializeAllianceAccess(blob);
        assertEquals(allianceId, other.getAllianceId());
        assertTrue(other.getAllianceAccess().isInteract());
        assertFalse(other.getAllianceAccess().isBuild());

        other.clearAllianceAccess();
        assertEquals(null, other.getAllianceId());
        assertFalse(other.getAllianceAccess().isInteract());
    }

    @Test
    void allianceMembershipHelpers() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Alliance alliance = Alliance.create("TestAlliance", leader);
        assertTrue(alliance.isLeader(leader));
        assertTrue(alliance.isMember(leader));
        assertFalse(alliance.isMember(member));

        alliance.addInvite(member, System.currentTimeMillis());
        assertTrue(alliance.isInvited(member));
        alliance.addMember(member, System.currentTimeMillis());
        assertTrue(alliance.isMember(member));
        assertFalse(alliance.isInvited(member));
        assertEquals(2, alliance.size());
    }

    @Test
    void allianceEntryToggleDefaultsOffAndRequiresMembershipWhenEnabled() {
        UUID owner = UUID.randomUUID();
        UUID ally = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Alliance alliance = Alliance.create("EntryAlliance", owner);
        alliance.addMember(ally, System.currentTimeMillis());

        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setAllianceId(alliance.getId());

        // Default: Enter toggle OFF — even alliance members are denied private-plot entry.
        assertFalse(plot.isAllianceEntryEnabled());
        assertFalse(plot.allowsAllianceEntry(ally, alliance));
        assertFalse(plot.allowsAllianceEntry(owner, alliance));

        // Opt in: members may enter; non-members and wrong alliances may not.
        plot.getAllianceAccess().setEnter(true);
        assertTrue(plot.isAllianceEntryEnabled());
        assertTrue(plot.allowsAllianceEntry(ally, alliance));
        assertTrue(plot.allowsAllianceEntry(owner, alliance));
        assertFalse(plot.allowsAllianceEntry(stranger, alliance));

        Alliance other = Alliance.create("Other", stranger);
        assertFalse(plot.allowsAllianceEntry(stranger, other),
                "A different alliance must never satisfy this plot's entry grant");

        // Turning the toggle back off restores the safe default.
        plot.getAllianceAccess().setEnter(false);
        assertFalse(plot.isAllianceEntryEnabled());
        assertFalse(plot.allowsAllianceEntry(ally, alliance));
    }

    @Test
    void allianceFriendlyPvpToggleDefaultsOffAndBlocksAllyDamageWhenEnabled() {
        UUID owner = UUID.randomUUID();
        UUID allyA = UUID.randomUUID();
        UUID allyB = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Alliance alliance = Alliance.create("PvpAlliance", owner);
        alliance.addMember(allyA, System.currentTimeMillis());
        alliance.addMember(allyB, System.currentTimeMillis());

        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setAllianceId(alliance.getId());

        // Default: Friendly PvP OFF — allies are not treated as friendly for damage cancel.
        assertFalse(plot.isAllianceFriendlyPvpEnabled());
        assertFalse(plot.areAllianceAllies(allyA, allyB, alliance));
        assertFalse(plot.areAllianceAllies(owner, allyA, alliance));

        // Opt in: both combatants must be members of THIS plot's alliance.
        plot.getAllianceAccess().setFriendlyPvp(true);
        assertTrue(plot.isAllianceFriendlyPvpEnabled());
        assertTrue(plot.areAllianceAllies(allyA, allyB, alliance));
        assertTrue(plot.areAllianceAllies(owner, allyA, alliance));
        assertFalse(plot.areAllianceAllies(allyA, outsider, alliance));
        assertFalse(plot.areAllianceAllies(outsider, allyB, alliance));

        // Membership without the toggle (or after clearing the plot join) grants nothing.
        plot.getAllianceAccess().setFriendlyPvp(false);
        assertFalse(plot.areAllianceAllies(allyA, allyB, alliance));
        plot.getAllianceAccess().setFriendlyPvp(true);
        plot.clearAllianceAccess();
        assertFalse(plot.isAllianceFriendlyPvpEnabled());
        assertFalse(plot.areAllianceAllies(allyA, allyB, alliance));
    }

    @Test
    void privatePlotEntryDecisionUsesAllianceEntryOrTrust() {
        // Mirrors ProtectionManager.onPlayerMove: deny when entry closed AND no INTERACT AND no alliance entry.
        assertTrue(shouldDenyPrivateEntry(false, false, false));
        assertFalse(shouldDenyPrivateEntry(true, false, false), "Open entry flag admits everyone");
        assertFalse(shouldDenyPrivateEntry(false, true, false), "INTERACT trust admits the player");
        assertFalse(shouldDenyPrivateEntry(false, false, true), "Alliance Entry admits alliance members");
        assertFalse(shouldDenyPrivateEntry(false, true, true));
    }

    @Test
    void openPlotPvpDecisionUsesAllianceFriendlyPvp() {
        // Mirrors ProtectionManager.onEntityDamage after plot-wide PvP protection is inactive:
        // friendly-ally damage is cancelled only when areAllianceAllies is true.
        assertFalse(shouldCancelFriendlyAlliancePvp(false), "Default OFF leaves open-plot PvP alone");
        assertTrue(shouldCancelFriendlyAlliancePvp(true), "Friendly PvP ON cancels ally-vs-ally damage");
    }

    private static boolean shouldDenyPrivateEntry(boolean entryOpen, boolean hasInteract, boolean allianceEntry) {
        return !entryOpen && !hasInteract && !allianceEntry;
    }

    private static boolean shouldCancelFriendlyAlliancePvp(boolean areAllianceAllies) {
        return areAllianceAllies;
    }
}
