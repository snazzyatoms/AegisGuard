package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.util.TeleportUtil;          // ✅ Folia-safe teleports
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VisitGUI
 * - Allows players to warp to plots they are trusted on.
 * - Allows warping to public Server Zones (Warps).
 * - Fully localized for language switching.
 *
 * ✅ Title fix: uses plugin.gui().title(...) + safe suffix clamp
 * ✅ Click fix: prevents filler arrows from paging
 * ✅ Bottom inventory ignored
 * ✅ 1.2.6 QOL: respects travel/visit toggles + access re-check on teleport
 */
public class VisitGUI {

    private final AegisGuard plugin;
    private static final int PLOTS_PER_PAGE = 45;

    public VisitGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class VisitHolder implements InventoryHolder {
        private final int page;
        private final boolean showingWarps;
        private final List<Plot> plots;

        public VisitHolder(List<Plot> plots, int page, boolean showingWarps) {
            this.plots = plots;
            this.page = page;
            this.showingWarps = showingWarps;
        }

        public int getPage() { return page; }
        public boolean isShowingWarps() { return showingWarps; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    // --------------------------------------------------
    // Codex-safe helpers (with fallbacks)
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String safeRole(String role) {
        if (role == null || role.isBlank()) return "Member";
        return role;
    }

    private void sendSystem(Player p, String key, String fallback) {
        String msg = t(p, key, fallback);
        if (msg == null || msg.isBlank()) return;
        p.sendMessage(GUIManager.color(msg));
    }

    private boolean canTeleport(Player p) {
        // Extra safety for live reloads: command gate may be bypassed if GUI is already open.
        if (plugin.cfg() != null && !plugin.cfg().isTravelSystemEnabled()) {
            sendSystem(p, "travel_system_disabled", "&cTravel System is disabled.");
            return false;
        }
        if (plugin.cfg() != null && !plugin.cfg().allowVisitTeleport()) {
            sendSystem(p, "visit_teleport_disabled", "&cVisiting is currently disabled.");
            return false;
        }
        return true;
    }

    // --------------------------------------------------
    // OPEN
    // --------------------------------------------------

    public void open(Player player, int page, boolean showWarps) {
        List<Plot> displayPlots = new ArrayList<>();

        // --- FILTER LOGIC ---
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;

            if (showWarps) {
                if (plot.isServerWarp()) displayPlots.add(plot);
            } else {
                // Trusted Plots (Member/Co-Owner but NOT Owner)
                if (plot.getPlayerRoles() != null
                        && plot.getPlayerRoles().containsKey(player.getUniqueId())
                        && plot.getOwner() != null
                        && !plot.getOwner().equals(player.getUniqueId())) {
                    displayPlots.add(plot);
                }
            }
        }

        // Sort Alphabetically
        displayPlots.sort((p1, p2) -> {
            String n1 = showWarps ? p1.getWarpName() : p1.getOwnerName();
            String n2 = showWarps ? p2.getWarpName() : p2.getOwnerName();
            if (n1 == null || n1.isBlank()) n1 = "Unknown";
            if (n2 == null || n2.isBlank()) n2 = "Unknown";
            return n1.compareToIgnoreCase(n2);
        });

        // Pagination
        int maxPages = (int) Math.ceil((double) displayPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;
        if (maxPages == 0) page = 0;

        // Localized titles
        String modeTitleKey = showWarps ? "visit_title_warps" : "visit_title_trusted";
        String fallbackTitle = showWarps ? "&6Server Waypoints" : "&9Trusted Claims";

        // ✅ Base title via central formatter (colors + fallback + clamp)
        String baseTitle = plugin.gui().title(player, modeTitleKey, fallbackTitle);

        // ✅ Add suffix (use § so we don't rely on & here), then clamp
        String fullTitle = clampTitle(baseTitle + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")");
        Inventory inv = Bukkit.createInventory(new VisitHolder(displayPlots, page, showWarps), 54, fullTitle);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Empty state
        if (displayPlots.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    t(player, "visit_empty_title", "&cNothing to visit"),
                    tl(player, "visit_empty_lore", List.of(
                            "&7No destinations were found for this mode.",
                            "&7Try switching tabs."
                    ))
            ));
        }

        // Populate Items
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= displayPlots.size()) break;

            Plot plot = displayPlots.get(index);
            if (plot == null) continue;

            ItemStack icon;

