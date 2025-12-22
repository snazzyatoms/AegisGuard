package com.aegisguard.gui;

import com.aegisguard.AegisGuard;

// --- IMPORTS (Holders) ---
import com.aegisguard.expansions.ExpansionRequestAdminGUI.ExpansionAdminHolder;
import com.aegisguard.expansions.ExpansionRequestGUI.ExpansionHolder;
import com.aegisguard.gui.AdminGUI.AdminHolder;
import com.aegisguard.gui.AdminPlotListGUI.PlotListHolder;
import com.aegisguard.gui.BiomeGUI.BiomeHolder;
import com.aegisguard.gui.InfoGUI.InfoHolder;
import com.aegisguard.gui.LevelingGUI.LevelingHolder;
import com.aegisguard.gui.PlayerGUI.PlayerMenuHolder;
import com.aegisguard.gui.PlotAuctionGUI.PlotAuctionHolder;
import com.aegisguard.gui.PlotCosmeticsGUI.CosmeticsHolder;
import com.aegisguard.gui.PlotFlagsGUI.PlotFlagsHolder;
import com.aegisguard.gui.PlotMarketGUI.PlotMarketHolder;
import com.aegisguard.gui.PlotStatusGUI.PlotStatusHolder;
import com.aegisguard.gui.RolesGUI.PlotSelectorHolder;
import com.aegisguard.gui.RolesGUI.RoleAddHolder;
import com.aegisguard.gui.RolesGUI.RoleManageHolder;
import com.aegisguard.gui.RolesGUI.RolesMenuHolder;
import com.aegisguard.gui.SettingsGUI.SettingsGUIHolder;
import com.aegisguard.gui.VisitGUI.VisitHolder;
import com.aegisguard.gui.ZoningGUI.ZoningHolder;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
import java.util.Set;

/**
 * GUIListener
 * - Central click router for ALL AegisGuard GUIs.
 * - Strictly blocks inventory movement while our menus are open.
 *
 * ✅ Upgrade:
 * - Intercepts "Refresh / Reload" buttons and reloads CodexEngine too.
 * - Attempts to reopen the same GUI after reload.
 */
public class GUIListener implements Listener {

    private final AegisGuard plugin;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // Helper: is this one of our GUIs?
    private boolean isAegisGuiHolder(InventoryHolder holder) {
        return holder instanceof PlayerMenuHolder
                || holder instanceof VisitHolder
                || holder instanceof InfoHolder
                || holder instanceof SettingsGUIHolder
                || holder instanceof AdminHolder
                || holder instanceof PlotListHolder
                || holder instanceof PlotSelectorHolder
                || holder instanceof RolesMenuHolder
                || holder instanceof RoleAddHolder
                || holder instanceof RoleManageHolder
                || holder instanceof PlotFlagsHolder
                || holder instanceof CosmeticsHolder
                || holder instanceof LevelingHolder
                || holder instanceof ZoningHolder
                || holder instanceof BiomeHolder
                || holder instanceof PlotMarketHolder
                || holder instanceof PlotAuctionHolder
                || holder instanceof ExpansionHolder
                || holder instanceof ExpansionAdminHolder
                || holder instanceof PlotStatusHolder;
    }

