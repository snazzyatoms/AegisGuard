package com.aegisguard.visualization;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;

/**
 * Fallback renderer used when packet-based visualization is unavailable.
 */
final class NoOpBorderRenderer implements BorderRenderer {

    @Override
    public void render(Player player, Plot plot) {
        // Particles are handled separately in PlotVisualizerTask.
    }

    @Override
    public void clear(Player player) {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
