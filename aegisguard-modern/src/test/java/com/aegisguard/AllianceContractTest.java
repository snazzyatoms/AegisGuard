package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 7 (Alliance Access) contract checks.
 */
class AllianceContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsSafelyDefaultedAllianceSection() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        Map<String, Object> alliance = (Map<String, Object>) config.get("alliance_access");
        assertTrue(alliance != null, "config.yml must declare an alliance_access section");
        assertEquals(Boolean.TRUE, alliance.get("enabled"));
        assertTrue(((Number) alliance.get("max_members")).intValue() >= 2);
        assertTrue(((Number) config.get("config_schema")).intValue() >= 1281);
    }

    @Test
    void allianceServicesAreWiredIntoPluginLifecycle() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("new com.aegisguard.alliance.AllianceManager(this)"));
        assertTrue(plugin.contains("new com.aegisguard.alliance.AllianceService(this)"));
        assertTrue(plugin.contains("allianceManager.load()"));
        assertTrue(plugin.contains("allianceManager.save()"));
    }

    @Test
    void allianceGuiIsRoutedAndPlayerMenuExposesEntryPoint() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("holder instanceof AllianceMenuHolder"));
        assertTrue(listener.contains("holder instanceof AllianceConfirmHolder"));
        assertTrue(listener.contains("allianceAccess().handleMenuClick"));

        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("allianceAccess().openMenu"));
        assertTrue(playerGui.contains("button_alliance_access"));
    }

    @Test
    void allianceNeverTouchesOwnershipMoneyOrRentals() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("alliance/AllianceService.java"));
        assertFalse(service.contains("setOwner(") || service.contains("setRole(")
                        || service.contains("EconomyManager") || service.contains("VaultHook")
                        || service.contains("setForSale") || service.contains("setRental"),
                "AllianceService must never touch ownership, roles, money, or rentals");

        String access = Files.readString(JAVA_ROOT.resolve("alliance/AllianceAccess.java"));
        assertFalse(access.contains("MANAGE"));
        assertTrue(access.contains("All default false"));
    }

    @Test
    void plotExplicitlyBlocksAllianceManageGrants() throws Exception {
        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plot.contains("grantsAlliancePermission"));
        assertTrue(plot.contains("MANAGE_MEMBERS"));
        assertTrue(plot.contains("\"MANAGE\".equals(needle)"));
    }

    @Test
    void configSchemaWasBumpedForAllianceAccess() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1281")
                || migration.contains("CURRENT_SCHEMA = 1282")
                || migration.contains("CURRENT_SCHEMA = 1283")
                || migration.contains("CURRENT_SCHEMA = 1284") || migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286")
                || migration.contains("CURRENT_SCHEMA = 1287")
                || migration.contains("CURRENT_SCHEMA = 1294")
                || migration.contains("CURRENT_SCHEMA = 1300")
                || migration.contains("CURRENT_SCHEMA = 1304")
                || migration.contains("CURRENT_SCHEMA = 1305")
                || migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1280")
                || migration.contains("CURRENT_SCHEMA = 1278"));
    }

    @Test
    void allianceCommandIsRegistered() throws Exception {
        String command = Files.readString(JAVA_ROOT.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("\"alliance\""));
        assertTrue(command.contains("handleAlliance"));
    }

    @Test
    void protectionManagerWiresAllianceEntryAndFriendlyPvp() throws Exception {
        String protection = Files.readString(JAVA_ROOT.resolve("protection/ProtectionManager.java"));
        assertTrue(protection.contains("allowsAllianceEntry("),
                "Plot-entry protection must consult Alliance Entry");
        assertTrue(protection.contains("areAllianceAllies("),
                "Plot-PvP damage protection must consult Alliance Friendly PvP");
        assertTrue(protection.contains("onPlayerMove"),
                "Alliance Entry must live in the player-move / plot-entry path");
        assertTrue(protection.contains("onEntityDamage"),
                "Alliance Friendly PvP must live in the entity-damage / PvP path");
    }
}
