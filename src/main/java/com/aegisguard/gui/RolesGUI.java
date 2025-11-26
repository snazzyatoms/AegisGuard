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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RolesGUI
 * - Replaces the old TrustedGUI.
 * - Features: Plot Selector, Main Role List, Add Player, Manage Player Role.
 * - UPDATED: Admins can now manage roles on any plot (including Server Plots).
 */
public class RolesGUI {

    private final AegisGuard plugin;

    public RolesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------
     * Inventory Holders (Must be PUBLIC)
     * ----------------------------- */
    
    public static class PlotSelectorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public static class RolesMenuHolder implements InventoryHolder {
        private final Plot plot;
        public RolesMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleAddHolder implements InventoryHolder {
        private final Plot plot;
        public RoleAddHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }
    
    public static class RoleManageHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        public RoleManageHolder(Plot plot, OfflinePlayer target) { 
            this.plot = plot; 
            this.target = target;
        }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        @Override public Inventory getInventory() { return null; }
    }


    /* -----------------------------
     * Main Entry Point
     * ----------------------------- */
    public void open(Player owner) {
        // 1. Check if admin is standing in a plot they want to manage
        if (plugin.isAdmin(owner)) {
            Plot currentLocPlot = plugin.store().getPlotAt(owner.getLocation());
            if (currentLocPlot != null) {
                // If standing in a plot (Server or Player), open roles for THAT plot directly
                openRolesMenu(owner, currentLocPlot);
                return;
            }
        }

        // 2. Normal Flow: Show owned plots
        List<Plot> plots = plugin.store().getPlots(owner.getUniqueId());
        if (plots == null || plots.isEmpty()) {
            plugin.msg().send(owner, "no_plot_here");
            return;
        }

        if (plots.size() > 1) {
            openPlotSelector(owner, plots);
        } else {
            openRolesMenu(owner, plots.get(0));
        }
    }

    /* -----------------------------
     * GUI: Plot Selector
     * ----------------------------- */
    private void openPlotSelector(Player owner, List<Plot> plots) {
        String title = GUIManager.safeText(plugin.msg().get(owner, "trusted_plot_selector_title"), "§bSelect a Plot to Manage");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(), 54, title);

