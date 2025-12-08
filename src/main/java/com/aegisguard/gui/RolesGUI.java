package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.flags.TriState;
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
 * - Now also manages per-role flag permissions via a submenu.
 * - Fully localized: Supports instant language switching.
 *
 * IMPORTANT BACKEND NOTE:
 * This GUI expects your data/store layer to expose per-role flag overrides:
 *
 *   TriState getRoleFlagState(Plot plot, String roleName, String flagKey);
 *   void setRoleFlagState(Plot plot, String roleName, String flagKey, TriState state);
 *
 * Where TriState = INHERIT (follow normal flag logic), ALLOW (bypass), DENY.
 * You can wire these into your existing flag engine however you like.
 */
public class RolesGUI {

    private final AegisGuard plugin;

    /**
     * Flags that can be managed per-role in the Role Permissions submenu.
     * These keys should map to your internal flag identifiers.
     */
    private static final List<String> ROLE_FLAG_KEYS = List.of(
        "PVP",
        "CONTAINERS",
        "MOBS",
        "PETS",
        "ENTITIES",
        "FARM",
        "TNT",
        "FIRE",
        "PISTON",
        "ENTRY",
        "SHOP",
        "FLY",
        "ANIMALS",
        "REDSTONE",
        "VEHICLES"
    );

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

    /**
     * New holder for the Role Permissions submenu.
     * This edits the PER-ROLE flag bypass configuration for the role the target currently has.
     */
    public static class RoleFlagsHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final String roleName;

        public RoleFlagsHolder(Plot plot, OfflinePlayer target, String roleName) {
            this.plot = plot;
            this.target = target;
            this.roleName = roleName;
        }

        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public String getRoleName() { return roleName; }

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

