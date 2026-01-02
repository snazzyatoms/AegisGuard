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

        out.add(color("&7Buy: &e" + s.buyPricePerBlock + " &7per block"));
        out.add(color("&7Sell: &e" + s.sellPricePerBlock + " &7per block"));
        out.add(color("&7Fees: &aBuy " + s.buyFeePercent + "% + " + s.buyFeeFlat + " &7| &cSell " + s.sellFeePercent + "% + " + s.sellFeeFlat));

        out.add(color("&7Cooldown: &e" + s.cooldownSeconds + "s &7| Trades/hour: &e" + s.maxTradesPerHour));
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
        normalizeWindows(st);

        long now = System.currentTimeMillis();

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

        double baseCost = blocks * s.buyPricePerBlock;
        double fee = (baseCost * (s.buyFeePercent / 100.0)) + s.buyFeeFlat;
        double total = baseCost + fee;

        if (!plugin.vault().has(p, total)) {
            return fail("&cYou don't have enough money. Cost: &e" + plugin.vault().format(total));
        }

        if (!plugin.vault().charge(p, total)) {
            return fail("&cPayment failed. Please try again.");
        }

        mgr.addBought(p.getUniqueId(), blocks);

        // Update state
        st.lastTradeMillis = now;
        st.tradesThisHour++;
        st.boughtTodayBlocks += blocks;
        st.moneySpentToday += total;
        st.lastAnyGainMillis = now;

        // Sell lock tracking for purchased blocks
        addPurchaseLot(st, blocks, now, s.sellLockMaxLockedBlocks);

        saveAsync();

        long newAvail = mgr.getAvailableBlocks(p.getUniqueId());
        return ok("&aPurchased &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(total)
                + "&a. &7(Available: &a" + newAvail + "&7)");
    }

    public ExchangeResult sell(Player p, long blocks) {
        ExchangeSettings s = resolveSettings(p);

        if (!s.enabled) return fail("&cExchange is disabled.");
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

        PlayerState st = getState(p.getUniqueId());
        normalizeWindows(st);

        long now = System.currentTimeMillis();

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
            return fail("&cYou only have &e" + nonStarterRemaining + " &csellable ClaimBlocks (starter blocks cannot be sold).");
        }

        // Sell-lock enforcement
        if (s.sellLockEnabled && !bypassLock) {
            if ("ALL".equalsIgnoreCase(s.sellLockScope)) {
                long holdMs = s.sellLockHoldMinutes * 60_000L;
                long until = st.lastAnyGainMillis + holdMs;
                if (now < until) {
                    long secs = Math.max(1, (until - now + 999) / 1000);
                    return fail("&cSell-lock active. Try again in &e" + secs + "s&c.");
                }
            } else {
                pruneLots(st, now, s.sellLockHoldMinutes);

                long lockedPurchased = sumLockedLots(st, now, s.sellLockHoldMinutes);

                // Fairness: assume any over-starter consumption hits bought blocks first,
                // so locked purchased can't exceed bought remaining after that consumption.
                long bought = cbd.getBoughtBlocks();
                long purchasedRemainingAfterConsumption = Math.max(0L, bought - nonStarterConsumed);
                long effectiveLocked = Math.min(lockedPurchased, purchasedRemainingAfterConsumption);

                long allowed = Math.max(0L, nonStarterRemaining - effectiveLocked);

                if (blocks > allowed) {
                    long next = nextUnlockSeconds(st, now, s.sellLockHoldMinutes);
                    if (next <= 0) next = (s.sellLockHoldMinutes * 60L);
                    return fail("&cSell-lock active. You can sell &e" + allowed + " &cnow. Next unlock in ~&e" + next + "s&c.");
                }
            }
        }

        double base = blocks * s.sellPricePerBlock;
        double fee = (base * (s.sellFeePercent / 100.0)) + s.sellFeeFlat;
        double payout = Math.max(0.0, base - fee);

        if (!Double.isFinite(payout) || payout <= 0.0) {
            return fail("&cSell payout is invalid. Check exchange prices/fees.");
        }

        // Deduct blocks: earned -> bonus -> bought (but never sell locked purchased)
        if (!deductForSell(cbd, blocks, starter, used, spent, s, st)) {
            return fail("&cUnable to process sell. (Sell-lock or balance restriction)");
        }

        mgr.saveAsync();

        plugin.vault().give(p, payout);

        st.lastTradeMillis = now;
        st.tradesThisHour++;
        st.soldTodayBlocks += blocks;
        st.moneyEarnedToday += payout;

        saveAsync();

        long newAvail = mgr.getAvailableBlocks(uuid);
        return ok("&aSold &e" + blocks + " &aClaimBlocks for &6" + plugin.vault().format(payout)
                + "&a. &7(Available: &a" + newAvail + "&7)");
    }

    // -------------------------------------------------------------------------
    // Internal: Deduction rules
    // -------------------------------------------------------------------------

    private boolean deductForSell(ClaimBlockData cbd, long amount, long starter, long used, long spent,
                                 ExchangeSettings s, PlayerState st) {

        long nonStarterConsumed = Math.max(0L, (used + spent) - starter);

        // Remaining nonstarter was already checked in sell()
        long earned = cbd.getEarnedBlocks();
        long bonus = cbd.getBonusBlocks();
        long bought = cbd.getBoughtBlocks();

        // Determine how many bought blocks are unlocked (for EXCHANGE_PURCHASED_ONLY lock)
        long unlockedBought = Long.MAX_VALUE;
        if (s.sellLockEnabled && "EXCHANGE_PURCHASED_ONLY".equalsIgnoreCase(s.sellLockScope)) {
            pruneLots(st, System.currentTimeMillis(), s.sellLockHoldMinutes);
            long lockedPurchased = sumLockedLots(st, System.currentTimeMillis(), s.sellLockHoldMinutes);

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
    // Settings resolution (supports profiles + sane defaults)
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

        String profile = cfg.getString("claim_blocks.exchange.profile", "safe_small");
        profile = (profile == null) ? "safe_small" : profile.trim();
        boolean useCustom = profile.equalsIgnoreCase("custom");

        String basePath = "claim_blocks.exchange.";
        String profPath = "claim_blocks.exchange.profiles." + profile + ".";

        // Rates
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

        // Limits
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

        // Fees
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

        // Sell lock
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

                    // Reduce this lot by overflow, or remove it
                    long newAmt = lot.amount - overflow;
                    if (newAmt <= 0) {
                        it.remove();
                        total -= lot.amount;
                    } else {
                        it.set(new Lot(lot.timeMillis, newAmt));
                        total -= overflow;
                    }
                    // Remove any older lots entirely
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

        int sellMin;
        long sellMaxPerTrade;
        long sellDailyCapBlocks;

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
