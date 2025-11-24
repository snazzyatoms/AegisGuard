package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
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
import java.util.List;

public class VisitGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45;

    public VisitGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Holder to identify this specific GUI and store state (Page + Mode).
     */
    public static class VisitHolder implements InventoryHolder {
        private final int page;
        private final boolean showingWarps; // True = Server Warps, False = Friend Plots
        private final List<Plot> plots;

        public VisitHolder(List<Plot> plots, int page, boolean showingWarps) {
            this.plots = plots;
            this.page = page;
            this.showingWarps = showingWarps;
        }

        public int getPage() { return page; }
        public boolean isShowingWarps() { return showingWarps; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page, boolean showWarps) {
        List<Plot> displayPlots = new ArrayList<>();
        
        // Filter Logic
        for (Plot plot : plugin.store().getAllPlots()) {
            if (showWarps) {
                // Show only Server Warps (Spawn, Market, etc.)
                if (plot.isServerWarp()) {
                    displayPlots.add(plot);
                }
            } else {
                // Show Trusted Plots (Friends)
                // Logic: Player has a role AND is NOT the owner
                if (plot.getPlayerRoles().containsKey(player.getUniqueId()) && !plot.getOwner().equals(player.getUniqueId())) {
                    displayPlots.add(plot);
                }
            }
        }

        // Sort alphabetically
        displayPlots.sort((p1, p2) -> {
            String n1 = showWarps ? p1.getWarpName() : p1.getOwnerName();
            String n2 = showWarps ? p2.getWarpName() : p2.getOwnerName();
            // Handle nulls safely
            if (n1 == null) n1 = "Unknown";
            if (n2 == null) n2 = "Unknown";
            return n1.compareToIgnoreCase(n2);
        });

        int maxPages = (int) Math.ceil((double) displayPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String modeTitle = showWarps ? "§6Server Waypoints" : "§9Trusted Plots";
        String title = GUIManager.safeText(null, modeTitle) + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";

        Inventory inv = Bukkit.createInventory(new VisitHolder(displayPlots, page, showWarps), 54, title);

        // Background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // Populate Items
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= displayPlots.size()) break;

            Plot plot = displayPlots.get(index);
            ItemStack icon;

            if (showWarps) {
                // Server Warp Icon
                Material mat = plot.getWarpIcon();
                if (mat == null) mat = Material.BEACON;
                String name = plot.getWarpName();
                if (name == null) name = "Server Warp";
                
                icon = GUIManager.icon(mat, "§6" + name, List.of("§7Click to warp."));
            } else {
                // Player Head
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
                String role = plot.getRole(player.getUniqueId());
                
                icon = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) icon.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(owner);
                    String ownerName = plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown";
                    meta.setDisplayName("§e" + ownerName + "'s Dominion");
                    meta.setLore(List.of(
                        "§7World: §f" + plot.getWorld(),
                        "§7Your Status: §a" + role,
                        " ",
                        "§eClick to Teleport"
                    ));
                    icon.setItemMeta(meta);
                }
            }
            inv.setItem(i, icon);
        }

        // --- TOGGLE BUTTON (Slot 49 Center) ---
        if (showWarps) {
            inv.setItem(49, GUIManager.icon(Material.PLAYER_HEAD, "§bSwitch to: Trusted Plots", List.of("§7View plots you are trusted on.")));
        } else {
            inv.setItem(49, GUIManager.icon(Material.BEACON, "§6Switch to: Server Warps", List.of("§7View official server locations.")));
        }

        // Navigation
        if (page > 0) inv.setItem(45, GUIManager.icon(Material.ARROW, "§fPrevious Page", null));
        
        // Back Button (Slot 48)
        inv.setItem(48, GUIManager.icon(Material.NETHER_STAR, "§fBack to Menu", null));
        
        if (page < maxPages - 1) inv.setItem(53, GUIManager.icon(Material.ARROW, "§fNext Page", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, VisitHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        boolean warps = holder.isShowingWarps();

        // Page Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { 
            open(player, holder.getPage() - 1, warps); 
            plugin.effects().playMenuFlip(player); 
            return; 
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { 
            open(player, holder.getPage() + 1, warps); 
            plugin.effects().playMenuFlip(player); 
            return; 
        }
        
        // Back to Menu
        if (slot == 48) { 
            plugin.gui().openMain(player); 
            plugin.effects().playMenuFlip(player); 
            return; 
        }

        // Switch Mode (Warp vs Friends)
        if (slot == 49) {
            open(player, 0, !warps); // Flip the boolean
            plugin.effects().playMenuFlip(player);
            return;
        }

        // Teleport Logic (Clicking a Plot/Warp)
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() != Material.AIR && e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
            int index = (holder.getPage() * PLOTS_PER_PAGE) + slot;
            if (index < holder.getPlots().size()) {
                Plot plot = holder.getPlots().get(index);
                
                if (plot.getSpawnLocation() != null) {
                    player.teleport(plot.getSpawnLocation());
                } else {
                    // Fallback to center if no spawn set
                    player.teleport(plot.getCenter(plugin));
                }
                
                plugin.msg().send(player, "home-set-success"); // "Teleport successful" message
                plugin.effects().playConfirm(player);
                player.closeInventory();
            }
        }
    }
}
