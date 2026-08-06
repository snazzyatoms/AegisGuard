package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure leadership transfer rules (unit-testable).
 */
public final class ArenaLeadershipRules {

    private ArenaLeadershipRules() {}

    public enum Action {
        KEEP_RESERVED,
        RESTORE_LEADER,
        TRANSFER,
        END_RUN,
        NONE
    }

    public static final class Decision {
        public final Action action;
        public final UUID newLeaderId;
        public final String reason;

        public Decision(Action action, UUID newLeaderId, String reason) {
            this.action = action;
            this.newLeaderId = newLeaderId;
            this.reason = reason;
        }
    }

    /** Deliberate leave: transfer immediately. */
    public static Decision onDeliberateLeave(ArenaRun run, UUID leavingLeader) {
        if (run == null || leavingLeader == null || !leavingLeader.equals(run.getLeaderId())) {
            return new Decision(Action.NONE, null, "not_leader");
        }
        UUID next = pickEligibleFighter(run, leavingLeader);
        if (next == null) return new Decision(Action.END_RUN, null, "LEAVE");
        return new Decision(Action.TRANSFER, next, "LEAVE");
    }

    /** Disconnect: reserve leadership during grace. */
    public static Decision onLeaderDisconnect(ArenaRun run, long now) {
        if (run == null || run.getLeaderId() == null) return new Decision(Action.NONE, null, "none");
        run.setReservedLeaderId(run.getLeaderId());
        run.setLeaderDisconnectAt(now);
        return new Decision(Action.KEEP_RESERVED, run.getLeaderId(), "DISCONNECT_RESERVE");
    }

    /** Reconnect during grace: keep leadership. */
    public static Decision onLeaderReconnectDuringGrace(ArenaRun run, UUID playerId, long now, long graceMillis) {
        if (run == null || playerId == null) return new Decision(Action.NONE, null, "none");
        if (!playerId.equals(run.getReservedLeaderId()) && !playerId.equals(run.getLeaderId())) {
            return new Decision(Action.NONE, null, "not_reserved");
        }
        if (run.getLeaderDisconnectAt() <= 0) return new Decision(Action.NONE, null, "not_disconnected");
        if (now - run.getLeaderDisconnectAt() > graceMillis) {
            return new Decision(Action.NONE, null, "grace_expired");
        }
        return new Decision(Action.RESTORE_LEADER, playerId, "RECONNECT_WITHIN_GRACE");
    }

    /** Grace expired: transfer or end. */
    public static Decision onGraceExpired(ArenaRun run, long now, long graceMillis) {
        if (run == null || run.getLeaderDisconnectAt() <= 0) return new Decision(Action.NONE, null, "none");
        if (now - run.getLeaderDisconnectAt() < graceMillis) {
            return new Decision(Action.KEEP_RESERVED, run.getReservedLeaderId(), "STILL_IN_GRACE");
        }
        UUID next = pickEligibleFighter(run, run.getReservedLeaderId());
        if (next == null) return new Decision(Action.END_RUN, null, "GRACE_EXPIRED");
        return new Decision(Action.TRANSFER, next, "GRACE_EXPIRED");
    }

    public static UUID pickEligibleFighter(ArenaRun run, UUID exclude) {
        if (run == null) return null;
        List<ArenaParticipant> fighters = new ArrayList<>();
        for (ArenaParticipant p : run.getParticipants().values()) {
            if (p.isFighting() && (exclude == null || !exclude.equals(p.getPlayerId()))) {
                fighters.add(p);
            }
        }
        fighters.sort(Comparator.comparing(p -> p.getPlayerId().toString()));
        return fighters.isEmpty() ? null : fighters.get(0).getPlayerId();
    }
}
