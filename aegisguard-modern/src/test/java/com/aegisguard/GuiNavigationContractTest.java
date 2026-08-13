package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiNavigationContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Pattern HOLDER_CLASS = Pattern.compile(
            "static\\s+(?:final\\s+)?class\\s+(\\w+Holder)\\b");

    @Test
    void everySubmenuSourceDeclaresBackAndExitControls() throws Exception {
        List<String> submenus = List.of(
                "gui/AdminGUI.java", "gui/AdminPlotListGUI.java", "gui/ClaimBlockExchangeGUI.java",
                "gui/DoctorRepairGUI.java", "gui/InfoGUI.java", "gui/LevelingGUI.java",
                "gui/LocalMarketGUI.java", "gui/MigrationAdminGUI.java", "gui/PlotAuctionGUI.java",
                "gui/PlotCosmeticsGUI.java", "gui/PlotFlagsGUI.java", "gui/PlotMarketGUI.java",
                "gui/PlotStatusGUI.java", "gui/RolesGUI.java", "gui/SettingsGUI.java", "gui/LanguageSelectGUI.java",
                "gui/StallBrowseGUI.java", "gui/StallPurchaseConfirmGUI.java", "gui/VisitGUI.java", "gui/ZoneBrowseGUI.java",
                "gui/ZoneTenantGUI.java", "gui/ZoningGUI.java", "gui/RentConfirmGUI.java",
                "gui/MyRentalsGUI.java", "gui/SettlementsInboxGUI.java", "gui/ClaimMergeGUI.java",
                "gui/TransferConfirmGUI.java", "gui/GiftBlocksGUI.java", "gui/MyTenantsGUI.java",
                "gui/ModerationGUI.java", "gui/GroupPlotsGUI.java", "gui/StorageMigrateGUI.java",
                "gui/WorldControlsGUI.java",
                "expansions/ExpansionRequestGUI.java",
                "expansions/ExpansionRequestAdminGUI.java",
                "expansions/ExpansionInstantApprovalsGUI.java",
                "snapshots/SnapshotAdminGUI.java",
                "audit/AuditAdminGUI.java", "guestpass/GuestPassGUI.java", "lockdown/LockdownGUI.java",
                "profile/RealmProfileGUI.java", "guidance/FirstClaimWalkthroughGUI.java",
                "routes/RoutesGUI.java", "routes/RouteAdminGUI.java",
                "alliance/AllianceAccessGUI.java");

        for (String relative : submenus) {
            String source = Files.readString(JAVA_ROOT.resolve(relative));
            assertTrue(source.contains("button_back"), relative + " must declare a Back control");
            assertTrue(source.contains("button_exit"), relative + " must declare an Exit control");
        }

        String rootMenu = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(rootMenu.contains("button_exit"), "The root menu must provide Exit");
    }

    @Test
    void settingsLanguageOpensAPickerInsteadOfCycling() throws Exception {
        String settings = Files.readString(JAVA_ROOT.resolve("gui/SettingsGUI.java"));
        String picker = Files.readString(JAVA_ROOT.resolve("gui/LanguageSelectGUI.java"));
        String manager = Files.readString(JAVA_ROOT.resolve("gui/GUIManager.java"));
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));

        assertTrue(settings.contains("languageSelect().open(player, plot)"),
                "Settings language button must open the picker");
        assertFalse(settings.contains("getNextStyle("),
                "Settings must not cycle languages on click");
        assertTrue(picker.contains("getAvailableStyles()"));
        assertTrue(picker.contains("setPlayerStyle(player, style)"));
        assertTrue(picker.contains("language_select_title"));
        assertTrue(picker.contains("button_back"));
        assertTrue(picker.contains("button_exit"));
        assertTrue(picker.contains("aegis_lang_style"));
        assertTrue(manager.contains("new LanguageSelectGUI(plugin)"));
        assertTrue(listener.contains("LanguageSelectHolder"));
        assertTrue(listener.contains("languageSelect().handleClick"));
    }

    @Test
    void centralListenerRoutesEveryDeclaredInventoryHolder() throws Exception {
        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        List<String> missing = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(JAVA_ROOT)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                Matcher matcher = HOLDER_CLASS.matcher(source);
                while (matcher.find()) {
                    String holder = matcher.group(1);
                    if (!listener.contains("holder instanceof " + holder)
                            && !listener.contains("instanceof " + holder)) {
                        missing.add(path.getFileName() + "#" + holder);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "GUIListener must route holders: " + missing);

        for (String holder : List.of("DoctorHolder", "RoleManageHolder", "RoleFlagsHolder")) {
            assertTrue(listener.contains("holder instanceof " + holder), holder + " must be protected and routed");
        }
        assertTrue(listener.contains("handleRoleFlagsClick"));
        assertTrue(listener.contains("CLICK_GUARD_NANOS"));
    }

    @Test
    void doctorAsyncResultsCannotReopenAClosedMenuAndRepairRequiresConfirmation() throws Exception {
        String doctor = Files.readString(JAVA_ROOT.resolve("gui/DoctorRepairGUI.java"));
        assertTrue(doctor.contains("isCurrentRequest"));
        assertTrue(doctor.contains("requestId"));
        assertTrue(doctor.contains("doctor_repair_confirm"));
        assertTrue(doctor.contains("confirmation"));
    }

    @Test
    void giftBlocksBalanceDoesNotOverwriteRecipientSlots() throws Exception {
        String gift = Files.readString(JAVA_ROOT.resolve("gui/GiftBlocksGUI.java"));
        assertTrue(gift.contains("inv.setItem(49,"), "Balance chrome must stay on the footer row");
        assertFalse(gift.contains("inv.setItem(4, GUIManager.createItem(Material.EMERALD"),
                "Balance must not steal recipient slot 4");
    }

    @Test
    void myRentalsKeepsClickIndexAlignedWithEntryIndex() throws Exception {
        String rentals = Files.readString(JAVA_ROOT.resolve("gui/MyRentalsGUI.java"));
        assertTrue(rentals.contains("int slot = idx - start"),
                "My Rentals must map visual slots to entry indexes without compacting");
        assertFalse(Pattern.compile("inv\\.setItem\\(slot\\+\\+").matcher(rentals).find(),
                "My Rentals must not compact skipped entries into earlier slots");
    }

    @Test
    void moderationSeparatesOnlineAndBannedSlots() throws Exception {
        String moderation = Files.readString(JAVA_ROOT.resolve("gui/ModerationGUI.java"));
        assertTrue(moderation.contains("ONLINE_SLOTS"));
        assertTrue(moderation.contains("BAN_START"));
        assertTrue(moderation.contains("getBanned()"));
    }

    @Test
    void destructiveGuestAndZoneActionsRequireShiftConfirm() throws Exception {
        String guest = Files.readString(JAVA_ROOT.resolve("guestpass/GuestPassGUI.java"));
        String zone = Files.readString(JAVA_ROOT.resolve("gui/ZoneTenantGUI.java"));
        assertTrue(guest.contains("guest_pass_revoke_hint"));
        assertTrue(guest.contains("isShiftClick()"));
        assertTrue(zone.contains("zone_tenant_evict_hint"));
        assertTrue(zone.contains("isShiftClick()"));
    }
}
