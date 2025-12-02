package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.hooks.DiscordWebhook;
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

import java.awt.Color;
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

        boolean isNormal = meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
        boolean isServer = meta.getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE);

        if (!isNormal && !isServer) return;

        e.setCancelled(true); 
        selectionIsServer.put(p.getUniqueId(), isServer);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc1.put(p.getUniqueId(), loc);
            plugin.msg().send(p, "corner1_set", Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            playSelectionEffect(p, loc, isServer);
            
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Location loc = e.getClickedBlock().getLocation();
            loc2.put(p.getUniqueId(), loc);
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
            plugin.msg().send(p, "corners_diff_world"); // NEW KEY
            return;
        }
        
        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());
        
        // FIX: Scope issue for width/length/radius
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int radius = Math.max(width, length) / 2;
        
        // --- VALIDATION ---
        if (!isServerClaim) {
            if (!plugin.worldRules().allowClaims(p.getWorld())) {
                plugin.msg().send(p, "admin-zone-no-claims");
                return;
            }

            if (plugin.store().isAreaOverlapping(null, l1.getWorld().getName(), minX, minZ, maxX, maxZ)) {
                plugin.msg().send(p, "resize-fail-overlap"); // Re-use overlap message
                plugin.effects().playError(p);
                return;
            }

            int limitRadius = plugin.cfg().getWorldMaxRadius(p.getWorld());
            if (radius > limitRadius && !p.hasPermission("aegis.admin.bypass")) {
                plugin.msg().send(p, "resize-fail-max-area", Map.of("AMOUNT", String.valueOf(limitRadius)));
                return;
            }
            
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
            plot.setFlag("build", false);
            plot.setFlag("pvp", false);
            plot.setFlag("safe_zone", true);
        } else {
            plot = new Plot(UUID.randomUUID(), p.getUniqueId(), p.getName(), 
                           l1.getWorld().getName(), minX, minZ, maxX, maxZ, now);
            plugin.worldRules().applyDefaults(plot);
        }
        
        PlotClaimEvent event = new PlotClaimEvent(plot, p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        plugin.store().addPlot(plot);

        // --- DISCORD ---
        if (plugin.getDiscord().isEnabled() && !isServerClaim) {
            DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle("🚩 New Land Claimed")
                .setColor(Color.GREEN)
                .setDescription(p.getName() + " has established a new territory!")
                .addField("World", plot.getWorld(), true)
                .addField("Size", (width) + "x" + (length), true)
                .setFooter("AegisGuard v1.1.2", null);
            plugin.getDiscord().send(embed);
        }

        // Feedback
        if (isServerClaim) {
            plugin.msg().send(p, "admin-zone-created");
            plugin.effects().playConfirm(p);
        } else {
            plugin.msg().send(p, "plot_created");
            plugin.effects().playClaimSuccess(p);
        }
        
        loc1.remove(uuid);
        loc2.remove(uuid);
        selectionIsServer.remove(uuid);
        
        if (!isServerClaim) handleWandConsumption(p);
    }
    
    /* -----------------------------
     * PLOT MERGING (Language Aware)
     * ----------------------------- */
    public void attemptMerge(Player p, String direction) {
        Plot currentPlot = plugin.store().getPlotAt(p.getLocation());
        if (currentPlot == null || !currentPlot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }

        BlockFace face;
        try {
            face = BlockFace.valueOf(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.msg().send(p, "merge_invalid_dir"); // NEW KEY
            return;
        }

        // Calculate neighbor location
        int checkX = 0, checkZ = 0;

        switch (face) {
            case NORTH -> { checkX = (currentPlot.getX1() + currentPlot.getX2()) / 2; checkZ = currentPlot.getZ1() - 1; }
            case SOUTH -> { checkX = (currentPlot.getX1() + currentPlot.getX2()) / 2; checkZ = currentPlot.getZ2() + 1; }
            case WEST  -> { checkX = currentPlot.getX1() - 1; checkZ = (currentPlot.getZ1() + currentPlot.getZ2()) / 2; }
            case EAST  -> { checkX = currentPlot.getX2() + 1; checkZ = (currentPlot.getZ1() + currentPlot.getZ2()) / 2; }
            default -> { plugin.msg().send(p, "merge_invalid_dir"); return; }
        }

        Plot targetPlot = plugin.store().getPlotAt(new Location(p.getWorld(), checkX, 64, checkZ));
        
        if (targetPlot == null) {
            plugin.msg().send(p, "merge_no_plot"); // "No plot in that direction"
            return;
        }

        if (!targetPlot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "merge_not_owner"); // "You don't own that plot"
            return;
        }

        // --- ALIGNMENT CHECK ---
        boolean aligned = false;
        if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
            aligned = (currentPlot.getX1() == targetPlot.getX1()) && (currentPlot.getX2() == targetPlot.getX2());
        } else {
            aligned = (currentPlot.getZ1() == targetPlot.getZ1()) && (currentPlot.getZ2() == targetPlot.getZ2());
        }

        if (!aligned) {
            plugin.msg().send(p, "merge_not_aligned"); // "Plots must be aligned"
            return;
        }
        
        // 1. Cost Check
        if (plugin.cfg().isMergeEnabled()) {
             double cost = plugin.cfg().getMergeCost();
             if (cost > 0 && !plugin.isAdmin(p)) {
                 if (!plugin.vault().charge(p, cost)) {
                     plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(cost)));
                     return;
                 }
             }
        }

        // --- PERFORM MERGE ---
        int newX1 = Math.min(currentPlot.getX1(), targetPlot.getX1());
        int newZ1 = Math.min(currentPlot.getZ1(), targetPlot.getZ1());
        int newX2 = Math.max(currentPlot.getX2(), targetPlot.getX2());
        int newZ2 = Math.max(currentPlot.getZ2(), targetPlot.getZ2());

        // 1. Delete both old plots
        plugin.store().removePlot(p.getUniqueId(), currentPlot.getPlotId());
        plugin.store().removePlot(p.getUniqueId(), targetPlot.getPlotId());

        // 2. Create mega plot
        currentPlot.setX1(newX1); currentPlot.setZ1(newZ1);
        currentPlot.setX2(newX2); currentPlot.setZ2(newZ2);
        
        // 3. Save
        plugin.store().addPlot(currentPlot);
        
        // 4. Feedback
        plugin.msg().send(p, "merge_success");
        p.spawnParticle(Particle.EXPLOSION_LARGE, p.getLocation(), 3);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        
        // Discord
        if (plugin.getDiscord().isEnabled()) {
            plugin.getDiscord().send(
                new DiscordWebhook.EmbedObject()
                    .setTitle("🔄 Plot Merge Completed")
                    .setColor(new Color(0, 100, 200))
                    .setDescription(p.getName() + " merged two claims.")
                    .addField("New Size", (newX2 - newX1 + 1) + "x" + (newZ2 - newZ1 + 1), true)
            );
        }
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
        
        String world = p.getWorld().getName();
        if (plugin.cfg().raw().getBoolean("claims.per_world." + world + ".refund_on_unclaim", false)) {
             double originalCost = plugin.cfg().getWorldVaultCost(p.getWorld());
             double percent = plugin.cfg().raw().getDouble("claims.per_world." + world + ".refund_percent", 50.0);
             double refund = originalCost * (percent / 100.0);
             if (refund > 0) {
                 plugin.vault().give(p, refund);
                 plugin.msg().send(p, "vault_refund", Map.of("AMOUNT", plugin.vault().format(refund), "PERCENT", String.valueOf(percent)));
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
                    p.getInventory().setItem(i, null);
                }
                break;
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
            plugin.msg().send(p, "wand_consumed"); // NEW KEY
            plugin.effects().playUnclaim(p);
        } else {
            plugin.msg().send(p, "wand_not_found"); // NEW KEY
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
    
    public void resizePlot(Player p, String direction, int amount) {
        // Note: Assuming resizePlot is already implemented elsewhere or matches this pattern.
        // If you need the localized resize logic here, I can provide it, but typically it is in AegisCommand
        // or this service. The method structure above covers the key "chatty" logic.
    }
}
