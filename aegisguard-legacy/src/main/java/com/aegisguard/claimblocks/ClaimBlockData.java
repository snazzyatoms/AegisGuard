package com.aegisguard.claimblocks;

import java.util.*;

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
    private boolean playtimeEarningEnabled;

    // Transient (not saved to DB, calculated at runtime)
    private transient long usedBlocksCache = 0;
    private transient long lastUsedCacheUpdate = 0L;

    // =========================================================================
    // ✅ NEW (v1.2.5): Sell-Lock Lot Tracking
    // =========================================================================
    // We track "lots" (amount + timestamp + type) so we can enforce:
    // - hold_minutes
    // - scope (EXCHANGE_PURCHASED_ONLY vs ALL)
    // - max_locked_blocks (lock only most recent X eligible blocks)
    //
    // Persisted as a string list in YAML:
    //   type|atMillis|amount
    // Example:
    //   EXCHANGE|1700000000000|250
    //
    public enum LotType {
        EARNED,
        BONUS,
        BOUGHT,
        EXCHANGE
    }

    public static final class SellLockLot {
        private LotType type;
        private long atMillis;
        private long amount;

        public SellLockLot(LotType type, long atMillis, long amount) {
            this.type = type;
            this.atMillis = atMillis;
            this.amount = amount;
        }

        public LotType getType() { return type; }
        public long getAtMillis() { return atMillis; }
        public long getAmount() { return amount; }

        public void setAmount(long amount) { this.amount = Math.max(0, amount); }
    }

    // Newest lots are appended to the end.
    private final Deque<SellLockLot> sellLockLots = new ArrayDeque<>();

    public ClaimBlockData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.earnedBlocks = 0;
        this.bonusBlocks = 0;
        this.boughtBlocks = 0;
        this.spentBlocks = 0;
        this.claimedStarter = false;
        this.playtimeEarningEnabled = true;
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

    public boolean isPlaytimeEarningEnabled() { return playtimeEarningEnabled; }
    public void setPlaytimeEarningEnabled(boolean enabled) { this.playtimeEarningEnabled = enabled; }

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

    /**
     * Get the number of available (unused) claim blocks.
     * This is the total blocks minus the used blocks cache.
     *
     * @return The number of available blocks
     */
    public long getAvailable() {
        long total = getTotalNonStarter();
        return Math.max(0, total - usedBlocksCache - spentBlocks);
    }

    /**
     * Get the number of available blocks including starter amount.
     *
     * @param starterFromConfig The starter amount from config
     * @return The number of available blocks including starter
     */
    public long getAvailableWithStarter(long starterFromConfig) {
        long total = getTotalWithStarter(starterFromConfig);
        return Math.max(0, total - usedBlocksCache - spentBlocks);
    }

    public long getLastUsedCacheUpdate() {
        return lastUsedCacheUpdate;
    }

    public void setLastUsedCacheUpdate(long lastUsedCacheUpdate) {
        this.lastUsedCacheUpdate = Math.max(0L, lastUsedCacheUpdate);
    }

    // =========================================================================
    // Sell-Lock Lot API
    // =========================================================================

    /**
     * Record an incoming lot for sell-lock tracking.
     * Merges into the most recent lot if the type matches and it's recent (anti-spam).
     */
    public synchronized void recordLot(LotType type, long amount) {
        recordLot(type, amount, System.currentTimeMillis());
    }

    public synchronized void recordLot(LotType type, long amount, long atMillis) {
        if (type == null || amount <= 0) return;

        SellLockLot last = sellLockLots.peekLast();
        if (last != null && last.type == type) {
            long age = Math.abs(atMillis - last.atMillis);
            if (age <= 60_000L) { // merge within 60s
                last.amount = Math.max(0, last.amount + amount);
                pruneLotsIfNeeded();
                return;
            }
        }

        sellLockLots.addLast(new SellLockLot(type, atMillis, amount));
        pruneLotsIfNeeded();
    }

    /** Sum lots for a set of types. */
    public synchronized long sumLots(EnumSet<LotType> eligibleTypes) {
        if (eligibleTypes == null || eligibleTypes.isEmpty()) return 0;
        long sum = 0;
        for (SellLockLot lot : sellLockLots) {
            if (lot != null && eligibleTypes.contains(lot.type)) {
                sum += Math.max(0, lot.amount);
            }
        }
        return sum;
    }

    /**
     * Computes how many blocks are currently locked among eligible types.
     *
     * maxLockedBlocks:
     * - <=0 : lock applies to all eligible blocks
     * - >0  : lock applies only to the most recent maxLockedBlocks eligible blocks
     */
    public synchronized long computeLockedBlocks(EnumSet<LotType> eligibleTypes,
                                                 long holdMillis,
                                                 long maxLockedBlocks,
                                                 long nowMillis) {
        if (eligibleTypes == null || eligibleTypes.isEmpty()) return 0;
        if (holdMillis <= 0) return 0;

        long remainingCap = (maxLockedBlocks <= 0) ? Long.MAX_VALUE : maxLockedBlocks;
        long locked = 0;

        Iterator<SellLockLot> it = sellLockLots.descendingIterator(); // newest first
        while (it.hasNext() && remainingCap > 0) {
            SellLockLot lot = it.next();
            if (lot == null) continue;
            if (!eligibleTypes.contains(lot.type)) continue;

            long amt = Math.max(0, lot.amount);
            if (amt <= 0) continue;

            long inCap = Math.min(amt, remainingCap);
            long age = nowMillis - lot.atMillis;

            if (age < holdMillis) locked += inCap;

            remainingCap -= inCap;
        }

        return Math.max(0, locked);
    }

    /**
     * Returns the soonest remaining lock time (seconds) for any locked eligible lot portion
     * in the capped window. 0 means nothing locked right now.
     */
    public synchronized long getMinRemainingHoldSeconds(EnumSet<LotType> eligibleTypes,
                                                        long holdMillis,
                                                        long maxLockedBlocks,
                                                        long nowMillis) {
        if (eligibleTypes == null || eligibleTypes.isEmpty()) return 0;
        if (holdMillis <= 0) return 0;

        long remainingCap = (maxLockedBlocks <= 0) ? Long.MAX_VALUE : maxLockedBlocks;
        long best = Long.MAX_VALUE;

        Iterator<SellLockLot> it = sellLockLots.descendingIterator(); // newest first
        while (it.hasNext() && remainingCap > 0) {
            SellLockLot lot = it.next();
            if (lot == null) continue;
            if (!eligibleTypes.contains(lot.type)) continue;

            long amt = Math.max(0, lot.amount);
            if (amt <= 0) continue;

            long inCap = Math.min(amt, remainingCap);
            long age = nowMillis - lot.atMillis;

            if (age < holdMillis && inCap > 0) {
                long remaining = holdMillis - age;
                if (remaining < best) best = remaining;
            }

            remainingCap -= inCap;
        }

        if (best == Long.MAX_VALUE) return 0;
        return (best + 999L) / 1000L;
    }

    /**
     * Consumes blocks from eligible lot types in FIFO order (oldest first).
     * Returns how many blocks were consumed.
     */
    public synchronized long consumeLotsFIFO(EnumSet<LotType> eligibleTypes, long amount) {
        if (eligibleTypes == null || eligibleTypes.isEmpty()) return 0;
        if (amount <= 0) return 0;

        long remaining = amount;

        Iterator<SellLockLot> it = sellLockLots.iterator(); // oldest first
        while (it.hasNext() && remaining > 0) {
            SellLockLot lot = it.next();
            if (lot == null) { it.remove(); continue; }
            if (!eligibleTypes.contains(lot.type)) continue;

            long lotAmt = Math.max(0, lot.amount);
            if (lotAmt <= 0) { it.remove(); continue; }

            long take = Math.min(lotAmt, remaining);
            lot.amount = lotAmt - take;
            remaining -= take;

            if (lot.amount <= 0) it.remove();
        }

        return amount - remaining;
    }

    /** Persist lots to YAML as List<String> (type|atMillis|amount). */
    public synchronized List<String> serializeLots() {
        List<String> out = new ArrayList<>(sellLockLots.size());
        for (SellLockLot lot : sellLockLots) {
            if (lot == null) continue;
            if (lot.amount <= 0) continue;
            out.add(lot.type.name() + "|" + lot.atMillis + "|" + lot.amount);
        }
        return out;
    }

    /** Load lots from YAML List<String> (type|atMillis|amount). */
    public synchronized void deserializeLots(List<String> lines) {
        sellLockLots.clear();
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;

            String[] parts = line.split("\\|");
            if (parts.length != 3) continue;

            try {
                LotType type = LotType.valueOf(parts[0].trim());
                long at = Long.parseLong(parts[1].trim());
                long amt = Long.parseLong(parts[2].trim());
                if (amt <= 0) continue;

                sellLockLots.addLast(new SellLockLot(type, at, amt));
            } catch (Exception ignored) {}
        }

        pruneLotsIfNeeded();
    }

    private synchronized void pruneLotsIfNeeded() {
        // Hard cap on stored lots to avoid unbounded growth
        final int MAX_LOTS = 250;

        while (sellLockLots.size() > MAX_LOTS) {
            SellLockLot first = sellLockLots.pollFirst();
            if (first == null) break;

            SellLockLot next = sellLockLots.peekFirst();
            if (next != null && next.type == first.type) {
                next.amount = Math.max(0, next.amount + first.amount);
                next.atMillis = Math.min(next.atMillis, first.atMillis);
            } else {
                // If we can't merge, drop the oldest to keep the file bounded.
            }
        }
    }
}
