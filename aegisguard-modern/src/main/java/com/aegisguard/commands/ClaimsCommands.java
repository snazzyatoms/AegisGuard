package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.ClaimPricingCalculator;  // ✅ NEW: Fair Pricing Calculator
import com.aegisguard.economy.CurrencyType;  // ✅ NEW: Currency Type enum
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.*;

final class ClaimsCommands extends CommandBase {

    ClaimsCommands(AegisGuard plugin) { super(plugin); }

    void handleClaim(Player p) {
        if (!plugin.selection().hasSelection(p)) {
            sendKey(p, "must_select", "&c❌ You must select two corners with the Wand first.");
            return;
        }

        // External protection compat
        if (isSelectionProtectedElsewhere(p)) {
            sendKey(p, "claim_denied_external_generic",
                    "&c⛔ Claim denied: protected by another plugin.");
            plugin.effects().playError(p);
            return;
        }

        long area = plugin.selection().getSelectionArea(p);
        if (area <= 0) return;

        // -------------------------------------------------------
        // Determine which economy systems are active
        // -------------------------------------------------------
        boolean useVault = plugin.cfg().raw().getBoolean("economy.enabled", true)
                && plugin.cfg().raw().getBoolean("economy.use_vault", true)
                && plugin.eco() != null
                && plugin.eco().isVaultEnabled();

        boolean useClaimBlocks = plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)
                && plugin.getClaimBlockManager() != null;

        // Get pricing calculator (may be null if disabled)
        ClaimPricingCalculator pricing = plugin.getPricingCalculator();

        // -------------------------------------------------------
        // PATH A: Vault Economy (money-based claiming with fair pricing)
        // -------------------------------------------------------
        if (useVault && !useClaimBlocks) {
            double cost;

            // Use fair pricing if available, otherwise fall back to flat cost
            if (pricing != null && pricing.isEnabled()) {
                cost = pricing.calculateClaimCost(area);
            } else {
                cost = plugin.cfg().raw().getDouble("economy.claim_cost", 100.0);
            }

            // Check if player can afford
            if (!plugin.eco().canAfford(p, cost, CurrencyType.VAULT)) {
                String formatted = plugin.eco().format(cost, CurrencyType.VAULT);

                // Show breakdown if fair pricing is active
                if (pricing != null && pricing.isEnabled()) {
                    ClaimPricingCalculator.CostBreakdown breakdown = pricing.getBreakdown(area);
                    sendKey(p, "claim_cost_breakdown", "&c❌ Cannot afford this claim.");
                    sendMsg(p, "&7Area: &e" + area + " blocks");
                    if (breakdown.hasExtraCost()) {
                        sendMsg(p, "&7Base (" + breakdown.baseBlocks() + " blocks): &e" +
                                plugin.eco().format(breakdown.baseCost(), CurrencyType.VAULT));
                        sendMsg(p, "&7Expansion (" + breakdown.extraBlocks() + " blocks): &e" +
                                plugin.eco().format(breakdown.extraCost(), CurrencyType.VAULT));
                    }
                    sendMsg(p, "&7Total: &c" + formatted);
                } else {
                    sendKey(p, "claim_cannot_afford",
                            "&c❌ You need {COST} to claim this area.",
                            Map.of("COST", formatted));
                }
                plugin.effects().playError(p);
                return;
            }

            // Withdraw money and confirm claim; refund if validation rejects the claim
            String formatted = plugin.eco().format(cost, CurrencyType.VAULT);
            if (!plugin.eco().withdraw(p, cost, CurrencyType.VAULT)) {
                sendKey(p, "claim_cannot_afford",
                        "&c❌ You need {COST} to claim this area.",
                        Map.of("COST", formatted));
                plugin.effects().playError(p);
                return;
            }
            if (!plugin.selection().confirmClaim(p)) {
                plugin.eco().deposit(p, cost, CurrencyType.VAULT);
                return;
            }

            sendKey(p, "claim_success_vault",
                    "&a✔ Claimed &e{AREA}&a blocks for &6{COST}&a!",
                    Map.of("AREA", String.valueOf(area), "COST", formatted));
            plugin.effects().playConfirm(p);
            return;
        }

