package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.util.TeleportUtil; // Folia-safe teleports
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
 * PlotMarketGUI
 * - A paginated GUI for buying and renting plots.
 * - Fully localized for dynamic language switching.
 *
 * ✅ Title now uses plugin.gui().tr(...) + safe page suffix clamp
 * ✅ All visible strings now go through language keys (with safe fallbacks)
 */
public class PlotMarketGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45;

    public PlotMarketGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotMarketHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotMarketHolder(List<Plot> plots, int page) {
            this.plots = plots;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    /* -----------------------------
     * OPEN GUI
     * ----------------------------- */
    public void open(Player player, int page) {
        // 1. Gather all plots (Sale + Rent)
        List<Plot> allPlots = new ArrayList<>();
        allPlots.addAll(plugin.store().getPlotsForSale());
        // allPlots.addAll(plugin.store().getPlotsForRent()); // Uncomment when rent logic fully implemented

        // 2. Sort (Cheapest First)
        allPlots.sort(Comparator.comparingDouble(Plot::getSalePrice));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;
        if (maxPages == 0) page = 0;

        // ✅ Title: localized + page suffix, clamped safely to 32 chars
        String baseTitle = plugin.gui().tr(player, "market_gui_title", "&2Real Estate");
        String suffix = GUIManager.color(" &8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        Inventory inv = Bukkit.createInventory(new PlotMarketHolder(allPlots, page), 54, title);

        // 3. Fill Background (bottom row only, so listings stay empty)
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // 4. Populate Listings
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int plotIndex = startIndex + i;
            if (plotIndex >= allPlots.size()) break;

            Plot plot = allPlots.get(plotIndex);
            boolean isRent = plot.isForRent();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());

            double rawPrice = isRent ? plot.getRentPrice() : plot.getSalePrice();
            String priceStr = plugin.eco().format(rawPrice, CurrencyType.VAULT);

            // Localized Type Strings (safe)
            String typeKey = isRent ? "market_type_rent" : "market_type_sale";
            String defaultType = isRent ? "&bFor Rent" : "&aFor Sale";
            String typeStr = GUIManager.safeText(plugin.msg().get(player, typeKey), defaultType);

            String ownerName = (owner.getName() != null ? owner.getName() : "Unknown");
            String sizeStr = (plot.getX2() - plot.getX1() + 1) + "x" + (plot.getZ2() - plot.getZ1() + 1);

            // Localized display name (with safe fallback)
            String nameTpl = plugin.msg().get(player, "market_listing_name", Map.of(
                    "TYPE", typeStr,
                    "PRICE", priceStr
            ));
            if (nameTpl == null || nameTpl.isBlank()) nameTpl = typeStr + ": &e" + priceStr;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try { meta.setOwningPlayer(owner); } catch (Throwable ignored) {}

                meta.setDisplayName(GUIManager.color(nameTpl));

                List<String> lore = new ArrayList<>();

                // ✅ Localized info lines (safe fallbacks)
                String ownerLine = plugin.msg().get(player, "market_lore_owner", Map.of("OWNER", ownerName));
                if (ownerLine == null || ownerLine.isBlank()) ownerLine = "&7Owner: &f" + ownerName;
                lore.add(GUIManager.color(ownerLine));

                String worldLine = plugin.msg().get(player, "market_lore_world", Map.of("WORLD", plot.getWorld()));
                if (worldLine == null || worldLine.isBlank()) worldLine = "&7World: &f" + plot.getWorld();
                lore.add(GUIManager.color(worldLine));

                String sizeLine = plugin.msg().get(player, "market_lore_size", Map.of("SIZE", sizeStr));
                if (sizeLine == null || sizeLine.isBlank()) sizeLine = "&7Size: &e" + sizeStr;
                lore.add(GUIManager.color(sizeLine));

                if (plot.getDescription() != null && !plot.getDescription().isEmpty()) {
                    String noteLine = plugin.msg().get(player, "market_lore_note", Map.of("NOTE", plot.getDescription()));
                    if (noteLine == null || noteLine.isBlank()) noteLine = "&7Note: &f" + plot.getDescription();
                    lore.add(GUIManager.color(noteLine));
                }

                lore.add(" ");

                // Localized action hints (safe list)
                List<String> hints = plugin.msg().getList(player, "market_item_lore");
                if (hints == null || hints.isEmpty()) {
                    hints = List.of(
                            "&eLeft Click: &7Preview claim",
                            "&aRight Click: &7Purchase"
                    );
                }
                for (String s : hints) lore.add(GUIManager.color(s));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // 5. Navigation Buttons

        // Previous page
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    null
            ));
        }

        // Back to main menu
        inv.setItem(48, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.gui().tr(player, "button_back_menu", "&fReturn to Menu"),
                plugin.gui().trList(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        // Next page
        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                    null
            ));
        }

        // Exit
        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /* -----------------------------
     * HANDLER
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e, PlotMarketHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int page = holder.getPage();

        // Nav (only act if the clicked item matches)
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { open(player, page - 1); return; }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { open(player, page + 1); return; }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 49) { player.closeInventory(); return; }

        // Listing Click
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (page * PLOTS_PER_PAGE) + slot;
            if (index >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(index);
            if (plot == null) return;

            // Teleport (Left Click)
            if (e.getClick().isLeftClick()) {
                Location center = plot.getCenter(plugin);
                if (center != null) {
                    // Folia/Paper-safe teleport
                    TeleportUtil.safeTeleport(plugin, player, center);
                    player.closeInventory();
                    plugin.msg().send(player, "market-teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                }
            }
            // Buy/Rent (Right Click)
            else if (e.getClick().isRightClick()) {
                if (plot.isForSale()) {
                    handleBuy(player, plot);
                } else if (plot.isForRent()) {
                    handleRent(player, plot);
                }
            }
        }
    }

    private void handleBuy(Player buyer, Plot plot) {
        // 1. Validation
        if (plot.getOwner().equals(buyer.getUniqueId())) {
            plugin.msg().send(buyer, "market-buy-own");
            plugin.effects().playError(buyer);
            return;
        }

        int max = plugin.cfg().getWorldMaxClaims(buyer.getWorld());
        int current = plugin.store().getPlots(buyer.getUniqueId()).size();
        if (current >= max && max > 0 && !plugin.isAdmin(buyer)) {
            plugin.msg().send(buyer, "max_claims_reached", Map.of("AMOUNT", String.valueOf(max)));
            return;
        }

        // 2. Transaction
        double price = plot.getSalePrice();
        if (!plugin.eco().withdraw(buyer, price, CurrencyType.VAULT)) {
            plugin.msg().send(buyer, "need_vault", Map.of(
                    "AMOUNT", plugin.eco().format(price, CurrencyType.VAULT)
            ));
            return;
        }

        // 3. Pay Seller (safe if offline)
        OfflinePlayer seller = Bukkit.getOfflinePlayer(plot.getOwner());
        Player sellerPlayer = seller.getPlayer();
        if (sellerPlayer != null) {
            plugin.eco().deposit(sellerPlayer, price, CurrencyType.VAULT);
        }

        // 4. Transfer
        plugin.store().changePlotOwner(plot, buyer.getUniqueId(), buyer.getName());
        plot.setForSale(false, 0); // Remove from market
        plugin.store().setDirty(true);

        // 5. Notify
        plugin.msg().send(buyer, "market-buy-success", Map.of(
                "PRICE", plugin.eco().format(price, CurrencyType.VAULT),
                "PLAYER", seller.getName()
        ));
        plugin.effects().playClaimSuccess(buyer);

        if (sellerPlayer != null && seller.isOnline()) {
            plugin.msg().send(sellerPlayer, "market-sold", Map.of(
                    "PRICE", plugin.eco().format(price, CurrencyType.VAULT),
                    "PLAYER", buyer.getName()
            ));
        }

        buyer.closeInventory();
    }

    private void handleRent(Player renter, Plot plot) {
        renter.sendMessage(plugin.msg().get(renter, "market-rent-soon")); // "Coming Soon" message
    }

    // -----------------------------
    // Title clamp helpers
    // -----------------------------
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
}
