package com.aegisguard.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompatParticleTest {

    @Test
    void legacyAndCurrentParticleNamesResolveToTheSameRuntimeConstant() {
        assertAlias("VILLAGER_HAPPY", "HAPPY_VILLAGER");
        assertAlias("FIREWORKS_SPARK", "FIREWORK");
        assertAlias("SMOKE_LARGE", "LARGE_SMOKE");
        assertAlias("SMOKE_NORMAL", "SMOKE");
    }

    private void assertAlias(String legacyName, String currentName) {
        assertNotNull(CompatParticle.match(legacyName));
        assertEquals(CompatParticle.match(legacyName), CompatParticle.match(currentName));
    }
}
