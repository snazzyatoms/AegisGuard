package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3 (Emergency Plot Lockdown) contract checks: safe config defaults, GUI/plugin wiring,
 * the audit hook for activate/deactivate, and the two non-negotiable safety rules - never trap a
 * player (INTERACT is never restrictable) and never touch ownership/roles/Guest Passes.
 */
class LockdownContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void configShipsASafelyDefaultedLockdownSectionDisabledByDefaultOnExistingPlots() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> lockdown = (Map<String, Object>) config.get("lockdown");
        assertTrue(lockdown != null, "config.yml must declare a lockdown section");
        assertEquals(Boolean.TRUE, lockdown.get("require_confirmation"), "Lockdown toggles must require confirmation by default");

        @SuppressWarnings("unchecked")
        List<String> restricted = (List<String>) lockdown.get("restricted_permissions");
        assertTrue(restricted != null && !restricted.isEmpty(), "lockdown.restricted_permissions must have safe defaults");
        assertFalse(restricted.contains("INTERACT"), "INTERACT must never be a configurable restricted permission");
    }

    @Test
    void lockdownIsNeverActiveOnANewlyConstructedPlot() throws Exception {
        String plotSource = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plotSource.contains("private volatile boolean lockdownActive = false;"),
                "Lockdown must default to inactive for every plot, including existing 1.2.7 claims");
    }

    @Test
    void interactCanNeverBeRestrictedEvenIfMisconfigured() throws Exception {
        String plotSource = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plotSource.contains("if (\"INTERACT\".equals(needle)) return false;"),
                "isLockdownRestrictable must hard-code INTERACT as never restrictable, regardless of config");
    }

    @Test
    void canInteractAtAlsoHonorsLockdownSoGuestPassesCannotBypassContainers() throws Exception {
        String plotSource = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plotSource.contains("isPermissionRestrictedByLockdown(needle, pl)"),
                "canInteractAt must deny lockdown-restricted tokens instead of falling through to hasPermission");
    }

    @Test
    void lockdownServiceIsWiredIntoThePluginLifecycleAndAudited() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("lockdownService = new com.aegisguard.lockdown.LockdownService(this)"));

        String service = Files.readString(JAVA_ROOT.resolve("lockdown/LockdownService.java"));
        assertTrue(service.contains("AuditCategory.LOCKDOWN"), "Activating/deactivating lockdown must be audited");
        assertTrue(service.contains("plugin.store().savePlot(plot)"), "Lockdown changes must be persisted with the plot");
        assertFalse(service.contains("setRole") && service.contains("playerRoles"),
                "LockdownService must never touch permanent roles");
    }

    @Test
    void lockdownGuiIsRoutedThroughTheCentralListenerAndPlayerMenu() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("holder instanceof LockdownMenuHolder"));
        assertTrue(listener.contains("holder instanceof LockdownConfirmHolder"));

        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("lockdownGui().open(player)"), "The main menu must offer a Lockdown entry point");
    }

    @Test
    void lockdownRequiresAnExplicitConfirmationStepBeforeActivatingOrDeactivating() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("lockdown/LockdownGUI.java"));
        assertTrue(gui.contains("requiresConfirmation()"), "The GUI must consult the confirmation setting before toggling");
        assertTrue(gui.contains("openConfirm"), "The GUI must offer an explicit confirmation screen");
        assertTrue(gui.contains("button_deactivate_lockdown") || gui.contains("Deactivate Lockdown"),
                "The GUI must expose an obvious unlock action while lockdown is active");
    }
}
