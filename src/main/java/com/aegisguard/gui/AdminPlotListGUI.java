package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.util.TeleportUtil;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AdminPlotListGUI
 * - A paginated GUI for admins to view and manage all plots.
 * - Fully localized for language switching (titles + item names + lore + buttons).
 * - ✅ Title fixed via GUIManager.title() (translates & + hex + clamps)
 */
public class AdminPlotListGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45;

    public AdminPlotListGUI(AegisGuard AegisGuard) {
        this.plugin = AegisGuard;
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
        List<Plot> allPlots = new ArrayList<>(plugin.store().getAllPlots());

        // Safer sort (avoid null owner names causing NPE)
        allPlots.sort(Comparator.comparing(
                p -> p.getOwnerName() == null ? "" : p.getOwnerName(),
                String.CASE_INSENSITIVE_ORDER
        ));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= maxPages && maxPages > 0) page = maxPages - 1;
        else if (maxPages == 0) page = 0;

        // ✅ Localized title + page suffix (clamped safely)
        String suffix = GUIManager.color(" &8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")");
        String baseTitle = plugin.gui().title(player, "admin_plot_list_title", "&cPlot Registry");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        Inventory inv = Bukkit.createInventory(new PlotListHolder(allPlots, page), 54, title);

        // Fill footer background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Preload localized lore templates (with fallbacks)
        String loreIdFmt = tr(player, "admin_plot_lore_id", "&7ID: &e{ID}");
        String loreWorldFmt = tr(player, "admin_plot_lore_world", "&7World: &f{WORLD}");
        String loreBoundsFmt = tr(player, "admin_plot_lore_bounds", "&7Bounds: &a{X1}, {Z1}");
        String loreToFmt = tr(player, "admin_plot_lore_to", "&7        to &a{X2}, {Z2}");

        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int plotIndex = startIndex + i;
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
                    rawName = plugin.codex().tr(player, "admin_plot_item_name", Map.of("OWNER", ownerName));
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
                    try { rawTag = plugin.codex().tr(player, "admin_server_zone_tag"); } catch (Throwable ignored) {}
                    lore.add(GUIManager.safeText("admin_server_zone_tag", rawTag, "&c[SERVER ZONE]"));
                }

                lore.add(" ");

                List<String> actions = plugin.gui().trList(player, "admin_plot_actions", List.of(
                        "&eLeft-Click: &7Teleport",
                        "&cRight-Click: &7Delete Plot"
                ));
                for (String line : actions) lore.add(GUIManager.color(line));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // --- NAV BUTTONS ---
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    null
            ));
        }

        inv.setItem(48, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.gui().tr(player, "button_back_admin", "&fBack to Admin"),
                plugin.gui().trList(player, "back_admin_lore", List.of("&7Return to Admin Menu."))
        ));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                    null
            ));
        }

        // Exit button
        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotListHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, currentPage - 1);
            return;
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, currentPage + 1);
            return;
        }
        if (slot == 48) {
            plugin.gui().admin().open(player);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int plotIndex = (currentPage * PLOTS_PER_PAGE) + slot;
            if (plotIndex >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(plotIndex);
            if (plot == null) {
                // Localized error (fallback included)
                player.sendMessage(plugin.gui().tr(player, "admin_plot_missing", "&cPlot no longer exists."));
                open(player, currentPage);
                return;
            }

            if (e.getClick().isLeftClick()) {
                Location loc = plot.getCenter(plugin);
                if (loc != null && loc.getWorld() != null) {
                    int y = loc.getWorld().getHighestBlockYAt(loc);
                    loc.setY(y + 1);

                    TeleportUtil.safeTeleport(plugin, player, loc);

                    // Chat feedback stays in msg system (already keyed)
                    plugin.msg().send(player, "admin_plot_teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                    player.closeInventory();
                } else {
                    player.sendMessage(plugin.gui().tr(player, "admin_plot_invalid_location", "&cInvalid world or location."));
                }
            } else if (e.getClick().isRightClick()) {
                plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                plugin.msg().send(player, "admin_plot_deleted", Map.of("PLAYER", plot.getOwnerName()));
                plugin.effects().playUnclaim(player);
                open(player, currentPage);
            }
        }
    }

    private String clampTitleWithSuffix(String base, String suffix) {
        final int MAX = 32;
        if (base == null) base = "";
        if (suffix == null) suffix = "";

        String combined = base + suffix;
        if (combined.length() <= MAX) return combined;

        // If suffix alone is too long, clamp it hard.
        if (suffix.length() >= MAX) {
            String cut = suffix.substring(0, MAX);
            return cut.endsWith("§") ? cut.substring(0, MAX - 1) : cut;
        }

        int remainingForBase = MAX - suffix.length();
        String trimmedBase = base.length() > remainingForBase ? base.substring(0, remainingForBase) : base;

        // Avoid cutting off a color code marker.
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