            inv.setItem(slot, GUIManager.createItem(
                Material.GRASS_BLOCK,
                "§aPlot #" + (slot + 1),
                lore
            ));
            slot++;
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
                lore.add("§eClick to Edit Role & Permissions");
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
            inv.setItem(22, GUIManager.createItem(
                Material.BARRIER,
                "§cNo Players Nearby", 
                List.of("§7Ask your friend to stand closer!")
            ));
        }

        inv.setItem(49, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")));
            
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --- GUI 4: MANAGE SPECIFIC PLAYER (Role + Permissions entry point) ---
    private void openManageMenu(Player player, Plot plot, OfflinePlayer target) {
        String title = GUIManager.safeText(
            plugin.msg().get(player, "roles_manage_title", Map.of("PLAYER", target.getName())),
            "§8Manage: " + target.getName()
        );
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(plot, target), 27, title);

        List<String> roles = plugin.cfg().getRoleNames();
        String currentRole = plot.getRole(target.getUniqueId());

        for (int i = 0; i < roles.size(); i++) {
            if (i >= 27) break; 
            String roleName = roles.get(i);
            boolean isCurrent = roleName.equalsIgnoreCase(currentRole);
            
            Material icon = isCurrent ? Material.LIME_DYE : Material.GRAY_DYE;
            String name = (isCurrent ? "§a" : "§7") + roleName;
            
            inv.setItem(i, GUIManager.createItem(
                icon,
                name, 
                List.of(isCurrent ? "§a(Current Role)" : "§eClick to Set")
            ));
        }

        // Remove Button
        inv.setItem(22, GUIManager.createItem(
            Material.REDSTONE_BLOCK, 
            plugin.msg().get(player, "button_remove_trusted"), 
            List.of("§7Revoke all access.")
        ));

        // NEW: Role Flag Permissions button
        String roleDisplay = (currentRole != null) ? currentRole : "Unassigned";
        inv.setItem(24, GUIManager.createItem(
            Material.BOOK,
            "§bEdit Role Permissions",
            List.of(
                "§7Role: §f" + roleDisplay,
                " ",
                "§7Adjust what this role may do",
                "§7inside this dominion.",
                " ",
                "§eClick to open role flags"
            )
        ));
        
        // Back
        inv.setItem(18, GUIManager.createItem(
            Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --- GUI 5: ROLE FLAG PERMISSIONS (Per-role flag toggles) ---
    private void openRoleFlagsMenu(Player player, Plot plot, OfflinePlayer target, String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            // If somehow no role is assigned yet, just warn & return
            plugin.msg().send(player, "role_self"); // reuse or create better key later
            plugin.effects().playError(player);
            openManageMenu(player, plot, target);
            return;
        }

        String title = "§8Role Flags: " + roleName;
        Inventory inv = Bukkit.createInventory(
            new RoleFlagsHolder(plot, target, roleName),
            27,
            title
        );

        int slot = 0;
        for (String flagKey : ROLE_FLAG_KEYS) {
            if (slot >= 18) break; // keep bottom row for back / legend

            TriState state = TriState.INHERIT;
            try {
                // EXPECTED BACKEND API:
                // Returns INHERIT / ALLOW / DENY, or null = INHERIT
                TriState stored = plugin.store().getRoleFlagState(plot, roleName, flagKey);
                if (stored != null) state = stored;
            } catch (NoSuchMethodError ignored) {
                // Until you wire backend, default to INHERIT
            }

            Material icon = mapFlagToIcon(flagKey);
            ItemStack item = buildRoleFlagItem(flagKey, state, icon);

            inv.setItem(slot++, item);
        }

        // Back
        inv.setItem(22, GUIManager.createItem(
            Material.ARROW,
            plugin.msg().get(player, "button_back"),
            plugin.msg().getList(player, "back_lore")
        ));

        // (Optional) A small legend item
        inv.setItem(26, GUIManager.createItem(
            Material.PAPER,
            "§7Legend",
            List.of(
                "§aAllow §7= Role may bypass this rule.",
                "§cDeny  §7= Role is always blocked.",
                "§7Inherit §7= Follow normal claim logic."
            )
        ));

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
        
        int slot = e.getSlot();

        if (slot == 18) { // Back
            openRolesMenu(player, plot);
            return;
        }

        if (slot == 22) { // Remove
            plugin.store().removePlayerRole(plot, target.getUniqueId());
            plugin.msg().send(player, "role_removed", Map.of("PLAYER", target.getName()));
            plugin.effects().playUnclaim(player);
            openRolesMenu(player, plot);
            return;
        }

        if (slot == 24) { 
            // Open Role Permissions submenu for the target's current role
            String currentRole = plot.getRole(target.getUniqueId());
            if (currentRole == null) {
                // If no role set yet, just bounce them back with a gentle hint
                plugin.msg().send(player, "role_self"); // reuse / replace with better key later
                plugin.effects().playError(player);
                return;
            }
            openRoleFlagsMenu(player, plot, target, currentRole);
            return;
        }

        // Handle clicking on a dye to set role
        List<String> roles = plugin.cfg().getRoleNames();
        if (slot >= 0 && slot < roles.size()) {
            String newRole = roles.get(slot);
            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", target.getName(), "ROLE", newRole));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot);
        }
    }

    public void handleRoleFlagsClick(Player player, InventoryClickEvent e, RoleFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        String roleName = holder.getRoleName();
        int slot = e.getSlot();

        if (slot == 22) { // Back
            openManageMenu(player, plot, target);
            return;
        }

        if (slot < 0 || slot >= ROLE_FLAG_KEYS.size()) {
            return;
        }

        String flagKey = ROLE_FLAG_KEYS.get(slot);

        // Get current state from backend
        TriState current = TriState.INHERIT;
        try {
            TriState stored = plugin.store().getRoleFlagState(plot, roleName, flagKey);
            if (stored != null) current = stored;
        } catch (NoSuchMethodError ignored) {
            // Until backend is wired, act as INHERIT
        }

        TriState next = nextTriState(current);

        try {
            plugin.store().setRoleFlagState(plot, roleName, flagKey, next);
        } catch (NoSuchMethodError ignored) {
            // No-op until backend exists
        }

        plugin.effects().playToggle(player);

        // Refresh the GUI so the new state + lore are visible
        openRoleFlagsMenu(player, plot, target, roleName);
    }

    // --- SMALL HELPERS ---

    private TriState nextTriState(TriState current) {
        if (current == null || current == TriState.INHERIT) return TriState.ALLOW;
        if (current == TriState.ALLOW) return TriState.DENY;
        return TriState.INHERIT;
    }

    private Material mapFlagToIcon(String key) {
        switch (key.toUpperCase()) {
            case "PVP":        return Material.IRON_SWORD;
            case "CONTAINERS": return Material.CHEST;
            case "MOBS":       return Material.ZOMBIE_HEAD;
            case "PETS":       return Material.BONE;
            case "ENTITIES":   return Material.ARMOR_STAND;
            case "FARM":       return Material.WHEAT;
            case "TNT":        return Material.TNT;
            case "FIRE":       return Material.FLINT_AND_STEEL;
            case "PISTON":     return Material.PISTON;
            case "ENTRY":      return Material.OAK_DOOR;
            case "SHOP":       return Material.EMERALD;
            case "FLY":        return Material.FEATHER;
            case "ANIMALS":    return Material.CARROT;
            case "REDSTONE":   return Material.REDSTONE;
            case "VEHICLES":   return Material.MINECART;
            default:           return Material.PAPER;
        }
    }

    private ItemStack buildRoleFlagItem(String flagKey, TriState state, Material icon) {
        String niceName = "§b" + flagKey.substring(0, 1).toUpperCase() + flagKey.substring(1).toLowerCase();

        String stateLabel;
        String stateColor;
        switch (state) {
            case ALLOW:
                stateLabel = "Allow (Bypass)";
                stateColor = "§a";
                break;
            case DENY:
                stateLabel = "Deny (Blocked)";
                stateColor = "§c";
                break;
            default:
                stateLabel = "Inherit";
                stateColor = "§7";
                break;
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Current: " + stateColor + stateLabel);
        lore.add(" ");
        lore.add("§7Inherit: Follow normal plot/world rules.");
        lore.add("§7Allow: This role may bypass this restriction.");
        lore.add("§7Deny: This role is always blocked.");
        lore.add(" ");
        lore.add("§eClick to cycle");

        return GUIManager.createItem(icon, niceName, lore);
    }
}
