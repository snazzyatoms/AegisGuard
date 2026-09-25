package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicBetaContractTest {

    private static final Path CONFIG = Path.of("src/main/resources/config.yml");
    private static final Path SERVICE = Path.of("src/main/java/com/aegisguard/publicbeta/PublicBetaService.java");
    private static final Path WORLDS = Path.of("src/main/java/com/aegisguard/publicbeta/PublicBetaWorldService.java");
    private static final Path PLAYER_GUI = Path.of("src/main/java/com/aegisguard/gui/PlayerGUI.java");
    private static final Path COMMAND = Path.of("src/main/java/com/aegisguard/commands/AegisCommand.java");
    private static final List<String> LANGUAGES = List.of(
            "modern_english", "old_english", "spanish_mx", "spanish_ar",
            "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl");

    @Test
    @SuppressWarnings("unchecked")
    void publicBetaModeShipsEnabledWithDistinctWorldsAndScopedRole() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.readString(CONFIG));
        Map<String, Object> beta = (Map<String, Object>) root.get("public-beta-mode");
        assertEquals(Boolean.TRUE, beta.get("enabled"));
        assertEquals("AegisGuard Public Beta — Play & Test Server", beta.get("server-name"));

        Map<String, Object> worlds = (Map<String, Object>) beta.get("worlds");
        Map<String, Object> hub = (Map<String, Object>) worlds.get("welcome-hub");
        Map<String, Object> play = (Map<String, Object>) worlds.get("play-world");
        Map<String, Object> lab = (Map<String, Object>) worlds.get("test-lab");
        assertEquals("PRIMARY_SERVER_WORLD", hub.get("source"));
        assertEquals("ENTIRE_WORLD", hub.get("protection-mode"));
        assertEquals(64, hub.get("protection-radius"));
        assertEquals("aegis_beta_play", play.get("name"));
        assertEquals("aegis_beta_test_lab", lab.get("name"));
        assertEquals("NORMAL", play.get("generator"));
        assertEquals("NORMAL", lab.get("generator"));
        assertEquals(50, play.get("spawn-protection-radius"));
        assertEquals(50, lab.get("spawn-protection-radius"));

        Map<String, Object> voice = (Map<String, Object>) beta.get("voice-chat");
        assertEquals(Boolean.TRUE, voice.get("enabled"));
        assertEquals("PROXIMITY", voice.get("default-mode"));
        assertEquals(Boolean.TRUE, voice.get("respect-player-groups"));
        assertEquals(Boolean.FALSE, voice.get("hearth-isolates-proximity"));

        Map<String, Object> role = (Map<String, Object>) beta.get("role");
        assertEquals("PUBLIC_BETA_PLAYER", role.get("id"));
        assertEquals(List.of("PUBLIC_PLAYER", "TEST_LAB_FLOW"), role.get("grants"));
    }

    @Test
    void scopedRoleNeverCreatesElevatedPermissionState() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("ROLE_ID = \"PUBLIC_BETA_PLAYER\""));
        assertTrue(source.contains("TEST_LAB_FLOW"));
        assertFalse(source.contains("setOp("));
        assertFalse(source.contains("addAttachment("));
        assertTrue(source.contains("aegis.admin.publicbeta.hub-protection"));
        assertFalse(source.contains("aegis.serverzone.manage"));
        assertFalse(source.contains("setRole("));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyLanguageContainsTheCompletePublicBetaFlow() throws Exception {
        List<String> required = List.of(
                "public_beta_language_confirm_title", "public_beta_language_continue_name",
                "public_beta_language_continue_lore", "public_beta_language_choose_name",
                "public_beta_language_choose_lore", "public_beta_choice_title",
                "public_beta_play_name", "public_beta_play_lore", "public_beta_lab_name",
                "public_beta_lab_lore", "public_beta_choose_language_name",
                "public_beta_choose_language_lore", "public_beta_decide_later_name",
                "public_beta_decide_later_lore", "public_beta_guide_name",
                "public_beta_guide_lore", "public_beta_guide_book_lore",
                "public_beta_guide_details", "public_beta_guide_claiming",
                "public_beta_guide_hub", "public_beta_hub_name", "public_beta_hub_lore",
                "public_beta_entered_hub", "public_beta_hub_interaction_denied",
                "public_beta_hub_claim_denied", "public_beta_hub_protection_usage",
                "public_beta_hub_protection_confirm", "public_beta_hub_protection_missing",
                "public_beta_hub_protection_conflict", "public_beta_hub_protection_success",
                "admin_help_publicbeta_hub", "admin_publicbeta_combined_usage",
                "public_beta_guide_received",
                "public_beta_welcome_title", "public_beta_welcome_header_name",
                "public_beta_welcome_header_lore", "public_beta_welcome_read_name",
                "public_beta_welcome_read_lore", "public_beta_welcome_choices_name",
                "public_beta_welcome_choices_lore", "public_beta_welcome_menu_name",
                "public_beta_welcome_menu_lore", "public_beta_welcome_close_name",
                "public_beta_welcome_close_lore", "public_beta_menu_button_name",
                "public_beta_menu_button_lore", "public_beta_command_unavailable",
                "public_beta_test_lab_denied",
                "public_beta_world_unavailable", "public_beta_entered_test_lab",
                "public_beta_entered_play_world", "public_beta_spawn_welcome_message",
                "public_beta_spawn_claim_denied",
                "public_beta_voice_button_name", "public_beta_voice_button_lore",
                "public_beta_voice_title", "public_beta_voice_proximity_name",
                "public_beta_voice_proximity_lore",
                "public_beta_voice_global_name", "public_beta_voice_global_lore",
                "public_beta_voice_world_name", "public_beta_voice_world_lore",
                "public_beta_voice_selected", "public_beta_voice_saved",
                "public_beta_voice_requirement_name", "public_beta_voice_requirement_lore",
                "voice_chat_client_requirement_lore",
                "settings_public_beta_voice_name", "settings_public_beta_voice_lore",
                "hearth_toggle_lore",
                "public_beta_destination_unavailable_name",
                "button_admin_public_beta_worlds", "public_beta_worlds_title",
                "public_beta_world_review_name", "public_beta_world_cancel_name",
                "public_beta_world_confirm_title", "public_beta_world_confirm_name",
                "public_beta_world_admin_denied", "admin_help_publicbeta");

        for (String language : LANGUAGES) {
            Path file = Path.of("src/main/resources/lang", language, "guis.yml");
            Map<String, Object> values = new Yaml().load(Files.readString(file));
            for (String key : required) {
                assertTrue(values.containsKey(key), language + " missing " + key);
                Object value = values.get(key);
                assertFalse(value == null || String.valueOf(value).isBlank(), language + " blank " + key);
            }
        }
    }

    @Test
    void everyLanguageDocumentsUngroupedProximityVoice() throws Exception {
        List<String> leftoverIsolation = List.of(
                "voice is isolated the same way",
                "the voice is likewise kept apart",
                "la voix est isolée de la même façon",
                "wird die Stimme genauso getrennt",
                "la voce è isolata allo stesso modo",
                "głos jest izolowany tak samo",
                "a voz fica isolada do mesmo jeito",
                "la voz se aísla de la misma forma",
                "la voz se aísla del mismo modo");
        for (String language : LANGUAGES) {
            String guis = Files.readString(Path.of("src/main/resources/lang", language, "guis.yml"));
            for (String leftover : leftoverIsolation) {
                assertFalse(guis.contains(leftover), language + " still says: " + leftover);
            }
            assertTrue(guis.contains("public_beta_voice_proximity_lore:"), language + " missing proximity lore");
            assertTrue(guis.contains("hearth_toggle_lore:"), language + " missing hearth lore");
            assertTrue(guis.contains("public_beta_guide_details:"), language + " missing guide details");
        }
    }

    @Test
    void provisioningIsRestartGatedIdentityCheckedAndNonDestructive() throws Exception {
        String worlds = Files.readString(WORLDS);
        assertTrue(worlds.contains("PENDING_RESTART"));
        assertTrue(worlds.contains("provisionAtStartup"));
        assertTrue(worlds.contains("PersistentDataType.STRING"));
        assertTrue(worlds.contains("owned-by-aegisguard"));
        assertTrue(worlds.contains("An unregistered world folder already exists"));
        assertTrue(worlds.contains("ATOMIC_MOVE"));
        assertFalse(worlds.contains("deleteDirectory"));
        assertFalse(worlds.contains("unloadWorld"));
        assertFalse(worlds.contains("Files.walk"));

        String service = Files.readString(SERVICE);
        assertTrue(service.contains("ProtectionPreset.HUB.apply"));
        assertTrue(service.contains("refusing to overwrite"));
        assertTrue(service.contains("openWorldWizard"));
        assertTrue(service.contains("openVoiceMenu"));
        assertTrue(service.contains("pendingLanguages.remove(id)"));
        assertTrue(service.contains("languageReopenScheduled.remove(id)"));
        assertTrue(service.contains("MAX_INDEXED_HUB_RADIUS = 512"));
        assertTrue(service.contains("ENTIRE_WORLD uses event guards"));
        assertTrue(service.contains("onHubBlockBreak"));
        assertTrue(service.contains("onHubInteraction"));
        assertTrue(service.contains("onHubBucketEmpty"));
        assertTrue(service.contains("onHubClaim"));
        assertTrue(service.contains("findHubExpansionConflict"));
        assertTrue(service.contains("ensureArrivalProtection(Role.PLAY_WORLD)"));
        assertTrue(service.contains("ensureArrivalProtection(Role.TEST_LAB)"));
        assertTrue(service.contains("onArrivalZoneClaim"));
        assertTrue(service.contains("arrival-welcome."));
        assertFalse(service.contains("player.sendTitle"));
        assertTrue(service.contains("refusing to overwrite it"));
        assertTrue(service.contains("relocateArrivalZone"));
        assertTrue(service.contains("Moved protected "));
        assertTrue(service.contains("public_beta_arrival"));
        assertTrue(service.contains("isLegacyArrivalCandidate"));
        assertTrue(service.contains("durable identity flags"));
        assertTrue(service.contains("plot.setFlag(\"safe_zone\", true)"));
        assertTrue(service.contains("plot.setFlag(\"entry\", true)"));
        assertTrue(service.contains("plot.setFlag(\"storm-ward\", true)"));
    }

    @Test
    void guideIsReadableWrittenBookWithMenuAndLegacyUpgradeFlow() throws Exception {
        String service = Files.readString(SERVICE);
        assertTrue(service.contains("new ItemStack(Material.WRITTEN_BOOK)"));
        assertTrue(service.contains("BookMeta.Generation.ORIGINAL"));
        assertTrue(service.contains("guide-book-read"));
        assertTrue(service.contains("player.isSneaking()"));
        assertTrue(service.contains("player.openBook(book)"));
        assertTrue(service.contains("inventory.setItem(slot, createGuideBook(player))"));
        assertTrue(service.contains("updates reach existing testers"));
        assertTrue(service.contains("ChatColor.WHITE.toString(), ChatColor.GRAY.toString()"));
        assertTrue(service.contains("ChatColor.YELLOW.toString(), ChatColor.DARK_AQUA.toString()"));
        assertTrue(service.contains("/ag quickclaim [radius]"));
        assertTrue(service.contains("/ag qc [radius]"));
        assertTrue(service.contains("/ag claim"));
        assertFalse(service.contains("ItemStack guide = new ItemStack(Material.PAPER)"));
    }

    @Test
    void onboardingHasWelcomeScreenAndPermanentMenuRecovery() throws Exception {
        String service = Files.readString(SERVICE);
        String playerGui = Files.readString(PLAYER_GUI);
        String command = Files.readString(COMMAND);

        assertTrue(service.contains("Screen.WELCOME"));
        assertTrue(service.contains("openWelcomeMenu(player)"));
        assertTrue(service.contains("shouldRestoreLanguageOnboarding"));
        assertTrue(service.contains("InventoryCloseEvent.Reason.OPEN_NEW")
                || service.contains("OPEN_NEW"));
        assertTrue(service.contains("TELEPORT"));
        assertTrue(service.contains("showFirstJoinLanguage"));
        assertTrue(service.contains("teleportFuture().whenComplete"));
        assertTrue(service.contains("isLanguageOnboardingHolder"));
        assertTrue(service.contains("welcome_read_guide"));
        assertTrue(service.contains("public void openBetaMenu(Player player)"));
        assertTrue(playerGui.contains("SLOT_PUBLIC_BETA"));
        assertTrue(playerGui.contains("plugin.publicBeta().openBetaMenu(player)"));
        assertTrue(command.contains("case \"beta\", \"publicbeta\""));
        assertTrue(command.contains("case \"hub\""));
        assertTrue(service.contains("return_hub"));
        assertTrue(service.contains("public void returnToHub(Player player)"));
    }
}
