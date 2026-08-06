package com.aegisguard.arena;

/**
 * Arena combat mode. PvP values are scaffolded; MVP implements PvE only.
 */
public enum ArenaMode {
    PVE_WAVES,
    PVE_BOSS,
    PVP_FFA,
    PVP_TEAMS,
    PVP_DUEL,
    PVP_LPS,
    PVP_TIMED_SCORE;

    public boolean isPve() {
        return this == PVE_WAVES || this == PVE_BOSS;
    }

    public boolean isPvp() {
        return !isPve();
    }
}
