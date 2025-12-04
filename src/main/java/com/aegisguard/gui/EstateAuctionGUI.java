package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
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
 * EstateAuctionGUI
 * - Allows players to bid on expired/seized estates.
 * - Updated for v1.3.0 Estate System.
 */
public class EstateAuctionGUI {

    private final AegisGuard plugin;
    private final int ESTATES_PER_PAGE = 45;

    public EstateAuctionGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class AuctionHolder implements InventoryHolder {
        private final int page;
        private final List<Estate> estates;

        public AuctionHolder(List<Estate> estates, int page) {
            this.estates = estates;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Estate> getEstates() { return estates; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Fetch auctions from EstateManager (Assuming you implemented getAuctions())
        // For now, filter manually:
        List<Estate> allAuctions = new ArrayList<>();
        for (Estate e : plugin.getEstateManager().getAllEstates()) {
            // Placeholder check: In v1.3.0 we might check a flag or specific status
            if (e.getFlag("is_auction")) allAuctions.add(e); 
        }
        
        // Sort by price (Cheapest first?)
        // allAuctions.sort(Comparator.comparingDouble(Estate::getCurrentBid)); 

        int maxPages = (int) Math.ceil((double) allAuctions.size() / ESTATES_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = lang.getGui("title_auction") + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";
        Inventory inv = Bukkit.createInventory(new AuctionHolder(allAuctions, page), 54, title);

        // Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Listings
        int startIndex = page * ESTATES_PER_PAGE;
        for (int i = 0; i < ESTATES_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= allAuctions.size()) break;

            Estate estate = allAuctions.get(index);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(estate.getOwnerId());
            
            // Assuming Auction Data is stored in the Estate object now (add to Estate.java if missing)
            // double currentBid = estate.getCurrentBid();
            // String bidderName = ...

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                if (!estate.isGuild()) meta.setOwningPlayer(owner);
                
                meta.setDisplayName("§e" + estate.getName());
                
                List<String> lore = new ArrayList<>();
                lore.add("§7World: §f" + estate.getWorld().getName());
                lore.add("§7Size: §a" + estate.getRegion().getWidth() + "x" + estate.getRegion().getLength());
                lore.add(" ");
                // lore.add("§7Current Bid: §6$" + currentBid);
                lore.add(" ");
                lore.add("§aLeft-Click to Bid");
                
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

    public void handleClick(Player player, InventoryClickEvent e, AuctionHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45) { open(player, currentPage - 1); return; }
        if (slot == 53) { open(player, currentPage + 1); return; }

        if (slot < ESTATES_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (currentPage * ESTATES_PER_PAGE) + slot;
            if (index >= holder.getEstates().size()) return;

            Estate estate = holder.getEstates().get(index);
            
            if (e.isLeftClick()) {
                bidOnEstate(player, estate);
                open(player, currentPage); // Refresh
            }
        }
    }

    private void bidOnEstate(Player bidder, Estate estate) {
        if (estate.getOwnerId().equals(bidder.getUniqueId())) {
            bidder.sendMessage("§cYou cannot bid on your own estate.");
            return;
        }
        
        // Logic: Withdraw Money -> Update Bid -> Notify
        // double currentBid = estate.getCurrentBid();
        // double newBid = currentBid + 100.0; // Increment
        
        // if (plugin.getEconomy().withdraw(bidder, newBid)) {
        //     estate.setCurrentBid(newBid);
        //     estate.setBidder(bidder.getUniqueId());
        //     bidder.sendMessage("§aBid Placed: $" + newBid);
        // }
    }
}
