package com.aegisguard.api.service;

import java.util.UUID;

/**
 * Access to a player's ClaimBlock ledger: total/used/available balances and
 * atomic spend/refund operations. Spending is the caller's responsibility to
 * roll back via {@link #refund(UUID, long)} if the accompanying action later
 * fails.
 */
public interface ClaimBlockAccess {

    long getTotalBlocks(UUID playerId);

    long getUsedBlocks(UUID playerId);

    long getSpentBlocks(UUID playerId);

    long getAvailableBlocks(UUID playerId);

    void invalidateOwnerCache(UUID playerId);

    boolean canAfford(UUID playerId, long amount);

    boolean spend(UUID playerId, long amount);

    void refund(UUID playerId, long amount);

    void addEarned(UUID playerId, long amount);

    void addBonus(UUID playerId, long amount);

    void addBought(UUID playerId, long amount);

    void addBoughtFromExchange(UUID playerId, long amount);

    boolean isPlaytimeEarningEnabled(UUID playerId);

    void setPlaytimeEarningEnabled(UUID playerId, boolean enabled);
}
