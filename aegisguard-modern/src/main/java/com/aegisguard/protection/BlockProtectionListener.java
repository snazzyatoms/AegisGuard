package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.guidance.DenialGuidance;
import com.aegisguard.hooks.protection.HookAction;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.Iterator;

public class BlockProtectionListener implements Listener {

    private final AegisGuard plugin;

    public BlockProtectionListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (plugin.isBypassing(e.getPlayer())) return;
        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getBlock().getLocation(), e.getPlayer(), HookAction.BLOCK_BREAK)) {
            return;
        }

        if (!plot.canBuildAt(e.getPlayer(), e.getBlock().getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (plugin.isBypassing(e.getPlayer())) return;
        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getBlock().getLocation(), e.getPlayer(), HookAction.BLOCK_PLACE)) {
            return;
        }

        if (!plot.canBuildAt(e.getPlayer(), e.getBlock().getLocation(), plugin, "BLOCK_PLACE")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_PLACE", "cannot_place");
            plugin.effects().playError(e.getPlayer());
        }
    }

    /**
     * Block liquid/dragon-egg flow across claim borders when the player-facing liquid-flow ward
     * is protected on either side (defaults ON). Server config {@code protections.liquid_flow}
     * remains a master kill-switch.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (!plugin.cfg().liquidFlowProtection()) return;
        Block from = e.getBlock();
        Block to = e.getToBlock();
        if (from == null || to == null) return;
        Plot fromPlot = plugin.store().getPlotAt(from.getLocation());
        Plot toPlot = plugin.store().getPlotAt(to.getLocation());
        if (fromPlot == null && toPlot == null) return;
        if (fromPlot != null && toPlot != null && fromPlot.getPlotId().equals(toPlot.getPlotId())) return;

        boolean protectFrom = fromPlot != null && plugin.protection().isFlagEnabled(fromPlot, "liquid-flow");
        boolean protectTo = toPlot != null && plugin.protection().isFlagEnabled(toPlot, "liquid-flow");
        // Existing installs without the plot key still default protected via isFlagEnabled.
        if (protectFrom || protectTo) {
            e.setCancelled(true);
        }
    }

    /**
     * Block dispensers injecting liquid source blocks across claim borders. BlockFromToEvent only
     * catches liquid that *spreads* after the source lands — a dispenser placed outside a claim and
     * facing inward places the source block itself inside the protected region. Same-claim
     * dispensers keep working; governed by the same liquid-flow ward and master switch as flow.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockDispense(BlockDispenseEvent e) {
        if (!plugin.cfg().liquidFlowProtection()) return;

        ItemStack item = e.getItem();
        if (item == null || !isLiquidBucket(item.getType())) return;

        Block dispenser = e.getBlock();
        if (!(dispenser.getBlockData() instanceof org.bukkit.block.data.Directional directional)) return;
        Block target = dispenser.getRelative(directional.getFacing());

        Plot targetPlot = plugin.store().getPlotAt(target.getLocation());
        if (targetPlot == null) return;

        Plot dispenserPlot = plugin.store().getPlotAt(dispenser.getLocation());
        if (dispenserPlot != null && dispenserPlot.getPlotId().equals(targetPlot.getPlotId())) return;

        if (plugin.protection().isFlagEnabled(targetPlot, "liquid-flow")) {
            e.setCancelled(true);
        }
    }

    /**
     * The reverse of the dispense fix above: a dispenser holding an empty bucket just
     * outside a claim can drain a water/lava source inside the border. The event fires on
     * the fluid block without naming the actor, so we scan the adjacent faces for an
     * out-of-plot dispenser pointed at the fluid. Same-claim dispensers keep working.
     */
    private static final BlockFace[] DRAIN_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFluidLevelChange(FluidLevelChangeEvent e) {
        Plot fluidPlot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (fluidPlot == null) return;
        if (!plugin.protection().isFlagEnabled(fluidPlot, "liquid-flow")) return;

        for (BlockFace face : DRAIN_FACES) {
            Block rel = e.getBlock().getRelative(face);
            if (rel.getType() != Material.DISPENSER) continue;
            if (!(rel.getBlockData() instanceof org.bukkit.block.data.Directional dir)) continue;
            // The dispenser must be facing the fluid to drain it: dispenser sits at `face`
            // offset from the fluid, so it drains when it faces back toward the fluid.
            if (dir.getFacing() != face.getOppositeFace()) continue;

            Plot dispenserPlot = plugin.store().getPlotAt(rel.getLocation());
            if (dispenserPlot == null
                    || !dispenserPlot.getPlotId().equals(fluidPlot.getPlotId())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private boolean isLiquidBucket(Material type) {
        return switch (type) {
            case WATER_BUCKET, LAVA_BUCKET, POWDER_SNOW_BUCKET, COD_BUCKET, SALMON_BUCKET,
                    PUFFERFISH_BUCKET, TROPICAL_FISH_BUCKET, AXOLOTL_BUCKET, TADPOLE_BUCKET -> true;
            default -> false;
        };
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null) return;

        Player player = e.getPlayer();
        if (plugin.isBypassing(player)) return;

        if (e.getAction() == Action.PHYSICAL && clicked.getType() == Material.FARMLAND) {
            if (plugin.protection().isFlagEnabled(plot, "farm")
                    && !plot.canInteractAt(player, clicked.getLocation(), plugin, "FARM")) {
                e.setCancelled(true);
                DenialGuidance.send(plugin, player, plot, "FARM", "cannot_interact");
                plugin.effects().playError(player);
            }
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(clicked.getLocation(), player, HookAction.CONTAINER_INTERACT)) {
            return;
        }

        if (isContainer(clicked) && plugin.protection().isFlagEnabled(plot, "containers")
                && !plot.canInteractAt(player, clicked.getLocation(), plugin, "CONTAINERS")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "CONTAINERS", "cannot_interact");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onItemFrameInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (!(clicked instanceof ItemFrame)) return;

        Player player = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null || plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(clicked.getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (plugin.protection().isFlagEnabled(plot, "decor")
                && !canUseDecorativeEntity(plot, player, clicked.getLocation())) {
            e.setCancelled(true);
            denyInteract(player, plot);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        Player player = e.getPlayer();
        ArmorStand stand = e.getRightClicked();

        Plot plot = plugin.store().getPlotAt(stand.getLocation());
        if (plot == null || plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(stand.getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (plugin.protection().isFlagEnabled(plot, "decor")
                && !canUseDecorativeEntity(plot, player, stand.getLocation())) {
            e.setCancelled(true);
            denyInteract(player, plot);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlockClicked().getRelative(e.getBlockFace()).getLocation());
        if (plot == null || plugin.isBypassing(e.getPlayer())) return;
        if (!plot.canBuildAt(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()).getLocation(), plugin, "BLOCK_PLACE")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_PLACE", "cannot_place");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlockClicked().getLocation());
        if (plot == null || plugin.isBypassing(e.getPlayer())) return;
        if (!plot.canBuildAt(e.getPlayer(), e.getBlockClicked().getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, e.getPlayer(), plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        filterExplodedBlocks(e.blockList().iterator(), "tnt-damage");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        filterExplodedBlocks(e.blockList().iterator(), "tnt-damage");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockIgnite(BlockIgniteEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockSpread(BlockSpreadEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (e.getSource().getType() != Material.FIRE) return;
        if (plugin.protection().isFlagEnabled(plot, "fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBurn(BlockBurnEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (shouldCancelPiston(e.getBlock(), e.getDirection(), e.getBlocks())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (shouldCancelPiston(e.getBlock(), e.getDirection(), e.getBlocks())) {
            e.setCancelled(true);
        }
    }

    /**
     * Vanilla mob-griefing ward (mirrors the gamerule players know): endermen stealing/placing
     * blocks, sheep grazing, snow golem trails, ravagers trampling crops, silverfish infesting,
     * the wither eating blocks, and falling sand/gravel/anvils landing inside claims all route
     * through EntityChangeBlockEvent. Player-attributed changes get a normal build check instead.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (e.getEntity() instanceof Player player) {
            if (plugin.isBypassing(player)) return;
            if (!plot.canBuildAt(player, e.getBlock().getLocation(), plugin, "BLOCK_BREAK")) {
                e.setCancelled(true);
                DenialGuidance.send(plugin, player, plot, "BLOCK_BREAK", "cannot_break");
                plugin.effects().playError(player);
            }
            return;
        }

        if (plugin.protection().isFlagEnabled(plot, "mob-griefing")) {
            e.setCancelled(true);
        }
    }

    /** Frost Walker / snow-golem trails forming blocks inside claims. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityBlockForm(EntityBlockFormEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (e.getEntity() instanceof Player player) {
            if (plugin.isBypassing(player)) return;
            if (!plot.canBuildAt(player, e.getBlock().getLocation(), plugin, "BLOCK_PLACE")) {
                e.setCancelled(true);
                DenialGuidance.send(plugin, player, plot, "BLOCK_PLACE", "cannot_place");
                plugin.effects().playError(player);
            }
            return;
        }

        if (plugin.protection().isFlagEnabled(plot, "mob-griefing")) {
            e.setCancelled(true);
        }
    }

    /** Zombies breaking claim doors. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityBreakDoor(EntityBreakDoorEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "mob-griefing")) {
            e.setCancelled(true);
        }
    }

    /** Mobs trampling turtle eggs / farmland inside claims. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityTrample(EntityInteractEvent e) {
        Block block = e.getBlock();
        if (block == null) return;
        Material type = block.getType();
        if (type != Material.TURTLE_EGG && type != Material.FARMLAND) return;

        Plot plot = plugin.store().getPlotAt(block.getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "mob-griefing")) {
            e.setCancelled(true);
        }
    }

    /**
     * Sponge placed outside a claim can drain water inside it (absorb radius crosses borders).
     * Filter absorbed blocks the same way explosions are filtered: same-claim sponges work.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpongeAbsorb(SpongeAbsorbEvent e) {
        Plot spongePlot = plugin.store().getPlotAt(e.getBlock().getLocation());
        e.getBlocks().removeIf(state -> {
            Plot target = plugin.store().getPlotAt(state.getLocation());
            if (target == null) return false;
            if (spongePlot != null && spongePlot.getPlotId().equals(target.getPlotId())) return false;
            return plugin.protection().isFlagEnabled(target, "liquid-flow");
        });
    }

    /**
     * Bonemeal (and dispenser bonemeal) converting/spreading blocks into claims. Player use gets
     * a per-block build check; source-less (dispenser) fertilization is blocked cross-claim via
     * the mob-griefing ward.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockFertilize(BlockFertilizeEvent e) {
        Block source = e.getBlock();
        Player player = e.getPlayer();
        Plot sourcePlot = plugin.store().getPlotAt(source.getLocation());

        boolean[] denied = {false};
        e.getBlocks().removeIf(state -> {
            Plot target = plugin.store().getPlotAt(state.getLocation());
            if (target == null) return false;
            if (sourcePlot != null && sourcePlot.getPlotId().equals(target.getPlotId())) return false;

            if (player != null) {
                if (plugin.isBypassing(player)) return false;
                if (!target.canBuildAt(player, state.getLocation(), plugin, "BLOCK_PLACE")) {
                    denied[0] = true;
                    return true;
                }
                return false;
            }
            return plugin.protection().isFlagEnabled(target, "mob-griefing");
        });

        if (denied[0] && player != null) {
            Plot sourcePlotFinal = sourcePlot;
            if (sourcePlotFinal == null) {
                // Any remaining target plot is fine for the guidance context.
                sourcePlotFinal = e.getBlocks().stream()
                        .map(s -> plugin.store().getPlotAt(s.getLocation()))
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            }
            if (sourcePlotFinal != null) {
                DenialGuidance.send(plugin, player, sourcePlotFinal, "BLOCK_PLACE", "cannot_place");
                plugin.effects().playError(player);
            }
        }
    }

    /**
     * Projectiles that hit redstone-triggerable blocks (targets, wooden buttons, pressure
     * plates) fire no interact event, so the redstone ward never ran — an arrow shot into a
     * claim could trip mechanisms. Player shots reuse the interact check; source-less shots
     * (dispensers) are blocked under the ward.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onProjectileBlockHit(ProjectileHitEvent e) {
        Block hit = e.getHitBlock();
        if (hit == null) return;
        if (!isProjectileTriggerable(hit.getType())) return;

        Plot plot = plugin.store().getPlotAt(hit.getLocation());
        if (plot == null) return;
        if (!plugin.protection().isFlagEnabled(plot, "redstone")) return;

        Player shooter = null;
        if (e.getEntity() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) {
            shooter = p;
        }

        if (shooter == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(shooter)) return;
        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(hit.getLocation(), shooter, HookAction.REDSTONE_INTERACT)) {
            return;
        }

        if (!plot.canInteractAt(shooter, hit.getLocation(), plugin, "REDSTONE")
                && !plot.canInteractAt(shooter, hit.getLocation(), plugin, "INTERACT")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, shooter, plot, "REDSTONE", "cannot_interact");
            plugin.effects().playError(shooter);
        }
    }

    private boolean isProjectileTriggerable(Material type) {
        String name = type.name();
        return type == Material.TARGET
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE");
    }

    /**
     * Portal creation (nether ignition, world-gen exit portals) filters created blocks that
     * would land inside claims the creator cannot build in — keeps portals from punching
     * portal blocks or frames through a protected border.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPortalCreate(PortalCreateEvent e) {
        Player creator = e.getEntity() instanceof Player p ? p : null;
        boolean removed = false;
        for (Iterator<BlockState> it = e.getBlocks().iterator(); it.hasNext();) {
            BlockState state = it.next();
            Plot target = plugin.store().getPlotAt(state.getLocation());
            if (target == null) continue;
            if (creator != null) {
                if (plugin.isBypassing(creator)) continue;
                if (!target.canBuildAt(creator, state.getLocation(), plugin, "BLOCK_PLACE")) {
                    it.remove();
                    removed = true;
                }
            } else if (plugin.protection().isFlagEnabled(target, "mob-griefing")) {
                it.remove();
                removed = true;
            }
        }
        if (removed && e.getBlocks().isEmpty()) {
            e.setCancelled(true);
            if (creator != null) {
                plugin.effects().playError(creator);
            }
        }
    }

    /** Dispenser shears: a dispenser outside a claim must not shear an animal inside it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockShearEntity(BlockShearEntityEvent e) {
        Plot target = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (target == null) return;

        Plot source = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (source != null && source.getPlotId().equals(target.getPlotId())) return;

        if (plugin.protection().isFlagEnabled(target, "animals")) {
            e.setCancelled(true);
        }
    }

    /**
     * Tree/mushroom growth filters blocks that would land inside claims: bonemeal use by a
     * player still needs build rights per block; unowned growth is stopped by mob-griefing.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onStructureGrow(StructureGrowEvent e) {
        Player player = e.getPlayer();
        Plot sourcePlot = plugin.store().getPlotAt(e.getLocation());

        boolean[] denied = {false};
        e.getBlocks().removeIf(state -> {
            Plot target = plugin.store().getPlotAt(state.getLocation());
            if (target == null) return false;
            if (sourcePlot != null && sourcePlot.getPlotId().equals(target.getPlotId())) return false;

            if (player != null) {
                if (plugin.isBypassing(player)) return false;
                if (!target.canBuildAt(player, state.getLocation(), plugin, "BLOCK_PLACE")) {
                    denied[0] = true;
                    return true;
                }
                return false;
            }
            return plugin.protection().isFlagEnabled(target, "mob-griefing");
        });

        if (denied[0] && player != null) {
            Plot context = sourcePlot != null ? sourcePlot
                    : e.getBlocks().stream()
                            .map(s -> plugin.store().getPlotAt(s.getLocation()))
                            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (context != null) {
                DenialGuidance.send(plugin, player, context, "BLOCK_PLACE", "cannot_place");
                plugin.effects().playError(player);
            }
        }
    }

    /** Dispenser/entity cauldron fill and drain crossing a claim border (liquid-flow ward). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCauldronLevelChange(CauldronLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) return; // players are gated by the interactables ward

        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;
        if (plugin.protection().isFlagEnabled(plot, "liquid-flow")) {
            e.setCancelled(true);
        }
    }

    /** Beds and other multi-block placements where the extra blocks land inside a claim. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockMultiPlace(BlockMultiPlaceEvent e) {
        Player player = e.getPlayer();
        if (plugin.isBypassing(player)) return;

        for (BlockState state : e.getReplacedBlockStates()) {
            Plot plot = plugin.store().getPlotAt(state.getLocation());
            if (plot == null) continue;
            if (plugin.protectionHooks() != null
                    && plugin.protectionHooks().shouldBypass(state.getLocation(), player, HookAction.BLOCK_PLACE)) {
                continue;
            }
            if (!plot.canBuildAt(player, state.getLocation(), plugin, "BLOCK_PLACE")) {
                e.setCancelled(true);
                DenialGuidance.send(plugin, player, plot, "BLOCK_PLACE", "cannot_place");
                plugin.effects().playError(player);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player player = e.getPlayer();
        if (player == null) return;

        Hanging hanging = e.getEntity();
        Plot plot = plugin.store().getPlotAt(hanging.getLocation());
        if (plot == null || plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(hanging.getLocation(), player, HookAction.BLOCK_PLACE)) {
            return;
        }

        if (!plot.canBuildAt(player, hanging.getLocation(), plugin, "BLOCK_PLACE")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "BLOCK_PLACE", "cannot_place");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Hanging hanging = e.getEntity();
        Plot plot = plugin.store().getPlotAt(hanging.getLocation());
        if (plot == null) return;

        Player player = resolvePlayer(e.getRemover());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(hanging.getLocation(), player, HookAction.BLOCK_BREAK)) {
            return;
        }

        boolean decorProtected = plugin.protection().isFlagEnabled(plot, "decor");
        if (decorProtected && !canUseDecorativeEntity(plot, player, hanging.getLocation())
                && !plot.canBuildAt(player, hanging.getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "INTERACT", "cannot_interact");
            plugin.effects().playError(player);
        } else if (!decorProtected && !plot.canBuildAt(player, hanging.getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreakOther(HangingBreakEvent e) {
        if (e instanceof HangingBreakByEntityEvent) return;

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;

        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ArmorStand stand)) return;

        Plot plot = plugin.store().getPlotAt(stand.getLocation());
        if (plot == null) return;

        Player player = resolvePlayer(e.getDamager());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(stand.getLocation(), player, HookAction.BLOCK_BREAK)) {
            return;
        }

        boolean decorProtected = plugin.protection().isFlagEnabled(plot, "decor");
        if (decorProtected) {
            if (!canUseDecorativeEntity(plot, player, stand.getLocation())
                    && !plot.canBuildAt(player, stand.getLocation(), plugin, "BLOCK_BREAK")) {
                e.setCancelled(true);
                DenialGuidance.send(plugin, player, plot, "INTERACT", "cannot_interact");
                plugin.effects().playError(player);
            }
        } else if (!plot.canBuildAt(player, stand.getLocation(), plugin, "BLOCK_BREAK")) {
            e.setCancelled(true);
            DenialGuidance.send(plugin, player, plot, "BLOCK_BREAK", "cannot_break");
            plugin.effects().playError(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onVehicleDamage(VehicleDamageEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getVehicle().getLocation());
        if (plot == null) return;
        if (!plugin.protection().isFlagEnabled(plot, "vehicles")) return;

        Player player = resolvePlayer(e.getAttacker());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getVehicle().getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (!plot.canInteractAt(player, e.getVehicle().getLocation(), plugin, "VEHICLES")) {
            e.setCancelled(true);
            denyInteract(player, plot, "VEHICLES");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getVehicle().getLocation());
        if (plot == null) return;
        if (!plugin.protection().isFlagEnabled(plot, "vehicles")) return;

        Player player = resolvePlayer(e.getAttacker());
        if (player == null) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isBypassing(player)) return;

        if (plugin.protectionHooks() != null
                && plugin.protectionHooks().shouldBypass(e.getVehicle().getLocation(), player, HookAction.OTHER)) {
            return;
        }

        if (!plot.canInteractAt(player, e.getVehicle().getLocation(), plugin, "VEHICLES")) {
            e.setCancelled(true);
        }
    }

    private void filterExplodedBlocks(Iterator<Block> iterator, String flag) {
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot != null && (plugin.protection().isFlagEnabled(plot, flag) || plugin.protection().isFlagEnabled(plot, "explosions"))) {
                iterator.remove();
            }
        }
    }

    private boolean shouldCancelPiston(Block piston, BlockFace direction, Iterable<Block> movedBlocks) {
        Plot pistonPlot = plugin.store().getPlotAt(piston.getLocation());
        if (pistonPlot != null && plugin.protection().isFlagEnabled(pistonPlot, "piston-use")) {
            return true;
        }

        for (Block moved : movedBlocks) {
            Plot fromPlot = plugin.store().getPlotAt(moved.getLocation());
            Plot toPlot = plugin.store().getPlotAt(moved.getRelative(direction).getLocation());
            if ((fromPlot != null && plugin.protection().isFlagEnabled(fromPlot, "piston-use"))
                    || (toPlot != null && plugin.protection().isFlagEnabled(toPlot, "piston-use"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isContainer(Block block) {
        if (block == null) return false;
        BlockState state = block.getState();
        return state instanceof InventoryHolder;
    }

    private boolean canUseDecorativeEntity(Plot plot, Player player, org.bukkit.Location location) {
        return plot.canInteractAt(player, location, plugin, "INTERACT");
    }

    private void denyInteract(Player player, Plot plot) {
        denyInteract(player, plot, "INTERACT");
    }

    private void denyInteract(Player player, Plot plot, String permission) {
        DenialGuidance.send(plugin, player, plot, permission, "cannot_interact");
        plugin.effects().playError(player);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
