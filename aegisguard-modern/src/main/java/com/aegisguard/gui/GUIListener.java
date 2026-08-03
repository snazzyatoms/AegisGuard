package com.aegisguard.gui;

import com.aegisguard.AegisGuard;

// --- IMPORTS (Holders) ---
import com.aegisguard.audit.AuditAdminGUI.AuditHolder;
import com.aegisguard.expansions.ExpansionRequestAdminGUI.ExpansionAdminHolder;
import com.aegisguard.expansions.ExpansionRequestGUI.ExpansionHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassMenuHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassAddHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassPresetHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassDurationHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassModeHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassConfirmHolder;
import com.aegisguard.guestpass.GuestPassGUI.GuestPassDetailHolder;
import com.aegisguard.lockdown.LockdownGUI.LockdownMenuHolder;
import com.aegisguard.lockdown.LockdownGUI.LockdownOptionsHolder;
import com.aegisguard.lockdown.LockdownGUI.LockdownConfirmHolder;
import com.aegisguard.profile.RealmProfileGUI.RealmProfileMenuHolder;
import com.aegisguard.profile.RealmProfileGUI.NoticeboardHolder;
import com.aegisguard.guidance.FirstClaimWalkthroughGUI.WalkthroughHolder;
import com.aegisguard.routes.RoutesGUI.RoutesMenuHolder;
import com.aegisguard.routes.RoutesGUI.RouteDetailHolder;
import com.aegisguard.routes.RouteAdminGUI.RouteAdminHolder;
import com.aegisguard.routes.RouteAdminGUI.RouteEditHolder;
import com.aegisguard.alliance.AllianceAccessGUI.AllianceMenuHolder;
import com.aegisguard.alliance.AllianceAccessGUI.AllianceConfirmHolder;
import com.aegisguard.alliance.AllianceAccessGUI.AllianceRosterHolder;
import com.aegisguard.gui.AdminGUI.AdminHolder;
import com.aegisguard.gui.AdminPlotListGUI.PlotListHolder;
import com.aegisguard.gui.ClaimBlockExchangeGUI.ExchangeHolder;
import com.aegisguard.gui.DoctorRepairGUI.DoctorHolder;
import com.aegisguard.gui.InfoGUI.InfoHolder;
import com.aegisguard.gui.LevelingGUI.LevelingHolder;
import com.aegisguard.gui.LocalMarketGUI.LocalMarketHolder;
import com.aegisguard.gui.MigrationAdminGUI.MigrationMainHolder;
import com.aegisguard.gui.MigrationAdminGUI.MigrationPreviewHolder;
import com.aegisguard.gui.PlayerGUI.PlayerMenuHolder;
import com.aegisguard.gui.PlotAuctionGUI.PlotAuctionHolder;
import com.aegisguard.gui.PlotCosmeticsGUI.CosmeticsHolder;
import com.aegisguard.gui.PlotFlagsGUI.PlotFlagsHolder;
import com.aegisguard.gui.PlotFlagsGUI.PlotFlagsPresetConfirmHolder;
import com.aegisguard.gui.PlotMarketGUI.PlotMarketHolder;
import com.aegisguard.gui.PlotStatusGUI.PlotStatusHolder;
import com.aegisguard.gui.RolesGUI.PlotSelectorHolder;
import com.aegisguard.gui.RolesGUI.RoleAddHolder;
import com.aegisguard.gui.RolesGUI.RoleManageHolder;
import com.aegisguard.gui.RolesGUI.RoleFlagsHolder;
import com.aegisguard.gui.RolesGUI.RolesMenuHolder;
import com.aegisguard.gui.SettingsGUI.SettingsGUIHolder;
import com.aegisguard.gui.StallBrowseGUI.StallListHolder;
import com.aegisguard.gui.StallBrowseGUI.StallManageHolder;
import com.aegisguard.gui.StallBrowseGUI.StallPreviewHolder;
import com.aegisguard.gui.VisitGUI.VisitHolder;
import com.aegisguard.gui.WorldControlsGUI.WorldControlsHolder;
import com.aegisguard.gui.ZoningGUI.ZoningHolder;
import com.aegisguard.gui.ZoneBrowseGUI.ZoneBrowseHolder;
import com.aegisguard.gui.ZoneTenantGUI.ZoneTenantHolder;
import com.aegisguard.gui.RentConfirmGUI.RentConfirmHolder;
import com.aegisguard.gui.MyRentalsGUI.MyRentalsHolder;
import com.aegisguard.gui.ModerationGUI.ModerationHolder;
import com.aegisguard.gui.MyTenantsGUI.MyTenantsHolder;
import com.aegisguard.gui.SettlementsInboxGUI.SettlementsHolder;
import com.aegisguard.gui.GroupPlotsGUI.GroupPlotsHolder;
import com.aegisguard.gui.TransferConfirmGUI.TransferConfirmHolder;
import com.aegisguard.gui.ConvertToServerGUI.StaffWandHolder;
import com.aegisguard.gui.ConvertToServerGUI.ConvertSelectHolder;
import com.aegisguard.gui.ConvertToServerGUI.ConvertConfirmHolder;
import com.aegisguard.gui.ClaimMergeGUI.ClaimMergeHolder;
import com.aegisguard.gui.GiftBlocksGUI.GiftBlocksHolder;
import com.aegisguard.gui.StorageMigrateGUI.StorageMigrateHolder;
import com.aegisguard.snapshots.SnapshotAdminGUI.SnapshotHolder;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUIListener (1.2.6 QoL pass)
 * - Central click router for ALL AegisGuard GUIs.
 * - Strictly blocks inventory movement while our menus are open.
 *
 * 1.2.6 improvements:
 * - Folia-safe: only reacts to top-inventory clicks; cancels everything; ignores player-inventory clicks.
 * - Reload interception is now primarily PDC-driven (aegis_action) with a safer legacy fallback.
 * - Supports distinct reload intents:
 *     reload_all / reload_settings / reload  -> full reload pipeline
 *     refresh_lang / refresh                -> language-only refresh (Codex reload)
 * - Heavy work runs async; GUI reopen happens on main thread.
 */
