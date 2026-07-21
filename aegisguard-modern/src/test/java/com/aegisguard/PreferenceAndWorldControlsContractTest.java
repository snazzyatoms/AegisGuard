package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenceAndWorldControlsContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void playerSoundsCannotBypassTheCentralPreferenceGate() throws Exception {
        List<String> bypasses;
        try (var files = Files.walk(JAVA_ROOT)) {
            bypasses = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("util/EffectUtil.java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> line.contains(".playSound("))
                                    .filter(line -> !line.contains("plugin.effects().playSound("))
                                    .map(line -> path + ": " + line.trim());
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    })
                    .toList();
        }
        assertTrue(bypasses.isEmpty(), () -> "Sound preference bypasses found: " + bypasses);

        String effects = Files.readString(JAVA_ROOT.resolve("util/EffectUtil.java"));
        assertTrue(effects.contains("!plugin.isSoundEnabled(player)"));
    }

    @Test
    void notificationModesAndTogglesUseTheirAuthoritativeSettings() throws Exception {
        String settings = Files.readString(JAVA_ROOT.resolve("gui/SettingsGUI.java"));
        String greetings = Files.readString(JAVA_ROOT.resolve("listeners/PlotGreetingListener.java"));
        String notifications = Files.readString(JAVA_ROOT.resolve("notify/NotificationManager.java"));

        assertTrue(settings.contains("toggleGreetings(uuid)"));
        assertTrue(settings.contains("toggleAdminUpdates(uuid)"));
        assertTrue(settings.contains("cycleMode(uuid)"));
        assertTrue(greetings.contains("deliverGreeting(player, mode, msg"));
        assertTrue(greetings.contains("isConfiguredGreeting(msg, \"greetings.enter\")"));
        assertTrue(greetings.contains("isConfiguredGreeting(msg, \"greetings.leave\")"));
        assertFalse(greetings.contains("Entering: &f{OWNER}"));
        assertFalse(greetings.contains("Leaving: &f{OWNER}"));
        assertTrue(notifications.contains("hasAdminUpdatesEnabled(online.getUniqueId())"));
        assertTrue(notifications.contains("plugin.runMain(player"));
    }

    @Test
    void worldControlsAreRealAndClaimRulesAreEnforced() throws Exception {
        String admin = Files.readString(JAVA_ROOT.resolve("gui/AdminGUI.java"));
        String worldGui = Files.readString(JAVA_ROOT.resolve("gui/WorldControlsGUI.java"));
        String selection = Files.readString(JAVA_ROOT.resolve("selection/SelectionService.java"));

        assertTrue(admin.contains("plugin.gui().worldControls().open(player)"));
        assertFalse(admin.contains("world_controls_missing"));
        assertTrue(worldGui.contains("claims.per_world."));
        assertTrue(worldGui.contains("plugin.worldRules().reload()"));
        assertTrue(selection.contains("!plugin.worldRules().allowClaims(selectedWorld)"));
    }
}
