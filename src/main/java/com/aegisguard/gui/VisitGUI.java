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
import java.util.Comparator;
import java.util.List;

/**
 * VisitGUI
 * - Allows players to warp to plots they are trusted on.
 * - Allows warping to public Server Zones (Warps).
 */
public class VisitGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45;

    public VisitGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class VisitHolder implements InventoryHolder {
        private final int page;
        private final boolean showingWarps; // Toggle state
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
        
        // --- FILTER LOGIC ---
        for (Plot plot : plugin.store().getAllPlots()) {
            if (showWarps) {
                // Show Server Warps
                if (plot.isServerWarp()) {
                    displayPlots.add(plot);
                }
            } else {
                // Show Trusted Plots (Where player is Member/Co-Owner, but NOT Owner)
                if (plot.getPlayerRoles().containsKey(player.getUniqueId()) && !plot.getOwner().equals(player.getUniqueId())) {
                    displayPlots.add(plot);
                }
            }
        }

        // Sort Alphabetically
        displayPlots.sort((p1, p2) -> {
            String n1 = showWarps ? p1.getWarpName() : p1.getOwnerName();
            String n2 = showWarps ? p2.getWarpName() : p2.getOwnerName();
            if (n1 == null) n1 = "Unknown";
            if (n2 == null) n2 = "Unknown";
            return n1.compareToIgnoreCase(n2);
        });

        // Pagination
        int maxPages = (int) Math.ceil((double) displayPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String modeTitle = showWarps ? "§6Server Waypoints" : "§9Trusted Plots";
        String title = GUIManager.safeText(null, modeTitle) + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";

        Inventory inv = Bukkit.createInventory(new VisitHolder(displayPlots, page, showWarps), 54, title);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Populate Items
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int index = startIndex + i;
            if (index >= displayPlots.size()) break;

            Plot plot = displayPlots.get(index);
            ItemStack icon;

            if (showWarps) {
                // Server Warp
                Material mat = plot.getWarpIcon() != null ? plot.getWarpIcon() : Material.BEACON;
                String name = plot.getWarpName() != null ? plot.getWarpName() : "Server Warp";
                icon = GUIManager.createItem(mat, "§6" + name, List.of("§7Click to warp."));
            } else {
                // Trusted Plot
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
                String role = plot.getRole(player.getUniqueId());
                String ownerName = (plot.getOwnerName() != null) ? plot.getOwnerName() : "Unknown";
                String alias = (plot.getEntryTitle() != null) ? "§7Alias: §f" + plot.getEntryTitle() : "";

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(owner);
                    meta.setDisplayName("§e" + ownerName + "'s Dominion");
                    List<String> lore = new ArrayList<>();
                    lore.add("§7World: §f" + plot.getWorld());
                    lore.add("§7Your Status: §a" + role);
                    if (!alias.isEmpty()) lore.add(alias);
                    lore.add(" ");
                    lore.add("§eClick to Teleport");
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                icon = head;
            }
            inv.setItem(i, icon);
        }

        // --- TOGGLE BUTTON (Slot 49) ---
        if (showWarps) {
            inv.setItem(49, GUIManager.createItem(Material.PLAYER_HEAD, "§bSwitch to: Trusted Plots", List.of("§7View plots you are trusted on.")));
        } else {
            inv.setItem(49, GUIManager.createItem(Material.BEACON, "§6Switch to: Server Warps", List.of("§7View official server locations.")));
        }

        // Navigation
        if (page > 0) inv.setItem(45, GUIManager.createItem(Material.ARROW, "§fPrevious Page", null));
        if (page < maxPages - 1) inv.setItem(53, GUIManager.createItem(Material.ARROW, "§fNext Page", null));
        
        // Back
        inv.setItem(48, GUIManager.createItem(Material.NETHER_STAR, "§fBack to Menu", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, VisitHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        boolean warps = holder.isShowingWarps();

        // Nav
        if (slot == 45) { open(player, holder.getPage() - 1, warps); return; }
        if (slot == 53) { open(player, holder.getPage() + 1, warps); return; }
        if (slot == 48) { plugin.gui().openMain(player); return; }

        // Switch Mode
        if (slot == 49) {
            open(player, 0, !warps);
            plugin.effects().playMenuFlip(player);
            return;
        }

        // Teleport
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() != Material.AIR) {
            int index = (holder.getPage() * PLOTS_PER_PAGE) + slot;
            if (index < holder.getPlots().size()) {
                Plot plot = holder.getPlots().get(index);
                
                if (plot.getSpawnLocation() != null) {
                    player.teleport(plot.getSpawnLocation());
                } else {
                    player.teleport(plot.getCenter(plugin));
                }
                
                plugin.msg().send(player, "home-set-success"); // Reusing "Teleport Success" msg
                plugin.effects().playTeleport(player);
                player.closeInventory();
            }
        }
    }
}
