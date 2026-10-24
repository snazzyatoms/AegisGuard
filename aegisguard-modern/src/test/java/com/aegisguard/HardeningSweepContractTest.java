package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the hardening sweep: teleport/respawn/join entry enforcement (the last border
 * bypass — entry was walk-only), role-priority hierarchy enforcement, and the auction
 * offline-refund fix.
 */
class HardeningSweepContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void teleportEntryGateExists() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(src.contains("onTeleportEntry(PlayerTeleportEvent e)"),
                "a generic PlayerTeleportEvent entry gate must exist");
        assertTrue(src.contains("isEntryDenied(to, p)"),
                "teleport gate must reuse the shared entry-denial logic");
        assertTrue(src.contains("to.isBanned(p.getUniqueId())"),
                "teleport gate must enforce claim bans");
        assertTrue(src.contains("PlotLeaveEvent") && src.contains("PlotEnterEvent"),
                "teleports must fire enter/leave events for API/tracking consistency");
    }

    @Test
    void respawnAndJoinEntryEnforced() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(src.contains("PlayerRespawnEvent"),
                "respawns inside denied claims must be rerouted to world spawn");
        assertTrue(src.contains("setRespawnLocation"),
                "respawn handler must relocate the respawn point");
        assertTrue(src.contains("PlayerJoinEvent"),
                "banned players rejoining inside a claim must be ejected");
        assertTrue(src.contains("runEntityLater"),
                "join ejection must be delayed and run on the player's region");
        assertTrue(src.contains("private boolean isEntryDenied(Plot to, Player p)"),
                "move/teleport/respawn must share one entry-denial predicate");
    }

    @Test
    void roleHierarchyIsEnforced() throws Exception {
        String src = Files.readString(JAVA.resolve("data/Plot.java"));
        assertTrue(src.contains("rolePriority("),
                "Plot must expose configured role priorities");
        assertTrue(src.contains("editorPriority(editor, pl) > rolePriority(getRole(targetUUID), pl)"),
                "editors must outrank the member they modify");
        assertTrue(src.contains("canAssignRole(@Nullable Player editor, @Nullable String roleName"),
                "role assignment must be gated by hierarchy");
        assertTrue(src.contains("editorPriority(editor, pl) > rolePriority(roleName, pl)"),
                "editors must not grant roles at or above their own rank");

        String gui = Files.readString(JAVA.resolve("gui/RolesGUI.java"));
        assertTrue(gui.contains("plot.canAssignRole(player, newRole, plugin)"),
                "RolesGUI must enforce the hierarchy before addPlayerRole");
    }

    @Test
    void auctionOfflineRefundFixed() throws Exception {
        String src = Files.readString(JAVA.resolve("gui/PlotAuctionGUI.java"));
        assertTrue(src.contains("plugin.eco().deposit(oldBidder, refund, CurrencyType.VAULT)"),
                "outbid refunds must deposit to OfflinePlayer");
        assertTrue(src.contains("runGlobal("),
                "offline refunds must run on a thread legal under Folia");

        String eco = Files.readString(JAVA.resolve("economy/EconomyManager.java"));
        assertTrue(eco.contains("deposit(OfflinePlayer p, double amount, CurrencyType type)"),
                "EconomyManager must offer an OfflinePlayer deposit overload");
        assertTrue(eco.contains("plugin.vault().give(p, amount)"),
                "offline Vault deposits must route through the Vault hook");
    }

    @Test
    void vaultClaimRefundsWhenValidationRejectsTheClaim() throws Exception {
        String selection = Files.readString(JAVA.resolve("selection/SelectionService.java"));
        String command = TestSources.commandsPackage();

        assertTrue(selection.contains("public boolean confirmClaim(Player p, boolean isServerClaim)"),
                "confirmClaim must report whether the plot was actually created");
        assertTrue(command.contains("if (!plugin.eco().withdraw(p, cost, CurrencyType.VAULT))"),
                "the Vault claim path must honor the withdraw result");
        assertTrue(command.contains("if (!plugin.selection().confirmClaim(p)) {\n"
                        + "                plugin.eco().deposit(p, cost, CurrencyType.VAULT);"),
                "a rejected claim must refund the Vault charge");
    }

    @Test
    void plotHostilePurgeIsRegionAware() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(src.contains("private void purgeChunkHostiles("),
                "hostile purge must be split per chunk");
        assertTrue(src.contains("plugin.runAt(chunkAnchor, () -> purgeChunkHostiles("),
                "chunks owned by another Folia region must be scanned on their own thread");
    }

    @Test
    void stallPurchaseRefusesForeignRegionBeforeCharging() throws Exception {
        String src = Files.readString(JAVA.resolve("market/TradeStallService.java"));
        int guard = src.indexOf("TeleportUtil.regionOwns(stall.getChestLocation())");
        int charge = src.indexOf("if (!chargeBuyer(buyer, listing))");
        assertTrue(guard > 0 && charge > guard,
                "the region-ownership guard must run before the buyer is charged");
    }
}
