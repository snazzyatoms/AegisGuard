package com.aegisguard;

import com.aegisguard.alliance.AllianceAccess;
import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPassPreset;
import com.aegisguard.protection.ProtectionPreset;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract checks for the 1.3.0 player-protection "hero pack".
 */
class ProtectionHeroPackContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void configSchemaBumpedAndWardDefaultsPresent() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1286, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> protections = (Map<String, Object>) config.get("protections");
        assertEquals(Boolean.TRUE, protections.get("hopper_pipe"));
        assertEquals(Boolean.TRUE, protections.get("teleport_ward"));
        assertEquals(Boolean.TRUE, protections.get("storm_ward"));
        assertEquals(Boolean.TRUE, protections.get("decor"));
        assertEquals(Boolean.TRUE, protections.get("doors"));
        assertEquals(Boolean.TRUE, protections.get("liquid_flow"));

        Map<String, Object> alliance = (Map<String, Object>) config.get("alliance_access");
        Map<String, Object> disallow = (Map<String, Object>) alliance.get("disallow");
        assertTrue(disallow.containsKey("vehicles"));
        assertEquals(Boolean.FALSE, disallow.get("vehicles"));
    }

    @Test
    void protectionManagerWiresHeroPackHandlers() throws Exception {
        String protection = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(protection.contains("onHopperPipe"));
        assertTrue(protection.contains("onTeleportWard"));
        assertTrue(protection.contains("onLightningStrike"));
        assertTrue(protection.contains("onDoorInteract"));
        assertTrue(protection.contains("\"hopper-pipe\""));
        assertTrue(protection.contains("\"teleport-ward\""));
        assertTrue(protection.contains("\"storm-ward\""));
        assertTrue(protection.contains("\"doors\""));

        String blocks = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(blocks.contains("liquid-flow"));
        assertTrue(blocks.contains("\"decor\""));
    }

    @Test
    void plotFlagsGuiExposesWardsAndPresetsWithoutCollidingNav() throws Exception {
        String flags = Files.readString(JAVA.resolve("gui/PlotFlagsGUI.java"));
        assertTrue(flags.contains("Page.HUB"));
        assertTrue(flags.contains("Page.SAFETY"));
        assertTrue(flags.contains("Page.PRESETS"));
        assertTrue(flags.contains("ProtectionPreset.HOME"));
        assertTrue(flags.contains("isServerZone()"));
        assertTrue(flags.contains("PlotFlagsPresetConfirmHolder"));
        assertTrue(flags.contains("rawSlot == 48"));
        assertTrue(flags.contains("rawSlot == 49"));
        assertFalse(flags.contains("togglePaid(player, plot, \"fly\""));
    }

    @Test
    void protectionPresetsApplyExpectedBundles() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 10, 10);
        ProtectionPreset.HOME.apply(plot);
        assertFalse(plot.getFlag("entry", true), "Home should close entry (entry=false)");
        assertTrue(plot.getFlag("containers", false));
        assertTrue(plot.getFlag("hopper-pipe", false));
        assertTrue(plot.getFlag("teleport-ward", false));

        ProtectionPreset.SHOP.apply(plot);
        assertTrue(plot.getFlag("entry", false), "Shop should open entry");
        assertTrue(plot.getFlag("shop-interact", false));
        assertTrue(plot.getFlag("containers", false));

        ProtectionPreset.ARENA.apply(plot);
        assertFalse(plot.getFlag("pvp", true), "Arena allows PvP (protection off)");
        assertTrue(plot.getFlag("tnt-damage", false));

        ProtectionPreset.FARM.apply(plot);
        assertFalse(plot.getFlag("farm", true));
        assertFalse(plot.getFlag("animals", true));
        assertTrue(plot.getFlag("mobs", false));
    }

    @Test
    void guestPassHeroPresetsGrantExpectedTokens() {
        assertEquals(Set.of("INTERACT", "ANIMALS"), GuestPassPreset.ANIMAL_SITTER.getPermissions());
        assertEquals(Set.of("INTERACT", "REDSTONE"), GuestPassPreset.REDSTONE_HELPER.getPermissions());
        assertEquals(Set.of("INTERACT", "VEHICLES"), GuestPassPreset.VEHICLE_GUEST.getPermissions());
        assertTrue(GuestPassPreset.ordered().contains(GuestPassPreset.ANIMAL_SITTER));
        assertTrue(GuestPassPreset.ordered().contains(GuestPassPreset.VEHICLE_GUEST));
        assertFalse(GuestPassPreset.ANIMAL_SITTER.requiresContainerWarning());
        assertFalse(GuestPassPreset.ANIMAL_SITTER.grantsBuildAccess());
    }

    @Test
    void allianceVehiclesToggleDefaultsOffAndSerializes() {
        AllianceAccess access = new AllianceAccess();
        assertFalse(access.isVehicles());
        assertTrue(access.toggle("vehicles"));
        assertTrue(access.isVehicles());
        assertTrue(access.grantsPermission("VEHICLES"));

        AllianceAccess loaded = AllianceAccess.deserialize(access.serialize());
        assertTrue(loaded.isVehicles());
        assertTrue(loaded.serialize().contains("vehicles:1"));
    }

    @Test
    void timedSoftLockdownExpiresAndNeverWipesRoles() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 10, 10);
        plot.setRole(member, "builder");

        long expires = System.currentTimeMillis() - 1_000L;
        plot.setLockdown(true, owner, "Owner", expires, "SOFT");
        assertTrue(plot.isSoftLockdown());
        assertFalse(plot.isLockdownActive(), "Expired soft lockdown should auto-lift");
        assertEquals("builder", plot.getRole(member));
        assertTrue(plot.isPermissionRestrictedByLockdown("CONTAINERS", null) == false);
    }

    @Test
    void softLockdownRestrictsOnlyBuildAndContainers() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 10, 10);
        plot.setLockdown(true, owner, "Owner", 0L, "SOFT");
        assertTrue(plot.isPermissionRestrictedByLockdown("CONTAINERS", null));
        assertTrue(plot.isPermissionRestrictedByLockdown("BUILD", null));
        assertFalse(plot.isPermissionRestrictedByLockdown("INTERACT", null));
        assertFalse(plot.isPermissionRestrictedByLockdown("ANIMALS", null));
        assertFalse(plot.isPermissionRestrictedByLockdown("VEHICLES", null));
    }

    @Test
    void migrationSchemaConstantMatchesConfig() throws Exception {
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1284") || migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286"));
    }

    @Test
    void timedLockdownSweepUsesFoliaSafeGlobalRepeatingTask() throws Exception {
        String plugin = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("startLockdownSweepTask()"));
        assertTrue(plugin.contains("lockdownSweepTask = runGlobalRepeating"),
                "Timed lockdown expiry must use Folia-safe runGlobalRepeating like guest-pass/rental sweeps");
        assertTrue(plugin.contains("lockdownService.sweepExpired()"));
    }
}
