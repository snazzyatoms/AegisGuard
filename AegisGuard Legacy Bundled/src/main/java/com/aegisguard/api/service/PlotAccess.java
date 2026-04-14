package com.aegisguard.api.service;

import com.aegisguard.data.Plot;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlotAccess {

    List<Plot> getPlots(UUID ownerId);

    @Nullable Plot getPlot(UUID ownerId, UUID plotId);

    @Nullable Plot getPlotById(UUID plotId);

    Collection<Plot> getAllPlots();

    Collection<Plot> getPlotsForSale();

    Collection<Plot> getPlotsForAuction();

    Collection<Plot> getPlotsInWorld(String worldName);

    @Nullable Plot getPlotAt(Location location);

    boolean isAreaOverlapping(@Nullable Plot ignore, String worldName, int x1, int z1, int x2, int z2);

    void savePlot(Plot plot);

    void savePlotSync(Plot plot);
}
