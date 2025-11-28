package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import com.aegisguard.data.Plot;
import org.bukkit.*;
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
    
    // Selection Cache
    private final Map<UUID, Location> loc1 = new HashMap<>();
    private final Map<UUID, Location> loc2 = new HashMap<>();
    private final Map<UUID, Boolean> selectionIsServer = new HashMap<>();
    
    // NBT Keys
    public static final NamespacedKey WAND_KEY = new NamespacedKey("aegisguard", "wand");
    public static final NamespacedKey SERVER_WAND_KEY = new NamespacedKey("aegisguard", "server_wand");

    // Dependency Injection
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
        
        // Fast fail checks
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Check Wand Type via NBT
        boolean isNormal = meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
        boolean isServer = meta.getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE);

        if (!isNormal && !isServer) return;

        e.setCancelled(true); // Prevent breaking blocks with wand

        // Update Selection Mode
        selectionIsServer.put(p.getUniqueId(), isServer);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc1.put(p.getUniqueId(), loc);
            
            // Visuals & Feedback
            plugin.msg().send(p, "corner1_set", Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            playSelectionEffect(p, loc, isServer);
            
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc2.put(p.getUniqueId(), loc);
            
            // Visuals & Feedback
            plugin.msg().send(p, "corner2_set", Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            playSelectionEffect(p, loc, isServer);
        }
    }

    public void confirmClaim(Player p) {
        UUID uuid = p.getUniqueId();
        
        if (!hasSelection(p)) {
            plugin.msg().send(p, "must_select");
            plugin.effects().playError(p);
            return;
        }

        boolean isServerClaim = selectionIsServer.getOrDefault(uuid, false);
        Location l1 = loc1.get(uuid);
        Location l2 = loc2.get(uuid);

        if (!l1.getWorld().equals(l2.getWorld())) {
            p.sendMessage(ChatColor.RED + "Corners must be in the same world.");
            return;
        }
        
        // Calculate Bounds
        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());
        
        // --- VALIDATION CHECKS ---
        if (!isServerClaim) {
            // 1. World Disabled?
            if (!plugin.worldRules().allowClaims(p.getWorld())) {
                plugin.msg().send(p, "admin-zone-no-claims");
                return;
            }

            // 2. Overlap Check
            if (plugin.store().isAreaOverlapping(null, l1.getWorld().getName(), minX, minZ, maxX, maxZ)) {
                p.sendMessage(ChatColor.RED + "You cannot claim here! It overlaps another plot.");
                plugin.effects().playError(p);
                return;
            }

            // 3. Size Limits
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

            // 4. Max Claims Count
            int maxClaims = plugin.cfg().getWorldMaxClaims(p.getWorld());
            int currentCount = plugin.store().getPlots(p.getUniqueId()).size();
            if (currentCount >= maxClaims && !p.hasPermission("aegis.admin.bypass")) {
                plugin.msg().send(p, "max_claims_reached", Map.of("AMOUNT", String.valueOf(maxClaims)));
                return;
            }

            // 5. Economy Check
            double cost = plugin.cfg().getWorldVaultCost(p.getWorld());
            if (plugin.cfg().useVault(p.getWorld()) && cost > 0) {
                if (!plugin.vault().charge(p, cost)) {
                    plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(cost)));
                    return;
                }
                plugin.msg().send(p, "cost_deducted", Map.of("AMOUNT", plugin.vault().format(cost)));
            }
        }

        // --- CREATION ---
        Plot plot;
        long now = System.currentTimeMillis();
        
        if (isServerClaim) {
            plot = new Plot(UUID.randomUUID(), Plot.SERVER_OWNER_UUID, "Server", 
                           l1.getWorld().getName(), minX, minZ, maxX, maxZ, now);
            // Lock down server plots by default
            plot.setFlag("build", false);
            plot.setFlag("pvp", false);
            plot.setFlag("mobs", false);
            plot.setFlag("safe_zone", true);
        } else {
            plot = new Plot(UUID.randomUUID(), p.getUniqueId(), p.getName(), 
                           l1.getWorld().getName(), minX, minZ, maxX, maxZ, now);
            plugin.worldRules().applyDefaults(plot);
        }
        
        // Fire Event
        PlotClaimEvent event = new PlotClaimEvent(plot, p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        // Save
        plugin.store().addPlot(plot);

        // Feedback
        if (isServerClaim) {
            p.sendMessage("§a§l[Aegis] §aServer Zone created successfully!");
            plugin.effects().playConfirm(p);
        } else {
            plugin.msg().send(p, "plot_created");
            plugin.effects().playClaimSuccess(p);
        }
        
        // Cleanup
        loc1.remove(uuid);
        loc2.remove(uuid);
        selectionIsServer.remove(uuid);
        
        // Consume Wand (Player Only)
        if (!isServerClaim) handleWandConsumption(p);
    }
    
    // --- RESIZE LOGIC ---
    public void resizePlot(Player p, String direction, int amount) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }

        // 1. Calculate Cost
        double costPerBlock = plugin.cfg().raw().getDouble("resize-cost-per-block", 10.0);
        double totalCost = amount * costPerBlock; 
        
        if (plugin.cfg().useVault(p.getWorld()) && totalCost > 0) {
            if (!plugin.vault().has(p, totalCost)) {
                plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(totalCost)));
                return;
            }
        }

        // 2. Calculate New Coordinates
        int x1 = plot.getX1();
        int z1 = plot.getZ1();
        int x2 = plot.getX2();
        int z2 = plot.getZ2();

        switch (direction.toLowerCase()) {
            case "north" -> z1 -= amount;
            case "south" -> z2 += amount;
            case "west"  -> x1 -= amount;
            case "east"  -> x2 += amount;
            default -> { return; }
        }
        
        // 3. CRITICAL: Check for Overlap BEFORE resizing
        // We pass the current plot ID to ignore it in the overlap check (so it doesn't overlap itself)
        if (plugin.store().isAreaOverlapping(plot.getPlotId(), plot.getWorldName(), x1, z1, x2, z2)) {
            p.sendMessage(ChatColor.RED + "Cannot resize: You would overlap another plot!");
            plugin.effects().playError(p);
            return;
        }

        // 4. Charge Money
        if (totalCost > 0) {
            plugin.vault().charge(p, totalCost);
        }

        // 5. Commit Changes
        // Note: Ideally, updatePlot(plot) is better than remove/add, but sticking to your API pattern:
        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
        
        plot.setX1(x1); plot.setX2(x2);
        plot.setZ1(z1); plot.setZ2(z2);
        
        plugin.store().addPlot(plot);
        plugin.store().setDirty(true);

        plugin.msg().send(p, "resize-success", Map.of("DIRECTION", direction, "AMOUNT", String.valueOf(amount)));
        plugin.effects().playConfirm(p);
    }

    // --- UTILITIES ---

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
        
        // Refund Logic
        String world = p.getWorld().getName();
        if (plugin.cfg().raw().getBoolean("claims.per_world." + world + ".refund_on_unclaim", false)) {
             double originalCost = plugin.cfg().getWorldVaultCost(p.getWorld());
             double percent = plugin.cfg().raw().getDouble("claims.per_world." + world + ".refund_percent", 50.0);
             double refund = originalCost * (percent / 100.0);
             
             if (refund > 0) {
                 plugin.vault().give(p, refund);
                 plugin.msg().send(p, "vault_refund", Map.of(
                     "AMOUNT", plugin.vault().format(refund), 
                     "PERCENT", String.valueOf(percent)
                 ));
             }
        }
    }

    private void handleWandConsumption(Player p) {
        boolean consume = plugin.cfg().raw().getBoolean("claims.consume_wand_on_claim", true);
        boolean adminBypass = plugin.cfg().raw().getBoolean("claims.admin_keep_wand", true);
        
        if (consume) {
            if (adminBypass && plugin.isAdmin(p)) return;
            consumeWand(p);
        }
    }

    public void consumeWand(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isWand(contents[i])) {
                if (contents[i].getAmount() > 1) {
                    contents[i].setAmount(contents[i].getAmount() - 1);
                } else {
                    p.getInventory().setItem(i, null); // Remove completely
                }
                break; // Only consume one
            }
        }
    }
    
    public void manualConsumeWand(Player p) {
        boolean found = false;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isWand(contents[i])) {
                p.getInventory().setItem(i, null);
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
    
    private void playSelectionEffect(Player p, Location loc, boolean isServer) {
        try {
            // Visualize the corner
            Particle particle = isServer ? Particle.SOUL_FIRE_FLAME : Particle.VILLAGER_HAPPY;
            p.spawnParticle(particle, loc.clone().add(0.5, 1.2, 0.5), 5, 0.2, 0.2, 0.2, 0);
            
            // Play a sound
            p.playSound(p.getLocation(), isServer ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        } catch (Exception ignored) {
            // Older version fallback or error
        }
    }
}
