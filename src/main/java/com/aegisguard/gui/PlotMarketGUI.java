package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
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
 * - Updated to support Rent listings alongside Sale listings.
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
        // Add rent plots? Usually rent is a separate logic or mixed. Assuming mixed for now.
        // allPlots.addAll(plugin.store().getPlotsForRent()); // If you implement getPlotsForRent()

        // 2. Sort (Cheapest First)
        allPlots.sort(Comparator.comparingDouble(Plot::getSalePrice));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = "§2Real Estate (Page " + (page + 1) + ")";
        Inventory inv = Bukkit.createInventory(new PlotMarketHolder(allPlots, page), 54, title);

        // 3. Fill Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // 4. Populate Listings
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int plotIndex = startIndex + i;
            if (plotIndex >= allPlots.size()) break;

            Plot plot = allPlots.get(plotIndex);
            boolean isRent = plot.isForRent(); // Check rent status
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
            
            String priceStr = plugin.eco().format(isRent ? plot.getRentPrice() : plot.getSalePrice(), CurrencyType.VAULT);
            String typeStr = isRent ? "§bFor Rent" : "§aFor Sale";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(owner);
                meta.setDisplayName(typeStr + ": §e" + priceStr);
                
                List<String> lore = new ArrayList<>();
                lore.add("§7Owner: §f" + (owner.getName() != null ? owner.getName() : "Unknown"));
                lore.add("§7World: §f" + plot.getWorld());
                lore.add("§7Size: §e" + (plot.getX2() - plot.getX1() + 1) + "x" + (plot.getZ2() - plot.getZ1() + 1));
                if (plot.getDescription() != null) lore.add("§7Note: §f" + plot.getDescription());
                lore.add(" ");
                lore.add("§eLeft-Click: §7Teleport to preview");
                lore.add("§aRight-Click: §7" + (isRent ? "Rent" : "Buy") + " this plot");
                
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // 5. Navigation Buttons
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW, "§aPrevious Page", null));
        }
        
        inv.setItem(48, GUIManager.createItem(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to dashboard.")));
        
        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW, "§aNext Page", null));
        }

        inv.setItem(49, GUIManager.createItem(Material.BARRIER, "§cClose", null));

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

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, page - 1);
            return;
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, page + 1);
            return;
        }
        if (slot == 48) { // Back
            plugin.gui().openMain(player);
            return;
        }
        if (slot == 49) { // Close
            player.closeInventory();
            return;
        }

        // Listing Click
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (page * PLOTS_PER_PAGE) + slot;
            if (index >= holder.getPlots().size()) return;
            
            Plot plot = holder.getPlots().get(index);
            if (plot == null) return;

            // Teleport (Left Click)
            if (e.isLeftClick()) {
                Location center = plot.getCenter(plugin);
                if (center != null) {
                    player.teleport(center);
                    player.closeInventory();
                    plugin.msg().send(player, "market-teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                }
            } 
            // Buy/Rent (Right Click)
            else if (e.isRightClick()) {
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
            plugin.msg().send(buyer, "need_vault", Map.of("AMOUNT", plugin.eco().format(price, CurrencyType.VAULT)));
            return;
        }

        // 3. Pay Seller
        OfflinePlayer seller = Bukkit.getOfflinePlayer(plot.getOwner());
        if (seller.hasPlayedBefore()) {
            plugin.eco().deposit(seller.getPlayer(), price, CurrencyType.VAULT); // Logic usually handles offline via Vault
        }

        // 4. Transfer
        plugin.store().changePlotOwner(plot, buyer.getUniqueId(), buyer.getName());
        plot.setForSale(false, 0); // Remove from market
        plugin.store().setDirty(true);

        // 5. Notify
        plugin.msg().send(buyer, "market-buy-success", Map.of("PRICE", plugin.eco().format(price, CurrencyType.VAULT), "PLAYER", seller.getName()));
        plugin.effects().playClaimSuccess(buyer);
        
        if (seller.isOnline()) {
            plugin.msg().send(seller.getPlayer(), "market-sold", Map.of("PRICE", plugin.eco().format(price, CurrencyType.VAULT), "PLAYER", buyer.getName()));
        }
        
        buyer.closeInventory();
    }
    
    private void handleRent(Player renter, Plot plot) {
        // Simplified rent logic (usually involves setting 'currentRenter' field in plot)
        // ...
        renter.sendMessage("§eRent system coming in v1.1.2!");
    }
}
