package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for staff/admin/migration/rollback polish. */
class StaffAdminContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path LANG = Path.of("src/main/resources/lang");

    @Test
    void migrationWizardUsesActionTagsNotSlotCloseCollision() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("gui/MigrationAdminGUI.java"));
        assertTrue(migration.contains("preview_griefdefender"));
        assertTrue(migration.contains("confirm_import"));
        assertTrue(migration.contains("request_import"));
        assertTrue(migration.contains("tagAction") || migration.contains("tagged("));
        // The old slot-22 close handler closed GriefDefender instead of previewing.
        assertFalse(migration.contains("raw == 41 || raw == 22"));
        assertFalse(migration.contains("if (raw == 22)"));
    }

    @Test
    void migrateCommandOpensClaimWizardNotStorageByDefault() throws Exception {
        String admin = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));
        int migrateIdx = admin.indexOf("private void handleMigrate");
        assertTrue(migrateIdx > 0);
        String handle = admin.substring(migrateIdx, migrateIdx + 900);
        assertTrue(handle.contains("args.length == 1"));
        int claimOpen = handle.indexOf("migration().open(player)");
        int storageBranch = handle.indexOf("equals(\"storage\")");
        assertTrue(claimOpen > 0, "default migrate path should open claim wizard");
        assertTrue(storageBranch > claimOpen, "storage migrate must be an explicit sub-action after default open");
        assertTrue(admin.contains("sendAdminHelp"));
        assertTrue(admin.contains("admin_restore_confirm_hint"));
        assertTrue(admin.contains("case \"help\""));
    }

    @Test
    void storageMigrateConfirmsAndCanReturnToDoctor() throws Exception {
        String storage = Files.readString(JAVA_ROOT.resolve("gui/StorageMigrateGUI.java"));
        assertTrue(storage.contains("openFromDoctor"));
        assertTrue(storage.contains("confirm_run"));
        assertTrue(storage.contains("openConfirm"));
        assertTrue(storage.contains("doctor().open"));
        assertTrue(storage.contains("button_back"));
        assertTrue(storage.contains("button_exit"));

        String doctor = Files.readString(JAVA_ROOT.resolve("gui/DoctorRepairGUI.java"));
        assertTrue(doctor.contains("openFromDoctor(player)"));
    }

    @Test
    void staffMenuLoreKeysPresentInAllLanguages() throws Exception {
        List<String> keys = List.of(
                "staff_command_center_lore:",
                "admin_diagnostics_lore:",
                "admin_snapshots_lore:",
                "admin_migration_lore:",
                "admin_audit_lore:",
                "doctor_settlements_lore:",
                "doctor_delinquents_lore:",
                "doctor_storage_migrate_lore:",
                "migration_gui_title:",
                "migration_confirm_import_lore:",
                "storage_migrate_confirm_lore:",
                "admin_set_spawn_lore:",
                "button_admin_convert_server:",
                "admin_convert_server_lore:",
                "convert_select_title:",
                "convert_confirm_title:",
                "convert_confirm_details_lore:",
                "staff_wand_menu_title:",
                "staff_wand_convert_name:",
                "button_admin_expansion_mode:",
                "admin_expansion_mode_lore_queue:",
                "admin_expansion_mode_lore_instant:"
        );
        for (String lang : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar")) {
            String guis = Files.readString(LANG.resolve(lang + "/guis.yml"));
            for (String key : keys) {
                assertTrue(guis.contains(key), lang + " missing " + key);
            }
            assertTrue(guis.contains("What:") || guis.contains("Qué:") || guis.contains("what it doth")
                            || guis.contains("Staff hall") || guis.contains("Centro de staff"),
                    lang + " staff lore should explain purpose");
            String system = Files.readString(LANG.resolve(lang + "/system.yml"));
            assertTrue(system.contains("admin_help_header:"), lang + " missing admin help");
            assertTrue(system.contains("admin_restore_confirm_hint:"), lang + " missing restore confirm hint");
            assertTrue(system.contains("admin_expansion_mode_set_queue:"), lang + " missing expansion mode queue feedback");
            assertTrue(system.contains("admin_expansion_mode_set_instant:"), lang + " missing expansion mode instant feedback");
            assertTrue(system.contains("convert_blocker_no_plot:"), lang + " missing convert blocker");
            assertTrue(system.contains("convert_success:"), lang + " missing convert success");
            assertTrue(system.contains("{TARGET}"), lang + " convert success needs {TARGET}");
        }
    }

    @Test
    void staffMenuExpansionApprovalModeToggleIsUniqueAndPersisted() throws Exception {
        String admin = Files.readString(JAVA_ROOT.resolve("gui/AdminGUI.java"));
        assertTrue(admin.contains("SLOT_TOGGLE_EXPANSION_MODE = 16"));
        assertTrue(admin.contains("toggle_expansion_approval_mode"));
        assertTrue(admin.contains("cycleExpansionApprovalMode"));
        assertTrue(admin.contains("expansions.approval_mode"));
        assertTrue(admin.contains("expansions.approval.mode"));
        assertTrue(admin.contains("\"INSTANT\"") || admin.contains("'INSTANT'"));
        assertTrue(admin.contains("\"QUEUE\"") || admin.contains("'QUEUE'"));
        // Must not collide with existing policy/tool slots.
        assertFalse(admin.contains("SLOT_TOGGLE_LOW_OVERHEAD= 16"));
        assertFalse(admin.contains("SLOT_TOOL_REQUESTS      = 16"));

        String manager = Files.readString(JAVA_ROOT.resolve("expansions/ExpansionRequestManager.java"));
        assertTrue(manager.contains("public ApprovalMode getApprovalMode()"));
        assertTrue(manager.contains("expansions.approval_mode"));
    }

    @Test
    void snapshotLoreMentionsShiftConfirm() throws Exception {
        for (String lang : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar")) {
            String guis = Files.readString(LANG.resolve(lang + "/guis.yml"));
            assertTrue(guis.contains("Shift") || guis.contains("shift"),
                    lang + " snapshot lore should mention Shift-click safety");
        }
    }
}
