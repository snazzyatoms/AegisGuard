package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.ClaimPricingCalculator;  // ✅ NEW: Fair Pricing Calculator
import com.aegisguard.economy.CurrencyType;  // ✅ NEW: Currency Type enum
import com.aegisguard.groups.PlotGroup;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.territory.TerritoryLifeService;
import com.aegisguard.util.TeleportUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final ClaimBlockExchangeService exchange;

    private static final String[] SUB_COMMANDS = {
            "wand", "menu", "claim", "unclaim", "help",
            "setspawn", "home", "welcome", "farewell",
            "sell", "unsell", "rent", "unrent", "rental", "market", "auction",
            "kick", "ban", "unban", "visit",
            "level", "zone", "subplot", "subzone", "like",
            "rename", "stuck", "setdesc",
            "consume", "ledger", "blocks",
            "group", "discover", "favorite", "activity",
            // ✅ Added: reload support (Codex + config)
            "reload", "refresh",
            // ✅ NEW: cost preview command
            "cost",
            // ✅ NEW: notify command for toggling notifications
            "notify"
    };

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
        this.exchange = plugin.exchange();
    }

    // --------------------------------------------------
    // LANGUAGE (Codex helpers)
    // --------------------------------------------------

    private String tr(CommandSender sender, String key, String fallback) {
        return tr(sender, key, fallback, Collections.emptyMap());
    }

    private String tr(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        // If Codex is missing, use fallback.
        if (plugin.codex() == null || key == null || key.isBlank()) {
            return applyPlaceholders(fallback, placeholders);
        }

        try {
            String value = plugin.codex().tr(sender, key, placeholders);

            // IMPORTANT: CodexEngine returns the key if missing.
            if (value == null || value.isBlank() || value.equalsIgnoreCase(key)) {
                return applyPlaceholders(fallback, placeholders);
            }

            return value;
        } catch (Throwable ignored) {
            return applyPlaceholders(fallback, placeholders);
        }
    }

    private void sendKey(CommandSender sender, String key, String fallback) {
        sendKey(sender, key, fallback, Collections.emptyMap());
    }

    private void sendKey(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        String msg = tr(sender, key, fallback, placeholders);
        sendMsg(sender, msg);
    }

    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) return input;

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isEmpty()) continue;
            String v = (e.getValue() == null) ? "" : e.getValue();
            out = out.replace("{" + k + "}", v);
        }
        return out;
    }

    private void notifyGroup(PlotGroup group, UUID excludePlayer,
                             String titleKey, String titleFallback,
                             String messageKey, String messageFallback,
                             Map<String, String> placeholders) {
        if (plugin.notifications() == null || group == null) return;
        if (!plugin.getConfig().getBoolean("group_plots.notifications.enabled", true)) return;

        plugin.notifications().notifyGroupMembers(
                group,
                excludePlayer,
                titleKey,
                titleFallback,
                messageKey,
                messageFallback,
                placeholders
        );
    }

    private void notifyPlot(Plot plot, UUID excludePlayer,
                            String titleKey, String titleFallback,
                            String messageKey, String messageFallback,
                            Map<String, String> placeholders) {
        if (plugin.notifications() == null || plot == null) return;
        plugin.notifications().notifyPlotMembers(
                plot,
                excludePlayer,
                titleKey,
                titleFallback,
                messageKey,
                messageFallback,
                placeholders
        );
    }

    private void notifyLowTreasuryIfNeeded(PlotGroup group, Plot linkedPlot, UUID excludePlayer) {
        if (plugin.notifications() == null || group == null) return;
        if (!plugin.getConfig().getBoolean("group_plots.notifications.enabled", true)) return;

        double threshold = Math.max(0.0D, plugin.getConfig().getDouble("group_plots.notifications.low_treasury_threshold", 250.0D));
        if (threshold <= 0.0D) return;

        double balance = linkedPlot != null ? linkedPlot.getTreasuryBalance() : group.getTreasuryBalance();
        if (balance > threshold) return;

        String formattedBalance = plugin.eco() != null && plugin.eco().isVaultReady()
                ? plugin.eco().format(balance, CurrencyType.VAULT)
                : String.format(Locale.US, "%.2f", balance);

        notifyGroup(
                group,
                excludePlayer,
                "notify_group_title",
                "&6Group Update",
                "notify_group_treasury_low",
                "&e{GROUP}'s treasury is running low. Remaining balance: &6{BALANCE}&e.",
                Map.of(
                        "GROUP", group.getName(),
                        "BALANCE", formattedBalance
                )
        );
    }

    private String getReadablePlotName(Plot plot) {
        if (plot == null) return "Claim";
        String entryTitle = plot.getEntryTitle();
        if (entryTitle != null && !entryTitle.isBlank()) return entryTitle;
        String plotName = plot.getPlotName();
        if (plotName != null && !plotName.isBlank()) return plotName;
        String ownerName = plot.getOwnerName();
        if (ownerName != null && !ownerName.isBlank()) return ownerName + "'s Claim";
        return "Claim";
    }

    // --------------------------------------------------
    // Command
    // --------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // ✅ Allow console for reload/help
        if (!(sender instanceof Player p)) {
            if (args.length > 0) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("reload") || sub.equals("refresh")) {
                    handleReload(sender, args);
                    return true;
                }
                if (sub.equals("help")) {
                    sendHelp(sender);
                    return true;
                }
            }

            // Use Codex for console too (default language).
            sendKey(sender, "players_only", "&cError: This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().openMain(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> {
                if (SelectionService.playerHasAnyWand(p)) {
                    sendKey(p, "wand_already_on", "&eYou already have the Aegis Scepter in your inventory.");
                    plugin.effects().playError(p);
                    break;
                }
                p.getInventory().addItem(createScepter(p));
                sendKey(p, "wand_given", "&a⚡ You received the Aegis Scepter.");
                plugin.effects().playConfirm(p);
            }

            case "menu" -> plugin.gui().openMain(p);

            case "claim" -> handleClaim(p);

            case "unclaim" -> plugin.selection().unclaimHere(p);

            case "kick" -> handleKick(p, args);

            case "ban" -> handleBan(p, args);

            case "unban" -> handleUnban(p, args);

            case "visit" -> {
                if (!plugin.cfg().isTravelSystemEnabled()) {
                    sendKey(p, "travel_system_disabled", "&cTravel system is disabled.");
                    return true;
                }
                plugin.gui().visit().open(p, 0, false);
            }

            case "setspawn" -> handleSetSpawn(p);

            case "home" -> handleHome(p);

            case "welcome" -> handleWelcomeFarewell(p, args, true);

            case "farewell" -> handleWelcomeFarewell(p, args, false);

            case "stuck" -> handleStuck(p);

            case "rename" -> handleRename(p, args);

            case "setdesc" -> handleSetDesc(p, args);

            case "market" -> openMarketMenu(p, args);

            case "sell" -> handleSell(p, args);

            case "unsell" -> handleUnsell(p);

            case "rent" -> handleRentListing(p, args);

            case "unrent" -> handleUnrent(p);

            case "rental" -> handleRentalContract(p, args);

            case "discover" -> handleDiscover(p, args);

            case "favorite" -> handleFavorite(p);

            case "activity" -> handleActivity(p);

            case "auction" -> plugin.gui().auction().open(p, 0);

            case "level" -> openLevelMenu(p);

            case "zone" -> openZoneMenu(p);

            case "subplot", "subzone" -> handleCreateSubplot(p, args);

            case "like" -> handleLike(p);

            case "consume" -> plugin.selection().manualConsumeWand(p);

            case "ledger" -> showLedger(p);

            case "blocks" -> handleBlocks(p, args);

            case "group" -> handleGroup(p, args);

            // ✅ Added: /aegis reload [soft|nogui]
            case "reload", "refresh" -> handleReload(p, args);

            // ✅ NEW: /aegis cost - Preview claim cost
            case "cost" -> handleCostPreview(p);

            // ✅ NEW: /aegis notify - Manage player notifications (v1.2.6 enhanced)
            case "notify" -> handleNotify(p, args);

            case "help" -> sendHelp(p);

            default -> sendHelp(p);
        }

        return true;
    }

    // --------------------------------------------------
    // Reload (Config + Codex)
    // --------------------------------------------------

    private void handleReload(CommandSender sender, String[] args) {
        // Console is allowed. Players need admin permission.
        if (sender instanceof Player pp) {
            if (!plugin.isAdmin(pp) && !pp.hasPermission("aegis.reload")) {
                sendKey(sender, "reload_no_perm", "&cError: You do not have permission for this.");
                plugin.effects().playError(pp);
                return;
            }
        }

        boolean refreshGuis = true;

        // Optional: /aegis reload soft  (or nogui) will avoid closing open GUIs.
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("soft") || mode.equals("nogui") || mode.equals("noguis") || mode.equals("no-gui") || mode.equals("no-guis")) {
                refreshGuis = false;
            }
        }

        sendKey(sender, "reload_start", "&7Reloading AegisGuard settings and language packs...");

        long start = System.currentTimeMillis();
        try {
            // This should re-read config + re-seed missing bundles + reload Codex
            plugin.reloadAegisGuard(refreshGuis);
        } catch (Throwable t) {
            sendKey(sender, "reload_failed", "&cReload failed: &7{ERROR}", Map.of("ERROR", String.valueOf(t.getMessage())));
            if (sender instanceof Player pp) plugin.effects().playError(pp);
            return;
        }

        long ms = System.currentTimeMillis() - start;
        sendKey(sender, "reload_done", "&aReload complete. &7({MS}ms)", Map.of("MS", String.valueOf(ms)));
        if (sender instanceof Player pp) plugin.effects().playConfirm(pp);
    }

    // --------------------------------------------------
    // ✅ UPDATED: CLAIM with Fair Pricing Integration
    // --------------------------------------------------

    private void handleClaim(Player p) {
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

            // Withdraw money and confirm claim
            plugin.eco().withdraw(p, cost, CurrencyType.VAULT);
            plugin.selection().confirmClaim(p);

            String formatted = plugin.eco().format(cost, CurrencyType.VAULT);
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

            // Confirm the claim
            plugin.selection().confirmClaim(p);

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

    // --------------------------------------------------
    // ✅ NEW: Cost Preview Command (/ag cost)
    // --------------------------------------------------

    private void handleCostPreview(Player p) {
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

    // --------------------------------------------------
    // Protection Check Helpers
    // --------------------------------------------------

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

    // --------------------------------------------------
    // Ledger (Claim Blocks)
    // --------------------------------------------------

    private void showLedger(Player p) {
        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) {
            sendKey(p, "claim_blocks_disabled", "&cClaim Blocks are disabled on this server.");
            return;
        }

        long total = mgr.getTotalBlocks(p.getUniqueId());
        long used = mgr.getUsedBlocks(p.getUniqueId());
        long avail = mgr.getAvailableBlocks(p.getUniqueId());

        sendMsg(p, "&8&m------------------------");
        sendKey(p, "blocks_ledger_title", "&6&lLand Ledger &7({PLAYER})",
                Map.of("PLAYER", p.getName()));
        sendKey(p, "blocks_ledger_total", "&7Total Capacity: &e{TOTAL}",
                Map.of("TOTAL", String.valueOf(total)));
        sendKey(p, "blocks_ledger_used", "&7Used Land: &c{USED}",
                Map.of("USED", String.valueOf(used)));
        sendKey(p, "blocks_ledger_available", "&7Available: &a{AVAILABLE}",
                Map.of("AVAILABLE", String.valueOf(avail)));
        sendMsg(p, "&8&m------------------------");
    }

    // --------------------------------------------------
    // ClaimBlocks Exchange (/ag blocks ...)
    // --------------------------------------------------

    private void handleBlocks(Player p, String[] args) {
        // /ag blocks
        if (args.length == 1) {
            showLedger(p);
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> {
                sendMsg(p, "&8&m------------------------");
                sendMsg(p, "&6&lClaimBlocks Exchange");
                sendMsg(p, "&e/ag blocks &7- show your ledger");
                sendMsg(p, "&e/ag blocks rates &7- view buy/sell rates");
                sendMsg(p, "&e/ag blocks buy <amount> &7- buy claimblocks");
                sendMsg(p, "&e/ag blocks sell <amount> &7- sell claimblocks");
                sendMsg(p, "&e/ag blocks earnings [on|off|status] &7- manage passive block earnings");
                sendMsg(p, "&8&m------------------------");
            }

            case "rates", "rate", "prices", "price" -> {
                List<String> lines = exchange.getRatesLines(p);
                for (String line : lines) sendMsg(p, line);
            }

            case "buy" -> {
                if (args.length < 3) {
                    sendKey(p, "blocks_buy_usage", "&cUsage: /ag blocks buy <amount>");
                    return;
                }
                long amount = parsePositiveLong(args[2]);
                if (amount <= 0) {
                    sendKey(p, "blocks_amount_positive", "&cAmount must be a positive number.");
                    return;
                }

                ClaimBlockExchangeService.ExchangeResult res = exchange.buy(p, amount);
                if (!res.success()) {
                    sendMsg(p, res.message());
                    plugin.effects().playError(p);
                    return;
                }

                sendMsg(p, res.message());
                plugin.effects().playConfirm(p);
            }

            case "sell" -> {
                if (args.length < 3) {
                    sendKey(p, "blocks_sell_usage", "&cUsage: /ag blocks sell <amount>");
                    return;
                }
                long amount = parsePositiveLong(args[2]);
                if (amount <= 0) {
                    sendKey(p, "blocks_amount_positive", "&cAmount must be a positive number.");
                    return;
                }

                ClaimBlockExchangeService.ExchangeResult res = exchange.sell(p, amount);
                if (!res.success()) {
                    sendMsg(p, res.message());
                    plugin.effects().playError(p);
                    return;
                }

                sendMsg(p, res.message());
                plugin.effects().playConfirm(p);
            }

            case "earnings", "earning", "passive" -> handleBlockEarnings(p, args);

            default -> {
                // If someone typed something else, show ledger + hint
                showLedger(p);
                sendMsg(p, "&7Tip: &e/ag blocks help &7for exchange commands.");
            }
        }
    }

    private long parsePositiveLong(String s) {
        try {
            long v = Long.parseLong(s);
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void handleBlockEarnings(Player p, String[] args) {
        boolean serverEnabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        boolean optOutAllowed = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.player_opt_out_allowed", true);

        if (!serverEnabled) {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_server_disabled",
                            "&cPassive Claim Block earnings are disabled by the server.")));
            plugin.effects().playError(p);
            return;
        }

        if (args.length < 3 || args[2].equalsIgnoreCase("status")) {
            boolean enabled = plugin.getClaimBlockManager().isPlaytimeEarningEnabled(p.getUniqueId());
            sendMsg(p, "&8&m------------------------");
            sendMsg(p, "&6&lPassive Claim Block Earnings");
            sendMsg(p, "&7Server Playtime Rewards: " + (serverEnabled ? "&aEnabled" : "&cDisabled"));
            sendMsg(p, "&7Player Toggle Allowed: " + (optOutAllowed ? "&aYes" : "&cNo"));
            sendMsg(p, "&7Your Status: " + (enabled ? "&aEnabled" : "&cDisabled"));
            sendMsg(p, "&8&m------------------------");
            return;
        }

        if (!optOutAllowed) {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_toggle_locked",
                            "&cThis server does not allow players to change passive earnings.")));
            plugin.effects().playError(p);
            return;
        }

        Boolean desired = parseBooleanArg(args[2]);
        if (desired == null) {
            sendMsg(p, "&cUsage: /ag blocks earnings <on|off|status>");
            plugin.effects().playError(p);
            return;
        }

        plugin.getClaimBlockManager().setPlaytimeEarningEnabled(p.getUniqueId(), desired);

        if (desired) {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_enabled",
                            "&aPassive Claim Block earnings enabled.")));
            plugin.effects().playConfirm(p);
        } else {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_disabled",
                            "&ePassive Claim Block earnings disabled.")));
            plugin.effects().playError(p);
        }
    }

    // --------------------------------------------------
    // Rename / Description
    // --------------------------------------------------

    private void handleRename(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "usage_rename", "&cUsage: /ag rename <n>");
            return;
        }

        String name = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        name = ChatColor.translateAlternateColorCodes('&', name);

        if (name.length() > 32) {
            sendKey(p, "rename_too_long", "&cName is too long (Max {MAX} chars).",
                    Map.of("MAX", "32"));
            return;
        }

        String oldName = getReadablePlotName(plot);
        plot.setEntryTitle(name);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "plot_renamed", "&a✔ Plot renamed to: &r{NAME}",
                Map.of("NAME", name));
        notifyPlot(
                plot,
                p.getUniqueId(),
                "notify_claim_title",
                "&bClaim Update",
                "notify_plot_renamed",
                "&e{PLAYER} renamed {OLD_NAME} &eto &b{NEW_NAME}&e.",
                Map.of(
                        "PLAYER", p.getName(),
                        "OLD_NAME", oldName,
                        "NEW_NAME", name
                )
        );
        plugin.effects().playConfirm(p);
    }

    private void handleSetDesc(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "usage_setdesc", "&cUsage: /ag setdesc <Description>");
            return;
        }

        String desc = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        desc = ChatColor.translateAlternateColorCodes('&', desc);

        plot.setDescription(desc);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "plot_desc_updated", "&a✔ Plot description updated.");
        plugin.effects().playConfirm(p);
    }

    // --------------------------------------------------
    // Stuck
    // --------------------------------------------------

    private void handleStuck(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "unstuck_not_in_plot", "&cYou are not inside a plot.");
            return;
        }

        Location loc = p.getLocation();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        int dX1 = Math.abs(x - plot.getX1());
        int dX2 = Math.abs(x - plot.getX2());
        int dZ1 = Math.abs(z - plot.getZ1());
        int dZ2 = Math.abs(z - plot.getZ2());

        int min = Math.min(Math.min(dX1, dX2), Math.min(dZ1, dZ2));
        Location target = loc.clone();

        if (min == dX1) target.setX(plot.getX1() - 2);
        else if (min == dX2) target.setX(plot.getX2() + 2);
        else if (min == dZ1) target.setZ(plot.getZ1() - 2);
        else target.setZ(plot.getZ2() + 2);

        World world = loc.getWorld();
        int safeY = world.getHighestBlockYAt(target);
        target.setY(safeY + 1);

        TeleportUtil.safeTeleport(plugin, p, target);

        sendKey(p, "unstuck_success", "&a✔ Teleported to safety.");
        plugin.effects().playTeleport(p);
    }

