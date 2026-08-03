package com.aegisguard;

import com.aegisguard.config.ConfigMigrationService;
import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolishPackContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void schemaIs1281AndHooksShipDisabled() throws Exception {
        assertEquals(1281, ConfigMigrationService.CURRENT_SCHEMA);

        Yaml yaml = new Yaml();
        Map<?, ?> config;
        try (InputStream in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1281, ((Number) config.get("config_schema")).intValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> hooks = (Map<String, Object>) config.get("hooks");
        assertFalse((Boolean) ((Map<?, ?>) hooks.get("dynmap")).get("enabled"));
        assertFalse((Boolean) ((Map<?, ?>) hooks.get("discord")).get("enabled"));
        assertFalse((Boolean) ((Map<?, ?>) hooks.get("bluemap")).get("enabled"));
        assertFalse((Boolean) ((Map<?, ?>) hooks.get("pl3xmap")).get("enabled"));
        @SuppressWarnings("unchecked")
        Map<String, Object> compat = (Map<String, Object>) hooks.get("protection_compat");
        assertFalse((Boolean) compat.get("enabled"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) compat.get("plugins");
        for (String id : new String[]{"worldguard", "griefprevention", "griefdefender", "towny", "residence"}) {
            assertFalse((Boolean) ((Map<?, ?>) plugins.get(id)).get("enabled"), id + " should ship disabled");
        }
    }

    @Test
    void roleNicknameRoundTripAndCapacityHelpers() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 10, 10, 0L);
        plot.setMaxMembers(2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        plot.setRole(a, "member");
        plot.setRoleNickname(a, "Best Friend");
        assertEquals("Best Friend", plot.getRoleNickname(a));
        assertEquals(1, plot.countTrustedMembers());
        assertFalse(plot.isAtMemberCapacity());

        plot.setRole(b, "trusted");
        assertTrue(plot.isAtMemberCapacity());
        plot.setRole(c, "builder");
        // capacity helper is advisory; assignment still allowed at model layer
        assertEquals(3, plot.countTrustedMembers());

        String blob = plot.serializeRoleNicknames();
        Plot restored = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 10, 10, 0L);
        restored.deserializeRoleNicknames(blob);
        assertEquals("Best Friend", restored.getRoleNickname(a));

        plot.removeRole(a);
        assertNull(plot.getRoleNickname(a));
    }

    @Test
    void rolesCatalogTrustedAndCoOwnerManageMembers() throws Exception {
        String config = Files.readString(RESOURCES.resolve("config.yml"));
        assertTrue(config.contains("\n  trusted:"));
        assertTrue(config.contains("MANAGE_MEMBERS"));
        String rolesGui = Files.readString(JAVA_ROOT.resolve("gui/RolesGUI.java"));
        assertTrue(rolesGui.contains("equalsIgnoreCase(\"owner\")"));
        assertTrue(rolesGui.contains("isAtMemberCapacity"));
        assertTrue(rolesGui.contains("setRoleNickname"));
    }

    @Test
    void animalsHandlersConsultPermissionAndRentConfirmIsWired() throws Exception {
        String protection = Files.readString(JAVA_ROOT.resolve("protection/ProtectionManager.java"));
        assertTrue(protection.contains("hasPermission(p.getUniqueId(), \"ANIMALS\", plugin)"));

        String market = Files.readString(JAVA_ROOT.resolve("gui/PlotMarketGUI.java"));
        assertTrue(market.contains("openRentConfirm"));
        assertTrue(market.contains("rentConfirm().openPlotRent"));

        String zones = Files.readString(JAVA_ROOT.resolve("gui/ZoneBrowseGUI.java"));
        assertTrue(zones.contains("rentConfirm().openZoneRent"));

        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("RentConfirmHolder"));
        assertTrue(listener.contains("MyRentalsHolder"));

        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/RentConfirmGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/MyRentalsGUI.java")));

        String mapHooks = Files.readString(JAVA_ROOT.resolve("hooks/MapHookManager.java"));
        assertTrue(mapHooks.contains("hooks.dynmap.enabled\", false)"));
        String protectHooks = Files.readString(JAVA_ROOT.resolve("hooks/protection/ProtectionHookManager.java"));
        assertTrue(protectHooks.contains("hooks.protection_compat.enabled\", false)"));
    }

    @Test
    void javaDocsSayJava21() throws Exception {
        Path rootDoc = Path.of("../DIRECT_RELEASES.md");
        assertTrue(Files.exists(rootDoc), "repo-root DIRECT_RELEASES.md must exist");
        String direct = Files.readString(rootDoc);
        assertTrue(direct.contains("Java 21"));
        assertFalse(direct.contains("Java 17"));
    }
}
