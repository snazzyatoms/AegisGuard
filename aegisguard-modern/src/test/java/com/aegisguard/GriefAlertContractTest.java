package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the grief-attempt staff alert: denials counted per (player, plot), alerts perm-gated
 * and rate-limited, state bounded and evicted on quit, tuning keys shipped in config.yml.
 */
class GriefAlertContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void denialTrackingIsWired() throws Exception {
        String src = Files.readString(JAVA.resolve("guidance/DenialGuidance.java"));
        assertTrue(src.contains("DENY_WINDOWS"), "per-player denial windows must exist");
        assertTrue(src.contains("trackDenial"), "send() must feed the denial tracker");
        assertTrue(src.contains("alertStaff"), "threshold must trigger a staff alert");
        // Tracking must run before the message-throttle early-return.
        assertTrue(src.indexOf("trackDenial(plugin, player, plot)")
                        < src.indexOf("isThrottled(plugin, player, key)"),
                "denial tracking must count throttled denials too");
    }

    @Test
    void alertsArePermGatedAndBounded() throws Exception {
        String src = Files.readString(JAVA.resolve("guidance/DenialGuidance.java"));
        assertTrue(src.contains("aegis.admin.alerts"),
                "staff alerts must check aegis.admin.alerts");
        assertTrue(src.contains("runMain(staff"),
                "staff alerts must be delivered on each staff member's region thread");
        assertTrue(src.contains("alertedAt"), "alerts must be cooldown-suppressed");
        assertTrue(src.contains("removeIf"), "expired windows must prune");
        assertTrue(src.contains("DENY_WINDOWS.remove(playerId)"),
                "quit eviction must drop the player's denial windows");
    }

    @Test
    @SuppressWarnings("unchecked")
    void configShipsTuningKeys() throws Exception {
        Map<String, Object> config;
        try (var in = Files.newInputStream(RES.resolve("config.yml"))) {
            config = new Yaml().load(in);
        }
        Map<String, Object> protections = (Map<String, Object>) config.get("protections");
        assertNotNull(protections, "config.yml must ship a protections section");
        Object threshold = protections.get("grief_alert_threshold");
        Object window = protections.get("grief_alert_window_seconds");
        Object cooldown = protections.get("grief_alert_cooldown_seconds");
        assertNotNull(threshold, "protections.grief_alert_threshold must exist");
        assertNotNull(window, "protections.grief_alert_window_seconds must exist");
        assertNotNull(cooldown, "protections.grief_alert_cooldown_seconds must exist");
        assertTrue(((Number) threshold).intValue() > 0);
        assertTrue(((Number) window).longValue() > 0);
        assertTrue(((Number) cooldown).longValue() > 0);
    }

    @Test
    void alertKeyExistsEverywhere() throws Exception {
        Yaml yaml = new Yaml();
        for (String lang : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> sys;
            try (var in = Files.newInputStream(RES.resolve("lang/" + lang + "/system.yml"))) {
                sys = yaml.load(in);
            }
            assertTrue(sys.containsKey("grief_alert_staff"), lang + "/system.yml missing grief_alert_staff");
            Map<String, Object> codex;
            try (var in = Files.newInputStream(RES.resolve("codex/" + lang + ".yml"))) {
                codex = yaml.load(in);
            }
            assertTrue(codex.containsKey("grief_alert_staff"), lang + ".yml missing grief_alert_staff");
        }
    }
}
