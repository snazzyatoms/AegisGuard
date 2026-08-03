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
                "no_perm", "players_only", "button_exit");
        for (String language : List.of(
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> translated = loadLanguage(language);
            int identical = 0;
            int compared = 0;
            for (String key : probeKeys) {
                if (!english.containsKey(key) || !translated.containsKey(key)) continue;
                compared++;
                if (String.valueOf(english.get(key)).equals(String.valueOf(translated.get(key)))) {
                    identical++;
                }
            }
            assertTrue(compared >= 5, language + " missing probe keys for translation check");
            final int identicalCount = identical;
            final int comparedCount = compared;
            assertTrue(identicalCount < comparedCount,
                    () -> language + " still looks like English for core UI probes ("
                            + identicalCount + "/" + comparedCount + " identical)");
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
}
