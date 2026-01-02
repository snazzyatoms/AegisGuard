package com.aegisguard.economy.exchange;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ClaimBlockExchangeConfig {

    public final boolean enabled;
    public final long cooldownSeconds;
    public final double minSpreadPercent; // safety: warn if sell >= buy within spread

    public final TradeSide buy;
    public final TradeSide sell;

    public static final class TradeSide {
        public final boolean enabled;
        public final double pricePerBlock;
        public final double feePercent;     // fee applied to total (buy) or revenue (sell)
        public final long minPerTx;
        public final long maxPerTx;
        public final long dailyCap;         // cap in claimblocks per day (0 = unlimited)

        private TradeSide(boolean enabled, double pricePerBlock, double feePercent,
                          long minPerTx, long maxPerTx, long dailyCap) {
            this.enabled = enabled;
            this.pricePerBlock = pricePerBlock;
            this.feePercent = feePercent;
            this.minPerTx = minPerTx;
            this.maxPerTx = maxPerTx;
            this.dailyCap = dailyCap;
        }
    }

    private ClaimBlockExchangeConfig(boolean enabled, long cooldownSeconds, double minSpreadPercent,
                                    TradeSide buy, TradeSide sell) {
        this.enabled = enabled;
        this.cooldownSeconds = cooldownSeconds;
        this.minSpreadPercent = minSpreadPercent;
        this.buy = buy;
        this.sell = sell;
    }

    public static ClaimBlockExchangeConfig load(FileConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection("claimblocks.exchange");
        if (sec == null) {
            // Default disabled if not present
            return new ClaimBlockExchangeConfig(false, 10L, 0.0,
                    new TradeSide(false, 0.0, 0.0, 1, 0, 0),
                    new TradeSide(false, 0.0, 0.0, 1, 0, 0));
        }

        boolean enabled = sec.getBoolean("enabled", false);
        long cooldown = sec.getLong("cooldown-seconds", 10L);
        double minSpread = sec.getDouble("min-spread-percent", 0.0);

        TradeSide buy = loadSide(sec.getConfigurationSection("buy"), true);
        TradeSide sell = loadSide(sec.getConfigurationSection("sell"), false);

        return new ClaimBlockExchangeConfig(enabled, cooldown, minSpread, buy, sell);
    }

    private static TradeSide loadSide(ConfigurationSection side, boolean defaultEnabled) {
        if (side == null) {
            return new TradeSide(defaultEnabled, 10.0, 0.0, 1, 10000, 0);
        }
        boolean enabled = side.getBoolean("enabled", defaultEnabled);
        double price = side.getDouble("price-per-block", defaultEnabled ? 10.0 : 5.0);
        double fee = side.getDouble("fee-percent", 0.0);
        long min = side.getLong("min-per-tx", 1);
        long max = side.getLong("max-per-tx", 10000);
        long cap = side.getLong("daily-cap", 0);
        return new TradeSide(enabled, price, fee, min, max, cap);
    }
}
