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
 * Contract checks for Phase 1/2 1.3.0 improvements beyond Guest Passes.
 */
class PhaseImprovementsContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void travelAndAllianceDefaultsPreserveLegacyBehavior() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }

        Map<String, Object> travel = (Map<String, Object>) config.get("travel");
        assertEquals(0, ((Number) travel.get("cooldown_seconds")).intValue());
        assertEquals(Boolean.FALSE, travel.get("require_confirmation"));
        assertEquals(Boolean.FALSE, travel.get("block_while_in_combat"));

        Map<String, Object> alliance = (Map<String, Object>) config.get("alliance_access");
        assertEquals(0, ((Number) alliance.get("invite_expire_minutes")).intValue());
        Map<String, Object> disallow = (Map<String, Object>) alliance.get("disallow");
        assertEquals(Boolean.FALSE, disallow.get("build"));
        assertEquals(Boolean.FALSE, disallow.get("containers"));

        Map<String, Object> mob = (Map<String, Object>) config.get("mob_barrier");
        assertEquals(Boolean.TRUE, mob.get("protect_hostile"));
        assertEquals(Boolean.FALSE, mob.get("protect_passive"));
        assertEquals(Boolean.FALSE, mob.get("protect_boss"));
    }

    @Test
    void staffHealthCheckAndSafeTravelAreWired() throws Exception {
        String admin = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));
        assertTrue(admin.contains("\"health\""));
        assertTrue(admin.contains("StaffHealthCheck.report"));

        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("SafeTravelService"));

        assertTrue(Files.exists(JAVA_ROOT.resolve("admin/StaffHealthCheck.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("travel/SafeTravelService.java")));
    }

    @Test
    void notificationCategoriesDefaultOnAndSettingsExposeToggles() throws Exception {
        String settings = Files.readString(JAVA_ROOT.resolve("gui/SettingsGUI.java"));
        assertTrue(settings.contains("toggleGuestPassNotifications"));
        assertTrue(settings.contains("toggleAllianceNotifications"));
        assertTrue(settings.contains("toggleLockdownNotifications"));
        assertTrue(settings.contains("toggleTravelNotifications"));
        assertTrue(settings.contains("togglePlotNoticeNotifications"));

        String prefs = Files.readString(JAVA_ROOT.resolve("notify/PlayerNotificationSettings.java"));
        assertTrue(prefs.contains("guestPassNotifications = true"));
        assertTrue(prefs.contains("travelNotifications = true"));
    }

    @Test
    void recoverySnapshotsCoverRiskyActions() throws Exception {
        String snapshot = Files.readString(JAVA_ROOT.resolve("snapshots/ClaimSnapshot.java"));
        assertTrue(snapshot.contains("PRE_LOCKDOWN"));
        assertTrue(snapshot.contains("PRE_ALLIANCE_ACCESS"));
        assertTrue(snapshot.contains("PRE_STAFF_DESTINATION"));

        String lockdown = Files.readString(JAVA_ROOT.resolve("lockdown/LockdownService.java"));
        assertTrue(lockdown.contains("PRE_LOCKDOWN"));

        String alliance = Files.readString(JAVA_ROOT.resolve("alliance/AllianceService.java"));
        assertTrue(alliance.contains("PRE_ALLIANCE_ACCESS"));

        String adminGui = Files.readString(JAVA_ROOT.resolve("snapshots/SnapshotAdminGUI.java"));
        assertTrue(adminGui.contains("require_restore_confirmation"));
        assertTrue(adminGui.contains("snapshot_restore_summary"));
    }

    @Test
    void travelDestinationsExposeRecentAndCategories() throws Exception {
        String visit = Files.readString(JAVA_ROOT.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("RECENT"));
        assertTrue(visit.contains("mode_RECENT"));
        assertTrue(visit.contains("getWarpCategory"));
        assertTrue(visit.contains("visit_help"));

        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plot.contains("warpCategory"));
        assertTrue(plot.contains("setWarpCategory"));
    }

    @Test
    void schemaBumpIncludesTravelImprovements() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1281")
                || migration.contains("CURRENT_SCHEMA = 1282")
                || migration.contains("CURRENT_SCHEMA = 1283")
                || migration.contains("CURRENT_SCHEMA = 1284") || migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286")
                || migration.contains("CURRENT_SCHEMA = 1287")
                || migration.contains("CURRENT_SCHEMA = 1294")
                || migration.contains("CURRENT_SCHEMA = 1300")
                || migration.contains("CURRENT_SCHEMA = 1280"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(config.contains("config_schema: 1281")
                || config.contains("config_schema: 1280")
                || config.contains("config_schema: 1282")
                || config.contains("config_schema: 1283")
                || config.contains("config_schema: 1284") || config.contains("config_schema: 1285")
                || config.contains("config_schema: 1286")
                || config.contains("config_schema: 1287")
                || config.contains("config_schema: 1294")
                || config.contains("config_schema: 1300"));
        assertFalse(config.contains("config_schema: 1278\n"));
    }
}
