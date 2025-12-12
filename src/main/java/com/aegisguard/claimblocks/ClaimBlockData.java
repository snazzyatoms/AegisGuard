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
    
    // The "Starter" Flag
    // If true, they have already used their "Free First Claim" coupon.
    // They cannot claim another "Starter" plot even if they delete their current one.
    private boolean claimedStarter;

    // Transient (not saved to DB, calculated at runtime)
    private transient long usedBlocksCache = 0;

    public ClaimBlockData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.earnedBlocks = 0;
        this.bonusBlocks = 0;
        this.boughtBlocks = 0;
        this.claimedStarter = false;
    }

    // --- Getters & Setters ---

    public UUID getOwner() { return playerUUID; }

    public long getEarnedBlocks() { return earnedBlocks; }
    public void setEarnedBlocks(long earned) { this.earnedBlocks = earned; }
    public void addEarnedBlocks(long amount) { this.earnedBlocks += amount; }

    public long getBonusBlocks() { return bonusBlocks; }
    public void setBonusBlocks(long bonus) { this.bonusBlocks = bonus; }
    public void addBonusBlocks(long amount) { this.bonusBlocks += amount; }

    public long getBoughtBlocks() { return boughtBlocks; }
    public void setBoughtBlocks(long bought) { this.boughtBlocks = bought; }
    public void addBoughtBlocks(long amount) { this.boughtBlocks += amount; }

    public boolean hasClaimedStarter() { return claimedStarter; }
    public void setClaimedStarter(boolean claimed) { this.claimedStarter = claimed; }

    /**
     * Used blocks are calculated by the Manager scanning actual plots,
     * but we cache it here for quick GUI display.
     */
    public long getUsedBlocksCache() { return usedBlocksCache; }
    public void setUsedBlocksCache(long used) { this.usedBlocksCache = used; }
}
