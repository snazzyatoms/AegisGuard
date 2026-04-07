package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import com.aegisguard.claimblocks.ClaimBlockData;
import com.aegisguard.data.Plot;
import com.aegisguard.groups.PlotGroup;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.protection.ProtectionHookManager;
import com.aegisguard.listeners.WandSafetyListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.awt.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SelectionService - Claim creation & resizing logic.
 *
 * v1.2.6 (QoL + stability) goals:
 * - Restore 1.2.5 behavior (wands, full command API surface, overlap rules, server claims)
 * - Keep 1.2.6 direction (cleaner selection object, protection hooks, claim blocks, steward auto-role for server plots)
 * - Add compatibility overloads used by commands/GUI (confirmClaim(Player), hasSelection, getSelectionArea, etc.)
 */
public class SelectionService implements Listener {

    // Use string-based keys so this is safe even during early classloading.
    public static final NamespacedKey WAND_KEY = new NamespacedKey("aegisguard", "aegis_wand");
    public static final NamespacedKey SERVER_WAND_KEY = new NamespacedKey("aegisguard", "aegis_server_wand");

    private final AegisGuard plugin;

    // 1.2.6 structure: store selection object per player
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    // 1.2.5 behavior: keep track of which wand type the player is using (for messages + consumption rules)
    // values: "claim_wand" | "server_claim_wand"
    private final Map<UUID, String> playerWandType = new ConcurrentHashMap<>();

    public SelectionService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // Selection storage
    // ------------------------------------------------------------

    public Selection get(UUID uuid) {
        return selections.get(uuid);
    }

    public void set(UUID uuid, Selection sel) {
        if (sel == null) selections.remove(uuid);
        else selections.put(uuid, sel);
    }

