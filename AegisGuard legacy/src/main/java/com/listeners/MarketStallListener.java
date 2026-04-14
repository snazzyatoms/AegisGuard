package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;

import java.util.List;
import java.util.Locale;

public class MarketStallListener implements Listener {

    private static final List<BlockFace> SEARCH_FACES = List.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    );

    private final AegisGuard plugin;

    public MarketStallListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String marker = normalize(event.getLine(0));
        Plot plot = plugin.store().getPlotAt(event.getBlock().getLocation());
        if (plot == null) return;

        boolean existingStall = plot.getStallAtSign(event.getBlock().getLocation()) != null;
        if (!isStallMarker(marker) && !existingStall) return;

        if (!isEnabledForPlot(plot) && isStallMarker(marker)) {
            event.setCancelled(true);
            plugin.effects().playError(event.getPlayer());
            send(event.getPlayer(), "market_stall_external_override",
                    "&eTradeStalls are disabled here because this plot is using an external market integration.");
            return;
        }

        Player player = event.getPlayer();
        if (!isStallMarker(marker)) {
            if (plot.removeStallBySign(event.getBlock().getLocation())) {
                plugin.store().savePlot(plot);
                send(player, "market_stall_removed", "&eTradeStall removed.");
            }
            return;
        }

        Block container = findLinkedContainer(event.getBlock());
        if (container == null) {
            event.setCancelled(true);
            plugin.effects().playError(player);
            send(player, "market_stall_no_container", "&cPlace the sign next to a single chest or barrel to create a TradeStall.");
            return;
        }

        if (requiresShopFlag() && !plot.getFlag("shop-interact", false)) {
            event.setCancelled(true);
            plugin.effects().playError(player);
            send(player, "market_stall_requires_shop", "&cEnable Shop Interact on this plot before creating a stall.");
            return;
        }

        String zoneName = resolveAllowedZoneName(player, plot, container.getLocation());
        if (!plot.canManage(player, plugin) && zoneName == null) {
            event.setCancelled(true);
            plugin.effects().playError(player);
            send(player, "market_stall_no_access", "&cYou need plot management access or a rented zone to create a TradeStall.");
            return;
        }

        String title = sanitizeTitle(event.getLine(1), player.getName() + "'s Stall");
        MarketStall stall = new MarketStall(
                player.getUniqueId(),
                player.getName(),
                plot.getWorld(),
                container.getX(),
                container.getY(),
                container.getZ(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                title,
                zoneName,
                System.currentTimeMillis()
        );

        plot.addStall(stall);
        plugin.store().savePlot(plot);

        event.setLine(0, ChatColor.GOLD + "[TradeStall]");
        event.setLine(1, title.length() > 15 ? title.substring(0, 15) : title);

        plugin.effects().playConfirm(player);
        send(player, "market_stall_created", "&aTradeStall created. Visitors will now browse it through a protected menu.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Plot plot = plugin.store().getPlotAt(event.getBlock().getLocation());
        if (plot == null) return;

        boolean removed = plot.removeStallByChest(event.getBlock().getLocation());
        removed = plot.removeStallBySign(event.getBlock().getLocation()) || removed;
        if (!removed) return;

        plugin.store().savePlot(plot);
        send(event.getPlayer(), "market_stall_removed", "&eTradeStall removed.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || !isSupportedContainer(event.getClickedBlock())) return;

        Plot plot = plugin.store().getPlotAt(event.getClickedBlock().getLocation());
        if (plot == null) return;
        if (!isEnabledForPlot(plot)) return;

        MarketStall stall = plot.getStallAtChest(event.getClickedBlock().getLocation());
        if (stall == null) return;

        Player player = event.getPlayer();
        if (stall.canStock(player, plot, plugin)) return;

        event.setCancelled(true);

        if (requiresShopFlag() && !plot.getFlag("shop-interact", false)) {
            plugin.effects().playError(player);
            send(player, "market_stall_requires_shop", "&cThis plot's stall browsing is currently disabled.");
            return;
        }

        if (!stall.isActive(plot)) {
            plugin.effects().playError(player);
            send(player, "market_stall_inactive", "&cThat TradeStall is currently unavailable.");
            return;
        }

        plugin.gui().stallBrowse().openPreview(player, plot, stall);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("market_stalls.enabled", true);
    }

    private boolean isEnabledForPlot(Plot plot) {
        return plot != null && plugin.tradeStalls() != null && plugin.tradeStalls().isEnabledFor(plot);
    }

    private boolean requiresShopFlag() {
        return plugin.getConfig().getBoolean("market_stalls.require_shop_flag", true);
    }

    private boolean allowZoneRenters() {
        return plugin.getConfig().getBoolean("market_stalls.allow_zone_renters", true);
    }

    private String resolveAllowedZoneName(Player player, Plot plot, org.bukkit.Location location) {
        if (player == null || plot == null || location == null) return null;
        if (!allowZoneRenters()) return null;

        Zone zone = plot.getZoneAt(location);
        if (zone == null || !zone.isRentedBy(player.getUniqueId())) return null;
        return zone.getName();
    }

    private Block findLinkedContainer(Block signBlock) {
        for (BlockFace face : SEARCH_FACES) {
            Block relative = signBlock.getRelative(face);
            if (!isSupportedContainer(relative)) continue;
            if (isUnsupportedDoubleChest(relative)) continue;
            return relative;
        }
        return null;
    }

    private boolean isSupportedContainer(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

    private boolean isUnsupportedDoubleChest(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return false;
        return chest.getInventory() instanceof DoubleChestInventory;
    }

    private boolean isStallMarker(String marker) {
        return "[stall]".equals(marker)
                || "[shop]".equals(marker)
                || "[market]".equals(marker)
                || "[trade]".equals(marker)
                || "[tradestall]".equals(marker);
    }

    private String normalize(String line) {
        return ChatColor.stripColor(line == null ? "" : line).trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeTitle(String raw, String fallback) {
        String stripped = ChatColor.stripColor(raw == null ? "" : raw).trim();
        if (stripped.isBlank()) return fallback;
        return stripped.length() > 32 ? stripped.substring(0, 32) : stripped;
    }

    private void send(Player player, String key, String fallback) {
        String resolved = plugin.gui().tr(player, key, fallback);
        player.sendMessage(plugin.msg().prefix() + resolved);
    }
}
