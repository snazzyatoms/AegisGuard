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

    // -------------------------------------------------------------------------
    // Public DTOs (GUI/Commands can use these)
    // -------------------------------------------------------------------------

    public record ExchangeResult(boolean success, String message) {}

    public enum Type {
        OK,
        DISABLED,
        NO_PERMISSION,
        VAULT_UNAVAILABLE,
        WORLD_BLOCKED,
        INVALID_AMOUNT,
        COOLDOWN,
        HOURLY_CAP,
        DAILY_CAP_BLOCKS,
        DAILY_CAP_MONEY,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_BLOCKS,
        SELL_LOCKED,
        ERROR
    }

    /**
     * longA / dblA are optional "extra details" for GUIs:
     * - COOLDOWN: longA = seconds remaining
     * - DAILY_CAP_*: longA = remaining blocks, dblA = remaining money
     * - INSUFFICIENT_FUNDS: dblA = money needed
     */
    public record Result(Type type, String message, long longA, double dblA) {}

    public record Quote(long blocks, double unitPrice, double subtotal, double fee, double totalOrPayout) {}

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

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
    // Public API (Rates + Quotes)
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

        out.add(color("&7Buy: &e" + s.buyPricePerBlock + " &7per block"));
        out.add(color("&7Sell: &e" + s.sellPricePerBlock + " &7per block"));
        out.add(color("&7Fees: &aBuy " + s.buyFeePercent + "% + " + s.buyFeeFlat + " &7| &cSell " + s.sellFeePercent + "% + " + s.sellFeeFlat));

        out.add(color("&7Cooldown: &e" + s.cooldownSeconds + "s &7| Trades/hour: &e" + s.maxTradesPerHour));
        out.add(color("&7Buy limits: &e" + s.buyMin + "-" + s.buyMaxPerTrade + " &7per trade, daily: &e" + s.buyDailyCapBlocks));
        out.add(color("&7Sell limits: &e" + s.sellMin + "-" + s.sellMaxPerTrade + " &7per trade, daily: &e" + s.sellDailyCapBlocks));

        if (s.buyDailyCapMoney > 0.0) out.add(color("&7Buy money cap/day: &e" + money(s.buyDailyCapMoney)));
        if (s.sellDailyCapMoney > 0.0) out.add(color("&7Sell money cap/day: &e" + money(s.sellDailyCapMoney)));

        if (s.sellLockEnabled) {
            out.add(color("&7Sell-lock: &e" + s.sellLockHoldMinutes + "m &7(" + s.sellLockScope + ")"));
        } else {
            out.add(color("&7Sell-lock: &aDisabled"));
        }

        out.add(color("&8&m------------------------"));
        return out;
    }

    public Quote quoteBuy(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);
        long amt = Math.max(0, blocks);

        double baseCost = amt * s.buyPricePerBlock;
        double fee = (baseCost * (s.buyFeePercent / 100.0)) + s.buyFeeFlat;
        double total = baseCost + fee;

        if (!Double.isFinite(total)) total = 0.0;
        if (total < 0.0) total = 0.0;

        return new Quote(amt, s.buyPricePerBlock, baseCost, fee, total);
    }

    public Quote quoteSell(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);
        long amt = Math.max(0, blocks);

        double base = amt * s.sellPricePerBlock;
        double fee = (base * (s.sellFeePercent / 100.0)) + s.sellFeeFlat;
        double payout = Math.max(0.0, base - fee);

        if (!Double.isFinite(payout)) payout = 0.0;

        return new Quote(amt, s.sellPricePerBlock, base, fee, payout);
    }

    // -------------------------------------------------------------------------
    // Backwards compatible API (keeps your original signature)
    // -------------------------------------------------------------------------

    public ExchangeResult buy(Player p, long blocks) {
        Result r = buyDetailed(p, blocks);
        return new ExchangeResult(r.type == Type.OK, r.message);
    }

    public ExchangeResult sell(Player p, long blocks) {
        Result r = sellDetailed(p, blocks);
        return new ExchangeResult(r.type == Type.OK, r.message);
    }

    // -------------------------------------------------------------------------
    // Detailed API (GUI uses this)
    // -------------------------------------------------------------------------

    public Result buyDetailed(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return res(Type.DISABLED, "&cExchange is disabled.", 0, 0);
        if (!isWorldAllowed(p, s)) return res(Type.WORLD_BLOCKED, "&cExchange is not allowed in this world.", 0, 0);
        if (!isVaultReady()) return res(Type.VAULT_UNAVAILABLE, "&cVault economy is not available.", 0, 0);
        if (!hasPerm(p, s.exchangePerm)) return res(Type.NO_PERMISSION, "&cYou do not have permission to use the exchange.", 0, 0);
        if (!hasPerm(p, s.buyPerm)) return res(Type.NO_PERMISSION, "&cYou do not have permission to buy ClaimBlocks.", 0, 0);

        if (blocks <= 0) return res(Type.INVALID_AMOUNT, "&cAmount must be positive.", 0, 0);
        if (blocks < s.buyMin) return res(Type.INVALID_AMOUNT, "&cMinimum buy amount is &e" + s.buyMin + "&c.", 0, 0);
        if (s.buyMaxPerTrade > 0 && blocks > s.buyMaxPerTrade) return res(Type.INVALID_AMOUNT, "&cMaximum buy per trade is &e" + s.buyMaxPerTrade + "&c.", 0, 0);

        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) return res(Type.ERROR, "&cClaimBlocks system is not available.", 0, 0);

        boolean bypass = p.hasPermission(s.bypassPerm);

        PlayerState st = getState(p.getUniqueId());
        normalizeWindows(st);

        long now = System.currentTimeMillis();

        if (!bypass) {
            long remainingCd = cooldownRemainingSeconds(st, now, s.cooldownSeconds);
            if (remainingCd > 0) return res(Type.COOLDOWN, "&cPlease wait &e" + remainingCd + "s &cbefore trading again.", remainingCd, 0);

            if (s.maxTradesPerHour > 0 && st.tradesThisHour >= s.maxTradesPerHour) {
                return res(Type.HOURLY_CAP, "&cHourly trade limit reached. Try again later.", 0, 0);
            }

            if (s.buyDailyCapBlocks > 0 && st.boughtTodayBlocks + blocks > s.buyDailyCapBlocks) {
                long left = Math.max(0, s.buyDailyCapBlocks - st.boughtTodayBlocks);
                return res(Type.DAILY_CAP_BLOCKS, "&cDaily buy cap reached. You can only buy &e" + left + " &cmore today.", left, 0);
            }
        }

        Quote q = quoteBuy(p, blocks);
        double total = q.totalOrPayout();

        if (!bypass && s.buyDailyCapMoney > 0.0 && (st.moneySpentToday + total) > s.buyDailyCapMoney) {
            double leftMoney = Math.max(0.0, s.buyDailyCapMoney - st.moneySpentToday);
            return res(Type.DAILY_CAP_MONEY, "&cDaily buy money cap reached. Remaining today: &e" + money(leftMoney), 0, leftMoney);
        }

        if (!plugin.vault().has(p, total)) {
            double needed = Math.max(0.0, total - plugin.vault().balance(p));
            return res(Type.INSUFFICIENT_FUNDS, "&cYou don't have enough money. Cost: &e" + plugin.vault().format(total), 0, needed);
        }

        if (!plugin.vault().charge(p, total)) {
            return res(Type.ERROR, "&cPayment failed. Please try again.", 0, 0);
        }

        // Add bought blocks (non-starter)
        mgr.addBought(p.getUniqueId(), blocks);

        // Update state
        st.lastTradeMillis = now;
        st.tradesThisHour++;
        st.boughtTodayBlocks += blocks;
        st.moneySpentToday += total;
        st.lastAnyGainMillis = now;

        // Sell lock tracking for purchased blocks
        addPurchaseLot(st, blocks, now, s.sellLockMaxLockedBlocks);

        audit(p, "BUY", blocks, total);

        saveAsync();
        mgr.saveAsync();

        long newAvail = mgr.getAvailableBlocks(p.getUniqueId());
        return res(Type.OK,
                "&aPurchased &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(total)
                        + "&a. &7(Available: &a" + newAvail + "&7)",
                0, 0
        );
    }

    public Result sellDetailed(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return res(Type.DISABLED, "&cExchange is disabled.", 0, 0);
        if (!isWorldAllowed(p, s)) return res(Type.WORLD_BLOCKED, "&cExchange is not allowed in this world.", 0, 0);
        if (!isVaultReady()) return res(Type.VAULT_UNAVAILABLE, "&cVault economy is not available.", 0, 0);
        if (!hasPerm(p, s.exchangePerm)) return res(Type.NO_PERMISSION, "&cYou do not have permission to use the exchange.", 0, 0);

        if (s.sellRequiresPermission && !hasPerm(p, s.sellPerm)) {
            return res(Type.NO_PERMISSION, "&cYou do not have permission to sell ClaimBlocks.", 0, 0);
        }

        if (blocks <= 0) return res(Type.INVALID_AMOUNT, "&cAmount must be positive.", 0, 0);
        if (blocks < s.sellMin) return res(Type.INVALID_AMOUNT, "&cMinimum sell amount is &e" + s.sellMin + "&c.", 0, 0);
        if (s.sellMaxPerTrade > 0 && blocks > s.sellMaxPerTrade) return res(Type.INVALID_AMOUNT, "&cMaximum sell per trade is &e" + s.sellMaxPerTrade + "&c.", 0, 0);

        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) return res(Type.ERROR, "&cClaimBlocks system is not available.", 0, 0);

        boolean bypass = p.hasPermission(s.bypassPerm);
        boolean bypassLock = p.hasPermission(s.bypassSellLockPerm) || bypass;

        PlayerState st = getState(p.getUniqueId());
        normalizeWindows(st);

        long now = System.currentTimeMillis();

        if (!bypass) {
            long remainingCd = cooldownRemainingSeconds(st, now, s.cooldownSeconds);
            if (remainingCd > 0) return res(Type.COOLDOWN, "&cPlease wait &e" + remainingCd + "s &cbefore trading again.", remainingCd, 0);

            if (s.maxTradesPerHour > 0 && st.tradesThisHour >= s.maxTradesPerHour) {
                return res(Type.HOURLY_CAP, "&cHourly trade limit reached. Try again later.", 0, 0);
            }

            if (s.sellDailyCapBlocks > 0 && st.soldTodayBlocks + blocks > s.sellDailyCapBlocks) {
                long left = Math.max(0, s.sellDailyCapBlocks - st.soldTodayBlocks);
                return res(Type.DAILY_CAP_BLOCKS, "&cDaily sell cap reached. You can only sell &e" + left + " &cmore today.", left, 0);
            }
        }

        // Sellable balance calculation: never allow selling "starter" capacity
        UUID uuid = p.getUniqueId();
        ClaimBlockData cbd = mgr.getOrCreate(uuid);

        long starter = plugin.cfg().raw().getLong("claim_blocks.starting_blocks", 0);
        long used = mgr.getUsedBlocks(uuid);
        long spent = mgr.getSpentBlocks(uuid);

        long nonStarterTotal = cbd.getTotalNonStarter();
        long nonStarterConsumed = Math.max(0L, (used + spent) - starter);
        long nonStarterRemaining = Math.max(0L, nonStarterTotal - nonStarterConsumed);

        if (nonStarterRemaining < blocks) {
            return res(Type.INSUFFICIENT_BLOCKS,
                    "&cYou only have &e" + nonStarterRemaining + " &csellable ClaimBlocks (starter blocks cannot be sold).",
                    nonStarterRemaining, 0
            );
        }

        // Sell-lock enforcement
        if (s.sellLockEnabled && !bypassLock) {
            if ("ALL".equalsIgnoreCase(s.sellLockScope)) {
                long holdMs = s.sellLockHoldMinutes * 60_000L;
                long until = st.lastAnyGainMillis + holdMs;
                if (now < until) {
                    long secs = Math.max(1, (until - now + 999) / 1000);
                    return res(Type.SELL_LOCKED, "&cSell-lock active. Try again in &e" + secs + "s&c.", secs, 0);
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
                    return res(Type.SELL_LOCKED,
                            "&cSell-lock active. You can sell &e" + allowed + " &cnow. Next unlock in ~&e" + next + "s&c.",
                            next, 0
                    );
                }
            }
        }

        Quote q = quoteSell(p, blocks);
        double payout = q.totalOrPayout();

        if (!Double.isFinite(payout) || payout <= 0.0) {
            return res(Type.ERROR, "&cSell payout is invalid. Check exchange prices/fees.", 0, 0);
        }

        if (!bypass && s.sellDailyCapMoney > 0.0 && (st.moneyEarnedToday + payout) > s.sellDailyCapMoney) {
            double leftMoney = Math.max(0.0, s.sellDailyCapMoney - st.moneyEarnedToday);
            return res(Type.DAILY_CAP_MONEY, "&cDaily sell money cap reached. Remaining today: &e" + money(leftMoney), 0, leftMoney);
        }

        if (!deductForSell(cbd, blocks, starter, used, spent, s, st)) {
            return res(Type.SELL_LOCKED, "&cUnable to process sell. (Sell-lock or balance restriction)", 0, 0);
        }

        mgr.saveAsync();

        plugin.vault().give(p, payout);

        st.lastTradeMillis = now;
        st.tradesThisHour++;
        st.soldTodayBlocks += blocks;
        st.moneyEarnedToday += payout;

        audit(p, "SELL", blocks, payout);

        saveAsync();

        long newAvail = mgr.getAvailableBlocks(uuid);
        return res(Type.OK,
                "&aSold &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(payout)
                        + "&a. &7(Available: &a" + newAvail + "&7)",
                0, 0
        );
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
    // Settings resolution
    // -------------------------------------------------------------------------

    private ExchangeSettings resolveSettings(Player p) {
        FileConfiguration cfg = plugin.cfg().raw();

        ExchangeSettings s = new ExchangeSettings();

        s.enabled = cfg.getBoolean("claim_blocks.exchange.enabled", false);

        s.exchangePerm = cfg.getString("claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange");
        s.buyPerm = cfg.getString("claim_blocks.exchange.permissions.buy", "aegis.claimblocks.buy");
        s.sellPerm = cfg.getString("claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell");
        s.bypassPerm = cfg.getString("claim_blocks.exchange.permissions.bypass_limits", "aegis.claimblocks.exchange.bypass");
        s.bypassSellLockPerm = cfg.getString("claim_blocks.exchange.permissions.bypass_sell_lock", "aegis.claimblocks.selllock.bypass");

        s.sellRequiresPermission = cfg.getBoolean("claim_blocks.exchange.sell_requires_permission.enabled", false);

        s.worldsAllowlistEnabled = cfg.getBoolean("claim_blocks.exchange.worlds_allowed.enabled", false);
        s.worldsAllowed = new HashSet<>(cfg.getStringList("claim_blocks.exchange.worlds_allowed.worlds"));

        s.auditEnabled = cfg.getBoolean("claim_blocks.exchange.audit.enabled", true);
        s.auditToConsole = cfg.getBoolean("claim_blocks.exchange.audit.log_to_console", true);

        String profile = cfg.getString("claim_blocks.exchange.profile", "safe_small");
        profile = (profile == null) ? "safe_small" : profile.trim();
        boolean useCustom = profile.equalsIgnoreCase("custom");

        String basePath = "claim_blocks.exchange.";
        String profPath = "claim_blocks.exchange.profiles." + profile + ".";

        s.buyPricePerBlock = readDouble(cfg,
                (useCustom ? basePath : profPath) + "rates.buy_price_per_block",
                basePath + "rates.buy_price_per_block",
                (useCustom ? basePath : profPath) + "buy.price_per_block",
                basePath + "buy.price_per_block",
                10.0);

        s.sellPricePerBlock = readDouble(cfg,
                (useCustom ? basePath : profPath) + "rates.sell_price_per_block",
                basePath + "rates.sell_price_per_block",
                (useCustom ? basePath : profPath) + "sell.price_per_block",
                basePath + "sell.price_per_block",
                6.0);

        s.cooldownSeconds = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.cooldown_seconds",
                basePath + "limits.cooldown_seconds",
                (useCustom ? basePath : profPath) + "cooldown_seconds",
                basePath + "cooldown_seconds",
                10);

        s.maxTradesPerHour = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.max_trades_per_hour",
                basePath + "limits.max_trades_per_hour",
                30);

        s.buyMin = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.min_blocks",
                basePath + "limits.buy.min_blocks",
                (useCustom ? basePath : profPath) + "buy.min_per_tx",
                basePath + "buy.min_per_tx",
                1);

        s.buyMaxPerTrade = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.max_blocks_per_trade",
                basePath + "limits.buy.max_blocks_per_trade",
                (useCustom ? basePath : profPath) + "buy.max_per_tx",
                basePath + "buy.max_per_tx",
                5000);

        s.buyDailyCapBlocks = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.max_blocks_per_day",
                basePath + "limits.buy.max_blocks_per_day",
                (useCustom ? basePath : profPath) + "buy.daily_cap",
                basePath + "buy.daily_cap",
                25000);

        s.buyDailyCapMoney = readDouble(cfg,
                (useCustom ? basePath : profPath) + "limits.buy.max_money_per_day",
                basePath + "limits.buy.max_money_per_day",
                0.0);

        s.sellMin = (int) readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.min_blocks",
                basePath + "limits.sell.min_blocks",
                (useCustom ? basePath : profPath) + "sell.min_per_tx",
                basePath + "sell.min_per_tx",
                1);

        s.sellMaxPerTrade = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.max_blocks_per_trade",
                basePath + "limits.sell.max_blocks_per_trade",
                (useCustom ? basePath : profPath) + "sell.max_per_tx",
                basePath + "sell.max_per_tx",
                5000);

        s.sellDailyCapBlocks = readLong(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.max_blocks_per_day",
                basePath + "limits.sell.max_blocks_per_day",
                (useCustom ? basePath : profPath) + "sell.daily_cap",
                basePath + "sell.daily_cap",
                15000);

        s.sellDailyCapMoney = readDouble(cfg,
                (useCustom ? basePath : profPath) + "limits.sell.max_money_per_day",
                basePath + "limits.sell.max_money_per_day",
                0.0);

        s.buyFeePercent = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.buy.percent",
                basePath + "fees.buy.percent",
                (useCustom ? basePath : profPath) + "buy.fee_percent",
                basePath + "buy.fee_percent",
                2.5);

        s.buyFeeFlat = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.buy.flat",
                basePath + "fees.buy.flat",
                0.0);

        s.sellFeePercent = readDouble(cfg,
                (useCustom ? basePath : profPath) + "fees.sell.percent",
                basePath + "fees.sell.percent",
                (useCustom ? basePath : profPath) + "sell.fee_percent",
                basePath + "sell.fee_percent",
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

    private long readLong(FileConfiguration cfg, String p1, String p2, String p3, String p4, long def) {
        if (cfg.contains(p1)) return cfg.getLong(p1);
        if (cfg.contains(p2)) return cfg.getLong(p2);
        if (cfg.contains(p3)) return cfg.getLong(p3);
        if (cfg.contains(p4)) return cfg.getLong(p4);
        return def;
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
    // State windows
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void shutdown() {
        try { save(); } catch (Throwable ignored) {}
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
    // Audit
    // -------------------------------------------------------------------------

    private void audit(Player p, String side, long blocks, double money) {
        ExchangeSettings s = resolveSettings(p);
        if (!s.auditEnabled || !s.auditToConsole) return;
        try {
            plugin.getLogger().info("[Exchange] " + side + " player=" + p.getName() + " uuid=" + p.getUniqueId()
                    + " blocks=" + blocks + " money=" + money);
        } catch (Throwable ignored) {}
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private Result res(Type type, String msg, long longA, double dblA) {
        return new Result(type, color(msg), longA, dblA);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private String money(double amt) {
        if (plugin.vault() != null) return plugin.vault().format(amt);
        return String.format("$%,.2f", amt);
    }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    private static class ExchangeSettings {
        boolean enabled;

        String exchangePerm;
        String buyPerm;
        String sellPerm;
        String bypassPerm;
        String bypassSellLockPerm;

        boolean sellRequiresPermission;

        boolean worldsAllowlistEnabled;
        Set<String> worldsAllowed = new HashSet<>();

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

        boolean auditEnabled;
        boolean auditToConsole;
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
