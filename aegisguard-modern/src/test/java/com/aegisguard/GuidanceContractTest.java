package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 5 (Clearer Player Guidance) contract checks: safer denial messages, optional
 * first-claim walkthrough wiring, and the new repeat-notifications / replay preferences.
 */
class GuidanceContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsAnOptionalFirstClaimWalkthroughSectionEnabledByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        Map<String, Object> section = (Map<String, Object>) config.get("first_claim_walkthrough");
        assertTrue(section != null, "config.yml must declare a first_claim_walkthrough section");
        assertTrue(Boolean.TRUE.equals(section.get("enabled")),
                "The walkthrough must be optional but enabled by default for new installs");
    }

    @Test
    void denialGuidanceIsUsedByBlockProtectionAndPrefersGuestPassAwareMessages() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("protection/BlockProtectionListener.java"));
        assertTrue(listener.contains("DenialGuidance.send"),
                "BlockProtectionListener must route denials through DenialGuidance");

        String guidance = Files.readString(JAVA_ROOT.resolve("guidance/DenialGuidance.java"));
        assertTrue(guidance.contains("guest_pass_missing_permission"));
        assertTrue(guidance.contains("plot_action_needs_pass"));
        assertTrue(guidance.contains("lockdown_action_blocked"));
        assertTrue(guidance.contains("isRepeatNotificationsEnabled"),
                "DenialGuidance must honor the repeat-notifications preference");
    }

    @Test
    void walkthroughIsOptionalSkippableAndReplayable() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("guidance/FirstClaimWalkthroughGUI.java"));
        assertTrue(gui.contains("button_exit") || gui.contains("Skip"),
                "Walkthrough must expose an obvious Skip/Exit control");
        assertTrue(gui.contains("openIfFirstClaim"),
                "Walkthrough must only auto-open once via openIfFirstClaim");
        assertTrue(gui.contains("first_claim_walkthrough.enabled"));

        String settings = Files.readString(JAVA_ROOT.resolve("gui/SettingsGUI.java"));
        assertTrue(settings.contains("walkthrough().open(player, 0)"),
                "Settings must offer a Replay Walkthrough entry point");

        String command = Files.readString(JAVA_ROOT.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("case \"guide\""),
                "/ag guide must reopen the walkthrough on demand");
    }

    @Test
    void walkthroughIsWiredIntoPluginListenerAndGuiManager() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("FirstClaimGuidanceListener"));

        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("holder instanceof WalkthroughHolder"));

        String manager = Files.readString(JAVA_ROOT.resolve("gui/GUIManager.java"));
        assertTrue(manager.contains("new FirstClaimWalkthroughGUI(plugin)"));
        assertTrue(manager.contains("walkthrough()"));
    }

    @Test
    void playerNotificationSettingsPersistRepeatAndWalkthroughFlags() throws Exception {
        String settings = Files.readString(JAVA_ROOT.resolve("notify/PlayerNotificationSettings.java"));
        assertTrue(settings.contains("repeat_notifications"));
        assertTrue(settings.contains("walkthrough_seen"));
        assertTrue(settings.contains("toggleRepeatNotifications"));

        String manager = Files.readString(JAVA_ROOT.resolve("notify/NotificationManager.java"));
        assertTrue(manager.contains("toggleRepeatNotifications"));
        assertTrue(manager.contains("markWalkthroughSeen"));
        assertTrue(manager.contains("hasSeenWalkthrough"));
    }

    @Test
    void entryDenialMessageMentionsGuestPasses() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/lang/modern_english/system.yml"));
        assertTrue(english.contains("plot_entry_denied:") && english.contains("Guest Pass"),
                "plot_entry_denied must mention asking the owner for a Guest Pass");
        assertTrue(english.contains("plot_action_needs_pass:"));
        assertTrue(english.contains("guest_pass_missing_permission:"));
        assertTrue(english.contains("{PERMISSION}"),
                "guest_pass_missing_permission must expose a PERMISSION placeholder");
    }

    @Test
    void configSchemaWasBumpedForPlayerGuidance() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        // Schema continues to advance with later milestones; Player Guidance landed at 1276.
        assertTrue(migration.contains("CURRENT_SCHEMA = 1276")
                        || migration.contains("CURRENT_SCHEMA = 1277")
                        || migration.contains("CURRENT_SCHEMA = 1278")
                        || migration.contains("CURRENT_SCHEMA = 1280")
                        || migration.contains("CURRENT_SCHEMA = 1281")
                        || migration.contains("CURRENT_SCHEMA = 1282")
                || migration.contains("CURRENT_SCHEMA = 1283")
                || migration.contains("CURRENT_SCHEMA = 1284") || migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286")
                || migration.contains("CURRENT_SCHEMA = 1287"),
                "Config schema must be at least 1276 after Player Guidance");
    }

    @Test
    void walkthroughNeverMutatesPlotState() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("guidance/FirstClaimWalkthroughGUI.java"));
        assertFalse(gui.contains("setRole") || gui.contains("setLockdown") || gui.contains("addGuestPass")
                        || gui.contains("savePlot"),
                "The walkthrough must remain informational and never mutate plot state");
    }
}
