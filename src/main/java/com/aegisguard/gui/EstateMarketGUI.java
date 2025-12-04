package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.economy.CurrencyType;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
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

/**
 * EstateMarketGUI
 * - A paginated GUI for buying and renting estates.
 * - Fully localized for dynamic language switching.
 */
public class EstateMarketGUI {

    private final AegisGuard plugin;
    private final int ESTATES_PER_PAGE = 45;

    public EstateMarketGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class MarketHolder implements InventoryHolder {
        private final int page;
        private final List<Estate> estates;

        public MarketHolder(List<Estate> estates, int page) {
            this.estates = estates;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Estate> getEstates() { return estates; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // 1. Gather all estates for sale
        List<Estate> marketList = new ArrayList<>();
        for (Estate e : plugin.getEstateManager().getAllEstates()) {
            if (e.isForSale() || e.isForRent()) {
                marketList.add(e);
            }
        }

        // 2. Sort (Cheapest First)
        marketList.sort(Comparator.comparingDouble(Estate::getSalePrice));

        int maxPages = (int) Math.ceil((double) marketList.size() / ESTATES_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = lang.getGui("title_market") + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";
        Inventory inv = Bukkit.createInventory(new MarketHolder(marketList, page), 54, title);

        // 3. Fill Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // 4. Populate Listings
        int startIndex = page * ESTATES_PER_PAGE;
        for (int i = 0; i < ESTATES_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= marketList.size()) break;

            Estate estate = marketList.get(index);
            boolean isRent = estate.isForRent(); 
            OfflinePlayer owner = Bukkit.getOfflinePlayer(estate.getOwnerId());
            
            String priceStr = plugin.getEconomy().format(isRent ? estate.getRentPrice() : estate.getSalePrice(), CurrencyType.VAULT);
            
            String typeStr = isRent ? "§bFor Rent" : "§aFor Sale"; // TODO: Add lang key

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                if (!estate.isGuild()) meta.setOwningPlayer(owner);
                
                meta.setDisplayName(typeStr + ": §e" + priceStr);
                
                List<String> lore = new ArrayList<>();
                lore.add("§7Owner: §f" + (owner.getName() != null ? owner.getName() : "Unknown"));
                lore.add("§7World: §f" + estate.getWorld().getName());
                lore.add("§7Size: §e" + estate.getRegion().getWidth() + "x" + estate.getRegion().getLength());
                if (estate.getDescription() != null) lore.add("§7Note: §f" + estate.getDescription());
                lore.add(" ");
                lore.add("§eLeft-Click to Teleport");
                lore.add("§aRight-Click to Buy/Rent");

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW, lang.getGui("button_prev")));
        }
        
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW, lang.getGui("button_next")));
        }

        player.openInventory(inv);
    }

    public void handleClick(Player player, InventoryClickEvent e, MarketHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int page = holder.getPage();

        // Nav
        if (slot == 45) { open(player, page - 1); return; }
        if (slot == 53) { open(player, page + 1); return; }
        if (slot == 49) { player.closeInventory(); return; }

        // Listing Click
        if (slot < ESTATES_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (page * ESTATES_PER_PAGE) + slot;
            if (index >= holder.getEstates().size()) return;
            
            Estate estate = holder.getEstates().get(index);
            if (estate == null) return;

            // Teleport (Left Click)
            if (e.isLeftClick()) {
                Location center = estate.getCenter();
                if (center != null) {
                    center.setY(center.getWorld().getHighestBlockYAt(center) + 1);
                    player.teleport(center);
                    player.sendMessage("§aTeleported to estate.");
                    player.closeInventory();
                }
            } 
            // Buy/Rent (Right Click)
            else if (e.isRightClick()) {
                if (estate.isForSale()) {
                    handleBuy(player, estate);
                } else if (estate.isForRent()) {
                    player.sendMessage("§eRent system coming in future update.");
                }
            }
        }
    }

    private void handleBuy(Player buyer, Estate estate) {
        // 1. Validation
        if (estate.getOwnerId().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§cYou cannot buy your own estate.");
            return;
        }
        
        int max = plugin.getConfig().getInt("estates.max_estates_per_player", 3);
        int current = plugin.getEstateManager().getEstates(buyer.getUniqueId()).size();
        
        if (current >= max && !plugin.isAdmin(buyer)) {
            buyer.sendMessage("§cYou have reached the maximum estate limit.");
            return;
        }

        // 2. Transaction
        double price = estate.getSalePrice();
        if (!plugin.getEconomy().withdraw(buyer, price)) {
            buyer.sendMessage("§cInsufficient Funds. Cost: " + price);
            return;
        }

        // 3. Pay Seller
        OfflinePlayer seller = Bukkit.getOfflinePlayer(estate.getOwnerId());
        if (seller.hasPlayedBefore()) {
            plugin.getEconomy().deposit(seller.getPlayer(), price); 
        }

        // 4. Transfer Ownership
        plugin.getEstateManager().transferOwnership(estate, buyer.getUniqueId(), false); // false = Private
        estate.setForSale(false, 0);
        // plugin.getEstateManager().saveEstate(estate);

        // 5. Notify
        buyer.sendMessage("§aPurchase Successful!");
        GUIManager.playSuccess(buyer);
        
        if (seller.isOnline()) {
            seller.getPlayer().sendMessage("§aYour estate '" + estate.getName() + "' was sold for $" + price);
        }
        
        buyer.closeInventory();
    }
}
