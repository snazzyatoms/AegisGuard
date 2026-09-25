package com.aegisguard.publicbeta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicBetaValueTest {

    @Test
    void voiceModeDefaultsSafely() {
        assertEquals(PublicBetaVoiceMode.PROXIMITY,
                PublicBetaVoiceMode.parse(null, PublicBetaVoiceMode.PROXIMITY));
        assertEquals(PublicBetaVoiceMode.PROXIMITY,
                PublicBetaVoiceMode.parse("broken", PublicBetaVoiceMode.PROXIMITY));
        assertEquals(PublicBetaVoiceMode.GLOBAL,
                PublicBetaVoiceMode.parse("global", PublicBetaVoiceMode.PROXIMITY));
        assertEquals(PublicBetaVoiceMode.CURRENT_WORLD,
                PublicBetaVoiceMode.parse("current_world", PublicBetaVoiceMode.PROXIMITY));
    }

    @Test
    void worldNamesRejectTraversalAndPrivateDevelopmentMarkers() {
        assertTrue(PublicBetaWorldService.validWorldName("aegis_beta_test_lab"));
        assertFalse(PublicBetaWorldService.validWorldName("../private"));
        assertFalse(PublicBetaWorldService.validWorldName("bad/world"));
        assertTrue(PublicBetaWorldService.looksPrivate("AegisGuard-private-development"));
        assertFalse(PublicBetaWorldService.looksPrivate("aegis_beta_play"));
    }
}
