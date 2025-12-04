package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
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

public class AdminPlotListGUI {

    private final AegisGuard plugin;
    private final int ESTATES_PER_PAGE = 45;

    public AdminPlotListGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class EstateListHolder implements InventoryHolder {
        private final int page;
        private final List<Estate> estates;

        public EstateListHolder(List<Estate> estates, int page) {
            this.estates = estates;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Estate> getEstates() { return estates; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Fetch all estates
        List<Estate> allEstates = new ArrayList<>(plugin.getEstateManager().getAllEstates());
        // Sort by Name (A-Z)
        allEstates.sort(Comparator.comparing(Estate::getName, String.CASE_INSENSITIVE_ORDER));

        int maxPages = (int) Math.ceil((double) allEstates.size() / ESTATES_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= maxPages && maxPages > 0) {
             page = maxPages - 1;
        } else if (maxPages == 0) {
             page = 0;
        }

        String title = lang.getGui("title_admin_estates") + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";
        Inventory inv = Bukkit.createInventory(new EstateListHolder(allEstates, page), 54, title);

        // Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int startIndex = page * ESTATES_PER_PAGE;
        for (int i = 0; i < ESTATES_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= allEstates.size()) break;

            Estate estate = allEstates.get(index);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(estate.getOwnerId());

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                if (!estate.isGuild()) meta.setOwningPlayer(owner); // Only show skin for players
                
                String displayName = "§b" + estate.getName();
                meta.setDisplayName(displayName);
                
                List<String> lore = new ArrayList<>();
                lore.add("§7Owner: §f" + (owner.getName() != null ? owner.getName() : "Unknown"));
                lore.add("§7World: §f" + estate.getWorld().getName());
                lore.add("§7Size: §a" + estate.getRegion().getWidth() + "x" + estate.getRegion().getLength());
                lore.add(" ");
                lore.add("§eLeft-Click to Teleport");
                lore.add("§cRight-Click to Delete");

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

    public void handleClick(Player player, InventoryClickEvent e, EstateListHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage - 1); return; }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage + 1); return; }
        if (slot == 49) { player.closeInventory(); return; }

        // Item Click
        if (slot < ESTATES_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (currentPage * ESTATES_PER_PAGE) + slot;
            if (index >= holder.getEstates().size()) return;

            Estate estate = holder.getEstates().get(index);
            
            if (e.isLeftClick()) {
                // Teleport
                Location center = estate.getCenter();
                if (center != null) {
                    center.setY(center.getWorld().getHighestBlockYAt(center) + 1);
                    player.teleport(center);
                    player.sendMessage("§aTeleported to " + estate.getName());
                    player.closeInventory();
                }
            } else if (e.isRightClick()) {
                // Delete
                plugin.getEstateManager().deleteEstate(estate.getId());
                player.sendMessage("§cDeleted Estate: " + estate.getName());
                GUIManager.playSuccess(player); // "Ding" sound
                open(player, currentPage); // Refresh
            }
        }
    }
}
