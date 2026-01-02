package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimBlockManager {

    private final AegisGuard plugin;

    private final Map<UUID, ClaimBlockData> cache = new ConcurrentHashMap<>();

    private final File file;
    private FileConfiguration data;

    private final Object ioLock = new Object();

    public ClaimBlockManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-blocks.yml");
        load();
    }

    // =========================================================================
    // ✅ NEW (v1.2.5): Sell Lock Support Types
    // =========================================================================

    public enum SellLockScope {
        EXCHANGE_PURCHASED_ONLY,
        ALL
    }

    public static final class SellLockConfig {
        public final boolean enabled;
        public final long holdMinutes;
        public final SellLockScope scope;
        public final long maxLockedBlocks;

        public SellLockConfig(boolean enabled, long holdMinutes, SellLockScope scope, long maxLockedBlocks) {
            this.enabled = enabled;
            this.holdMinutes = Math.max(0, holdMinutes);
            this.scope = (scope == null) ? SellLockScope.EXCHANGE_PURCHASED_ONLY : scope;
            this.maxLockedBlocks = maxLockedBlocks;
        }

        public long holdMillis() {
            return holdMinutes * 60_000L;
        }
    }

    public static final class SaleWithdrawResult {
        public final boolean success;
        public final String failKey; // reason marker for messaging
        public final long requested;

        public final long fromBought;
        public final long fromEarned;
        public final long fromBonus;

        public final long lockedSecondsRemaining; // 0 if not locked
        public final long unlockedAvailable;      // for helpful messaging

        private SaleWithdrawResult(boolean success, String failKey, long requested,
                                   long fromBought, long fromEarned, long fromBonus,
                                   long lockedSecondsRemaining, long unlockedAvailable) {
            this.success = success;
            this.failKey = failKey;
            this.requested = requested;
            this.fromBought = fromBought;
            this.fromEarned = fromEarned;
            this.fromBonus = fromBonus;
            this.lockedSecondsRemaining = lockedSecondsRemaining;
            this.unlockedAvailable = unlockedAvailable;
        }

        public static SaleWithdrawResult ok(long requested, long fromBought, long fromEarned, long fromBonus) {
            return new SaleWithdrawResult(true, null, requested, fromBought, fromEarned, fromBonus, 0, 0);
        }

        public static SaleWithdrawResult fail(String key, long requested, long lockedSeconds, long unlockedAvailable) {
            return new SaleWithdrawResult(false, key, requested, 0, 0, 0, lockedSeconds, unlockedAvailable);
        }
    }

    // -------------------------------------------------------------------------
    // --- Core Math ---
    // -------------------------------------------------------------------------

    public long getTotalBlocks(UUID uuid) {
        ClaimBlockData user = getOrCreate(uuid);
        long starter = plugin.cfg().raw().getLong("claim_blocks.starting_blocks", 1000);
        return starter + user.getEarnedBlocks() + user.getBonusBlocks() + user.getBoughtBlocks();
    }

    public long getUsedBlocks(UUID uuid) {
        long used = 0;
        for (Plot plot : plugin.store().getPlots(uuid)) {
            if (plot != null) used += plot.getArea();
        }
        getOrCreate(uuid).setUsedBlocksCache(used);
        return used;
    }

    public long getSpentBlocks(UUID uuid) {
        return getOrCreate(uuid).getSpentBlocks();
    }

    public long getAvailableBlocks(UUID uuid) {
        long available = getTotalBlocks(uuid) - getUsedBlocks(uuid) - getSpentBlocks(uuid);
        return Math.max(0, available);
    }

    public boolean canAfford(UUID uuid, long amount) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null && p.hasPermission("aegis.admin.bypass-limits")) return true;
        return getAvailableBlocks(uuid) >= amount;
    }

    // -------------------------------------------------------------------------
    // --- Spending for upgrades/features ---
    // -------------------------------------------------------------------------

    public boolean spend(UUID uuid, long amount) {
        if (uuid == null) return false;
        if (amount <= 0) return true;
        if (plugin.cfg() == null || !plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)) return false;

        if (!canAfford(uuid, amount)) return false;

        getOrCreate(uuid).addSpentBlocks(amount);
        saveAsync();
        return true;
    }

    public void refund(UUID uuid, long amount) {
        if (uuid == null) return;
        if (amount <= 0) return;

        getOrCreate(uuid).removeSpentBlocks(amount);
        saveAsync();
    }

    // -------------------------------------------------------------------------
    // --- Safer Mutators (always persisted) ---
    // -------------------------------------------------------------------------

    public void addEarned(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData d = getOrCreate(uuid);
        d.addEarnedBlocks(amount);
        d.recordLot(ClaimBlockData.LotType.EARNED, amount);
        saveAsync();
    }

    public void addBonus(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData d = getOrCreate(uuid);
        d.addBonusBlocks(amount);
        d.recordLot(ClaimBlockData.LotType.BONUS, amount);
        saveAsync();
    }

    /**
     * Generic bought blocks (NOT exchange-tracked).
     * If you want sell-lock scope EXCHANGE_PURCHASED_ONLY to apply, use addBoughtFromExchange().
     */
    public void addBought(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData d = getOrCreate(uuid);
        d.addBoughtBlocks(amount);
        d.recordLot(ClaimBlockData.LotType.BOUGHT, amount);
        saveAsync();
    }

    /**
     * ✅ NEW (v1.2.5): Bought via ClaimBlocks Exchange (Vault buy).
     * This is what sell-lock uses when scope = EXCHANGE_PURCHASED_ONLY.
     */
    public void addBoughtFromExchange(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData d = getOrCreate(uuid);
        d.addBoughtBlocks(amount);
        d.recordLot(ClaimBlockData.LotType.EXCHANGE, amount);
        saveAsync();
    }

    public ClaimBlockData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, ClaimBlockData::new);
    }

    public void setStarterClaimed(UUID uuid, boolean claimed) {
        ClaimBlockData d = getOrCreate(uuid);
        d.setClaimedStarter(claimed);
        saveAsync();
    }

    // =========================================================================
    // ✅ NEW (v1.2.5): Exchange Sell Withdrawal (sell-lock aware)
    // =========================================================================

    /**
     * Removes claimblocks from the wallet for an exchange "SELL" transaction.
     *
     * Enforces sell-lock if enabled and not bypassed.
     * Also respects available blocks: total - used - spent.
     *
     * @param bypassLock true if player has bypass permission
     */
    public SaleWithdrawResult withdrawForExchangeSell(UUID uuid, long amount, SellLockConfig lock, boolean bypassLock) {
        if (uuid == null) return SaleWithdrawResult.fail("invalid_uuid", amount, 0, 0);
        if (amount <= 0) return SaleWithdrawResult.ok(amount, 0, 0, 0);

        if (plugin.cfg() == null || !plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)) {
            return SaleWithdrawResult.fail("claimblocks_disabled", amount, 0, 0);
        }

        long available = getAvailableBlocks(uuid);
        if (available < amount) {
            return SaleWithdrawResult.fail("insufficient_claimblocks", amount, 0, available);
        }

        ClaimBlockData d = getOrCreate(uuid);

        long now = System.currentTimeMillis();
        long holdMillis = (lock == null) ? 0 : lock.holdMillis();
        long maxLocked = (lock == null) ? 0 : lock.maxLockedBlocks;

        // If scope ALL, we can do a direct lock check against available.
        if (lock != null && lock.enabled && !bypassLock && holdMillis > 0 && lock.scope == SellLockScope.ALL) {
            EnumSet<ClaimBlockData.LotType> eligible = EnumSet.allOf(ClaimBlockData.LotType.class);
            long locked = d.computeLockedBlocks(eligible, holdMillis, maxLocked, now);
            locked = Math.min(locked, available);
            long unlocked = Math.max(0, available - locked);

            if (amount > unlocked) {
                long remSec = d.getMinRemainingHoldSeconds(eligible, holdMillis, maxLocked, now);
                return SaleWithdrawResult.fail("sell_lock_active", amount, remSec, unlocked);
            }
        }

        long remaining = amount;
        long fromBought = 0;
        long fromEarned = 0;
        long fromBonus = 0;

        // 1) Withdraw from bought blocks (respecting EXCHANGE_PURCHASED_ONLY lock when enabled)
        long bought = d.getBoughtBlocks();
        if (bought > 0 && remaining > 0) {

            if (lock != null && lock.enabled && !bypassLock && holdMillis > 0 && lock.scope == SellLockScope.EXCHANGE_PURCHASED_ONLY) {
                EnumSet<ClaimBlockData.LotType> exchangeOnly = EnumSet.of(ClaimBlockData.LotType.EXCHANGE);

                long exchangeTracked = d.sumLots(exchangeOnly);
                long boughtTracked = d.sumLots(EnumSet.of(ClaimBlockData.LotType.BOUGHT));
                long trackedTotal = exchangeTracked + boughtTracked;

                // Legacy/untracked bought blocks are considered "unlocked"
                long unknownBought = Math.max(0, bought - trackedTotal);

                long lockedExchange = d.computeLockedBlocks(exchangeOnly, holdMillis, maxLocked, now);
                lockedExchange = Math.min(lockedExchange, exchangeTracked);

                long unlockedExchange = Math.max(0, exchangeTracked - lockedExchange);

                long unlockedBoughtTotal = unknownBought + boughtTracked + unlockedExchange;
                long takeBought = Math.min(remaining, Math.min(unlockedBoughtTotal, bought));

                if (takeBought > 0) {
                    d.setBoughtBlocks(bought - takeBought);

                    long left = takeBought;

                    // Unknown bought has no lots to consume; treat as consumed first
                    long takeUnknown = Math.min(left, unknownBought);
                    left -= takeUnknown;

                    long takeBoughtLots = Math.min(left, boughtTracked);
                    if (takeBoughtLots > 0) {
                        d.consumeLotsFIFO(EnumSet.of(ClaimBlockData.LotType.BOUGHT), takeBoughtLots);
                        left -= takeBoughtLots;
                    }

                    long takeExchangeLots = Math.min(left, unlockedExchange);
                    if (takeExchangeLots > 0) {
                        d.consumeLotsFIFO(EnumSet.of(ClaimBlockData.LotType.EXCHANGE), takeExchangeLots);
                        left -= takeExchangeLots;
                    }

                    fromBought = takeBought;
                    remaining -= takeBought;
                }

            } else {
                long take = Math.min(remaining, bought);
                if (take > 0) {
                    d.setBoughtBlocks(bought - take);

                    long consumedBoughtLots = d.consumeLotsFIFO(EnumSet.of(ClaimBlockData.LotType.BOUGHT), take);
                    long leftover = take - consumedBoughtLots;
                    if (leftover > 0) {
                        d.consumeLotsFIFO(EnumSet.of(ClaimBlockData.LotType.EXCHANGE), leftover);
                    }

                    fromBought = take;
                    remaining -= take;
                }
            }
        }

        // 2) Earned blocks
        if (remaining > 0) {
            long earned = d.getEarnedBlocks();
            long take = Math.min(remaining, earned);
            if (take > 0) {
                d.setEarnedBlocks(earned - take);
                d.consumeLotsFIFO(EnumSet.of(ClaimBlockData.LotType.EARNED), take);
                fromEarned = take;
                remaining -= take;
            }
        }

        // 3) Bonus blocks
        if (remaining > 0) {
            long bonus = d.getBonusBlocks();
            long take = Math.min(remaining, bonus);
            if (take > 0) {
                d.setBonusBlocks(bonus - take);
                d.consumeLotsFIFO(EnumSet.of(ClaimBlockData.LotType.BONUS), take);
                fromBonus = take;
                remaining -= take;
            }
        }

        // Should never happen due to availability check, but keep a safety net.
        if (remaining > 0) {
            // Best-effort rollback of numeric balances.
            d.addBoughtBlocks(fromBought);
            d.addEarnedBlocks(fromEarned);
            d.addBonusBlocks(fromBonus);

            saveAsync();
            return SaleWithdrawResult.fail("sale_withdraw_inconsistent", amount, 0, available);
        }

        saveAsync();
        return SaleWithdrawResult.ok(amount, fromBought, fromEarned, fromBonus);
    }

    // -------------------------------------------------------------------------
    // --- Persistence (YAML) ---
    // -------------------------------------------------------------------------

    public void load() {
        synchronized (ioLock) {
            if (!file.exists()) {
                try { file.getParentFile().mkdirs(); file.createNewFile(); }
                catch (IOException e) { e.printStackTrace(); }
            }

            data = YamlConfiguration.loadConfiguration(file);

            if (data.contains("players")) {
                for (String key : Objects.requireNonNull(data.getConfigurationSection("players")).getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        ClaimBlockData cbd = new ClaimBlockData(uuid);
                        String path = "players." + key;

                        cbd.setEarnedBlocks(data.getLong(path + ".earned", 0));
                        cbd.setBonusBlocks(data.getLong(path + ".bonus", 0));
                        cbd.setBoughtBlocks(data.getLong(path + ".bought", 0));
                        cbd.setSpentBlocks(data.getLong(path + ".spent", 0)); // ✅ NEW
                        cbd.setClaimedStarter(data.getBoolean(path + ".starter_claimed", false));

                        // ✅ NEW (v1.2.5): Sell-Lock lots
                        List<String> lots = data.getStringList(path + ".sell_lock_lots");
                        if (lots != null && !lots.isEmpty()) {
                            cbd.deserializeLots(lots);
                        }

                        cache.put(uuid, cbd);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (data == null) data = YamlConfiguration.loadConfiguration(file);

            Map<UUID, ClaimBlockData> snap = new HashMap<>(cache);

            for (Map.Entry<UUID, ClaimBlockData> entry : snap.entrySet()) {
                String path = "players." + entry.getKey();
                ClaimBlockData cbd = entry.getValue();

                data.set(path + ".earned", cbd.getEarnedBlocks());
                data.set(path + ".bonus", cbd.getBonusBlocks());
                data.set(path + ".bought", cbd.getBoughtBlocks());
                data.set(path + ".spent", cbd.getSpentBlocks()); // ✅ NEW
                data.set(path + ".starter_claimed", cbd.hasClaimedStarter());

                // ✅ NEW (v1.2.5): Persist sell-lock lots
                data.set(path + ".sell_lock_lots", cbd.serializeLots());
            }

            try { data.save(file); }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void shutdown() {
        save();
    }
}
