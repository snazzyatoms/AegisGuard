package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for the 1.2.7 → 1.3.0 jar-swap upgrade path. */
class SeamlessUpgradeContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path LANG = RESOURCES.resolve("lang");
    private static final Path CODEX = RESOURCES.resolve("codex");

    @Test
    void transitionCommandIsWiredWithUpgradeAliasAndNeverCallsDoctorRepair() throws Exception {
        String admin = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));
        assertTrue(admin.contains("\"transition\", \"upgrade\", \"v130\"")
                || (admin.contains("\"transition\"") && admin.contains("\"upgrade\"")));
        assertTrue(admin.contains("case \"transition\", \"upgrade\", \"v130\"")
                || admin.contains("handleTransition"));
        assertTrue(admin.contains("isTransitionSubcommand") || admin.contains("equalsIgnoreCase(\"transition\")"));
        assertTrue(admin.contains("admin_transition_already"));
        assertTrue(admin.contains("Already on 1.4.0; nothing to convert."));
        assertTrue(admin.contains("reloadAegisGuard(true)"));
        assertTrue(admin.contains("configMigration()"));
        int transitionIdx = admin.indexOf("private void handleTransition");
        assertTrue(transitionIdx > 0);
        String handle = admin.substring(transitionIdx, Math.min(admin.length(), transitionIdx + 2500));
        assertTrue(handle.contains("Doctor is optional"));
        assertFalse(handle.contains("runDoctor"));
        assertFalse(handle.contains("repair confirm"));

        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("transition"));
    }

    @Test
    void first1287UpgradeLogsPlotsUnchangedAndDoctorNotRequired() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306"));
        assertTrue(migration.contains("Existing plots were left unchanged"));
        assertTrue(migration.contains("Doctor is not required"));
        assertTrue(migration.contains("lastReport()"));
    }

    @Test
    void transitionKeysExistInEveryLanguageAndCodex() throws Exception {
        List<String> keys = List.of(
                "admin_help_transition:",
                "admin_transition_schema:",
                "admin_transition_plots:",
                "admin_transition_already:",
                "admin_transition_ran:",
                "admin_transition_backup:",
                "admin_transition_report:",
                "admin_transition_doctor_optional:"
        );
        List<String> guiKeys = List.of(
                "snapshot_scope_header_name:",
                "snapshot_scope_header_lore:"
        );
        for (String lang : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            String system = Files.readString(LANG.resolve(lang + "/system.yml"));
            String guis = Files.readString(LANG.resolve(lang + "/guis.yml"));
            String codex = Files.readString(CODEX.resolve(lang + ".yml"));
            for (String key : keys) {
                assertTrue(system.contains(key), lang + " system missing " + key);
                assertTrue(codex.contains(key), "codex/" + lang + " missing " + key);
            }
            for (String key : guiKeys) {
                assertTrue(guis.contains(key), lang + " guis missing " + key);
                assertTrue(codex.contains(key), "codex/" + lang + " missing " + key);
            }
            assertTrue(system.contains("{CURRENT}") && system.contains("{TARGET}"),
                    lang + " schema message needs {CURRENT}/{TARGET}");
            assertTrue(system.contains("{SCHEMA}"), lang + " ran message needs {SCHEMA}");
            assertTrue(system.contains("{PATH}"), lang + " backup/report needs {PATH}");
        }
    }
}
