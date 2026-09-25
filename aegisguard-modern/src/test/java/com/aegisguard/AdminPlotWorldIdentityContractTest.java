package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPlotWorldIdentityContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    void staffPlotRegistryShowsFriendlyRoleAndExactWorldFolder() throws Exception {
        String source = Files.readString(MAIN.resolve("java/com/aegisguard/gui/AdminPlotListGUI.java"));
        assertTrue(source.contains("admin_plot_lore_world_type"));
        assertTrue(source.contains("admin_plot_lore_world_folder"));
        assertTrue(source.contains("Role.WELCOME_HUB"));
        assertTrue(source.contains("Role.PLAY_WORLD"));
        assertTrue(source.contains("Role.TEST_LAB"));
        assertTrue(source.contains("comparing(Plot::getWorld"));
    }

    @Test
    void plotWorldIdentityTextExistsInEveryLanguage() throws Exception {
        List<String> keys = List.of(
                "admin_plot_item_name", "admin_plot_lore_id", "admin_plot_lore_world_type",
                "admin_plot_lore_world_folder", "admin_plot_lore_bounds", "admin_plot_lore_to",
                "admin_plot_world_role_hub", "admin_plot_world_role_play",
                "admin_plot_world_role_test_lab", "admin_plot_world_role_other",
                "admin_plot_world_unknown", "admin_server_zone_tag");
        try (var styles = Files.list(MAIN.resolve("resources/lang"))) {
            for (Path style : styles.filter(Files::isDirectory).toList()) {
                String gui = Files.readString(style.resolve("guis.yml"));
                for (String key : keys) assertTrue(gui.contains(key + ":"), style + " missing " + key);
            }
        }
    }
}
