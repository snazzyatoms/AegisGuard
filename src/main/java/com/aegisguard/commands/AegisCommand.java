package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.ClaimPricingCalculator;  // ✅ NEW: Fair Pricing Calculator
import com.aegisguard.economy.CurrencyType;  // ✅ NEW: Currency Type enum
import com.aegisguard.selection.SelectionService;
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
            "wand", "menu", "claim", "unclaim", "resize", "help",
            "setspawn", "home", "welcome", "farewell",
            "sell", "unsell", "market", "auction",
            "kick", "ban", "unban", "visit",
            "level", "zone", "like",
            "rename", "stuck", "setdesc", "merge",
            "consume", "ledger", "blocks",
            // ✅ Added: reload support (Codex + config)
            "reload", "refresh",
            // ✅ NEW: cost preview command
            "cost",
            // ✅ NEW: notify command for toggling notifications
            "notify"
    };

    private static final String[] RESIZE_DIRECTIONS = {"north", "south", "east", "west"};

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
        this.exchange = new ClaimBlockExchangeService(plugin);
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

            case "resize" -> handleResize(p, args);

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

            case "merge" -> {
                if (args.length < 2) {
                    sendKey(p, "usage_merge", "&cUsage: /ag merge <N/S/E/W>");
                } else {
                    plugin.selection().attemptMerge(p, args[1]);
                }
            }

            case "market" -> plugin.gui().market().open(p, 0);

            case "sell" -> handleSell(p, args);

            case "unsell" -> handleUnsell(p);

            case "auction" -> plugin.gui().auction().open(p, 0);

            case "level" -> openLevelMenu(p);

            case "zone" -> openZoneMenu(p);

            case "like" -> handleLike(p);

            case "consume" -> plugin.selection().manualConsumeWand(p);

            case "ledger" -> showLedger(p);

            case "blocks" -> handleBlocks(p, args);

            // ✅ Added: /aegis reload [soft|nogui]
            case "reload", "refresh" -> handleReload(p, args);

            // ✅ NEW: /aegis cost - Preview claim cost
            case "cost" -> handleCostPreview(p);

            // ✅ NEW: /aegis notify - Toggle player notifications
            case "notify" -> handleNotify(p);

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

    // --------------------------------------------------
    // Rename / Description
    // --------------------------------------------------

    private void handleRename(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.getOwner().equals(p.getUniqueId()) && !plugin.isAdmin(p)) {
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

        plot.setEntryTitle(name);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "plot_renamed", "&a✔ Plot renamed to: &r{NAME}",
                Map.of("NAME", name));
        plugin.effects().playConfirm(p);
    }

    private void handleSetDesc(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.getOwner().equals(p.getUniqueId()) && !plugin.isAdmin(p)) {
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
    // Resize
    // --------------------------------------------------

    private void handleResize(Player p, String[] args) {
        if (args.length < 3) {
            sendKey(p, "resize_usage", "&cUsage: /aegis resize <direction> <amount>");
            sendKey(p, "resize_directions", "&eDirections: &f{DIRS}",
                    Map.of("DIRS", String.join(", ", RESIZE_DIRECTIONS)));
            return;
        }

        String direction = args[1].toLowerCase(Locale.ROOT);

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendKey(p, "amount_must_number", "&cAmount must be a number.");
            return;
        }

        if (amount <= 0) {
            sendKey(p, "amount_must_positive", "&cAmount must be positive.");
            return;
        }

        if (!Arrays.asList(RESIZE_DIRECTIONS).contains(direction)) {
            sendKey(p, "invalid_direction", "&cInvalid direction. Use: &f{DIRS}",
                    Map.of("DIRS", String.join(", ", RESIZE_DIRECTIONS)));
            return;
        }

        // ✅ Optional: Show expansion cost preview if fair pricing is enabled
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot != null && plot.getOwner().equals(p.getUniqueId())) {
            ClaimPricingCalculator pricing = plugin.getPricingCalculator();

            if (pricing != null && pricing.isEnabled() && plugin.eco() != null && plugin.eco().isVaultEnabled()) {
                // Calculate additional blocks based on direction
                long currentWidth = Math.abs(plot.getX2() - plot.getX1()) + 1;
                long currentLength = Math.abs(plot.getZ2() - plot.getZ1()) + 1;
                long additionalBlocks;

                if (direction.equals("north") || direction.equals("south")) {
                    additionalBlocks = currentWidth * amount;
                } else {
                    additionalBlocks = currentLength * amount;
                }

                double expansionCost = pricing.calculateExpansionCost(additionalBlocks);

                if (expansionCost > 0) {
                    String formatted = plugin.eco().format(expansionCost, CurrencyType.VAULT);
                    sendMsg(p, "&7Expansion cost: &e" + formatted + " &7for &e" + additionalBlocks + " &7blocks");
                }
            }
        }

        plugin.selection().resizePlot(p, direction, amount);
    }

    // --------------------------------------------------
    // Kick / Ban / Unban
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
        if (!kPlot.getOwner().equals(p.getUniqueId()) && !plugin.isAdmin(p)) {
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
        if (bPlot == null || !bPlot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        OfflinePlayer bTarget = Bukkit.getOfflinePlayer(args[1]);
        if (bTarget.getUniqueId() != null && bTarget.getUniqueId().equals(p.getUniqueId())) {
            sendKey(p, "ban_self_error", "&cCannot ban yourself.");
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
        if (uPlot == null || !uPlot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
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
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
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
        if (homePlot == null || !homePlot.getOwner().equals(p.getUniqueId())) {
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
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
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
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "sell_usage", "&cUsage: /ag sell <price>");
            return;
        }

        try {
            double price = Double.parseDouble(args[1]);
            if (price <= 0) {
                sendKey(p, "price_must_positive", "&cPrice must be positive.");
                return;
            }

            plot.setForSale(true, price);

            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);

            String formatted = plugin.eco().format(price, CurrencyType.VAULT);

            sendKey(p, "market-for-sale", "&a✔ Claim listed for &6{PRICE}&a.",
                    Map.of("PRICE", formatted));

        } catch (NumberFormatException e) {
            sendKey(p, "invalid_number", "&cInvalid number.");
        }
    }

    private void handleUnsell(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        plot.setForSale(false, 0);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "market-not-for-sale", "&e✔ Claim listing removed.");
    }

    // --------------------------------------------------
    // Menus
    // --------------------------------------------------

    private void openLevelMenu(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        if (plugin.cfg().isLevelingEnabled()) plugin.gui().leveling().open(p, plot);
        else sendKey(p, "leveling_disabled", "&cLeveling is disabled.");
    }

    private void openZoneMenu(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        if (plugin.cfg().isZoningEnabled()) plugin.gui().zoning().open(p, plot);
        else sendKey(p, "zoning_disabled", "&cZoning is disabled.");
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
            if (args[0].equalsIgnoreCase("resize") || args[0].equalsIgnoreCase("merge")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], Arrays.asList(RESIZE_DIRECTIONS), completions);
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
                List<String> subs = Arrays.asList("rates", "buy", "sell", "help");
                StringUtil.copyPartialMatches(args[1], subs, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("blocks")) {
                if (args[1].equalsIgnoreCase("buy") || args[1].equalsIgnoreCase("sell")) {
                    return Arrays.asList("1", "10", "64", "100", "500", "1000");
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
     * ✅ NEW: /aegis notify
     * Toggles player notification preferences.
     * When disabled, the player won't receive certain notifications (e.g., claim events, admin messages).
     */
    private void handleNotify(Player p) {
        // Try to get the current notification state from player's persistent data or config
        boolean notificationsEnabled = true;
        
        try {
            // Check if plugin has a method to get/set player notifications
            // This could be stored in player data, config, or a separate manager
            if (plugin.store() != null) {
                // Try reflection to find notification methods
                try {
                    Method getMethod = plugin.store().getClass().getMethod("getNotifications", UUID.class);
                    Object result = getMethod.invoke(plugin.store(), p.getUniqueId());
                    if (result instanceof Boolean) {
                        notificationsEnabled = (Boolean) result;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Method doesn't exist, use default
                }
            }
        } catch (Throwable t) {
            // Fall back to checking config or default value
            notificationsEnabled = plugin.getConfig().getBoolean("player." + p.getUniqueId() + ".notifications", true);
        }
        
        // Toggle the notification state
        boolean newState = !notificationsEnabled;
        
        try {
            // Try to save the new state
            if (plugin.store() != null) {
                try {
                    Method setMethod = plugin.store().getClass().getMethod("setNotifications", UUID.class, boolean.class);
                    setMethod.invoke(plugin.store(), p.getUniqueId(), newState);
                } catch (NoSuchMethodException ignored) {
                    // Store in config as fallback
                    plugin.getConfig().set("player." + p.getUniqueId() + ".notifications", newState);
                    plugin.saveConfig();
                }
            } else {
                // Store in config
                plugin.getConfig().set("player." + p.getUniqueId() + ".notifications", newState);
                plugin.saveConfig();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[AegisCommand] Failed to save notification preference: " + t.getMessage());
            sendKey(p, "notify_error", "&cFailed to save notification preference.");
            return;
        }
        
        // Send confirmation message
        if (newState) {
            sendKey(p, "notify_enabled", "&aNotifications enabled. You will receive claim and admin notifications.");
            if (plugin.effects() != null) {
                try {
                    plugin.effects().playConfirm(p);
                } catch (Throwable ignored) {}
            }
        } else {
            sendKey(p, "notify_disabled", "&cNotifications disabled. You will not receive claim and admin notifications.");
            if (plugin.effects() != null) {
                try {
                    plugin.effects().playError(p);
                } catch (Throwable ignored) {}
            }
        }
    }
}
