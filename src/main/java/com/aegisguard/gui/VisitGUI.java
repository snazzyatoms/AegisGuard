package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
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
import java.util.Map;

public class VisitGUI {

    private final AegisGuard plugin;
    private final int ESTATES_PER_PAGE = 45;

    public VisitGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class VisitHolder implements InventoryHolder {
        private final int page;
        private final boolean showingWarps; // Toggle state
        private final List<Estate> estates;

        public VisitHolder(List<Estate> estates, int page, boolean showingWarps) {
            this.estates = estates;
            this.page = page;
            this.showingWarps = showingWarps;
        }

        public int getPage() { return page; }
        public boolean isShowingWarps() { return showingWarps; }
        public List<Estate> getEstates() { return estates; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page, boolean showWarps) {
        LanguageManager lang = plugin.getLanguageManager();
        List<Estate> displayList = new ArrayList<>();
        
        // --- FILTER LOGIC ---
        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            if (showWarps) {
                // Show Public Warps (Server Zones or Guild Warps if implemented)
                // Placeholder: Assuming Server Zones or specially flagged estates are warps
                if (estate.getFlag("is_warp")) {
                    displayList.add(estate);
                }
            } else {
                // Show Trusted Estates (Where I am a member but NOT owner)
                if (estate.isMember(player.getUniqueId()) && !estate.getOwnerId().equals(player.getUniqueId())) {
                    displayList.add(estate);
                }
            }
        }

        // Sort Alphabetically
        displayList.sort(Comparator.comparing(Estate::getName, String.CASE_INSENSITIVE_ORDER));

        // Pagination
        int maxPages = (int) Math.ceil((double) displayList.size() / ESTATES_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        // Title
        String modeTitleKey = showWarps ? "visit_title_warps" : "visit_title_trusted";
        String defaultTitle = showWarps ? "§6Server Waypoints" : "§9Trusted Estates";
        
        String modeTitle = lang.getMsg(player, modeTitleKey);
        if (modeTitle.contains("Missing")) modeTitle = defaultTitle;
        
        String title = modeTitle + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";
        Inventory inv = Bukkit.createInventory(new VisitHolder(displayList, page, showWarps), 54, title);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Populate Items
        int startIndex = page * ESTATES_PER_PAGE;
        for (int i = 0; i < ESTATES_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= displayList.size()) break;

            Estate estate = displayList.get(index);
            ItemStack icon;

            if (showWarps) {
                // Warp Icon
                Material mat = Material.BEACON; // Could be customized in Estate object later
                icon = GUIManager.createItem(mat, "§6" + estate.getName(), lang.getMsgList(player, "visit_warp_lore"));
            } else {
                // Trusted Estate Head
                OfflinePlayer owner = Bukkit.getOfflinePlayer(estate.getOwnerId());
                String role = estate.getMemberRole(player.getUniqueId());
                
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    if (!estate.isGuild()) meta.setOwningPlayer(owner);
                    
                    // Localized Name: "Steve's Estate"
                    String nameFormat = lang.getMsg(player, "visit_plot_name").replace("{PLOT}", estate.getName());
                    if (nameFormat.contains("Missing")) nameFormat = "§e" + estate.getName();
                    
                    meta.setDisplayName(nameFormat);
                    
                    List<String> lore = new ArrayList<>(lang.getMsgList(player, "visit_plot_lore"));
                    lore.replaceAll(s -> s.replace("{WORLD}", estate.getWorld().getName())
                                          .replace("{ROLE}", role));
                    
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                icon = head;
            }
            inv.setItem(i, icon);
        }

        // --- TOGGLE BUTTON (Slot 49) ---
        if (showWarps) {
            inv.setItem(49, GUIManager.createItem(Material.PLAYER_HEAD, 
                lang.getMsg(player, "visit_switch_trusted"), 
                lang.getMsgList(player, "visit_switch_trusted_lore")));
        } else {
            inv.setItem(49, GUIManager.createItem(Material.BEACON, 
                lang.getMsg(player, "visit_switch_warps"), 
                lang.getMsgList(player, "visit_switch_warps_lore")));
        }

        // Navigation
        if (page > 0) inv.setItem(45, GUIManager.createItem(Material.ARROW, lang.getGui("button_prev")));
        if (page < maxPages - 1) inv.setItem(53, GUIManager.createItem(Material.ARROW, lang.getGui("button_next")));
        
        // Back
        inv.setItem(48, GUIManager.createItem(Material.NETHER_STAR, lang.getGui("button_back")));

        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, VisitHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        boolean warps = holder.isShowingWarps();

        // Nav
        if (slot == 45) { open(player, holder.getPage() - 1, warps); return; }
        if (slot == 53) { open(player, holder.getPage() + 1, warps); return; }
        if (slot == 48) { plugin.getGuiManager().openGuardianCodex(player); return; }

        // Switch Mode
        if (slot == 49) {
            open(player, 0, !warps);
            // plugin.effects().playMenuFlip(player);
            return;
        }

        // Teleport
        if (slot < ESTATES_PER_PAGE && e.getCurrentItem().getType() != Material.AIR) {
            int index = (holder.getPage() * ESTATES_PER_PAGE) + slot;
            if (index < holder.getEstates().size()) {
                Estate estate = holder.getEstates().get(index);
                
                if (estate.getSpawnLocation() != null) {
                    player.teleport(estate.getSpawnLocation());
                } else {
                    Location center = estate.getCenter();
                    center.setY(center.getWorld().getHighestBlockYAt(center) + 1);
                    player.teleport(center);
                }
                
                player.sendMessage(plugin.getLanguageManager().getMsg(player, "home-set-success")); // Reuse "Teleport Success"
                // plugin.effects().playTeleport(player);
                player.closeInventory();
            }
        }
    }
}
