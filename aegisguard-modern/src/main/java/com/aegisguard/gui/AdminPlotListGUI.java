package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AdminPlotListGUI (1.2.6 QoL pass)
 * - PDC action routing (aegis_action) for prev/next/back + plot entries.
 * - Safer delete (SHIFT+RIGHT) to prevent accidental nukes.
 * - Keeps both back and close controls in the footer for cleaner admin navigation.
 * - Optional robustness: plot entries store plot id/owner in PDC to avoid index desync issues.
 * - Folia/perf: loads/sorts plot list async, builds inventory on main thread.
 */
public class AdminPlotListGUI {

    private final AegisGuard plugin;

    // 45 plot slots (0-44) + 9 footer (45-53)
    private static final int PLOTS_PER_PAGE = 45;

    private final NamespacedKey keyAction;
    private final NamespacedKey keyPlotId;
    private final NamespacedKey keyPlotOwner;

    public AdminPlotListGUI(AegisGuard AegisGuard) {
        this.plugin = AegisGuard;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
        this.keyPlotId = new NamespacedKey(plugin, "aegis_plot_id");
        this.keyPlotOwner = new NamespacedKey(plugin, "aegis_plot_owner");
    }

    public static class PlotListHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotListHolder(List<Plot> plots, int page) {
            this.plots = plots;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        if (!plugin.isAdmin(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.gui().tr(player, "no_perm", "&cError: You do not have permission for this.")));
            plugin.effects().playError(player);
            return;
        }