        // -------------------------------------------------------
        // PATH B: Claim Blocks (block-based claiming with optional multiplier)
        // -------------------------------------------------------
        if (useClaimBlocks) {
            ClaimBlockManager blocks = plugin.getClaimBlockManager();
            UUID uuid = p.getUniqueId();

            // Calculate claim block cost (with optional fair pricing multiplier)
            long blockCost;
            if (pricing != null && pricing.isEnabled()) {
                blockCost = pricing.calculateClaimBlockCost(area);
            } else {
                blockCost = area;  // 1:1 ratio
            }

            if (!blocks.canAfford(uuid, blockCost)) {
                long missing = blockCost - blocks.getAvailableBlocks(uuid);

                // Show breakdown if fair pricing adds a multiplier
                if (pricing != null && pricing.isEnabled() && blockCost > area) {
                    sendKey(p, "claim_blocks_not_enough_fair",
                            "&c❌ You need {AMOUNT} more Claim Blocks.",
                            Map.of("AMOUNT", String.valueOf(missing)));
                    sendMsg(p, "&7Area: &e" + area + " blocks");
                    sendMsg(p, "&7Cost (with multiplier): &c" + blockCost + " claim blocks");
                } else {
                    sendKey(p, "claim_blocks_not_enough",
                            "&c❌ You need {AMOUNT} more Claim Blocks.",
                            Map.of("AMOUNT", String.valueOf(missing)));
                }
                plugin.effects().playError(p);
                return;
            }

            // First claim limit check
            if (!blocks.getOrCreate(uuid).hasClaimedStarter() && !p.hasPermission("aegis.admin.bypass-limits")) {
                long maxStarter = plugin.cfg().raw().getLong("claim_blocks.first_claim_limit.max_area", 1000);
                if (area > maxStarter) {
                    sendKey(p, "claim_blocks_first_claim_limit",
                            "&c❌ First claim limit: max area &6{MAX}&c.",
                            Map.of("MAX", String.valueOf(maxStarter)));
                    plugin.effects().playError(p);
                    return;
                }
            }

            // Confirm the claim (denial messages are sent by the selection service)
            if (!plugin.selection().confirmClaim(p)) return;

            Plot at = plugin.store().getPlotAt(p.getLocation());
            if (at != null && uuid.equals(at.getOwner())) {
                if (!blocks.getOrCreate(uuid).hasClaimedStarter()) {
                    blocks.setStarterClaimed(uuid, true);
                }
                blocks.getUsedBlocks(uuid);
            }

            // Show success with cost info if multiplier was applied
            if (pricing != null && pricing.isEnabled() && blockCost > area) {
                sendKey(p, "claim_success_blocks_fair",
                        "&a✔ Claimed &e{AREA}&a blocks (cost: &6{COST}&a claim blocks)!",
                        Map.of("AREA", String.valueOf(area), "COST", String.valueOf(blockCost)));
            }
            return;
        }

