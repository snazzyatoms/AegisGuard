package com.aegisguard.economy.exchange;

import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ClaimBlockExchangeService {

    public enum Action { BUY, SELL }

    public enum Status {
        SUCCESS,
        FEATURE_DISABLED,
        SIDE_DISABLED,
        VAULT_UNAVAILABLE,
        INVALID_AMOUNT,
        BELOW_MIN,
        ABOVE_MAX,
        COOLDOWN_ACTIVE,
        DAILY_CAP_REACHED,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_CLAIMBLOCKS
    }

    public static final class Result {
        public final Status status;
        public final Action action;

        public final long blocks;
        public final double money; // positive = paid to player (sell payout), negative = charged (buy total)
        public final long cooldownRemainingSeconds;
        public final long dailyRemaining; // remaining claimblocks in cap, -1 if unlimited

        private Result(Status status, Action action, long blocks, double money,
                       long cooldownRemainingSeconds, long dailyRemaining) {
            this.status = status;
            this.action = action;
            this.blocks = blocks;
            this.money = money;
            this.cooldownRemainingSeconds = cooldownRemainingSeconds;
            this.dailyRemaining = dailyRemaining;
        }

        public static Result ok(Action action, long blocks, double money) {
            return new Result(Status.SUCCESS, action, blocks, money, 0, -1);
        }

        public static Result fail(Status status, Action action, long blocks, long cd, long dailyRemaining) {
            return new Result(status, action, blocks, 0.0, cd, dailyRemaining);
        }
    }

    private final ClaimBlockExchangeConfig cfg;
    private final MoneyProvider money;
    private final ClaimBlockAccount claimBlocks;
    private final ExchangeStateStore stateStore;

    public ClaimBlockExchangeService(ClaimBlockExchangeConfig cfg,
                                    MoneyProvider money,
                                    ClaimBlockAccount claimBlocks,
                                    ExchangeStateStore stateStore) {
        this.cfg = cfg;
        this.money = money;
        this.claimBlocks = claimBlocks;
        this.stateStore = stateStore;
    }

    public Result buy(Player player, long blocks) {
        return trade(player, blocks, Action.BUY);
    }

    public Result sell(Player player, long blocks) {
        return trade(player, blocks, Action.SELL);
    }

    private Result trade(Player player, long blocks, Action action) {
        if (!cfg.enabled) return Result.fail(Status.FEATURE_DISABLED, action, blocks, 0, -1);
        if (!money.isAvailable()) return Result.fail(Status.VAULT_UNAVAILABLE, action, blocks, 0, -1);

        ClaimBlockExchangeConfig.TradeSide side = (action == Action.BUY) ? cfg.buy : cfg.sell;
        if (!side.enabled) return Result.fail(Status.SIDE_DISABLED, action, blocks, 0, -1);

        if (blocks <= 0) return Result.fail(Status.INVALID_AMOUNT, action, blocks, 0, -1);
        if (blocks < side.minPerTx) return Result.fail(Status.BELOW_MIN, action, blocks, 0, remainingDaily(player, action, side));
        if (side.maxPerTx > 0 && blocks > side.maxPerTx) return Result.fail(Status.ABOVE_MAX, action, blocks, 0, remainingDaily(player, action, side));

        // cooldown
        ExchangeStateStore.State st = stateStore.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldownMs = cfg.cooldownSeconds * 1000L;
        if (cooldownMs > 0 && (now - st.lastTradeMillis) < cooldownMs) {
            long remaining = (cooldownMs - (now - st.lastTradeMillis) + 999) / 1000;
            return Result.fail(Status.COOLDOWN_ACTIVE, action, blocks, remaining, remainingDaily(player, action, side));
        }

        // daily caps (in claimblocks)
        if (side.dailyCap > 0) {
            long used = (action == Action.BUY) ? st.boughtToday : st.soldToday;
            long remaining = Math.max(0, side.dailyCap - used);
            if (blocks > remaining) {
                return Result.fail(Status.DAILY_CAP_REACHED, action, blocks, 0, remaining);
            }
        }

        if (action == Action.BUY) {
            // money -> claimblocks
            double base = side.pricePerBlock * blocks;
            double fee = base * (side.feePercent / 100.0);
            double total = roundMoney(base + fee);

            if (!money.has(player, total)) {
                return Result.fail(Status.INSUFFICIENT_FUNDS, action, blocks, 0, remainingDaily(player, action, side));
            }
            if (!money.withdraw(player, total)) {
                return Result.fail(Status.INSUFFICIENT_FUNDS, action, blocks, 0, remainingDaily(player, action, side));
            }

            claimBlocks.deposit(player.getUniqueId(), blocks);

            st.lastTradeMillis = now;
            st.boughtToday += blocks;

            return Result.ok(Action.BUY, blocks, -total);
        } else {
            // claimblocks -> money
            if (claimBlocks.getBalance(player.getUniqueId()) < blocks) {
                return Result.fail(Status.INSUFFICIENT_CLAIMBLOCKS, action, blocks, 0, remainingDaily(player, action, side));
            }

            double base = side.pricePerBlock * blocks;
            double fee = base * (side.feePercent / 100.0);
            double payout = roundMoney(Math.max(0.0, base - fee));

            if (!claimBlocks.withdraw(player.getUniqueId(), blocks)) {
                return Result.fail(Status.INSUFFICIENT_CLAIMBLOCKS, action, blocks, 0, remainingDaily(player, action, side));
            }

            if (!money.deposit(player, payout)) {
                // If deposit fails, refund claimblocks to avoid loss.
                claimBlocks.deposit(player.getUniqueId(), blocks);
                return Result.fail(Status.VAULT_UNAVAILABLE, action, blocks, 0, remainingDaily(player, action, side));
            }

            st.lastTradeMillis = now;
            st.soldToday += blocks;

            return Result.ok(Action.SELL, blocks, payout);
        }
    }

    private long remainingDaily(Player player, Action action, ClaimBlockExchangeConfig.TradeSide side) {
        if (side.dailyCap <= 0) return -1;
        ExchangeStateStore.State st = stateStore.get(player.getUniqueId());
        long used = (action == Action.BUY) ? st.boughtToday : st.soldToday;
        return Math.max(0, side.dailyCap - used);
    }

    private static double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
