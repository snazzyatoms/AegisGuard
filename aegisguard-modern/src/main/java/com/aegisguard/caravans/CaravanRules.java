package com.aegisguard.caravans;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Pure caravan math: distance, travel time, risk, weighted events, charge, and settlement.
 * Kept Bukkit-free so route/payout behaviour can be unit-tested.
 */
public final class CaravanRules {

    private CaravanRules() {}

    public enum Risk {
        LOW, MEDIUM, HIGH;

        public static Risk parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) return LOW;
            try {
                return Risk.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return LOW;
            }
        }
    }

    public enum Event {
        SAFE, AMBUSH, TOLL, BOON, DELAY;

        public static Event parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) return SAFE;
            try {
                return Event.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return SAFE;
            }
        }
    }

    public record Quote(double cargo, double fee, double insurancePremium, double charged) {
        public boolean isFree() {
            return charged <= 0.0D;
        }
    }

    public record Settlement(
            boolean failed,
            double deliveredCargo,
            double profit,
            double ownerToll,
            double escortCut,
            double merchantPayout,
            double refund
    ) {
        public static Settlement failRefund(double refund) {
            return new Settlement(true, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, Math.max(0.0D, refund));
        }
    }

    public static int chebyshev(int x1, int z1, int x2, int z2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
    }

    public static long travelTimeMs(int distance, long msPerBlock, long minMs, long maxMs) {
        long raw = Math.max(0, distance) * Math.max(1L, msPerBlock);
        if (raw < minMs) raw = minMs;
        if (maxMs > 0L && raw > maxMs) raw = maxMs;
        return raw;
    }

    public static Risk riskForDistance(int distance, int mediumAt, int highAt) {
        if (distance >= Math.max(mediumAt, highAt) && highAt > 0) return Risk.HIGH;
        if (highAt > 0 && distance >= highAt) return Risk.HIGH;
        if (mediumAt > 0 && distance >= mediumAt) return Risk.MEDIUM;
        return Risk.LOW;
    }

    /**
     * {@code roll} is 0-99 inclusive. Remaining weight after ambush/toll/boon/delay is SAFE.
     * Escort divides ambush weight (rounded down, minimum 0).
     */
    public static Event rollEvent(int roll, int ambushWeight, int tollWeight, int boonWeight,
                                  int delayWeight, boolean escorted, int escortAmbushDivisor) {
        int ambush = Math.max(0, ambushWeight);
        if (escorted) {
            int div = Math.max(1, escortAmbushDivisor);
            ambush = ambush / div;
        }
        int toll = Math.max(0, tollWeight);
        int boon = Math.max(0, boonWeight);
        int delay = Math.max(0, delayWeight);
        int cursor = 0;
        int value = Math.floorMod(roll, 100);
        cursor += ambush;
        if (value < cursor) return Event.AMBUSH;
        cursor += toll;
        if (value < cursor) return Event.TOLL;
        cursor += boon;
        if (value < cursor) return Event.BOON;
        cursor += delay;
        if (value < cursor) return Event.DELAY;
        return Event.SAFE;
    }

    public static Quote quote(double cargoValue, double feeRate, double minFee,
                              double insuranceRate, boolean insured) {
        double cargo = clampMoney(cargoValue);
        double fee = Math.max(minFee, cargo * Math.max(0.0D, feeRate));
        fee = clampMoney(fee);
        double premium = insured ? clampMoney(cargo * Math.max(0.0D, insuranceRate)) : 0.0D;
        return new Quote(cargo, fee, premium, clampMoney(cargo + fee + premium));
    }

    /**
     * Charge-then-deliver settlement. Ambush without escort or insurance loses cargo
     * and refunds fee+premium only. Insured or escorted ambush delivers remaining cargo.
     */
    public static Settlement settle(double cargoValue, Quote quote, Event event,
                                    boolean escorted, boolean insured,
                                    double ambushLossRate, double boonBonusRate,
                                    double tollRate, double escortCutRate) {
        if (quote == null) quote = new Quote(cargoValue, 0.0D, 0.0D, cargoValue);
        Event resolved = event == null ? Event.SAFE : event;
        if (resolved == Event.AMBUSH && !escorted && !insured) {
            return Settlement.failRefund(quote.fee() + quote.insurancePremium());
        }

        double cargo = clampMoney(cargoValue);
        if (resolved == Event.AMBUSH) {
            cargo = clampMoney(cargo * (1.0D - clampUnit(ambushLossRate) * 0.35D));
        }
        double profit = resolved == Event.BOON ? clampMoney(cargo * Math.max(0.0D, boonBonusRate)) : 0.0D;
        double gross = cargo + profit;
        double extraToll = resolved == Event.TOLL ? clampMoney(gross * Math.max(0.0D, tollRate)) : 0.0D;
        double baseToll = clampMoney(gross * Math.max(0.0D, tollRate));
        double ownerToll = clampMoney(baseToll + extraToll);
        if (ownerToll > gross) ownerToll = gross;
        double remaining = clampMoney(gross - ownerToll);
        double escortCut = escorted ? clampMoney(remaining * clampUnit(escortCutRate)) : 0.0D;
        if (escortCut > remaining) escortCut = remaining;
        double merchant = clampMoney(remaining - escortCut);
        return new Settlement(false, cargo, profit, ownerToll, escortCut, merchant, 0.0D);
    }

    public static boolean shouldComplete(long etaAt, long nowMs) {
        return etaAt > 0L && nowMs >= etaAt;
    }

    public static boolean canCancel(long dispatchedAt, long etaAt, long nowMs, double cancelProgress) {
        if (etaAt <= dispatchedAt) return nowMs <= dispatchedAt;
        double span = etaAt - dispatchedAt;
        double progress = (nowMs - dispatchedAt) / span;
        return progress < clampUnit(cancelProgress);
    }

    public static long remainingCooldownMs(long lastDispatchAt, long nowMs, long cooldownMs) {
        if (lastDispatchAt <= 0L || cooldownMs <= 0L) return 0L;
        return Math.max(0L, (lastDispatchAt + cooldownMs) - nowMs);
    }

    public static boolean sameHop(UUID originId, UUID destId) {
        return originId != null && originId.equals(destId);
    }

    public static double clampMoney(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        return Math.round(amount * 100.0D) / 100.0D;
    }

    public static double clampUnit(double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.min(1.0D, Math.max(0.0D, value));
    }
}
