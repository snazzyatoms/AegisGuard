package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClaimBlock Exchange (Vault <-> ClaimBlocks) service.
 *
 * ✅ Up-to-date with the "claim_blocks.exchange" config block we standardized:
 * - claim_blocks.exchange.enabled
 * - claim_blocks.exchange.profile (safe_small|balanced_mid|fast_large|custom)
 * - claim_blocks.exchange.min_spread_percent
 * - claim_blocks.exchange.cooldown_seconds (custom) OR profiles.<name>.cooldown_seconds
 * - claim_blocks.exchange.buy.* / sell.* (custom) OR profiles.<name>.buy.* / sell.*
 * - claim_blocks.exchange.sell_lock.* (custom) OR profiles.<name>.sell_lock.*
 * - claim_blocks.exchange.worlds_allowed.*
 * - claim_blocks.exchange.sell_requires_permission.*
 *
 * 🧩 Backwards-compatible reads:
 * - Old "rates/limits/fees/permissions" paths are still supported as fallbacks.
 */
public class ClaimBlockExchangeService {

    public record ExchangeResult(boolean success, String message) {}

    private final AegisGuard plugin;

    private final File file;
    private FileConfiguration data;
    private final Object ioLock = new Object();

    private final Map<UUID, PlayerState> cache = new ConcurrentHashMap<>();

    public ClaimBlockExchangeService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-block-exchange.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<String> getRatesLines(Player p) {
        ExchangeSettings s = resolveSettings(p);

        List<String> out = new ArrayList<>();
        out.add(color("&8&m------------------------"));
        out.add(color("&6&lClaimBlocks Exchange"));

        if (!s.enabled) {
            out.add(color("&cExchange is disabled."));
            out.add(color("&8&m------------------------"));
            return out;
        }

        if (!isVaultReady()) {
            out.add(color("&cVault economy is not available."));
            out.add(color("&7Requires: &eeconomy.use_vault=true &7and &eeconomy.vault.enabled=true &7and a Vault provider."));
            out.add(color("&8&m------------------------"));
            return out;
        }

        // Spread warning (info only)
        if (s.minSpreadPercent > 0.0 && s.buyEnabled && s.sellEnabled && s.buyPricePerBlock > 0.0) {
            double spread = 100.0 * (1.0 - (s.sellPricePerBlock / s.buyPricePerBlock));
            if (spread < s.minSpreadPercent) {
                out.add(color("&e⚠ Warning: Buy/Sell spread is only &6" + format2(spread) + "%&e (min recommended: "
                        + format2(s.minSpreadPercent) + "%)."));
            }
        }

        out.add(color("&7Buy: " + (s.buyEnabled ? "&aEnabled" : "&cDisabled")
                + "&7 @ &e" + s.buyPricePerBlock + " &7per block &8| &7Fee: &e" + s.buyFeePercent + "%"));
        out.add(color("&7Sell: " + (s.sellEnabled ? "&aEnabled" : "&cDisabled")
                + "&7 @ &e" + s.sellPricePerBlock + " &7per block &8| &7Fee: &e" + s.sellFeePercent + "%"));

        out.add(color("&7Cooldown: &e" + s.cooldownSeconds + "s" + (s.maxTradesPerHour > 0 ? " &7| Trades/hour: &e" + s.maxTradesPerHour : "")));
        out.add(color("&7Buy limits: &e" + s.buyMin + "-" + s.buyMaxPerTrade + " &7per trade, daily: &e" + s.buyDailyCapBlocks));
        out.add(color("&7Sell limits: &e" + s.sellMin + "-" + s.sellMaxPerTrade + " &7per trade, daily: &e" + s.sellDailyCapBlocks));

        if (s.sellLockEnabled) {
            out.add(color("&7Sell-lock: &e" + s.sellLockHoldMinutes + "m &7(" + s.sellLockScope + ")"));
        } else {
            out.add(color("&7Sell-lock: &aDisabled"));
        }

        out.add(color("&8&m------------------------"));
        return out;
    }

