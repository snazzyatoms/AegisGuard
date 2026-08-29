package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSnapshotGuidebookContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void claimSettingsUsesPagesAndHidesPersonalPresetsOnServerPlots() throws Exception {
        String flags = Files.readString(JAVA.resolve("gui/PlotFlagsGUI.java"));
        assertTrue(flags.contains("enum Page"));
        assertTrue(flags.contains("isServerZone()"));
        assertTrue(flags.contains("ProtectionPreset.HOME"));
        assertTrue(flags.contains("open(player, plot, Page.HUB)"));
        assertTrue(flags.contains("button_back_claim_hub"));
        assertTrue(flags.contains("plot_flags_server_title"));
        assertFalse(flags.contains("placePresetButton(player, inv, 37, ProtectionPreset.HOME"));
    }

    @Test
    void staffToolsAreFiftyFourSlotsWithoutArenaExpansionCollision() throws Exception {
        String admin = Files.readString(JAVA.resolve("gui/AdminGUI.java"));
        assertTrue(admin.contains("SIZE = 54"));
        assertTrue(admin.contains("SLOT_TOGGLE_EXPANSION_MODE = 16"));
        assertTrue(admin.contains("SLOT_TOOL_ARENA          = 38") || admin.contains("SLOT_TOOL_ARENA = 38"));
        assertTrue(admin.contains("cycle_snapshot_schedule"));
        assertTrue(admin.contains("create_here") || Files.readString(JAVA.resolve("snapshots/SnapshotAdminGUI.java")).contains("create_here"));
    }

    @Test
    void snapshotGuiIsLazyAndCanCreateFromMenu() throws Exception {
        String gui = Files.readString(JAVA.resolve("gui/GUIManager.java"));
        assertTrue(gui.contains("snapshotAdminGUI == null && plugin.getSnapshotManager() != null"));
        String snapshots = Files.readString(JAVA.resolve("snapshots/SnapshotAdminGUI.java"));
        assertTrue(snapshots.contains("create_here"));
        assertTrue(snapshots.contains("create_server_zones"));
        assertTrue(snapshots.contains("Does not copy world blocks")
                || snapshots.contains("not world blocks")
                || snapshots.contains("Builds were not copied"));
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        assertTrue(manager.contains("cycleScheduledInterval"));
        assertTrue(manager.contains("runScheduledPass"));
        assertTrue(manager.contains("SCHEDULED"));
        String plugin = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("startScheduledSnapshotTask()"));
        assertTrue(plugin.contains("runGlobalRepeating"));
    }

    @Test
    void firstClaimOpensLanguagePickerWhenStyleIsUnset() throws Exception {
        String walkthrough = Files.readString(JAVA.resolve("guidance/FirstClaimWalkthroughGUI.java"));
        assertTrue(walkthrough.contains("hasSavedPlayerStyle"));
        assertTrue(walkthrough.contains("ReturnTo.WALKTHROUGH"));
        assertTrue(walkthrough.contains("openAfterLanguageChoice"));
        String picker = Files.readString(JAVA.resolve("gui/LanguageSelectGUI.java"));
        assertTrue(picker.contains("enum ReturnTo"));
        assertTrue(picker.contains("WALKTHROUGH"));
        String codex = Files.readString(JAVA.resolve("language/CodexEngine.java"));
        assertTrue(codex.contains("hasSavedPlayerStyle"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduledSnapshotConfigDefaultsOffAndTargetsServerZones() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        Map<String, Object> snapshots = (Map<String, Object>) config.get("snapshots");
        Map<String, Object> scheduled = (Map<String, Object>) snapshots.get("scheduled");
        assertEquals(Boolean.FALSE, scheduled.get("enabled"));
        assertEquals(360, ((Number) scheduled.get("interval_minutes")).intValue());
        assertEquals("server_zones", scheduled.get("targets"));
        Map<String, Object> backup = (Map<String, Object>) snapshots.get("build_backup");
        assertEquals(Boolean.FALSE, backup.get("enabled"));
        assertEquals(1305, ((Number) config.get("config_schema")).intValue());
    }

    @Test
    void claimStatusUsesSectionBandsAndLocalizedTitle() throws Exception {
        String status = Files.readString(JAVA.resolve("gui/PlotStatusGUI.java"));
        assertTrue(status.contains("plot_status_gui_title"));
        assertTrue(status.contains("addSectionFrame"));
        assertTrue(status.contains("plot_status_access_title"));
        assertTrue(status.contains("getSlot() == 48"));
        assertTrue(status.contains("getSlot() == 20"));
        assertTrue(status.contains("getSlot() == 21"));
        assertTrue(status.contains("getSlot() == 22"));
        assertTrue(status.contains("getSlot() == 23"));
        assertFalse(status.contains("getSlot() == 38"));
        assertFalse(status.contains("getSlot() == 40"));
        assertFalse(status.contains("getSlot() == 42"));
    }

    @Test
    void guidebookDocumentsNewFeatures() throws Exception {
        String info = Files.readString(JAVA.resolve("gui/InfoGUI.java"));
        for (String key : new String[]{
                "codex_root_whats_new_name", "codex_travel_safe_name", "codex_travel_routes_name",
                "codex_menus_language_name", "codex_security_guest_name", "codex_security_lockdown_name",
                "codex_security_alliance_name", "codex_identity_realm_name",
                "codex_advanced_arena_name", "codex_advanced_arena_off_name"
        }) {
            assertTrue(info.contains(key), "InfoGUI missing " + key);
        }
    }
}
