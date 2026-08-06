package com.aegisguard.arena;

/**
 * Shared run lifecycle states.
 */
public enum ArenaRunState {
    LOBBY,
    COUNTDOWN,
    WAVE,
    WAVE_CLEAR,
    MILESTONE_BOSS,
    CHECKPOINT,
    CLEAR,
    WIPE,
    ABORT,
    CRASH_RECOVERY,
    CLEANUP,
    CLOSED;

    public boolean isTerminal() {
        return this == CLEAR || this == WIPE || this == ABORT || this == CRASH_RECOVERY || this == CLOSED;
    }

    public boolean isActiveCombat() {
        return this == COUNTDOWN || this == WAVE || this == WAVE_CLEAR
                || this == MILESTONE_BOSS || this == CHECKPOINT;
    }

    public boolean allowsRewardsOrBoards() {
        return this == CLEAR || this == WIPE;
    }
}