    public ExchangeResult buy(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return fail("&cExchange is disabled.");
        if (!s.buyEnabled) return fail("&cBuying ClaimBlocks is disabled.");
        if (!isWorldAllowed(p, s)) return fail("&cExchange is not allowed in this world.");
        if (!isVaultReady()) return fail("&cVault economy is not available.");

        if (!hasPerm(p, s.exchangePerm)) return fail("&cYou do not have permission to use the exchange.");
        if (!hasPerm(p, s.buyPerm)) return fail("&cYou do not have permission to buy ClaimBlocks.");

        if (blocks < s.buyMin) return fail("&cMinimum buy amount is &e" + s.buyMin + "&c.");
        if (s.buyMaxPerTrade > 0 && blocks > s.buyMaxPerTrade) return fail("&cMaximum buy per trade is &e" + s.buyMaxPerTrade + "&c.");

        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) return fail("&cClaimBlocks system is not available.");

        boolean bypass = p.hasPermission(s.bypassPerm);

        PlayerState st = getState(p.getUniqueId());
        long now = System.currentTimeMillis();

        synchronized (st) {
            normalizeWindows(st);

            if (!bypass) {
                long remainingCd = cooldownRemainingSeconds(st, now, s.cooldownSeconds);
                if (remainingCd > 0) return fail("&cPlease wait &e" + remainingCd + "s &cbefore trading again.");

                if (s.maxTradesPerHour > 0 && st.tradesThisHour >= s.maxTradesPerHour) {
                    return fail("&cHourly trade limit reached. Try again later.");
                }

                if (s.buyDailyCapBlocks > 0 && st.boughtTodayBlocks + blocks > s.buyDailyCapBlocks) {
                    long left = Math.max(0, s.buyDailyCapBlocks - st.boughtTodayBlocks);
                    return fail("&cDaily buy cap reached. You can only buy &e" + left + " &cmore today.");
                }
            }
        }

        double baseCost = blocks * s.buyPricePerBlock;
        double fee = (baseCost * (s.buyFeePercent / 100.0)) + s.buyFeeFlat; // buyFeeFlat supported for legacy configs
        double total = baseCost + fee;

        if (!plugin.vault().has(p, total)) {
            return fail("&cYou don't have enough money. Cost: &e" + plugin.vault().format(total));
        }

        if (!plugin.vault().charge(p, total)) {
            return fail("&cPayment failed. Please try again.");
        }

        // Add purchased blocks (kept as-is to match your current manager/service integration)
        mgr.addBought(p.getUniqueId(), blocks);
        mgr.saveAsync();

        synchronized (st) {
            st.lastTradeMillis = now;
            st.tradesThisHour++;
            st.boughtTodayBlocks += blocks;
            st.moneySpentToday += total;

            // For sell_lock scope ALL, this should ideally be updated by *any* claimblock gain source.
            // This tracks exchange buys (and can be called externally via markAnyGain()).
            st.lastAnyGainMillis = now;

            // Sell lock tracking for purchased blocks
            addPurchaseLot(st, blocks, now, s.sellLockMaxLockedBlocks);
        }

        saveAsync();

