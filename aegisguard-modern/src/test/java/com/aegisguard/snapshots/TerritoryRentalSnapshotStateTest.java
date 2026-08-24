package com.aegisguard.snapshots;

import com.aegisguard.territory.TerritoryLifeService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRentalSnapshotStateTest {

    @Test
    void roundTripPreservesEveryOfferAndContractFieldExactly() {
        UUID plotId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID renterId = UUID.randomUUID();
        TerritoryLifeService.RentalOffer offer =
                new TerritoryLifeService.RentalOffer(47.25D, 125.75D, 19);
        TerritoryLifeService.RentalContract contract = new TerritoryLifeService.RentalContract(
                plotId, ownerId, renterId, 43.5D, 111.25D, 17,
                1_723_456_789_012L, 1_823_456_789_012L, true, true);

        TerritoryRentalSnapshotState.State restored = TerritoryRentalSnapshotState.decode(
                TerritoryRentalSnapshotState.encode(plotId, offer, contract));

        assertEquals(plotId, restored.plotId());
        assertEquals(offer, restored.offer());
        assertEquals(plotId, restored.contract().plotId());
        assertEquals(ownerId, restored.contract().ownerId());
        assertEquals(renterId, restored.contract().renterId());
        assertEquals(43.5D, restored.contract().rent());
        assertEquals(111.25D, restored.contract().deposit());
        assertEquals(17, restored.contract().termDays());
        assertEquals(1_723_456_789_012L, restored.contract().startedAt());
        assertEquals(1_823_456_789_012L, restored.contract().expiresAt());
        assertTrue(restored.contract().reminderSent());
        assertTrue(restored.contract().autoRenew());
    }

    @Test
    void explicitAbsenceRemovesOfferAndContractInsteadOfCreatingFallbacks() {
        UUID plotId = UUID.randomUUID();
        TerritoryRentalSnapshotState.State restored = TerritoryRentalSnapshotState.decode(
                TerritoryRentalSnapshotState.encode(plotId, null, null));

        assertEquals(plotId, restored.plotId());
        assertNull(restored.offer());
        assertNull(restored.contract());
    }

    @Test
    void legacyBlankPayloadIsAcceptedForBackwardCompatibleReconciliation() {
        assertNull(TerritoryRentalSnapshotState.decode(""));
        TerritoryRentalSnapshotState.validate("", UUID.randomUUID());
    }

    @Test
    void wrongPlotAndCorruptPayloadsFailBeforeRestoreMutation() {
        UUID plotId = UUID.randomUUID();
        String encoded = TerritoryRentalSnapshotState.encode(plotId, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> TerritoryRentalSnapshotState.validate(encoded, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> TerritoryRentalSnapshotState.validate("not-base64", plotId));
    }

    @Test
    void contractCannotBeCapturedUnderAnotherPlot() {
        UUID plotId = UUID.randomUUID();
        TerritoryLifeService.RentalContract contract = new TerritoryLifeService.RentalContract(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10D, 20D,
                7, 100L, 200L, false, false);

        assertThrows(IllegalArgumentException.class,
                () -> TerritoryRentalSnapshotState.encode(plotId, null, contract));
        assertFalse(contract.autoRenew());
    }
}
