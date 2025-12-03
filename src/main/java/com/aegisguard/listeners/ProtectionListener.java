package com.yourname.aegisguard.listeners;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.EstateManager;
import com.yourname.aegisguard.managers.RoleManager;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {

    private final AegisGuard plugin;

    public ProtectionListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ==========================================================
    // 🧱 BUILDING PROTECTION
    // ==========================================================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (checkProtection(event.getPlayer(), event.getBlock(), "BLOCK_BREAK")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (checkProtection(event.getPlayer(), event.getBlock(), "BLOCK_PLACE")) {
            event.setCancelled(true);
        }
    }

    // ==========================================================
    // 🗝️ INTERACTION PROTECTION (Chests, Doors, Buttons)
    // ==========================================================

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() == Action.PHYSICAL) return; // Allow pressure plates unless specified

        String flag = "INTERACT"; // Default flag
        Material type = event.getClickedBlock().getType();

        // Smart Flag Detection: Determine what they are touching
        if (isContainer(type)) {
            flag = "CONTAINER"; // Chests, Barrels, Shulkers
        } else if (isDoor(type)) {
            flag = "USE_DOORS";
        } else if (isButtonOrLever(type)) {
            flag = "USE_BUTTONS";
        }

        if (checkProtection(event.getPlayer(), event.getClickedBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    // ==========================================================
    // ⚔️ PVP & MOB PROTECTION
    // ==========================================================

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        
        // Is the VICTIM inside an Estate?
        Estate estate = plugin.getEstateManager().getEstateAt(event.getEntity().getLocation());
        if (estate == null) return; // Wilderness PVP is handled by other plugins

        // 1. Player vs Player
        if (event.getEntity() instanceof Player) {
            // Check if PVP flag is disabled in this estate
            if (!estate.getFlag("pvp")) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "PvP is disabled in this Estate.");
            }
        }
        // 2. Player vs Animal/Mob
        else {
            // Check if player has permission to kill mobs here
            if (checkProtection(attacker, event.getEntity().getLocation(), "MOB_KILL")) {
                event.setCancelled(true);
            }
        }
    }

    // ==========================================================
    // 🧠 THE CORE LOGIC (The "Brain")
    // ==========================================================

    /**
     * Checks if a player is ALLOWED to perform an action.
     * @return TRUE if the action should be BLOCKED (Protection active).
     */
    private boolean checkProtection(Player player, Block block, String flag) {
        return checkProtection(player, block.getLocation(), flag);
    }

    private boolean checkProtection(Player player, org.bukkit.Location loc, String flag) {
        // 1. Admin Bypass Check
        if (player.hasPermission("aegis.admin.bypass")) return false; // Allow Admins

        // 2. Is there an Estate here?
        Estate estate = plugin.getEstateManager().getEstateAt(loc);
        if (estate == null) return false; // Wilderness is free (unless configured otherwise)

        // 3. Is the player the OWNER?
        if (estate.getOwnerId().equals(player.getUniqueId())) return false; // Owners can do anything

        // 4. Is the player a MEMBER?
        if (estate.isMember(player.getUniqueId())) {
            String roleId = estate.getMemberRole(player.getUniqueId());
            RoleManager.RoleDefinition role = plugin.getRoleManager().getPrivateRole(roleId);
            
            // If role exists and has the flag, ALLOW IT (Return false to block)
            if (role != null && role.hasDefaultFlag(flag)) {
                return false; 
            }
        }

        // 5. Fallback: VISITOR / BANNED
        // If we reached here, they are a visitor or didn't have permission.
        // Send denial message only if they tried to interact
        sendDenialMessage(player, flag);
        return true; // BLOCK THE ACTION
    }

    private void sendDenialMessage(Player player, String flag) {
        // Cooldown check could go here to prevent spam
        player.sendTitle("", ChatColor.RED + "✖ Protection Active", 0, 20, 10);
        // Or use the LanguageManager: plugin.getLanguageManager().getMsg(player, "no_permission");
    }

    // --- UTILITIES ---
    private boolean isContainer(Material m) {
        return m.name().contains("CHEST") || m.name().contains("SHULKER") || m.name().contains("BARREL") || m.name().contains("HOPPER") || m.name().contains("DISPENSER");
    }

    private boolean isDoor(Material m) {
        return m.name().contains("DOOR") || m.name().contains("GATE") || m.name().contains("TRAPDOOR");
    }
    
    private boolean isButtonOrLever(Material m) {
        return m.name().contains("BUTTON") || m.name().contains("LEVER") || m.name().contains("PLATE");
    }
}