// --------------------------------------------------
// Kick / Ban / Unban
// --------------------------------------------------

    // --------------------------------------------------

    private void handleKick(Player p, String[] args) {
        if (args.length < 2) {
            sendKey(p, "kick_usage", "&cUsage: /ag kick <player>");
            return;
        }

        Plot kPlot = plugin.store().getPlotAt(p.getLocation());
        if (kPlot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!kPlot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        Player kTarget = Bukkit.getPlayer(args[1]);
        if (kTarget == null) {
            sendKey(p, "player_not_found", "&cPlayer not found.");
            return;
        }
        if (!kPlot.isInside(kTarget.getLocation())) {
            sendKey(p, "player_not_in_plot", "&cPlayer is not in your plot.");
            return;
        }
        if (kTarget.getUniqueId().equals(p.getUniqueId())) {
            sendKey(p, "cannot_kick_self", "&cYou cannot kick yourself.");
            return;
        }
        if (kPlot.isOwner(kTarget.getUniqueId()) || Plot.SERVER_OWNER_UUID.equals(kTarget.getUniqueId())) {
            sendKey(p, "cannot_kick_owner", "&cYou cannot kick the plot owner.");
            return;
        }
        if (kTarget.hasPermission("aegis.admin.bypass") || kTarget.isOp()) {
            sendKey(p, "cannot_kick_operator", "&cYou cannot kick a Server Operator.");
            return;
        }

        Location spawn = kTarget.getWorld().getSpawnLocation();
        TeleportUtil.safeTeleport(plugin, kTarget, spawn);

        // Target gets message in THEIR language.
        sendKey(kTarget, "kicked_target", "§cYou were kicked from {OWNER}'s plot.",
                Map.of("OWNER", kPlot.getOwnerName()));

        // Sender gets message in THEIR language.
        sendKey(p, "kicked_sender", "&eKicked {PLAYER}",
                Map.of("PLAYER", kTarget.getName()));
    }

    private void handleBan(Player p, String[] args) {
        if (args.length < 2) {
            sendKey(p, "ban_usage", "&cUsage: /ag ban <player>");
            return;
        }

        Plot bPlot = plugin.store().getPlotAt(p.getLocation());
        if (bPlot == null) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (!bPlot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        OfflinePlayer bTarget = Bukkit.getOfflinePlayer(args[1]);
        if (bTarget.getUniqueId() != null && bTarget.getUniqueId().equals(p.getUniqueId())) {
            sendKey(p, "ban_self_error", "&cCannot ban yourself.");
            return;
        }
        if (bTarget.getUniqueId() != null
                && (bPlot.isOwner(bTarget.getUniqueId()) || Plot.SERVER_OWNER_UUID.equals(bTarget.getUniqueId()))) {
            sendKey(p, "ban_owner_error", "&cCannot ban the plot owner.");
            return;
        }

        bPlot.addBan(bTarget.getUniqueId());

        plugin.store().savePlot(bPlot);
        plugin.store().setDirty(true);

        sendKey(p, "ban_success", "&cBanned {PLAYER}",
                Map.of("PLAYER", (bTarget.getName() == null ? args[1] : bTarget.getName())));

        if (bTarget.isOnline()) {
            Player online = bTarget.getPlayer();
            if (online != null && bPlot.isInside(online.getLocation())) {
                Location spawn = online.getWorld().getSpawnLocation();
                TeleportUtil.safeTeleport(plugin, online, spawn);

                // Target gets message in THEIR language.
                sendKey(online, "ban_target", "§4You have been BANNED from this plot.");
            }
        }
    }

    private void handleUnban(Player p, String[] args) {
        if (args.length < 2) {
            sendKey(p, "unban_usage", "&cUsage: /ag unban <player>");
            return;
        }

        Plot uPlot = plugin.store().getPlotAt(p.getLocation());
        if (uPlot == null) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (!uPlot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        OfflinePlayer uTarget = Bukkit.getOfflinePlayer(args[1]);
        uPlot.removeBan(uTarget.getUniqueId());

        plugin.store().savePlot(uPlot);
        plugin.store().setDirty(true);

        sendKey(p, "unban_success", "&aUnbanned {PLAYER}",
                Map.of("PLAYER", (uTarget.getName() == null ? args[1] : uTarget.getName())));
    }

    // --------------------------------------------------
    // Home / Spawn
    // --------------------------------------------------

    private void handleSetSpawn(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            plugin.effects().playError(p);
            return;
        }
        if (!plot.isInside(p.getLocation())) {
            sendKey(p, "home-fail-outside", "&c❌ You must be inside your claim to set its home.");
            plugin.effects().playError(p);
            return;
        }

        plot.setSpawnLocation(p.getLocation());

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "home-set-success", "&a✔ Claim home set to your current location.");
        plugin.effects().playConfirm(p);
    }

    private void handleHome(Player p) {
        if (!plugin.cfg().allowHomeTeleport()) {
            sendKey(p, "home_teleport_disabled", "&cHome teleport is disabled.");
            return;
        }

        Plot homePlot = plugin.store().getPlotAt(p.getLocation());
        if (homePlot == null || !homePlot.canManage(p, plugin)) {
            List<Plot> plots = plugin.store().getPlots(p.getUniqueId());
            if (plots != null && !plots.isEmpty()) homePlot = plots.get(0);
            else {
                sendKey(p, "no_plot_here", "&c❌ You do not own any plots.");
                plugin.effects().playError(p);
                return;
            }
        }

        if (homePlot.getSpawnLocation() == null) {
            sendKey(p, "home-fail-no-spawn", "&c❌ No home set! Use /ag setspawn.");
            plugin.effects().playError(p);
            return;
        }

        TeleportUtil.safeTeleport(plugin, p, homePlot.getSpawnLocation());
        plugin.effects().playConfirm(p);
    }

    private void handleWelcomeFarewell(Player p, String[] args, boolean isWelcome) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        if (args.length < 2) {
            if (isWelcome) plot.setWelcomeMessage(null);
            else plot.setFarewellMessage(null);

            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);

            if (isWelcome) sendKey(p, "welcome-cleared", "&e✔ Welcome message cleared.");
            else sendKey(p, "farewell-cleared", "&e✔ Farewell message cleared.");
            return;
        }

        String msg = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (isWelcome) plot.setWelcomeMessage(msg);
        else plot.setFarewellMessage(msg);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        if (isWelcome) sendKey(p, "welcome-set", "&a✔ Welcome message updated.");
        else sendKey(p, "farewell-set", "&a✔ Farewell message updated.");
    }

    // --------------------------------------------------
    // Market
    // --------------------------------------------------

    
