package com.aegisguard;

import com.aegisguard.config.ConfigMigrationService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for the 1.3.0 harden/polish pass (schema 1282). */
class HardenPolishContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void schemaIs1282() {
        assertTrue(ConfigMigrationService.CURRENT_SCHEMA >= 1282);
    }

    @Test
    void mergingDefaultsOffWithAlignmentRequired() throws Exception {
        String config = Files.readString(RESOURCES.resolve("config.yml"));
        assertTrue(config.contains("merging:"));
        assertTrue(config.contains("require_alignment: true"));
        // shipped default must be false (opt-in)
        int mergingIdx = config.indexOf("merging:");
        String mergingBlock = config.substring(mergingIdx, mergingIdx + 120);
        assertTrue(mergingBlock.contains("enabled: false"));

        String ag = Files.readString(JAVA_ROOT.resolve("config/AGConfig.java"));
        assertTrue(ag.contains("claims.merging.enabled\", false"));
    }

    @Test
    void settlementsPlayerRetryIsScoped() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("gui/SettlementsInboxGUI.java"));
        assertTrue(gui.contains("retrySettlementsFor(player.getUniqueId())"));
        assertTrue(gui.contains("retrySettlements()"));
        assertTrue(gui.contains("isAdminView()"));
        assertTrue(gui.contains("doctor().open"));

        String life = Files.readString(JAVA_ROOT.resolve("territory/TerritoryLifeService.java"));
        assertTrue(life.contains("retrySettlementsFor"));
        assertTrue(life.contains("if (playerId != null && !playerId.equals(settlement.playerId())) continue;"));
    }

    @Test
    void mergeGuiEnforcesAlignmentAndConfirm() throws Exception {
        String merge = Files.readString(JAVA_ROOT.resolve("gui/ClaimMergeGUI.java"));
        assertTrue(merge.contains("ClaimMergeMath"));
        assertTrue(merge.contains("require_alignment"));
        assertTrue(merge.contains("openConfirm"));
        assertTrue(merge.contains("claim_merge_alignment_required"));
        assertTrue(merge.contains("claim_merge_role_conflict"));
        assertTrue(merge.contains("button_back"));
        assertTrue(merge.contains("button_exit"));
    }

    @Test
    void transferSettlesDepositsBeforeOwnershipChange() throws Exception {
        String transfer = Files.readString(JAVA_ROOT.resolve("gui/TransferConfirmGUI.java"));
        assertTrue(transfer.contains("settleBeforeTransfer"));
        assertTrue(transfer.contains("refundDeposit"));
        assertTrue(transfer.contains("takeHeldDeposit"));
        assertTrue(transfer.contains("button_back"));
        assertTrue(transfer.contains("button_exit"));
    }

    @Test
    void giftPermissionAndGuiExist() throws Exception {
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("aegis.claimblocks.gift:"));
        assertTrue(pluginYml.contains("aegis.claimblocks.gift: true"));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/GiftBlocksGUI.java")));
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("GiftBlocksHolder"));
    }

    @Test
    void liquidFlowProtectionAndZoneDepositsPresent() throws Exception {
        String protection = Files.readString(JAVA_ROOT.resolve("protection/BlockProtectionListener.java"));
        assertTrue(protection.contains("BlockFromToEvent"));
        assertTrue(protection.contains("onBlockFromTo"));
        String config = Files.readString(RESOURCES.resolve("config.yml"));
        assertTrue(config.contains("liquid_flow: true"));
        assertTrue(config.contains("default_deposit:"));
        String zone = Files.readString(JAVA_ROOT.resolve("data/Zone.java"));
        assertTrue(zone.contains("heldDeposit"));
        assertTrue(zone.contains("takeHeldDeposit"));
    }

    @Test
    void papiUsesLocalizedLiterals() throws Exception {
        String papi = Files.readString(JAVA_ROOT.resolve("hooks/AegisPAPIExpansion.java"));
        assertTrue(papi.contains("papi_wilderness"));
        assertTrue(papi.contains("papi_enabled"));
        assertTrue(papi.contains("papi_none"));
        assertFalse(papi.contains("return \"Wilderness\";"));
    }
}
