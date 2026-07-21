package com.aegisguard.api.internal;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.AegisGuardAPI;
import com.aegisguard.api.service.ClaimBlockAccess;
import com.aegisguard.api.service.EconomyAccess;
import com.aegisguard.api.service.PlotAccess;
import com.aegisguard.api.service.ProtectionAccess;
import com.aegisguard.api.service.SelectionAccess;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.selection.Selection;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class DefaultAegisGuardAPI implements AegisGuardAPI {

    private final AegisGuard plugin;
    private final PlotAccess plots;
    private final ClaimBlockAccess claimBlocks;
    private final EconomyAccess economy;
    private final SelectionAccess selections;
    private final ProtectionAccess protection;

    public DefaultAegisGuardAPI(AegisGuard plugin) {
        this.plugin = plugin;
        this.plots = new PlotAccessImpl(plugin);
        this.claimBlocks = new ClaimBlockAccessImpl(plugin);
        this.economy = new EconomyAccessImpl(plugin);
        this.selections = new SelectionAccessImpl(plugin);
        this.protection = new ProtectionAccessImpl(plugin);
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public String version() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public PlotAccess plots() {
        return plots;
    }

    @Override
    public ClaimBlockAccess claimBlocks() {
        return claimBlocks;
    }

    @Override
    public EconomyAccess economy() {
        return economy;
    }

    @Override
    public SelectionAccess selections() {
        return selections;
    }

    @Override
    public ProtectionAccess protection() {
        return protection;
    }

    private static final class PlotAccessImpl implements PlotAccess {

        private final AegisGuard plugin;

        private PlotAccessImpl(AegisGuard plugin) {
            this.plugin = plugin;
        }

        @Override
        public List<Plot> getPlots(UUID ownerId) {
            return plugin.store().getPlots(ownerId);
        }

        @Override
        public @Nullable Plot getPlot(UUID ownerId, UUID plotId) {
            return plugin.store().getPlot(ownerId, plotId);
        }

        @Override
        public @Nullable Plot getPlotById(UUID plotId) {
            if (plotId == null) {
                return null;
            }
            for (Plot plot : plugin.store().getAllPlots()) {
                if (plot != null && plotId.equals(plot.getPlotId())) {
                    return plot;
                }
            }
            return null;
        }

        @Override
        public Collection<Plot> getAllPlots() {
            return plugin.store().getAllPlots();
        }

        @Override
        public Collection<Plot> getPlotsForSale() {
            return plugin.store().getPlotsForSale();
        }

        @Override
        public Collection<Plot> getPlotsForAuction() {
            return plugin.store().getPlotsForAuction();
        }

        @Override
        public Collection<Plot> getPlotsInWorld(String worldName) {
            return plugin.store().getPlotsInWorld(worldName);
        }

        @Override
        public @Nullable Plot getPlotAt(Location location) {
            return plugin.store().getPlotAt(location);
        }

        @Override
        public boolean isAreaOverlapping(@Nullable Plot ignore, String worldName, int x1, int z1, int x2, int z2) {
            return plugin.store().isAreaOverlapping(ignore, worldName, x1, z1, x2, z2);
        }

        @Override
        public void savePlot(Plot plot) {
            plugin.store().savePlot(plot);
        }

        @Override
        public void savePlotSync(Plot plot) {
            plugin.store().savePlotSync(plot);
        }
    }

    private static final class ClaimBlockAccessImpl implements ClaimBlockAccess {

        private final AegisGuard plugin;

        private ClaimBlockAccessImpl(AegisGuard plugin) {
            this.plugin = plugin;
        }

        @Override
        public long getTotalBlocks(UUID playerId) {
            return plugin.claimBlocks().getTotalBlocks(playerId);
        }

        @Override
        public long getUsedBlocks(UUID playerId) {
            return plugin.claimBlocks().getUsedBlocks(playerId);
        }

        @Override
        public long getSpentBlocks(UUID playerId) {
            return plugin.claimBlocks().getSpentBlocks(playerId);
        }

        @Override
        public long getAvailableBlocks(UUID playerId) {
            return plugin.claimBlocks().getAvailableBlocks(playerId);
        }

        @Override
        public void invalidateOwnerCache(UUID playerId) {
            plugin.claimBlocks().invalidateOwnerCache(playerId);
        }

        @Override
        public boolean canAfford(UUID playerId, long amount) {
            return plugin.claimBlocks().canAfford(playerId, amount);
        }

        @Override
        public boolean spend(UUID playerId, long amount) {
            return plugin.claimBlocks().spend(playerId, amount);
        }

        @Override
        public void refund(UUID playerId, long amount) {
            plugin.claimBlocks().refund(playerId, amount);
        }

        @Override
        public void addEarned(UUID playerId, long amount) {
            plugin.claimBlocks().addEarned(playerId, amount);
        }

        @Override
        public void addBonus(UUID playerId, long amount) {
            plugin.claimBlocks().addBonus(playerId, amount);
        }

        @Override
        public void addBought(UUID playerId, long amount) {
            plugin.claimBlocks().addBought(playerId, amount);
        }

        @Override
        public void addBoughtFromExchange(UUID playerId, long amount) {
            plugin.claimBlocks().addBoughtFromExchange(playerId, amount);
        }

        @Override
        public boolean isPlaytimeEarningEnabled(UUID playerId) {
            return plugin.claimBlocks().isPlaytimeEarningEnabled(playerId);
        }

        @Override
        public void setPlaytimeEarningEnabled(UUID playerId, boolean enabled) {
            plugin.claimBlocks().setPlaytimeEarningEnabled(playerId, enabled);
        }
    }

    private static final class EconomyAccessImpl implements EconomyAccess {

        private final AegisGuard plugin;

        private EconomyAccessImpl(AegisGuard plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean isVaultReady() {
            return plugin.eco().isVaultReady();
        }

        @Override
        public boolean has(Player player, double amount, CurrencyType type) {
            return plugin.eco().has(player, amount, type);
        }

        @Override
        public boolean withdraw(Player player, double amount, CurrencyType type) {
            return plugin.eco().withdraw(player, amount, type);
        }

        @Override
        public void deposit(Player player, double amount, CurrencyType type) {
            plugin.eco().deposit(player, amount, type);
        }

        @Override
        public double getBalance(Player player, CurrencyType type) {
            return plugin.eco().getBalance(player, type);
        }

        @Override
        public String format(double amount, CurrencyType type) {
            return plugin.eco().format(amount, type);
        }
    }

    private static final class SelectionAccessImpl implements SelectionAccess {

        private final AegisGuard plugin;

        private SelectionAccessImpl(AegisGuard plugin) {
            this.plugin = plugin;
        }

        @Override
        public @Nullable Selection get(UUID playerId) {
            return plugin.selection().get(playerId);
        }

        @Override
        public boolean hasSelection(Player player) {
            return plugin.selection().hasSelection(player);
        }

        @Override
        public long getSelectionArea(Player player) {
            return plugin.selection().getSelectionArea(player);
        }

        @Override
        public void clearSelection(Player player) {
            plugin.selection().clearSelection(player);
        }

        @Override
        public void setLoc1(Player player, Location location) {
            plugin.selection().setLoc1(player, location);
        }

        @Override
        public void setLoc2(Player player, Location location) {
            plugin.selection().setLoc2(player, location);
        }

        @Override
        public void confirmClaim(Player player) {
            plugin.selection().confirmClaim(player);
        }

        @Override
        public void confirmClaim(Player player, boolean serverClaim) {
            plugin.selection().confirmClaim(player, serverClaim);
        }

        @Override
        @Deprecated(forRemoval = false)
        public void resizePlot(Player player, String mode, int amount) {
            plugin.selection().resizePlot(player, mode, amount);
        }
    }

    private static final class ProtectionAccessImpl implements ProtectionAccess {

        private final AegisGuard plugin;

        private ProtectionAccessImpl(AegisGuard plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean isFlagEnabled(Plot plot, String flagKey) {
            return plugin.protection().isFlagEnabled(plot, flagKey);
        }

        @Override
        public boolean isMobProtectionEnabled(Plot plot) {
            return plugin.protection().isMobProtectionEnabled(plot);
        }

        @Override
        public boolean isSafeZoneEnabled(Plot plot) {
            return plugin.protection().isSafeZoneEnabled(plot);
        }

        @Override
        public void toggleSafeZone(Plot plot, boolean enabled) {
            plugin.protection().toggleSafeZone(plot, enabled);
        }
    }
}
