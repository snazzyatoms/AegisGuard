package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot; // --- FIX: Use the standalone Plot class ---
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
 * - This is a complete, multi-stage GUI for managing plot roles.
 * - Features: Plot Selector, Main Role List, Add Player, Manage Player Role.
 */
public class RolesGUI {

    private final AegisGuard plugin;

    public RolesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------
     * Inventory Holders (Must be PUBLIC)
     * ----------------------------- */
    
    // Holder for the Plot Selector GUI
    public static class PlotSelectorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    // Holder for the Main Roles GUI (shows player heads)
    public static class RolesMenuHolder implements InventoryHolder {
        private final Plot plot;
        public RolesMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    // Holder for the "Add Player" GUI (shows online players)
    public static class RoleAddHolder implements InventoryHolder {
        private final Plot plot;
        public RoleAddHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }
    
    // Holder for the "Manage Player" GUI (shows roles to assign)
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
        List<Plot> plots = plugin.store().getPlots(owner.getUniqueId());
        if (plots == null || plots.isEmpty()) {
            plugin.msg().send(owner, "no_plot_here");
            return;
        }

        // If the player has more than one plot, ask them which one to manage.
        if (plots.size() > 1) {
            openPlotSelector(owner, plots);
        } else {
            // Otherwise, open the menu for their only plot.
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

        // Fill background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        
        // Show players with roles
        int slot = 0;
        for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
            if (slot >= 45) break;
            if (entry.getKey().equals(owner.getUniqueId())) continue; // Don't show the owner

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

        // SLOT 45: Add Player
        inv.setItem(45, GUIManager.icon(
                Material.EMERALD,
                GUIManager.safeText(plugin.msg().get(owner, "button_add_trusted"), "§aAdd Player"),
                plugin.msg().getList(owner, "add_trusted_lore")));

        // SLOT 52: Back
        inv.setItem(52, GUIManager.icon(
                Material.ARROW,
                GUIManager.safeText(plugin.msg().get(owner, "button_back"), "§fBack"),
                plugin.msg().getList(owner, "back_lore")));

        // SLOT 53: Exit
        inv.setItem(53, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(owner, "button_exit"), "§cExit"),
                plugin.msg().getList(owner, "exit_lore")));

        owner.openInventory(inv);
        plugin.effects().playMenuOpen(owner);
    }

    /* -----------------------------
     * GUI: Add Player
     * ----------------------------- */
    private void openAddMenu(Player player, Plot plot) {
        String addTitle = GUIManager.safeText(plugin.msg().get(player, "add_trusted_title"), "§bAdd Player to Plot");
        Inventory addMenu = Bukkit.createInventory(new RoleAddHolder(plot), 54, addTitle);

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 54) break;
            // Don't show owner or players already on the plot
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (plot.getPlayerRoles().containsKey(online.getUniqueId())) continue; 

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName("§e" + online.getName());
                meta.setLore(plugin.msg().getList(player, "add_trusted_lore"));
                head.setItemMeta(meta);
            }
            addMenu.setItem(slot++, head);
        }
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

        List<String> roleNames = plugin.cfg().getRoleNames(); // "co-owner", "member", "guest"
        
        // Slot for each role
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

        // "Remove" button
        inv.setItem(26, GUIManager.icon(
                Material.REDSTONE_BLOCK,
                GUIManager.safeText(plugin.msg().get(player, "button_remove_trusted"), "§cRemove From Plot"),
                List.of("§7Remove this player's role entirely.")
        ));
        
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }


    /* -----------------------------
     * Click Handlers (Called by GUIListener)
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

        // Handle player head clicks (Manage)
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

        // Handle navigation clicks
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
            default: // Ignore filler
                break;
        }
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, RoleAddHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;

        Plot plot = holder.getPlot();
        if (plot == null) { player.closeInventory(); return; }

        SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
        if (meta != null) {
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null) {
                // Open the "Manage" menu to force the owner to select a role
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

        // Sanity check
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.msg().send(player, "role_self");
            plugin.effects().playError(player);
            return;
        }
        if (target.getUniqueId().equals(plot.getOwner())) {
             plugin.msg().send(player, "role_is_owner");
             plugin.effects().playError(player);
             return;
        }

        int slot = e.getSlot();
        
        if (slot == 26) { // Remove Role
            plugin.store().removePlayerRole(plot, target.getUniqueId());
            plugin.msg().send(player, "role_removed", Map.of("PLAYER", target.getName()));
            plugin.effects().playUnclaim(player);
            openRolesMenu(player, plot); // Go back to main menu
            return;
        }

        List<String> roleNames = plugin.cfg().getRoleNames();
        if (slot >= 0 && slot < roleNames.size()) {
            String newRole = roleNames.get(slot);
            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", target.getName(), "ROLE", newRole));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot); // Go back to main menu
        }
    }
}
