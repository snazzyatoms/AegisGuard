package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiNavigationContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void everySubmenuSourceDeclaresBackAndExitControls() throws Exception {
        List<String> submenus = List.of(
                "gui/AdminGUI.java", "gui/AdminPlotListGUI.java", "gui/ClaimBlockExchangeGUI.java",
                "gui/DoctorRepairGUI.java", "gui/InfoGUI.java", "gui/LevelingGUI.java",
                "gui/LocalMarketGUI.java", "gui/MigrationAdminGUI.java", "gui/PlotAuctionGUI.java",
                "gui/PlotCosmeticsGUI.java", "gui/PlotFlagsGUI.java", "gui/PlotMarketGUI.java",
                "gui/PlotStatusGUI.java", "gui/RolesGUI.java", "gui/SettingsGUI.java",
                "gui/StallBrowseGUI.java", "gui/VisitGUI.java", "gui/ZoneBrowseGUI.java",
                "gui/ZoneTenantGUI.java", "gui/ZoningGUI.java", "expansions/ExpansionRequestGUI.java",
                "expansions/ExpansionRequestAdminGUI.java", "snapshots/SnapshotAdminGUI.java",
                "audit/AuditAdminGUI.java", "guestpass/GuestPassGUI.java", "lockdown/LockdownGUI.java",
                "profile/RealmProfileGUI.java", "guidance/FirstClaimWalkthroughGUI.java",
                "routes/RoutesGUI.java", "routes/RouteAdminGUI.java");

        for (String relative : submenus) {
            String source = Files.readString(JAVA_ROOT.resolve(relative));
            assertTrue(source.contains("button_back"), relative + " must declare a Back control");
            assertTrue(source.contains("button_exit"), relative + " must declare an Exit control");
        }

        String rootMenu = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(rootMenu.contains("button_exit"), "The root menu must provide Exit");
    }

    @Test
    void centralListenerRoutesEveryRoleAndDoctorHolder() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        for (String holder : List.of("DoctorHolder", "RoleManageHolder", "RoleFlagsHolder")) {
            assertTrue(listener.contains("holder instanceof " + holder), holder + " must be protected and routed");
        }
        assertTrue(listener.contains("handleRoleFlagsClick"));
        assertTrue(listener.contains("CLICK_GUARD_NANOS"));
    }

    @Test
    void doctorAsyncResultsCannotReopenAClosedMenuAndRepairRequiresConfirmation() throws Exception {
        String doctor = Files.readString(JAVA_ROOT.resolve("gui/DoctorRepairGUI.java"));
        assertTrue(doctor.contains("isCurrentRequest"));
        assertTrue(doctor.contains("requestId"));
        assertTrue(doctor.contains("doctor_repair_confirm"));
        assertTrue(doctor.contains("confirmation"));
    }
}