            if (showWarps) {
                // Server Warp
                Material mat = plot.getWarpIcon() != null ? plot.getWarpIcon() : Material.BEACON;
                String name = plot.getWarpName() != null ? plot.getWarpName() : "Server Warp";

                String dn = t(player, "visit_warp_name", "&6{WARP}")
                        .replace("{WARP}", name);

                List<String> lore = tl(player, "visit_warp_lore", List.of(
                        "&7A public waypoint.",
                        " ",
                        "&eClick to teleport"
                ));

                icon = GUIManager.createItem(
                        mat,
                        dn,
                        lore
                );
            } else {
                // Trusted Plot
                OfflinePlayer owner = (plot.getOwner() != null) ? Bukkit.getOfflinePlayer(plot.getOwner()) : null;
                String role = safeRole(plot.getRole(player.getUniqueId()));
                String ownerName = (plot.getOwnerName() != null) ? plot.getOwnerName() : "Unknown";
                String alias = (plot.getEntryTitle() != null && !plot.getEntryTitle().isBlank())
                        ? plot.getEntryTitle()
                        : ownerName + "'s Plot";

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    if (owner != null) {
                        try { meta.setOwningPlayer(owner); } catch (Throwable ignored) {}
                    }

                    // Display name
                    String dnRaw = null;
                    try {
                        if (plugin.codex() != null) dnRaw = plugin.codex().tr(player, "visit_plot_name", Map.of("PLOT", alias));
                    } catch (Throwable ignored) {}

                    String displayName = GUIManager.safeText(dnRaw, "&e" + alias);
                    meta.setDisplayName(GUIManager.color(displayName));

                    // Lore with placeholders
                    List<String> baseLore = tl(player, "visit_plot_lore", List.of(
                            "&7World: &f{WORLD}",
                            "&7Role: &b{ROLE}",
                            " ",
                            "&eClick to teleport"
                    ));

                    String worldName = (plot.getWorld() != null ? plot.getWorld() : "Unknown");

                    List<String> lore = new ArrayList<>();
                    for (String line : baseLore) {
                        if (line == null) continue;
                        lore.add(GUIManager.color(
                                line.replace("{WORLD}", worldName)
                                        .replace("{ROLE}", role)
                        ));
                    }

                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                icon = head;
            }

            inv.setItem(i, icon);
        }

        // --- TOGGLE BUTTON (Slot 49) ---
        if (showWarps) {
            inv.setItem(49, GUIManager.createItem(
                    Material.PLAYER_HEAD,
                    t(player, "visit_switch_trusted", "&9Trusted Claims"),
                    tl(player, "visit_switch_trusted_lore", List.of("&7Show plots you are trusted on."))
            ));
        } else {
            inv.setItem(49, GUIManager.createItem(
                    Material.BEACON,
                    t(player, "visit_switch_warps", "&6Server Waypoints"),
                    tl(player, "visit_switch_warps_lore", List.of("&7Show server public warps."))
            ));
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_prev_page", "&fPrevious Page"),
                    null
            ));
        }
        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_next_page", "&fNext Page"),
                    null
            ));
        }

        // Back
        inv.setItem(48, GUIManager.createItem(
                Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main menu."))
        ));

        // Exit (does not conflict with toggle @49)
        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --------------------------------------------------
    // CLICK HANDLER
    // --------------------------------------------------

    public void handleClick(Player player, InventoryClickEvent e, VisitHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // Ignore clicks from bottom inventory
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        int slot = e.getSlot();
        boolean warps = holder.isShowingWarps();

        // Nav (only if the item is actually an arrow)
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { open(player, holder.getPage() - 1, warps); return; }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { open(player, holder.getPage() + 1, warps); return; }

        // Back / Exit
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 50 && e.getCurrentItem().getType() == Material.BARRIER) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }

        // Switch Mode
        if (slot == 49) {
            open(player, 0, !warps);
            plugin.effects().playMenuFlip(player);
            return;
        }

        // Teleport
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() != Material.AIR) {
            int index = (holder.getPage() * PLOTS_PER_PAGE) + slot;
            if (index < 0 || index >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(index);
            if (plot == null) return;

            // 1.2.6 QOL: re-check access before teleporting (trust/warp might have changed)
            if (warps) {
                if (!plot.isServerWarp()) {
                    sendSystem(player, "visit_not_available", "&cThat waypoint is no longer available.");
                    plugin.effects().playError(player);
                    open(player, holder.getPage(), true);
                    return;
                }
            } else {
                if (plot.getPlayerRoles() == null || !plot.getPlayerRoles().containsKey(player.getUniqueId())
                        || (plot.getOwner() != null && plot.getOwner().equals(player.getUniqueId()))) {
                    sendSystem(player, "visit_not_trusted", "&cYou are no longer trusted on that plot.");
                    plugin.effects().playError(player);
                    open(player, holder.getPage(), false);
                    return;
                }
            }

            if (!canTeleport(player)) {
                plugin.effects().playError(player);
                return;
            }

            Location target = plot.getSpawnLocation() != null
                    ? plot.getSpawnLocation()
                    : plot.getCenter(plugin);

            if (target == null || target.getWorld() == null) {
                sendSystem(player, "visit_fail_no_spawn", "&cThis destination has no valid spawn set.");
                plugin.effects().playError(player);
                return;
            }

            // Close first to avoid double-click spam and to keep Folia-safe teleport predictable
            player.closeInventory();

            TeleportUtil.safeTeleport(plugin, player, target);
            sendSystem(player, "visit_teleport_success", "&aTeleported.");
            plugin.effects().playTeleport(player);
        }
    }

    // Vanilla inventory title safety clamp (simple, consistent with your other GUIs)
    private String clampTitle(String t) {
        if (t == null) return "";
        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
