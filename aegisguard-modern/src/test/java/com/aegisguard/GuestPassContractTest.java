package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 2 (Temporary Guest Passes) contract checks: safe config defaults, GUI/plugin wiring,
 * the audit hook for issue/revoke/expiry, and the rule that a Guest Pass never overwrites or
 * removes a player's permanent role.
 */
class GuestPassContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void configShipsASafelyDefaultedGuestPassesSection() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> guestPasses = (Map<String, Object>) config.get("guest_passes");
        assertTrue(guestPasses != null, "config.yml must declare a guest_passes section");
        assertEquals(Boolean.TRUE, guestPasses.get("enabled"), "Guest Passes must be enabled by default");
        assertTrue(((Number) guestPasses.get("max_active_per_plot")).intValue() > 0);
        assertTrue(((Number) guestPasses.get("max_duration_minutes")).longValue() > 0);
    }

    @Test
    void guestPassServiceIsWiredIntoThePluginLifecycle() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("guestPassService = new com.aegisguard.guestpass.GuestPassService(this)"));
        assertTrue(plugin.contains("registerEvents(guestPassService"));
        assertTrue(plugin.contains("freezeAllActiveSessions"));
        assertTrue(plugin.contains("startGuestPassExpiryTask"));
        assertTrue(plugin.contains("guestPassService.runExpirySweep()"));
    }

    @Test
    void guestPassGuiIsRoutedThroughTheCentralListenerAndPlayerMenu() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        for (String holder : java.util.List.of("GuestPassMenuHolder", "GuestPassAddHolder",
                "GuestPassPresetHolder", "GuestPassDurationHolder", "GuestPassModeHolder",
                "GuestPassConfirmHolder", "GuestPassDetailHolder")) {
            assertTrue(listener.contains("holder instanceof " + holder), holder + " must be protected and routed");
        }

        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("guestPasses().open(player)"), "The main menu must offer a Guest Passes entry point");

        String guestGui = Files.readString(JAVA_ROOT.resolve("guestpass/GuestPassGUI.java"));
        assertTrue(guestGui.contains("GuestPassMode.REAL_TIME"));
        assertTrue(guestGui.contains("GuestPassMode.ACTIVE_PLAYTIME"));
        assertTrue(guestGui.contains("guest_pass_entry_mode_line"));
    }

    @Test
    void issuingOrRevokingAGuestPassWritesAnAuditEntry() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("guestpass/GuestPassService.java"));
        assertTrue(service.contains("AuditCategory.GUEST_PASS"), "Guest Pass issue/revoke/expiry must be audited");
        assertTrue(service.contains("plugin.store().savePlot(plot)"), "Guest Pass changes must be persisted with the plot");
    }

    @Test
    void guestPassNeverOverwritesOrRemovesPermanentTrust() throws Exception {
        String plotSource = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plotSource.contains("guestPasses.remove(playerUUID)"),
                "Bans must revoke Guest Passes, matching the additive-only design");

        String service = Files.readString(JAVA_ROOT.resolve("guestpass/GuestPassService.java"));
        assertTrue(!service.contains("playerRoles.remove") && !service.contains("setRole"),
                "GuestPassService must never touch permanent roles directly");
    }
}