        // 1.2.6: load + sort async (store queries can be heavy), then build/open on main
        final int requestedPage = page;
        plugin.runGlobalAsync(() -> {
            List<Plot> allPlots;
            try {
                allPlots = new ArrayList<>(plugin.store().getAllPlots());
            } catch (Throwable t) {
                allPlots = new ArrayList<>();
                plugin.getLogger().warning("[AdminPlotListGUI] Failed to read plots: " + t.getMessage());
            }

            allPlots.sort(Comparator.comparing(
                    p -> p.getOwnerName() == null ? "" : p.getOwnerName(),
                    String.CASE_INSENSITIVE_ORDER
            ));

            int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
            int fixedPage = requestedPage;
            if (fixedPage < 0) fixedPage = 0;
            if (fixedPage >= maxPages && maxPages > 0) fixedPage = maxPages - 1;
            else if (maxPages == 0) fixedPage = 0;

            final List<Plot> finalPlots = allPlots;
            final int finalPage = fixedPage;
            final int finalMaxPages = maxPages;

            plugin.runMain(player, () -> buildAndOpen(player, finalPlots, finalPage, finalMaxPages));
        });
    }

    private void buildAndOpen(Player player, List<Plot> allPlots, int page, int maxPages) {
        // ✅ Localized title + page suffix (clamped safely)
        String suffix = GUIManager.color(" &8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")");
        String baseTitle = plugin.gui().title(player, "admin_plot_list_title", "&cPlot Registry");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        Inventory inv = Bukkit.createInventory(new PlotListHolder(allPlots, page), 54, title);

        // 1.2.6: fill EVERYTHING with filler so there are no “dead” holes to click/drag into
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Preload localized lore templates (with fallbacks)
        String loreIdFmt = tr(player, "admin_plot_lore_id", "&7ID: &e{ID}");
        String loreWorldFmt = tr(player, "admin_plot_lore_world", "&7World: &f{WORLD}");
        String loreBoundsFmt = tr(player, "admin_plot_lore_bounds", "&7Bounds: &a{X1}, {Z1}");
        String loreToFmt = tr(player, "admin_plot_lore_to", "&7        to &a{X2}, {Z2}");

        int startIndex = page * PLOTS_PER_PAGE;

        for (int slot = 0; slot < PLOTS_PER_PAGE; slot++) {
            int plotIndex = startIndex + slot;
            if (plotIndex >= allPlots.size()) break;

            Plot plot = allPlots.get(plotIndex);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try { meta.setOwningPlayer(owner); } catch (Exception ignored) {}

                String ownerName = plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown";

                // Name: localized with placeholder
                String rawName = null;
                try {
                    if (plugin.codex() != null) rawName = plugin.codex().tr(player, "admin_plot_item_name", Map.of("OWNER", ownerName));
                } catch (Throwable ignored) {}

                String name = GUIManager.safeText(
                        "admin_plot_item_name",
                        rawName,
                        "&bOwner: &f" + ownerName
                );

                meta.setDisplayName(name);

                // Lore: localized templates + replacements
                List<String> lore = new ArrayList<>();

                String shortId = plot.getPlotId().toString();
                if (shortId.length() > 8) shortId = shortId.substring(0, 8);

                lore.add(GUIManager.color(loreIdFmt.replace("{ID}", shortId)));
                lore.add(GUIManager.color(loreWorldFmt.replace("{WORLD}", plot.getWorld())));
                lore.add(GUIManager.color(loreBoundsFmt
                        .replace("{X1}", String.valueOf(plot.getX1()))
                        .replace("{Z1}", String.valueOf(plot.getZ1()))
                ));
                lore.add(GUIManager.color(loreToFmt
                        .replace("{X2}", String.valueOf(plot.getX2()))
                        .replace("{Z2}", String.valueOf(plot.getZ2()))
                ));

                if (plot.isServerZone()) {
                    String rawTag = null;
                    try { if (plugin.codex() != null) rawTag = plugin.codex().tr(player, "admin_server_zone_tag"); } catch (Throwable ignored) {}
                    lore.add(GUIManager.safeText("admin_server_zone_tag", rawTag, "&c[SERVER ZONE]"));
                }

                lore.add(" ");

                // 1.2.6: safer delete action
                List<String> actions = plugin.gui().trList(player, "admin_plot_actions", List.of(
                        "&eLeft-Click: &7Teleport",
                        "&bRight-Click: &7Convert → Server Plot",
                        "&cShift-Right-Click: &7Delete Plot"
                ));
                for (String line : actions) lore.add(GUIManager.color(line));

                meta.setLore(lore);

                // 1.2.6: tag plot entry with action + ids
                tagAction(meta, "plot_entry");
                tagPlot(meta, plot);

                head.setItemMeta(meta);
            }

            inv.setItem(slot, head);
        }

        // --- NAV BUTTONS (PDC-tagged) ---
        if (page > 0) {
            ItemStack prev = GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    null
            );
            tagAction(prev, "prev_page");
            inv.setItem(45, prev);
        }

        // 1.2.6: admin footer keeps both return and close controls.
        ItemStack back = GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.gui().tr(player, "button_back_admin", "&fBack to Admin"),
                plugin.gui().trList(player, "back_admin_lore", List.of("&7Return to Admin Menu."))
        );
        tagAction(back, "back_admin");
        inv.setItem(49, back);

        ItemStack close = GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        );
        tagAction(close, "close_menu");
        inv.setItem(50, close);

        if (page < maxPages - 1) {
            ItemStack next = GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                    null
            );
            tagAction(next, "next_page");
            inv.setItem(53, next);
        }

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotListHolder holder) {
        e.setCancelled(true);

        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int currentPage = holder.getPage();

        // 1.2.6: action routing (slot-independent)
        String action = getAction(clicked);
        if (action != null) {
            switch (action) {
                case "prev_page" -> { open(player, currentPage - 1); return; }
                case "next_page" -> { open(player, currentPage + 1); return; }
                case "back_admin" -> { plugin.gui().admin().open(player); return; }
                case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
                case "plot_entry" -> { /* handled below */ }
                default -> { /* ignore */ }
            }
        }

        // Plot entry click
        if (clicked.getType() != Material.PLAYER_HEAD) return;

        Plot plot = resolvePlotFromItem(clicked, holder, currentPage, e.getSlot());
        if (plot == null) {
            player.sendMessage(plugin.gui().tr(player, "admin_plot_missing", "&cPlot no longer exists."));
            open(player, currentPage);
            return;
        }

        if (GuiClicks.destructive(e)) {
            plugin.runMain(player, () -> {
                try {
                    plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                } catch (Throwable t) {
                    plugin.getLogger().warning("[AdminPlotListGUI] removePlot failed: " + t.getMessage());
                }
                plugin.msg().send(player, "admin_plot_deleted", Map.of("PLAYER", plot.getOwnerName()));
                plugin.effects().playUnclaim(player);
                open(player, currentPage);
            });
            return;
        }

        if (GuiClicks.alternate(e)) {
            if (plot.isServerZone()) {
                player.sendMessage(plugin.gui().tr(player, "convert_blocker_already_server",
                        "&eThis plot is already a server zone."));
                player.sendMessage(plugin.gui().tr(player, "admin_plot_delete_hint",
                        "&cDrop (Q) or sneak-right-click to delete this plot."));
                plugin.effects().playError(player);
                return;
            }
            plugin.effects().playMenuFlip(player);
            plugin.gui().convertToServer().openSelect(player, plot);
            return;
        }

        if (!GuiClicks.primary(e)) return;

        Location loc = plot.getCenter(plugin);
        if (loc != null && loc.getWorld() != null) {
            var result = plugin.safeTravel().travel(player, loc,
                    com.aegisguard.travel.SafeTravelService.Kind.STAFF);
            if (!result.isSuccess()) return;

            plugin.msg().send(player, "admin_plot_teleport", Map.of("PLAYER", plot.getOwnerName()));
            plugin.effects().playConfirm(player);
            player.closeInventory();
        } else {
            player.sendMessage(plugin.gui().tr(player, "admin_plot_invalid_location", "&cInvalid world or location."));
            plugin.effects().playError(player);
        }
    }

    private Plot resolvePlotFromItem(ItemStack item, PlotListHolder holder, int currentPage, int slot) {
        // 1) PDC-first (robust against list/index desync)
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String plotIdStr = pdc.get(keyPlotId, PersistentDataType.STRING);
                String ownerStr = pdc.get(keyPlotOwner, PersistentDataType.STRING);

                if (plotIdStr != null && ownerStr != null) {
                    UUID plotId = UUID.fromString(plotIdStr);
                    UUID owner = UUID.fromString(ownerStr);

                    for (Plot p : holder.getPlots()) {
                        if (p == null) continue;
                        if (owner.equals(p.getOwner()) && plotId.equals(p.getPlotId())) return p;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2) Legacy fallback (slot index)
        if (slot < 0 || slot >= PLOTS_PER_PAGE) return null;
        int plotIndex = (currentPage * PLOTS_PER_PAGE) + slot;
        if (plotIndex < 0 || plotIndex >= holder.getPlots().size()) return null;
        return holder.getPlots().get(plotIndex);
    }

    private void tagAction(ItemStack item, String action) {
        if (item == null || action == null || action.isBlank()) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            tagAction(meta, action);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private void tagAction(ItemMeta meta, String action) {
        try {
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action.trim().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {}
    }

    private void tagPlot(ItemMeta meta, Plot plot) {
        try {
            meta.getPersistentDataContainer().set(keyPlotId, PersistentDataType.STRING, plot.getPlotId().toString());
            meta.getPersistentDataContainer().set(keyPlotOwner, PersistentDataType.STRING, plot.getOwner().toString());
        } catch (Throwable ignored) {}
    }

    private String getAction(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String v = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
            return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String clampTitleWithSuffix(String base, String suffix) {
        final int MAX = 32;
        if (base == null) base = "";
        if (suffix == null) suffix = "";

        String combined = base + suffix;
        if (combined.length() <= MAX) return combined;

        if (suffix.length() >= MAX) {
            String cut = suffix.substring(0, MAX);
            return cut.endsWith("§") ? cut.substring(0, MAX - 1) : cut;
        }

        int remainingForBase = MAX - suffix.length();
        String trimmedBase = base.length() > remainingForBase ? base.substring(0, remainingForBase) : base;

        if (trimmedBase.endsWith("§")) trimmedBase = trimmedBase.substring(0, Math.max(0, trimmedBase.length() - 1));

        return trimmedBase + suffix;
    }

    /**
     * Small helper for Codex-aware single-line lookups with fallback.
     */
    private String tr(Player p, String key, String fallback) {
        String raw = null;
        try { if (plugin.codex() != null) raw = plugin.codex().tr(p, key); } catch (Throwable ignored) {}
        return GUIManager.safeText(key, raw, fallback);
    }
}
