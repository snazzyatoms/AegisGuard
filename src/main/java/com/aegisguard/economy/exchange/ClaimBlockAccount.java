package com.aegisguard.economy.exchange;

import java.util.UUID;

/**
 * Adapter for wherever AegisGuard stores a player's ClaimBlocks.
 * Implement this using your existing PlayerData / Profile / Economy system.
 */
public interface ClaimBlockAccount {

    long getBalance(UUID playerId);

    /**
     * Removes claimblocks if possible.
     * @return true if removed, false if insufficient balance or invalid.
     */
    boolean withdraw(UUID playerId, long amount);

    /**
     * Adds claimblocks.
     */
    void deposit(UUID playerId, long amount);
}
