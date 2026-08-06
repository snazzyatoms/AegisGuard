package com.aegisguard.arena;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArenaLeadershipRulesTest {

    @Test
    void deliberateLeaveTransfersImmediately() {
        UUID leader = UUID.randomUUID();
        UUID fighter = UUID.randomUUID();
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, leader);
        run.getOrCreate(leader).setState(ParticipantState.FIGHTING);
        run.getOrCreate(fighter).setState(ParticipantState.FIGHTING);

        ArenaLeadershipRules.Decision d = ArenaLeadershipRules.onDeliberateLeave(run, leader);
        assertEquals(ArenaLeadershipRules.Action.TRANSFER, d.action);
        assertEquals(fighter, d.newLeaderId);
        assertEquals("LEAVE", d.reason);
    }

    @Test
    void disconnectReservesLeadershipDuringGrace() {
        UUID leader = UUID.randomUUID();
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, leader);
        run.getOrCreate(leader).setState(ParticipantState.DISCONNECTED);

        long now = 1_000_000L;
        ArenaLeadershipRules.Decision d = ArenaLeadershipRules.onLeaderDisconnect(run, now);
        assertEquals(ArenaLeadershipRules.Action.KEEP_RESERVED, d.action);
        assertEquals(leader, run.getReservedLeaderId());

        ArenaLeadershipRules.Decision reconnect = ArenaLeadershipRules.onLeaderReconnectDuringGrace(run, leader, now + 1_000L, 60_000L);
        assertEquals(ArenaLeadershipRules.Action.RESTORE_LEADER, reconnect.action);
    }

    @Test
    void graceExpiryTransfersToFightingMember() {
        UUID leader = UUID.randomUUID();
        UUID fighter = UUID.randomUUID();
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, leader);
        run.getOrCreate(leader).setState(ParticipantState.DISCONNECTED);
        run.getOrCreate(fighter).setState(ParticipantState.FIGHTING);
        run.setReservedLeaderId(leader);
        run.setLeaderDisconnectAt(1_000L);

        ArenaLeadershipRules.Decision d = ArenaLeadershipRules.onGraceExpired(run, 70_000L, 60_000L);
        assertEquals(ArenaLeadershipRules.Action.TRANSFER, d.action);
        assertEquals(fighter, d.newLeaderId);
        assertEquals("GRACE_EXPIRED", d.reason);
    }

    @Test
    void leadershipTransferTokenPreventsDuplicate() {
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, UUID.randomUUID());
        assertTrue(run.tryBeginLeadershipTransfer("LEAVE"));
        assertFalse(run.tryBeginLeadershipTransfer("LEAVE"));
        run.completeLeadershipTransfer(UUID.randomUUID());
        assertTrue(run.tryBeginLeadershipTransfer("GRACE_EXPIRED"));
    }

    @Test
    void disconnectGraceExpiryLeavesNoFighters() {
        UUID leader = UUID.randomUUID();
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, leader);
        ArenaParticipant part = run.getOrCreate(leader);
        part.setState(ParticipantState.DISCONNECTED);
        part.setDisconnectedSince(1_000L);
        part.setEliminatedHandled(true);
        part.setState(ParticipantState.ELIMINATED);
        assertEquals(0, run.countFighting());
    }
}
