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
}
