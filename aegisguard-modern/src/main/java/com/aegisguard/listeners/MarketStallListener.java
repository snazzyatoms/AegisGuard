package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        boolean bindCreate = plugin.tradeStalls() != null && plugin.tradeStalls().hasCreateBind(event.getPlayer());
        if (!isStallMarker(marker) && !existingStall && !bindCreate) return;

        if (!isEnabledForPlot(plot) && (isStallMarker(marker) || bindCreate)) {
            event.setCancelled(true);
            plugin.effects().playError(event.getPlayer());
            send(event.getPlayer(), "market_stall_external_override",
                    "&eTradeStalls are disabled here because this plot is using an external market integration.");
            return;
        }

        Player player = event.getPlayer();
        if (!isStallMarker(marker) && !bindCreate) {
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

        if (!createStall(player, plot, event.getBlock(), container, event.getLine(1), event)) {
            event.setCancelled(true);
        }
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
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null) return;

        if (plugin.tradeStalls() != null && plugin.tradeStalls().hasCreateBind(player)) {
            if (handleBindClick(player, plot, clicked)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!isEnabledForPlot(plot)) return;

        if (isSign(clicked)) {
            if (player.isSneaking()) return;
            MarketStall stall = plot.getStallAtSign(clicked.getLocation());
            if (stall == null) return;
            event.setCancelled(true);
            openStallGui(player, plot, stall);
            return;
        }

        if (!isSupportedContainer(clicked)) return;

        MarketStall stall = plot.getStallAtChest(clicked.getLocation());
        if (stall == null) return;

        if (stall.canStock(player, plot, plugin)) return;

        event.setCancelled(true);
        openStallGui(player, plot, stall);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (plugin.tradeStalls() == null) return;
        Location from = inventoryLocation(event.getSource());
        Location to = inventoryLocation(event.getDestination());
        if (plugin.tradeStalls().isStallContainer(from) || plugin.tradeStalls().isStallContainer(to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.tradeStalls() != null) {
            plugin.tradeStalls().clearCreateBind(event.getPlayer());
        }
    }

    private boolean handleBindClick(Player player, Plot plot, Block clicked) {
        if (!isEnabledForPlot(plot)) {
            plugin.tradeStalls().clearCreateBind(player);
            plugin.effects().playError(player);
            send(player, "market_stall_external_override",
                    "&eTradeStalls are disabled here because this plot is using an external market integration.");
            return true;
        }

        Block sign;
        Block container;
        if (isSupportedContainer(clicked)) {
            container = clicked;
            sign = findLinkedSign(clicked);
            if (sign == null) {
                plugin.effects().playError(player);
                send(player, "market_stall_bind_need_sign",
                        "&cPlace a sign next to this chest, then right-click again. First line can be [stall] or [shop].");
                return true;
            }
        } else if (isSign(clicked)) {
            sign = clicked;
            container = findLinkedContainer(clicked);
            if (container == null) {
                plugin.effects().playError(player);
                send(player, "market_stall_bind_need_chest",
                        "&cPlace this sign next to a single chest or barrel, then right-click again.");
                return true;
            }
        } else {
            return false;
        }

        if (isUnsupportedDoubleChest(container)) {
            plugin.effects().playError(player);
            send(player, "market_stall_no_container", "&cPlace the sign next to a single chest or barrel to create a TradeStall.");
            return true;
        }

        if (plot.getStallAtChest(container.getLocation()) != null) {
            plugin.tradeStalls().consumeCreateBind(player);
            openStallGui(player, plot, plot.getStallAtChest(container.getLocation()));
            return true;
        }

        plugin.tradeStalls().consumeCreateBind(player);
        createStall(player, plot, sign, container, player.getName() + "'s Stall", null);
        return true;
    }

    private boolean createStall(Player player, Plot plot, Block signBlock, Block container, String rawTitle, SignChangeEvent signEvent) {
        if (requiresShopFlag() && !plot.getFlag("shop-interact", false)) {
            plugin.effects().playError(player);
            send(player, "market_stall_requires_shop", "&cEnable Shop Interact on this plot before creating a stall.");
            return false;
        }

        String zoneName = resolveAllowedZoneName(player, plot, container.getLocation());
        if (!plot.canManage(player, plugin) && zoneName == null) {
            plugin.effects().playError(player);
            send(player, "market_stall_no_access", "&cYou need plot management access or a rented zone to create a TradeStall.");
            return false;
        }

        if (plot.getStallAtChest(container.getLocation()) != null) {
            plugin.effects().playError(player);
            send(player, "market_stall_generic_error", "&cThat TradeStall action could not be completed.");
            return false;
        }

        String title = sanitizeTitle(rawTitle, player.getName() + "'s Stall");
        MarketStall stall = new MarketStall(
                player.getUniqueId(),
                player.getName(),
                plot.getWorld(),
                container.getX(),
                container.getY(),
                container.getZ(),
                signBlock.getX(),
                signBlock.getY(),
                signBlock.getZ(),
                title,
                zoneName,
                System.currentTimeMillis()
        );

        plot.addStall(stall);
        plugin.store().savePlot(plot);

        if (signEvent != null) {
            plugin.tradeStalls().applyCreatedSignLines(signEvent, stall);
        } else {
            plugin.tradeStalls().refreshSign(stall);
        }

        plugin.effects().playConfirm(player);
        send(player, "market_stall_created",
                "&aTradeStall created. Visitors browse from the sign or chest menu — they cannot take items from the chest.");
        send(player, "market_stall_create_guide",
                "&7Stock the chest, then set prices in the TradeStall manage menu.");
        return true;
    }

    private void openStallGui(Player player, Plot plot, MarketStall stall) {
        if (requiresShopFlag() && !plot.getFlag("shop-interact", false)) {
            plugin.effects().playError(player);
            send(player, "market_stall_requires_shop", "&cThis plot's stall browsing is currently disabled.");
            return;
        }

        if (!stall.isActive(plot) && !stall.canStock(player, plot, plugin)) {
            plugin.effects().playError(player);
            send(player, "market_stall_inactive", "&cThat TradeStall is currently unavailable.");
            return;
        }

        if (stall.canStock(player, plot, plugin)) {
            plugin.gui().stallBrowse().openManage(player, plot, stall, 0);
        } else {
            plugin.gui().stallBrowse().openPreview(player, plot, stall);
        }
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

    private String resolveAllowedZoneName(Player player, Plot plot, Location location) {
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

    private Block findLinkedSign(Block container) {
        for (BlockFace face : SEARCH_FACES) {
            Block relative = container.getRelative(face);
            if (isSign(relative)) return relative;
        }
        return null;
    }

    private boolean isSupportedContainer(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

    private boolean isSign(Block block) {
        if (block == null) return false;
        String name = block.getType().name();
        return name.endsWith("_SIGN") || name.endsWith("_HANGING_SIGN") || "SIGN".equals(name);
    }

    private boolean isUnsupportedDoubleChest(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return false;
        return chest.getInventory() instanceof DoubleChestInventory;
    }

    private Location inventoryLocation(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null) return null;
        try {
            Location loc = inventory.getLocation();
            if (loc != null) return loc;
        } catch (Throwable ignored) {}
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        if (holder instanceof org.bukkit.block.Container container) {
            return container.getLocation();
        }
        if (holder instanceof org.bukkit.block.DoubleChest chest) {
            return chest.getLocation();
        }
        return null;
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
