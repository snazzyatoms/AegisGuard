package com.yourname.aegisguard.selection;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Cuboid;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionService implements Listener {

    private final AegisGuard plugin;
    
    // Selection Cache (Player -> Location)
    private final Map<UUID, Location> loc1 = new HashMap<>();
    private final Map<UUID, Location> loc2 = new HashMap<>();
    private final Map<UUID, Boolean> selectionIsServer = new HashMap<>();
    
    // NBT Keys for the Wand Item
    public static final NamespacedKey WAND_KEY = new NamespacedKey("aegisguard", "wand");
    public static final NamespacedKey SERVER_WAND_KEY = new NamespacedKey("aegisguard", "server_wand");

    public SelectionService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean hasSelection(Player p) {
        return loc1.containsKey(p.getUniqueId()) && loc2.containsKey(p.getUniqueId());
    }
    
    public Cuboid getSelection(Player p) {
        if (!hasSelection(p)) return null;
        return new Cuboid(loc1.get(p.getUniqueId()), loc2.get(p.getUniqueId()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        boolean isNormal = meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
        boolean isServer = meta.getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE);

        if (!isNormal && !isServer) return;

        e.setCancelled(true); 
        selectionIsServer.put(p.getUniqueId(), isServer);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc1.put(p.getUniqueId(), loc);
            // "Corner 1 Set"
            p.sendMessage(ChatColor.GREEN + "Corner 1 set at " + formatLoc(loc));
            playSelectionEffect(p, loc, isServer);
            
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc2.put(p.getUniqueId(), loc);
            // "Corner 2 Set"
            p.sendMessage(ChatColor.GREEN + "Corner 2 set at " + formatLoc(loc));
            playSelectionEffect(p, loc, isServer);
        }
    }

    // ==========================================================
    // 🏗️ CONFIRM CLAIM (THE LOGIC)
    // ==========================================================
    public void confirmClaim(Player p) {
        UUID uuid = p.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();
        
        if (!hasSelection(p)) {
            p.sendMessage(lang.getMsg(p, "must_select")); // "You must select 2 corners first."
            return;
        }

        boolean isServerClaim = selectionIsServer.getOrDefault(uuid, false);
        Location l1 = loc1.get(uuid);
        Location l2 = loc2.get(uuid);

        if (!l1.getWorld().equals(l2.getWorld())) {
            p.sendMessage(ChatColor.RED + "Corners must be in the same world!");
            return;
        }
        
        Cuboid selection = new Cuboid(l1, l2);
        int radius = Math.max(selection.getWidth(), selection.getLength()) / 2;
        
        // --- VALIDATION ---
        if (!isServerClaim) {
            // 1. World Allowed?
            if (!plugin.getConfig().getBoolean("estates.per_world." + p.getWorld().getName() + ".allow_estates", true)) {
                p.sendMessage(ChatColor.RED + "Estates are disabled in this world.");
                return;
            }

            // 2. Overlap Check
            if (plugin.getEstateManager().isOverlapping(selection)) {
                p.sendMessage(lang.getMsg(p, "claim_failed_overlap"));
                return;
            }

            // 3. Size Limit
            int maxRadius = plugin.getConfig().getInt("estates.max_radius", 100);
            if (radius > maxRadius && !p.hasPermission("aegis.admin.bypass")) {
                p.sendMessage(lang.getMsg(p, "claim_failed_limit").replace("%max%", String.valueOf(maxRadius)));
                return;
            }
            
            // 4. Cost Check (Personal Wallet)
            double cost = plugin.getEconomy().calculateClaimCost(selection);
            if (plugin.getConfig().getBoolean("economy.enabled", true) && cost > 0) {
                if (!plugin.getEconomy().withdraw(p, cost)) {
                    p.sendMessage(lang.getMsg(p, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
                    return;
                }
                p.sendMessage(ChatColor.GREEN + "Deducted $" + cost + " from your wallet.");
            }
        }

        // --- CREATION ---
        String name;
        if (isServerClaim) {
            name = "Server Zone";
        } else {
            // Auto-Name: "Steve's Estate"
            String format = plugin.getConfig().getString("estates.naming.private_format", "%player%'s Estate");
            name = format.replace("%player%", p.getName());
        }
        
        // Create via Manager
        Estate estate = plugin.getEstateManager().createEstate(p, selection, name, false); // isGuild = false (Private)
        
        if (isServerClaim && estate != null) {
            // Set Server Flags
            estate.setFlag("build", false);
            estate.setFlag("pvp", false);
            estate.setFlag("safe_zone", true);
            // TODO: Set Owner to Server UUID in EstateManager
        }

        if (estate != null) {
            p.sendMessage(lang.getMsg(p, "claim_success")
                .replace("%type%", isServerClaim ? "Server Zone" : lang.getTerm("type_private"))
                .replace("%name%", name));
            
            // Consume Wand
            if (!isServerClaim && plugin.getConfig().getBoolean("estates.consume_wand_on_claim", true)) {
                consumeWand(p);
            }
            
            // Clear Selection
            loc1.remove(uuid);
            loc2.remove(uuid);
            selectionIsServer.remove(uuid);
        }
    }
    
    // ==========================================================
    // 🛠️ UTILITIES
    // ==========================================================

    public void consumeWand(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
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
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
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
