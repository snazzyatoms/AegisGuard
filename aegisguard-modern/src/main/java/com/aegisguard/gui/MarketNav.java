package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Shared Back routing for Local Market and the screens it opens.
 * Destinations are simple tokens, optionally nested as {@code parent:child}.
 */
public final class MarketNav {

    public static final String MAIN = "main";
    public static final String LOCAL_MARKET = "local_market";
    public static final String MY_RENTALS = "my_rentals";
    public static final String ZONE_BROWSE = "zone_browse";
    public static final String ZONING = "zoning";
    public static final String MY_TENANTS = "my_tenants";
    public static final String PLOT_MARKET = "plot_market";
    public static final String STALL_LIST = "stall_list";
    public static final String STALL_PREVIEW = "stall_preview";

    private MarketNav() {}

    public static String normalize(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) return MAIN;
        return returnTo.trim().toLowerCase(Locale.ROOT);
    }

    public static String nest(String parent, String returnTo) {
        return normalize(parent) + ":" + normalize(returnTo);
    }

    public static void back(AegisGuard plugin, Player player, String returnTo, Plot plot) {
        if (player == null || plugin == null || plugin.gui() == null) return;
        String dest = normalize(returnTo);
        int colon = dest.indexOf(':');
        String head = colon < 0 ? dest : dest.substring(0, colon);
        String rest = colon < 0 ? MAIN : dest.substring(colon + 1);

        switch (head) {
            case LOCAL_MARKET -> {
                if (plot != null) plugin.gui().localMarket().open(player, plot);
                else plugin.gui().openMain(player);
            }
            case ZONING -> {
                if (plot != null) plugin.gui().zoning().open(player, plot, rest);
                else plugin.gui().openMain(player);
            }
            case ZONE_BROWSE -> {
                if (plot != null) plugin.gui().zoneBrowse().open(player, plot, rest);
                else plugin.gui().openMain(player);
            }
            case MY_RENTALS -> plugin.gui().myRentals().openFrom(player, 0, rest, plot);
            case MY_TENANTS -> plugin.gui().myTenants().openFrom(player, rest, plot);
            case PLOT_MARKET -> plugin.gui().market().open(player, 0, plotFrom(rest, plot));
            case STALL_LIST -> {
                if (plot != null) plugin.gui().stallBrowse().openList(player, plot);
                else plugin.gui().openMain(player);
            }
            default -> plugin.gui().openMain(player);
        }
    }

    public static Plot findPlot(AegisGuard plugin, UUID plotId) {
        if (plugin == null || plugin.store() == null || plotId == null) return null;
        return plugin.store().getAllPlots().stream()
                .filter(p -> p != null && plotId.equals(p.getPlotId()))
                .findFirst()
                .orElse(null);
    }

    private static Plot plotFrom(String ignored, Plot fallback) {
        return fallback;
    }
}
