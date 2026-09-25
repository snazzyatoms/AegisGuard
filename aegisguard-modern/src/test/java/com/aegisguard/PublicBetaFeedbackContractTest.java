package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicBetaFeedbackContractTest {

    private static final Path ROOT = Path.of("src/main");

    @Test
    void playerMenusCommandsAndStaffInboxStayConnected() throws Exception {
        String service = Files.readString(ROOT.resolve("java/com/aegisguard/publicbeta/PublicBetaFeedbackService.java"));
        String beta = Files.readString(ROOT.resolve("java/com/aegisguard/publicbeta/PublicBetaService.java"));
        String player = Files.readString(ROOT.resolve("java/com/aegisguard/gui/PlayerGUI.java"));
        String command = Files.readString(ROOT.resolve("java/com/aegisguard/commands/AegisCommand.java"));
        String admin = Files.readString(ROOT.resolve("java/com/aegisguard/gui/AdminGUI.java"));

        assertTrue(service.contains("public-beta/feedback-inbox.yml"));
        assertTrue(service.contains("instance-id"));
        assertTrue(service.contains("COOLDOWN_MILLIS"));
        assertTrue(service.contains("FeedbackInboxHolder"));
        assertTrue(service.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(service.contains("notification-pending"));
        assertTrue(service.contains("deliverPendingNotifications"));
        assertTrue(service.contains("lastSubmitted.remove(id)"));
        assertTrue(service.contains("ACKNOWLEDGED"));
        assertTrue(service.contains("MySubmissionsHolder"));
        assertTrue(service.contains("entry.playerUuid().equals(player.getUniqueId())"));
        assertTrue(service.contains("\"back_admin\""));
        assertTrue(service.contains("plugin.gui().admin().open(player)"));
        assertTrue(service.contains("SubmissionDetailHolder"));
        assertTrue(service.contains("openSubmissionDetail"));
        assertTrue(service.contains("\"detail_back\""));
        assertTrue(service.contains("\"detail_exit\""));
        assertTrue(beta.contains("my_submissions"));
        for (String action : List.of("report_problem", "send_feedback", "suggest_feature")) {
            assertTrue(beta.contains(action));
        }
        for (String slot : List.of("SLOT_REPORT_PROBLEM", "SLOT_GENERAL_FEEDBACK", "SLOT_FEATURE_SUGGESTION")) {
            assertTrue(player.contains(slot));
        }
        for (String subcommand : List.of("case \"report\"", "case \"feedback\"", "case \"suggest\"")) {
            assertTrue(command.contains(subcommand));
        }
        assertTrue(admin.contains("open_public_beta_feedback"));
        assertTrue(beta.contains("beta_back"));
        assertTrue(beta.contains("beta_exit"));
        assertTrue(beta.contains("giveComplimentaryWorldWand(player, worldKey)"));
        assertTrue(command.contains("giveClaimWand(Player player, boolean complimentaryBetaWand)"));
    }

    @Test
    void everyLanguageHasFeedbackAndNavigationKeys() throws Exception {
        List<String> keys = List.of(
                "public_beta_report_problem_name", "public_beta_feedback_name",
                "public_beta_suggestion_name", "public_beta_back_name", "public_beta_exit_name",
                "public_beta_feedback_prompt", "public_beta_feedback_submitted",
                "public_beta_feedback_inbox_title", "button_admin_public_beta_feedback",
                "admin_help_publicbeta_feedback", "public_beta_guide_feedback");
        keys = new java.util.ArrayList<>(keys);
        keys.add("public_beta_wand_given");
        keys.add("public_beta_guide_wands");
        keys.addAll(List.of("public_beta_category_problem", "public_beta_category_feedback",
                "public_beta_category_suggestion", "public_beta_status_new", "public_beta_status_reviewing",
                "public_beta_status_acknowledged", "public_beta_feedback_player_acknowledged",
                "public_beta_feedback_player_status",
                "public_beta_my_submissions_name", "public_beta_my_submissions_lore",
                "public_beta_my_submissions_title", "public_beta_my_submissions_empty_name",
                "public_beta_my_submissions_empty_lore", "public_beta_feedback_detail_title",
                "public_beta_status_resolved", "public_beta_status_dismissed",
                "public_beta_feedback_status_label", "public_beta_feedback_world_label",
                "public_beta_feedback_from_label", "public_beta_feedback_location_label",
                "public_beta_feedback_message_label", "public_beta_feedback_read_lore",
                "public_beta_feedback_cycle_lore", "public_beta_feedback_previous_name",
                "public_beta_feedback_next_name", "public_beta_feedback_close_name"));
        Path lang = ROOT.resolve("resources/lang");
        try (var styles = Files.list(lang)) {
            for (Path style : styles.filter(Files::isDirectory).toList()) {
                String gui = Files.readString(style.resolve("guis.yml"));
                for (String key : keys) assertTrue(gui.contains(key + ":"), style + " missing " + key);
            }
        }
    }
}
