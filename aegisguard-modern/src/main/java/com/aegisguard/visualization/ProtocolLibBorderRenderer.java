package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional ProtocolLib-based claim border renderer.
 *
 * <p>When ProtocolLib is installed and {@code visualization.protocol_lib.enabled}
 * is true, the wand visualizer sends fake block-change packets to the viewing
 * player only. This produces a solid, highly visible border line without placing
 * real blocks or showing particles to other players.</p>
 *
 * <p>Fake blocks are remembered per-player and restored to the real block state
 * when the player moves to a different plot or unequips the wand. All world/block
 * reads happen on the owning region thread because this renderer is invoked
 * from the per-player visualizer task.</p>
 */
public final class ProtocolLibBorderRenderer implements BorderRenderer {

    private final AegisGuard plugin;
    private final ProtocolManager protocolManager;
    private final Material borderMaterial;
    private final int yOffset;

    // Player UUID -> set of positions currently shown as fake border blocks
    private final ConcurrentHashMap<UUID, Set<BlockPosition>> activeFakeBlocks = new ConcurrentHashMap<>();

    public ProtocolLibBorderRenderer(AegisGuard plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.borderMaterial = resolveMaterial();
        this.yOffset = plugin.getConfig().getInt("visualization.protocol_lib.y_offset", 0);
    }

    public boolean isAvailable() {
        return protocolManager != null;
    }

    /**
     * Render the border of {@code plot} for {@code player}. Any fake blocks from
     * a previous plot are cleared first.
     */
    public void render(Player player, Plot plot) {
        if (!isAvailable() || player == null || plot == null) return;
        if (!plot.getWorld().equals(player.getWorld().getName())) {
            clear(player);
            return;
        }

        World world = player.getWorld();
        int y = Math.clamp(player.getLocation().getBlockY() + yOffset,
                world.getMinHeight() + 1, world.getMaxHeight() - 1);

        Set<BlockPosition> current = new HashSet<>();
        collectBorderPositions(plot, y, current);

        Set<BlockPosition> previous = activeFakeBlocks.getOrDefault(player.getUniqueId(), Set.of());

        // Restore blocks that are no longer on the new border
        Set<BlockPosition> stale = new HashSet<>(previous);
        stale.removeAll(current);
        if (!stale.isEmpty()) {
            restoreRealBlocks(player, world, stale);
        }

        // Show new fake border blocks
        WrappedBlockData fakeData = WrappedBlockData.createData(borderMaterial);
        for (BlockPosition pos : current) {
            sendBlockChange(player, pos, fakeData);
        }

        activeFakeBlocks.put(player.getUniqueId(), current);
    }

    /**
     * Remove all fake blocks for a player, restoring the real block state.
     */
    public void clear(Player player) {
        if (!isAvailable() || player == null) return;
        Set<BlockPosition> previous = activeFakeBlocks.remove(player.getUniqueId());
        if (previous == null || previous.isEmpty()) return;

        restoreRealBlocks(player, player.getWorld(), previous);
    }

    /**
     * Restore real block states for positions that may span multiple Folia
     * regions. Positions are grouped by chunk and each group runs on the
     * region thread that owns that chunk so block reads stay legal.
     */
    private void restoreRealBlocks(Player player, World world, Set<BlockPosition> positions) {
        Map<Long, java.util.List<BlockPosition>> byChunk = new java.util.HashMap<>();
        for (BlockPosition pos : positions) {
            long key = (((long) (pos.getX() >> 4)) << 32) | ((pos.getZ() >> 4) & 0xffffffffL);
            byChunk.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(pos);
        }
        for (java.util.List<BlockPosition> batch : byChunk.values()) {
            BlockPosition first = batch.get(0);
            Location anchor = new Location(world, first.getX(), first.getY(), first.getZ());
            plugin.runAt(anchor, () -> {
                if (!player.isOnline()) return;
                for (BlockPosition pos : batch) {
                    try {
                        restoreRealBlock(player, world, pos);
                    } catch (Throwable t) {
                        plugin.getLogger().fine("Could not restore border block at " + pos + ": " + t);
                    }
                }
            });
        }
    }

    private void collectBorderPositions(Plot plot, int y, Set<BlockPosition> out) {
        int x1 = plot.getX1();
        int x2 = plot.getX2();
        int z1 = plot.getZ1();
        int z2 = plot.getZ2();

        for (int x = x1; x <= x2; x++) {
            out.add(new BlockPosition(x, y, z1));
            out.add(new BlockPosition(x, y, z2));
        }
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            out.add(new BlockPosition(x1, y, z));
            out.add(new BlockPosition(x2, y, z));
        }
    }

    private void restoreRealBlock(Player player, World world, BlockPosition pos) {
        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        WrappedBlockData realData = WrappedBlockData.createData(world.getBlockAt(loc).getBlockData());
        sendBlockChange(player, pos, realData);
    }

    private void sendBlockChange(Player player, BlockPosition pos, WrappedBlockData data) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
        packet.getBlockPositionModifier().write(0, pos);
        packet.getBlockData().write(0, data);
        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to send border packet to "
                    + player.getName() + " at " + pos + ": " + e.getMessage());
        }
    }

    private Material resolveMaterial() {
        String name = plugin.getConfig().getString("visualization.protocol_lib.material", "LIME_STAINED_GLASS");
        if (name == null || name.isBlank()) return Material.LIME_STAINED_GLASS;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Material.LIME_STAINED_GLASS;
        }
    }
}
