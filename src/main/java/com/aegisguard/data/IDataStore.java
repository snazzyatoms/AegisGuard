// Adjust package to match your project structure:
package com.aegisguard.storage;

import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.data.PlayerData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * IDataStore = storage contract ONLY.
 * No SQL/YAML implementation logic belongs in this interface.
 *
 * Implementations:
 *  - SqlDataStore (Hikari/MySQL/SQLite)
 *  - YamlDataStore (files)
 *
 * Folia-safe note:
 * All methods are async-friendly via CompletableFuture so implementations can do I/O off-thread.
 */
public interface IDataStore {

    enum Type {
        SQL,
        YAML
    }

    Type getType();

    /**
     * Initialize connections / load files / prepare schema, etc.
     */
    CompletableFuture<Void> init();

    /**
     * Flush pending writes and close resources.
     */
    CompletableFuture<Void> shutdown();

    // ------------------------------------------------------------
    // Plots
    // ------------------------------------------------------------

    CompletableFuture<Optional<Plot>> loadPlot(String plotId);

    CompletableFuture<List<Plot>> loadPlotsByOwner(UUID owner);

    CompletableFuture<List<Plot>> loadAllPlots();

    CompletableFuture<Void> savePlot(Plot plot);

    CompletableFuture<Void> deletePlot(String plotId);

    // ------------------------------------------------------------
    // Zones (sub-plots / rentals / server zones if you model them here)
    // ------------------------------------------------------------

    CompletableFuture<List<Zone>> loadZonesForPlot(String plotId);

    CompletableFuture<Void> saveZone(Zone zone);

    CompletableFuture<Void> deleteZone(String zoneId);

    // ------------------------------------------------------------
    // Player Data (claim blocks, preferences, language, sound toggles, etc.)
    // ------------------------------------------------------------

    CompletableFuture<Optional<PlayerData>> loadPlayer(UUID playerId);

    CompletableFuture<Void> savePlayer(PlayerData data);

    // ------------------------------------------------------------
    // Optional: convenience helpers
    // ------------------------------------------------------------

    default CompletableFuture<Boolean> plotExists(String plotId) {
        return loadPlot(plotId).thenApply(Optional::isPresent);
    }
}
