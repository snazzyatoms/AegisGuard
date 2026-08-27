package com.aegisguard.beacon;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotDeleteEvent;
import com.aegisguard.data.Plot;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public final class BeaconListener implements Listener {

    private final AegisGuard plugin;

    public BeaconListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private BeaconService beacons() {
        return plugin.beacons();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        BeaconService service = beacons();
        if (service == null || !service.isEnabled()) return;
        Player player = event.getPlayer();
        if (player.isSneaking() || player.isDead() || !player.isOnline()) return;
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (hasBlockingInventory(player)) return;
        if (service.recentlyTraveled(player)) return;
        TeleportBeacon origin = service.nearest(to);
        if (origin == null || !origin.isLinked()) return;
        if (!service.shouldPrompt(player)) return;
        if (!service.canDepart(player, origin)) return;
        service.openPadConfirm(player, origin);
    }

    private boolean hasBlockingInventory(Player player) {
        try {
            InventoryType type = player.getOpenInventory().getType();
            return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE;
        } catch (Throwable ignored) {
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        BeaconService service = beacons();
        if (service == null || !service.isEnabled()) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !service.isPadMaterial(block.getType())) return;
        Player player = event.getPlayer();
        Plot plot = plugin.store().getPlotAt(block.getLocation());
        if (plot == null) return;
        if (hasBlockingInventory(player)) return;

        TeleportBeacon existing = service.getAt(block.getLocation());
        if (player.isSneaking() && plot.canManage(player, plugin)) {
            event.setCancelled(true);
            if (existing == null) {
                if (service.store().forPlot(plot.getPlotId()).size() >= service.maxFor(plot)) {
                    service.send(player, "beacon_at_cap", "&cThis plot already has the maximum number of beacons.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                    return;
                }
                TeleportBeacon created = service.create(player, plot, block);
                if (created == null) return;
                service.store().save();
                service.send(player, "beacon_created",
                        "&aBeacon created. Pick a preset, then link it to another pad.");
                plugin.gui().beacons().openSetup(player, created);
            } else {
                plugin.gui().beacons().openEdit(player, existing);
            }
            return;
        }
        if (existing != null && existing.isLinked()) {
            event.setCancelled(true);
            service.openPadConfirm(player, existing);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BeaconService service = beacons();
        if (service == null || !service.isEnabled()) return;
        TeleportBeacon beacon = service.getAt(event.getBlock().getLocation());
        if (beacon == null) return;
        Player player = event.getPlayer();
        if (!service.canManage(player, beacon)) {
            event.setCancelled(true);
            service.send(player, "beacon_break_denied", "&cYou cannot break this teleport beacon.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        service.store().remove(beacon.getId());
        service.send(player, "beacon_removed", "&eBeacon unbound. Linked pads that pointed here were cleared.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        unbindExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        unbindExploded(event.blockList());
    }

    private void unbindExploded(java.util.List<Block> blocks) {
        BeaconService service = beacons();
        if (service == null || !service.isEnabled() || blocks == null) return;
        for (Block block : blocks) {
            if (block == null) continue;
            TeleportBeacon beacon = service.getAt(block.getLocation());
            if (beacon != null) service.store().remove(beacon.getId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        cancelIfMovesBeacon(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        cancelIfMovesBeacon(event.getBlocks(), event);
    }

    private void cancelIfMovesBeacon(java.util.List<Block> blocks, org.bukkit.event.Cancellable event) {
        BeaconService service = beacons();
        if (service == null || !service.isEnabled() || blocks == null) return;
        for (Block block : blocks) {
            if (block != null && service.getAt(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlotDelete(PlotDeleteEvent event) {
        BeaconService service = beacons();
        if (service == null || event.getPlot() == null) return;
        UUID plotId = event.getPlot().getPlotId();
        if (plugin.scheduler() != null) {
            plugin.scheduler().runGlobal(() -> service.removeForPlot(plotId));
        } else {
            service.removeForPlot(plotId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        BeaconService service = beacons();
        if (service == null || !service.isEnabled()) return;
        Player player = event.getPlayer();
        if (!service.hasPendingRename(player)) return;
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.scheduler().runEntity(player, () -> service.handleRenameChat(player, message), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BeaconService service = beacons();
        if (service == null) return;
        service.clearPlayerState(event.getPlayer());
    }
}