    public void clear(UUID uuid) {
        selections.remove(uuid);
        playerWandType.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------
    // 1.2.5 API surface restored (used by commands + GUIs)
    // ------------------------------------------------------------

    public void setLoc1(Player p, Location loc) {
        if (p == null) return;
        Selection sel = selections.computeIfAbsent(p.getUniqueId(), k -> new Selection(null, null));
        sel.setL1(loc);
        selections.put(p.getUniqueId(), sel);
    }

    public void setLoc2(Player p, Location loc) {
        if (p == null) return;
        Selection sel = selections.computeIfAbsent(p.getUniqueId(), k -> new Selection(null, null));
        sel.setL2(loc);
        selections.put(p.getUniqueId(), sel);
    }

    public boolean hasSelection(Player p) {
        if (p == null) return false;
        Selection sel = selections.get(p.getUniqueId());
        return sel != null && sel.isComplete();
    }

    public void clearSelection(Player p) {
        if (p == null) return;
        clear(p.getUniqueId());
    }

    /**
     * Returns selection area in blocks (2D) - used by /aegis confirm messaging and GUIs.
     */
    public long getSelectionArea(Player p) {
        if (p == null) return 0L;
        Selection sel = selections.get(p.getUniqueId());
        if (sel == null || !sel.isComplete()) return 0L;

        Location l1 = sel.getL1();
        Location l2 = sel.getL2();
        if (l1 == null || l2 == null) return 0L;
        if (l1.getWorld() == null || l2.getWorld() == null) return 0L;
        if (!l1.getWorld().equals(l2.getWorld())) return 0L;

        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        long width = (maxX - minX) + 1L;
        long depth = (maxZ - minZ) + 1L;

        return width * depth;
    }

    // ------------------------------------------------------------
    // Wands (1.2.5 behavior)
    // ------------------------------------------------------------

    public void setPlayerWand(Player p, String wandType) {
        if (p == null) return;
        if (wandType == null) {
            playerWandType.remove(p.getUniqueId());
        } else {
            playerWandType.put(p.getUniqueId(), wandType);
        }
    }

    public String getPlayerWand(Player p) {
        if (p == null) return null;
        return playerWandType.get(p.getUniqueId());
    }

    /**
     * Static helper expected by 1.2.5+ command paths.
     */
    public static boolean playerHasAnyWand(Player p) {
        return WandSafetyListener.playerHasAnyWand(p);
    }

    /**
     * Manually consumes a claim wand (used by /aegis confirm safety and admin flows).
     * Honors config:
     * - claims.consume_wand_on_claim
     * - claims.admin_keep_wand
     */
    public void manualConsumeWand(Player p) {
        if (p == null) return;

        boolean consume = plugin.cfg().raw().getBoolean("claims.consume_wand_on_claim", true);
        if (!consume) return;

        boolean adminKeep = plugin.cfg().raw().getBoolean("claims.admin_keep_wand", true);
        if (adminKeep && (plugin.isAdmin(p) || p.hasPermission("aegis.admin"))) {
            return;
        }

        consumeOneWandFromInventory(p);
    }

    private void consumeOneWandFromInventory(Player p) {
        PlayerInventory inv = p.getInventory();

        // Prefer the item in hand (most intuitive)
        int held = inv.getHeldItemSlot();
        if (tryConsumeIfWand(inv, held)) return;

        // Otherwise scan inventory
        for (int i = 0; i < inv.getSize(); i++) {
            if (tryConsumeIfWand(inv, i)) return;
        }
    }

    private boolean tryConsumeIfWand(PlayerInventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        if (it == null || it.getType().isAir()) return false;
        if (!isAegisWand(it)) return false;

        int amt = it.getAmount();
        if (amt <= 1) inv.setItem(slot, null);
        else it.setAmount(amt - 1);

        return true;
    }

    private boolean isAegisWand(ItemStack item) {
        if (item == null) return false;
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(WAND_KEY, PersistentDataType.BYTE) || pdc.has(SERVER_WAND_KEY, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------
    // Claim confirmation (restored behavior + 1.2.6 QoL)
    // ------------------------------------------------------------

    /**
     * Compatibility overload (older code calls confirmClaim(player) without the server flag).
     */
    public void confirmClaim(Player p) {
        confirmClaim(p, false);
    }

    public void confirmClaim(Player p, boolean isServerClaim) {
        SelectionContext ctx = getSelectionContext(p);
        if (ctx == null) return;

        // Limits (supports 1.2.5 + 1.2.6 config styles)
        int maxRadius = getWorldInt(ctx.worldName, "max_radius",
                plugin.cfg().raw().getInt("claims.max_radius_global",
                        plugin.cfg().raw().getInt("claims.max_radius", 200)));

        int maxArea = getWorldInt(ctx.worldName, "max_area",
                plugin.cfg().raw().getInt("claims.max_area", 50000));

        if (!p.hasPermission("aegis.admin.bypass") && !p.hasPermission("aegis.admin.bypass-limits")) {
            if (ctx.radius > maxRadius) {
                plugin.msg().send(p, "resize-fail-max-area");
                return;
            }
            if (ctx.area > maxArea) {
                plugin.msg().send(p, "resize-fail-max-area");
                return;
            }
        }

        // Overlap checks against Aegis plots (robust AABB overlap, not only corners)
        for (Plot other : plugin.store().getPlotsInWorld(ctx.worldName)) {
            if (other == null) continue;

            // Ignore if selection equals same plot bounds is irrelevant here, since we are creating a new plot.
            int oMinX = Math.min(other.getX1(), other.getX2());
            int oMaxX = Math.max(other.getX1(), other.getX2());
            int oMinZ = Math.min(other.getZ1(), other.getZ2());
            int oMaxZ = Math.max(other.getZ1(), other.getZ2());

            boolean overlaps = (ctx.minX <= oMaxX && ctx.maxX >= oMinX) && (ctx.minZ <= oMaxZ && ctx.maxZ >= oMinZ);
            if (overlaps) {
                plugin.msg().send(p, "resize-fail-overlap");
                return;
            }
        }

        // Compatibility: if other protection plugin present, yield if configured
        ProtectionHookManager hooks = plugin.protectionHooks();
        if (hooks != null && hooks.isAreaProtectedElsewhere(ctx.worldName, ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ)) {
            plugin.msg().send(p, "claim_external_protection_conflict");
            return;
        }

        // ClaimBlock economy checks (existing behavior preserved)
        boolean claimBlocksEnabled = plugin.cfg().raw().getBoolean("claim_blocks.enabled", true);
        if (!isServerClaim && claimBlocksEnabled && !p.hasPermission("aegis.admin.bypass-limits")) {

            ClaimBlockData blocks = plugin.claimBlocks().getOrCreate(p.getUniqueId());

            // First claim limit
            boolean starterEnabled = plugin.cfg().raw().getBoolean("claim_blocks.first_claim_limit.enabled", true);
            int firstClaimLimit = plugin.cfg().raw().getInt("claim_blocks.first_claim_limit.max_area", 10000);

            if (starterEnabled && !blocks.hasClaimedStarter() && ctx.area > firstClaimLimit) {
                plugin.msg().send(p, "first_claim_too_large");
                return;
            }

            boolean perBlock = plugin.cfg().raw().getBoolean("claim_blocks.require_per_block", true);
            int required = perBlock ? ctx.area : 1;

            if (blocks.getAvailable() < required) {
                plugin.msg().send(p, "claim_blocks_not_enough");
                return;
            }

            // Spend blocks now (keeps 1.2.5 behavior: do not create plot if spend fails)
            boolean spent = plugin.claimBlocks().spend(p.getUniqueId(), required);
            if (!spent) {
                plugin.msg().send(p, "claim_blocks_not_enough");
                return;
            }
        }

        // --- CREATION ---
        Plot plot;
        long now = System.currentTimeMillis();

        if (isServerClaim) {
            plot = new Plot(
                    UUID.randomUUID(),
                    Plot.SERVER_OWNER_UUID,
                    "Server",
                    ctx.worldName,
                    ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ, now
            );

            // Server plot defaults (safe fallback)
            plot.setFlag("build", true);
            plot.setFlag("pvp", true);
            plot.setFlag("safe_zone", true);

            // 1.2.6 QoL: server plot creators should never be locked out
            plot.setRole(p.getUniqueId(), "steward");
        } else {
            plot = new Plot(
                    UUID.randomUUID(),
                    p.getUniqueId(),
                    p.getName(),
                    ctx.worldName,
                    ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ, now
            );
            plugin.worldRules().applyDefaults(plot);
        }

        PlotClaimEvent event = new PlotClaimEvent(plot, p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        plugin.store().addPlot(plot);

        // Mark starter claim as used AFTER successful save
        if (!isServerClaim && claimBlocksEnabled) {
            boolean starterEnabled = plugin.cfg().raw().getBoolean("claim_blocks.first_claim_limit.enabled", true);
            if (starterEnabled) {
                ClaimBlockData blocks = plugin.claimBlocks().getOrCreate(p.getUniqueId());
                if (!blocks.hasClaimedStarter()) {
                    blocks.setClaimedStarter(true);
                    plugin.claimBlocks().save();
                }
            }
        }

        // Wand consumption (restored)
        String wandType = getPlayerWand(p);
        if (wandType != null) {
            boolean consume = plugin.cfg().raw().getBoolean("claims.consume_wand_on_claim", true);
            if (consume) manualConsumeWand(p);
            setPlayerWand(p, null);
        }

        selections.remove(p.getUniqueId());

        // Effects + feedback
        if (plugin.effects() != null) plugin.effects().playClaimSuccess(p);
        else p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);

        plugin.msg().send(p, isServerClaim ? "admin-zone-created" : "plot_claimed");

        // Discord webhook (if configured)
        if (plugin.getDiscord() != null) {
            try {
                DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject();
                embed.setTitle(isServerClaim ? "Admin Zone Created" : "Plot Claimed");
                embed.setDescription(p.getName() + " claimed a plot.");
                embed.addField("World", ctx.worldName, true);
                embed.addField("Area", String.valueOf(ctx.area), true);
                embed.addField("Owner", isServerClaim ? "Server" : p.getName(), true);
                embed.setColor(isServerClaim ? Color.CYAN : Color.GREEN);

                plugin.getDiscord().send(embed);
            } catch (Exception ignored) {
                // Keep silent: discord must never break claims.
            }
        }
    }

    public Plot confirmGroupClaim(Player p, PlotGroup group) {
        if (p == null || group == null) return null;

        SelectionContext ctx = getSelectionContext(p);
        if (ctx == null) return null;

        int maxRadius = getWorldInt(ctx.worldName, "max_radius",
                plugin.cfg().raw().getInt("claims.max_radius_global",
                        plugin.cfg().raw().getInt("claims.max_radius", 200)));
        int maxArea = getWorldInt(ctx.worldName, "max_area",
                plugin.cfg().raw().getInt("claims.max_area", 50000));

        if (!p.hasPermission("aegis.admin.bypass") && !p.hasPermission("aegis.admin.bypass-limits")) {
            if (ctx.radius > maxRadius || ctx.area > maxArea) {
                plugin.msg().send(p, "resize-fail-max-area");
                return null;
            }
        }

        for (Plot other : plugin.store().getPlotsInWorld(ctx.worldName)) {
            if (other == null) continue;
            int oMinX = Math.min(other.getX1(), other.getX2());
            int oMaxX = Math.max(other.getX1(), other.getX2());
            int oMinZ = Math.min(other.getZ1(), other.getZ2());
            int oMaxZ = Math.max(other.getZ1(), other.getZ2());
            boolean overlaps = (ctx.minX <= oMaxX && ctx.maxX >= oMinX) && (ctx.minZ <= oMaxZ && ctx.maxZ >= oMinZ);
            if (overlaps) {
                plugin.msg().send(p, "resize-fail-overlap");
                return null;
            }
        }

        ProtectionHookManager hooks = plugin.protectionHooks();
        if (hooks != null && hooks.isAreaProtectedElsewhere(ctx.worldName, ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ)) {
            plugin.msg().send(p, "claim_external_protection_conflict");
            return null;
        }

        Plot plot = new Plot(
                UUID.randomUUID(),
                group.getLeader(),
                plugin.groups().getMemberName(group.getLeader()),
                ctx.worldName,
                ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ,
                System.currentTimeMillis()
        );
        plugin.worldRules().applyDefaults(plot);
        plot.setGroupPlot(true);
        plot.setTreasuryBalance(group.getTreasuryBalance());
        plot.setGroupId(group.getId());
        plot.setGroupName(group.getName());
        plot.setMaxMembers(Math.max(plot.getMaxMembers(), group.size()));
        plot.setPlotName(group.getName());

        for (UUID memberId : group.getMemberIds()) {
            if (memberId != null && !memberId.equals(group.getLeader())) {
                plot.setRole(memberId, "trusted");
            }
        }

        PlotClaimEvent event = new PlotClaimEvent(plot, p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        plugin.store().addPlot(plot);
        selections.remove(p.getUniqueId());
        setPlayerWand(p, null);

        if (plugin.effects() != null) plugin.effects().playClaimSuccess(p);
        else p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);

        return plot;
    }

    // ------------------------------------------------------------
    // Plot operations used by commands (restored stubs where needed)
    // ------------------------------------------------------------

    public void unclaimHere(Player p) {
        if (p == null) return;

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            plugin.msg().send(p, "unstuck_not_in_plot");
            return;
        }

        if (!plot.canManage(p, plugin)) {
            plugin.msg().send(p, "no_permission");
            return;
        }

        plugin.store().removePlot(plot.getOwner(), plot.getId());

        // Recalc claim blocks for owner if enabled
        if (!plot.isServerZone() && plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)) {
            plugin.claimBlocks().invalidateOwnerCache(plot.getOwner());
        }

        plugin.msg().send(p, "plot_unclaimed");
    }

    /**
     * 1.2.5 behavior: merge is intentionally not implemented yet,
     * but the command paths expect the method to exist.
     */
    public void attemptMerge(Player p, String mode) {
        if (p == null) return;
        plugin.msg().send(p, "error_generic");
    }

    /**
     * 1.2.5 behavior: resizing is a planned feature. Keep signature for command compatibility.
     */
    public void resizePlot(Player p, String mode, int amount) {
        if (p == null) return;
        plugin.msg().send(p, "error_generic");
    }

    // ------------------------------------------------------------
    // Config helpers
    // ------------------------------------------------------------

    private int getWorldInt(String worldName, String key, int def) {
        if (worldName == null) return def;

        ConfigurationSection overrides = plugin.cfg().raw().getConfigurationSection("claims.world_overrides");
        if (overrides == null) return def;

        ConfigurationSection worldSec = overrides.getConfigurationSection(worldName);
        if (worldSec == null) return def;

        return worldSec.getInt(key, def);
    }

    private SelectionContext getSelectionContext(Player p) {
        Selection sel = selections.get(p.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            plugin.msg().send(p, "invalid_selection");
            return null;
        }

        Location l1 = sel.getL1();
        Location l2 = sel.getL2();
        if (l1 == null || l2 == null || l1.getWorld() == null || l2.getWorld() == null) {
            plugin.msg().send(p, "invalid_selection");
            return null;
        }
        if (!l1.getWorld().equals(l2.getWorld())) {
            plugin.msg().send(p, "invalid_selection");
            return null;
        }

        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());
        int width = (maxX - minX) + 1;
        int depth = (maxZ - minZ) + 1;
        return new SelectionContext(l1, l2, l1.getWorld(), l1.getWorld().getName(), minX, maxX, minZ, maxZ, width, depth);
    }

    private static final class SelectionContext {
        private final Location l1;
        private final Location l2;
        private final World world;
        private final String worldName;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int width;
        private final int depth;
        private final int radius;
        private final int area;

        private SelectionContext(Location l1, Location l2, World world, String worldName,
                                 int minX, int maxX, int minZ, int maxZ, int width, int depth) {
            this.l1 = l1;
            this.l2 = l2;
            this.world = world;
            this.worldName = worldName;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.width = width;
            this.depth = depth;
            this.radius = Math.max(width, depth);
            this.area = width * depth;
        }
    }
}
