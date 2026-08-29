package com.aegisguard.caravans;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotDeleteEvent;
import com.aegisguard.beacon.TeleportBeacon;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Resume notifications, refund in-flight caravans when a route pad is destroyed,
 * and drop escort status if the escort logs out.
 */
public final class CaravanListener implements Listener {

    private final AegisGuard plugin;

    public CaravanListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private CaravanService caravans() {
        return plugin.caravans();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        CaravanService service = caravans();
        if (service == null || !service.isEnabled()) return;
        Player player = event.getPlayer();
        plugin.runMain(player, () -> service.notifyPending(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CaravanService service = caravans();
        if (service == null || !service.isEnabled()) return;
        java.util.UUID id = event.getPlayer().getUniqueId();
        boolean changed = false;
        for (Caravan caravan : service.store().inFlight()) {
            if (id.equals(caravan.getEscortId())) {
                caravan.setEscortId(null);
                service.store().markDirty();
                changed = true;
            }
        }
        if (changed) service.save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        abortAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlotDelete(PlotDeleteEvent event) {
        CaravanService service = caravans();
        if (service == null || !service.isEnabled() || plugin.beacons() == null) return;
        if (event.getPlot() == null) return;
        for (TeleportBeacon beacon : plugin.beacons().store().forPlot(event.getPlot().getPlotId())) {
            service.abortForBeacon(beacon.getId(), "route_closed");
        }
    }

    private void abortAt(Block block) {
        CaravanService service = caravans();
        if (service == null || !service.isEnabled() || plugin.beacons() == null || block == null) return;
        TeleportBeacon beacon = plugin.beacons().getAt(block.getLocation());
        if (beacon == null) return;
        service.abortForBeacon(beacon.getId(), "route_closed");
    }
}
