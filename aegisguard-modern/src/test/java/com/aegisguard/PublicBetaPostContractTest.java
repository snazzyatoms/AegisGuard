package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicBetaPostContractTest {

    private static final Path ROOT = Path.of("src/main");
    private static final List<String> LANGUAGES = List.of(
            "modern_english", "french_fr", "german_de", "italian_it", "old_english",
            "polish_pl", "portuguese_br", "spanish_ar", "spanish_mx");
    private static final List<String> REQUIRED_KEYS = List.of(
            "post_menu_button_name", "post_menu_button_lore", "post_title", "post_beta_name", "post_beta_lore",
            "post_inbox_name", "post_draft_name", "post_send_name", "post_sent_name", "post_blocked_name",
            "post_future_name", "post_future_lore", "post_drafts_title", "post_recipients_title", "post_confirm_title",
            "post_sent_title", "post_inbox_title", "post_blocked_title", "post_draft_item_name",
            "post_sealed_letter_name", "post_unavailable", "post_draft_given", "post_sent_success",
            "post_received_notice", "post_join_notice", "post_reported", "post_player_blocked", "post_already_sent",
            "post_save_failed");

    @Test
    void postServiceIsPersistentPrivateAndCrashIdempotent() throws Exception {
        String source = Files.readString(ROOT.resolve("java/com/aegisguard/publicbeta/PublicBetaPostService.java"));
        assertTrue(source.contains("public-beta/post-office.yml"));
        assertTrue(source.contains("ATOMIC_MOVE"));
        assertTrue(source.contains("delivered-drafts."));
        assertTrue(source.contains("draftIdKey"));
        assertTrue(source.contains("mail.recipient().equals(player.getUniqueId())"));
        assertTrue(source.contains("PublicBetaFeedbackService.Category.PROBLEM"));
        assertTrue(source.contains("MAX_PAGES"));
        assertTrue(source.contains("physical-delivered"));
        assertTrue(source.contains("if (!saved)"));
        assertTrue(source.contains("post_save_failed"));
        assertTrue(source.contains("lastSent.remove(id)"));
    }

    @Test
    void postRemainsTextOnlyAndPublicBetaScoped() throws Exception {
        String source = Files.readString(ROOT.resolve("java/com/aegisguard/publicbeta/PublicBetaPostService.java"));
        String playerMenu = Files.readString(ROOT.resolve("java/com/aegisguard/gui/PlayerGUI.java"));
        String config = Files.readString(ROOT.resolve("resources/config.yml"));
        assertTrue(source.contains("hasBetaPlayerRole"));
        assertTrue(source.contains("Material.WRITABLE_BOOK"));
        assertTrue(source.contains("Material.PAPER"));
        assertTrue(config.contains("attachments are intentionally unsupported"));
        assertTrue(config.contains("send-cooldown-seconds: 30"));
        assertTrue(playerMenu.contains("SLOT_AEGIS_POST"));
        assertTrue(playerMenu.contains("post_menu_button_name"));
        assertTrue(playerMenu.contains("plugin.publicBetaPost().open(player)"));
        assertTrue(playerMenu.contains("hasBetaPlayerRole(player.getUniqueId())"));
    }

    @Test
    void everyLanguageHasNativePostKeys() throws Exception {
        Yaml yaml = new Yaml();
        for (String language : LANGUAGES) {
            Map<?, ?> values = yaml.load(Files.readString(ROOT.resolve("resources/lang/" + language + "/guis.yml")));
            for (String key : REQUIRED_KEYS) assertTrue(values.containsKey(key), language + " missing " + key);
        }
    }
}
