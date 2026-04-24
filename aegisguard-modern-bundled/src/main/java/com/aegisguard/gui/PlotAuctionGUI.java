package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.util.TeleportUtil; // ✅ Folia-safe teleports
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.UUID;

/**
 * PlotAuctionGUI
 * - Allows players to bid on expired plots.
 * - Fully localized (title + items + lore + navigation + feedback).
 *
 * ✅ Uses GUIManager.color() (public) and safe localized fallbacks.
 * ✅ Title clamped safely with page suffix.
 */
public class PlotAuctionGUI {

    private final AegisGuard plugin;
    private static final int PLOTS_PER_PAGE = 45;

    public PlotAuctionGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotAuctionHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotAuctionHolder(List<Plot> plots, int page) {
            this.plots = plots;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        List<Plot> allPlots = new ArrayList<>(plugin.store().getPlotsForAuction());
        allPlots.sort(Comparator.comparingDouble(Plot::getCurrentBid));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;
        if (maxPages == 0) page = 0;

        // Base title (already safe + colorized + clamped)
        String baseTitle = plugin.gui().title(
                player,
                "auction_gui_title",
                "&6🏷 Plot Auctions"
        );

        // Add page suffix and clamp again
        String suffix = GUIManager.color(" &8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        Inventory inv = Bukkit.createInventory(new PlotAuctionHolder(allPlots, page), 54, title);

        // Footer background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Common localized words
        String noneWord = word(player, "none", "None");
        String unknownWord = word(player, "unknown_player", "Unknown");

        // Listings
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int plotIndex = startIndex + i;
            if (plotIndex >= allPlots.size()) break;

            Plot plot = allPlots.get(plotIndex);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());

            UUID bidderId = plot.getCurrentBidder();
            OfflinePlayer currentBidder = (bidderId != null) ? Bukkit.getOfflinePlayer(bidderId) : null;

            String bidderName = (currentBidder != null && currentBidder.getName() != null)
                    ? currentBidder.getName()
                    : (bidderId == null ? noneWord : unknownWord);

            String ownerName = (plot.getOwnerName() != null && !plot.getOwnerName().isBlank())
                    ? plot.getOwnerName()
                    : unknownWord;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try { meta.setOwningPlayer(owner); } catch (Throwable ignored) {}

                // Localized Item Name
                String itemName = line(player,
                        "auction_item_name",
                        "&ePlot Auction (&f{OWNER}&e)",
                        Map.of("OWNER", ownerName)
                );
                meta.setDisplayName(itemName);

                List<String> lore = new ArrayList<>();

                // Localized world/size lines
                lore.add(line(player,
                        "auction_lore_world",
                        "&7World: &f{WORLD}",
                        Map.of("WORLD", plot.getWorld())
                ));

                int sizeX = (plot.getX2() - plot.getX1() + 1);
                int sizeZ = (plot.getZ2() - plot.getZ1() + 1);
                lore.add(line(player,
                        "auction_lore_size",
                        "&7Size: &a{X}x{Z}",
                        Map.of("X", String.valueOf(sizeX), "Z", String.valueOf(sizeZ))
                ));

                lore.add(" ");

                String bidStr = plugin.eco().format(plot.getCurrentBid(), CurrencyType.VAULT);

                lore.add(line(player,
                        "auction_current_bid",
                        "&7Current Bid: &e{AMOUNT}",
                        Map.of("AMOUNT", bidStr)
                ));

                lore.add(line(player,
                        "auction_highest_bidder",
                        "&7Highest Bidder: &f{PLAYER}",
                        Map.of("PLAYER", bidderName)
                ));

                lore.add(" ");

                // Action hints
                lore.addAll(lines(player,
                        "auction_item_lore",
                        List.of(
                                "&eLeft-click: &7Teleport to the plot.",
                                "&cRight-click: &7Place a bid."
                        )
                ));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(
                    Material.ARROW,
                    safeName(player, "button_prev_page", "&fPrevious"),
                    null
            ));
        }

        inv.setItem(48, GUIManager.createItem(
                Material.NETHER_STAR,
                safeName(player, "button_back_menu", "&fBack"),
                safeLore(player, "back_menu_lore", List.of("&7Return to the previous menu."))
        ));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(
                    Material.ARROW,
                    safeName(player, "button_next_page", "&fNext"),
                    null
            ));
        }

        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                safeName(player, "button_exit", "&cClose"),
                safeLore(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotAuctionHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage - 1); return; }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage + 1); return; }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 49) { player.closeInventory(); return; }

        // Listing Click
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (currentPage * PLOTS_PER_PAGE) + slot;
            if (index >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(index);
            if (plot == null) return;

            if (e.getClick().isLeftClick()) {
                Location loc = plot.getCenter(plugin);
                if (loc != null) {
                    TeleportUtil.safeTeleport(plugin, player, loc);
                    player.closeInventory();
                    plugin.msg().send(player, "market-teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                }
            } else if (e.getClick().isRightClick()) {
                bidOnPlot(player, plot);
                open(player, currentPage);
            }
        }
    }

    private void bidOnPlot(Player bidder, Plot plot) {
        if (plot.getOwner().equals(bidder.getUniqueId())) {
            plugin.msg().send(bidder, "auction-bid-own");
            plugin.effects().playError(bidder);
            return;
        }

        if (bidder.getUniqueId().equals(plot.getCurrentBidder())) {
            plugin.msg().send(bidder, "auction-already-highest");
            plugin.effects().playError(bidder);
            return;
        }

        double currentBid = plot.getCurrentBid();
        double minIncrease = plugin.cfg().raw().getDouble("auction.min_bid_increase", 100.0);
        double newBid = (currentBid == 0) ? minIncrease : currentBid + minIncrease;

        if (!plugin.eco().withdraw(bidder, newBid, CurrencyType.VAULT)) {
            plugin.msg().send(bidder, "need_vault",
                    Map.of("AMOUNT", plugin.eco().format(newBid, CurrencyType.VAULT)));
            plugin.effects().playError(bidder);
            return;
        }

        // Refund old bidder (if online)
        if (plot.getCurrentBidder() != null) {
            OfflinePlayer oldBidder = Bukkit.getOfflinePlayer(plot.getCurrentBidder());
            if (oldBidder.getPlayer() != null) {
                plugin.eco().deposit(oldBidder.getPlayer(), currentBid, CurrencyType.VAULT);
                if (oldBidder.isOnline()) {
                    plugin.msg().send(oldBidder.getPlayer(), "auction-outbid",
                            Map.of("PLAYER", bidder.getName()));
                }
            }
        }

        plot.setCurrentBid(newBid, bidder.getUniqueId());
        plugin.store().setDirty(true);

        plugin.msg().send(bidder, "auction-bid-success",
                Map.of("AMOUNT", plugin.eco().format(newBid, CurrencyType.VAULT)));
        plugin.effects().playConfirm(bidder);
    }

    // --------------------------------------------------
    // Localization + Title Safety Helpers
    // --------------------------------------------------

    private String safeName(Player p, String key, String fallback) {
        String s = null;
        try { s = plugin.msg().get(p, key); } catch (Throwable ignored) {}
        if (s == null || s.isBlank() || s.equalsIgnoreCase(key) || s.contains("[Missing")) s = fallback;
        return s;
    }

    private List<String> safeLore(Player p, String key, List<String> fallback) {
        List<String> list = null;
        try { list = plugin.msg().getList(p, key); } catch (Throwable ignored) {}
        return (list == null || list.isEmpty()) ? (fallback == null ? List.of() : fallback) : list;
    }

    private String word(Player p, String key, String fallback) {
        String s = null;
        try { s = plugin.msg().get(p, key); } catch (Throwable ignored) {}
        if (s == null || s.isBlank() || s.equalsIgnoreCase(key) || s.contains("[Missing")) s = fallback;
        // Return as plain text (no color noise in names)
        return ChatColor.stripColor(GUIManager.color(s));
    }

    private String line(Player p, String key, String fallback, Map<String, String> vars) {
        String s = null;
        try {
            if (vars == null || vars.isEmpty()) s = plugin.msg().get(p, key);
            else s = plugin.msg().get(p, key, vars);
        } catch (Throwable ignored) {}

        if (s == null || s.isBlank() || s.equalsIgnoreCase(key) || s.contains("[Missing")) s = fallback;
        return GUIManager.color(s);
    }

    private List<String> lines(Player p, String key, List<String> fallback) {
        List<String> list = null;
        try { list = plugin.msg().getList(p, key); } catch (Throwable ignored) {}
        if (list == null || list.isEmpty()) list = (fallback == null ? List.of() : fallback);

        List<String> out = new ArrayList<>();
        for (String s : list) out.add(GUIManager.color(s));
        return out;
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
}
