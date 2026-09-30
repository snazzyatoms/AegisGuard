package com.aegisguard.biomes;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies a plot's chosen biome across its claimed area.
 *
 * <p>The plot's {@code customBiome} field was persisted long before any code could set
 * it; this service is the missing apply path used by {@code /aegis biome} / BiomeGUI.
 *
 * <p>Folia safety: every chunk column is applied on the region thread that owns it via
 * {@link AegisGuard#runAt(Location, Runnable)}. Work is spread over ticks (a small batch
 * of columns per step) so large plots never stall a region or the main thread.
 */
public class BiomeService {

    private final AegisGuard plugin;

    /** Chunk columns processed per step; each step waits a tick before the next. */
    private static final int COLUMNS_PER_STEP = 4;
    private static final long STEP_DELAY_TICKS = 1L;

    public BiomeService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.modules() == null
                || plugin.modules().on(com.aegisguard.config.Modules.Id.BIOMES);
    }

    /** The configured allow-list (config.yml {@code biomes.allowed}). */
    public List<String> allowedBiomes() {
        List<String> raw = plugin.getConfig().getStringList("biomes.allowed");
        if (raw.isEmpty()) return List.of("PLAINS");
        List<String> out = new ArrayList<>(raw.size());
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) continue;
            out.add(entry.trim().toUpperCase(Locale.ROOT));
        }
        return out.isEmpty() ? List.of("PLAINS") : out;
    }

    public double costPerChange() {
        return Math.max(0.0D, plugin.getConfig().getDouble("biomes.cost_per_change", 0.0D));
    }

    /** Resolve a configured name to a Bukkit biome; null when unknown. */
    public Biome resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        try {
            return Biome.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {}
        try {
            NamespacedKey key = NamespacedKey.minecraft(normalized.toLowerCase(Locale.ROOT));
            return Registry.BIOME.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isAllowed(String name) {
        if (name == null) return false;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (String entry : allowedBiomes()) {
            if (entry.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    /**
     * Applies {@code biome} to every block column inside {@code plot}'s cuboid.
     * Finishes by persisting {@code customBiome} and running {@code onDone} on the
     * player's own region thread (may be null to skip the callback).
     */
    public void apply(Plot plot, Biome biome, Player player, Runnable onDone) {
        if (plot == null || biome == null) return;
        World world = plot.getWorld() == null ? null : Bukkit.getWorld(plot.getWorld());
        if (world == null) return;

        ArrayDeque<int[]> columns = new ArrayDeque<>();
        int minCx = plot.getX1() >> 4;
        int maxCx = plot.getX2() >> 4;
        int minCz = plot.getZ1() >> 4;
        int maxCz = plot.getZ2() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                columns.add(new int[]{cx, cz});
            }
        }
        if (columns.isEmpty()) return;

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        String biomeName = biome.name();
        Location anchor = new Location(world, plot.getX1(), Math.max(minY, 64), plot.getZ1());

        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            int processed = 0;
            while (processed < COLUMNS_PER_STEP && !columns.isEmpty()) {
                int[] col = columns.poll();
                processed++;
                if (col == null) continue;
                int cx = col[0], cz = col[1];
                Location colAnchor = new Location(world, (cx << 4) + 8, Math.max(minY, 64), (cz << 4) + 8);
                plugin.runAt(colAnchor, () -> applyColumn(world, plot, cx, cz, minY, maxY, biome));
            }
            if (!columns.isEmpty()) {
                plugin.scheduler().runGlobalLater(step[0], STEP_DELAY_TICKS);
                return;
            }
            plot.setCustomBiome(biomeName);
            plugin.store().setDirty(true);
            if (onDone != null) {
                if (player != null) plugin.runMain(player, onDone);
                else plugin.runAt(anchor, onDone);
            }
        };
        plugin.runAt(anchor, step[0]);
    }

    private void applyColumn(World world, Plot plot, int cx, int cz, int minY, int maxY, Biome biome) {
        world.getChunkAt(cx, cz);
        int fromX = Math.max(cx << 4, plot.getX1());
        int toX = Math.min((cx << 4) + 15, plot.getX2());
        int fromZ = Math.max(cz << 4, plot.getZ1());
        int toZ = Math.min((cz << 4) + 15, plot.getZ2());
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                // Biomes are stored per 4x4x4 quart; stepping by 4 covers every cell.
                for (int y = minY; y < maxY; y += 4) {
                    world.setBiome(x, y, z, biome);
                }
            }
        }
    }
}