private void handleSell(Player p, String[] args) {
    Plot plot = plugin.store().getPlotAt(p.getLocation());
    if (plot == null || !plot.isOwner(p)) {
        sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
        return;
    }
    if (plot.isServerZone() || plot.isGroupPlot() || plot.isForAuction() || plot.isForRent() || plot.hasActiveRental()
            || plot.getZones().stream().anyMatch(zone -> zone.isRented() || zone.isListedForRent())) {
        sendKey(p, "market-listing-conflict", "&cThis plot cannot be listed while another ownership or market state is active.");
        return;
    }
    if (args.length < 2) {
        sendKey(p, "sell_usage", "&cUsage: /ag sell <price>");
        return;
    }

    double price;
    try {
        price = Double.parseDouble(args[1]);
        if (!Double.isFinite(price) || price <= 0) {
            sendKey(p, "price_must_positive", "&cPrice must be positive.");
            return;
        }
    } catch (NumberFormatException e) {
        sendKey(p, "invalid_number", "&cInvalid number.");
        return;
    }

    if (price <= 0) {
        sendKey(p, "price_must_positive", "&cPrice must be positive.");
        return;
    }

    plot.setForSale(true, price);

    plugin.store().savePlot(plot);
    plugin.store().setDirty(true);

    String formatted;
    if (plugin.eco() != null && plugin.eco().isVaultEnabled()) {
        formatted = plugin.eco().format(price, CurrencyType.VAULT);
    } else {
        formatted = String.valueOf(price);
    }

    sendKey(p, "market-for-sale", "&a✔ Claim listed for &6{PRICE}&a.",
            Map.of("PRICE", formatted));
}