        // -------------------------------------------------------
        // PATH C: No economy system (free claiming)
        // -------------------------------------------------------
        plugin.selection().confirmClaim(p);
    }

    public void runClaim(Player p) {
        handleClaim(p);
    }

    void handleQuickClaim(Player p, String[] args) {
        boolean enabled = true;
        int defRadius = 5;
        int maxRadius = 25;
        try {
            if (plugin.cfg() != null) {
                enabled = plugin.cfg().raw().getBoolean("claims.quick_claim.enabled", true);
                defRadius = plugin.cfg().raw().getInt("claims.quick_claim.default_radius", 5);
                maxRadius = plugin.cfg().raw().getInt("claims.quick_claim.max_radius", 25);
            }
        } catch (Throwable ignored) {}
        if (!enabled) {
            sendKey(p, "quickclaim_disabled", "&cQuick-Claim is disabled on this server.");
            plugin.effects().playError(p);
            return;
        }
        int radius = Math.max(1, defRadius);
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sendKey(p, "quickclaim_usage", "&eUsage: /ag quickclaim [radius]");
                plugin.effects().playError(p);
                return;
            }
        }
        if (radius < 1) {
            sendKey(p, "quickclaim_usage", "&eUsage: /ag quickclaim [radius]");
            plugin.effects().playError(p);
            return;
        }
        int cap = Math.max(1, maxRadius);
        if (radius > cap) {
            sendKey(p, "quickclaim_radius_capped", "&eRadius capped at {MAX}.",
                    Map.of("MAX", String.valueOf(cap)));
            radius = cap;
        }
        plugin.selection().setSelectionAround(p, radius);
        handleClaim(p);
    }

    void handleCostPreview(Player p) {
        if (!plugin.selection().hasSelection(p)) {
            sendKey(p, "must_select", "&c❌ Select two corners first to preview cost.");
            return;
        }

        long area = plugin.selection().getSelectionArea(p);
        if (area <= 0) {
            sendKey(p, "invalid_selection", "&cInvalid selection.");
            return;
        }

        ClaimPricingCalculator pricing = plugin.getPricingCalculator();

        sendMsg(p, "&8&m--------------------------");
        sendKey(p, "cost_preview_title", "&6&l📊 Claim Cost Preview");
        sendMsg(p, "&8&m--------------------------");
        sendMsg(p, "&7Selection Area: &e" + area + " blocks");

        // Show fair pricing breakdown if enabled
        if (pricing != null && pricing.isEnabled()) {
            ClaimPricingCalculator.CostBreakdown breakdown = pricing.getBreakdown(area);

            sendMsg(p, "&7Base Area: &a" + breakdown.baseBlocks() + " blocks");
            if (breakdown.hasExtraCost()) {
                sendMsg(p, "&7Expansion Area: &c" + breakdown.extraBlocks() + " blocks");
            }
            sendMsg(p, "&8&m--------------------------");

            // Vault money cost
            if (plugin.eco() != null && plugin.eco().isVaultEnabled()) {
                String baseCostStr = plugin.eco().format(breakdown.baseCost(), CurrencyType.VAULT);
                sendMsg(p, "&7Base Cost: &a" + baseCostStr);

                if (breakdown.hasExtraCost()) {
                    String extraCostStr = plugin.eco().format(breakdown.extraCost(), CurrencyType.VAULT);
                    sendMsg(p, "&7Expansion Cost: &c" + extraCostStr);
                }

                String totalCostStr = plugin.eco().format(breakdown.totalCost(), CurrencyType.VAULT);
                sendMsg(p, "&6Total Cost: &e&l" + totalCostStr);
            }
        } else {
            // Legacy flat pricing
            double cost = plugin.cfg().raw().getDouble("economy.claim_cost", 100.0);
            if (plugin.eco() != null && plugin.eco().isVaultEnabled()) {
                String formatted = plugin.eco().format(cost, CurrencyType.VAULT);
                sendMsg(p, "&7Flat Cost: &e" + formatted);
            }
        }

        // Claim blocks cost
        if (plugin.getClaimBlockManager() != null) {
            long blockCost;
            if (pricing != null && pricing.isEnabled()) {
                blockCost = pricing.calculateClaimBlockCost(area);
            } else {
                blockCost = area;
            }
            sendMsg(p, "&7Claim Blocks: &b" + blockCost);
            if (blockCost > area) {
                sendMsg(p, "&8(Multiplier applied for large claim)");
            }
        }

        sendMsg(p, "&8&m--------------------------");
        plugin.effects().playConfirm(p);
    }

    private boolean isSelectionProtectedElsewhere(Player p) {
        try {
            if (plugin.protectionHooks() == null) return false;

            Object mgr = plugin.protectionHooks();

            try {
                Method isEnabled = mgr.getClass().getMethod("isEnabled");
                Object enabled = isEnabled.invoke(mgr);
                if (enabled instanceof Boolean b && !b) return false;
            } catch (Throwable ignored) {}

            try {
                Method getPol = mgr.getClass().getMethod("getOverlapPolicy");
                Object pol = getPol.invoke(mgr);
                if (pol != null && "AEGIS_WINS".equalsIgnoreCase(pol.toString())) {
                    return false;
                }
            } catch (Throwable ignored) {}

            Location a = getSelectionCorner(p, true);
            Location b = getSelectionCorner(p, false);

            if (a == null || b == null || a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
                try {
                    Method isProt = mgr.getClass().getMethod("isProtectedElsewhere", Location.class);
                    Object result = isProt.invoke(mgr, p.getLocation());
                    return result instanceof Boolean bb && bb;
                } catch (Throwable ignored) {
                    return false;
                }
            }

            int x1 = a.getBlockX();
            int z1 = a.getBlockZ();
            int x2 = b.getBlockX();
            int z2 = b.getBlockZ();
            String world = a.getWorld().getName();

            try {
                Method areaMethod = mgr.getClass().getMethod("isAreaProtectedElsewhere", String.class, int.class, int.class, int.class, int.class);
                Object result = areaMethod.invoke(mgr, world, x1, z1, x2, z2);
                return result instanceof Boolean bb && bb;
            } catch (Throwable ignored) {
                try {
                    Method isProt = mgr.getClass().getMethod("isProtectedElsewhere", Location.class);
                    Object result = isProt.invoke(mgr, p.getLocation());
                    return result instanceof Boolean bb && bb;
                } catch (Throwable ignored2) {
                    return false;
                }
            }

        } catch (Throwable ignored) {
            return false;
        }
    }

    private Location getSelectionCorner(Player p, boolean first) {
        Object sel = plugin.selection();
        if (sel == null) return null;

        String[] candidates = first
                ? new String[]{"getPos1", "getFirstPos", "getFirstPosition", "getSelectionPos1", "getPrimaryPos", "getPrimaryPosition"}
                : new String[]{"getPos2", "getSecondPos", "getSecondPosition", "getSelectionPos2", "getSecondaryPos", "getSecondaryPosition"};

        for (String name : candidates) {
            try {
                Method m = sel.getClass().getMethod(name, Player.class);
                Object out = m.invoke(sel, p);
                if (out instanceof Location loc) return loc;
            } catch (Throwable ignored) {}
        }

        for (String name : candidates) {
            try {
                Method m = sel.getClass().getMethod(name, UUID.class);
                Object out = m.invoke(sel, p.getUniqueId());
                if (out instanceof Location loc) return loc;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    void handleCreateSubplot(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot to do that.");
            return;
        }

        if (!plugin.cfg().isZoningEnabled()) {
            sendKey(p, "zoning_disabled", "&cZoning is disabled.");
            return;
        }

        if (!plot.canManage(p, plugin)) {
            sendKey(p, "not_plot_owner", "&cYou cannot manage this plot.");
            return;
        }

        if (!plugin.selection().hasSelection(p)) {
            sendKey(p, "must_select", "&c❌ You must select two corners with the Wand first.");
            sendKey(p, "subplot_usage",
                    "&eUse &b/ag wand &e, mark two corners inside your claim, then run &b/ag subplot [name]&e.");
            return;
        }

        String customName = null;
        if (args.length >= 2) {
            customName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (customName.isBlank()) {
                customName = null;
            }
        }

        boolean created = plugin.gui().zoning().createZoneFromSelection(p, plot, customName);
        if (created) {
            sendKey(p, "subplot_created_hint",
                    "&7This subplot now appears in the &bZone Manager&7 for rent, room, or market setup.");
        }
    }

    void handleBiome(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "biome_not_in_plot", "&cStand inside your claim to change its biome.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cYou do not have permission to do that.");
            return;
        }
        if (plugin.gui().biome() == null) {
            sendKey(p, "biome_unavailable", "&cPlot biomes are unavailable on this server.");
            return;
        }
        plugin.gui().biome().open(p, plot);
    }

}