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
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    @EventHandler(ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isAegisWand(item)) return;

        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        // Sneak + right-click: staff context menu (Doctor + Convert → Server Plot + Staff Tools).
        // Preserves Doctor access while adding convert without changing corner-selection clicks.
        if (isServerWand(item) && player.isSneaking()
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.runMain(player, () -> plugin.gui().convertToServer().openStaffWandMenu(player));
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedBlock() == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        Location selected = event.getClickedBlock().getLocation();

        if (item != null && item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(SERVER_WAND_KEY, PersistentDataType.BYTE)) {
                setPlayerWand(player, "server_claim_wand");
            } else {
                setPlayerWand(player, "claim_wand");
            }
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            setLoc1(player, selected);
            player.sendMessage(color(plugin.gui().tr(player, "selection_corner1",
                    "&aFirst corner selected: &f{X}, {Y}, {Z}",
                    java.util.Map.of(
                            "X", String.valueOf(selected.getBlockX()),
                            "Y", String.valueOf(selected.getBlockY()),
                            "Z", String.valueOf(selected.getBlockZ())))));
            if (plugin.effects() != null) plugin.effects().playConfirm(player);
            return;
        }

        setLoc2(player, selected);
        player.sendMessage(color(plugin.gui().tr(player, "selection_corner2",
                "&bSecond corner selected: &f{X}, {Y}, {Z}",
                java.util.Map.of(
                        "X", String.valueOf(selected.getBlockX()),
                        "Y", String.valueOf(selected.getBlockY()),
                        "Z", String.valueOf(selected.getBlockZ())))));

        long area = getSelectionArea(player);
        if (area > 0L) {
            String confirmCommand = isServerWand(item) ? "/agadmin claim" : "/ag claim";
            player.sendMessage(color(plugin.gui().tr(player, "selection_area_confirm",
                    "&7Selection area: &e{AREA} blocks &7- use &a{COMMAND} &7to confirm.",
                    java.util.Map.of("AREA", String.valueOf(area), "COMMAND", confirmCommand))));
        }

        if (plugin.effects() != null) plugin.effects().playConfirm(player);
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

    /**
     * Quick-Claim helper: sets the player's two selection corners to a square of the
     * given radius centered on their current location. A radius of 5 yields an 11x11 plot.
     */
    public void setSelectionAround(Player p, int radius) {
        if (p == null) return;
        int r = Math.max(0, radius);
        Location center = p.getLocation();
        if (center == null || center.getWorld() == null) return;
        Location c1 = new Location(center.getWorld(), center.getBlockX() - r, center.getBlockY(), center.getBlockZ() - r);
        Location c2 = new Location(center.getWorld(), center.getBlockX() + r, center.getBlockY(), center.getBlockZ() + r);
        setLoc1(p, c1);
        setLoc2(p, c2);
    }

    /**
     * Quick-Claim: builds a square selection of the given radius around the player and
     * delegates to {@link #confirmClaim(Player)} so ALL existing validation and economy
     * (overlap, min/max radius, world rules, claim blocks, max claims, PlotClaimEvent) run.
     */
    public void quickClaim(Player p, int radius) {
        if (p == null) return;
        setSelectionAround(p, radius);
        confirmClaim(p);
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

    private boolean isServerWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE);
    }

    private String color(String text) {
        return text == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
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

        World selectedWorld = Bukkit.getWorld(ctx.worldName);
        if (!isServerClaim && !p.hasPermission("aegis.admin.bypass")
                && !plugin.worldRules().allowClaims(selectedWorld)) {
            denyClaim(p, "claims_disabled_in_world", null,
                    "claim_hint_world_disabled", "&7Claiming is turned off in this world. Try a claimable world.");
            return;
        }

        // Limits (supports 1.2.5 + 1.2.6 config styles)
        int maxRadius = getWorldInt(ctx.worldName, "max_radius",
                plugin.cfg().raw().getInt("claims.max_radius_global",
                        plugin.cfg().raw().getInt("claims.max_radius", 200)));

        int maxArea = getWorldInt(ctx.worldName, "max_area",
                plugin.cfg().raw().getInt("claims.max_area", 50000));

        if (rejectIfOutsideClaimLimits(p, ctx, maxRadius, maxArea)) return;

        // Guardrail: enforce max_claims_per_player for personal claims (quick-claim + normal claim).
        // Skipped for server claims and admins/bypass, matching the PlotMarketGUI purchase cap.
        if (!isServerClaim && !hasClaimLimitBypass(p)) {
            int maxClaims = plugin.cfg().getWorldMaxClaims(ctx.world);
            if (maxClaims > 0) {
                int current = plugin.store().getPlots(p.getUniqueId()).size();
                if (current >= maxClaims) {
                    denyClaim(p, "max_claims_reached", Map.of("AMOUNT", String.valueOf(maxClaims)),
                            "claim_hint_max_claims",
                            "&7Unclaim a plot you no longer need, or ask staff to raise your limit.");
                    return;
                }
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
                denyClaim(p, "claim_overlap", null,
                        "claim_hint_overlap",
                        "&7Move to open land or pick a spot that doesn't touch another claim.");
                return;
            }
        }

        // Compatibility: if other protection plugin present, yield if configured
        ProtectionHookManager hooks = plugin.protectionHooks();
        if (hooks != null && hooks.isAreaProtectedElsewhere(ctx.worldName, ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ)) {
            denyClaim(p, "claim_external_protection_conflict", null,
                    "claim_hint_external", "&7That land is protected by another plugin. Pick a different spot.");
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
                denyClaim(p, "first_claim_too_large", null,
                        "claim_hint_first_claim", "&7Try a smaller first claim, then expand it later.");
                return;
            }

            boolean perBlock = plugin.cfg().raw().getBoolean("claim_blocks.require_per_block", true);
            int required = perBlock ? ctx.area : 1;

            if (!plugin.claimBlocks().canAfford(p.getUniqueId(), required)) {
                denyClaim(p, "claim_blocks_not_enough", null,
                        "claim_hint_need_blocks", "&7Earn or buy more Claim Blocks, then try again.");
                return;
            }
            // Land is counted in used plot area after the plot is created.
            // Do not also spend() that area or expansion will drive the ledger negative.
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
        if (!isServerClaim && plugin.claimBlocks() != null) {
            plugin.claimBlocks().invalidateOwnerCache(p.getUniqueId());
        }

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

        plugin.msg().send(p, isServerClaim ? "admin-zone-created" : "plot_claimed");

        if (isServerClaim && plugin.serverZoneStewardship() != null) {
            plugin.serverZoneStewardship().grantSteward(p, plot, true);
        }

        // Discord webhook (if configured)
        if (plugin.getDiscord() != null) {
            try {
                DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject();
                embed.setTitle(plugin.console().plain(
                        isServerClaim ? "discord_claim_title_server" : "discord_claim_title_plot",
                        isServerClaim ? "Admin Zone Created" : "Plot Claimed"));
                embed.setDescription(plugin.console().plain(
                        "discord_claim_description",
                        "{PLAYER} claimed a plot.",
                        java.util.Map.of("PLAYER", p.getName())));
                embed.addField(plugin.console().plain("discord_claim_field_world", "World"), ctx.worldName, true);
                embed.addField(plugin.console().plain("discord_claim_field_area", "Area"), String.valueOf(ctx.area), true);
                embed.addField(plugin.console().plain("discord_claim_field_owner", "Owner"),
                        isServerClaim
                                ? plugin.console().plain("discord_claim_owner_server", "Server")
                                : p.getName(),
                        true);
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

        World selectedWorld = Bukkit.getWorld(ctx.worldName);
        if (!p.hasPermission("aegis.admin.bypass") && !plugin.worldRules().allowClaims(selectedWorld)) {
            plugin.msg().send(p, "claims_disabled_in_world");
            plugin.effects().playError(p);
            return null;
        }

        int maxRadius = getWorldInt(ctx.worldName, "max_radius",
                plugin.cfg().raw().getInt("claims.max_radius_global",
                        plugin.cfg().raw().getInt("claims.max_radius", 200)));
        int maxArea = getWorldInt(ctx.worldName, "max_area",
                plugin.cfg().raw().getInt("claims.max_area", 50000));

        if (rejectIfOutsideClaimLimits(p, ctx, maxRadius, maxArea)) return null;

        for (Plot other : plugin.store().getPlotsInWorld(ctx.worldName)) {
            if (other == null) continue;
            int oMinX = Math.min(other.getX1(), other.getX2());
            int oMaxX = Math.max(other.getX1(), other.getX2());
            int oMinZ = Math.min(other.getZ1(), other.getZ2());
            int oMaxZ = Math.max(other.getZ1(), other.getZ2());
            boolean overlaps = (ctx.minX <= oMaxX && ctx.maxX >= oMinX) && (ctx.minZ <= oMaxZ && ctx.maxZ >= oMinZ);
            if (overlaps) {
                plugin.msg().send(p, "claim_overlap");
                return null;
            }
        }

        ProtectionHookManager hooks = plugin.protectionHooks();
        if (hooks != null && hooks.isAreaProtectedElsewhere(ctx.worldName, ctx.minX, ctx.minZ, ctx.maxX, ctx.maxZ)) {
            plugin.msg().send(p, "claim_external_protection_conflict");
            return null;
        }

        boolean claimBlocksEnabled = plugin.cfg().raw().getBoolean("claim_blocks.enabled", true);
        if (claimBlocksEnabled && !p.hasPermission("aegis.admin.bypass-limits")) {
            boolean perBlock = plugin.cfg().raw().getBoolean("claim_blocks.require_per_block", true);
            int required = perBlock ? ctx.area : 1;
            UUID payer = group.getLeader() != null ? group.getLeader() : p.getUniqueId();
            if (plugin.claimBlocks() == null || !plugin.claimBlocks().canAfford(payer, required)) {
                plugin.msg().send(p, "claim_blocks_not_enough");
                return null;
            }
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
        if (plugin.claimBlocks() != null) plugin.claimBlocks().invalidateOwnerCache(group.getLeader());
        selections.remove(p.getUniqueId());
        setPlayerWand(p, null);

        if (plugin.effects() != null) plugin.effects().playClaimSuccess(p);

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
     * Retained as a disabled no-op for binary compatibility with older API consumers.
     */
    @Deprecated(forRemoval = false)
    public void resizePlot(Player p, String mode, int amount) {
        if (p == null) return;
        plugin.msg().send(p, "error_generic");
    }

    // ------------------------------------------------------------
    // Config helpers
    // ------------------------------------------------------------

    private boolean rejectIfOutsideClaimLimits(Player p, SelectionContext ctx, int maxRadius, int maxArea) {
        if (p.hasPermission("aegis.admin.bypass") || p.hasPermission("aegis.admin.bypass-limits")) {
            return false;
        }
        int halfWidth = ctx.width / 2;
        int halfDepth = ctx.depth / 2;
        int minRadius = Math.max(1, plugin.cfg().getWorldMinRadius(ctx.world));
        if (halfWidth < minRadius || halfDepth < minRadius) {
            denyClaim(p, "claim_too_small", Map.of("MIN", String.valueOf(minRadius)),
                    "claim_hint_too_small",
                    "&7Expand your selection so both sides meet the minimum radius.");
            return true;
        }
        int worldMax = plugin.cfg().getWorldMaxRadius(ctx.world);
        if (ctx.radius > maxRadius || Math.max(halfWidth, halfDepth) > worldMax) {
            denyClaim(p, "claim_too_large", null,
                    "claim_hint_too_large",
                    "&7Shrink the selection, or use /ag quickclaim for a default-size plot.");
            return true;
        }
        if (ctx.area > maxArea) {
            denyClaim(p, "claim_too_large", null,
                    "claim_hint_too_large",
                    "&7Shrink the selection, or use /ag quickclaim for a default-size plot.");
            return true;
        }
        return false;
    }

    /**
     * Claim-limit bypass used by personal claims (including Quick-Claim). Matches the
     * market purchase cap: staff and explicit bypass permissions skip the cap.
     */
    private boolean hasClaimLimitBypass(Player p) {
        if (p == null) return false;
        if (p.hasPermission("aegis.admin.bypass") || p.hasPermission("aegis.admin.bypass-limits")) {
            return true;
        }
        try {
            return plugin.isAdmin(p);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Sends the existing denial key plus a one-line next-step hint (English fallback,
     * no new language-pack keys) and plays the error sound.
     */
    private void denyClaim(Player p, String key, Map<String, String> placeholders, String hintKey, String hintFallback) {
        if (p == null) return;
        try {
            if (plugin.msg() != null) {
                if (placeholders != null && !placeholders.isEmpty()) {
                    plugin.msg().send(p, key, placeholders);
                } else {
                    plugin.msg().send(p, key);
                }
            }
        } catch (Throwable ignored) {}
        if (hintFallback != null && !hintFallback.isBlank()) {
            String hint = hintFallback;
            try {
                if (plugin.gui() != null) {
                    hint = plugin.gui().tr(p, hintKey, hintFallback);
                }
            } catch (Throwable ignored) {}
            if (hint != null && !hint.isBlank()) {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', hint));
            }
        }
        if (plugin.effects() != null) plugin.effects().playError(p);
    }

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
