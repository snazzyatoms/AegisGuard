package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.*;

final class TravelCommands extends CommandBase {

    TravelCommands(AegisGuard plugin) { super(plugin); }

    void handleSetSpawn(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            plugin.effects().playError(p);
            return;
        }
        if (!plot.isInside(p.getLocation())) {
            sendKey(p, "home-fail-outside", "&c❌ You must be inside your claim to set its home.");
            plugin.effects().playError(p);
            return;
        }

        plot.setSpawnLocation(p.getLocation());

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "home-set-success", "&a✔ Claim home set to your current location.");
        plugin.effects().playConfirm(p);
    }

    void handleHome(Player p) {
        if (!plugin.cfg().allowHomeTeleport()) {
            sendKey(p, "home_teleport_disabled", "&cHome teleport is disabled.");
            return;
        }

        Plot homePlot = plugin.store().getPlotAt(p.getLocation());
        if (homePlot == null || !homePlot.canManage(p, plugin)) {
            List<Plot> plots = plugin.store().getPlots(p.getUniqueId());
            if (plots != null && !plots.isEmpty()) homePlot = plots.get(0);
            else {
                sendKey(p, "no_plot_here", "&c❌ You do not own any plots.");
                plugin.effects().playError(p);
                return;
            }
        }

        if (homePlot.getSpawnLocation() == null) {
            sendKey(p, "home-fail-no-spawn", "&c❌ No home set! Use /ag setspawn.");
            plugin.effects().playError(p);
            return;
        }

        var homeTravel = plugin.safeTravel().travel(p, homePlot.getSpawnLocation(),
                com.aegisguard.travel.SafeTravelService.Kind.HOME);
        if (!homeTravel.isSuccess()) return;
        plugin.effects().playConfirm(p);
    }

    void handleStuck(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "unstuck_not_in_plot", "&cYou are not inside a plot.");
            return;
        }

        Location loc = p.getLocation();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        int dX1 = Math.abs(x - plot.getX1());
        int dX2 = Math.abs(x - plot.getX2());
        int dZ1 = Math.abs(z - plot.getZ1());
        int dZ2 = Math.abs(z - plot.getZ2());

        int min = Math.min(Math.min(dX1, dX2), Math.min(dZ1, dZ2));
        Location target = loc.clone();

        if (min == dX1) target.setX(plot.getX1() - 2);
        else if (min == dX2) target.setX(plot.getX2() + 2);
        else if (min == dZ1) target.setZ(plot.getZ1() - 2);
        else target.setZ(plot.getZ2() + 2);

        World world = loc.getWorld();
        int safeY = com.aegisguard.util.TeleportUtil.highestBlockYOr(
                world, target.getBlockX(), target.getBlockZ(), loc.getBlockY());
        target.setY(safeY + 1);

        var unstuck = plugin.safeTravel().travel(p, target, com.aegisguard.travel.SafeTravelService.Kind.UNSTUCK);
        if (!unstuck.isSuccess()) return;

        sendKey(p, "unstuck_success", "&a✔ Teleported to safety.");
        plugin.effects().playTeleport(p);
    }

    void handleNetworkHub(Player p) {
        if (!plugin.cfg().isTravelSystemEnabled()) {
            sendKey(p, "travel_system_disabled", "&cTravel system is disabled.");
            return;
        }
        var netTravel = plugin.networkTravel();
        if (netTravel != null && netTravel.sendToHub(p)) {
            p.closeInventory();
            return;
        }
        var result = plugin.safeTravel().travel(p, p.getWorld().getSpawnLocation(),
                com.aegisguard.travel.SafeTravelService.Kind.SPAWN);
        if (result.isSuccess()) p.closeInventory();
    }

    public void hub(Player p) {
        handleNetworkHub(p);
    }

    void handleNetworkServers(Player p) {
        var net = plugin.network();
        if (net == null || !net.isNetworked() || plugin.gui().networkServers() == null) {
            sendKey(p, "network_disabled", "&cCross-server networking is not enabled here.");
            return;
        }
        plugin.gui().networkServers().open(p, 0);
    }

    void handleCaravan(Player player, String[] args) {
        if (plugin.caravans() == null || !plugin.caravans().isEnabled()
                || plugin.gui() == null || plugin.gui().caravans() == null) {
            sendKey(player, "caravan_unavailable", "&cCaravans are unavailable.");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "menu";
        switch (action) {
            case "list", "status" -> {
                var owned = plugin.caravans().store().forOwner(player.getUniqueId());
                if (owned.isEmpty()) {
                    sendKey(player, "caravan_none", "&eYou have no caravans. Open &f/ag caravan&e to dispatch one.");
                    plugin.gui().caravans().open(player);
                    return;
                }
                sendKey(player, "caravan_list_header", "&6Your caravans:");
                for (var caravan : owned) {
                    sendMsg(player, "&7- &f" + caravan.routeLabel() + " &8(" + caravan.getStatus().name() + ")");
                }
            }
            case "cancel" -> {
                if (args.length < 3) {
                    sendKey(player, "caravan_cancel_usage", "&eUsage: &f/ag caravan cancel <id>");
                    return;
                }
                try {
                    java.util.UUID id = java.util.UUID.fromString(args[2]);
                    if (!plugin.caravans().cancel(player, id)) {
                        plugin.effects().playError(player);
                    }
                } catch (IllegalArgumentException ignored) {
                    sendKey(player, "caravan_bad_id", "&cThat is not a valid caravan id.");
                }
            }
            default -> plugin.gui().caravans().open(player);
        }
    }

    void handleGathering(Player player, String[] args) {
        if (plugin.gatherings() == null || !plugin.gatherings().isEnabled()
                || plugin.gui() == null || plugin.gui().gatherings() == null) {
            sendKey(player, "gathering_disabled", "&cOpen House is disabled on this server.");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "menu";
        switch (action) {
            case "start" -> {
                int minutes = plugin.gatherings().defaultMinutes();
                if (args.length >= 3) {
                    try {
                        minutes = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                        sendKey(player, "gathering_usage",
                                "&eUsage: /ag gathering start [minutes] | stop | menu");
                        return;
                    }
                }
                plugin.gatherings().start(player, minutes);
            }
            case "stop", "end" -> plugin.gatherings().stop(player);
            default -> plugin.gui().gatherings().open(player);
        }
    }

}