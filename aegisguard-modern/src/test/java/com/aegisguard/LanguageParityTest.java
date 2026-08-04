package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageParityTest {

    private static final Path LANG_ROOT = Path.of("src/main/resources/lang");
    private static final List<String> LANGUAGES = List.of(
            "modern_english", "old_english", "spanish_mx", "spanish_ar",
            "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl");
    private static final List<String> BUNDLES = List.of(
            "guis.yml", "system.yml", "upgrades.yml", "expansions.yml");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Z0-9_]+}");
    private static final Pattern GUIDE_KEY = Pattern.compile("\"(codex_[a-zA-Z0-9_]+)\"");

    @Test
    void everyLanguageContainsEveryModernEnglishKeyWithMatchingPlaceholders() throws Exception {
        Map<String, Object> reference = loadLanguage("modern_english");
        for (String language : LANGUAGES) {
            Map<String, Object> translated = loadLanguage(language);
            List<String> missing = reference.keySet().stream()
                    .filter(key -> !translated.containsKey(key))
                    .sorted()
                    .toList();
            assertTrue(missing.isEmpty(), () -> language + " is missing keys: " + missing);

            List<String> mismatched = new ArrayList<>();
            for (Map.Entry<String, Object> entry : reference.entrySet()) {
                Set<String> expected = placeholders(entry.getValue());
                Set<String> actual = placeholders(translated.get(entry.getKey()));
                if (!expected.equals(actual)) {
                    mismatched.add(entry.getKey() + " expected=" + expected + " actual=" + actual);
                }
            }
            assertTrue(mismatched.isEmpty(), () -> language + " has mismatched placeholders: " + mismatched);
        }
    }

    @Test
    void allReleaseMenusAndCommandsAreLocalized() throws Exception {
        Set<String> required = Set.of(
                "button_back", "button_exit", "button_back_admin", "button_back_menu",
                "button_prev", "button_next", "button_page", "back_lore", "exit_lore",
                "doctor_menu_title", "doctor_confirm_title", "doctor_summary_name",
                "doctor_scan_name", "doctor_report_name", "doctor_repair_name",
                "doctor_repair_confirm_name", "doctor_issue_name", "doctor_no_issues",
                "admin_wand_doctor_hint", "rental_contract_title", "rental_contract_renewed",
                "rental_contract_cancelled", "discovery_disabled", "discovery_visible",
                "activity_title", "activity_empty", "doctor_scan_running",
                "doctor_repair_running", "doctor_repair_complete", "doctor_report_saved",
                "my_rentals_guide_lore", "my_rentals_full_actions", "my_rentals_zone_actions",
                "plot_status_button_lore", "plot_status_button_locked_lore",
                "alliance_roster_title", "alliance_roster_members_line", "alliance_roster_invites_line",
                "alliance_roster_leader", "alliance_roster_member", "alliance_roster_pending_name",
                "alliance_unknown_player", "guest_pass_revoke_hint", "zone_tenant_evict_hint",
                "revoke_guest_pass_lore", "zone_tenant_evict_lore");

        for (String language : LANGUAGES) {
            Map<String, Object> translated = loadLanguage(language);
            List<String> missing = required.stream().filter(key -> !translated.containsKey(key)).sorted().toList();
            assertTrue(missing.isEmpty(), () -> language + " is missing release keys: " + missing);
            for (String key : required) {
                assertFalse(isBlankValue(translated.get(key)), language + " has a blank " + key);
            }
        }
    }

    @Test
    void consoleDiscordAndActivityLogKeysExistInEveryPack() throws Exception {
        Set<String> required = Set.of(
                "log_enabled", "log_disabled", "log_reloaded", "log_codex_initialized",
                "log_codex_init_failed", "log_banned_player_detected", "log_banned_plots_removed",
                "log_discord_webhook_failed", "log_admin_audit", "log_admin_console_reload",
                "log_settlement_queued", "log_territory_life_save_failed",
                "log_snapshots_created", "log_snapshots_rolled_back", "log_snapshots_loaded",
                "log_map_dynmap_hooked", "log_map_bluemap_hooked", "log_map_pl3xmap_hooked",
                "log_plot_sale_failed", "log_plot_rental_failed", "log_convert_audit",
                "discord_ban_wipe_title", "discord_ban_wipe_description",
                "discord_ban_wipe_footer", "discord_claim_title_plot", "discord_claim_title_server",
                "discord_claim_description", "discord_event_lockdown_title",
                "discord_event_market_sale_title", "discord_event_rental_start_title",
                "discord_event_rental_end_title", "discord_event_zone_rental_end_title",
                "discord_event_guest_pass_title",
                "activity_detail_plot_claimed", "activity_detail_plot_deleted",
                "activity_detail_plot_sold", "activity_detail_rental_started",
                "activity_detail_rental_expired", "activity_detail_rental_cancelled",
                "activity_type_plot_claimed", "activity_type_plot_sold",
                "activity_type_rental_started", "rental_contract_ended_early_notice",
                "zone_rent_left_landlord_notice");

        for (String language : LANGUAGES) {
            Map<String, Object> translated = loadLanguage(language);
            List<String> missing = required.stream()
                    .filter(key -> !translated.containsKey(key) || isBlankValue(translated.get(key)))
                    .sorted()
                    .toList();
            assertTrue(missing.isEmpty(), () -> language + " missing console/discord/activity keys: " + missing);
        }
    }

    @Test
    void footerNavigationKeysStayNonBlankAcrossAllPacks() throws Exception {
        Set<String> footerKeys = Set.of(
                "button_back", "button_exit", "button_prev", "button_next", "button_page",
                "button_previous_page", "button_next_page", "back_lore", "exit_lore");
        for (String language : LANGUAGES) {
            Map<String, Object> translated = loadLanguage(language);
            for (String key : footerKeys) {
                if (!translated.containsKey(key)) continue; // optional aliases may be absent
                assertFalse(isBlankValue(translated.get(key)), language + " blank footer key " + key);
            }
            for (String required : List.of("button_back", "button_exit", "back_lore", "exit_lore")) {
                assertTrue(translated.containsKey(required), language + " missing " + required);
                assertFalse(isBlankValue(translated.get(required)), language + " blank " + required);
            }
        }
    }

    @Test
    void newLanguagePacksAreNotSilentEnglishCopies() throws Exception {
        Map<String, Object> english = loadLanguage("modern_english");
        List<String> probeKeys = List.of(
                "button_back", "menu_title", "button_claim_land", "settings_language_name",
                "no_perm", "players_only", "button_exit",
                "main_section_territory_name", "main_section_access_name",
                "main_section_economy_name", "main_section_explore_name",
                "back_lore", "exit_lore");
        for (String language : List.of(
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> translated = loadLanguage(language);
            int identical = 0;
            int compared = 0;
            for (String key : probeKeys) {
                if (!english.containsKey(key) || !translated.containsKey(key)) continue;
                compared++;
                if (normalizedValue(english.get(key)).equals(normalizedValue(translated.get(key)))) {
                    identical++;
                }
            }
            assertTrue(compared >= 8, language + " missing probe keys for translation check");
            final int identicalCount = identical;
            final int comparedCount = compared;
            assertTrue(identicalCount == 0,
                    () -> language + " still looks like English for core UI probes ("
                            + identicalCount + "/" + comparedCount + " identical)");
        }
    }

    @Test
    void newLanguagePackLoreAndButtonsAreMostlyTranslated() throws Exception {
        Map<String, Object> english = loadLanguage("modern_english");
        // Proper nouns / brands / config paths that may remain English.
        Set<String> allowExact = Set.of(
                "AegisGuard", "Vault", "Dynmap", "LuckPerms", "PlaceholderAPI",
                "WorldGuard", "GriefPrevention", "GriefDefender", "Towny", "Essentials",
                "ClaimBlocks", "TradeStall", "N/A", "ON", "OFF", "Hub", "Auto", "Arena",
                "Admin", "Shop", "Nether", "Console", "System", "Dawnreach", "Realmforge",
                "Bastion", "Stonewright", "Wayfinder");
        Pattern colorOrToken = Pattern.compile(
                "&[0-9a-fk-orx]|§[0-9a-fk-orx]|\\{[A-Z0-9_]+}|AegisGuard|plugins/AegisGuard/[\\w./-]+",
                Pattern.CASE_INSENSITIVE);

        for (String language : List.of(
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> translated = loadLanguage(language);
            int compared = 0;
            int identical = 0;
            List<String> samples = new ArrayList<>();
            for (Map.Entry<String, Object> entry : english.entrySet()) {
                String key = entry.getKey();
                String keyLower = key.toLowerCase();
                boolean highVisibility = keyLower.contains("lore")
                        || keyLower.startsWith("button_")
                        || keyLower.endsWith("_title")
                        || keyLower.endsWith("_name")
                        || keyLower.startsWith("main_section_");
                if (!highVisibility) continue;
                if (keyLower.startsWith("style_")) continue;
                if (!translated.containsKey(key)) continue;

                String eng = normalizedValue(entry.getValue());
                String loc = normalizedValue(translated.get(key));
                if (eng.isBlank() || loc.isBlank()) continue;

                String engPlain = colorOrToken.matcher(eng).replaceAll(" ").replaceAll("\\s+", " ").trim();
                if (engPlain.length() < 4) continue;
                if (allowExact.contains(engPlain)) continue;
                // Skip rows that are only a placeholder label like "{PLAYER}".
                if (engPlain.matches("\\{?[A-Z0-9_]+}?")) continue;

                compared++;
                if (eng.equals(loc)) {
                    identical++;
                    if (samples.size() < 12) samples.add(key);
                }
            }
            assertTrue(compared >= 80, language + " expected many lore/button keys, found " + compared);
            double ratio = compared == 0 ? 0.0 : (identical * 1.0 / compared);
            final int identicalCount = identical;
            final int comparedCount = compared;
            assertTrue(ratio <= 0.08,
                    () -> language + " has too many English-copy lore/button values: "
                            + identicalCount + "/" + comparedCount
                            + " (" + String.format("%.1f", ratio * 100) + "%). Samples: " + samples);
        }
    }

    @Test
    void guardianGuideNeverFallsBackFromSpanishToEnglish() throws Exception {
        String infoGui = Files.readString(Path.of("src/main/java/com/aegisguard/gui/InfoGUI.java"));
        Set<String> guideKeys = new HashSet<>();
        Matcher matcher = GUIDE_KEY.matcher(infoGui);
        while (matcher.find()) guideKeys.add(matcher.group(1));

        Map<String, Object> mexican = loadLanguage("spanish_mx");
        Map<String, Object> argentinian = loadLanguage("spanish_ar");
        List<String> missingMexican = guideKeys.stream()
                .filter(key -> !mexican.containsKey(key))
                .sorted()
                .toList();
        assertTrue(missingMexican.isEmpty(), () -> "spanish_mx is missing Guardian Guide keys: " + missingMexican);

        // Argentinian wording takes priority; complete Mexican Spanish is the
        // deliberate same-language fallback for newly introduced guide cards.
        List<String> missingEffectiveArgentinian = guideKeys.stream()
                .filter(key -> !argentinian.containsKey(key) && !mexican.containsKey(key))
                .sorted()
                .toList();
        assertTrue(missingEffectiveArgentinian.isEmpty(),
                () -> "spanish_ar cannot resolve Guardian Guide keys in Spanish: " + missingEffectiveArgentinian);
    }

    @Test
    void guardianGuideHelpAndStaffKeysExistInEveryPack() throws Exception {
        Set<String> required = extractGuideHelpStaffKeys();
        assertTrue(required.size() >= 100, "expected a large guide/help/staff key set, found " + required.size());

        Map<String, Object> english = loadLanguage("modern_english");
        List<String> missingEnglish = required.stream().filter(key -> !english.containsKey(key)).sorted().toList();
        assertTrue(missingEnglish.isEmpty(),
                () -> "modern_english missing guide/help/staff keys: " + missingEnglish);

        for (String language : LANGUAGES) {
            Map<String, Object> translated = loadLanguage(language);
            List<String> missing = required.stream()
                    .filter(key -> !translated.containsKey(key) || isBlankValue(translated.get(key)))
                    .sorted()
                    .toList();
            assertTrue(missing.isEmpty(), () -> language + " missing guide/help/staff keys: " + missing);
        }
    }

    @Test
    void guideHelpAndStaffLoreAreTranslatedInNonEnglishPacks() throws Exception {
        Map<String, Object> english = loadLanguage("modern_english");
        Set<String> required = extractGuideHelpStaffKeys();
        Pattern colorOrToken = Pattern.compile(
                "&[0-9a-fk-orx]|§[0-9a-fk-orx]|\\{[A-Z0-9_]+}|AegisGuard|/ag(?:admin)?(?:\\s+[\\w<>|-]+)*",
                Pattern.CASE_INSENSITIVE);

        for (String language : List.of(
                "spanish_mx", "spanish_ar", "portuguese_br", "french_fr",
                "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> translated = loadLanguage(language);
            int compared = 0;
            int identical = 0;
            List<String> samples = new ArrayList<>();
            for (String key : required) {
                if (!english.containsKey(key) || !translated.containsKey(key)) continue;
                String keyLower = key.toLowerCase();
                boolean check = keyLower.contains("lore")
                        || keyLower.endsWith("_name")
                        || keyLower.endsWith("_title")
                        || keyLower.startsWith("button_")
                        || keyLower.startsWith("staff_")
                        || keyLower.startsWith("walkthrough_");
                if (!check) continue;

                String eng = normalizedValue(english.get(key));
                String loc = normalizedValue(translated.get(key));
                if (eng.isBlank() || loc.isBlank()) continue;
                String engPlain = colorOrToken.matcher(eng).replaceAll(" ").replaceAll("\\s+", " ").trim();
                if (engPlain.length() < 4) continue;
                if (engPlain.matches("\\{?[A-Z0-9_]+}?")) continue;
                // Command-syntax help rows may stay English.
                if (engPlain.startsWith("/ag") || engPlain.contains("griefprevention")
                        || engPlain.contains("griefdefender")) {
                    continue;
                }
                // Product/feature brand labels intentionally kept identical.
                String brand = engPlain.replace(" ", "");
                if (brand.equalsIgnoreCase("ClaimBlocks")
                        || brand.equalsIgnoreCase("TradeStalls")
                        || brand.equalsIgnoreCase("TradeStall")) {
                    continue;
                }

                compared++;
                if (eng.equals(loc)) {
                    identical++;
                    if (samples.size() < 15) samples.add(key);
                }
            }
            assertTrue(compared >= 40, language + " expected many guide/staff UI values, found " + compared);
            final int identicalCount = identical;
            final int comparedCount = compared;
            assertTrue(identicalCount == 0,
                    () -> language + " still has English-copy guide/help/staff UI: "
                            + identicalCount + "/" + comparedCount + ". Samples: " + samples);
        }
    }

    private Set<String> extractGuideHelpStaffKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        List<Path> sources = List.of(
                Path.of("src/main/java/com/aegisguard/gui/InfoGUI.java"),
                Path.of("src/main/java/com/aegisguard/gui/AdminGUI.java"),
                Path.of("src/main/java/com/aegisguard/guidance/FirstClaimWalkthroughGUI.java"));
        // Only keys that are passed to tr/trList/title/sendKey helpers.
        Pattern keyPattern = Pattern.compile(
                "(?:\\.tr|\\.trList|\\.title|sendKey)\\(\\s*(?:player|p)\\s*,\\s*\"((?:codex_|walkthrough_|staff_|button_|admin_|back_|exit_)[a-z0-9_]+)\"");
        for (Path source : sources) {
            Matcher matcher = keyPattern.matcher(Files.readString(source));
            while (matcher.find()) keys.add(matcher.group(1));
        }
        // InfoGUI section/card helpers pass keys as bare string args before fallbacks.
        Pattern infoKeyPattern = Pattern.compile(
                "\"((?:codex_|button_|back_|exit_)[a-z0-9_]+)\"");
        Matcher infoMatcher = infoKeyPattern.matcher(
                Files.readString(Path.of("src/main/java/com/aegisguard/gui/InfoGUI.java")));
        while (infoMatcher.find()) keys.add(infoMatcher.group(1));

        // Walkthrough builds page titles via concatenation.
        keys.add("walkthrough_menu_title_page1");
        keys.add("walkthrough_menu_title_page2");

        // Critical footer / language feedback keys used across menus.
        keys.addAll(List.of(
                "back_lore", "exit_lore", "back_menu_lore",
                "exchange_unavailable", "exchange_disabled",
                "snapshots_unavailable", "snapshots_disabled_config",
                "language_invalid_style", "language_set_to",
                "expansion_manager_unavailable",
                "admin_refreshing_lang", "admin_refresh_lang_complete",
                "admin_setting_enabled", "admin_setting_disabled",
                "settings_notif_mode_action_bar", "settings_notif_mode_chat", "settings_notif_mode_title"));

        // Hardcoded-string localization pass keys (staff/player chat + travel GUI).
        keys.addAll(List.of(
                "admin_console_reload_complete", "admin_bypass_mode", "admin_wand_received",
                "admin_wand_usage_hint", "admin_claim_need_selection", "selection_corner1",
                "selection_corner2", "selection_area_confirm", "player_name_invalid",
                "player_never_joined", "guest_pass_recipient_denied", "staff_health_title",
                "staff_health_ok", "notify_admin_update_title", "notify_status_header",
                "rental_expire_soon_renter", "rental_auto_renewed_renter",
                "rental_plot_rented_owner", "visit_server_warp_default",
                "visit_favorite_add", "visit_favorite_remove", "group_plots_join_name",
                "admin_rentals_cancelled", "admin_merge_success", "admin_blocks_updated"));

        // Console / Discord / territory-activity localization pass.
        keys.addAll(List.of(
                "log_enabled", "log_disabled", "log_reloaded", "log_codex_initialized",
                "log_banned_player_detected", "log_banned_plots_removed",
                "log_discord_webhook_failed", "log_admin_audit", "log_admin_console_reload",
                "log_settlement_queued", "log_snapshots_created", "log_map_dynmap_hooked",
                "log_plot_sale_failed", "log_convert_audit",
                "discord_ban_wipe_title", "discord_ban_wipe_description",
                "discord_claim_title_plot", "discord_claim_title_server",
                "discord_event_lockdown_title", "discord_event_market_sale_title",
                "discord_event_rental_start_title", "discord_event_rental_end_title",
                "discord_event_guest_pass_title",
                "activity_detail_plot_claimed", "activity_detail_plot_sold",
                "activity_detail_rental_started", "activity_detail_rental_expired",
                "activity_type_plot_claimed", "activity_type_plot_sold",
                "rental_contract_ended_early_notice", "zone_rent_left_landlord_notice"));

        // Action-tag / concatenation fragments are not language keys.
        keys.remove("back_main");
        keys.remove("walkthrough_menu_title_page");
        return keys;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadLanguage(String language) throws Exception {
        Map<String, Object> flattened = new HashMap<>();
        Yaml yaml = new Yaml();
        for (String bundle : BUNDLES) {
            Object loaded = yaml.load(Files.readString(LANG_ROOT.resolve(language).resolve(bundle)));
            flatten("", (Map<String, Object>) loaded, flattened);
        }
        return flattened;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, target);
            } else {
                target.put(key, entry.getValue());
            }
        }
    }

    private Set<String> placeholders(Object value) {
        Set<String> found = new HashSet<>();
        List<?> values = value instanceof List<?> list ? list : new ArrayList<>(List.of(String.valueOf(value)));
        for (Object element : values) {
            Matcher matcher = PLACEHOLDER.matcher(String.valueOf(element));
            while (matcher.find()) found.add(matcher.group());
        }
        return found;
    }

    private boolean isBlankValue(Object value) {
        if (value == null) return true;
        if (value instanceof List<?> list) {
            return list.isEmpty() || list.stream().allMatch(element -> String.valueOf(element).isBlank());
        }
        return String.valueOf(value).isBlank();
    }

    private String normalizedValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).reduce((a, b) -> a + "\n" + b).orElse("");
        }
        return String.valueOf(value);
    }
}
