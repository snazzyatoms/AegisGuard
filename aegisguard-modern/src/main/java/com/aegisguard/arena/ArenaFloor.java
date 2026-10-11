package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One playable map variant of an arena: a combat plot, the entry spawn
 * players land on, and the mob spawn points used while fighting there.
 * The arena's primary floor is synthesized from the legacy flat fields
 * (arenaPlotId / entrySpawn / mobSpawns); additional variants hang off
 * {@link ArenaDefinition#getFloors()}.
 */
public final class ArenaFloor {

    private final String name;
    private UUID floorPlotId;
    private ArenaSpawnPoint entrySpawn;
    private final List<ArenaSpawnPoint> mobSpawns = new ArrayList<>();
    private int weight = 1;
    /** Optional .schem file for instanced floors (plugins/AegisGuard/arena-schematics). */
    private String schematic;
    /**
     * World-space minimum corner the schematic was captured at. Entry/mob
     * spawns stay absolute (the original map's coords); at run start the
     * instance's paste origin replaces this corner and every spawn is
     * re-based as {@code instanceOrigin + (absolute - captureMin)}.
     */
    private String captureWorld;
    private int captureMinX, captureMinY, captureMinZ;
    /** Region size captured (x/y/z extents) — needed to wipe the instance. */
    private int captureSizeX, captureSizeY, captureSizeZ;

    public ArenaFloor(String name) {
        this.name = name == null || name.isBlank() ? "floor" : name;
    }

    public String getName() { return name; }

    public UUID getFloorPlotId() { return floorPlotId; }
    public void setFloorPlotId(UUID floorPlotId) { this.floorPlotId = floorPlotId; }

    public ArenaSpawnPoint getEntrySpawn() { return entrySpawn; }
    public void setEntrySpawn(ArenaSpawnPoint entrySpawn) { this.entrySpawn = entrySpawn; }

    public List<ArenaSpawnPoint> getMobSpawns() { return mobSpawns; }

    public int getWeight() { return Math.max(1, weight); }
    public void setWeight(int weight) { this.weight = Math.max(1, weight); }

    public String getSchematic() { return schematic; }
    public void setSchematic(String schematic) {
        this.schematic = schematic == null || schematic.isBlank() ? null : schematic;
    }

    public String getCaptureWorld() { return captureWorld; }
    public int getCaptureMinX() { return captureMinX; }
    public int getCaptureMinY() { return captureMinY; }
    public int getCaptureMinZ() { return captureMinZ; }
    public int getCaptureSizeX() { return captureSizeX; }
    public int getCaptureSizeY() { return captureSizeY; }
    public int getCaptureSizeZ() { return captureSizeZ; }

    /** Stamp the capture geometry — called by ArenaSchematicService after a save. */
    public void setCaptureBounds(String world, int minX, int minY, int minZ,
                               int sizeX, int sizeY, int sizeZ) {
        this.captureWorld = world == null || world.isBlank() ? null : world;
        this.captureMinX = minX;
        this.captureMinY = minY;
        this.captureMinZ = minZ;
        this.captureSizeX = Math.max(0, sizeX);
        this.captureSizeY = Math.max(0, sizeY);
        this.captureSizeZ = Math.max(0, sizeZ);
    }

    /**
     * True when this floor instances per run: a named schematic exists AND the
     * capture origin is recorded so absolute spawns can be re-based.
     */
    public boolean isInstanced() {
        return schematic != null && captureWorld != null && captureSizeX > 0;
    }

    /**
     * A floor is usable when it has an entry point and mob spawns, plus either
     * a bound combat plot (persistent map) or a recorded schematic capture
     * (instanced map — the instance itself supplies the region).
     */
    public boolean isUsable() {
        boolean hasAnchor = floorPlotId != null || isInstanced();
        return hasAnchor
                && entrySpawn != null && entrySpawn.hasWorldIdentity()
                && !mobSpawns.isEmpty();
    }
}
