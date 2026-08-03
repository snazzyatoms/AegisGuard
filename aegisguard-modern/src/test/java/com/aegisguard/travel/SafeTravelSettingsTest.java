package com.aegisguard.travel;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTravelSettingsTest {

    @Test
    void defaultsPreserveExistingBehavior() {
        SafeTravelSettings defaults = SafeTravelSettings.defaults();
        assertTrue(defaults.preservesLegacyBehavior());
        assertEquals(0, defaults.getCooldownSeconds());
        assertFalse(defaults.isRequireConfirmation());
        assertFalse(defaults.isBlockWhileInCombat());
        assertEquals(4, defaults.getSafeSearchRadius());
        assertFalse(defaults.isApplyToStaff());
    }

    @Test
    void shippedConfigPreservesLegacyTravelDefaults() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> travel = (Map<String, Object>) config.get("travel");
        assertTrue(travel != null, "config.yml must declare a travel section");
        assertEquals(0, ((Number) travel.get("cooldown_seconds")).intValue());
        assertEquals(Boolean.FALSE, travel.get("require_confirmation"));
        assertEquals(Boolean.FALSE, travel.get("block_while_in_combat"));
        assertEquals(4, ((Number) travel.get("safe_search_radius")).intValue());
    }

    @Test
    void fromConfigReadsOptInRestrictions() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("travel.enabled", true);
        yaml.set("travel.cooldown_seconds", 15);
        yaml.set("travel.require_confirmation", true);
        yaml.set("travel.block_while_in_combat", true);
        yaml.set("travel.safe_search_radius", 8);

        SafeTravelSettings settings = SafeTravelSettings.fromConfig(yaml);
        assertEquals(15, settings.getCooldownSeconds());
        assertTrue(settings.isRequireConfirmation());
        assertTrue(settings.isBlockWhileInCombat());
        assertEquals(8, settings.getSafeSearchRadius());
        assertFalse(settings.preservesLegacyBehavior());
    }

    @Test
    void resultFactoriesExposeClearFailureKeys() {
        assertEquals("travel_fail_cooldown", SafeTravelResult.cooldown(2500).messageKey());
        assertEquals(3L, SafeTravelResult.cooldown(2500).remainingSeconds());
        assertEquals("travel_fail_combat", SafeTravelResult.inCombat(1000).messageKey());
        assertEquals("travel_confirm_prompt", SafeTravelResult.confirmationRequired().messageKey());
        assertEquals("travel_fail_unsafe", SafeTravelResult.unsafe().messageKey());
        assertTrue(SafeTravelResult.confirmationRequired().isConfirmationRequired());
        assertFalse(SafeTravelResult.unsafe().isSuccess());
    }
}
