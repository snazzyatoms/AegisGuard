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

public class ClaimBlockExchangeService {

    // ---- Backwards-compatible legacy return ----
    public record ExchangeResult(boolean success, String message) {}

    // ---- New GUI-friendly return ----
    public enum ResultType {
        OK,
        DISABLED,
        NO_PERMISSION,
        VAULT_UNAVAILABLE,
        WORLD_BLOCKED,
        COOLDOWN,
        HOURLY_CAP,
        DAILY_CAP,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_BLOCKS,
        SELL_LOCKED,
        ERROR
    }

    public record Result(ResultType type, String message, long longA, double dblA) {
        public static Result ok(String msg) { return new Result(ResultType.OK, msg, 0, 0); }
        public static Result err(ResultType t, String msg) { return new Result(t, msg, 0, 0); }
        public static Result withLong(ResultType t, String msg, long a) { return new Result(t, msg, a, 0); }
        public static Result withDbl(ResultType t, String msg, double a) { return new Result(t, msg, 0, a); }
    }

    public record Quote(long blocks, double unitPrice, double subtotal, double fee, double totalOrPayout) {}

    public record ExchangeOverview(
            double buyPrice,
            double sellPrice,
            double buyFeePercent,
            double buyFeeFlat,
            double sellFeePercent,
            double sellFeeFlat,
            long buyMinimum,
            long buyMaximum,
            long sellMinimum,
            long sellMaximum,
            long buyRemainingToday,
            long sellRemainingToday,
            int tradesRemainingThisHour,
            long cooldownRemainingSeconds,
            boolean sellLockEnabled,
            int sellLockMinutes
    ) {}

    private final AegisGuard plugin;

    private final File file;
    private FileConfiguration data;
    private final Object ioLock = new Object();

    private final Map<UUID, PlayerState> cache = new ConcurrentHashMap<>();

    /** Tracks whether the service has been shut down. */
    private volatile boolean shuttingDown = false;

    public ClaimBlockExchangeService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-block-exchange.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Shuts down the exchange service gracefully.
     * Saves all pending data and prevents further operations.
     */
    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;

        // Final synchronous save to ensure all data is persisted
        try {
            save();
        } catch (Throwable t) {
            plugin.getLogger().warning("[ClaimBlockExchange] Error during shutdown save: " + t.getMessage());
        }

