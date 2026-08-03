package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.territory.TerritoryLifeService;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<UUID> activeTransactions = ConcurrentHashMap.newKeySet();

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
        if (plugin.cfg().raw().getBoolean("full_plot_renting.enabled", true)) {
            plugin.store().getAllPlots().stream()
                    .filter(plot -> plot != null && plot.isForRent() && !plot.hasActiveRental())
                    .filter(plot -> allPlots.stream().noneMatch(existing -> existing.getPlotId().equals(plot.getPlotId())))
                    .forEach(allPlots::add);
        }

        // 2. Sort (Cheapest First)
        allPlots.sort(Comparator.comparingDouble(plot -> plot.isForSale() ? plot.getSalePrice() : plot.getRentPrice()));

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

                if (isRent) {
                    int defaultDays = Math.max(1, plugin.cfg().raw().getInt("full_plot_renting.duration_days", 7));
                    TerritoryLifeService.RentalOffer offer = plugin.territoryLife().getOffer(
                            plot.getPlotId(), plot.getRentPrice(), defaultDays);
                    lore.add(GUIManager.color("&7Rental term: &b" + offer.termDays() + " day"
                            + (offer.termDays() == 1 ? "" : "s")));
                    lore.add(GUIManager.color("&7Security deposit: &6"
                            + plugin.eco().format(offer.deposit(), CurrencyType.VAULT)));
                }

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
                    var result = plugin.safeTravel().travel(player, center,
                            com.aegisguard.travel.SafeTravelService.Kind.MARKET);
                    if (!result.isSuccess()) return;
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
                    openRentConfirm(player, plot);
                }
            }
        }
    }

    private void openRentConfirm(Player player, Plot plot) {
        if (!plugin.cfg().raw().getBoolean("full_plot_renting.enabled", true)) {
            plugin.msg().send(player, "market-renting-disabled");
            return;
        }
        if (plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "market-rent-own");
            return;
        }
        double price = plot.getRentPrice();
        if (!plot.isForRent() || plot.hasActiveRental() || !Double.isFinite(price) || price <= 0.0) {
            plugin.msg().send(player, "market-listing-unavailable");
            return;
        }
        int defaultDays = Math.max(1, plugin.cfg().raw().getInt("full_plot_renting.duration_days", 7));
        TerritoryLifeService.RentalOffer offer = plugin.territoryLife().getOffer(plot.getPlotId(), price, defaultDays);
        plugin.gui().rentConfirm().openPlotRent(player, plot, price, offer.deposit(), offer.termDays(), "market");
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

        double price = plot.getSalePrice();
        if (!plot.isForSale() || !Double.isFinite(price) || price <= 0.0 || plot.hasActiveRental()
                || plot.getZones().stream().anyMatch(zone -> zone.isRented())) {
            plugin.msg().send(buyer, "market-listing-unavailable");
            plugin.effects().playError(buyer);
            return;
        }

        UUID transactionId = plot.getPlotId();
        if (!activeTransactions.add(transactionId)) {
            plugin.msg().send(buyer, "market-transaction-busy");
            return;
        }

        UUID sellerId = plot.getOwner();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
        String candidateSellerName = seller.getName() == null ? plot.getOwnerName() : seller.getName();
        final String sellerName = candidateSellerName == null || candidateSellerName.isBlank()
                ? "Unknown" : candidateSellerName;

        boolean buyerCharged = false;
        boolean sellerPaid = false;
        try {
        if (!plugin.eco().withdraw(buyer, price, CurrencyType.VAULT)) {
            plugin.msg().send(buyer, "need_vault", Map.of(
                    "AMOUNT", plugin.eco().format(price, CurrencyType.VAULT)
            ));
            return;
        }
        buyerCharged = true;

        if (plugin.vault() == null || !plugin.vault().deposit(seller, price)) {
            if (plugin.vault() != null) plugin.vault().deposit(buyer, price);
            buyerCharged = false;
            plugin.msg().send(buyer, "market-payment-failed");
            plugin.getLogger().warning("Plot sale aborted: could not pay offline seller " + sellerId + ".");
            return;
        }
        sellerPaid = true;

        plot.setForSale(false, 0); // Remove from market
        plot.setForRent(false, 0);
        plugin.store().changePlotOwner(plot, buyer.getUniqueId(), buyer.getName());
        plugin.store().savePlotSync(plot);
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.territoryLife().log(plot.getPlotId(), buyer.getUniqueId(), "PLOT_SOLD",
                "Ownership transferred from " + sellerName + " to " + buyer.getName() + " for " + price + ".");
        plugin.getClaimBlockManager().invalidateOwnerCache(sellerId);
        plugin.getClaimBlockManager().invalidateOwnerCache(buyer.getUniqueId());
        if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();

        plugin.msg().send(buyer, "market-buy-success", Map.of(
                "PRICE", plugin.eco().format(price, CurrencyType.VAULT),
                "PLAYER", sellerName
        ));
        plugin.effects().playClaimSuccess(buyer);

        Player sellerPlayer = seller.getPlayer();
        if (sellerPlayer != null && seller.isOnline()) {
            plugin.runMain(sellerPlayer, () -> plugin.msg().send(sellerPlayer, "market-sold", Map.of(
                    "PRICE", plugin.eco().format(price, CurrencyType.VAULT), "PLAYER", buyer.getName())));
        }

        buyer.closeInventory();
        } catch (Throwable transactionError) {
            plugin.getLogger().severe("Plot sale transaction failed for " + transactionId + ": " + transactionError.getMessage());
            if (sellerPaid && plugin.vault() != null && !plugin.vault().charge(seller, price)) {
                plugin.getLogger().severe("Could not reverse seller payment for failed plot sale " + transactionId + ".");
            }
            if (buyerCharged && plugin.vault() != null) plugin.vault().deposit(buyer, price);
            if (plot.getOwner().equals(buyer.getUniqueId())) {
                plugin.store().changePlotOwner(plot, sellerId, sellerName);
            }
            plot.setForSale(true, price);
            plugin.store().savePlotSync(plot);
            plugin.msg().send(buyer, "market-payment-failed");
        } finally {
            activeTransactions.remove(transactionId);
        }
    }

    public void executeRent(Player renter, Plot plot) {
        handleRent(renter, plot);
    }

    private void handleRent(Player renter, Plot plot) {
        if (!plugin.cfg().raw().getBoolean("full_plot_renting.enabled", true)) {
            plugin.msg().send(renter, "market-renting-disabled");
            return;
        }
        if (plot.getOwner().equals(renter.getUniqueId())) {
            plugin.msg().send(renter, "market-rent-own");
            return;
        }

        double price = plot.getRentPrice();
        if (!plot.isForRent() || plot.hasActiveRental() || !Double.isFinite(price) || price <= 0.0) {
            plugin.msg().send(renter, "market-listing-unavailable");
            return;
        }

        long activeRentals = plugin.store().getAllPlots().stream()
                .filter(candidate -> candidate != null && candidate.isRentedBy(renter.getUniqueId()))
                .count();
        int maxRentals = Math.max(1, plugin.cfg().raw().getInt("full_plot_renting.max_active_rentals_per_player", 3));
        if (activeRentals >= maxRentals && !renter.hasPermission("aegis.rent.bypass-limit")) {
            plugin.msg().send(renter, "market-rent-limit", Map.of("AMOUNT", String.valueOf(maxRentals)));
            return;
        }

        UUID transactionId = plot.getPlotId();
        if (!activeTransactions.add(transactionId)) {
            plugin.msg().send(renter, "market-transaction-busy");
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
        int defaultDays = Math.max(1, plugin.cfg().raw().getInt("full_plot_renting.duration_days", 7));
        TerritoryLifeService.RentalOffer offer = plugin.territoryLife().getOffer(plot.getPlotId(), price, defaultDays);
        int days = offer.termDays();
        double total = price + offer.deposit();
        boolean renterCharged = false;
        boolean ownerPaid = false;
        try {
            if (!Double.isFinite(total) || total <= 0.0D
                    || !plugin.eco().withdraw(renter, total, CurrencyType.VAULT)) {
                plugin.msg().send(renter, "need_vault", Map.of("AMOUNT", plugin.eco().format(total, CurrencyType.VAULT)));
                return;
            }
            renterCharged = true;

            if (plugin.vault() == null || !plugin.vault().deposit(owner, price)) {
                boolean refunded = plugin.vault() != null && plugin.vault().deposit(renter, total);
                if (!refunded) plugin.territoryLife().addSettlement(renter.getUniqueId(), total,
                        "Refund after failed rental owner payment");
                renterCharged = !refunded;
                plugin.msg().send(renter, "market-payment-failed");
                return;
            }
            ownerPaid = true;

            plot.removeBan(renter.getUniqueId());
            long expires = System.currentTimeMillis() + (days * 86_400_000L);
            plot.setRenter(renter.getUniqueId(), expires);
            plugin.store().savePlotSync(plot);
            plugin.territoryLife().activateContract(plot.getPlotId(), plot.getOwner(), renter.getUniqueId(), offer, expires);
            plugin.territoryLife().log(plot.getPlotId(), renter.getUniqueId(), "RENTAL_STARTED",
                    "Rental started for " + days + " day(s); rent=" + price + ", deposit=" + offer.deposit() + ".");
            plugin.territoryLife().queueNotice(plot.getOwner(), "&aYour plot was rented by &f" + renter.getName()
                    + "&a for &e" + days + " day(s)&a.");

            plugin.msg().send(renter, "market-rent-success", Map.of(
                    "PRICE", plugin.eco().format(price, CurrencyType.VAULT),
                    "DAYS", String.valueOf(days)));
            Player ownerPlayer = owner.getPlayer();
            if (ownerPlayer != null && owner.isOnline()) {
                plugin.runMain(ownerPlayer, () -> plugin.msg().send(ownerPlayer, "market-rented", Map.of(
                        "PRICE", plugin.eco().format(price, CurrencyType.VAULT),
                        "PLAYER", renter.getName(), "DAYS", String.valueOf(days))));
            }
            plugin.effects().playConfirm(renter);
            renter.closeInventory();
        } catch (Throwable transactionError) {
            plugin.getLogger().severe("Plot rental transaction failed for " + transactionId + ": " + transactionError.getMessage());
            if (ownerPaid && plugin.vault() != null && !plugin.vault().charge(owner, price)) {
                plugin.getLogger().severe("Could not reverse owner payment for failed plot rental " + transactionId + ".");
            }
            if (renterCharged && (plugin.vault() == null || !plugin.vault().deposit(renter, total))) {
                plugin.territoryLife().addSettlement(renter.getUniqueId(), total,
                        "Refund after failed rental activation");
            }
            plugin.territoryLife().removeContract(plot.getPlotId());
            plot.clearRenter();
            plugin.store().savePlotSync(plot);
            plugin.msg().send(renter, "market-payment-failed");
        } finally {
            activeTransactions.remove(transactionId);
        }
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
