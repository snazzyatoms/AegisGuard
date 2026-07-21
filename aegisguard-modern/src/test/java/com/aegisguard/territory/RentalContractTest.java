package com.aegisguard.territory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RentalContractTest {

    @Test
    void renewalExtendsFromExistingExpiryAndResetsReminder() {
        long day = 86_400_000L;
        TerritoryLifeService.RentalContract contract = new TerritoryLifeService.RentalContract(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100.0D, 25.0D, 7, 1000L, 1000L + day, true);

        contract.extendFrom(1000L);

        assertEquals(1000L + (8L * day), contract.expiresAt());
        assertFalse(contract.reminderSent());
    }
}
