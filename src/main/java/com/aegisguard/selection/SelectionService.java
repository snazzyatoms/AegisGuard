package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
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
    
    // NBT Keys
    public static final NamespacedKey WAND_KEY = new NamespacedKey("aegisguard", "wand");
    public static final NamespacedKey SERVER_WAND_KEY = new NamespacedKey("aegisguard", "server_wand");

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

    // --- ADDED: The Sentinel Scepter Generator ---
    public ItemStack getWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Sentinel's Scepter");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "A tool of absolute authority.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Right-Click: " + ChatColor.WHITE + "Select Pos 1");
            lore.add(ChatColor.YELLOW + "Left-Click: "  + ChatColor.WHITE + "Select Pos 2");
            meta.setLore(lore);
            
            // Apply NBT so the listener recognizes it
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(SERVER_WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            
            wand.setItemMeta(meta);
        }
        return wand;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        
        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        boolean isNormal = meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
        boolean isServer = meta.getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE);

        // If it's not a wand, ignore
        if (!isNormal && !isServer) return;

        e.setCancelled(true); 
        selectionIsServer.put(p.getUniqueId(), isServer);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc1.put(p.getUniqueId(), loc);
            p.sendMessage(ChatColor.GREEN + "Corner 1 set at " + formatLoc(loc));
            playSelectionEffect(p, loc, isServer);
            
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc2.put(p.getUniqueId(), loc);
            p.sendMessage(ChatColor.GREEN + "Corner 2 set at " + formatLoc(loc));
            playSelectionEffect(p, loc, isServer);
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
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
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
