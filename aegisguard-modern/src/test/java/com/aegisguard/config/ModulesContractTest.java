package com.aegisguard.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulesContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void shippedConfigHasModuleSwitchboardAndMissingEnabledKeys() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1292, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertTrue(modules.containsKey("guest_passes"));
        assertTrue(modules.containsKey("expansions"));
        assertTrue(modules.containsKey("cosmetics"));
        assertTrue(modules.containsKey("market"));
        assertEquals(Boolean.TRUE, modules.get("arena"));
        assertEquals(Boolean.TRUE, modules.get("upkeep"));
        assertEquals(Boolean.TRUE, modules.get("claim_merge"));
        assertEquals(Boolean.FALSE, modules.get("wilderness_revert"));
        assertEquals(Boolean.TRUE, modules.get("guest_passes"));
        assertEquals(Boolean.TRUE, modules.get("alliance_access"));
        assertEquals(Boolean.TRUE, modules.get("group_plots"));
        assertEquals(Boolean.TRUE, modules.get("lockdown"));
        Map<String, Object> expansions = (Map<String, Object>) config.get("expansions");
        assertEquals(Boolean.TRUE, expansions.get("enabled"));
        Map<String, Object> cosmetics = (Map<String, Object>) config.get("cosmetics");
        assertEquals(Boolean.TRUE, cosmetics.get("enabled"));
        Map<String, Object> marketHub = (Map<String, Object>) config.get("market_hub");
        assertEquals(Boolean.TRUE, marketHub.get("enabled"));
    }

    @Test
    void modulesPreferSwitchboardOverLegacyEnabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("guest_passes.enabled", true);
        config.set("modules.guest_passes", false);
        Modules modules = Modules.of(config);
        assertFalse(modules.on(Modules.Id.GUEST_PASSES));
    }

    @Test
    void modulesFallBackToLegacyWhenSwitchboardUnset() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("expansions.enabled", false);
        Modules modules = Modules.of(config);
        assertFalse(modules.on(Modules.Id.EXPANSIONS));
    }

    @Test
    void everyOptionalModuleDefaultsOnExceptWildernessRevert() {
        for (Modules.Id id : Modules.Id.values()) {
            if (id == Modules.Id.WILDERNESS_REVERT) {
                assertFalse(id.defaultOn(), id.key() + " must default off");
                continue;
            }
            assertTrue(id.defaultOn(), id.key() + " must default on");
        }
    }

    @Test
    void missingSwitchboardKeysDefaultOn() {
        YamlConfiguration config = new YamlConfiguration();
        Modules modules = Modules.of(config);
        assertTrue(modules.on(Modules.Id.ARENA));
        assertTrue(modules.on(Modules.Id.UPKEEP));
        assertTrue(modules.on(Modules.Id.CLAIM_MERGE));
        assertFalse(modules.on(Modules.Id.WILDERNESS_REVERT));
        assertTrue(modules.on(Modules.Id.GUEST_PASSES));
    }

    @Test
    void playerCommandsAreGatedAndCoreCommandsStayOpen() {
        assertEquals(Modules.Id.MARKET, Modules.commandModule("sell"));
        assertEquals(Modules.Id.TRAVEL, Modules.commandModule("home"));
        assertEquals(Modules.Id.ARENA, Modules.commandModule("arena"));
        assertEquals(null, Modules.commandModule("claim"));
        assertEquals(null, Modules.commandModule("menu"));
        assertEquals(null, Modules.commandModule("wand"));
    }

    @Test
    void playerGuiHidesOptionalButtonsWhenModulesAreOff() throws Exception {
        String gui = Files.readString(Path.of("src/main/java/com/aegisguard/gui/PlayerGUI.java"));
        assertTrue(gui.contains("if (showExpand)"));
        assertTrue(gui.contains("9, 10, 11, 12, 13, 14, 15, 16, 17"));
        assertTrue(gui.contains("27, 28, 29, 30, 31, 32, 33, 34, 35"));
        assertFalse(gui.contains("claim_merge_button_disabled_lore"));
        String settings = Files.readString(Path.of("src/main/java/com/aegisguard/gui/SettingsGUI.java"));
        assertTrue(settings.contains("FIRST_CLAIM_WALKTHROUGH"));
        String status = Files.readString(Path.of("src/main/java/com/aegisguard/gui/PlotStatusGUI.java"));
        assertTrue(status.contains("Modules.Id.CLAIM_MERGE"));
        assertTrue(status.contains("Modules.Id.CLAIM_BLOCKS"));
        String command = Files.readString(Path.of("src/main/java/com/aegisguard/commands/AegisCommand.java"));
        assertTrue(command.contains("Modules.commandModule"));
    }
}