public class GUIListener implements Listener {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, Long> lastMenuClick = new ConcurrentHashMap<>();
    private static final long CLICK_GUARD_NANOS = 150_000_000L;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "aegis_action");
    }

    private boolean isAegisGuiHolder(InventoryHolder holder) {
        return holder instanceof PlayerMenuHolder
                || holder instanceof VisitHolder
                || holder instanceof InfoHolder
                || holder instanceof SettingsGUIHolder
                || holder instanceof AdminHolder
                || holder instanceof DoctorHolder
                || holder instanceof WorldControlsHolder
                || holder instanceof PlotListHolder
                || holder instanceof PlotSelectorHolder
                || holder instanceof RolesMenuHolder
                || holder instanceof RoleAddHolder
                || holder instanceof RoleManageHolder
                || holder instanceof RoleFlagsHolder
                || holder instanceof PlotFlagsHolder
                || holder instanceof PlotFlagsPresetConfirmHolder
                || holder instanceof CosmeticsHolder
                || holder instanceof LevelingHolder
                || holder instanceof ZoningHolder
                || holder instanceof PlotMarketHolder
                || holder instanceof PlotAuctionHolder
                || holder instanceof ExpansionHolder
                || holder instanceof ExpansionAdminHolder
                || holder instanceof PlotStatusHolder
                || holder instanceof ExchangeHolder
                || holder instanceof MigrationMainHolder
                || holder instanceof MigrationPreviewHolder
                || holder instanceof ZoneBrowseHolder
                || holder instanceof ZoneTenantHolder
                || holder instanceof RentConfirmHolder
                || holder instanceof MyRentalsHolder
                || holder instanceof ModerationHolder
                || holder instanceof MyTenantsHolder
                || holder instanceof SettlementsHolder
                || holder instanceof GroupPlotsHolder
                || holder instanceof TransferConfirmHolder
                || holder instanceof StaffWandHolder
                || holder instanceof ConvertSelectHolder
                || holder instanceof ConvertConfirmHolder
                || holder instanceof ClaimMergeHolder
                || holder instanceof GiftBlocksHolder
                || holder instanceof StorageMigrateHolder
                || holder instanceof LocalMarketHolder
                || holder instanceof StallListHolder
                || holder instanceof StallManageHolder
                || holder instanceof StallPreviewHolder
                || holder instanceof SnapshotHolder
                || holder instanceof AuditHolder
                || holder instanceof GuestPassMenuHolder
                || holder instanceof GuestPassAddHolder
                || holder instanceof GuestPassPresetHolder
                || holder instanceof GuestPassDurationHolder
                || holder instanceof GuestPassModeHolder
                || holder instanceof GuestPassConfirmHolder
                || holder instanceof GuestPassDetailHolder
                || holder instanceof LockdownMenuHolder
                || holder instanceof LockdownOptionsHolder
                || holder instanceof LockdownConfirmHolder
                || holder instanceof RealmProfileMenuHolder
                || holder instanceof NoticeboardHolder
                || holder instanceof WalkthroughHolder
                || holder instanceof RoutesMenuHolder
                || holder instanceof RouteDetailHolder
                || holder instanceof RouteAdminHolder
                || holder instanceof RouteEditHolder
                || holder instanceof AllianceMenuHolder
                || holder instanceof AllianceConfirmHolder
                || holder instanceof AllianceRosterHolder;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Always cancel while our GUIs are open
        e.setCancelled(true);

        // Ignore clicks in the player's own inventory entirely (prevents weird swaps/moves)
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(player.getInventory())) return;

        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(top)) return;

        // Outside click / invalid slot
        if (e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // 1.2.6: block spammy / inventory-manipulation click types
        ClickType click = e.getClick();
        switch (click) {
            case NUMBER_KEY,
                 DOUBLE_CLICK,
                 SWAP_OFFHAND,
                 DROP,
                 CONTROL_DROP,
                 MIDDLE -> {
                return;
            }
            default -> { /* continue */ }
        }

        long now = System.nanoTime();
        Long previous = lastMenuClick.put(player.getUniqueId(), now);
        if (previous != null && now - previous < CLICK_GUARD_NANOS) return;

        // 1.2.6: PDC-first reload detection (with legacy fallback)
        ReloadIntent intent = detectReloadIntent(clicked);
        if (intent != null) {
            boolean soft = (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT);
            handleGuiReload(player, holder, intent, soft);
            return;
        }

        // Route to specific GUI handler
        if (holder instanceof PlayerMenuHolder) {
            plugin.gui().player().handleClick(player, e);
        }
        else if (holder instanceof VisitHolder castHolder) {
            plugin.gui().visit().handleClick(player, e, castHolder);
        }
        else if (holder instanceof InfoHolder) {
            plugin.gui().info().handleClick(player, e);
        }
        else if (holder instanceof SettingsGUIHolder) {
            plugin.gui().settings().handleClick(player, e);
        }
        else if (holder instanceof AdminHolder) {
            plugin.gui().admin().handleClick(player, e);
        }
        else if (holder instanceof DoctorHolder castHolder) {
            plugin.gui().doctor().handleClick(player, e, castHolder);
        }
        else if (holder instanceof WorldControlsHolder castHolder) {
            plugin.gui().worldControls().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotListHolder castHolder) {
            plugin.gui().plotList().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotSelectorHolder castHolder) {
            plugin.gui().roles().handlePlotSelectorClick(player, e, castHolder);
        }
        else if (holder instanceof RolesMenuHolder castHolder) {
            plugin.gui().roles().handleRolesMenuClick(player, e, castHolder);
        }
        else if (holder instanceof RoleAddHolder castHolder) {
            plugin.gui().roles().handleAddTrustedClick(player, e, castHolder);
        }
        else if (holder instanceof RoleManageHolder castHolder) {
            plugin.gui().roles().handleManageRoleClick(player, e, castHolder);
        }
        else if (holder instanceof RoleFlagsHolder castHolder) {
            plugin.gui().roles().handleRoleFlagsClick(player, e, castHolder);
        }
        else if (holder instanceof PlotFlagsHolder castHolder) {
            plugin.gui().flags().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotFlagsPresetConfirmHolder castHolder) {
            plugin.gui().flags().handlePresetConfirmClick(player, e, castHolder);
        }
        else if (holder instanceof CosmeticsHolder castHolder) {
            plugin.gui().cosmetics().handleClick(player, e, castHolder);
        }
        else if (holder instanceof LevelingHolder castHolder) {
            plugin.gui().leveling().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ZoningHolder castHolder) {
            plugin.gui().zoning().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ZoneBrowseHolder castHolder) {
            plugin.gui().zoneBrowse().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ZoneTenantHolder castHolder) {
            plugin.gui().zoneTenant().handleClick(player, e, castHolder);
        }
        else if (holder instanceof RentConfirmHolder castHolder) {
            plugin.gui().rentConfirm().handleClick(player, e, castHolder);
        }
        else if (holder instanceof MyRentalsHolder castHolder) {
            plugin.gui().myRentals().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ModerationHolder castHolder) {
            plugin.gui().moderation().handleClick(player, e, castHolder);
        }
        else if (holder instanceof MyTenantsHolder castHolder) {
            plugin.gui().myTenants().handleClick(player, e, castHolder);
        }
        else if (holder instanceof SettlementsHolder castHolder) {
            plugin.gui().settlementsInbox().handleClick(player, e, castHolder);
        }
        else if (holder instanceof GroupPlotsHolder castHolder) {
            plugin.gui().groupPlots().handleClick(player, e, castHolder);
        }
        else if (holder instanceof TransferConfirmHolder castHolder) {
            plugin.gui().transferConfirm().handleClick(player, e, castHolder);
        }
        else if (holder instanceof StaffWandHolder) {
            plugin.gui().convertToServer().handleStaffWandClick(player, e);
        }
        else if (holder instanceof ConvertSelectHolder castHolder) {
            plugin.gui().convertToServer().handleSelectClick(player, e, castHolder);
        }
        else if (holder instanceof ConvertConfirmHolder castHolder) {
            plugin.gui().convertToServer().handleConfirmClick(player, e, castHolder);
        }
        else if (holder instanceof ClaimMergeHolder castHolder) {
            plugin.gui().claimMerge().handleClick(player, e, castHolder);
        }
        else if (holder instanceof GiftBlocksHolder castHolder) {
            plugin.gui().giftBlocks().handleClick(player, e, castHolder);
        }
        else if (holder instanceof StorageMigrateHolder castHolder) {
            plugin.gui().storageMigrate().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotMarketHolder castHolder) {
            plugin.gui().market().handleClick(player, e, castHolder);
        }
        else if (holder instanceof LocalMarketHolder castHolder) {
            plugin.gui().localMarket().handleClick(player, e, castHolder);
        }
        else if (holder instanceof StallListHolder castHolder) {
            plugin.gui().stallBrowse().handleListClick(player, e, castHolder);
        }
        else if (holder instanceof StallManageHolder castHolder) {
            plugin.gui().stallBrowse().handleManageClick(player, e, castHolder);
        }
        else if (holder instanceof StallPreviewHolder castHolder) {
            plugin.gui().stallBrowse().handlePreviewClick(player, e, castHolder);
        }
        else if (holder instanceof PlotAuctionHolder castHolder) {
            plugin.gui().auction().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ExpansionHolder) {
            plugin.gui().expansionRequest().handleClick(player, e);
        }
        else if (holder instanceof ExpansionAdminHolder) {
            plugin.gui().expansionAdmin().handleClick(player, e);
        }
        else if (holder instanceof PlotStatusHolder castHolder) {
            plugin.gui().plotStatus().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ExchangeHolder castHolder) {
            if (plugin.gui().exchange() != null) {
                plugin.gui().exchange().handleClick(player, e, castHolder);
            }
        }
        else if (holder instanceof MigrationMainHolder castHolder) {
            if (plugin.gui().migration() != null) {
                plugin.gui().migration().handleClick(player, e, castHolder);
            }
        }
        else if (holder instanceof MigrationPreviewHolder castHolder) {
            if (plugin.gui().migration() != null) {
                plugin.gui().migration().handlePreviewClick(player, e, castHolder);
            }
        }
        else if (holder instanceof SnapshotHolder) {
            if (plugin.gui().snapshotAdmin() != null) {
                plugin.gui().snapshotAdmin().handleClick(player, e);
            }
        }
        else if (holder instanceof AuditHolder) {
            if (plugin.gui().audit() != null) {
                plugin.gui().audit().handleClick(player, e);
            }
        }
        else if (holder instanceof GuestPassMenuHolder castHolder) {
            plugin.gui().guestPasses().handleMenuClick(player, e, castHolder);
        }
        else if (holder instanceof GuestPassAddHolder castHolder) {
            plugin.gui().guestPasses().handleAddClick(player, e, castHolder);
        }
        else if (holder instanceof GuestPassPresetHolder castHolder) {
            plugin.gui().guestPasses().handlePresetClick(player, e, castHolder);
        }
        else if (holder instanceof GuestPassDurationHolder castHolder) {
            plugin.gui().guestPasses().handleDurationClick(player, e, castHolder);
        }
        else if (holder instanceof GuestPassModeHolder castHolder) {
            plugin.gui().guestPasses().handleModeClick(player, e, castHolder);
        }
        else if (holder instanceof GuestPassConfirmHolder castHolder) {
            plugin.gui().guestPasses().handleConfirmClick(player, e, castHolder);
        }
        else if (holder instanceof GuestPassDetailHolder castHolder) {
            plugin.gui().guestPasses().handleDetailClick(player, e, castHolder);
        }
        else if (holder instanceof LockdownMenuHolder castHolder) {
            plugin.gui().lockdownGui().handleMenuClick(player, e, castHolder);
        }
        else if (holder instanceof LockdownOptionsHolder castHolder) {
            plugin.gui().lockdownGui().handleOptionsClick(player, e, castHolder);
        }
        else if (holder instanceof LockdownConfirmHolder castHolder) {
            plugin.gui().lockdownGui().handleConfirmClick(player, e, castHolder);
        }
        else if (holder instanceof RealmProfileMenuHolder castHolder) {
            plugin.gui().realmProfile().handleMenuClick(player, e, castHolder);
        }
        else if (holder instanceof NoticeboardHolder castHolder) {
            plugin.gui().realmProfile().handleNoticeboardClick(player, e, castHolder);
        }
        else if (holder instanceof WalkthroughHolder castHolder) {
            plugin.gui().walkthrough().handleClick(player, e, castHolder);
        }
        else if (holder instanceof RoutesMenuHolder castHolder) {
            plugin.gui().routes().handleMenuClick(player, e, castHolder);
        }
        else if (holder instanceof RouteDetailHolder castHolder) {
            plugin.gui().routes().handleDetailClick(player, e, castHolder);
        }
        else if (holder instanceof RouteAdminHolder castHolder) {
            plugin.gui().routeAdmin().handleListClick(player, e, castHolder);
        }
        else if (holder instanceof RouteEditHolder castHolder) {
            plugin.gui().routeAdmin().handleEditClick(player, e, castHolder);
        }
        else if (holder instanceof AllianceMenuHolder castHolder) {
            plugin.gui().allianceAccess().handleMenuClick(player, e, castHolder);
        }
        else if (holder instanceof AllianceConfirmHolder castHolder) {
            plugin.gui().allianceAccess().handleConfirmClick(player, e, castHolder);
        }
        else if (holder instanceof AllianceRosterHolder castHolder) {
            plugin.gui().allianceAccess().handleRosterClick(player, e, castHolder);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Strict: block ALL dragging while a menu is open
        e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ExchangeHolder
                && plugin.gui().exchange() != null) {
            plugin.gui().exchange().closeSession(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMenuClick.remove(event.getPlayer().getUniqueId());
        if (plugin.gui().exchange() != null) {
            plugin.gui().exchange().closeSession(event.getPlayer().getUniqueId());
        }
    }

    // --------------------------
    // Reload / Refresh (1.2.6)
    // --------------------------

    private enum ReloadIntent {
        FULL_RELOAD,
        LANG_REFRESH
    }

    private ReloadIntent detectReloadIntent(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        // 1) PDC-first detection
        String action = getAction(item);
        if (action != null) {
            switch (action) {
                case "reload", "reload_all", "reload_settings" -> { return ReloadIntent.FULL_RELOAD; }
                case "refresh", "refresh_lang" -> { return ReloadIntent.LANG_REFRESH; }
            }
        }

        // 2) Legacy fallback (material + display name keywords)
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        Material type = item.getType();
        boolean allowedMat =
                type == Material.REDSTONE ||
                type == Material.RECOVERY_COMPASS ||
                type == Material.COMMAND_BLOCK ||
                type == Material.REPEATING_COMMAND_BLOCK ||
                type == Material.CHAIN_COMMAND_BLOCK;

        if (!allowedMat) return null;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
        if (name == null) name = "";
        String n = name.toLowerCase(Locale.ROOT);

        // Favor "refresh/lang" if present, otherwise treat as full reload
        if (n.contains("refresh") || n.contains("refrescar") || n.contains("actualizar") || n.contains("language")) {
            return ReloadIntent.LANG_REFRESH;
        }
        if (n.contains("reload") || n.contains("recargar")) {
            return ReloadIntent.FULL_RELOAD;
        }

        return null;
    }

    private void handleGuiReload(Player player, InventoryHolder holder, ReloadIntent intent, boolean soft) {
        // Soft is only meaningful for FULL reload. For language refresh, always reopen (it’s fast and expected).
        if (intent == ReloadIntent.LANG_REFRESH) {
            plugin.effects().playMenuFlip(player);
            sendKey(player, "admin_refreshing_lang", "&aRefreshing language packs...");

            plugin.runGlobalAsync(() -> {
                try {
                    plugin.runMainGlobal(() -> {
                        try {
                            if (plugin.codex() != null) plugin.codex().reload();
                        } catch (Throwable t) {
                            plugin.getLogger().warning("[GUIListener] Codex refresh failed: " + t.getMessage());
                        }
                    });
                } catch (Throwable t) {
                    plugin.getLogger().warning("[GUIListener] runMainGlobal refresh block failed: " + t.getMessage());
                }

                plugin.runMain(player, () -> {
                    plugin.effects().playConfirm(player);
                    sendKey(player, "admin_refresh_lang_complete", "&aLanguage packs refreshed.");
                    tryReopenSameGui(player, holder);
                });
            });
            return;
        }

        // FULL reload
        if (soft) {
            plugin.effects().playMenuFlip(player);
            sendKey(player, "admin_reloading_soft", "&eReloading (soft)...");

            plugin.runGlobalAsync(() -> {
                try {
                    plugin.runMainGlobal(() -> tryInvokeReloadAegisGuard(false));
                    plugin.runMain(player, () -> plugin.effects().playConfirm(player));
                } catch (Throwable t) {
                    plugin.runMain(player, () -> {
                        plugin.effects().playError(player);
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&cReload failed: &7" + (t.getMessage() == null ? "unknown" : t.getMessage())));
                    });
                }
            });
            return;
        }

        plugin.effects().playMenuFlip(player);
        sendKey(player, "admin_reloading", "&eReloading AegisGuard...");

        plugin.runGlobalAsync(() -> {
            try {
                plugin.runMainGlobal(() -> tryInvokeReloadAegisGuard(true));
            } catch (Throwable t) {
                plugin.runMain(player, () -> {
                    plugin.effects().playError(player);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&cReload failed: &7" + (t.getMessage() == null ? "unknown" : t.getMessage())));
                });
                return;
            }

            plugin.runMain(player, () -> {
                plugin.effects().playConfirm(player);
                sendKey(player, "admin_reload_complete", "&aReload complete.");
                tryReopenSameGui(player, holder);
            });
        });
    }

    private String getAction(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.has(actionKey, PersistentDataType.STRING)) return null;
            String v = pdc.get(actionKey, PersistentDataType.STRING);
            return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void sendKey(Player p, String key, String fallback) {
        String msg = fallback;
        try {
            if (plugin.codex() != null) {
                String tr = plugin.codex().tr(p, key);
                if (tr != null && !tr.isBlank() && !tr.equalsIgnoreCase(key)) msg = tr;
            }
        } catch (Throwable ignored) {}

        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void tryInvokeReloadAegisGuard(boolean refreshMenus) {
        try {
            // Prefer reloadAegisGuard(boolean) if present
            Method m = plugin.getClass().getMethod("reloadAegisGuard", boolean.class);
            m.setAccessible(true);
            m.invoke(plugin, refreshMenus);
        } catch (Throwable t) {
            // If it exists but fails, bubble up
            throw new RuntimeException(t);
        }
    }

    // --------------------------
    // Reopen logic (unchanged core, kept compatible)
    // --------------------------

    private void tryReopenSameGui(Player player, InventoryHolder holder) {
        if (holder instanceof PlayerMenuHolder) {
            plugin.gui().openMain(player);
            return;
        }
        if (holder instanceof InfoHolder) {
            safeInvokeOpen(plugin.gui().info(), player);
            return;
        }
        if (holder instanceof SettingsGUIHolder) {
            safeInvokeOpen(plugin.gui().settings(), player);
            return;
        }
        if (holder instanceof AdminHolder) {
            safeInvokeOpen(plugin.gui().admin(), player);
            return;
        }
        if (holder instanceof DoctorHolder) {
            plugin.gui().doctor().open(player);
            return;
        }
        if (holder instanceof ExchangeHolder) {
            plugin.gui().openClaimBlockExchange(player);
            return;
        }

        if (holder instanceof SnapshotHolder castHolder) {
            if (plugin.gui().snapshotAdmin() != null) {
                int page = castHolder.getPage();
                if (!safeInvokeOpen(plugin.gui().snapshotAdmin(), player, page)) {
                    safeInvokeOpen(plugin.gui().snapshotAdmin(), player);
                }
            }
            return;
        }

        if (holder instanceof AuditHolder castHolder) {
            if (plugin.gui().audit() != null) {
                plugin.gui().audit().open(player, castHolder.getFilter(), castHolder.getPage());
            }
            return;
        }

        if (holder instanceof GuestPassMenuHolder castHolder) {
            plugin.gui().guestPasses().openMenu(player, castHolder.getPlot(), castHolder.getPage());
            return;
        }
        if (holder instanceof GuestPassAddHolder || holder instanceof GuestPassPresetHolder
                || holder instanceof GuestPassDurationHolder || holder instanceof GuestPassModeHolder
                || holder instanceof GuestPassConfirmHolder || holder instanceof GuestPassDetailHolder) {
            Object plot = readHolderValue(holder, "getPlot");
            if (plot instanceof com.aegisguard.data.Plot p) {
                plugin.gui().guestPasses().openMenu(player, p, 0);
            } else {
                plugin.gui().openMain(player);
            }
            return;
        }

        if (holder instanceof LockdownMenuHolder castHolder) {
            plugin.gui().lockdownGui().openMenu(player, castHolder.getPlot());
            return;
        }
        if (holder instanceof LockdownConfirmHolder castHolder) {
            plugin.gui().lockdownGui().openMenu(player, castHolder.getPlot());
            return;
        }

        if (holder instanceof RealmProfileMenuHolder castHolder) {
            plugin.gui().realmProfile().openMenu(player, castHolder.getPlot());
            return;
        }
        if (holder instanceof NoticeboardHolder castHolder) {
            plugin.gui().realmProfile().openNoticeboard(player, castHolder.getPlot());
            return;
        }

        if (holder instanceof WalkthroughHolder castHolder) {
            plugin.gui().walkthrough().open(player, castHolder.getPage());
            return;
        }

        if (holder instanceof RoutesMenuHolder castHolder) {
            plugin.gui().routes().open(player, castHolder.getPage());
            return;
        }
        if (holder instanceof RouteDetailHolder castHolder) {
            plugin.gui().routes().openDetail(player, castHolder.getRoute());
            return;
        }
        if (holder instanceof RouteAdminHolder castHolder) {
            plugin.gui().routeAdmin().open(player, castHolder.getPage());
            return;
        }
        if (holder instanceof RouteEditHolder castHolder) {
            plugin.gui().routeAdmin().openEdit(player, castHolder.getRoute());
            return;
        }

        if (holder instanceof AllianceMenuHolder castHolder) {
            plugin.gui().allianceAccess().openMenu(player, castHolder.getPlot());
            return;
        }
        if (holder instanceof AllianceConfirmHolder castHolder) {
            plugin.gui().allianceAccess().openMenu(player, castHolder.getPlot());
            return;
        }
        if (holder instanceof AllianceRosterHolder castHolder) {
            plugin.gui().allianceAccess().openRoster(player, castHolder.getPlot(), castHolder.getAllianceId());
            return;
        }

        Object page = readHolderValue(holder, "getPage", "page", "getCurrentPage", "currentPage");
        Object plot = readHolderValue(holder, "getPlot", "plot", "getSelectedPlot", "selectedPlot");
        Object isAdmin = readHolderValue(holder, "isAdmin", "getAdmin", "admin", "isAdminView");
        Object mode = readHolderValue(holder, "getMode", "mode");

        if (holder instanceof VisitHolder) {
            if (!safeInvokeOpen(plugin.gui().visit(), player, page, mode)) {
                if (!safeInvokeOpen(plugin.gui().visit(), player, page, isAdmin)) {
                    if (!safeInvokeOpen(plugin.gui().visit(), player, page, Boolean.FALSE)) {
                        if (!safeInvokeOpen(plugin.gui().visit(), player, page)) {
                            safeInvokeOpen(plugin.gui().visit(), player);
                        }
                    }
                }
            }
            return;
        }

        if (holder instanceof PlotListHolder) {
            if (!safeInvokeOpen(plugin.gui().plotList(), player, page)) {
                safeInvokeOpen(plugin.gui().plotList(), player);
            }
            return;
        }

        if (holder instanceof PlotSelectorHolder || holder instanceof RolesMenuHolder || holder instanceof RoleAddHolder || holder instanceof RoleManageHolder) {
            if (!safeInvokeOpen(plugin.gui().roles(), player, plot)) {
                if (!safeInvokeOpen(plugin.gui().roles(), player)) {
                    plugin.gui().openMain(player);
                }
            }
            return;
        }

        if (holder instanceof PlotFlagsHolder) {
            if (!safeInvokeOpen(plugin.gui().flags(), player, plot)) safeInvokeOpen(plugin.gui().flags(), player);
            return;
        }
        if (holder instanceof CosmeticsHolder) {
            if (!safeInvokeOpen(plugin.gui().cosmetics(), player, plot)) safeInvokeOpen(plugin.gui().cosmetics(), player);
            return;
        }
        if (holder instanceof LevelingHolder) {
            if (!safeInvokeOpen(plugin.gui().leveling(), player, plot)) safeInvokeOpen(plugin.gui().leveling(), player);
            return;
        }
        if (holder instanceof ZoningHolder) {
            if (!safeInvokeOpen(plugin.gui().zoning(), player, plot)) safeInvokeOpen(plugin.gui().zoning(), player);
            return;
        }
        if (holder instanceof ZoneBrowseHolder) {
            if (!safeInvokeOpen(plugin.gui().zoneBrowse(), player, plot)) safeInvokeOpen(plugin.gui().zoneBrowse(), player);
            return;
        }
        if (holder instanceof ZoneTenantHolder castHolder) {
            Object zoneName = readHolderValue(castHolder, "getZoneName", "zoneName");
            Object zone = null;
            if (plot instanceof com.aegisguard.data.Plot p && zoneName instanceof String zn) {
                zone = p.getZone(zn);
            }
            if (zone != null && safeInvokeOpen(plugin.gui().zoneTenant(), player, plot, zone)) {
                return;
            }
            if (!safeInvokeOpen(plugin.gui().zoneBrowse(), player, plot)) {
                plugin.gui().openMain(player);
            }
            return;
        }
        if (holder instanceof PlotMarketHolder) {
            if (!safeInvokeOpen(plugin.gui().market(), player, page)) safeInvokeOpen(plugin.gui().market(), player);
            return;
        }
        if (holder instanceof LocalMarketHolder) {
            if (!safeInvokeOpen(plugin.gui().localMarket(), player, plot)) safeInvokeOpen(plugin.gui().localMarket(), player);
            return;
        }
        if (holder instanceof StallListHolder) {
            if (!safeInvokeOpen(plugin.gui().stallBrowse(), player, plot)) safeInvokeOpen(plugin.gui().localMarket(), player, plot);
            return;
        }
        if (holder instanceof StallManageHolder castHolder) {
            Object stallKey = readHolderValue(castHolder, "getStallKey", "stallKey");
            if (!safeInvokeOpen(plugin.gui().stallBrowse(), player, plot, stallKey)) {
                if (!safeInvokeOpen(plugin.gui().stallBrowse(), player, plot)) {
                    safeInvokeOpen(plugin.gui().localMarket(), player, plot);
                }
            }
            return;
        }
        if (holder instanceof StallPreviewHolder castHolder) {
            Object stallKey = readHolderValue(castHolder, "getStallKey", "stallKey");
            if (!safeInvokeOpen(plugin.gui().stallBrowse(), player, plot, stallKey)) {
                if (!safeInvokeOpen(plugin.gui().stallBrowse(), player, plot)) {
                    safeInvokeOpen(plugin.gui().localMarket(), player, plot);
                }
            }
            return;
        }
        if (holder instanceof PlotAuctionHolder) {
            if (!safeInvokeOpen(plugin.gui().auction(), player, page)) safeInvokeOpen(plugin.gui().auction(), player);
            return;
        }

        if (holder instanceof ExpansionHolder) {
            safeInvokeOpen(plugin.gui().expansionRequest(), player);
            return;
        }
        if (holder instanceof ExpansionAdminHolder) {
            safeInvokeOpen(plugin.gui().expansionAdmin(), player);
            return;
        }

        if (holder instanceof PlotStatusHolder) {
            if (!safeInvokeOpen(plugin.gui().plotStatus(), player, plot)) safeInvokeOpen(plugin.gui().plotStatus(), player);
            return;
        }

        plugin.gui().openMain(player);
    }

    private Object readHolderValue(Object holder, String... methodCandidates) {
        if (holder == null || methodCandidates == null) return null;
        for (String name : methodCandidates) {
            if (name == null || name.isBlank()) continue;
            try {
                Method m = holder.getClass().getMethod(name);
                return m.invoke(holder);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private boolean safeInvokeOpen(Object gui, Object... args) {
        if (gui == null) return false;
        if (args == null) args = new Object[0];

        List<Object[]> attempts = new ArrayList<>();

        if (args.length >= 1 && args[0] instanceof Player p) {
            Object page = (args.length >= 2) ? args[1] : null;
            Object third = (args.length >= 3) ? args[2] : null;

            if (page instanceof Number && third instanceof Boolean) {
                attempts.add(new Object[]{p, ((Number) page).intValue(), (Boolean) third});
            }
            if (page instanceof Number && third != null && !(third instanceof Boolean)) {
                attempts.add(new Object[]{p, ((Number) page).intValue(), third});
            }
            if (page instanceof Number) {
                attempts.add(new Object[]{p, ((Number) page).intValue()});
            }

            Object plot = (args.length >= 2) ? args[1] : null;
            if (plot != null && !(plot instanceof Number) && !(plot instanceof Boolean)) {
                attempts.add(new Object[]{p, plot});
            }

            attempts.add(new Object[]{p});
        } else {
            attempts.add(args);
        }

        for (Object[] attempt : attempts) {
            if (attempt == null) continue;
            if (tryInvoke(gui, "open", attempt)) return true;
        }

        return false;
    }

    private boolean tryInvoke(Object target, String methodName, Object[] args) {
        try {
            Method[] methods = target.getClass().getMethods();
            for (Method m : methods) {
                if (!m.getName().equals(methodName)) continue;

                Class<?>[] ptypes = m.getParameterTypes();
                if (ptypes.length != args.length) continue;

                boolean ok = true;
                for (int i = 0; i < ptypes.length; i++) {
                    Object a = args[i];
                    Class<?> pt = ptypes[i];

                    if (a == null) { ok = false; break; }

                    if (pt.isPrimitive()) {
                        if (pt == int.class && a instanceof Integer) continue;
                        if (pt == int.class && a instanceof Number) { args[i] = ((Number) a).intValue(); continue; }
                        if (pt == boolean.class && a instanceof Boolean) continue;
                        ok = false; break;
                    }

                    if (!pt.isAssignableFrom(a.getClass())) {
                        if (Number.class.isAssignableFrom(pt) && a instanceof Number) continue;
                        ok = false;
                        break;
                    }
                }

                if (!ok) continue;

                m.invoke(target, args);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
