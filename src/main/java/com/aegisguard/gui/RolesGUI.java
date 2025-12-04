package com.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.managers.RoleManager.RoleDefinition;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

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
        private final Estate estate;
        public RolesMenuHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleAddHolder implements InventoryHolder {
        private final Estate estate;
        public RoleAddHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }
    
    public static class RoleManageHolder implements InventoryHolder {
        private final Estate estate;
        private final OfflinePlayer target;
        public RoleManageHolder(Estate estate, OfflinePlayer target) { 
            this.estate = estate; 
            this.target = target;
        }
        public Estate getEstate() { return estate; }
        public OfflinePlayer getTarget() { return target; }
        @Override public Inventory getInventory() { return null; }
    }

    // --- ENTRY POINT ---
    public void open(Player player) {
        // 1. Admin Override
        if (plugin.isAdmin(player)) {
            Estate standing = plugin.getEstateManager().getEstateAt(player.getLocation());
            if (standing != null) {
                openRolesMenu(player, standing);
                return;
            }
        }

        // 2. Normal User Flow
        List<Estate> estates = plugin.getEstateManager().getEstates(player.getUniqueId());
        
        if (estates == null || estates.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_plot_here"));
            return;
        }

        if (estates.size() > 1) {
            openPlotSelector(player, estates);
        } else {
            openRolesMenu(player, estates.get(0));
        }
    }

    // --- GUI 1: SELECT ESTATE ---
    private void openPlotSelector(Player player, List<Estate> estates) {
        String title = plugin.getLanguageManager().getGui("trusted_plot_selector_title");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(), 54, title);

        int slot = 0;
        for (Estate estate : estates) {
            if (slot >= 54) break;
            
            List<String> lore = new ArrayList<>();
            lore.add("§7World: §f" + estate.getWorld().getName());
            lore.add("§7Size: §e" + estate.getRegion().getWidth() + "x" + estate.getRegion().getLength());
            lore.add(" ");
            lore.add("§eClick to Manage Roles");

            inv.setItem(slot++, GUIManager.createItem(
                Material.GRASS_BLOCK,
                "§a" + estate.getName(), 
                lore
            ));
        }
        
        player.openInventory(inv);
    }

    // --- GUI 2: ROLES LIST ---
    public void openRolesMenu(Player player, Estate estate) {
        String title = plugin.getLanguageManager().getGui("roles_gui_title");
        Inventory inv = Bukkit.createInventory(new RolesMenuHolder(estate), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        // Get map of MemberUUID -> RoleID
        // Note: Estate.java logic might store roles in a Map<UUID, String>
        // If not, ensure getPlayerRoles() or getAllMembers() returns this map
        Set<UUID> members = estate.getAllMembers();
        
        for (UUID uuid : members) {
            if (slot >= 45) break;
            
            String roleId = estate.getMemberRole(uuid);
            RoleDefinition roleDef = plugin.getRoleManager().getPrivateRole(roleId);
            String roleName = (roleDef != null) ? roleDef.getDisplayName() : roleId;
            
            boolean isOwnerEntry = uuid.equals(estate.getOwnerId());
            boolean isViewerOwner = uuid.equals(player.getUniqueId());

            // Hide owner from themselves
            if (isOwnerEntry && isViewerOwner) continue;

            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = (member.getName() != null) ? member.getName() : "Unknown";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);
                meta.setDisplayName("§e" + name);
                List<String> lore = new ArrayList<>();
                lore.add("§7Role: §f" + roleName);
                lore.add(" ");
                lore.add("§eClick to Edit Role");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // Add Button
        inv.setItem(49, GUIManager.createItem(Material.EMERALD, 
            plugin.getLanguageManager().getMsg(player, "button_add_trusted"), 
            plugin.getLanguageManager().getMsgList(player, "add_trusted_lore")));

        // Back
        inv.setItem(48, GUIManager.createItem(Material.ARROW, plugin.getLanguageManager().getGui("button_back")));

        // Exit
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, plugin.getLanguageManager().getGui("button_close")));

        player.openInventory(inv);
    }

    // --- GUI 3: ADD PLAYER ---
    private void openAddMenu(Player player, Estate estate) {
        String title = plugin.getLanguageManager().getGui("add_trusted_title");
        Inventory inv = Bukkit.createInventory(new RoleAddHolder(estate), 54, title);

        int slot = 0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (slot >= 54) break;
            if (nearby.getLocation().distance(player.getLocation()) > 50) continue;
            if (nearby.equals(player)) continue; 
            if (estate.isMember(nearby.getUniqueId())) continue; 

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(nearby);
                meta.setDisplayName("§a" + nearby.getName());
                meta.setLore(List.of("§7Click to add to estate."));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (slot == 0) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER, "§cNo Players Nearby", 
                List.of("§7Ask your friend to stand closer!")));
        }

        inv.setItem(49, GUIManager.createItem(Material.ARROW, plugin.getLanguageManager().getGui("button_back")));
            
        player.openInventory(inv);
    }

    // --- GUI 4: MANAGE SPECIFIC PLAYER ---
    private void openManageMenu(Player player, Estate estate, OfflinePlayer target) {
        String title = "§8Manage: " + target.getName();
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(estate, target), 54, title);

        // 1. Fetch all available roles from RoleManager
        List<RoleDefinition> roles = new ArrayList<>(plugin.getRoleManager().getAllPrivateRoles());
        // Sort by priority
        roles.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        
        String currentRoleId = estate.getMemberRole(target.getUniqueId());

        int slot = 0;
        for (RoleDefinition role : roles) {
            if (slot >= 45) break; 
            
            boolean isCurrent = role.getId().equalsIgnoreCase(currentRoleId);
            Material icon = role.getIcon();
            String name = (isCurrent ? "§a" : "§7") + role.getDisplayName();
            
            List<String> lore = new ArrayList<>(role.getDescription());
            lore.add(" ");
            lore.add(isCurrent ? "§a(Current Role)" : "§eClick to Set");
            
            inv.setItem(slot++, GUIManager.createItem(icon, name, lore));
        }

        // Remove Button
        inv.setItem(49, GUIManager.createItem(Material.REDSTONE_BLOCK, 
            "§cRevoke Access", 
            List.of("§7Remove player from estate.")));
        
        // Back
        inv.setItem(45, GUIManager.createItem(Material.ARROW, plugin.getLanguageManager().getGui("button_back")));

        player.openInventory(inv);
    }

    // --- HANDLERS ---

    public void handlePlotSelectorClick(Player player, InventoryClickEvent e, PlotSelectorHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        List<Estate> estates = plugin.getEstateManager().getEstates(player.getUniqueId());
        int index = e.getSlot();
        
        if (index >= 0 && index < estates.size()) {
            openRolesMenu(player, estates.get(index));
        }
    }

    public void handleRolesMenuClick(Player player, InventoryClickEvent e, RolesMenuHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Estate estate = holder.getEstate();

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                openManageMenu(player, estate, meta.getOwningPlayer());
            }
            return;
        }

        int slot = e.getSlot();
        if (slot == 49) openAddMenu(player, estate);
        else if (slot == 48) plugin.getGuiManager().openGuardianCodex(player);
        else if (slot == 50) player.closeInventory();
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, RoleAddHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Estate estate = holder.getEstate();

        if (e.getSlot() == 49) { // Back
            openRolesMenu(player, estate);
            return;
        }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                // Default to 'Resident' or 'Guest'
                String defaultRole = plugin.getConfig().getString("role_system.default_private_role", "resident");
                estate.setMember(meta.getOwningPlayer().getUniqueId(), defaultRole);
                // plugin.getEstateManager().saveEstate(estate);
                
                player.sendMessage("§aAdded " + meta.getOwningPlayer().getName());
                openRolesMenu(player, estate);
            }
        }
    }

    public void handleManageRoleClick(Player player, InventoryClickEvent e, RoleManageHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        Estate estate = holder.getEstate();
        OfflinePlayer target = holder.getTarget();
        
        if (e.getSlot() == 45) { // Back
            openRolesMenu(player, estate);
            return;
        }

        if (e.getSlot() == 49) { // Remove
            estate.removeMember(target.getUniqueId());
            // plugin.getEstateManager().saveEstate(estate);
            player.sendMessage("§cRemoved " + target.getName());
            openRolesMenu(player, estate);
            return;
        }

        // Role Selection
        List<RoleDefinition> roles = new ArrayList<>(plugin.getRoleManager().getAllPrivateRoles());
        // Must match sorting used in openManageMenu
        roles.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        
        if (e.getSlot() >= 0 && e.getSlot() < roles.size()) {
            RoleDefinition newRole = roles.get(e.getSlot());
            
            estate.setMember(target.getUniqueId(), newRole.getId());
            // plugin.getEstateManager().saveEstate(estate);
            
            player.sendMessage("§aSet role to " + newRole.getDisplayName());
            openRolesMenu(player, estate);
        }
    }
}
