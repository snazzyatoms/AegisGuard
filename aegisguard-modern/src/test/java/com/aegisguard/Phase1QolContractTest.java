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
 * Contract coverage for 1.4 Phase 1: Settings origin, Quick-Claim, and max-claims guardrail.
 */
class Phase1QolContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaAndQuickClaimDefaultsShip() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1309, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> claims = (Map<String, Object>) config.get("claims");
        Map<String, Object> quick = (Map<String, Object>) claims.get("quick_claim");
        assertEquals(Boolean.TRUE, quick.get("enabled"));
        assertEquals(5, ((Number) quick.get("default_radius")).intValue());
        assertEquals(25, ((Number) quick.get("max_radius")).intValue());

        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308")
                || migration.contains("CURRENT_SCHEMA = 1309"));
    }

    @Test
    void settingsLiveOnlyOnHubAndStaffMenuWithOriginAwareBack() throws Exception {
        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("if (!showBack)"), "Settings must paint only on the HUB footer");
        assertTrue(player.contains("raw == SLOT_SETTINGS && page == Page.HUB"));
        assertTrue(player.contains("ReturnTo.PLAYER_MENU"));
        assertFalse(player.contains("plugin.gui().settings().open(player);"),
                "Player hub must pass an origin when opening Settings");

        String admin = Files.readString(JAVA.resolve("gui/AdminGUI.java"));
        assertTrue(admin.contains("open_settings"));
        assertTrue(admin.contains("ReturnTo.STAFF_MENU"));
        assertTrue(admin.contains("SLOT_NAV_SETTINGS"));

        String settings = Files.readString(JAVA.resolve("gui/SettingsGUI.java"));
        assertTrue(settings.contains("enum ReturnTo"));
        assertTrue(settings.contains("PLAYER_MENU"));
        assertTrue(settings.contains("STAFF_MENU"));
        assertTrue(settings.contains("dest == ReturnTo.STAFF_MENU"));
        assertTrue(settings.contains("plugin.gui().admin().open(player)"));
        assertTrue(settings.contains("languageSelect().open(player, plot"));
    }

    @Test
    void quickClaimReusesConfirmClaimAndCommandEconomy() throws Exception {
        String selection = Files.readString(JAVA.resolve("selection/SelectionService.java"));
        assertTrue(selection.contains("setSelectionAround"));
        assertTrue(selection.contains("quickClaim"));
        assertTrue(selection.contains("confirmClaim(p)"));
        assertTrue(selection.contains("denyClaim"));
        assertTrue(selection.contains("hasClaimLimitBypass"));
        assertTrue(selection.contains("max_claims_reached"));
        assertTrue(selection.contains("getWorldMaxClaims"));

        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("\"quickclaim\""));
        assertTrue(command.contains("handleQuickClaim"));
        assertTrue(command.contains("case \"quickclaim\", \"qc\""));
        assertTrue(command.contains("public void runClaim"));
        assertTrue(command.contains("setSelectionAround(p, radius)"));
        assertTrue(command.contains("handleClaim(p)"));

        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("showQuickClaim"));
        assertTrue(player.contains("handleQuickClaimClick"));
        assertTrue(player.contains("playerCommand().runClaim"));
        assertTrue(player.contains("button_quick_claim"));
    }

    @Test
    void languageAndSettlementsKeepSettingsOrigin() throws Exception {
        String language = Files.readString(JAVA.resolve("gui/LanguageSelectGUI.java"));
        assertTrue(language.contains("getSettingsReturn"));
        assertTrue(language.contains("settings().open(player, plot, settingsReturn)"));

        String settlements = Files.readString(JAVA.resolve("gui/SettlementsInboxGUI.java"));
        assertTrue(settlements.contains("openFromSettings"));
        assertTrue(settlements.contains("settings().open(player, holder.getSettingsReturn())"));

        String listener = Files.readString(JAVA.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("castHolder.getReturnTo()"));
        assertTrue(listener.contains("castHolder.getSettingsReturn()"));
    }
}