        long newAvail = mgr.getAvailableBlocks(p.getUniqueId());
        return ok("&aPurchased &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(total)
                + "&a. &7(Available: &a" + newAvail + "&7)");
    }

    public ExchangeResult sell(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return fail("&cExchange is disabled.");
        if (!s.sellEnabled) return fail("&cSelling ClaimBlocks is disabled.");
        if (!isWorldAllowed(p, s)) return fail("&cExchange is not allowed in this world.");
        if (!isVaultReady()) return fail("&cVault economy is not available.");
        if (!hasPerm(p, s.exchangePerm)) return fail("&cYou do not have permission to use the exchange.");

        // Optional sell permission gate from config
        if (s.sellRequiresPermission && !hasPerm(p, s.sellPerm)) {
            return fail("&cYou do not have permission to sell ClaimBlocks.");
        }

        if (blocks < s.sellMin) return fail("&cMinimum sell amount is &e" + s.sellMin + "&c.");
        if (s.sellMaxPerTrade > 0 && blocks > s.sellMaxPerTrade) return fail("&cMaximum sell per trade is &e" + s.sellMaxPerTrade + "&c.");

        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) return fail("&cClaimBlocks system is not available.");

        boolean bypass = p.hasPermission(s.bypassPerm);
        boolean bypassLock = p.hasPermission(s.bypassSellLockPerm) || bypass;

        UUID uuid = p.getUniqueId();
        ClaimBlockData cbd = mgr.getOrCreate(uuid);

        PlayerState st = getState(uuid);
        long now = System.currentTimeMillis();

        synchronized (st) {
            normalizeWindows(st);

            if (!bypass) {
                long remainingCd = cooldownRemainingSeconds(st, now, s.cooldownSeconds);
                if (remainingCd > 0) return fail("&cPlease wait &e" + remainingCd + "s &cbefore trading again.");

                if (s.maxTradesPerHour > 0 && st.tradesThisHour >= s.maxTradesPerHour) {
                    return fail("&cHourly trade limit reached. Try again later.");
                }

                if (s.sellDailyCapBlocks > 0 && st.soldTodayBlocks + blocks > s.sellDailyCapBlocks) {
                    long left = Math.max(0, s.sellDailyCapBlocks - st.soldTodayBlocks);
                    return fail("&cDaily sell cap reached. You can only sell &e" + left + " &cmore today.");
                }
            }
        }

        // Sellable balance calculation: never allow selling "starter" capacity
        long starter = plugin.cfg().raw().getLong("claim_blocks.starting_blocks", 0);
        long used = mgr.getUsedBlocks(uuid);
        long spent = mgr.getSpentBlocks(uuid);

        long nonStarterTotal = cbd.getTotalNonStarter();
        long nonStarterConsumed = Math.max(0L, (used + spent) - starter);
        long nonStarterRemaining = Math.max(0L, nonStarterTotal - nonStarterConsumed);

        if (nonStarterRemaining < blocks) {
            return fail("&cYou only have &e" + nonStarterRemaining + " &csellable ClaimBlocks (starter blocks cannot be sold).");
        }

        // Sell-lock enforcement
        if (s.sellLockEnabled && !bypassLock) {
            if ("ALL".equalsIgnoreCase(s.sellLockScope)) {
                long holdMs = s.sellLockHoldMinutes * 60_000L;
                long until;
                synchronized (st) {
                    until = st.lastAnyGainMillis + holdMs;
                }
                if (now < until) {
                    long secs = Math.max(1, (until - now + 999) / 1000);
                    return fail("&cSell-lock active. Try again in &e" + secs + "s&c.");
                }
            } else {
                long lockedPurchased;
                long nextUnlock;
                synchronized (st) {
                    pruneLots(st, now, s.sellLockHoldMinutes);
                    lockedPurchased = sumLockedLots(st, now, s.sellLockHoldMinutes);
                    nextUnlock = nextUnlockSeconds(st, now, s.sellLockHoldMinutes);
                }

                // Fairness: assume any over-starter consumption hits bought blocks first,
                // so locked purchased can't exceed bought remaining after that consumption.
                long bought = cbd.getBoughtBlocks();
                long purchasedRemainingAfterConsumption = Math.max(0L, bought - nonStarterConsumed);
                long effectiveLocked = Math.min(lockedPurchased, purchasedRemainingAfterConsumption);

                long allowed = Math.max(0L, nonStarterRemaining - effectiveLocked);

                if (blocks > allowed) {
                    if (nextUnlock <= 0) nextUnlock = (s.sellLockHoldMinutes * 60L);
                    return fail("&cSell-lock active. You can sell &e" + allowed + " &cnow. Next unlock in ~&e" + nextUnlock + "s&c.");
                }
            }
        }

        double base = blocks * s.sellPricePerBlock;
        double fee = (base * (s.sellFeePercent / 100.0)) + s.sellFeeFlat; // sellFeeFlat supported for legacy configs
        double payout = Math.max(0.0, base - fee);

        if (!Double.isFinite(payout) || payout <= 0.0) {
            return fail("&cSell payout is invalid. Check exchange prices/fees.");
        }

        // Deduct blocks: earned -> bonus -> bought (but never sell locked purchased)
        if (!deductForSell(cbd, blocks, starter, used, spent, s, st, now)) {
            return fail("&cUnable to process sell. (Sell-lock or balance restriction)");
        }

        mgr.saveAsync();

        plugin.vault().give(p, payout);

        synchronized (st) {
            st.lastTradeMillis = now;
            st.tradesThisHour++;
            st.soldTodayBlocks += blocks;
            st.moneyEarnedToday += payout;
        }

        saveAsync();

        long newAvail = mgr.getAvailableBlocks(uuid);
        return ok("&aSold &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(payout)
                + "&a. &7(Available: &a" + newAvail + "&7)");
    }

    /**
     * Optional hook: if you want sell_lock.scope=ALL to truly mean "ALL gains",
     * call this from your earn/level-up code when a player gains claim blocks.
     */
    public void markAnyGain(UUID playerId) {
        if (playerId == null) return;
        PlayerState st = getState(playerId);
        synchronized (st) {
            st.lastAnyGainMillis = System.currentTimeMillis();
        }
        saveAsync();
    }

    // -------------------------------------------------------------------------
    // Internal: Deduction rules
    // -------------------------------------------------------------------------

    private boolean deductForSell(ClaimBlockData cbd, long amount, long starter, long used, long spent,
                                 ExchangeSettings s, PlayerState st, long now) {

        long nonStarterConsumed = Math.max(0L, (used + spent) - starter);

        long earned = cbd.getEarnedBlocks();
        long bonus = cbd.getBonusBlocks();
        long bought = cbd.getBoughtBlocks();

        // Determine how many bought blocks are unlocked (for EXCHANGE_PURCHASED_ONLY lock)
        long unlockedBought = Long.MAX_VALUE;
        if (s.sellLockEnabled && "EXCHANGE_PURCHASED_ONLY".equalsIgnoreCase(s.sellLockScope)) {
            long lockedPurchased;
            synchronized (st) {
                pruneLots(st, now, s.sellLockHoldMinutes);
                lockedPurchased = sumLockedLots(st, now, s.sellLockHoldMinutes);
            }

            long purchasedRemainingAfterConsumption = Math.max(0L, bought - nonStarterConsumed);
            long effectiveLocked = Math.min(lockedPurchased, purchasedRemainingAfterConsumption);

            unlockedBought = Math.max(0L, purchasedRemainingAfterConsumption - effectiveLocked);
        }

        long remaining = amount;

        long takeEarned = Math.min(remaining, earned);
        earned -= takeEarned;
        remaining -= takeEarned;

        long takeBonus = Math.min(remaining, bonus);
        bonus -= takeBonus;
        remaining -= takeBonus;

        if (remaining > 0) {
            // Must come from bought, but only from unlocked portion
            if (remaining > unlockedBought) return false;
            if (remaining > bought) return false;
            bought -= remaining;
            remaining = 0;
        }

        cbd.setEarnedBlocks(earned);
        cbd.setBonusBlocks(bonus);
        cbd.setBoughtBlocks(bought);
        return true;
    }

    // -------------------------------------------------------------------------
    // Settings resolution (profiles + backwards-compat fallbacks)
    // -------------------------------------------------------------------------

    private ExchangeSettings resolveSettings(Player p) {
        FileConfiguration cfg = plugin.cfg().raw();
        ExchangeSettings s = new ExchangeSettings();

        final String base = "claim_blocks.exchange.";

        s.enabled = cfg.getBoolean(base + "enabled", false);

        // Spread warning threshold (info/log-only)
        s.minSpreadPercent = readDouble(cfg,
                base + "min_spread_percent",
                base + "min-spread-percent",
                0.0);

        // World allowlist
        s.worldsAllowlistEnabled = cfg.getBoolean(base + "worlds_allowed.enabled", false);
        s.worldsAllowed = new HashSet<>(cfg.getStringList(base + "worlds_allowed.worlds"));

        // Optional sell permission gate
        s.sellRequiresPermission = cfg.getBoolean(base + "sell_requires_permission.enabled", false);

        // Permissions:
        // Prefer config if present (legacy), otherwise sane defaults.
        s.exchangePerm = cfg.getString(base + "permissions.exchange", "aegis.claimblocks.exchange");
        // IMPORTANT: default buy perm = exchange perm (so you don't *need* a separate "buy" node)
        s.buyPerm = cfg.getString(base + "permissions.buy", s.exchangePerm);
        s.sellPerm = cfg.getString(base + "permissions.sell", "aegis.claimblocks.sell");
        s.bypassPerm = cfg.getString(base + "permissions.bypass_limits", "aegis.claimblocks.exchange.bypass");
        s.bypassSellLockPerm = cfg.getString(base + "permissions.bypass_sell_lock", "aegisguard.claimblocks.selllock.bypass");

        // Profile selection
        String profile = cfg.getString(base + "profile", "safe_small");
        profile = (profile == null || profile.isBlank()) ? "safe_small" : profile.trim();
        boolean useCustom = profile.equalsIgnoreCase("custom");

        String prof = base + "profiles." + profile + ".";

        // Cooldown (new paths first, then legacy)
        s.cooldownSeconds = (int) readLong(cfg,
                (useCustom ? base : prof) + "cooldown_seconds",
                base + "cooldown_seconds",
                (useCustom ? base : prof) + "limits.cooldown_seconds",
                base + "limits.cooldown_seconds",
                10);

        // Trades/hour (legacy feature, optional; default 0 = unlimited)
        s.maxTradesPerHour = (int) readLong(cfg,
                (useCustom ? base : prof) + "limits.max_trades_per_hour",
                base + "limits.max_trades_per_hour",
                0);

        // BUY side (new first, then legacy)
        s.buyEnabled = cfg.getBoolean((useCustom ? base : prof) + "buy.enabled",
                cfg.getBoolean(base + "buy.enabled", true));

        s.buyPricePerBlock = readDouble(cfg,
                (useCustom ? base : prof) + "buy.price_per_block",
                base + "buy.price_per_block",
                (useCustom ? base : prof) + "rates.buy_price_per_block",
                base + "rates.buy_price_per_block",
                10.0);

        s.buyFeePercent = readDouble(cfg,
                (useCustom ? base : prof) + "buy.fee_percent",
                base + "buy.fee_percent",
                (useCustom ? base : prof) + "fees.buy.percent",
                base + "fees.buy.percent",
                0.0);

        s.buyFeeFlat = readDouble(cfg,
                (useCustom ? base : prof) + "fees.buy.flat",
                base + "fees.buy.flat",
                0.0);

        s.buyMin = (int) readLong(cfg,
                (useCustom ? base : prof) + "buy.min_per_tx",
                base + "buy.min_per_tx",
                (useCustom ? base : prof) + "limits.buy.min_blocks",
                base + "limits.buy.min_blocks",
                1);

        s.buyMaxPerTrade = readLong(cfg,
                (useCustom ? base : prof) + "buy.max_per_tx",
                base + "buy.max_per_tx",
                (useCustom ? base : prof) + "limits.buy.max_blocks_per_trade",
                base + "limits.buy.max_blocks_per_trade",
                5000);

        s.buyDailyCapBlocks = readLong(cfg,
                (useCustom ? base : prof) + "buy.daily_cap",
                base + "buy.daily_cap",
                (useCustom ? base : prof) + "limits.buy.max_blocks_per_day",
                base + "limits.buy.max_blocks_per_day",
                0);

        // SELL side (new first, then legacy)
        s.sellEnabled = cfg.getBoolean((useCustom ? base : prof) + "sell.enabled",
                cfg.getBoolean(base + "sell.enabled", true));

        s.sellPricePerBlock = readDouble(cfg,
                (useCustom ? base : prof) + "sell.price_per_block",
                base + "sell.price_per_block",
                (useCustom ? base : prof) + "rates.sell_price_per_block",
                base + "rates.sell_price_per_block",
                6.0);

        s.sellFeePercent = readDouble(cfg,
                (useCustom ? base : prof) + "sell.fee_percent",
                base + "sell.fee_percent",
                (useCustom ? base : prof) + "fees.sell.percent",
                base + "fees.sell.percent",
                0.0);

        s.sellFeeFlat = readDouble(cfg,
                (useCustom ? base : prof) + "fees.sell.flat",
                base + "fees.sell.flat",
                0.0);

        s.sellMin = (int) readLong(cfg,
                (useCustom ? base : prof) + "sell.min_per_tx",
                base + "sell.min_per_tx",
                (useCustom ? base : prof) + "limits.sell.min_blocks",
                base + "limits.sell.min_blocks",
                1);

        s.sellMaxPerTrade = readLong(cfg,
                (useCustom ? base : prof) + "sell.max_per_tx",
                base + "sell.max_per_tx",
                (useCustom ? base : prof) + "limits.sell.max_blocks_per_trade",
                base + "limits.sell.max_blocks_per_trade",
                5000);

        s.sellDailyCapBlocks = readLong(cfg,
                (useCustom ? base : prof) + "sell.daily_cap",
                base + "sell.daily_cap",
                (useCustom ? base : prof) + "limits.sell.max_blocks_per_day",
                base + "limits.sell.max_blocks_per_day",
                0);

        // Sell-lock (new first, then legacy)
        s.sellLockEnabled = cfg.getBoolean((useCustom ? base : prof) + "sell_lock.enabled",
                cfg.getBoolean(base + "sell_lock.enabled", true));

        s.sellLockHoldMinutes = (int) readLong(cfg,
                (useCustom ? base : prof) + "sell_lock.hold_minutes",
                base + "sell_lock.hold_minutes",
                30);

        s.sellLockScope = cfg.getString((useCustom ? base : prof) + "sell_lock.scope",
                cfg.getString(base + "sell_lock.scope", "EXCHANGE_PURCHASED_ONLY"));

        s.sellLockMaxLockedBlocks = readLong(cfg,
                (useCustom ? base : prof) + "sell_lock.max_locked_blocks",
                base + "sell_lock.max_locked_blocks",
                0);

        // Prefer bypass permission defined under sell_lock, otherwise fall back to legacy permission paths
        String bypassLock = cfg.getString((useCustom ? base : prof) + "sell_lock.bypass_permission", null);
        if (bypassLock == null || bypassLock.isBlank()) {
            bypassLock = cfg.getString(base + "sell_lock.bypass_permission", null);
        }
        if (bypassLock != null && !bypassLock.isBlank()) {
            s.bypassSellLockPerm = bypassLock;
        }

        return s;
    }

    // -------------------------------------------------------------------------
    // Vault readiness + world + perms
    // -------------------------------------------------------------------------

    private boolean isVaultReady() {
        if (plugin.cfg() == null) return false;

        boolean useVault = plugin.cfg().raw().getBoolean("economy.use_vault", true);
        boolean vaultEnabled = plugin.cfg().raw().getBoolean("economy.vault.enabled", true);

        if (!useVault || !vaultEnabled) return false;
        if (plugin.vault() == null) return false;

        try {
            return plugin.vault().isEnabled();
        } catch (Throwable t) {
            // If wrapper doesn't expose isEnabled for some reason, assume it's usable if not null.
            return true;
        }
    }

    private boolean isWorldAllowed(Player p, ExchangeSettings s) {
        if (!s.worldsAllowlistEnabled) return true;
        if (p == null || p.getWorld() == null) return true;
        return s.worldsAllowed.contains(p.getWorld().getName());
    }

    private boolean hasPerm(Player p, String perm) {
        if (p == null) return false;
        if (perm == null || perm.isBlank()) return true;
        return p.hasPermission(perm);
    }

    // -------------------------------------------------------------------------
    // State windows (cooldown/hour/day)
    // -------------------------------------------------------------------------

    private void normalizeWindows(PlayerState st) {
        String today = LocalDate.now().toString();
        if (!today.equals(st.dayKey)) {
            st.dayKey = today;
            st.boughtTodayBlocks = 0;
            st.soldTodayBlocks = 0;
            st.moneySpentToday = 0;
            st.moneyEarnedToday = 0;
        }

        long now = System.currentTimeMillis();
        if (now - st.hourWindowStartMillis >= 3_600_000L) {
            st.hourWindowStartMillis = now;
            st.tradesThisHour = 0;
        }
    }

    private long cooldownRemainingSeconds(PlayerState st, long now, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        long until = st.lastTradeMillis + (cooldownSeconds * 1000L);
        if (now >= until) return 0;
        return Math.max(1, (until - now + 999) / 1000);
    }

    // -------------------------------------------------------------------------
    // Sell-lock lots
    // -------------------------------------------------------------------------

    private void addPurchaseLot(PlayerState st, long amount, long now, long maxLockedBlocks) {
        if (amount <= 0) return;

        st.purchaseLots.add(new Lot(now, amount));

        // Enforce cap: only the most recent N blocks are lock-tracked
        if (maxLockedBlocks > 0) {
            long total = 0;
            // Walk backwards (newest to oldest)
            ListIterator<Lot> it = st.purchaseLots.listIterator(st.purchaseLots.size());
            while (it.hasPrevious()) {
                Lot lot = it.previous();
                total += lot.amount;
                if (total > maxLockedBlocks) {
                    long overflow = total - maxLockedBlocks;

                    long newAmt = lot.amount - overflow;
                    if (newAmt <= 0) {
                        it.remove();
                        total -= lot.amount;
                    } else {
                        it.set(new Lot(lot.timeMillis, newAmt));
                        total -= overflow;
                    }

                    while (it.hasPrevious()) {
                        it.previous();
                        it.remove();
                    }
                    break;
                }
            }
        }
    }

    private void pruneLots(PlayerState st, long now, int holdMinutes) {
        if (holdMinutes <= 0) return;
        long holdMs = holdMinutes * 60_000L;
        st.purchaseLots.removeIf(lot -> (now - lot.timeMillis) >= holdMs);
    }

    private long sumLockedLots(PlayerState st, long now, int holdMinutes) {
        if (holdMinutes <= 0) return 0;
        long holdMs = holdMinutes * 60_000L;

        long total = 0;
        for (Lot lot : st.purchaseLots) {
            if ((now - lot.timeMillis) < holdMs) total += lot.amount;
        }
        return total;
    }

    private long nextUnlockSeconds(PlayerState st, long now, int holdMinutes) {
        if (holdMinutes <= 0) return 0;
        long holdMs = holdMinutes * 60_000L;

        long best = Long.MAX_VALUE;
        for (Lot lot : st.purchaseLots) {
            long unlockAt = lot.timeMillis + holdMs;
            if (unlockAt > now) best = Math.min(best, unlockAt);
        }
        if (best == Long.MAX_VALUE) return 0;
        return Math.max(1, (best - now + 999) / 1000);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void load() {
        synchronized (ioLock) {
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    //noinspection ResultOfMethodCallIgnored
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            data = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection players = data.getConfigurationSection("players");
            if (players == null) return;

            for (String key : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection s = players.getConfigurationSection(key);
                    if (s == null) continue;

                    PlayerState st = new PlayerState();
                    st.lastTradeMillis = s.getLong("last_trade", 0L);
                    st.hourWindowStartMillis = s.getLong("hour_start", System.currentTimeMillis());
                    st.tradesThisHour = s.getInt("hour_trades", 0);

                    st.dayKey = s.getString("day", LocalDate.now().toString());
                    st.boughtTodayBlocks = s.getLong("bought_today_blocks", 0L);
                    st.soldTodayBlocks = s.getLong("sold_today_blocks", 0L);
                    st.moneySpentToday = s.getDouble("money_spent_today", 0.0);
                    st.moneyEarnedToday = s.getDouble("money_earned_today", 0.0);

                    st.lastAnyGainMillis = s.getLong("last_any_gain", 0L);

                    List<Map<?, ?>> lots = s.getMapList("purchase_lots");
                    for (Map<?, ?> m : lots) {
                        Object t = m.get("t");
                        Object a = m.get("a");
                        long tt = (t instanceof Number n) ? n.longValue() : 0L;
                        long aa = (a instanceof Number n) ? n.longValue() : 0L;
                        if (tt > 0 && aa > 0) st.purchaseLots.add(new Lot(tt, aa));
                    }

                    cache.put(uuid, st);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void save() {
        synchronized (ioLock) {
            if (data == null) data = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection root = data.getConfigurationSection("players");
            if (root == null) root = data.createSection("players");

            for (Map.Entry<UUID, PlayerState> e : cache.entrySet()) {
                String path = e.getKey().toString();
                PlayerState st = e.getValue();

                ConfigurationSection s = root.getConfigurationSection(path);
                if (s == null) s = root.createSection(path);

                List<Lot> lotsSnapshot;
                long lastTrade, hourStart, boughtToday, soldToday, lastGain;
                int hourTrades;
                String dayKey;
                double spentToday, earnedToday;

                synchronized (st) {
                    lastTrade = st.lastTradeMillis;
                    hourStart = st.hourWindowStartMillis;
                    hourTrades = st.tradesThisHour;

                    dayKey = st.dayKey;
                    boughtToday = st.boughtTodayBlocks;
                    soldToday = st.soldTodayBlocks;
                    spentToday = st.moneySpentToday;
                    earnedToday = st.moneyEarnedToday;

                    lastGain = st.lastAnyGainMillis;

                    lotsSnapshot = new ArrayList<>(st.purchaseLots);
                }

                s.set("last_trade", lastTrade);
                s.set("hour_start", hourStart);
                s.set("hour_trades", hourTrades);

                s.set("day", dayKey);
                s.set("bought_today_blocks", boughtToday);
                s.set("sold_today_blocks", soldToday);
                s.set("money_spent_today", spentToday);
                s.set("money_earned_today", earnedToday);

                s.set("last_any_gain", lastGain);

                List<Map<String, Object>> lots = new ArrayList<>();
                for (Lot lot : lotsSnapshot) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("t", lot.timeMillis);
                    m.put("a", lot.amount);
                    lots.add(m);
                }
                s.set("purchase_lots", lots);
            }

            try {
                data.save(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void saveAsync() {
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
        } catch (Throwable t) {
            // Fallback (rare): if scheduler is unavailable (shutdown), save sync
            save();
        }
    }

    private PlayerState getState(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> {
            PlayerState st = new PlayerState();
            st.dayKey = LocalDate.now().toString();
            st.hourWindowStartMillis = System.currentTimeMillis();
            return st;
        });
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private ExchangeResult ok(String msg) { return new ExchangeResult(true, color(msg)); }
    private ExchangeResult fail(String msg) { return new ExchangeResult(false, color(msg)); }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static String format2(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    private long readLong(FileConfiguration cfg, String p1, String p2, String p3, String p4, long def) {
        if (cfg.contains(p1)) return cfg.getLong(p1);
        if (cfg.contains(p2)) return cfg.getLong(p2);
        if (cfg.contains(p3)) return cfg.getLong(p3);
        if (cfg.contains(p4)) return cfg.getLong(p4);
        return def;
    }

    private long readLong(FileConfiguration cfg, String p1, String p2, long def) {
        if (cfg.contains(p1)) return cfg.getLong(p1);
        if (cfg.contains(p2)) return cfg.getLong(p2);
        return def;
    }

    private long readLong(FileConfiguration cfg, String p1, long def) {
        return cfg.contains(p1) ? cfg.getLong(p1) : def;
    }

    private double readDouble(FileConfiguration cfg, String p1, String p2, String p3, String p4, double def) {
        if (cfg.contains(p1)) return cfg.getDouble(p1);
        if (cfg.contains(p2)) return cfg.getDouble(p2);
        if (cfg.contains(p3)) return cfg.getDouble(p3);
        if (cfg.contains(p4)) return cfg.getDouble(p4);
        return def;
    }

    private double readDouble(FileConfiguration cfg, String p1, String p2, double def) {
        if (cfg.contains(p1)) return cfg.getDouble(p1);
        if (cfg.contains(p2)) return cfg.getDouble(p2);
        return def;
    }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    private static class ExchangeSettings {
        boolean enabled;

        double minSpreadPercent;

        String exchangePerm;
        String buyPerm;
        String sellPerm;
        String bypassPerm;
        String bypassSellLockPerm;

        boolean sellRequiresPermission;

        boolean worldsAllowlistEnabled;
        Set<String> worldsAllowed = new HashSet<>();

        int cooldownSeconds;
        int maxTradesPerHour; // legacy optional

        boolean buyEnabled;
        double buyPricePerBlock;
        double buyFeePercent;
        double buyFeeFlat; // legacy optional
        int buyMin;
        long buyMaxPerTrade;
        long buyDailyCapBlocks;

        boolean sellEnabled;
        double sellPricePerBlock;
        double sellFeePercent;
        double sellFeeFlat; // legacy optional
        int sellMin;
        long sellMaxPerTrade;
        long sellDailyCapBlocks;

        boolean sellLockEnabled;
        int sellLockHoldMinutes;
        String sellLockScope;
        long sellLockMaxLockedBlocks;
    }

    private static class PlayerState {
        long lastTradeMillis = 0L;

        long hourWindowStartMillis = 0L;
        int tradesThisHour = 0;

        String dayKey = LocalDate.now().toString();
        long boughtTodayBlocks = 0L;
        long soldTodayBlocks = 0L;
        double moneySpentToday = 0.0;
        double moneyEarnedToday = 0.0;

        long lastAnyGainMillis = 0L;

        List<Lot> purchaseLots = new ArrayList<>();
    }

    private record Lot(long timeMillis, long amount) {}
}
