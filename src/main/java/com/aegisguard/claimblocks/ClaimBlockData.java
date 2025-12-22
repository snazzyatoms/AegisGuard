package com.aegisguard.claimblocks;

import java.util.UUID;

/**
 * ClaimBlockData
 * Represents the "Land Wallet" for a player.
 */
public class ClaimBlockData {

    private final UUID playerUUID;

    // The "Bank Account"
    private long earnedBlocks;      // From playtime/events
    private long bonusBlocks;       // From admin commands/ranks
    private long boughtBlocks;      // From economy/marketplace

    // ✅ NEW: spent blocks (used for non-land purchases like leveling upgrades)
    private long spentBlocks;

    // The "Starter" Flag
    private boolean claimedStarter;

    // Transient (not saved to DB, calculated at runtime)
    private transient long usedBlocksCache = 0;

    private transient long lastUsedCacheUpdate = 0L;

    public ClaimBlockData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.earnedBlocks = 0;
        this.bonusBlocks = 0;
        this.boughtBlocks = 0;
        this.spentBlocks = 0;
        this.claimedStarter = false;
    }

    public UUID getOwner() { return playerUUID; }

    public long getEarnedBlocks() { return earnedBlocks; }
    public void setEarnedBlocks(long earned) { this.earnedBlocks = Math.max(0, earned); }
    public void addEarnedBlocks(long amount) {
        if (amount <= 0) return;
        this.earnedBlocks = Math.max(0, this.earnedBlocks + amount);
    }

    public long getBonusBlocks() { return bonusBlocks; }
    public void setBonusBlocks(long bonus) { this.bonusBlocks = Math.max(0, bonus); }
    public void addBonusBlocks(long amount) {
        if (amount <= 0) return;
        this.bonusBlocks = Math.max(0, this.bonusBlocks + amount);
    }

    public long getBoughtBlocks() { return boughtBlocks; }
    public void setBoughtBlocks(long bought) { this.boughtBlocks = Math.max(0, bought); }
    public void addBoughtBlocks(long amount) {
        if (amount <= 0) return;
        this.boughtBlocks = Math.max(0, this.boughtBlocks + amount);
    }

    // ✅ NEW: Spent blocks
    public long getSpentBlocks() { return spentBlocks; }
    public void setSpentBlocks(long spent) { this.spentBlocks = Math.max(0, spent); }

    public void addSpentBlocks(long amount) {
        if (amount <= 0) return;
        this.spentBlocks = Math.max(0, this.spentBlocks + amount);
    }

    public void removeSpentBlocks(long amount) {
        if (amount <= 0) return;
        this.spentBlocks = Math.max(0, this.spentBlocks - amount);
    }

    public boolean hasClaimedStarter() { return claimedStarter; }
    public void setClaimedStarter(boolean claimed) { this.claimedStarter = claimed; }

    public long getUsedBlocksCache() { return usedBlocksCache; }
    public void setUsedBlocksCache(long used) {
        this.usedBlocksCache = Math.max(0, used);
        this.lastUsedCacheUpdate = System.currentTimeMillis();
    }

    /** Earned + Bonus + Bought (does NOT include starter config amount). */
    public long getTotalNonStarter() {
        return Math.max(0, earnedBlocks) + Math.max(0, bonusBlocks) + Math.max(0, boughtBlocks);
    }

    /** Full total including starter config amount provided by the manager/config. */
    public long getTotalWithStarter(long starterFromConfig) {
        return Math.max(0, starterFromConfig) + getTotalNonStarter();
    }

    public long getLastUsedCacheUpdate() {
        return lastUsedCacheUpdate;
    }
}
