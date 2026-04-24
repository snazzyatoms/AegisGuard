package com.aegisguard.api.service;

import java.util.UUID;

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
