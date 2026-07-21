package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AscensionHallContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void hallProvidesGuideDisciplineConfirmationAndSafeCeremony() throws Exception {
        String source = Files.readString(JAVA_ROOT.resolve("gui/LevelingGUI.java"));
        for (String page : List.of("HALL", "GUIDE", "DISCIPLINES", "CONFIRM")) {
            assertTrue(source.contains(page), "Ascension Hall must provide the " + page + " page");
        }
        assertTrue(source.contains("ascension_guide_name"));
        assertTrue(source.contains("ascension_confirm_accept"));
        assertTrue(source.contains("ascension_ceremony_title"));
        assertTrue(source.contains("aegis.admin.bypass-limits"));
        assertTrue(source.indexOf("plot.setLevel(nextLevel)")
                < source.indexOf("callEvent(new PlotLevelUpEvent"));
        assertFalse(source.contains("Material.EMERALD_BLOCK"));
        assertFalse(source.contains("Material.GOLD_BLOCK"));
        assertFalse(source.contains("Material.REDSTONE_BLOCK"));
    }

    @Test
    void failedUpgradeRestoresPaymentLevelAndGeometry() throws Exception {
        String source = Files.readString(JAVA_ROOT.resolve("gui/LevelingGUI.java"));
        assertTrue(source.contains("int oldX1 = plot.getX1()"));
        assertTrue(source.contains("updatePlotBounds(plot, oldX1, oldZ1, oldX2, oldZ2)"));
        assertTrue(source.contains("plugin.eco().deposit(player, cost, type)"));
        assertTrue(source.contains("ascension_transaction_rollback"));
    }

    @Test
    void effectsHaveOneOwnerAndPreserveExternalState() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("listeners/LevelingListener.java"));
        String protection = Files.readString(JAVA_ROOT.resolve("protection/ProtectionManager.java"));
        assertTrue(listener.contains("managedEffects"));
        assertTrue(listener.contains("displacedEffects"));
        assertTrue(listener.contains("previousFlight"));
        assertTrue(listener.contains("public void refresh(Player player, Plot plot)"));
        assertFalse(listener.contains("effect.getDuration() > 100000"));
        assertFalse(protection.contains("applyPlotBuffs"));
        assertFalse(protection.contains("clearPlotBuffs"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rewardsStayUtilityFocusedAndZenithOwnsFlight() throws Exception {
        Map<String, Object> config = new Yaml().load(
                Files.readString(Path.of("src/main/resources/config.yml")));
        Map<String, Object> leveling = (Map<String, Object>) config.get("leveling");
        Map<Object, Object> rewards = (Map<Object, Object>) leveling.get("rewards");
        String allRewards = rewards.values().toString();
        assertFalse(allRewards.contains("INCREASE_DAMAGE"));
        assertFalse(allRewards.contains("DAMAGE_RESISTANCE"));
        assertFalse(allRewards.contains("ABSORPTION"));
        assertFalse(allRewards.contains("RADIUS:"));
        assertTrue(String.valueOf(rewards.get(1)).equals("[]"), "Level 1 is the founding baseline, not an earned reward");
        assertTrue(String.valueOf(rewards.get(30)).contains("FLIGHT"));
        assertFalse(rewards.entrySet().stream()
                .filter(entry -> !String.valueOf(entry.getKey()).equals("30"))
                .anyMatch(entry -> String.valueOf(entry.getValue()).contains("FLIGHT")));
    }

    @Test
    void focusIsPersistedInEveryStorageBackend() throws Exception {
        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        String yml = Files.readString(JAVA_ROOT.resolve("data/YMLDataStore.java"));
        String sql = Files.readString(JAVA_ROOT.resolve("data/SQLDataStore.java"));
        assertTrue(plot.contains("ascensionFocus"));
        assertTrue(yml.contains("ascension.focus"));
        assertTrue(yml.contains("ascension.focus-changed-at"));
        assertTrue(sql.contains("ascensionFocus"));
        assertTrue(sql.contains("ascensionFocusChangedAt"));
    }
}
