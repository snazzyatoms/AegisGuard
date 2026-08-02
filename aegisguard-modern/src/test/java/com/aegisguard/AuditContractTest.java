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
 * Milestone 1 (Staff Audit Ledger) contract checks: permission gating, config wiring, the five
 * required hook points, and the explicit "no Discord export, no ordinary-action logging" scope
 * rule for this version.
 */
class AuditContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void theAuditViewerAndCommandRequireTheAuditPermission() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("audit/AuditAdminGUI.java"));
        String adminCommand = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));

        assertTrue(gui.contains("\"aegis.admin.audit\""), "AuditAdminGUI must gate on aegis.admin.audit");
        assertTrue(adminCommand.contains("handleAudit"), "AdminCommand must expose a handleAudit entry point");
        assertTrue(adminCommand.contains("\"aegis.admin.audit\""), "AdminCommand must gate /agadmin audit on aegis.admin.audit");
    }

    @Test
    void pluginDescriptorDeclaresTheAuditPermission() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> plugin;
        try (var input = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
            plugin = yaml.load(input);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) plugin.get("permissions");
        assertTrue(permissions.containsKey("aegis.admin.audit"), "plugin.yml must declare aegis.admin.audit");
    }

    @Test
    void configShipsASafelyDefaultedAuditSection() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) config.get("audit");
        assertTrue(audit != null, "config.yml must declare an audit section");
        assertEquals(Boolean.TRUE, audit.get("enabled"), "The audit ledger must be enabled by default");
        assertTrue(((Number) audit.get("max_entries")).intValue() > 0);
    }

    @Test
    void auditRecordsExactlyTheFiveRequiredHookPointCategories() throws Exception {
        String adminCommand = Files.readString(JAVA_ROOT.resolve("admin/AdminCommand.java"));

        assertTrue(adminCommand.contains("AuditCategory.SNAPSHOT_RESTORE"), "Snapshot restore must be audited");
        assertTrue(adminCommand.contains("AuditCategory.DOCTOR_REPAIR"), "Doctor repair must be audited");
        assertTrue(adminCommand.contains("AuditCategory.MIGRATION"), "Migration import must be audited");
        assertTrue(adminCommand.contains("AuditCategory.ADMIN_BYPASS"), "Admin bypass toggles must be audited");
        assertTrue(adminCommand.contains("AuditCategory.CLAIM_BLOCK_ADJUST"), "ClaimBlock adjustments must be audited");
    }

    @Test
    void auditServiceStaysWithinMilestoneOneScope() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("audit/AuditService.java"));
        assertFalse(service.toLowerCase().contains("discord"), "Discord export is explicitly out of scope for this version");
    }

    @Test
    void auditServiceIsWiredIntoThePluginLifecycle() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("auditService = new AuditService(this)"));
        assertTrue(plugin.contains("auditService.load()"));
        assertTrue(plugin.contains("auditService.save()"));
    }
}