        // Clear cache to free memory
        cache.clear();
    }

    /**
     * Returns true if the service is currently shutting down or has shut down.
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /** Flush in-memory limits before re-reading the persisted exchange state. */
    public void reload() {
        if (shuttingDown) return;
        save();
        cache.clear();
        load();
    }

    // -------------------------------------------------------------------------
    // Quotes (used by GUI)
    // -------------------------------------------------------------------------

    public Quote quoteBuy(long blocks) {
        ExchangeSettings s = resolveSettings(null);
        blocks = Math.max(1, blocks);

        double subtotal = blocks * s.buyPricePerBlock;
        double fee = (subtotal * (s.buyFeePercent / 100.0)) + s.buyFeeFlat;
        double total = subtotal + fee;

        return new Quote(blocks, s.buyPricePerBlock, subtotal, fee, total);
    }

    public Quote quoteSell(long blocks) {
        ExchangeSettings s = resolveSettings(null);
        blocks = Math.max(1, blocks);

        double subtotal = blocks * s.sellPricePerBlock;
        double fee = (subtotal * (s.sellFeePercent / 100.0)) + s.sellFeeFlat;
        double payout = Math.max(0.0, subtotal - fee);

        return new Quote(blocks, s.sellPricePerBlock, subtotal, fee, payout);
    }

    public ExchangeOverview getOverview(Player player) {
        ExchangeSettings s = resolveSettings(player);
        PlayerState state = player == null ? new PlayerState() : getState(player.getUniqueId());
        normalizeWindows(state);

        long buyRemaining = s.buyDailyCapBlocks <= 0
                ? -1L : Math.max(0L, s.buyDailyCapBlocks - state.boughtTodayBlocks);
        long sellRemaining = s.sellDailyCapBlocks <= 0
                ? -1L : Math.max(0L, s.sellDailyCapBlocks - state.soldTodayBlocks);
        int tradesRemaining = s.maxTradesPerHour <= 0
                ? -1 : Math.max(0, s.maxTradesPerHour - state.tradesThisHour);
        long cooldown = cooldownRemainingSeconds(state, System.currentTimeMillis(), s.cooldownSeconds);

        return new ExchangeOverview(
                s.buyPricePerBlock,
                s.sellPricePerBlock,
                s.buyFeePercent,
                s.buyFeeFlat,
                s.sellFeePercent,
                s.sellFeeFlat,
                s.buyMin,
                s.buyMaxPerTrade,
                s.sellMin,
                s.sellMaxPerTrade,
                buyRemaining,
                sellRemaining,
                tradesRemaining,
                cooldown,
                s.sellLockEnabled,
                s.sellLockHoldMinutes
        );
    }

    // -------------------------------------------------------------------------
    // Rate Information (for GUIs and commands)
    // -------------------------------------------------------------------------

    /**
     * Returns formatted lines showing the current exchange rates for display in GUIs/commands.
     * Optionally personalized for a player (e.g., showing their daily limits remaining).
     *
     * @param player The player to personalize for (can be null for generic rates)
     * @return List of formatted strings describing current exchange rates
     */
    public List<String> getRatesLines(Player player) {
        List<String> lines = new ArrayList<>();
        ExchangeSettings s = resolveSettings(player);

        if (!s.enabled) {
            lines.add(color("&cExchange is currently disabled."));
            return lines;
        }

        // Header
        lines.add(color("&6&lClaim Block Exchange Rates"));
        lines.add(color("&7─────────────────────────"));

        // Buy rates
        lines.add(color("&a▸ Buy Price: &f" + formatMoney(s.buyPricePerBlock) + " &7per block"));
        if (s.buyFeePercent > 0 || s.buyFeeFlat > 0) {
            String feeStr = "";
            if (s.buyFeePercent > 0) feeStr += String.format("%.1f%%", s.buyFeePercent);
            if (s.buyFeeFlat > 0) {
                if (!feeStr.isEmpty()) feeStr += " + ";
                feeStr += formatMoney(s.buyFeeFlat) + " flat";
            }
            lines.add(color("  &7Buy Fee: &f" + feeStr));
        }

        // Sell rates
        lines.add(color("&c▸ Sell Price: &f" + formatMoney(s.sellPricePerBlock) + " &7per block"));
        if (s.sellFeePercent > 0 || s.sellFeeFlat > 0) {
            String feeStr = "";
            if (s.sellFeePercent > 0) feeStr += String.format("%.1f%%", s.sellFeePercent);
            if (s.sellFeeFlat > 0) {
                if (!feeStr.isEmpty()) feeStr += " + ";
                feeStr += formatMoney(s.sellFeeFlat) + " flat";
            }
            lines.add(color("  &7Sell Fee: &f" + feeStr));
        }

        // Limits section
        lines.add(color("&7─────────────────────────"));
        lines.add(color("&e&lLimits"));

        if (s.buyMin > 1 || s.sellMin > 1) {
            lines.add(color("&7Min Buy: &f" + s.buyMin + " &7| Min Sell: &f" + s.sellMin));
        }
        if (s.buyMaxPerTrade > 0 || s.sellMaxPerTrade > 0) {
            lines.add(color("&7Max/Trade: Buy &f" + (s.buyMaxPerTrade > 0 ? s.buyMaxPerTrade : "∞")
                    + " &7| Sell &f" + (s.sellMaxPerTrade > 0 ? s.sellMaxPerTrade : "∞")));
        }
        if (s.buyDailyCapBlocks > 0 || s.sellDailyCapBlocks > 0) {
            lines.add(color("&7Daily Cap: Buy &f" + (s.buyDailyCapBlocks > 0 ? s.buyDailyCapBlocks : "∞")
                    + " &7| Sell &f" + (s.sellDailyCapBlocks > 0 ? s.sellDailyCapBlocks : "∞")));
        }

        // Player-specific info
        if (player != null) {
            PlayerState st = getState(player.getUniqueId());
            normalizeWindows(st);

            lines.add(color("&7─────────────────────────"));
            lines.add(color("&b&lYour Status Today"));
            lines.add(color("&7Bought: &a" + st.boughtTodayBlocks + " &7blocks"));
            lines.add(color("&7Sold: &c" + st.soldTodayBlocks + " &7blocks"));

            if (s.buyDailyCapBlocks > 0) {
                long remaining = Math.max(0, s.buyDailyCapBlocks - st.boughtTodayBlocks);
                lines.add(color("&7Buy remaining: &a" + remaining + " &7blocks"));
            }
            if (s.sellDailyCapBlocks > 0) {
                long remaining = Math.max(0, s.sellDailyCapBlocks - st.soldTodayBlocks);
                lines.add(color("&7Sell remaining: &c" + remaining + " &7blocks"));
            }

            if (s.cooldownSeconds > 0) {
                long cdRemaining = cooldownRemainingSeconds(st, System.currentTimeMillis(), s.cooldownSeconds);
                if (cdRemaining > 0) {
                    lines.add(color("&7Cooldown: &e" + cdRemaining + "s"));
                }
            }
        }

        // Sell-lock info
        if (s.sellLockEnabled) {
            lines.add(color("&7─────────────────────────"));
            lines.add(color("&d⚠ Sell-Lock: &f" + s.sellLockHoldMinutes + " min hold"));
        }

        return lines;
    }

    /**
     * Convenience overload without player context.
     */
    public List<String> getRatesLines() {
        return getRatesLines(null);
    }

    /**
     * Formats money amount using Vault if available, otherwise simple format.
     */
    private String formatMoney(double amount) {
        if (plugin.vault() != null) {
            try {
                return plugin.vault().format(amount);
            } catch (Throwable ignored) {}
        }
        return String.format("$%.2f", amount);
    }

    // -------------------------------------------------------------------------
    // Legacy API (kept so older code still compiles)
    // -------------------------------------------------------------------------

    public ExchangeResult buy(Player p, long blocks) {
        if (shuttingDown) return new ExchangeResult(false, color("&cExchange is shutting down."));
        Result r = buyTrade(p, blocks);
        return new ExchangeResult(r.type() == ResultType.OK, color(r.message()));
    }

    public ExchangeResult sell(Player p, long blocks) {
        if (shuttingDown) return new ExchangeResult(false, color("&cExchange is shutting down."));
        Result r = sellTrade(p, blocks);
        return new ExchangeResult(r.type() == ResultType.OK, color(r.message()));
    }

    // -------------------------------------------------------------------------
    // New GUI-friendly API
    // -------------------------------------------------------------------------

    public Result buyTrade(Player p, long blocks) {
        if (shuttingDown) return Result.err(ResultType.DISABLED, "&cExchange is shutting down.");

        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return Result.err(ResultType.DISABLED, "&cExchange is disabled.");
        if (!isWorldAllowed(p, s)) return Result.err(ResultType.WORLD_BLOCKED, "&cExchange is not allowed in this world.");
        if (!isVaultReady(s)) return Result.err(ResultType.VAULT_UNAVAILABLE, "&cVault economy is not available.");
        if (!hasPerm(p, s.exchangePerm)) return Result.err(ResultType.NO_PERMISSION, "&cNo permission (exchange).");
        if (!hasPerm(p, s.buyPerm)) return Result.err(ResultType.NO_PERMISSION, "&cNo permission (buy).");

        if (blocks < s.buyMin) return Result.err(ResultType.ERROR, "&cMinimum buy amount is &e" + s.buyMin + "&c.");
        if (s.buyMaxPerTrade > 0 && blocks > s.buyMaxPerTrade) return Result.err(ResultType.ERROR, "&cMaximum buy per trade is &e" + s.buyMaxPerTrade + "&c.");

        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) return Result.err(ResultType.ERROR, "&cClaimBlocks system is not available.");
        ClaimBlockData blockData = mgr.getOrCreate(p.getUniqueId());
        if (blocks > Long.MAX_VALUE - blockData.getBoughtBlocks()) {
            return Result.err(ResultType.ERROR, "&cThis purchase would exceed the ClaimBlock storage limit.");
        }

        boolean bypass = p != null && p.hasPermission(s.bypassPerm);

        PlayerState st = getState(p.getUniqueId());
        normalizeWindows(st);

        long now = System.currentTimeMillis();

        if (!bypass) {
            long remainingCd = cooldownRemainingSeconds(st, now, s.cooldownSeconds);
            if (remainingCd > 0) return Result.withLong(ResultType.COOLDOWN, "&cCooldown active.", remainingCd);

            if (s.maxTradesPerHour > 0 && st.tradesThisHour >= s.maxTradesPerHour) {
                return Result.err(ResultType.HOURLY_CAP, "&cHourly trade limit reached.");
            }

            if (s.buyDailyCapBlocks > 0 && st.boughtTodayBlocks + blocks > s.buyDailyCapBlocks) {
                long left = Math.max(0, s.buyDailyCapBlocks - st.boughtTodayBlocks);
                return Result.withLong(ResultType.DAILY_CAP, "&cDaily buy cap reached.", left);
            }
        }

        double baseCost = blocks * s.buyPricePerBlock;
        double fee = (baseCost * (s.buyFeePercent / 100.0)) + s.buyFeeFlat;
        double total = baseCost + fee;

        if (!Double.isFinite(total) || total <= 0.0) {
            return Result.err(ResultType.ERROR, "&cInvalid buy total. Check exchange rates/fees.");
        }

        if (!bypass && s.buyDailyCapMoney > 0.0 && (st.moneySpentToday + total) > s.buyDailyCapMoney) {
            double left = Math.max(0.0, s.buyDailyCapMoney - st.moneySpentToday);
            return Result.withLong(ResultType.DAILY_CAP, "&cDaily buy money cap reached.", (long) Math.floor(left));
        }

        if (!plugin.vault().has(p, total)) {
            double needed = total;
            return Result.withDbl(ResultType.INSUFFICIENT_FUNDS, "&cNot enough money.", needed);
        }

        if (!plugin.vault().charge(p, total)) {
            return Result.err(ResultType.ERROR, "&cPayment failed. Please try again.");
        }

        mgr.addBoughtFromExchange(p.getUniqueId(), blocks);

        st.lastTradeMillis = now;
        st.tradesThisHour++;
        st.boughtTodayBlocks += blocks;
        st.moneySpentToday += total;
        st.lastAnyGainMillis = now;

        addPurchaseLot(st, blocks, now, s.sellLockMaxLockedBlocks);

        saveAsync();

        audit(s, p, "BUY", blocks, total);

        long newAvail = mgr.getAvailableBlocks(p.getUniqueId());
        return Result.ok("&aPurchased &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(total)
                + "&a. &7(Available: &a" + newAvail + "&7)");
    }

    public Result sellTrade(Player p, long blocks) {
        if (shuttingDown) return Result.err(ResultType.DISABLED, "&cExchange is shutting down.");

        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return Result.err(ResultType.DISABLED, "&cExchange is disabled.");
        if (!isWorldAllowed(p, s)) return Result.err(ResultType.WORLD_BLOCKED, "&cExchange is not allowed in this world.");
        if (!isVaultReady(s)) return Result.err(ResultType.VAULT_UNAVAILABLE, "&cVault economy is not available.");
        if (!hasPerm(p, s.exchangePerm)) return Result.err(ResultType.NO_PERMISSION, "&cNo permission (exchange).");

        if (s.sellRequiresPermission && !hasPerm(p, s.sellPerm)) {
            return Result.err(ResultType.NO_PERMISSION, "&cNo permission (sell).");
        }

        if (blocks < s.sellMin) return Result.err(ResultType.ERROR, "&cMinimum sell amount is &e" + s.sellMin + "&c.");
        if (s.sellMaxPerTrade > 0 && blocks > s.sellMaxPerTrade) return Result.err(ResultType.ERROR, "&cMaximum sell per trade is &e" + s.sellMaxPerTrade + "&c.");

        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) return Result.err(ResultType.ERROR, "&cClaimBlocks system is not available.");

        boolean bypass = p != null && p.hasPermission(s.bypassPerm);
        boolean bypassLock = p != null && (p.hasPermission(s.bypassSellLockPerm) || bypass);

        PlayerState st = getState(p.getUniqueId());
        normalizeWindows(st);

        long now = System.currentTimeMillis();

        if (!bypass) {
            long remainingCd = cooldownRemainingSeconds(st, now, s.cooldownSeconds);
            if (remainingCd > 0) return Result.withLong(ResultType.COOLDOWN, "&cCooldown active.", remainingCd);

            if (s.maxTradesPerHour > 0 && st.tradesThisHour >= s.maxTradesPerHour) {
                return Result.err(ResultType.HOURLY_CAP, "&cHourly trade limit reached.");
            }

            if (s.sellDailyCapBlocks > 0 && st.soldTodayBlocks + blocks > s.sellDailyCapBlocks) {
                long left = Math.max(0, s.sellDailyCapBlocks - st.soldTodayBlocks);
                return Result.withLong(ResultType.DAILY_CAP, "&cDaily sell cap reached.", left);
            }
        }

        UUID uuid = p.getUniqueId();
        ClaimBlockData cbd = mgr.getOrCreate(uuid);

        long starter = plugin.cfg().raw().getLong("claim_blocks.starting_blocks", 0);
        long used = mgr.getUsedBlocks(uuid);
        long spent = mgr.getSpentBlocks(uuid);

        long nonStarterTotal = cbd.getTotalNonStarter();
        long nonStarterConsumed = Math.max(0L, (used + spent) - starter);
        long nonStarterRemaining = Math.max(0L, nonStarterTotal - nonStarterConsumed);

        if (nonStarterRemaining < blocks) {
            return Result.err(ResultType.INSUFFICIENT_BLOCKS,
                    "&cYou only have &e" + nonStarterRemaining + " &csellable ClaimBlocks (starter blocks cannot be sold).");
        }

        if (s.sellLockEnabled && !bypassLock) {
            if ("ALL".equalsIgnoreCase(s.sellLockScope)) {
                long holdMs = s.sellLockHoldMinutes * 60_000L;
                long until = st.lastAnyGainMillis + holdMs;
                if (now < until) {
                    long secs = Math.max(1, (until - now + 999) / 1000);
                    return Result.withLong(ResultType.SELL_LOCKED, "&cSell-lock active.", secs);
                }
            } else {
                pruneLots(st, now, s.sellLockHoldMinutes);

                long lockedPurchased = sumLockedLots(st, now, s.sellLockHoldMinutes);

                long bought = cbd.getBoughtBlocks();
                long purchasedRemainingAfterConsumption = Math.max(0L, bought - nonStarterConsumed);
                long effectiveLocked = Math.min(lockedPurchased, purchasedRemainingAfterConsumption);

                long allowed = Math.max(0L, nonStarterRemaining - effectiveLocked);

                if (blocks > allowed) {
                    long next = nextUnlockSeconds(st, now, s.sellLockHoldMinutes);
                    if (next <= 0) next = (s.sellLockHoldMinutes * 60L);
                    return Result.withLong(ResultType.SELL_LOCKED, "&cSell-lock active.", next);
                }
            }
        }

        double base = blocks * s.sellPricePerBlock;
        double fee = (base * (s.sellFeePercent / 100.0)) + s.sellFeeFlat;
        double payout = Math.max(0.0, base - fee);

        if (!Double.isFinite(payout) || payout <= 0.0) {
            return Result.err(ResultType.ERROR, "&cSell payout is invalid. Check exchange prices/fees.");
        }

        if (!bypass && s.sellDailyCapMoney > 0.0 && (st.moneyEarnedToday + payout) > s.sellDailyCapMoney) {
            double left = Math.max(0.0, s.sellDailyCapMoney - st.moneyEarnedToday);
            return Result.withLong(ResultType.DAILY_CAP, "&cDaily sell money cap reached.", (long) Math.floor(left));
        }

        long earnedBefore = cbd.getEarnedBlocks();
        long bonusBefore = cbd.getBonusBlocks();
        long boughtBefore = cbd.getBoughtBlocks();
        if (!deductForSell(cbd, blocks, starter, used, spent, s, st)) {
            return Result.err(ResultType.SELL_LOCKED, "&cUnable to process sell due to sell-lock/balance rules.");
        }

        if (!plugin.vault().deposit(p, payout)) {
            cbd.setEarnedBlocks(earnedBefore);
            cbd.setBonusBlocks(bonusBefore);
            cbd.setBoughtBlocks(boughtBefore);
            mgr.saveAsync();
            return Result.err(ResultType.ERROR,
                    "&cEconomy payout failed. Your ClaimBlocks were restored; please try again.");
        }

        mgr.saveAsync();

        st.lastTradeMillis = now;
        st.tradesThisHour++;
        st.soldTodayBlocks += blocks;
        st.moneyEarnedToday += payout;

        saveAsync();

        audit(s, p, "SELL", blocks, payout);

        long newAvail = mgr.getAvailableBlocks(uuid);
        return Result.ok("&aSold &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(payout)
                + "&a. &7(Available: &a" + newAvail + "&7)");
    }

    // -------------------------------------------------------------------------
    // Internal: Deduction rules
    // -------------------------------------------------------------------------

    private boolean deductForSell(ClaimBlockData cbd, long amount, long starter, long used, long spent,
                                 ExchangeSettings s, PlayerState st) {

        long nonStarterConsumed = Math.max(0L, (used + spent) - starter);

        long earned = cbd.getEarnedBlocks();
        long bonus = cbd.getBonusBlocks();
        long bought = cbd.getBoughtBlocks();

        long unlockedBought = Long.MAX_VALUE;
        if (s.sellLockEnabled && "EXCHANGE_PURCHASED_ONLY".equalsIgnoreCase(s.sellLockScope)) {
            long now = System.currentTimeMillis();
            pruneLots(st, now, s.sellLockHoldMinutes);
            long lockedPurchased = sumLockedLots(st, now, s.sellLockHoldMinutes);

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
    // Settings resolution (supports profiles + sane defaults)
    // -------------------------------------------------------------------------

    private ExchangeSettings resolveSettings(Player p) {
        FileConfiguration cfg = plugin.cfg().raw();

        ExchangeSettings s = new ExchangeSettings();

        s.enabled = cfg.getBoolean("claim_blocks.exchange.enabled", false);

        s.requireVaultProvider = cfg.getBoolean("claim_blocks.exchange.require_vault_provider", true);

        s.exchangePerm = cfg.getString("claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange");
        s.buyPerm = cfg.getString("claim_blocks.exchange.permissions.buy", "aegis.claimblocks.buy");
        s.sellPerm = cfg.getString("claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell");
        s.bypassPerm = cfg.getString("claim_blocks.exchange.permissions.bypass_limits", "aegis.claimblocks.exchange.bypass");
        s.bypassSellLockPerm = cfg.getString("claim_blocks.exchange.permissions.bypass_sell_lock", "aegis.claimblocks.selllock.bypass");

        s.sellRequiresPermission = cfg.getBoolean("claim_blocks.exchange.sell_requires_permission.enabled", false);

        s.worldsAllowlistEnabled = cfg.getBoolean("claim_blocks.exchange.worlds_allowed.enabled", false);
        s.worldsAllowed = new HashSet<>(cfg.getStringList("claim_blocks.exchange.worlds_allowed.worlds"));

        s.auditEnabled = cfg.getBoolean("claim_blocks.exchange.audit.enabled", true);
        s.auditConsole = cfg.getBoolean("claim_blocks.exchange.audit.log_to_console", true);

        String profile = cfg.getString("claim_blocks.exchange.profile", "safe_small");
        profile = (profile == null) ? "safe_small" : profile.trim();
        boolean useCustom = profile.equalsIgnoreCase("custom");

        String basePath = "claim_blocks.exchange.";
        String profPath = "claim_blocks.exchange.profiles." + profile + ".";

        s.buyPricePerBlock = readDouble(cfg,
                (useCustom ? basePath : profPath) + "rates.buy_price_per_block",
                basePath + "rates.buy_price_per_block",
                10.0);

        s.sellPricePerBlock = readDouble(cfg,
                (useCustom ? basePath : profPath) + "rates.sell_price_per_block",
                basePath + "rates.sell_price_per_block",
                6.0);

        s.cooldownSeconds = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.cooldown_seconds",
                basePath + "limits.cooldown_seconds",
                10);

        s.maxTradesPerHour = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.max_trades_per_hour",
                basePath + "limits.max_trades_per_hour",
                30);

        s.buyMin = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.min_blocks",
                basePath + "limits.buy.min_blocks",
                1);

        s.buyMaxPerTrade = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.max_blocks_per_trade",
                basePath + "limits.buy.max_blocks_per_trade",
                5000);

        s.buyDailyCapBlocks = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.max_blocks_per_day",
                basePath + "limits.buy.max_blocks_per_day",
                25000);

        s.buyDailyCapMoney = readDouble(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.max_money_per_day",
                basePath + "limits.buy.max_money_per_day",
                0.0);

        s.sellMin = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.min_blocks",
                basePath + "limits.sell.min_blocks",
                1);

        s.sellMaxPerTrade = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.max_blocks_per_trade",
                basePath + "limits.sell.max_blocks_per_trade",
                5000);

        s.sellDailyCapBlocks = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.max_blocks_per_day",
                basePath + "limits.sell.max_blocks_per_day",
                15000);

        s.sellDailyCapMoney = readDouble(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.max_money_per_day",
                basePath + "limits.sell.max_money_per_day",
                0.0);

        s.buyFeePercent = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.buy.percent",
                basePath + "fees.buy.percent",
                2.5);

        s.buyFeeFlat = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.buy.flat",
                basePath + "fees.buy.flat",
                0.0);

        s.sellFeePercent = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.sell.percent",
                basePath + "fees.sell.percent",
                5.0);

        s.sellFeeFlat = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.sell.flat",
                basePath + "fees.sell.flat",
                0.0);

        s.sellLockEnabled = cfg.getBoolean((useCustom ? basePath : profPath) + "sell_lock.enabled",
                cfg.getBoolean(basePath + "sell_lock.enabled", true));

        s.sellLockHoldMinutes = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "sell_lock.hold_minutes",
                basePath + "sell_lock.hold_minutes",
                30);

        s.sellLockScope = cfg.getString((useCustom ? basePath : profPath) + "sell_lock.scope",
                cfg.getString(basePath + "sell_lock.scope", "EXCHANGE_PURCHASED_ONLY"));

        s.sellLockMaxLockedBlocks = readLong(cfg,
                (useCustom ? basePath : profPath) + "sell_lock.max_locked_blocks",
                basePath + "sell_lock.max_locked_blocks",
                0);

        return s;
    }

    private long readLong(FileConfiguration cfg, String path, long def) {
        return cfg.contains(path) ? cfg.getLong(path) : def;
    }

    private long readLong(FileConfiguration cfg, String p1, String p2, long def) {
        if (cfg.contains(p1)) return cfg.getLong(p1);
        if (cfg.contains(p2)) return cfg.getLong(p2);
        return def;
    }

    private double readDouble(FileConfiguration cfg, String p1, String p2, double def) {
        if (cfg.contains(p1)) return cfg.getDouble(p1);
        if (cfg.contains(p2)) return cfg.getDouble(p2);
        return def;
    }

    // -------------------------------------------------------------------------
    // Vault readiness + world + perms
    // -------------------------------------------------------------------------

    private boolean isVaultReady(ExchangeSettings s) {
        if (plugin.cfg() == null) return false;

        boolean useVault = plugin.cfg().raw().getBoolean("economy.use_vault", true);
        boolean vaultEnabled = plugin.cfg().raw().getBoolean("economy.vault.enabled", true);

        if (!useVault || !vaultEnabled) return false;
        if (plugin.vault() == null) return false;

        try {
            boolean enabled = plugin.vault().isEnabled();
            if (!enabled) return false;
        } catch (Throwable ignored) {}

        // If require_vault_provider is true, we rely on vault wrapper being honest.
        // If your VaultHook has a stronger check (provider != null), add it there.
        return true;
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

        if (maxLockedBlocks > 0) {
            long total = 0;
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
                try { file.getParentFile().mkdirs(); file.createNewFile(); }
                catch (IOException e) { e.printStackTrace(); }
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
                } catch (Throwable ignored) {}
            }
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (data == null) data = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection root = data.getConfigurationSection("players");
            if (root == null) root = data.createSection("players");

            for (Map.Entry<UUID, PlayerState> e : cache.entrySet()) {
                String path = e.getKey().toString();
                PlayerState st = e.getValue();

                ConfigurationSection s = root.getConfigurationSection(path);
                if (s == null) s = root.createSection(path);

                s.set("last_trade", st.lastTradeMillis);
                s.set("hour_start", st.hourWindowStartMillis);
                s.set("hour_trades", st.tradesThisHour);

                s.set("day", st.dayKey);
                s.set("bought_today_blocks", st.boughtTodayBlocks);
                s.set("sold_today_blocks", st.soldTodayBlocks);
                s.set("money_spent_today", st.moneySpentToday);
                s.set("money_earned_today", st.moneyEarnedToday);

                s.set("last_any_gain", st.lastAnyGainMillis);

                List<Map<String, Object>> lots = new ArrayList<>();
                for (Lot lot : st.purchaseLots) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("t", lot.timeMillis);
                    m.put("a", lot.amount);
                    lots.add(m);
                }
                s.set("purchase_lots", lots);
            }

            try { data.save(file); }
            catch (IOException ex) { ex.printStackTrace(); }
        }
    }

    private void saveAsync() {
        if (shuttingDown) {
            // During shutdown, save synchronously to ensure data is persisted
            save();
            return;
        }
        plugin.runGlobalAsync(this::save);
    }

    private PlayerState getState(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> {
            PlayerState st = new PlayerState();
            st.dayKey = LocalDate.now().toString();
            st.hourWindowStartMillis = System.currentTimeMillis();
            return st;
        });
    }

    private void audit(ExchangeSettings s, Player p, String side, long blocks, double money) {
        if (!s.auditEnabled) return;
        if (!s.auditConsole) return;
        if (p == null) return;

        try {
            plugin.getLogger().info("[Exchange] " + side + " " + blocks + " blocks by " + p.getName()
                    + " (" + p.getUniqueId() + ") money=" + String.format(Locale.ROOT, "%.2f", money)
                    + " world=" + (p.getWorld() != null ? p.getWorld().getName() : "unknown"));
        } catch (Throwable ignored) {}
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    private static class ExchangeSettings {
        boolean enabled;

        boolean requireVaultProvider;

        String exchangePerm;
        String buyPerm;
        String sellPerm;
        String bypassPerm;
        String bypassSellLockPerm;

        boolean sellRequiresPermission;

        boolean worldsAllowlistEnabled;
        Set<String> worldsAllowed = new HashSet<>();

        boolean auditEnabled;
        boolean auditConsole;

        double buyPricePerBlock;
        double sellPricePerBlock;

        int cooldownSeconds;
        int maxTradesPerHour;

        int buyMin;
        long buyMaxPerTrade;
        long buyDailyCapBlocks;
        double buyDailyCapMoney;

        int sellMin;
        long sellMaxPerTrade;
        long sellDailyCapBlocks;
        double sellDailyCapMoney;

        double buyFeePercent;
        double buyFeeFlat;
        double sellFeePercent;
        double sellFeeFlat;

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