    /**
     * NOTE: ignoreCancelled=false so GUI buttons still work even if another plugin
     * cancels the click event earlier. We always cancel movement ourselves anyway.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Always cancel: these are menu inventories, never item-storage.
        e.setCancelled(true);

        // 2) Block clicks originating in the player's own inventory while GUI open
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(player.getInventory())) {
            return;
        }

        // Only handle clicks in the top GUI
        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(top)) return;

        // Sanity: ignore bottom inventory raw slots
        if (e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // ✅ Global: handle reload/refresh buttons (Codex + config)
        // (STRICT detection to prevent false positives like "Language: ...")
        if (isReloadTrigger(clicked)) {
            boolean soft = (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT);
            handleGuiReload(player, holder, soft);
            return;
        }

        // 1) Block ALL shift-click / hotbar swaps / offhand / double-click globally
        ClickType click = e.getClick();
        switch (click) {
            case SHIFT_LEFT,
                 SHIFT_RIGHT,
                 NUMBER_KEY,
                 DOUBLE_CLICK,
                 SWAP_OFFHAND -> {
                return;
            }
            default -> { /* continue */ }
        }

        // Route to the already-initialized GUI instances (no new allocations per click)
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
        else if (holder instanceof PlotFlagsHolder castHolder) {
            plugin.gui().flags().handleClick(player, e, castHolder);
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
        else if (holder instanceof BiomeHolder castHolder) {
            plugin.gui().biomes().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotMarketHolder castHolder) {
            plugin.gui().market().handleClick(player, e, castHolder);
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
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Strict: block ALL drags while our GUI is open.
        e.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // Reload Detection + Handling
    // ---------------------------------------------------------------------

    /**
     * Reload/refresh detection MUST be strict.
     * Otherwise "Language: ..." buttons get mistaken for reload triggers.
     *
     * Preferred: PDC aegis_action=reload|refresh|reload_all|refresh_lang|reload_settings
     * Fallback: only allow specific materials + words like reload/refresh/recargar/refrescar/actualizar.
     */
    private boolean isReloadTrigger(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // 1) PDC detection (best)
        try {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            NamespacedKey actionKey = new NamespacedKey(plugin, "aegis_action");
            if (pdc.has(actionKey, PersistentDataType.STRING)) {
                String v = pdc.get(actionKey, PersistentDataType.STRING);
                if (v != null) {
                    String vv = v.trim().toLowerCase(Locale.ROOT);
                    return vv.equals("reload")
                            || vv.equals("refresh")
                            || vv.equals("reload_all")
                            || vv.equals("refresh_lang")
                            || vv.equals("reload_settings");
                }
            }
        } catch (Throwable ignored) {}

        // 2) Fallback detection (STRICT)
        // Only allow obvious reload materials so WRITABLE_BOOK ("Language") never matches.
        Material type = item.getType();
        boolean allowedMat =
                type == Material.REDSTONE ||
                type == Material.RECOVERY_COMPASS ||
                type == Material.COMMAND_BLOCK ||
                type == Material.REPEATING_COMMAND_BLOCK ||
                type == Material.CHAIN_COMMAND_BLOCK;

        if (!allowedMat) return false;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
        if (name == null) name = "";
        String n = name.toLowerCase(Locale.ROOT);

        // IMPORTANT: do NOT match "language"/"idioma" alone
        return n.contains("reload")
                || n.contains("refresh")
                || n.contains("recargar")
                || n.contains("refrescar")
                || n.contains("actualizar");
    }

    private void handleGuiReload(Player player, InventoryHolder holder, boolean soft) {
        // Soft = reload config+codex but don't try to re-open. (Right click)
        // Normal = reload + try reopen current GUI.
        try {
            if (soft) {
                plugin.reloadAegisGuard(false);
                plugin.effects().playConfirm(player);
                return;
            }

            plugin.reloadAegisGuard(true);

            // Re-open same GUI on next tick-ish (safe with Folia & Bukkit)
            plugin.runMain(player, () -> {
                tryReopenSameGui(player, holder);
            });

            plugin.effects().playConfirm(player);
        } catch (Throwable t) {
            plugin.effects().playError(player);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cReload failed: &7" + t.getMessage()));
        }
    }

    // ---------------------------------------------------------------------
    // Reopen logic (best effort)
    // ---------------------------------------------------------------------

    private void tryReopenSameGui(Player player, InventoryHolder holder) {
        // Known simple ones
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

        // Page-based holders / Plot-based holders: try reflection args
        Object page = readHolderValue(holder, "getPage", "page", "getCurrentPage", "currentPage");
        Object plot = readHolderValue(holder, "getPlot", "plot", "getSelectedPlot", "selectedPlot");
        Object isAdmin = readHolderValue(holder, "isAdmin", "getAdmin", "admin", "isAdminView");

        // Visit GUI
        if (holder instanceof VisitHolder) {
            if (!safeInvokeOpen(plugin.gui().visit(), player, page, isAdmin)) {
                if (!safeInvokeOpen(plugin.gui().visit(), player, page, Boolean.FALSE)) {
                    if (!safeInvokeOpen(plugin.gui().visit(), player, page)) {
                        safeInvokeOpen(plugin.gui().visit(), player);
                    }
                }
            }
            return;
        }

        // Plot list
        if (holder instanceof PlotListHolder) {
            if (!safeInvokeOpen(plugin.gui().plotList(), player, page)) {
                safeInvokeOpen(plugin.gui().plotList(), player);
            }
            return;
        }

        // Roles menus
        if (holder instanceof PlotSelectorHolder || holder instanceof RolesMenuHolder || holder instanceof RoleAddHolder || holder instanceof RoleManageHolder) {
            // Best effort: open roles menu again
            if (!safeInvokeOpen(plugin.gui().roles(), player, plot)) {
                if (!safeInvokeOpen(plugin.gui().roles(), player)) {
                    plugin.gui().openMain(player);
                }
            }
            return;
        }

        // Flags / Cosmetics / Leveling / Zoning / Biome
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
        if (holder instanceof BiomeHolder) {
            if (!safeInvokeOpen(plugin.gui().biomes(), player, plot)) safeInvokeOpen(plugin.gui().biomes(), player);
            return;
        }

        // Market / Auction
        if (holder instanceof PlotMarketHolder) {
            if (!safeInvokeOpen(plugin.gui().market(), player, page)) safeInvokeOpen(plugin.gui().market(), player);
            return;
        }
        if (holder instanceof PlotAuctionHolder) {
            if (!safeInvokeOpen(plugin.gui().auction(), player, page)) safeInvokeOpen(plugin.gui().auction(), player);
            return;
        }

        // Expansion
        if (holder instanceof ExpansionHolder) {
            safeInvokeOpen(plugin.gui().expansionRequest(), player);
            return;
        }
        if (holder instanceof ExpansionAdminHolder) {
            safeInvokeOpen(plugin.gui().expansionAdmin(), player);
            return;
        }

        // Plot status
        if (holder instanceof PlotStatusHolder) {
            if (!safeInvokeOpen(plugin.gui().plotStatus(), player, plot)) safeInvokeOpen(plugin.gui().plotStatus(), player);
            return;
        }

        // Fallback
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

        // Build best-attempt argument lists in priority order
        // (Player, page, admin) -> (Player, page) -> (Player, plot) -> (Player)
        if (args.length >= 1 && args[0] instanceof Player p) {
            Object page = (args.length >= 2) ? args[1] : null;
            Object third = (args.length >= 3) ? args[2] : null;

            if (page instanceof Number && third instanceof Boolean) {
                attempts.add(new Object[]{p, ((Number) page).intValue(), (Boolean) third});
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
            // If caller didn't pass Player first, just try raw
            attempts.add(args);
        }

        // Try invoke open(...) with reflection
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

                    // primitive handling
                    if (pt.isPrimitive()) {
                        if (pt == int.class && a instanceof Integer) continue;
                        if (pt == int.class && a instanceof Number) { args[i] = ((Number) a).intValue(); continue; }
                        if (pt == boolean.class && a instanceof Boolean) continue;
                        ok = false; break;
                    }

                    if (!pt.isAssignableFrom(a.getClass())) {
                        // Allow Number -> Integer for boxed params
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
