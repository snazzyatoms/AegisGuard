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
import java.util.Map;
import java.util.UUID;

/**
 * RolesGUI
 * - Manage trusted players and their permission levels.
 * - Fully localized: Supports instant language switching.
 */
public class RolesGUI {

    private final AegisGuard plugin;

    public RolesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // --- HOLDERS ---
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

    // --- ENTRY POINT ---
    public void open(Player player) {
        // 1. Admin Override
        if (plugin.isAdmin(player)) {
            Plot standingPlot = plugin.store().getPlotAt(player.getLocation());
            if (standingPlot != null) {
                openRolesMenu(player, standingPlot);
                return;
            }
        }

        // 2. Normal User Flow
        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        
        if (plots == null || plots.isEmpty()) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }

        if (plots.size() > 1) {
            openPlotSelector(player, plots);
        } else {
            openRolesMenu(player, plots.get(0));
        }
    }

    // --- GUI 1: SELECT PLOT ---
    private void openPlotSelector(Player player, List<Plot> plots) {
        String title = GUIManager.safeText(plugin.msg().get(player, "trusted_plot_selector_title"), "§8Select Plot");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(), 54, title);

        int slot = 0;
        for (Plot plot : plots) {
            if (slot >= 54) break;
            
            List<String> lore = new ArrayList<>();
            lore.add("§7World: §f" + plot.getWorld());
            lore.add("§7Size: §e" + (plot.getX2() - plot.getX1()) + "x" + (plot.getZ2() - plot.getZ1()));
            lore.add(" ");
            lore.add("§eClick to Manage Roles");

            inv.setItem(slot++, GUIManager.createItem(
                Material.GRASS_BLOCK,
                "§aPlot #" + (slot), 
                lore
            ));
        }
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --- GUI 2: ROLES LIST ---
    public void openRolesMenu(Player player, Plot plot) {
        String title = GUIManager.safeText(plugin.msg().get(player, "roles_gui_title"), "§8Manage Roles");
        Inventory inv = Bukkit.createInventory(new RolesMenuHolder(plot), 54, title);

        // Footer filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
            if (slot >= 45) break;
            
            UUID uuid = entry.getKey();
            String role = entry.getValue();
            
            boolean isOwnerEntry = uuid.equals(plot.getOwner());
            boolean isViewerOwner = uuid.equals(player.getUniqueId());
            boolean isAdmin = plugin.isAdmin(player);

            // 1) Never show owner's own entry to themselves (even if admin)
            if (isOwnerEntry && isViewerOwner) continue;
            // 2) Only admins can see the owner entry on someone else's plot
            if (isOwnerEntry && !isAdmin) continue;

            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = (member.getName() != null) ? member.getName() : "Unknown";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);
                meta.setDisplayName("§e" + name);
                List<String> lore = new ArrayList<>();
                lore.add("§7Role: §f" + role);
                lore.add(" ");
                lore.add("§eClick to Edit Role");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // Add Button
        inv.setItem(49, GUIManager.createItem(Material.EMERALD, 
            plugin.msg().get(player, "button_add_trusted"), 
            plugin.msg().getList(player, "add_trusted_lore")));

        // Back
        inv.setItem(48, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")));

        // Exit
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, 
            plugin.msg().get(player, "button_exit"), 
            plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --- GUI 3: ADD PLAYER ---
    private void openAddMenu(Player player, Plot plot) {
        String title = GUIManager.safeText(plugin.msg().get(player, "add_trusted_title"), "§8Add Trusted Player");
        Inventory inv = Bukkit.createInventory(new RoleAddHolder(plot), 54, title);

        int slot = 0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (slot >= 54) break;
            if (nearby.getLocation().distance(player.getLocation()) > 50) continue;
            if (nearby.equals(player)) continue; 
            if (plot.getPlayerRoles().containsKey(nearby.getUniqueId())) continue; 

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(nearby);
                meta.setDisplayName("§a" + nearby.getName());
                meta.setLore(List.of("§7Click to add to plot."));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (slot == 0) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER, "§cNo Players Nearby", 
                List.of("§7Ask your friend to stand closer!")));
        }

        inv.setItem(49, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")));
            
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --- GUI 4: MANAGE SPECIFIC PLAYER ---
    private void openManageMenu(Player player, Plot plot, OfflinePlayer target) {
        String title = GUIManager.safeText(plugin.msg().get(player, "roles_manage_title", Map.of("PLAYER", target.getName())), "§8Manage: " + target.getName());
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(plot, target), 27, title);

        List<String> roles = plugin.cfg().getRoleNames();
        String currentRole = plot.getRole(target.getUniqueId());

        for (int i = 0; i < roles.size(); i++) {
            if (i >= 27) break; 
            String roleName = roles.get(i);
            boolean isCurrent = roleName.equalsIgnoreCase(currentRole);
            
            Material icon = isCurrent ? Material.LIME_DYE : Material.GRAY_DYE;
            String name = (isCurrent ? "§a" : "§7") + roleName;
            
            inv.setItem(i, GUIManager.createItem(icon, name, 
                List.of(isCurrent ? "§a(Current Role)" : "§eClick to Set")));
        }

        // Remove Button
        inv.setItem(22, GUIManager.createItem(Material.REDSTONE_BLOCK, 
            plugin.msg().get(player, "button_remove_trusted"), 
            List.of("§7Revoke all access.")));
        
        // Back
        inv.setItem(18, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --- HANDLERS ---

    public void handlePlotSelectorClick(Player player, InventoryClickEvent e, PlotSelectorHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        int index = e.getSlot();
        
        if (index >= 0 && index < plots.size()) {
            openRolesMenu(player, plots.get(index));
        }
    }

    public void handleRolesMenuClick(Player player, InventoryClickEvent e, RolesMenuHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Plot plot = holder.getPlot();

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                openManageMenu(player, plot, meta.getOwningPlayer());
            }
            return;
        }

        int slot = e.getSlot();
        if (slot == 49) openAddMenu(player, plot);
        else if (slot == 48) plugin.gui().openMain(player);
        else if (slot == 50) player.closeInventory();
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, RoleAddHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Plot plot = holder.getPlot();

        if (e.getSlot() == 49) { // Back
            openRolesMenu(player, plot);
            return;
        }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                openManageMenu(player, plot, meta.getOwningPlayer());
            }
        }
    }

    public void handleManageRoleClick(Player player, InventoryClickEvent e, RoleManageHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        
        if (e.getSlot() == 18) { // Back
            openRolesMenu(player, plot);
            return;
        }

        if (e.getSlot() == 22) { // Remove
            plugin.store().removePlayerRole(plot, target.getUniqueId());
            plugin.msg().send(player, "role_removed", Map.of("PLAYER", target.getName()));
            plugin.effects().playUnclaim(player);
            openRolesMenu(player, plot);
            return;
        }

        List<String> roles = plugin.cfg().getRoleNames();
        if (e.getSlot() >= 0 && e.getSlot() < roles.size()) {
            String newRole = roles.get(e.getSlot());
            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", target.getName(), "ROLE", newRole));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot);
        }
    }
}