private void handleUnsell(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        plot.setForSale(false, 0);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "market-not-for-sale", "&e✔ Claim listing removed.");
    }

    private void handleRentListing(Player p, String[] args) {
        if (!plugin.cfg().raw().getBoolean("full_plot_renting.enabled", true)) {
            sendKey(p, "market-renting-disabled", "&cFull-plot renting is disabled on this server.");
            return;
        }
        if (!p.hasPermission("aegis.rent")) {
            sendKey(p, "no_permission", "&cYou do not have permission to list plots for rent.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.isOwner(p)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (plot.isServerZone() || plot.isGroupPlot() || plot.isForAuction() || plot.isForSale() || plot.hasActiveRental()
                || plot.getZones().stream().anyMatch(zone -> zone.isRented() || zone.isListedForRent())) {
            sendKey(p, "market-listing-conflict", "&cThis plot cannot be listed while another ownership or market state is active.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "rent_usage", "&cUsage: /ag rent <price> [days] [deposit]");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            sendKey(p, "invalid_number", "&cInvalid number.");
            return;
        }
        double minimum = Math.max(0.01, plugin.cfg().raw().getDouble("full_plot_renting.minimum_price", 1.0));
        double maximum = Math.max(minimum, plugin.cfg().raw().getDouble("full_plot_renting.maximum_price", 1_000_000_000.0));
        if (!Double.isFinite(price) || price < minimum || price > maximum) {
            sendKey(p, "rent_price_range", "&cRent must be between {MIN} and {MAX}.", Map.of(
                    "MIN", String.valueOf(minimum), "MAX", String.valueOf(maximum)));
            return;
        }

        int defaultDays = Math.max(1, plugin.cfg().raw().getInt("full_plot_renting.duration_days", 7));
        int maximumDays = Math.max(defaultDays, plugin.cfg().raw().getInt("full_plot_renting.maximum_duration_days", 90));
        int days = defaultDays;
        double deposit = 0.0D;
        try {
            if (args.length >= 3) days = Integer.parseInt(args[2]);
            if (args.length >= 4) deposit = Double.parseDouble(args[3]);
        } catch (NumberFormatException exception) {
            sendKey(p, "invalid_number", "&cDays and deposit must be valid numbers.");
            return;
        }
        double maximumDeposit = Math.max(0.0D,
                plugin.cfg().raw().getDouble("full_plot_renting.maximum_deposit", 1_000_000.0D));
        if (days < 1 || days > maximumDays || !Double.isFinite(deposit) || deposit < 0.0D || deposit > maximumDeposit) {
            sendMsg(p, "&cDays must be 1-" + maximumDays + " and deposit must be 0-" + maximumDeposit + ".");
            return;
        }

        plot.setForRent(true, price);
        plugin.territoryLife().setOffer(plot.getPlotId(), price, deposit, days);
        plugin.store().savePlotSync(plot);
        sendKey(p, "market-for-rent", "&a✔ Claim listed for rent at &6{PRICE}&a per term.", Map.of(
                "PRICE", plugin.eco().format(price, CurrencyType.VAULT)));
        sendMsg(p, "&7Term: &b" + days + " day(s) &8| &7Deposit: &6"
                + plugin.eco().format(deposit, CurrencyType.VAULT));
        plugin.territoryLife().log(plot.getPlotId(), p.getUniqueId(), "RENTAL_LISTED",
                "Listed for " + price + ", deposit=" + deposit + ", term=" + days + " day(s).");
    }

    private void handleUnrent(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.isOwner(p)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (plot.hasActiveRental()) {
            sendKey(p, "market-rental-active", "&cYou cannot remove this listing until the active rental expires.");
            return;
        }
        plot.setForRent(false, 0);
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.store().savePlotSync(plot);
        sendKey(p, "market-not-for-rent", "&e✔ Rental listing removed.");
        plugin.territoryLife().log(plot.getPlotId(), p.getUniqueId(), "RENTAL_UNLISTED", "Rental listing removed.");
    }

    private void handleRentalContract(Player p, String[] args) {
        TerritoryLifeService.RentalContract contract = findRentalContract(p);
        if (contract == null) {
            sendKey(p, "rental_contract_none", "&eYou do not have an active full-plot rental contract.");
            return;
        }
        Plot plot = plugin.store().getAllPlots().stream()
                .filter(candidate -> candidate != null && contract.plotId().equals(candidate.getPlotId()))
                .findFirst().orElse(null);
        if (plot == null) {
            sendKey(p, "rental_contract_plot_missing", "&cThis contract's plot is missing. Ask an admin to run /agadmin doctor scan.");
            return;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("status") || action.equals("info")) {
            long remaining = Math.max(0L, contract.expiresAt() - System.currentTimeMillis());
            sendKey(p, "rental_contract_title", "&6Rental Contract &8- &f{PLOT}", Map.of(
                    "PLOT", getReadablePlotName(plot)));
            sendKey(p, "rental_contract_terms", "&7Rent: &6{RENT} &8| &7Deposit: &6{DEPOSIT}", Map.of(
                    "RENT", plugin.eco().format(contract.rent(), CurrencyType.VAULT),
                    "DEPOSIT", plugin.eco().format(contract.deposit(), CurrencyType.VAULT)));
            sendKey(p, "rental_contract_remaining", "&7Remaining: &b{DAYS} day(s), {HOURS} hour(s)", Map.of(
                    "DAYS", Long.toString(Math.max(0L, remaining / 86_400_000L)),
                    "HOURS", Long.toString((remaining / 3_600_000L) % 24L)));
            sendKey(p, "rental_contract_actions", "&e/ag rental renew &7or &e/ag rental cancel confirm");
            return;
        }

        if (action.equals("renew")) {
            if (!contract.renterId().equals(p.getUniqueId())) {
                sendKey(p, "rental_contract_only_renter", "&cOnly the renter can renew this contract.");
                return;
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(contract.ownerId());
            if (!plugin.eco().withdraw(p, contract.rent(), CurrencyType.VAULT)) {
                sendKey(p, "rental_contract_need_funds", "&cYou need {AMOUNT} to renew.", Map.of(
                        "AMOUNT", plugin.eco().format(contract.rent(), CurrencyType.VAULT)));
                return;
            }
            if (plugin.vault() == null || !plugin.vault().deposit(owner, contract.rent())) {
                if (plugin.vault() == null || !plugin.vault().deposit(p, contract.rent())) {
                    plugin.territoryLife().addSettlement(p.getUniqueId(), contract.rent(), "Failed rental renewal refund");
                }
                sendKey(p, "rental_contract_payment_failed", "&cRenewal payment failed. No contract time was added.");
                return;
            }
            plugin.territoryLife().renew(plot.getPlotId());
            plot.setRentEndTime(contract.expiresAt());
            plugin.store().savePlotSync(plot);
            plugin.territoryLife().log(plot.getPlotId(), p.getUniqueId(), "RENTAL_RENEWED",
                    "Contract renewed for " + contract.termDays() + " day(s).");
            plugin.territoryLife().queueNotice(contract.ownerId(), "&aA rental contract was renewed for &e"
                    + contract.termDays() + " day(s)&a.");
            sendKey(p, "rental_contract_renewed", "&aRental renewed for &e{DAYS} day(s)&a.", Map.of(
                    "DAYS", Integer.toString(contract.termDays())));
            return;
        }

        if (action.equals("cancel")) {
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                sendKey(p, "rental_contract_cancel_prompt", "&eRun &b/ag rental cancel confirm &eto end this contract and return its deposit.");
                return;
            }
            boolean renter = contract.renterId().equals(p.getUniqueId());
            boolean owner = contract.ownerId().equals(p.getUniqueId());
            if (!renter && !owner) {
                sendKey(p, "rental_contract_not_party", "&cYou are not part of this rental contract.");
                return;
            }
            if (owner && !plugin.getConfig().getBoolean("full_plot_renting.allow_owner_early_cancel", false)) {
                sendKey(p, "rental_contract_owner_cancel_disabled", "&cOwners cannot end active contracts early on this server.");
                return;
            }
            plugin.territoryLife().removeContract(plot.getPlotId());
            plugin.territoryLife().refundDeposit(contract, "Deposit after early rental cancellation");
            plot.clearRenter();
            plugin.store().savePlotSync(plot);
            plugin.territoryLife().log(plot.getPlotId(), p.getUniqueId(), "RENTAL_CANCELLED",
                    "Contract ended early by " + (owner ? "owner" : "renter") + ".");
            plugin.territoryLife().queueNotice(owner ? contract.renterId() : contract.ownerId(),
                    "&eThe rental contract for plot &f" + plot.getPlotId() + " &ewas ended early.");
            sendKey(p, "rental_contract_cancelled", "&aRental contract ended. The deposit was refunded or queued for delivery.");
            return;
        }
        sendKey(p, "rental_contract_usage", "&cUsage: /ag rental <status|renew|cancel confirm>");
    }

    private TerritoryLifeService.RentalContract findRentalContract(Player player) {
        Plot here = plugin.store().getPlotAt(player.getLocation());
        if (here != null) {
            TerritoryLifeService.RentalContract local = plugin.territoryLife().contract(here.getPlotId());
            if (local != null && (local.ownerId().equals(player.getUniqueId()) || local.renterId().equals(player.getUniqueId()))) {
                return local;
            }
        }
        return plugin.territoryLife().contracts().stream()
                .filter(contract -> contract.ownerId().equals(player.getUniqueId()) || contract.renterId().equals(player.getUniqueId()))
                .findFirst().orElse(null);
    }

    private void handleDiscover(Player player, String[] args) {
        if (!player.hasPermission("aegis.discovery")) {
            sendKey(player, "no_permission", "&cYou do not have permission to use plot discovery.");
            return;
        }
        if (!plugin.getConfig().getBoolean("plot_discovery.enabled", true)) {
            sendKey(player, "discovery_disabled", "&cPlot discovery is disabled on this server.");
            return;
        }
        if (args.length == 1) {
            plugin.gui().visit().open(player, 0, com.aegisguard.gui.VisitGUI.VisitMode.DISCOVER);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("favorites")) {
            plugin.gui().visit().open(player, 0, com.aegisguard.gui.VisitGUI.VisitMode.FAVORITES);
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isOwner(player)) {
            sendKey(player, "discovery_owner_required", "&cStand inside a plot you own to change its discovery settings.");
            return;
        }
        if (action.equals("category") && args.length >= 3) {
            String category = args[2].toLowerCase(Locale.ROOT);
            List<String> allowed = plugin.getConfig().getStringList("plot_discovery.categories");
            if (!allowed.stream().anyMatch(value -> value.equalsIgnoreCase(category))) {
                sendKey(player, "discovery_invalid_category", "&cCategory must be one of: &f{CATEGORIES}", Map.of(
                        "CATEGORIES", String.join(", ", allowed)));
                return;
            }
            plugin.territoryLife().setCategory(plot.getPlotId(), category);
            plugin.territoryLife().log(plot.getPlotId(), player.getUniqueId(), "DISCOVERY_CATEGORY",
                    "Discovery category changed to " + category + ".");
            sendKey(player, "discovery_category_set", "&aDiscovery category set to &e{CATEGORY}&a.", Map.of(
                    "CATEGORY", category));
            return;
        }
        if (action.equals("visibility") && args.length >= 3) {
            if (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off")
                    && !args[2].equalsIgnoreCase("public") && !args[2].equalsIgnoreCase("private")) {
                sendKey(player, "discovery_visibility_invalid", "&cVisibility must be on or off.");
                return;
            }
            boolean visible = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("public");
            plugin.territoryLife().setVisible(plot.getPlotId(), visible);
            plugin.territoryLife().log(plot.getPlotId(), player.getUniqueId(), "DISCOVERY_VISIBILITY",
                    "Discovery visibility changed to " + (visible ? "public" : "private") + ".");
            sendKey(player, visible ? "discovery_visible" : "discovery_hidden",
                    visible ? "&aThis plot is visible in discovery." : "&eThis plot is hidden from discovery.");
            return;
        }
        sendMsg(player, "&e/ag discover &7| &e/ag discover favorites &7| &e/ag discover category <name> &7| &e/ag discover visibility <on|off>");
    }

    private void handleFavorite(Player player) {
        if (!player.hasPermission("aegis.discovery")) {
            sendKey(player, "no_permission", "&cYou do not have permission to use plot discovery.");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendKey(player, "favorite_plot_required", "&cStand inside a plot to favorite it.");
            return;
        }
        boolean added = plugin.territoryLife().toggleFavorite(player.getUniqueId(), plot.getPlotId());
        sendKey(player, added ? "favorite_added" : "favorite_removed",
                added ? "&aPlot added to your favorites." : "&ePlot removed from your favorites.");
    }

    private void handleActivity(Player player) {
        if (!player.hasPermission("aegis.activity")) {
            sendKey(player, "no_permission", "&cYou do not have permission to view territory activity.");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            sendKey(player, "activity_plot_required", "&cStand inside a plot you can manage to view its activity.");
            return;
        }
        List<TerritoryLifeService.ActivityEntry> entries = plugin.territoryLife().activity(plot.getPlotId(), 10);
        sendKey(player, "activity_title", "&6Recent Territory Activity &8(&f{PLOT}&8)", Map.of(
                "PLOT", getReadablePlotName(plot)));
        if (entries.isEmpty()) {
            sendKey(player, "activity_empty", "&7No activity has been recorded yet.");
            return;
        }
        long now = System.currentTimeMillis();
        for (TerritoryLifeService.ActivityEntry entry : entries) {
            long minutes = Math.max(0L, (now - entry.timestamp()) / 60_000L);
            sendKey(player, "activity_entry", "&8- &e{TYPE} &7{DETAILS} &8({MINUTES}m ago)", Map.of(
                    "TYPE", entry.type(), "DETAILS", entry.details(), "MINUTES", Long.toString(minutes)));
        }
    }

    // --------------------------------------------------
    // Menus
    // --------------------------------------------------

    private void openLevelMenu(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        if (plugin.cfg().isLevelingEnabled()) plugin.gui().leveling().open(p, plot);
        else sendKey(p, "leveling_disabled", "&cLeveling is disabled.");
    }

    private void openZoneMenu(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot to do that.");
            return;
        }

        if (!plugin.cfg().isZoningEnabled()) {
            sendKey(p, "zoning_disabled", "&cZoning is disabled.");
            return;
        }

        if (plot.canManage(p, plugin)) {
            plugin.gui().zoning().open(p, plot);
            return;
        }

        if (plot.hasBrowsableZonesFor(p)) {
            plugin.gui().zoneBrowse().open(p, plot);
            return;
        }

        sendKey(p, "zone_browse_none", "&cThere are no rentable zones here right now.");
    }

    private void handleCreateSubplot(Player p, String[] args) {
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

    private void openMarketMenu(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        boolean canUseLocal = plot != null
                && plugin.marketBridges() != null
                && plugin.marketBridges().plotQualifiesForLocalMarket(plot, p);

        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("global")) {
                plugin.gui().market().open(p, 0);
                return;
            }
            if (mode.equals("local")) {
                if (canUseLocal) {
                    plugin.gui().localMarket().open(p, plot);
                } else {
                    sendKey(p, "local_market_not_available", "&cThere is no local market set up on this plot.");
                }
                return;
            }
        }

        if (canUseLocal && plugin.marketBridges() != null && plugin.marketBridges().preferLocalWhenInPlot()) {
            plugin.gui().localMarket().open(p, plot);
        } else {
            plugin.gui().market().open(p, 0);
        }
    }

    private void handleLike(Player p) {
        if (!plugin.cfg().isLikesEnabled()) {
            sendKey(p, "like_disabled", "&cLikes are disabled on this server/world.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (plot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "like_own_plot", "&cError: You cannot like your own claim.");
            return;
        }

        if (plugin.cfg().oneLikePerPlayer() && plot.hasLiked(p.getUniqueId())) {
            plot.toggleLike(p.getUniqueId());
            sendKey(p, "like_removed", "&eYour like has been removed.");
        } else {
            plot.toggleLike(p.getUniqueId());
            sendKey(p, "like_success", "&a✔ You liked this claim. Total likes: &e{AMOUNT}",
                    Map.of("AMOUNT", String.valueOf(plot.getLikes())));
            if (plugin.horizons() != null) plugin.horizons().recordLike(plot, p.getUniqueId());
            plugin.effects().playConfirm(p);
        }

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
    }

    // --------------------------------------------------
    // Help
    // --------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sendKey(sender, "help_header", "&bAegisGuard Help");

        List<String> helpLines = Collections.emptyList();
        try {
            if (plugin.codex() != null) helpLines = plugin.codex().trList(sender, "help_lines");
        } catch (Throwable ignored) {}

        if (helpLines != null) {
            for (String line : helpLines) sendMsg(sender, line);
        }
    }

    private void handleGroup(Player p, String[] args) {
        if (plugin.groups() == null) {
            sendKey(p, "error_generic", "&cThat feature is not available right now.");
            return;
        }
        if (!plugin.getConfig().getBoolean("group_plots.enabled", true)) {
            sendKey(p, "feature_disabled", "&cThat feature is disabled on this server.");
            return;
        }

        if (args.length < 2) {
            sendKey(p, "group_help_create", "&e/ag group create <name> &7- create a player group");
            sendKey(p, "group_help_invite", "&e/ag group invite <player> &7- invite a nearby player");
            sendKey(p, "group_help_accept", "&e/ag group accept <name> &7- accept a pending invite");
            sendKey(p, "group_help_status", "&e/ag group status &7- view group and treasury status");
            sendKey(p, "group_help_claim", "&e/ag group claim &7- create the group plot from your selection");
            sendKey(p, "group_help_deposit", "&e/ag group deposit <amount> &7- add money to the group or plot treasury");
            sendKey(p, "group_help_leave", "&e/ag group leave &7- leave your current group");
            sendKey(p, "group_help_disband", "&e/ag group disband &7- disband your group before it owns a plot");
            return;
        }

        PlotGroup group = plugin.groups().getGroupForPlayer(p.getUniqueId());
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    sendKey(p, "group_usage_create", "&cUsage: /ag group create <name>");
                    return;
                }
                if (group != null) {
                    sendKey(p, "group_already_in_group", "&cYou are already in a group. Leave it before creating another.");
                    return;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                PlotGroup created = plugin.groups().createGroup(p.getUniqueId(), name);
                if (created == null) {
                    sendKey(p, "group_create_failed_name_taken", "&cThat group name is unavailable, or you are already grouped.");
                    return;
                }
                sendKey(p, "group_created", "&aCreated group &e{GROUP}&a. Invite players, build a treasury, then use &e/ag group claim&a.",
                        Map.of("GROUP", created.getName()));
            }
            case "status", "info" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                Plot linkedPlot = getLinkedGroupPlot(group);
                double treasury = linkedPlot != null ? linkedPlot.getTreasuryBalance() : group.getTreasuryBalance();
                sendKey(p, "group_status_header", "&6Group Status");
                sendKey(p, "group_status_name", "&7Group: &e{GROUP}", Map.of("GROUP", group.getName()));
                sendKey(p, "group_status_leader", "&7Leader: &e{PLAYER}",
                        Map.of("PLAYER", plugin.groups().getMemberName(group.getLeader())));
                sendKey(p, "group_status_members", "&7Members: &e{COUNT}", Map.of("COUNT", String.valueOf(group.size())));
                sendKey(p, "group_status_treasury", "&7Treasury: &e{AMOUNT}",
                        Map.of("AMOUNT", plugin.eco() != null && plugin.eco().isVaultEnabled()
                                ? plugin.eco().format(treasury, CurrencyType.VAULT)
                                : String.format(Locale.US, "%.2f", treasury)));
                sendKey(p, "group_status_linked_plot", "&7Linked Plot: {STATE}",
                        Map.of("STATE", linkedPlot == null ? "&cNone" : "&aYes"));
                sendKey(p, "group_status_eligible_members", "&7Starter-eligible members: &e{COUNT}",
                        Map.of("COUNT", String.valueOf(plugin.groups().getEligibleStarterMemberCount(group))));
                sendKey(p, "group_status_starter_area", "&7Free starter max area: &e{AREA}",
                        Map.of("AREA", String.valueOf(plugin.groups().getStarterMaxArea(group))));
            }
            case "invite" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_invite", "&cUsage: /ag group invite <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || !target.isOnline()) {
                    sendKey(p, "group_player_not_found", "&cThat player is not online.");
                    return;
                }
                if (target.getUniqueId().equals(p.getUniqueId())) {
                    sendKey(p, "group_cannot_invite_self", "&cYou are already the leader.");
                    return;
                }
                if (group.isMember(target.getUniqueId())) {
                    sendKey(p, "group_already_member", "&cThat player is already in your group.");
                    return;
                }
                if (plugin.groups().isInGroup(target.getUniqueId())) {
                    sendKey(p, "group_target_already_grouped", "&cThat player is already in another group.");
                    return;
                }
                double maxDistance = plugin.getConfig().getDouble("group_plots.invite_max_distance", 32.0D);
                if (!target.getWorld().equals(p.getWorld()) || target.getLocation().distanceSquared(p.getLocation()) > (maxDistance * maxDistance)) {
                    sendKey(p, "group_invite_too_far", "&cThat player must be nearby to receive a group invite.");
                    return;
                }
                plugin.groups().invitePlayer(group, target.getUniqueId());
                sendKey(p, "group_invite_sent", "&aInvited &e{PLAYER} &ato join &e{GROUP}&a.",
                        Map.of("PLAYER", target.getName(), "GROUP", group.getName()));
                sendKey(target, "group_invite_received",
                        "&e{PLAYER} &7invited you to join &e{GROUP}&7. Use &e/ag group accept {GROUP}",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName()));
            }
            case "accept" -> {
                if (group != null) {
                    sendKey(p, "group_already_in_group", "&cYou are already in a group. Leave it before joining another.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_accept", "&cUsage: /ag group accept <name>");
                    return;
                }
                PlotGroup invited = plugin.groups().getGroupByName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                if (invited == null || !invited.hasInvite(p.getUniqueId())) {
                    sendKey(p, "group_no_invite", "&cYou do not have a pending invite for that group.");
                    return;
                }
                if (!plugin.groups().acceptInvite(invited, p.getUniqueId())) {
                    sendKey(p, "group_accept_failed", "&cThat invite could not be accepted.");
                    return;
                }
                sendKey(p, "group_joined", "&aYou joined group &e{GROUP}&a.", Map.of("GROUP", invited.getName()));
                notifyGroup(
                        invited,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_member_joined",
                        "&a{PLAYER} joined the group &e{GROUP}&a.",
                        Map.of("PLAYER", p.getName(), "GROUP", invited.getName())
                );
            }
            case "leave" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_leader_cannot_leave", "&cThe leader cannot leave. Disband the group or keep leading it.");
                    return;
                }
                if (!plugin.groups().leaveGroup(group, p.getUniqueId())) {
                    sendKey(p, "group_leave_failed", "&cYou could not leave the group right now.");
                    return;
                }
                sendKey(p, "group_left", "&eYou left group &6{GROUP}&e.", Map.of("GROUP", group.getName()));
                notifyGroup(
                        group,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_member_left",
                        "&e{PLAYER} left the group &6{GROUP}&e.",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName())
                );
            }
            case "kick" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_kick", "&cUsage: /ag group kick <player>");
                    return;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (target.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_cannot_kick_leader", "&cYou cannot kick the group leader.");
                    return;
                }
                if (!group.isMember(target.getUniqueId())) {
                    sendKey(p, "group_member_not_found", "&cThat player is not in your group.");
                    return;
                }
                if (!plugin.groups().canRemoveMemberNow(group)) {
                    sendKey(p, "group_kick_locked", "&cStarter protection is active. You can remove members in about &e{MINUTES} &cminutes.",
                            Map.of("MINUTES", plugin.groups().describeStarterLockRemaining(group)));
                    return;
                }
                if (!plugin.groups().kickMember(group, target.getUniqueId())) {
                    sendKey(p, "group_kick_failed", "&cThat member could not be removed.");
                    return;
                }
                String targetName = target.getName() == null ? args[2] : target.getName();
                sendKey(p, "group_member_kicked", "&eRemoved &6{PLAYER} &efrom the group.",
                        Map.of("PLAYER", targetName));
                if (plugin.notifications() != null) {
                    plugin.notifications().notifyPlayer(
                            target.getUniqueId(),
                            "notify_group_title",
                            "&6Group Update",
                            "notify_group_member_removed",
                            "&cYou were removed from the group &6{GROUP}&c by &e{PLAYER}&c.",
                            Map.of("GROUP", group.getName(), "PLAYER", p.getName())
                    );
                }
                notifyGroup(
                        group,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_member_kicked",
                        "&e{TARGET} was removed from the group by &6{PLAYER}&e.",
                        Map.of("TARGET", targetName, "PLAYER", p.getName(), "GROUP", group.getName())
                );
            }
            case "deposit", "add" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_deposit", "&cUsage: /ag group deposit <amount>");
                    return;
                }
                if (plugin.eco() == null || !plugin.eco().isVaultReady()) {
                    sendKey(p, "group_vault_required", "&cVault economy is required for group treasury deposits.");
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException ex) {
                    sendKey(p, "group_amount_invalid", "&cAmount must be a valid number.");
                    return;
                }
                if (amount <= 0.0D) {
                    sendKey(p, "group_amount_positive", "&cAmount must be greater than 0.");
                    return;
                }
                if (!plugin.eco().withdraw(p, amount, CurrencyType.VAULT)) {
                    sendKey(p, "group_insufficient_funds", "&cYou do not have enough money for that deposit.");
                    return;
                }
                Plot linkedPlot = getLinkedGroupPlot(group);
                if (linkedPlot != null) {
                    linkedPlot.addTreasuryFunds(amount);
                    plugin.store().savePlot(linkedPlot);
                    String newBalance = plugin.eco().format(linkedPlot.getTreasuryBalance(), CurrencyType.VAULT);
                    sendKey(p, "group_deposit_success",
                            "&aDeposited &e{AMOUNT} &ainto the group treasury. New balance: &e{BALANCE}",
                            Map.of("AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance));
                    notifyGroup(
                            group,
                            null,
                            "notify_group_title",
                            "&6Group Update",
                            "notify_group_treasury_updated",
                            "&e{PLAYER} deposited &6{AMOUNT} &einto {GROUP}'s treasury. New balance: &6{BALANCE}&e.",
                            Map.of(
                                    "PLAYER", p.getName(),
                                    "AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance,
                                    "GROUP", group.getName()
                            )
                    );
                    notifyLowTreasuryIfNeeded(group, linkedPlot, null);
                } else {
                    group.addTreasuryFunds(amount);
                    plugin.groups().setDirty(true);
                    String newBalance = plugin.eco().format(group.getTreasuryBalance(), CurrencyType.VAULT);
                    sendKey(p, "group_deposit_success",
                            "&aDeposited &e{AMOUNT} &ainto the group treasury. New balance: &e{BALANCE}",
                            Map.of("AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance));
                    notifyGroup(
                            group,
                            null,
                            "notify_group_title",
                            "&6Group Update",
                            "notify_group_treasury_updated",
                            "&e{PLAYER} deposited &6{AMOUNT} &einto {GROUP}'s treasury. New balance: &6{BALANCE}&e.",
                            Map.of(
                                    "PLAYER", p.getName(),
                                    "AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance,
                                    "GROUP", group.getName()
                            )
                    );
                    notifyLowTreasuryIfNeeded(group, null, null);
                }
            }
            case "claim" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (group.hasLinkedPlot()) {
                    sendKey(p, "group_plot_already_exists", "&cThis group already has a linked plot.");
                    return;
                }
                if (!plugin.selection().hasSelection(p)) {
                    sendKey(p, "must_select", "&c❌ You must select two corners with the Wand first.");
                    return;
                }

                long area = plugin.selection().getSelectionArea(p);
                int eligible = plugin.groups().getEligibleStarterMemberCount(group);
                int minMembers = Math.max(1, plugin.getConfig().getInt("group_plots.min_members_to_claim", 2));
                if (eligible < minMembers) {
                    sendKey(p, "group_not_enough_members",
                            "&cYour group needs at least &e{COUNT} &cstarter-eligible members before it can claim a group plot.",
                            Map.of("COUNT", String.valueOf(minMembers)));
                    return;
                }

                boolean freeStarter = plugin.groups().qualifiesForFreeStarterClaim(group, (int) area);
                double charged = 0.0D;
                if (!freeStarter) {
                    if (plugin.eco() == null || !plugin.eco().isVaultReady()) {
                        sendKey(p, "group_claim_requires_vault", "&cA Vault economy is required for paid group claims.");
                        return;
                    }
                    double cost = plugin.groups().getRequiredClaimCost((int) area);
                    if (group.getTreasuryBalance() + 0.000001D < cost) {
                        sendKey(p, "group_claim_not_enough_treasury",
                                "&cYour group treasury needs &e{AMOUNT} &cmore before this plot can be claimed.",
                                Map.of("AMOUNT", plugin.eco().format(cost - group.getTreasuryBalance(), CurrencyType.VAULT)));
                        return;
                    }
                    if (!group.withdrawTreasuryFunds(cost)) {
                        sendKey(p, "group_claim_not_enough_treasury", "&cYour group treasury cannot cover that claim yet.");
                        return;
                    }
                    charged = cost;
                    plugin.groups().setDirty(true);
                }

                Plot newPlot = plugin.selection().confirmGroupClaim(p, group);
                if (newPlot == null) {
                    if (charged > 0.0D) {
                        group.addTreasuryFunds(charged);
                        plugin.groups().setDirty(true);
                    }
                    return;
                }

                if (freeStarter) {
                    plugin.groups().markStarterClaimUsed(group);
                    sendKey(p, "group_free_claim_created",
                            "&aCreated the free starter plot for &e{GROUP}&a. Protected size used: &e{AREA}&a blocks.",
                            Map.of("GROUP", group.getName(), "AREA", String.valueOf(area)));
                } else {
                    sendKey(p, "group_paid_claim_created",
                            "&aCreated the group plot for &e{GROUP}&a using &6{AMOUNT}&a from the treasury.",
                            Map.of("GROUP", group.getName(), "AMOUNT", plugin.eco().format(charged, CurrencyType.VAULT)));
                }

                plugin.groups().attachPlot(group, newPlot);
                newPlot.setTreasuryBalance(group.getTreasuryBalance());
                group.setTreasuryBalance(0.0D);
                plugin.groups().setDirty(true);
                plugin.store().savePlot(newPlot);
                notifyGroup(
                        group,
                        null,
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_plot_created",
                        "&a{PLAYER} created the group plot for &e{GROUP}&a.",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName())
                );
            }
            case "disband", "disable", "off" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (group.hasLinkedPlot()) {
                    sendKey(p, "group_disband_plot_exists", "&cThis group already owns a plot. Unclaim or migrate it before disbanding.");
                    return;
                }
                notifyGroup(
                        group,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_disbanded",
                        "&c{PLAYER} disbanded the group &6{GROUP}&c.",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName())
                );
                plugin.groups().disbandGroup(group);
                sendKey(p, "group_disbanded", "&eDisbanded group &6{GROUP}&e.", Map.of("GROUP", group.getName()));
            }
            default -> sendKey(p, "group_usage", "&cUsage: /ag group <create|invite|accept|status|deposit|claim|leave|kick|disband>");
        }
    }

    private Plot getLinkedGroupPlot(PlotGroup group) {
        if (group == null || group.getLinkedPlotId() == null) return null;
        return plugin.store().getAllPlots().stream()
                .filter(plot -> plot != null && group.getLinkedPlotId().equals(plot.getPlotId()))
                .findFirst()
                .orElse(null);
    }

    // --------------------------------------------------
    // Tab completion
    // --------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("subplot") || args[0].equalsIgnoreCase("subzone")) {
                return Arrays.asList("Market Stall", "Room", "Hotel Suite", "Storage", "Booth");
            }

            if (args[0].equalsIgnoreCase("notify")) {
                List<String> completions = new ArrayList<>();
                List<String> subs = Arrays.asList("greetings", "admin", "all", "mode", "status");
                StringUtil.copyPartialMatches(args[1], subs, completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("refresh")) {
                List<String> completions = new ArrayList<>();
                List<String> modes = Arrays.asList("soft", "nogui");
                StringUtil.copyPartialMatches(args[1], modes, completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("blocks")) {
                List<String> completions = new ArrayList<>();
                List<String> subs = Arrays.asList("rates", "buy", "sell", "earnings", "help");
                StringUtil.copyPartialMatches(args[1], subs, completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("group")) {
                List<String> completions = new ArrayList<>();
                List<String> subs = Arrays.asList("create", "invite", "accept", "status", "deposit", "claim", "leave", "kick", "disband");
                StringUtil.copyPartialMatches(args[1], subs, completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("market")) {
                List<String> completions = new ArrayList<>();
                List<String> subs = Arrays.asList("local", "global");
                StringUtil.copyPartialMatches(args[1], subs, completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("rental")) {
                return StringUtil.copyPartialMatches(args[1], List.of("status", "renew", "cancel"), new ArrayList<>());
            }

            if (args[0].equalsIgnoreCase("discover")) {
                return StringUtil.copyPartialMatches(args[1], List.of("favorites", "category", "visibility"), new ArrayList<>());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("rental") && args[1].equalsIgnoreCase("cancel")) {
                return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("discover") && args[1].equalsIgnoreCase("category")) {
                return StringUtil.copyPartialMatches(args[2], plugin.getConfig().getStringList("plot_discovery.categories"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("discover") && args[1].equalsIgnoreCase("visibility")) {
                return StringUtil.copyPartialMatches(args[2], List.of("on", "off"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("notify")) {
                if (args[1].equalsIgnoreCase("greetings")
                        || args[1].equalsIgnoreCase("admin")
                        || args[1].equalsIgnoreCase("all")) {
                    return Arrays.asList("on", "off");
                }
                if (args[1].equalsIgnoreCase("mode")) {
                    return Arrays.asList("chat", "actionbar", "title");
                }
            }

            if (args[0].equalsIgnoreCase("blocks")) {
                if (args[1].equalsIgnoreCase("earnings")) {
                    return Arrays.asList("on", "off", "status");
                }
                if (args[1].equalsIgnoreCase("buy") || args[1].equalsIgnoreCase("sell")) {
                    return Arrays.asList("1", "10", "64", "100", "500", "1000");
                }
            }

            if (args[0].equalsIgnoreCase("group")) {
                if (args[1].equalsIgnoreCase("deposit")) {
                    return Arrays.asList("100", "500", "1000", "5000", "10000");
                }
                if (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick")) {
                    List<String> options = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                    List<String> completions = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[2], options, completions);
                    return completions;
                }
            }
        }

        return null;
    }

    // --------------------------------------------------
    // Wand item (localized)
    // --------------------------------------------------

    private ItemStack createScepter(Player p) {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();

        if (meta != null) {
            String name = tr(p, "wand_item_name", "&bAegis Scepter");
            List<String> lore = Collections.emptyList();
            try {
                if (plugin.codex() != null) lore = plugin.codex().trList(p, "wand_item_lore");
            } catch (Throwable ignored) {}

            if (lore == null || lore.isEmpty()) {
                lore = Arrays.asList("&7Left/Right-click: Select corners");
            }

            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            meta.setLore(lore.stream()
                    .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                    .collect(Collectors.toList()));

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    SelectionService.WAND_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            NamespacedKey idKey = new NamespacedKey(plugin, "wand_id");
            meta.getPersistentDataContainer().set(
                    idKey,
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );

            rod.setItemMeta(meta);
        }

        return rod;
    }

    // --------------------------------------------------
    // Notify Command - Toggle Notifications
    // --------------------------------------------------

    /**
     * ✅ v1.2.6: /aegis notify [subcommand]
     * Manage player notification preferences with granular control.
     *
     * Subcommands:
     * - /aegis notify              → Legacy: toggle greetings (backwards compatible)
     * - /aegis notify greetings    → Toggle claim enter/exit messages
     * - /aegis notify admin        → Toggle admin update notifications
     * - /aegis notify mode <type>  → Set notification mode (chat/actionbar/title)
     * - /aegis notify status       → Show current notification settings
     */
    private void handleNotify(Player p, String[] args) {
        // Check if NotificationManager is available
        if (plugin.getNotificationManager() == null) {
            sendKey(p, "notify_unavailable", "&cNotification system unavailable.");
            plugin.effects().playError(p);
            return;
        }

        // No args: legacy behavior (toggle greetings)
        if (args.length == 1) {
            handleNotifyGreetings(p, args);
            return;
        }

        String subcommand = args[1].toLowerCase();

        switch (subcommand) {
            case "greetings", "greeting", "greet" -> handleNotifyGreetings(p, args);
            case "admin", "admins", "adminupdates" -> handleNotifyAdmin(p, args);
            case "all" -> handleNotifyAll(p, args);
            case "mode" -> handleNotifyMode(p, args);
            case "status", "settings", "info" -> handleNotifyStatus(p);
            default -> {
                sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify &8- &7Toggle greeting messages\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim enter/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings"
                );
            }
        }
    }

    private void handleNotifyGreetings(Player p, String[] args) {
        if (!canManageGreetingNotifications(p, true)) {
            plugin.effects().playError(p);
            return;
        }

        Boolean requested = parseBooleanArg(args.length >= 3 ? args[2] : null);
        if (args.length >= 3 && requested == null) {
            sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim entry/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings");
            plugin.effects().playError(p);
            return;
        }

        boolean newState;
        if (requested == null) {
            newState = plugin.getNotificationManager().toggleGreetings(p.getUniqueId());
        } else {
            var settings = plugin.getNotificationManager().getSettings(p.getUniqueId());
            settings.setGreetingsEnabled(requested);
            plugin.getNotificationManager().updateSettings(settings);
            newState = requested;
        }

        if (newState) {
            sendKey(p, "notify_greetings_enabled", "&aGreetings enabled. You will see claim enter/exit messages.");
            plugin.effects().playConfirm(p);
        } else {
            sendKey(p, "notify_greetings_disabled", "&cGreetings disabled. You will not see claim enter/exit messages.");
            plugin.effects().playError(p);
        }
    }

    private void handleNotifyAdmin(Player p, String[] args) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(p);
            return;
        }

        Boolean requested = parseBooleanArg(args.length >= 3 ? args[2] : null);
        if (args.length >= 3 && requested == null) {
            sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim entry/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings");
            plugin.effects().playError(p);
            return;
        }

        boolean newState;
        if (requested == null) {
            newState = plugin.getNotificationManager().toggleAdminUpdates(p.getUniqueId());
        } else {
            var settings = plugin.getNotificationManager().getSettings(p.getUniqueId());
            settings.setAdminUpdatesEnabled(requested);
            plugin.getNotificationManager().updateSettings(settings);
            newState = requested;
        }

        if (newState) {
            sendKey(p, "notify_admin_enabled", "&aAdmin notifications enabled.");
            plugin.effects().playConfirm(p);
        } else {
            sendKey(p, "notify_admin_disabled", "&cAdmin notifications disabled.");
            plugin.effects().playError(p);
        }
    }

    private void handleNotifyAll(Player p, String[] args) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(p);
            return;
        }

        if (!canManageGreetingNotifications(p, true)) {
            plugin.effects().playError(p);
            return;
        }

        Boolean requested = parseBooleanArg(args.length >= 3 ? args[2] : null);
        if (args.length >= 3 && requested == null) {
            sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim entry/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings");
            plugin.effects().playError(p);
            return;
        }

        var settings = plugin.getNotificationManager().getSettings(p.getUniqueId());
        boolean newState = requested != null ? requested : !(settings.isGreetingsEnabled() && settings.isAdminUpdatesEnabled());
        settings.setGreetingsEnabled(newState);
        settings.setAdminUpdatesEnabled(newState);
        plugin.getNotificationManager().updateSettings(settings);

        if (newState) {
            sendKey(p, "notify_greetings_enabled", "&aGreetings enabled. You will see claim enter/exit messages.");
            sendKey(p, "notify_admin_enabled", "&aAdmin notifications enabled.");
            plugin.effects().playConfirm(p);
        } else {
            sendKey(p, "notify_greetings_disabled", "&cGreetings disabled. You will not see claim enter/exit messages.");
            sendKey(p, "notify_admin_disabled", "&cAdmin notifications disabled.");
            plugin.effects().playError(p);
        }
    }

    private void handleNotifyMode(Player p, String[] args) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(p);
            return;
        }

        if (args.length < 3) {
            sendKey(p, "notify_mode_usage",
                "&eUsage: &7/aegis notify mode <chat|actionbar|title>\n" +
                "&7Available modes:\n" +
                "&7- &bchat &8- &7Standard chat messages\n" +
                "&7- &bactionbar &8- &7Action bar notifications (above hotbar)\n" +
                "&7- &btitle &8- &7Title/subtitle notifications (center screen)"
            );
            return;
        }

        String modeStr = args[2].trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("ACTIONBAR".equals(modeStr)) modeStr = "ACTION_BAR";

        com.aegisguard.notify.NotificationMode mode = null;
        for (com.aegisguard.notify.NotificationMode candidate : com.aegisguard.notify.NotificationMode.values()) {
            if (candidate.name().equals(modeStr)) {
                mode = candidate;
                break;
            }
        }
        if (mode == null) {
            sendKey(p, "notify_mode_invalid",
                "&cInvalid mode: &7" + args[2] + "\n" +
                "&eValid modes: &7chat, actionbar, title"
            );
            plugin.effects().playError(p);
            return;
        }

        plugin.getNotificationManager().setMode(p.getUniqueId(), mode);
        sendKey(p, "notify_mode_set", "&aNotification mode set to: &b" + mode.getDisplayName());
        plugin.effects().playConfirm(p);
    }

    private void handleNotifyStatus(Player p) {
        com.aegisguard.notify.PlayerNotificationSettings settings =
            plugin.getNotificationManager().getSettings(p.getUniqueId());

        boolean effectiveGreetings = areGreetingNotificationsEffectivelyEnabled(p, settings);
        String greetingStatus = effectiveGreetings ? "&aEnabled" : "&cDisabled";
        String adminStatus = settings.isAdminUpdatesEnabled() ? "&aEnabled" : "&cDisabled";
        String mode = "&b" + resolveEffectiveMode(settings).getDisplayName();

        sendKey(p, "notify_status",
            "&e&l━━━━━━━━━ &6Notification Settings &e&l━━━━━━━━━\n" +
            "&7Greetings: {GREETINGS}\n" +
            "&7Admin Updates: {ADMIN}\n" +
            "&7Mode: {MODE}\n" +
            "&e&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            Map.of(
                    "GREETINGS", greetingStatus,
                    "ADMIN", adminStatus,
                    "MODE", mode
            )
        );
    }

    private boolean canManageGreetingNotifications(Player p, boolean togglingState) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return false;
        }

        String base = "titles.claim_enter_exit";
        boolean enabled = plugin.getConfig().getBoolean(base + ".enabled", true);
        String mode = plugin.getConfig().getString(base + ".mode", "PER_PLAYER").toUpperCase(Locale.ROOT);
        boolean allowToggle = plugin.getConfig().getBoolean(base + ".allow_player_toggle", true);
        String requiredPermission = plugin.getConfig().getString(base + ".required_permission", "");
        String bypassPermission = plugin.getConfig().getString(base + ".bypass_permission", "aegis.notify.bypass");

        if (!enabled) {
            if (bypassPermission != null && !bypassPermission.isBlank() && p.hasPermission(bypassPermission)) {
                return true;
            }
            sendKey(p, "claim_enter_exit_notify_server_disabled",
                    "&cClaim enter/exit notifications are disabled server-wide.");
            return false;
        }

        if (requiredPermission != null && !requiredPermission.isBlank() && !p.hasPermission(requiredPermission)) {
            sendKey(p, "claim_enter_exit_notify_server_disabled",
                    "&cClaim enter/exit notifications are disabled server-wide.");
            return false;
        }

        if (togglingState && !"PER_PLAYER".equals(mode)) {
            sendKey(p, "claim_enter_exit_notify_toggle_not_allowed",
                    "&cThis server does not allow players to toggle claim notifications.");
            return false;
        }

        if (togglingState && !allowToggle) {
            sendKey(p, "claim_enter_exit_notify_toggle_not_allowed",
                    "&cThis server does not allow players to toggle claim notifications.");
            return false;
        }

        return true;
    }

    private boolean areGreetingNotificationsEffectivelyEnabled(Player p,
                                                               com.aegisguard.notify.PlayerNotificationSettings settings) {
        String base = "titles.claim_enter_exit";
        boolean enabled = plugin.getConfig().getBoolean(base + ".enabled", true);
        String mode = plugin.getConfig().getString(base + ".mode", "PER_PLAYER").toUpperCase(Locale.ROOT);
        String requiredPermission = plugin.getConfig().getString(base + ".required_permission", "");
        String bypassPermission = plugin.getConfig().getString(base + ".bypass_permission", "aegis.notify.bypass");

        if (!enabled) {
            return bypassPermission != null && !bypassPermission.isBlank() && p.hasPermission(bypassPermission);
        }

        if (requiredPermission != null && !requiredPermission.isBlank() && !p.hasPermission(requiredPermission)) {
            return false;
        }

        if ("FORCE_ON".equals(mode)) return true;
        if ("FORCE_OFF".equals(mode)) {
            return bypassPermission != null && !bypassPermission.isBlank() && p.hasPermission(bypassPermission);
        }

        return settings == null || settings.isGreetingsEnabled();
    }

    private com.aegisguard.notify.NotificationMode resolveEffectiveMode(
            com.aegisguard.notify.PlayerNotificationSettings settings
    ) {
        String configured = plugin.getConfig().getString("titles.claim_enter_exit.notification_location",
                plugin.getConfig().getString("titles.notification_location", "ACTION_BAR"));

        if (settings == null || settings.getMode() == null) {
            return com.aegisguard.notify.NotificationMode.fromString(configured);
        }

        return settings.getMode();
    }

    private Boolean parseBooleanArg(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled", "yes" -> Boolean.TRUE;
            case "off", "false", "disable", "disabled", "no" -> Boolean.FALSE;
            default -> null;
        };
    }
}
