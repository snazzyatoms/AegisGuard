package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.guidance.DenialGuidance;
import com.aegisguard.hooks.protection.HookAction;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.Iterator;

public class BlockProtectionListener implements Listener {

    private final AegisGuard plugin;

    public BlockProtectionListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (plugin.isBypassing(e.getPlayer())) return;
        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getBlock().getLocation(), e.getPlayer(), HookAction.BLOCK_BREAK)) {
            return;
        }

        if (!plot.canBuildAt(e.getPlayer(), e.getBlock().getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (plugin.isBypassing(e.getPlayer())) return;
        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getBlock().getLocation(), e.getPlayer(), HookAction.BLOCK_PLACE)) {
            return;
        }

        if (!plot.canBuildAt(e.getPlayer(), e.getBlock().getLocation(), plugin, "BLOCK_PLACE")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_PLACE", "cannot_place");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null) return;

        Player player = e.getPlayer();
        if (plugin.isBypassing(player)) return;

        if (e.getAction() == Action.PHYSICAL && clicked.getType() == Material.FARMLAND) {
            if (plugin.protection().isFlagEnabled(plot, "farm")
                    && !plot.canInteractAt(player, clicked.getLocation(), plugin, "FARM")) {
                e.setCancelled(true);
                DenialGuidance.send(plugin, player, plot, "FARM", "cannot_interact");
                plugin.effects().playError(player);
            }
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(clicked.getLocation(), player, HookAction.CONTAINER_INTERACT)) {
            return;
        }

        if (isContainer(clicked) && plugin.protection().isFlagEnabled(plot, "containers")
                && !plot.canInteractAt(player, clicked.getLocation(), plugin, "CONTAINERS")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "CONTAINERS", "cannot_interact");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onItemFrameInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (!(clicked instanceof ItemFrame)) return;

        Player player = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null || plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(clicked.getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (!canUseDecorativeEntity(plot, player, clicked.getLocation())) {
            e.setCancelled(true);
            denyInteract(player, plot);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        Player player = e.getPlayer();
        ArmorStand stand = e.getRightClicked();

        Plot plot = plugin.store().getPlotAt(stand.getLocation());
        if (plot == null || plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(stand.getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (!canUseDecorativeEntity(plot, player, stand.getLocation())) {
            e.setCancelled(true);
            denyInteract(player, plot);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlockClicked().getRelative(e.getBlockFace()).getLocation());
        if (plot == null || plugin.isBypassing(e.getPlayer())) return;
        if (!plot.canBuildAt(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()).getLocation(), plugin, "BLOCK_PLACE")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_PLACE", "cannot_place");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlockClicked().getLocation());
        if (plot == null || plugin.isBypassing(e.getPlayer())) return;
        if (!plot.canBuildAt(e.getPlayer(), e.getBlockClicked().getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        filterExplodedBlocks(e.blockList().iterator(), "tnt-damage");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        filterExplodedBlocks(e.blockList().iterator(), "tnt-damage");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockIgnite(BlockIgniteEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockSpread(BlockSpreadEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (e.getSource().getType() != Material.FIRE) return;
        if (plugin.protection().isFlagEnabled(plot, "fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBurn(BlockBurnEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (shouldCancelPiston(e.getBlock(), e.getDirection(), e.getBlocks())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (shouldCancelPiston(e.getBlock(), e.getDirection(), e.getBlocks())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player player = e.getPlayer();
        if (player == null) return;

        Hanging hanging = e.getEntity();
        Plot plot = plugin.store().getPlotAt(hanging.getLocation());
        if (plot == null || plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(hanging.getLocation(), player, HookAction.BLOCK_PLACE)) {
            return;
        }

        if (!plot.canBuildAt(player, hanging.getLocation(), plugin, "BLOCK_PLACE")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "BLOCK_PLACE", "cannot_place");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Hanging hanging = e.getEntity();
        Plot plot = plugin.store().getPlotAt(hanging.getLocation());
        if (plot == null) return;

        Player player = resolvePlayer(e.getRemover());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(hanging.getLocation(), player, HookAction.BLOCK_BREAK)) {
            return;
        }

        if (!plot.canBuildAt(player, hanging.getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreakOther(HangingBreakEvent e) {
        if (e instanceof HangingBreakByEntityEvent) return;

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;

        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ArmorStand stand)) return;

        Plot plot = plugin.store().getPlotAt(stand.getLocation());
        if (plot == null) return;

        Player player = resolvePlayer(e.getDamager());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(stand.getLocation(), player, HookAction.BLOCK_BREAK)) {
            return;
        }

        if (!plot.canBuildAt(player, stand.getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onVehicleDamage(VehicleDamageEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getVehicle().getLocation());
        if (plot == null) return;
        if (!plugin.protection().isFlagEnabled(plot, "vehicles")) return;

        Player player = resolvePlayer(e.getAttacker());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getVehicle().getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (!plot.canInteractAt(player, e.getVehicle().getLocation(), plugin, "VEHICLES")) {
            e.setCancelled(true);
            denyInteract(player, plot, "VEHICLES");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getVehicle().getLocation());
        if (plot == null) return;
        if (!plugin.protection().isFlagEnabled(plot, "vehicles")) return;

        Player player = resolvePlayer(e.getAttacker());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getVehicle().getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (!plot.canInteractAt(player, e.getVehicle().getLocation(), plugin, "VEHICLES")) {
            e.setCancelled(true);
        }
    }

    private void filterExplodedBlocks(Iterator<Block> iterator, String flag) {
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot != null && (plugin.protection().isFlagEnabled(plot, flag) || plugin.protection().isFlagEnabled(plot, "explosions"))) {
                iterator.remove();
            }
        }
    }

    private boolean shouldCancelPiston(Block piston, BlockFace direction, Iterable<Block> movedBlocks) {
        Plot pistonPlot = plugin.store().getPlotAt(piston.getLocation());
        if (pistonPlot != null && plugin.protection().isFlagEnabled(pistonPlot, "piston-use")) {
            return true;
        }

        for (Block moved : movedBlocks) {
            Plot fromPlot = plugin.store().getPlotAt(moved.getLocation());
            Plot toPlot = plugin.store().getPlotAt(moved.getRelative(direction).getLocation());
            if ((fromPlot != null && plugin.protection().isFlagEnabled(fromPlot, "piston-use"))
                    || (toPlot != null && plugin.protection().isFlagEnabled(toPlot, "piston-use"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isContainer(Block block) {
        if (block == null) return false;
        BlockState state = block.getState();
        return state instanceof InventoryHolder;
    }

    private boolean canUseDecorativeEntity(Plot plot, Player player, org.bukkit.Location location) {
        return plot.canInteractAt(player, location, plugin, "INTERACT");
    }

    private void denyInteract(Player player, Plot plot) {
        denyInteract(player, plot, "INTERACT");
    }

    private void denyInteract(Player player, Plot plot, String permission) {
        DenialGuidance.send(plugin, player, plot, permission, "cannot_interact");
        plugin.effects().playError(player);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
