package com.aegisguard.arena;

/**
 * Per-player state inside a shared ArenaRun.
 */
public enum ParticipantState {
    FIGHTING,
    ELIMINATED,
    SPECTATING,
    DISCONNECTED,
    FINISHED
}
