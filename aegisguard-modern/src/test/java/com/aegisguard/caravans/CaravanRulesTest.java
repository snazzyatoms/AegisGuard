package com.aegisguard.caravans;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaravanRulesTest {

    @Test
    void distanceAndTravelTimeClamp() {
        assertEquals(100, CaravanRules.chebyshev(0, 0, 100, 40));
        assertEquals(5_000L, CaravanRules.travelTimeMs(1, 50L, 5_000L, 600_000L));
        assertEquals(25_000L, CaravanRules.travelTimeMs(500, 50L, 5_000L, 600_000L));
        assertEquals(10_000L, CaravanRules.travelTimeMs(9_999, 50L, 1_000L, 10_000L));
    }

    @Test
    void riskBandsFollowDistanceThresholds() {
        assertEquals(CaravanRules.Risk.LOW, CaravanRules.riskForDistance(10, 250, 800));
        assertEquals(CaravanRules.Risk.MEDIUM, CaravanRules.riskForDistance(250, 250, 800));
        assertEquals(CaravanRules.Risk.HIGH, CaravanRules.riskForDistance(800, 250, 800));
    }

    @Test
    void weightedEventsCoverEachBucketAndEscortCutsAmbush() {
        assertEquals(CaravanRules.Event.AMBUSH, CaravanRules.rollEvent(0, 15, 15, 10, 10, false, 2));
        assertEquals(CaravanRules.Event.TOLL, CaravanRules.rollEvent(15, 15, 15, 10, 10, false, 2));
        assertEquals(CaravanRules.Event.BOON, CaravanRules.rollEvent(30, 15, 15, 10, 10, false, 2));
        assertEquals(CaravanRules.Event.DELAY, CaravanRules.rollEvent(40, 15, 15, 10, 10, false, 2));
        assertEquals(CaravanRules.Event.SAFE, CaravanRules.rollEvent(50, 15, 15, 10, 10, false, 2));
        assertEquals(CaravanRules.Event.TOLL, CaravanRules.rollEvent(7, 15, 15, 10, 10, true, 2));
    }

    @Test
    void quoteChargesCargoFeeAndOptionalInsurance() {
        CaravanRules.Quote uninsured = CaravanRules.quote(100.0D, 0.05D, 1.0D, 0.15D, false);
        assertEquals(100.0D, uninsured.cargo());
        assertEquals(5.0D, uninsured.fee());
        assertEquals(0.0D, uninsured.insurancePremium());
        assertEquals(105.0D, uninsured.charged());
        CaravanRules.Quote insured = CaravanRules.quote(100.0D, 0.05D, 1.0D, 0.15D, true);
        assertEquals(15.0D, insured.insurancePremium());
        assertEquals(120.0D, insured.charged());
    }

    @Test
    void ambushWithoutProtectionFailsAndRefundsFeeOnly() {
        CaravanRules.Quote quote = CaravanRules.quote(100.0D, 0.05D, 1.0D, 0.15D, false);
        CaravanRules.Settlement lost = CaravanRules.settle(100.0D, quote, CaravanRules.Event.AMBUSH,
                false, false, 1.0D, 0.20D, 0.05D, 0.10D);
        assertTrue(lost.failed());
        assertEquals(5.0D, lost.refund());
        assertEquals(0.0D, lost.merchantPayout());
    }

    @Test
    void insuredAmbushAndSafePassagePayMerchantAndToll() {
        CaravanRules.Quote quote = CaravanRules.quote(100.0D, 0.05D, 1.0D, 0.15D, true);
        CaravanRules.Settlement survived = CaravanRules.settle(100.0D, quote, CaravanRules.Event.AMBUSH,
                false, true, 1.0D, 0.20D, 0.05D, 0.10D);
        assertFalse(survived.failed());
        assertTrue(survived.merchantPayout() > 0.0D);
        CaravanRules.Settlement safe = CaravanRules.settle(100.0D, quote, CaravanRules.Event.SAFE,
                true, false, 1.0D, 0.20D, 0.05D, 0.10D);
        assertFalse(safe.failed());
        assertEquals(5.0D, safe.ownerToll());
        assertEquals(9.5D, safe.escortCut());
        assertEquals(85.5D, safe.merchantPayout());
    }

    @Test
    void resumeCompletesOverdueInFlightCaravansWithoutRecharging() {
        Caravan caravan = new Caravan(UUID.randomUUID());
        caravan.setStatus(Caravan.Status.IN_TRANSIT);
        caravan.setChargedVault(105.0D);
        caravan.setDispatchedAt(1_000L);
        caravan.setEtaAt(5_000L);
        assertFalse(CaravanRules.shouldComplete(caravan.getEtaAt(), 4_999L));
        assertTrue(CaravanRules.shouldComplete(caravan.getEtaAt(), 5_000L));
        assertTrue(CaravanRules.canCancel(1_000L, 5_000L, 1_500L, 0.25D));
        assertFalse(CaravanRules.canCancel(1_000L, 5_000L, 3_000L, 0.25D));
        assertTrue(CaravanRules.sameHop(caravan.getId(), caravan.getId()));
        assertFalse(CaravanRules.sameHop(caravan.getId(), UUID.randomUUID()));
    }

    @Test
    void dispatchCooldownAndMoneyClamp() {
        assertEquals(2_000L, CaravanRules.remainingCooldownMs(8_000L, 10_000L, 4_000L));
        assertEquals(0L, CaravanRules.remainingCooldownMs(1_000L, 10_000L, 4_000L));
        assertEquals(1.23D, CaravanRules.clampMoney(1.234D));
        assertEquals(0.0D, CaravanRules.clampMoney(-8.0D));
    }
}
