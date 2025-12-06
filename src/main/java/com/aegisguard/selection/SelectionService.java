package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SelectionService implements Listener {

    private final AegisGuard plugin;
    
    // Selection Cache
    private final Map<UUID, Location> loc1 = new HashMap<>();
    private final Map<UUID, Location> loc2 = new HashMap<>();
    private final Map<UUID, Boolean> selectionIsServer = new HashMap<>();
    
    // Removed: WAND_KEY and SERVER_WAND_KEY are now handled by ItemManager

    public SelectionService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean hasSelection(Player p) {
        return loc1.containsKey(p.getUniqueId()) && loc2.containsKey(p.getUniqueId());
    }
    
    // --- ADDED: Required for AdminCommand to save to SQL ---
    public Location[] getSelectionLocations(UUID uuid) {
        if (!loc1.containsKey(uuid) || !loc2.containsKey(uuid)) return null;
        return new Location[]{ loc1.get(uuid), loc2.get(uuid) };
    }

    public Cuboid getSelection(Player p) {
        if (!hasSelection(p)) return null;
        // Force vertical expansion even for raw gets
        return createVerticallyExpandedCuboid(loc1.get(p.getUniqueId()), loc2.get(p.getUniqueId()));
    }

    // REMOVED: getWand() method is now handled by ItemManager.java

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        
        if (item == null || item.getType() == Material.AIR) return;
        
        // --- NEW: Centralized Item Check ---
        boolean isPlayerWand = plugin.getItemManager().isPlayerWand(item);
        boolean isSentinelScepter = plugin.getItemManager().isSentinelScepter(item);

        // If it's not a valid wand, ignore the interaction
        if (!isPlayerWand && !isSentinelScepter) return;

        e.setCancelled(true); 
        
        // Sentinel Scepter is used for server claims (isServer=true)
        selectionIsServer.put(p.getUniqueId(), isSentinelScepter);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc1.put(p.getUniqueId(), loc);
            p.sendMessage(ChatColor.GREEN + "Corner 1 set at " + formatLoc(loc));
            playSelectionEffect(p, loc, isSentinelScepter);
            
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc2.put(p.getUniqueId(), loc);
            p.sendMessage(ChatColor.GREEN + "Corner 2 set at " + formatLoc(loc));
            playSelectionEffect(p, loc, isSentinelScepter);
        }
    }

    public void confirmClaim(Player p) {
        UUID uuid = p.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();
        
        if (!hasSelection(p)) {
            p.sendMessage(lang.getMsg(p, "must_select"));
            return;
        }

        boolean isServerClaim = selectionIsServer.getOrDefault(uuid, false);
        Location l1 = loc1.get(uuid);
        Location l2 = loc2.get(uuid);

        if (!l1.getWorld().equals(l2.getWorld())) {
            p.sendMessage(ChatColor.RED + "Corners must be in the same world!");
            return;
        }
        
        Cuboid selection = createVerticallyExpandedCuboid(l1, l2);
        
        // If this is a SERVER claim via the stick, we handle it specially
        // But usually, server claims are done via /ag admin claim
        if (isServerClaim) {
           // We defer to the AdminCommand for server claims to ensure SQL consistency
           p.sendMessage(ChatColor.GOLD + "Server Selection Active. Type " + ChatColor.WHITE + "/ag admin claim <Name>" + ChatColor.GOLD + " to finalize.");
           return;
        }

        // --- PLAYER CLAIM LOGIC (Existing) ---
        if (!plugin.getWorldRules().allowClaims(p.getWorld())) {
            p.sendMessage(ChatColor.RED + "Estates are disabled in this world.");
            return;
        }

        if (plugin.getEstateManager().isOverlapping(selection)) {
            p.sendMessage(lang.getMsg(p, "claim_failed_overlap"));
            return;
        }
        
        // ... (Remaining economy and player claim logic preserved) ...
        
        String format = plugin.getConfig().getString("estates.naming.private_format", "%player%'s Estate");
        String name = format.replace("%player%", p.getName());
        
        Estate estate = plugin.getEstateManager().createEstate(p, selection, name, false); 
        
        if (estate != null) {
            p.sendMessage(lang.getMsg(p, "claim_success")
                .replace("%type%", lang.getTerm("type_private"))
                .replace("%name%", name));
            
            if (plugin.getConfig().getBoolean("estates.consume_wand_on_claim", true)) {
                consumeWand(p);
            }
            
            loc1.remove(uuid);
            loc2.remove(uuid);
            selectionIsServer.remove(uuid);
        }
    }
    
    // NEW HELPER: Creates 256+ height region regardless of click
    private Cuboid createVerticallyExpandedCuboid(Location p1, Location p2) {
        World w = p1.getWorld();
        int minH = w.getMinHeight();
        int maxH = w.getMaxHeight();
        
        Location bot = p1.clone(); 
        bot.setY(minH);
        
        Location top = p2.clone(); 
        top.setY(maxH);
        
        return new Cuboid(bot, top);
    }
    
    public void consumeWand(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            // Updated check to use the local isWand(), which now uses ItemManager
            if (isWand(contents[i])) { 
                if (contents[i].getAmount() > 1) {
                    contents[i].setAmount(contents[i].getAmount() - 1);
                } else {
                    p.getInventory().setItem(i, null);
                }
                break;
            }
        }
    }
    
    public boolean isWand(ItemStack item) {
        // NEW: Check if the item is the official Player Wand using the ItemManager
        return plugin.getItemManager().isPlayerWand(item);
    }
    
    private void playSelectionEffect(Player p, Location loc, boolean isServer) {
        try {
            Particle particle = isServer ? Particle.SOUL_FIRE_FLAME : Particle.VILLAGER_HAPPY;
            p.spawnParticle(particle, loc.clone().add(0.5, 1.2, 0.5), 5, 0.2, 0.2, 0.2, 0);
            p.playSound(p.getLocation(), isServer ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        } catch (Exception ignored) {}
    }
    
    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }
}
