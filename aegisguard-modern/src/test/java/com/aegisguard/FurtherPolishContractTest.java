package com.aegisguard;

import com.aegisguard.config.ConfigMigrationService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract coverage for the 1.3.0 further-polish pass (schema advanced to 1282+).
 */
class FurtherPolishContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void schemaIsAtLeast1281() {
        assertTrue(ConfigMigrationService.CURRENT_SCHEMA >= 1281);
    }

    @Test
    void sqliteHonorsConfiguredFilePath() throws Exception {
        String sql = Files.readString(JAVA_ROOT.resolve("data/SQLDataStore.java"));
        assertTrue(sql.contains("getString(\"file\""));
        assertTrue(sql.contains("configured"));
    }

    @Test
    void auctionsNotGatedByUpkeepInPlayerGui() throws Exception {
        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("Modules.Id.AUCTION")
                || playerGui.contains("auction.enabled")
                || playerGui.contains("auctions.enabled")
                || playerGui.contains("market.auctions.enabled"));
        assertFalse(playerGui.contains("isUpkeepEnabled()"));
    }

    @Test
    void roleFlagOverridesExist() throws Exception {
        String plot = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plot.contains("resolveRoleFlagOverride"));
        assertTrue(plot.contains("resolvePermissionRoleFlag"));
    }

    @Test
    void giftBlocksAndMergeCommandsExist() throws Exception {
        String cmd = Files.readString(JAVA_ROOT.resolve("commands/AegisCommand.java"));
        assertTrue(cmd.contains("giftblocks"));
        assertTrue(cmd.contains("handleGiftBlocks"));
        assertTrue(cmd.contains("\"merge\""));
    }

    @Test
    void discordSendEventAndOptInEventsPresent() throws Exception {
        String discord = Files.readString(JAVA_ROOT.resolve("hooks/DiscordWebhook.java"));
        assertTrue(discord.contains("sendEvent"));
        String config = Files.readString(RESOURCES.resolve("config.yml"));
        assertTrue(config.contains("market_sale: false"));
        assertTrue(config.contains("rental_start: false"));
        assertTrue(config.contains("rental_end: false"));
        assertTrue(config.contains("lockdown: false"));
        assertTrue(config.contains("guest_pass: false"));
    }

    @Test
    void zoneLeaveAndAutoRenewWired() throws Exception {
        String rentConfirm = Files.readString(JAVA_ROOT.resolve("gui/RentConfirmGUI.java"));
        assertTrue(rentConfirm.contains("ZONE_LEAVE"));
        String myRentals = Files.readString(JAVA_ROOT.resolve("gui/MyRentalsGUI.java"));
        assertTrue(myRentals.contains("executeZoneLeave"));
        assertTrue(myRentals.contains("toggleAutoRenew"));
        String config = Files.readString(RESOURCES.resolve("config.yml"));
        assertTrue(config.contains("auto_renew:"));
    }

    @Test
    void routesGuidanceAndNewHubsExist() throws Exception {
        String routes = Files.readString(JAVA_ROOT.resolve("routes/RouteDiscoveryListener.java"));
        assertTrue(routes.contains("routes.guidance.enabled"));
        assertTrue(routes.contains("action_bar") || routes.contains("sendActionBar")
                || routes.contains("ActionBar"));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/ModerationGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/MyTenantsGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/SettlementsInboxGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/GroupPlotsGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/ClaimMergeGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/TransferConfirmGUI.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("data/PlotBackendMigrator.java")));
        assertTrue(Files.exists(JAVA_ROOT.resolve("gui/StorageMigrateGUI.java")));
    }

    @Test
    void rentalRenewCommandUsesConfirmGui() throws Exception {
        String cmd = Files.readString(JAVA_ROOT.resolve("commands/AegisCommand.java"));
        assertTrue(cmd.contains("openPlotRenew"));
        assertTrue(cmd.contains("openPlotCancel"));
    }
}