        for (int i = 0; i < plots.size(); i++) {
            if (i >= 54) break;
            Plot plot = plots.get(i);
            inv.setItem(i, GUIManager.icon(
                    Material.GRASS_BLOCK,
                    "§aPlot #" + (i + 1),
                    List.of(
                            "§7World: §f" + plot.getWorld(),
                            "§7Bounds: §e(" + plot.getX1() + ", " + plot.getZ1() + ")",
                            "§7Click to manage roles."
                    )
            ));
        }
        owner.openInventory(inv);
        plugin.effects().playMenuOpen(owner);
    }

    /* -----------------------------
     * GUI: Main Roles Menu
     * ----------------------------- */
    public void openRolesMenu(Player owner, Plot plot) {
        String title = GUIManager.safeText(plugin.msg().get(owner, "roles_gui_title"), "§b🛡 Plot Roles");
        Inventory inv = Bukkit.createInventory(new RolesMenuHolder(plot), 54, title);

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        
        int slot = 0;
        for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
            if (slot >= 45) break;
            // Don't show self in list unless admin view
            if (entry.getKey().equals(owner.getUniqueId()) && !plugin.isAdmin(owner)) continue; 

            OfflinePlayer member = Bukkit.getOfflinePlayer(entry.getKey());
            String roleName = entry.getValue();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);
                String playerName = member.getName() != null ? member.getName() : "Unknown";
                meta.setDisplayName("§a" + playerName);
                meta.setLore(List.of(
                        "§7Role: §e" + roleName,
                        " ",
                        "§7Click to manage this player."
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        inv.setItem(45, GUIManager.icon(
                Material.EMERALD,
                GUIManager.safeText(plugin.msg().get(owner, "button_add_trusted"), "§aAdd Nearby Player"),
                plugin.msg().getList(owner, "add_trusted_lore")));

        inv.setItem(52, GUIManager.icon(
                Material.ARROW,
                GUIManager.safeText(plugin.msg().get(owner, "button_back"), "§fBack"),
                plugin.msg().getList(owner, "back_lore")));

        inv.setItem(53, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(owner, "button_exit"), "§cExit"),
                plugin.msg().getList(owner, "exit_lore")));

        owner.openInventory(inv);
        plugin.effects().playMenuOpen(owner);
    }

    /* -----------------------------
     * GUI: Add Player (VICINITY CHECK ADDED)
     * ----------------------------- */
    private void openAddMenu(Player player, Plot plot) {
        String addTitle = GUIManager.safeText(plugin.msg().get(player, "add_trusted_title"), "§bAdd Nearby Player");
        Inventory addMenu = Bukkit.createInventory(new RoleAddHolder(plot), 54, addTitle);

        int slot = 0;
        double range = 50.0; // RADIUS: Players must be within 50 blocks

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 54) break;
            // Don't show self
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            // Don't show players already added
            if (plot.getPlayerRoles().containsKey(online.getUniqueId())) continue; 

            // --- VICINITY CHECK ---
            // 1. Must be in same world
            if (!online.getWorld().equals(player.getWorld())) continue;
            // 2. Must be close
            if (online.getLocation().distance(player.getLocation()) > range) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName("§e" + online.getName());
                meta.setLore(List.of("§7Click to trust this player."));
                head.setItemMeta(meta);
            }
            addMenu.setItem(slot++, head);
        }
        
        if (slot == 0) {
            addMenu.setItem(22, GUIManager.icon(Material.BARRIER, "§cNo Players Nearby", List.of("§7Ask your friend to stand closer!")));
        }

        // Navigation
        addMenu.setItem(49, GUIManager.icon(Material.ARROW, "§fBack", null));

        player.openInventory(addMenu);
        plugin.effects().playMenuFlip(player);
    }
    
    /* -----------------------------
     * GUI: Manage Specific Player
     * ----------------------------- */
    private void openManageMenu(Player player, Plot plot, OfflinePlayer target) {
        String currentRole = plot.getRole(target.getUniqueId());
        String title = GUIManager.safeText(plugin.msg().get(player, "roles_manage_title"), "§bManage: {PLAYER}")
                .replace("{PLAYER}", target.getName());
        
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(plot, target), 27, title);

        List<String> roleNames = plugin.cfg().getRoleNames(); 
        
        for (int i = 0; i < roleNames.size(); i++) {
            if (i >= 27) break;
            String roleName = roleNames.get(i);
            boolean isCurrent = roleName.equalsIgnoreCase(currentRole);
            
            inv.setItem(i, GUIManager.icon(
                    isCurrent ? Material.EMERALD_BLOCK : Material.NAME_TAG,
                    (isCurrent ? "§a" : "§e") + roleName,
                    List.of(
                            "§7Click to set this player's role.",
                            isCurrent ? "§a(Currently Selected)" : "§7(Click to select)"
                    )
            ));
        }

        inv.setItem(26, GUIManager.icon(
                Material.REDSTONE_BLOCK,
                GUIManager.safeText(plugin.msg().get(player, "button_remove_trusted"), "§cRemove From Plot"),
                List.of("§7Remove this player's role entirely.")
        ));
        
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }


    /* -----------------------------
     * Click Handlers
     * ----------------------------- */

    public void handlePlotSelectorClick(Player player, InventoryClickEvent e, PlotSelectorHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        int slot = e.getSlot();

        if (slot >= 0 && slot < plots.size()) {
            Plot selectedPlot = plots.get(slot);
            openRolesMenu(player, selectedPlot);
            plugin.effects().playMenuFlip(player);
        }
    }

    public void handleRolesMenuClick(Player player, InventoryClickEvent e, RolesMenuHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) { player.closeInventory(); return; }

        int slot = e.getSlot();

        if (slot < 45 && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null) {
                OfflinePlayer target = meta.getOwningPlayer();
                if (target != null) {
                    openManageMenu(player, plot, target);
                    plugin.effects().playMenuFlip(player);
                }
            }
            return;
        }

        switch (slot) {
            case 45: // Add Player
                openAddMenu(player, plot);
                plugin.effects().playMenuFlip(player);
                break;
            case 52: // Back
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;
            case 53: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
            default: 
                break;
        }
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, RoleAddHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // Handle Back button inside Add Menu
        if (e.getSlot() == 49 && e.getCurrentItem().getType() == Material.ARROW) {
             openRolesMenu(player, holder.getPlot());
             plugin.effects().playMenuFlip(player);
             return;
        }

        if (e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;

        Plot plot = holder.getPlot();
        if (plot == null) { player.closeInventory(); return; }

        SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
        if (meta != null) {
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null) {
                openManageMenu(player, plot, target);
                plugin.effects().playMenuFlip(player);
            }
        }
    }

    public void handleManageRoleClick(Player player, InventoryClickEvent e, RoleManageHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        if (plot == null || target == null) { player.closeInventory(); return; }

        // Check permission (Owner OR Admin)
        if (!plot.getOwner().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        int slot = e.getSlot();
        
        if (slot == 26) { // Remove Role
            plugin.store().removePlayerRole(plot, target.getUniqueId());
            plugin.msg().send(player, "role_removed", Map.of("PLAYER", target.getName()));
            plugin.effects().playUnclaim(player);
            openRolesMenu(player, plot); 
            return;
        }

        List<String> roleNames = plugin.cfg().getRoleNames();
        if (slot >= 0 && slot < roleNames.size()) {
            String newRole = roleNames.get(slot);
            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", target.getName(), "ROLE", newRole));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot); 
        }
    }
}
