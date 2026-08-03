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

class ExpansionAndExchangeContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path LANG_ROOT = Path.of("src/main/resources/lang");

    @Test
    void failedExchangePayoutRestoresEveryClaimBlockBalance() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("claimblocks/ClaimBlockExchangeService.java"));
        assertTrue(service.contains("addBoughtFromExchange"));
        assertTrue(service.contains("!plugin.vault().deposit(p, payout)"));
        assertTrue(service.contains("cbd.setEarnedBlocks(earnedBefore)"));
        assertTrue(service.contains("cbd.setBonusBlocks(bonusBefore)"));
        assertTrue(service.contains("cbd.setBoughtBlocks(boughtBefore)"));
        assertTrue(service.contains("Long.MAX_VALUE - blockData.getBoughtBlocks()"));
    }

    @Test
    void claimBlockExchangeStateStoreSavesWithFoliaSafeAsyncWrapper() throws Exception {
        String store = Files.readString(JAVA_ROOT.resolve("claimblocks/ClaimBlockExchangeStateStore.java"));
        assertTrue(store.contains("plugin.runGlobalAsync(this::save)"),
                "Claim-block exchange state must use AegisGuard.runGlobalAsync on Folia");
        assertFalse(store.contains("getScheduler().runTaskAsynchronously"),
                "Direct BukkitScheduler async saves break Folia");
    }

    @Test
    void exchangeIncludesAnInMenuLocalizedGuide() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("gui/ClaimBlockExchangeGUI.java"));
        assertTrue(gui.contains("renderGuide(p, e.getInventory())"));
        assertTrue(gui.contains("exchange.getOverview(player)"));
        assertTrue(gui.contains("claimblocks_exchange_guide_protection"));
        assertTrue(gui.contains("if (e.isShiftClick()) return"));
        assertTrue(gui.contains("clampAmount(p, s)"));
    }

    @Test
    void horizonsAreConfiguredAsFiveSlowRenownRanksAfterLevelThirty() throws Exception {
        Map<String, Object> root;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            root = new Yaml().load(input);
        }
        Map<?, ?> expansions = (Map<?, ?>) root.get("expansions");
        Map<?, ?> horizons = (Map<?, ?>) expansions.get("horizons");
        Map<?, ?> ranks = (Map<?, ?>) horizons.get("ranks");

        assertEquals(Boolean.TRUE, horizons.get("enabled"));
        assertEquals(30, ((Number) horizons.get("unlock_level")).intValue());
        assertEquals(5, ranks.size());
        assertEquals(2500L, ((Number) ((Map<?, ?>) ranks.get(1)).get("required_renown")).longValue());
        assertEquals(60000L, ((Number) ((Map<?, ?>) ranks.get(5)).get("required_renown")).longValue());
        assertEquals(750, ((Number) horizons.get("max_radius_global")).intValue());
    }

    @Test
    void horizonsUseExistingValidatedExpansionPipelineAndRealPlotAreaPricing() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("expansions/ExpansionRequestGUI.java"));
        String manager = Files.readString(JAVA_ROOT.resolve("expansions/ExpansionRequestManager.java"));

        assertTrue(gui.contains("plugin.horizons().requestNextExpansion(player, plot)"));
        assertTrue(manager.contains("calculateSmartCost(plot, newRadius)"));
        assertTrue(manager.contains("long currentArea = Math.max(1L, width * depth)"));
        assertTrue(manager.contains("createExpansionSnapshot"));
        assertTrue(manager.contains("refundExpansionCost"));
        assertTrue(manager.contains("isNextHorizonExpansion(plot, currentRadius, newRadius)"));
    }

    @Test
    void horizonSigilsAreBoundPersistentAndActivatedSafely() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("horizons/HorizonService.java"));
        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        String yml = Files.readString(JAVA_ROOT.resolve("data/YMLDataStore.java"));
        String sql = Files.readString(JAVA_ROOT.resolve("data/SQLDataStore.java"));

        assertTrue(service.contains("horizon_sigil"));
        assertTrue(service.contains("PersistentDataType.STRING"));
        assertTrue(service.contains("data.ownerId().equals(player.getUniqueId())"));
        assertTrue(service.contains("plot.getPlotId().equals(data.plotId())"));
        assertTrue(service.contains("Particle.CLOUD"));
        assertFalse(service.contains("createExplosion"));
        assertTrue(plot.contains("horizonRenown"));
        assertTrue(yml.contains("horizons.renown"));
        assertTrue(sql.contains("horizonRenown"));
    }

    @Test
    void everyLanguageShipsTheNewExperienceWithoutPlaceholderCopy() throws Exception {
        for (String language : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            String gui = Files.readString(LANG_ROOT.resolve(language).resolve("guis.yml"));
            assertTrue(gui.contains("claimblocks_exchange_guide_button:"), language);
            assertTrue(gui.contains("expansion_horizons_title:"), language);
            assertTrue(gui.contains("expansion_horizon_tier_5_name:"), language);
            assertFalse(gui.contains("Future Update"), language);
            assertFalse(gui.contains("Actualización Futura"), language);
        }
    }
}
