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
     * Holder for the Role Permissions submenu.
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
            // Chat/system: still MessagesUtil
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
        String baseTitle = plugin.codex().tr(player, "trusted_plot_selector_title");
        String title = GUIManager.safeText(baseTitle, "§8Select Plot");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(), 54, title);

        int slot = 0;
        for (Plot plot : plots) {
            if (slot >= 54) break;

            List<String> lore = new ArrayList<>();

            // World line
            String worldLine = plugin.codex().tr(
                player,
                "trusted_plot_world_line",
                Map.of("WORLD", plot.getWorld())
            );
            if (worldLine == null || worldLine.isEmpty()) {
                worldLine = "§7World: §f" + plot.getWorld();
            }
            lore.add(worldLine);

            // Size line
            int sizeX = plot.getX2() - plot.getX1();
            int sizeZ = plot.getZ2() - plot.getZ1();
            String sizeLine = plugin.codex().tr(
                player,
                "trusted_plot_size_line",
                Map.of("X", String.valueOf(sizeX), "Z", String.valueOf(sizeZ))
            );
            if (sizeLine == null || sizeLine.isEmpty()) {
                sizeLine = "§7Size: §e" + sizeX + "x" + sizeZ;
            }
            lore.add(sizeLine);

            lore.add(" ");

            // Click to manage
            String clickLine = plugin.codex().tr(player, "trusted_plot_click_manage");
            if (clickLine == null || clickLine.isEmpty()) {
                clickLine = "§eClick to Manage Roles";
            }
            lore.add(clickLine);

            String name = plugin.codex().tr(
                player,
                "trusted_plot_name",
                Map.of("INDEX", String.valueOf(slot + 1))
            );
            if (name == null || name.isEmpty()) {
                name = "§aPlot #" + (slot + 1);
            }

            inv.setItem(slot, GUIManager.createItem(
                Material.GRASS_BLOCK,
                name,
                lore
            ));
            slot++;
        }
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --- GUI 2: ROLES LIST ---
    public void openRolesMenu(Player player, Plot plot) {
        String baseTitle = plugin.codex().tr(player, "roles_gui_title");
        String title = GUIManager.safeText(baseTitle, "§8Manage Roles");
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

                String displayName = plugin.codex().tr(
                    player,
                    "roles_member_name",
                    Map.of("PLAYER", name)
                );
                if (displayName == null || displayName.isEmpty()) {
                    displayName = "§e" + name;
                }
                meta.setDisplayName(displayName);

                List<String> lore = new ArrayList<>();

                String roleLine = plugin.codex().tr(
                    player,
                    "roles_member_role_line",
                    Map.of("ROLE", role)
                );
                if (roleLine == null || roleLine.isEmpty()) {
                    roleLine = "§7Role: §f" + role;
                }
                lore.add(roleLine);

                lore.add(" ");

                String clickLine = plugin.codex().tr(player, "roles_member_click_lore");
                if (clickLine == null || clickLine.isEmpty()) {
                    clickLine = "§eClick to Edit Role & Permissions";
                }
                lore.add(clickLine);

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // Add Button
        String addName = plugin.codex().tr(player, "button_add_trusted");
        if (addName == null || addName.isEmpty()) addName = "§aAdd Trusted Player";
        List<String> addLore = plugin.codex().list(player, "add_trusted_lore");
        if (addLore == null) addLore = List.of();
        inv.setItem(49, GUIManager.createItem(Material.EMERALD, addName, addLore));

        // Back
        String backName = plugin.codex().tr(player, "button_back");
        if (backName == null || backName.isEmpty()) backName = "§fBack";
        List<String> backLore = plugin.codex().list(player, "back_lore");
        if (backLore == null) backLore = List.of();
        inv.setItem(48, GUIManager.createItem(Material.ARROW, backName, backLore));

        // Exit
        String exitName = plugin.codex().tr(player, "button_exit");
        if (exitName == null || exitName.isEmpty()) exitName = "§cClose";
        List<String> exitLore = plugin.codex().list(player, "exit_lore");
        if (exitLore == null) exitLore = List.of();
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, exitName, exitLore));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --- GUI 3: ADD PLAYER ---
    private void openAddMenu(Player player, Plot plot) {
        String baseTitle = plugin.codex().tr(player, "add_trusted_title");
        String title = GUIManager.safeText(baseTitle, "§8Add Trusted Player");
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

                String headName = plugin.codex().tr(
                    player,
                    "add_trusted_player_name",
                    Map.of("PLAYER", nearby.getName())
                );
                if (headName == null || headName.isEmpty()) {
                    headName = "§a" + nearby.getName();
                }

                String clickLore = plugin.codex().tr(player, "add_trusted_click_lore");
                if (clickLore == null || clickLore.isEmpty()) {
                    clickLore = "§7Click to add to plot.";
                }

                meta.setDisplayName(headName);
                meta.setLore(List.of(clickLore));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (slot == 0) {
            String noneName = plugin.codex().tr(player, "add_trusted_none_title");
            if (noneName == null || noneName.isEmpty()) {
                noneName = "§cNo Players Nearby";
            }
            List<String> noneLore = plugin.codex().list(player, "add_trusted_none_lore");
            if (noneLore == null || noneLore.isEmpty()) {
                noneLore = List.of("§7Ask your friend to stand closer!");
            }
            inv.setItem(22, GUIManager.createItem(
                Material.BARRIER,
                noneName,
                noneLore
            ));
        }

        String backName = plugin.codex().tr(player, "button_back");
        if (backName == null || backName.isEmpty()) backName = "§fBack";
        List<String> backLore = plugin.codex().list(player, "back_lore");
        if (backLore == null) backLore = List.of();
        inv.setItem(49, GUIManager.createItem(Material.ARROW, backName, backLore));
            
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --- GUI 4: MANAGE SPECIFIC PLAYER (Role + Permissions entry point) ---
    private void openManageMenu(Player player, Plot plot, OfflinePlayer target) {
        String targetName = (target.getName() != null) ? target.getName() : "Unknown";
        String baseTitle = plugin.codex().tr(
            player,
            "roles_manage_title",
            Map.of("PLAYER", targetName)
        );
        String title = GUIManager.safeText(
            baseTitle,
            "§8Manage: " + targetName
        );
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(plot, target), 27, title);

        List<String> roles = plugin.cfg().getRoleNames();
        String currentRole = plot.getRole(target.getUniqueId());

        for (int i = 0; i < roles.size(); i++) {
            if (i >= 27) break; 
            String roleName = roles.get(i);
            boolean isCurrent = roleName.equalsIgnoreCase(currentRole);
            
            Material icon = isCurrent ? Material.LIME_DYE : Material.GRAY_DYE;

            String displayRoleName = plugin.codex().tr(
                player,
                "roles_role_name",
                Map.of("ROLE", roleName)
            );
            if (displayRoleName == null || displayRoleName.isEmpty()) {
                displayRoleName = (isCurrent ? "§a" : "§7") + roleName;
            }

            String loreLine = plugin.codex().tr(
                player,
                isCurrent ? "roles_role_current_lore" : "roles_role_click_set_lore"
            );
            if (loreLine == null || loreLine.isEmpty()) {
                loreLine = isCurrent ? "§a(Current Role)" : "§eClick to Set";
            }

            inv.setItem(i, GUIManager.createItem(
                icon,
                displayRoleName,
                List.of(loreLine)
            ));
        }

        // Remove Button
        String removeName = plugin.codex().tr(player, "button_remove_trusted");
        if (removeName == null || removeName.isEmpty()) {
            removeName = "§cRemove Trusted";
        }
        List<String> removeLore = plugin.codex().list(player, "remove_trusted_lore");
        if (removeLore == null || removeLore.isEmpty()) {
            removeLore = List.of("§7Revoke all access.");
        }
        inv.setItem(22, GUIManager.createItem(
            Material.REDSTONE_BLOCK, 
            removeName, 
            removeLore
        ));

        // Role Flag Permissions button
        String roleDisplay = (currentRole != null) ? currentRole : "Unassigned";
        String flagsName = plugin.codex().tr(player, "button_role_permissions");
        if (flagsName == null || flagsName.isEmpty()) {
            flagsName = "§bEdit Role Permissions";
        }
        List<String> flagsLore = plugin.codex().list(player, "role_permissions_lore");
        if (flagsLore == null || flagsLore.isEmpty()) {
            flagsLore = List.of(
                "§7Role: §f" + roleDisplay,
                " ",
                "§7Adjust what this role may do",
                "§7inside this dominion.",
                " ",
                "§eClick to open role flags"
            );
        }
        inv.setItem(24, GUIManager.createItem(
            Material.BOOK,
            flagsName,
            flagsLore
        ));
        
        // Back
        String backName = plugin.codex().tr(player, "button_back");
        if (backName == null || backName.isEmpty()) backName = "§fBack";
        List<String> backLore = plugin.codex().list(player, "back_lore");
        if (backLore == null) backLore = List.of();
        inv.setItem(18, GUIManager.createItem(
            Material.ARROW, 
            backName, 
            backLore
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --- GUI 5: ROLE FLAG PERMISSIONS (Per-role flag toggles) ---
    private void openRoleFlagsMenu(Player player, Plot plot, OfflinePlayer target, String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            // Chat hint stays in MessagesUtil for now
            plugin.msg().send(player, "role_self");
            plugin.effects().playError(player);
            openManageMenu(player, plot, target);
            return;
        }

        String baseTitle = plugin.codex().tr(
            player,
            "role_flags_title",
            Map.of("ROLE", roleName)
        );
        String title = GUIManager.safeText(
            baseTitle,
            "§8Role Flags: " + roleName
        );

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
        String backName = plugin.codex().tr(player, "button_back");
        if (backName == null || backName.isEmpty()) backName = "§fBack";
        List<String> backLore = plugin.codex().list(player, "back_lore");
        if (backLore == null) backLore = List.of();
        inv.setItem(22, GUIManager.createItem(
            Material.ARROW,
            backName,
            backLore
        ));

        // Legend
        String legendName = plugin.codex().tr(player, "role_flags_legend_title");
        if (legendName == null || legendName.isEmpty()) legendName = "§7Legend";
        List<String> legendLore = plugin.codex().list(player, "role_flags_legend_lore");
        if (legendLore == null || legendLore.isEmpty()) {
            legendLore = List.of(
                "§aAllow §7= Role may bypass this rule.",
                "§cDeny  §7= Role is always blocked.",
                "§7Inherit §7= Follow normal claim logic."
            );
        }
        inv.setItem(26, GUIManager.createItem(
            Material.PAPER,
            legendName,
            legendLore
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
                plugin.msg().send(player, "role_self");
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
