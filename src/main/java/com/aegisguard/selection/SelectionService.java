package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
    private final Map<UUID, Location> loc1 = new HashMap<>();
    private final Map<UUID, Location> loc2 = new HashMap<>();
    
    // Tracks if the player is using the Admin Wand
    private final Map<UUID, Boolean> selectionIsServer = new HashMap<>();
    
    public static final NamespacedKey WAND_KEY = new NamespacedKey("aegisguard", "wand");
    public static final NamespacedKey SERVER_WAND_KEY = new NamespacedKey("aegisguard", "server_wand");

    public SelectionService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean hasSelection(Player p) {
        return loc1.containsKey(p.getUniqueId()) && loc2.containsKey(p.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Check which wand is being used
        boolean isNormal = meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
        boolean isServer = meta.getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE);

        if (!isNormal && !isServer) return;

        e.setCancelled(true);

        // Mark this selection session
        selectionIsServer.put(p.getUniqueId(), isServer);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            loc1.put(p.getUniqueId(), e.getClickedBlock().getLocation());
            plugin.msg().send(p, "corner1_set", Map.of(
                "X", String.valueOf(e.getClickedBlock().getX()),
                "Z", String.valueOf(e.getClickedBlock().getZ())
            ));
            if (isServer) p.sendMessage("§7(Selected with §cSentinel's Scepter§7)");
        } 
        else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            loc2.put(p.getUniqueId(), e.getClickedBlock().getLocation());
            plugin.msg().send(p, "corner2_set", Map.of(
                "X", String.valueOf(e.getClickedBlock().getX()),
                "Z", String.valueOf(e.getClickedBlock().getZ())
            ));
            if (isServer) p.sendMessage("§7(Selected with §cSentinel's Scepter§7)");
        }
    }

    public void confirmClaim(Player p) {
        UUID uuid = p.getUniqueId();
        if (!loc1.containsKey(uuid) || !loc2.containsKey(uuid)) {
            plugin.msg().send(p, "must_select");
            return;
        }

        // Determine mode
        boolean isServerClaim = selectionIsServer.getOrDefault(uuid, false);

        Location l1 = loc1.get(uuid);
        Location l2 = loc2.get(uuid);

        if (!l1.getWorld().equals(l2.getWorld())) {
            p.sendMessage(ChatColor.RED + "Corners must be in the same world.");
            return;
        }
        
        // --- REGULAR PLAYER CHECKS (Skip if Server Claim) ---
        if (!isServerClaim) {
            // 1. Check World Rules
            if (!plugin.worldRules().allowClaims(p.getWorld())) {
                plugin.msg().send(p, "admin-zone-no-claims");
                return;
            }

            int minX = Math.min(l1.getBlockX(), l2.getBlockX());
            int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
            int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
            int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

            // 2. Overlap Check
            if (plugin.store().isAreaOverlapping(null, l1.getWorld().getName(), minX, minZ, maxX, maxZ)) {
                p.sendMessage(ChatColor.RED + "You cannot claim here! It overlaps another plot.");
                return;
            }

            // 3. Limits
            int width = maxX - minX + 1;
            int length = maxZ - minZ + 1;
            int radius = Math.max(width, length) / 2;
            int limitRadius = plugin.cfg().getWorldMaxRadius(p.getWorld());
            
            if (radius > limitRadius && !p.hasPermission("aegis.admin.bypass")) {
                p.sendMessage(ChatColor.RED + "Claim too big! Max radius is " + limitRadius + " blocks.");
                return;
            }
            
            if (radius < plugin.cfg().getWorldMinRadius(p.getWorld())) {
                p.sendMessage(ChatColor.RED + "Claim too small.");
                return;
            }

            int maxClaims = plugin.cfg().getWorldMaxClaims(p.getWorld());
            int currentCount = plugin.store().getPlots(p.getUniqueId()).size();
            if (currentCount >= maxClaims && !p.hasPermission("aegis.admin.bypass")) {
                plugin.msg().send(p, "max_claims_reached", Map.of("AMOUNT", String.valueOf(maxClaims)));
                return;
            }

            // 4. Economy
            double cost = plugin.cfg().getWorldVaultCost(p.getWorld());
            if (plugin.cfg().useVault(p.getWorld()) && cost > 0) {
                if (!plugin.vault().charge(p, cost)) {
                    plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(cost)));
                    return;
                }
                plugin.msg().send(p, "cost_deducted", Map.of("AMOUNT", plugin.vault().format(cost)));
            }
        }

        // --- CREATE PLOT ---
        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        Plot plot;
        if (isServerClaim) {
            // SERVER PLOT CONFIG
            plot = new Plot(
                UUID.randomUUID(),
                Plot.SERVER_OWNER_UUID, // Special Owner
                "Server",
                l1.getWorld().getName(),
                minX, minZ, maxX, maxZ,
                System.currentTimeMillis()
            );
            // Server Defaults: LOCKED DOWN
            plot.setFlag("build", false);
            plot.setFlag("pvp", false);
            plot.setFlag("mobs", false);
            plot.setFlag("safe_zone", true); // Mark as safe zone
        } else {
            // PLAYER PLOT CONFIG
            plot = new Plot(
                UUID.randomUUID(),
                p.getUniqueId(),
                p.getName(),
                l1.getWorld().getName(),
                minX, minZ, maxX, maxZ,
                System.currentTimeMillis()
            );
            plugin.worldRules().applyDefaults(plot);
        }

        plugin.store().addPlot(plot);

        if (isServerClaim) {
            p.sendMessage("§a§l[Aegis] §aServer Zone created successfully!");
            p.sendMessage("§7(Type: Protected | Flags: SafeZone=True)");
            plugin.effects().playConfirm(p);
        } else {
            plugin.msg().send(p, "plot_created");
            plugin.effects().playClaimSuccess(p);
        }
        
        loc1.remove(uuid);
        loc2.remove(uuid);
        selectionIsServer.remove(uuid);
        
        // --- WAND CONSUMPTION ---
        // Only consume if it was a PLAYER claim (Admins keep their tools)
        if (!isServerClaim) {
            boolean consume = plugin.cfg().raw().getBoolean("claims.consume_wand_on_claim", true);
            boolean adminBypass = plugin.cfg().raw().getBoolean("claims.admin_keep_wand", true);
            
            if (consume) {
                if (adminBypass && plugin.isAdmin(p)) return;
                consumeWand(p);
            }
        }
    }
    
    public void consumeWand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isWand(hand)) {
            hand.setAmount(hand.getAmount() - 1);
            return;
        }
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (isWand(offhand)) {
            offhand.setAmount(offhand.getAmount() - 1);
            return;
        }
        for (ItemStack item : p.getInventory().getContents()) {
            if (isWand(item)) {
                item.setAmount(item.getAmount() - 1);
                break;
            }
        }
    }
    
    public void manualConsumeWand(Player p) {
        boolean found = false;
        for (ItemStack item : p.getInventory().getContents()) {
            if (isWand(item)) {
                item.setAmount(0);
                found = true;
            }
        }
        if (found) {
            p.sendMessage(ChatColor.GREEN + "Aegis Scepter(s) consumed.");
            plugin.effects().playUnclaim(p);
        } else {
            p.sendMessage(ChatColor.RED + "No Aegis Scepter found in your inventory.");
        }
    }
    
    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
    }

    public void unclaimHere(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }
        
        if (!plot.getOwner().equals(p.getUniqueId()) && !p.hasPermission("aegis.admin")) {
            plugin.msg().send(p, "no_perm");
            return;
        }
        
        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
        plugin.msg().send(p, "plot_unclaimed");
        plugin.effects().playUnclaim(p);
        
        // Refund only for players, not server plots
        if (!plot.isServerZone() && plugin.cfg().raw().getBoolean("claims.per_world." + p.getWorld().getName() + ".refund_on_unclaim", false)) {
             double originalCost = plugin.cfg().getWorldVaultCost(p.getWorld());
             double percent = plugin.cfg().raw().getDouble("claims.per_world." + p.getWorld().getName() + ".refund_percent", 50.0);
             double refund = originalCost * (percent / 100.0);
             if (refund > 0) {
                 plugin.vault().give(p, refund);
                 plugin.msg().send(p, "vault_refund", Map.of("AMOUNT", plugin.vault().format(refund), "PERCENT", String.valueOf(percent)));
             }
        }
    }
    
    public void resizePlot(Player p, String direction, int amount) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }

        double costPerBlock = plugin.cfg().raw().getDouble("resize-cost-per-block", 10.0);
        double totalCost = amount * costPerBlock; 
        
        if (plugin.cfg().useVault(p.getWorld()) && totalCost > 0) {
            if (!plugin.vault().charge(p, totalCost)) {
                plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(totalCost)));
                return;
            }
        }

        int x1 = plot.getX1();
        int z1 = plot.getZ1();
        int x2 = plot.getX2();
        int z2 = plot.getZ2();

        switch (direction.toLowerCase()) {
            case "north": z1 -= amount; break; 
            case "south": z2 += amount; break; 
            case "west":  x1 -= amount; break; 
            case "east":  x2 += amount; break; 
            default: return;
        }
        
        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
        plot.setX1(x1); plot.setX2(x2);
        plot.setZ1(z1); plot.setZ2(z2);
        plugin.store().addPlot(plot);
        plugin.store().setDirty(true);

        plugin.msg().send(p, "resize-success", Map.of("DIRECTION", direction, "AMOUNT", String.valueOf(amount)));
        plugin.effects().playConfirm(p);
    }
}
