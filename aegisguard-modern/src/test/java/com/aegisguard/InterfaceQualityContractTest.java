package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceQualityContractTest {

    private static final Path ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    void frontierAndMarketRetainRealRoutesInsideProfessionalHubs() throws Exception {
        String frontier = Files.readString(ROOT.resolve("expansions/ExpansionRequestGUI.java"));
        assertTrue(frontier.contains("ExpansionHolder(Page.FRONTIER), 54"));
        assertTrue(frontier.contains("expansion_frontier_guide_name"));
        assertTrue(frontier.contains("case 37"));
        assertTrue(frontier.contains("openHorizons(player)"));
        for (int radius : List.of(5, 10, 20, 35, 50)) {
            assertTrue(frontier.contains("currentRadius + " + radius));
        }

        String market = Files.readString(ROOT.resolve("gui/LocalMarketGUI.java"));
        assertTrue(market.contains("local_market_guide_name"));
        assertTrue(market.contains("local_market_pulse_name"));
        assertTrue(market.contains("zoneBrowse().open"));
        assertTrue(market.contains("stallBrowse().openList"));
        assertTrue(market.contains("dispatchBridge"));
    }

    @Test
    void zoneRolesAndTravelExposeGuidedDirectWorkflows() throws Exception {
        String zones = Files.readString(ROOT.resolve("gui/ZoningGUI.java"));
        assertTrue(zones.contains("zone_manager_guide_name"));
        assertTrue(zones.contains("zone_selection_status_name"));
        assertTrue(zones.contains("zone_public_preview_name"));
        assertTrue(zones.contains("if (slot == 51)"));

        String roles = Files.readString(ROOT.resolve("gui/RolesGUI.java"));
        assertTrue(roles.contains("roles_capacity_name"));
        assertTrue(roles.contains("roles_permission_model_name"));

        String travel = Files.readString(ROOT.resolve("gui/VisitGUI.java"));
        for (String mode : List.of("WARPS", "OWNED", "TRUSTED", "DISCOVER", "FAVORITES")) {
            assertTrue(travel.contains("case \"mode_" + mode + "\""));
        }
        assertFalse(travel.contains("case \"toggle_mode\""));
    }

    @Test
    void flightIsOwnedOnlyByAscension() throws Exception {
        String flags = Files.readString(ROOT.resolve("gui/PlotFlagsGUI.java"));
        String protection = Files.readString(ROOT.resolve("protection/ProtectionManager.java"));
        String leveling = Files.readString(ROOT.resolve("listeners/LevelingListener.java"));
        String defaults = Files.readString(ROOT.resolve("world/WorldRulesManager.java"));
        assertTrue(flags.contains("claim_settings_flight_ascension_name"));
        assertFalse(flags.contains("togglePaid(player, plot, \"fly\""));
        assertFalse(protection.contains("getFlag(\"fly\""));
        assertFalse(defaults.contains("plot.setFlag(\"fly\""));
        assertFalse(leveling.contains("if (plot.getFlag(\"fly\""));
        assertTrue(leveling.contains("equalsIgnoreCase(\"FLIGHT\")"));
    }

    @Test
    void worldRuntimeControlsUseRealGameRulesAndRollback() throws Exception {
        String world = Files.readString(ROOT.resolve("gui/WorldControlsGUI.java"));
        for (String rule : List.of("DO_MOB_SPAWNING", "DO_DAYLIGHT_CYCLE", "DO_WEATHER_CYCLE",
                "KEEP_INVENTORY", "MOB_GRIEFING")) {
            assertTrue(world.contains("GameRule." + rule));
        }
        assertTrue(world.contains("world.setGameRule(rule, next)"));
        assertTrue(world.contains("world.setGameRule(rule, previous)"));
        assertTrue(world.contains("canManage(player)"));
    }

    @Test
    void staffToolsHaveClearSectionsWithoutChangingActionRouting() throws Exception {
        String admin = Files.readString(ROOT.resolve("gui/AdminGUI.java"));
        assertTrue(admin.contains("staff_command_center_name"));
        assertTrue(admin.contains("staff_policy_section_name"));
        assertTrue(admin.contains("staff_toolbelt_section_name"));
        for (String action : List.of("open_requests", "open_instant_approvals", "open_diagnostics", "open_snapshots",
                "open_world_controls", "open_migration", "toggle_expansion_approval_mode",
                "open_convert_server")) {
            assertTrue(admin.contains("tagAction") && admin.contains(action));
        }
        assertTrue(admin.contains("SLOT_TOGGLE_EXPANSION_MODE"));
        assertTrue(admin.contains("SLOT_TOOL_CONVERT"));
    }

    @Test
    void staffAndPlayerChatFeedbackUsesLanguageKeys() throws Exception {
        String adminCommand = Files.readString(ROOT.resolve("admin/AdminCommand.java"));
        assertFalse(adminCommand.contains("ChatColor.GREEN + \"AegisGuard reload complete.\""));
        assertFalse(adminCommand.contains("ChatColor.RED + \"Players only.\""));
        assertFalse(adminCommand.contains("\"Bypass Mode: \""));
        assertTrue(adminCommand.contains("admin_bypass_mode"));
        assertTrue(adminCommand.contains("admin_wand_received"));
        assertTrue(adminCommand.contains("admin_rentals_cancelled"));
        assertTrue(adminCommand.contains("queueNoticeKey"));

        String notify = Files.readString(ROOT.resolve("commands/NotifyCommand.java"));
        assertFalse(notify.contains("sender.sendMessage(\"Players only.\""));
        assertTrue(notify.contains("notify_status_header"));

        String selection = Files.readString(ROOT.resolve("selection/SelectionService.java"));
        assertTrue(selection.contains("selection_corner1"));
        assertTrue(selection.contains("selection_area_confirm"));
        assertFalse(selection.contains("player.sendMessage(color(\"&aFirst corner selected:"));

        String roles = Files.readString(ROOT.resolve("gui/RolesGUI.java"));
        assertTrue(roles.contains("player_name_invalid"));
        assertTrue(roles.contains("player_never_joined"));

        String guest = Files.readString(ROOT.resolve("guestpass/GuestPassGUI.java"));
        assertTrue(guest.contains("guest_pass_recipient_denied"));

        String health = Files.readString(ROOT.resolve("admin/StaffHealthCheck.java"));
        assertTrue(health.contains("staff_health_title"));
        assertFalse(health.contains("sendMessage(\"§6§lAegisGuard Staff Health Check\")"));

        String visit = Files.readString(ROOT.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("visit_server_warp_default"));
        assertTrue(visit.contains("visit_favorite_add"));
        assertFalse(visit.contains("\"Category: \" +"));
    }
}
