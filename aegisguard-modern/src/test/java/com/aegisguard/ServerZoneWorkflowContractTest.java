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
        assertTrue(selection.contains("isServerWand(item) ? \"/agadmin claim\" : \"/ag claim\""));
        assertTrue(selection.contains("admin.wand.open_settings_after_claim"));
        assertTrue(selection.contains("plugin.gui().flags().open(p, plot)"));
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
}
