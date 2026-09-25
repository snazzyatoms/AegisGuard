package com.aegisguard.visualization;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;

/**
 * Abstraction over packet or particle claim-border rendering.
 *
 * <p>Keeping this interface free of ProtocolLib imports lets {@code PlotVisualizerTask}
 * hold a renderer without forcing the ProtocolLib-backed implementation to load on
 * servers that do not have ProtocolLib installed.</p>
 */
public interface BorderRenderer {

    /** Render the border of {@code plot} for {@code player}. */
    void render(Player player, Plot plot);

    /** Remove any rendered border for {@code player}. */
    void clear(Player player);

    /** Whether this renderer can actually function in the current environment. */
    boolean isAvailable();
}
