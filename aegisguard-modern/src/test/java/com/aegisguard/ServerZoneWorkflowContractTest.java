package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerZoneWorkflowContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void serverWandUsesAdminConfirmationAndOpensManagement() throws Exception {
        String selection = Files.readString(JAVA_ROOT.resolve("selection/SelectionService.java"));
        String stewardship = Files.readString(JAVA_ROOT.resolve("admin/ServerZoneStewardship.java"));
        assertTrue(selection.contains("isServerWand(item) ? \"/agadmin claim\" : \"/ag claim\""));
        assertTrue(selection.contains("serverZoneStewardship().grantSteward"));
        assertTrue(stewardship.contains("admin.wand.open_settings_after_claim"));
        assertTrue(stewardship.contains("flags().open"));
    }

    @Test
    void createAndConvertShareStewardshipPipeline() throws Exception {
        String stewardship = Files.readString(JAVA_ROOT.resolve("admin/ServerZoneStewardship.java"));
        String convert = Files.readString(JAVA_ROOT.resolve("gui/ConvertToServerGUI.java"));
        String selection = Files.readString(JAVA_ROOT.resolve("selection/SelectionService.java"));
        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));

        assertTrue(stewardship.contains("grantSteward"));
        assertTrue(stewardship.contains("setRole(actorId, \"steward\")"));
        assertTrue(stewardship.contains("server_zone_steward_granted"));
        assertTrue(stewardship.contains("open_settings_after_claim"));

        assertTrue(convert.contains("clearPlayerAccess()"));
        assertTrue(convert.contains("serverZoneStewardship().grantSteward"));
        assertTrue(selection.contains("serverZoneStewardship().grantSteward"));

        // Server zones: permission list / OP trust / bypass — not blanket isAdmin.
        assertTrue(plot.contains("staff_access.server_zone_manage_permissions"));
        assertTrue(plot.contains("admin.trust_operators"));
        int elevateMethod = plot.indexOf("private boolean hasElevatedManagementAccess");
        assertTrue(elevateMethod > 0);
        String elevate = plot.substring(elevateMethod, elevateMethod + 1600);
        assertTrue(elevate.contains("if (isServerZone())"));
        assertTrue(elevate.contains("return false;"));
        // Within the server-zone branch, isAdmin must not grant access.
        int sz = elevate.indexOf("if (isServerZone())");
        int after = elevate.indexOf("if (aegis.isAdmin(player))", sz);
        // isAdmin may appear later for non-server plots; ensure server branch returns before that.
        int retFalse = elevate.indexOf("return false;", sz);
        assertTrue(retFalse > sz);
        assertTrue(after < 0 || after > retFalse);
    }

    @Test
    void stewardRoleIncludesManageMembers() throws Exception {
        Map<String, Object> root;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            root = new Yaml().load(input);
        }
        Map<?, ?> roles = (Map<?, ?>) root.get("roles");
        Map<?, ?> steward = (Map<?, ?>) roles.get("steward");
        @SuppressWarnings("unchecked")
        java.util.List<String> perms = (java.util.List<String>) steward.get("permissions");
        assertTrue(perms.contains("MANAGE"));
        assertTrue(perms.contains("MANAGE_MEMBERS"));
    }

    @Test
    void authorizedStaffCanReopenServerZoneManagement() throws Exception {
        String admin = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));
        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));

        assertTrue(admin.contains("case \"manage\" -> handleServerManage(player)"));
        assertTrue(admin.contains("plot == null || !plot.isServerZone()"));
        assertTrue(admin.contains("!plot.canManage(player, plugin)"));
        assertTrue(admin.contains("plugin.gui().openMain(player)"));
        assertTrue(plot.contains("staff_access.server_zone_manage_permissions"));
    }

    @Test
    void immediateManagementCanBeDisabledByServerOwners() throws Exception {
        Map<String, Object> root;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            root = new Yaml().load(input);
        }
        Map<?, ?> admin = (Map<?, ?>) root.get("admin");
        Map<?, ?> wand = (Map<?, ?>) admin.get("wand");
        assertEquals(Boolean.TRUE, wand.get("open_settings_after_claim"));
    }

    @Test
    void convertToServerGuiUsesPdcRoutingConfirmAndSharedExecutePath() throws Exception {
        String convert = Files.readString(JAVA_ROOT.resolve("gui/ConvertToServerGUI.java"));
        String adminGui = Files.readString(JAVA_ROOT.resolve("gui/AdminGUI.java"));
        String adminCmd = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));
        String selection = Files.readString(JAVA_ROOT.resolve("selection/SelectionService.java"));
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));

        assertTrue(convert.contains("ConvertSelectHolder"));
        assertTrue(convert.contains("ConvertConfirmHolder"));
        assertTrue(convert.contains("StaffWandHolder"));
        assertTrue(convert.contains("tagAction") || convert.contains("plugin.gui().tagAction"));
        assertTrue(convert.contains("convert_confirm_yes"));
        assertTrue(convert.contains("findBlockerKey"));
        assertTrue(convert.contains("executeConvert"));
        assertTrue(convert.contains("changePlotOwner(plot, Plot.SERVER_OWNER_UUID"));
        assertTrue(convert.contains("SERVER_ZONE_CONVERT"));
        assertTrue(convert.contains("case SPAWN ->"));
        assertTrue(convert.contains("case ARENA ->"));
        assertTrue(convert.contains("ProtectionPreset.ARENA.apply"));

        assertTrue(adminGui.contains("open_convert_server"));
        assertTrue(adminGui.contains("SLOT_TOOL_CONVERT"));
        assertTrue(adminGui.contains("convertToServer().openFromStanding"));

        assertTrue(adminCmd.contains("convertGui.openFromStanding(player)"));
        assertTrue(adminCmd.contains("executeConvert(player, plot"));

        assertTrue(selection.contains("convertToServer().openStaffWandMenu(player)"));
        assertTrue(listener.contains("ConvertSelectHolder"));
        assertTrue(listener.contains("ConvertConfirmHolder"));
        assertTrue(listener.contains("StaffWandHolder"));
    }
}
