package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStallContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path LANG_ROOT = Path.of("src/main/resources/lang");
    private static final Path CODEX_ROOT = Path.of("src/main/resources/codex");
    private static final List<String> LANGUAGES = List.of(
            "modern_english", "old_english", "spanish_mx", "spanish_ar",
            "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl");

    @Test
    void localMarketBridgeSlotsDoNotClashWithMergeClaims() throws Exception {
        String source = Files.readString(JAVA_ROOT.resolve("gui/LocalMarketGUI.java"));
        assertTrue(source.contains("SLOT_MERGE = 29"));
        assertTrue(source.contains("BRIDGE_SLOTS = {36, 37, 38, 39, 41, 42, 43, 44}"));
        assertTrue(source.contains("SLOT_STALLS = 40"));
        assertTrue(source.contains("SLOT_CREATE = 16"));
        assertTrue(source.contains("SLOT_ZONE_RENTALS = 22"));

        Matcher merge = Pattern.compile("SLOT_MERGE\\s*=\\s*(\\d+)").matcher(source);
        assertTrue(merge.find());
        int mergeSlot = Integer.parseInt(merge.group(1));

        Matcher bridges = Pattern.compile("BRIDGE_SLOTS\\s*=\\s*\\{([^}]+)}").matcher(source);
        assertTrue(bridges.find());
        Set<Integer> bridgeSlots = new HashSet<>();
        for (String part : bridges.group(1).split(",")) {
            bridgeSlots.add(Integer.parseInt(part.trim()));
        }
        assertFalse(bridgeSlots.contains(mergeSlot), "External shop slots must not reuse Merge Claims slot 29");
        assertFalse(bridgeSlots.contains(40), "External shop slots must not reuse Trade Stalls");
        assertTrue(bridgeSlots.size() >= 6);
    }

    @Test
    void marketBackNavigationHoldersRememberReturnTo() throws Exception {
        List<String> files = List.of(
                "gui/PlotMarketGUI.java",
                "gui/ZoningGUI.java",
                "gui/ZoneBrowseGUI.java",
                "gui/ZoneTenantGUI.java",
                "gui/MyRentalsGUI.java",
                "gui/MyTenantsGUI.java",
                "gui/ClaimMergeGUI.java",
                "gui/GiftBlocksGUI.java",
                "gui/StallPurchaseConfirmGUI.java"
        );
        for (String relative : files) {
            String source = Files.readString(JAVA_ROOT.resolve(relative));
            assertTrue(source.contains("button_back"), relative + " must keep Back");
            assertTrue(source.contains("button_exit"), relative + " must keep Exit");
            if (relative.contains("StallPurchaseConfirm")) {
                assertTrue(source.contains("openPreview"), relative + " Back must return to stall preview");
                continue;
            }
            assertTrue(source.contains("returnTo") || source.contains("ReturnPlotId") || source.contains("getReturnTo"),
                    relative + " must remember the previous market screen");
            assertTrue(source.contains("MarketNav"), relative + " must route Back through MarketNav or equivalent");
        }
        String local = Files.readString(JAVA_ROOT.resolve("gui/LocalMarketGUI.java"));
        assertTrue(local.contains("local_market_rentals_name\", \"&aZone Rentals\""));
        assertFalse(local.contains("\"&aStalls & Rentals\""));
    }

    @Test
    void purchaseUsesPerSlotLockAndConfirmGui() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("market/TradeStallService.java"));
        assertTrue(service.contains("purchaseLocks"));
        assertTrue(service.contains("putIfAbsent"));
        assertTrue(service.contains("ResultType.BUSY"));
        assertTrue(service.contains("lockKey("));

        String preview = Files.readString(JAVA_ROOT.resolve("gui/StallBrowseGUI.java"));
        assertTrue(preview.contains("stallBuyConfirm().open"));
        assertFalse(Pattern.compile("tradeStalls\\(\\)\\.purchase\\(").matcher(preview).find(),
                "Preview clicks must not charge instantly");

        String confirm = Files.readString(JAVA_ROOT.resolve("gui/StallPurchaseConfirmGUI.java"));
        assertTrue(confirm.contains("tradeStalls().purchase"));
        assertTrue(confirm.contains("StallBuyConfirmHolder"));
    }

    @Test
    void stallVisitAndSignBrowseAreWired() throws Exception {
        String browse = Files.readString(JAVA_ROOT.resolve("gui/StallBrowseGUI.java"));
        assertTrue(browse.contains("Kind.STALL"));
        assertTrue(browse.contains("visitStall"));
        assertTrue(browse.contains("market_stall_visit_action"));

        String listener = Files.readString(JAVA_ROOT.resolve("listeners/MarketStallListener.java"));
        assertTrue(listener.contains("isSign("));
        assertTrue(listener.contains("openStallGui"));
        assertTrue(listener.contains("onHopperMove"));
        assertTrue(listener.contains("InventoryMoveItemEvent"));
        assertTrue(listener.contains("startCreateBind") || listener.contains("hasCreateBind"));

        String travel = Files.readString(JAVA_ROOT.resolve("travel/SafeTravelService.java"));
        assertTrue(travel.contains("STALL,"));
    }

    @Test
    void namingAndEmptyStateKeysExistInEveryLanguageAndCodex() throws Exception {
        List<String> keys = List.of(
                "local_market_rentals_name:",
                "local_market_create_stall_name:",
                "local_market_external_name:",
                "local_market_stalls_coexist_lore:",
                "local_market_stalls_empty_lore:",
                "stall_buy_confirm_title:",
                "stall_buy_confirm_name:",
                "market_stall_visit_action:",
                "market_stall_none_create_lore:",
                "market_stall_no_vault_lore:",
                "market_stall_bind_started:",
                "market_stall_purchase_busy:",
                "market_stall_visit_arrived:",
                "market_stall_create_guide:"
        );
        for (String language : LANGUAGES) {
            String guis = Files.readString(LANG_ROOT.resolve(language).resolve("guis.yml"));
            String system = Files.readString(LANG_ROOT.resolve(language).resolve("system.yml"));
            String combined = guis + "\n" + system;
            String codex = Files.readString(CODEX_ROOT.resolve(language + ".yml"));
            for (String key : keys) {
                assertTrue(combined.contains(key), language + " lang missing " + key);
                assertTrue(codex.contains(key), language + " codex missing " + key);
            }
            assertFalse(guis.contains("local_market_rentals_name: '&aStalls & Rentals'"),
                    language + " must rename Stalls & Rentals to Zone Rentals");
            if (language.equals("modern_english")) {
                assertTrue(guis.contains("local_market_rentals_name: '&aZone Rentals'"));
            }
        }
    }

    @Test
    void coexistRemainsTheDefaultIntegrationMode() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(config.contains("mode: COEXIST"));
        assertTrue(config.contains("DISABLE_ON_BRIDGED_PLOTS"));
        assertTrue(config.contains("QuickShop"));
        assertTrue(config.contains("ChestShop"));
        assertTrue(config.contains("Shopkeepers"));
        assertTrue(config.contains("ExcellentShop"));
        assertTrue(config.contains("player:"));
        assertTrue(config.contains("console:"));
    }
}
