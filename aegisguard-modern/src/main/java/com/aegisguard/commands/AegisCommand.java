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
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
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

/**
 * {@code /aegis} root command: permission/module gating, subcommand dispatch,
 * tab completion, help, and the small GUI-opening handlers that have no feature
 * module of their own. Feature subcommands are delegated to the sibling
 * *Commands classes in this package.
 */
public class AegisCommand extends CommandBase implements CommandExecutor, TabCompleter {

    private final ClaimBlockExchangeService exchange;
    private final ClaimsCommands claims;
    private final MarketCommands market;
    private final ChatCommands chat;
    private final SocialCommands social;
    private final RealmCommands realm;
    private final TravelCommands travel;
    private final EconomyCommands econ;
    private final BackupCommands backups;

    private static final String[] SUB_COMMANDS = {
            "wand", "menu", "claim", "quickclaim", "qc", "unclaim", "help",
            "backup", "backups",
            "setspawn", "home", "welcome", "farewell",
            "sell", "unsell", "rent", "unrent", "rental", "market", "auction",
            "kick", "ban", "unban", "visit", "travel",
            "level", "zone", "subplot", "subzone", "like",
            "rename", "stuck", "setdesc", "notice", "profile", "guide",
            "consume", "ledger", "blocks", "giftblocks", "merge", "biome",
            "group", "alliance", "arena", "beacon", "caravan", "gathering", "gatherings", "openhouse", "chat", "frequency", "staff", "staffchat",
            "discover", "favorite", "activity",
            "transfer", "heir", "succession", "settlements", "roles",
            // Cross-server (BungeeCord network) travel
            "hub", "lobby", "servers",
            // ✅ Added: reload support (Codex + config)
            "reload", "refresh",
            // ✅ NEW: cost preview command
            "cost",
            // ✅ NEW: notify command for toggling notifications
            "notify"
    };

    // Feature-gated subcommands: subcommand -> required permission node.
    private static final Map<String, String> SUB_PERMISSIONS = Map.ofEntries(
            Map.entry("wand", "aegis.wand"),
            Map.entry("menu", "aegis.menu"),
            Map.entry("claim", "aegis.claim"),
            Map.entry("quickclaim", "aegis.claim"),
            Map.entry("qc", "aegis.claim"),
            Map.entry("beacon", "aegis.beacon"),
            Map.entry("backup", "aegis.backup"),
            Map.entry("backups", "aegis.backup"),
            Map.entry("gathering", "aegis.gathering"),
            Map.entry("gatherings", "aegis.gathering"),
            Map.entry("openhouse", "aegis.gathering")
    );

    public AegisCommand(AegisGuard plugin) {
        super(plugin);
        this.exchange = plugin.exchange();
        this.claims = new ClaimsCommands(plugin);
        this.market = new MarketCommands(plugin);
        this.chat = new ChatCommands(plugin);
        this.social = new SocialCommands(plugin);
        this.realm = new RealmCommands(plugin);
        this.travel = new TravelCommands(plugin);
        this.econ = new EconomyCommands(plugin);
        this.backups = new BackupCommands(plugin);
    }

    /** Dynamic /hub,/lobby aliases registered through the CommandMap. */
    public void hub(Player player) {
        travel.hub(player);
    }

    /** GUI claim entry point (PlayerGUI delegates here). */
    public void runClaim(Player player) {
        claims.runClaim(player);
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
                    sendHelp(sender, args.length > 1 ? args[1] : null);
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

        String head = args[0].toLowerCase(Locale.ROOT);
        boolean chatFamily = head.equals("chat") || head.equals("frequency");
        com.aegisguard.config.Modules.Id gated = chatFamily
                ? null : com.aegisguard.config.Modules.commandModule(args[0]);
        if (gated != null && !plugin.modules().on(gated)) {
            sendKey(p, "module_disabled", "&c{MODULE} is disabled on this server.",
                    Map.of("MODULE", gated.displayName()));
            plugin.effects().playError(p);
            return true;
        }

        String requiredNode = SUB_PERMISSIONS.get(head);
        if (requiredNode != null && !p.hasPermission(requiredNode)) {
            sendKey(p, "no_permission", "&cYou do not have permission to use this command.");
            plugin.effects().playError(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> {
                giveClaimWand(p);
            }

            case "menu" -> plugin.gui().openMain(p);

            case "claim" -> claims.handleClaim(p);

            case "quickclaim", "qc" -> claims.handleQuickClaim(p, args);

            case "unclaim" -> plugin.selection().unclaimHere(p);

            case "kick" -> realm.handleKick(p, args);

            case "ban" -> realm.handleBan(p, args);

            case "unban" -> realm.handleUnban(p, args);

            case "visit" -> {
                if (!plugin.cfg().isTravelSystemEnabled()) {
                    sendKey(p, "travel_system_disabled", "&cTravel system is disabled.");
                    return true;
                }
                plugin.gui().visit().open(p, 0, false);
            }

            case "travel" -> {
                if (!plugin.cfg().isTravelSystemEnabled()) {
                    sendKey(p, "travel_system_disabled", "&cTravel system is disabled.");
                    return true;
                }
                plugin.gui().travelHub().open(p);
            }

            case "setspawn" -> travel.handleSetSpawn(p);

            case "home" -> travel.handleHome(p);

            case "welcome" -> realm.handleWelcomeFarewell(p, args, true);

            case "farewell" -> realm.handleWelcomeFarewell(p, args, false);

            case "stuck" -> travel.handleStuck(p);

            case "hub", "lobby" -> travel.handleNetworkHub(p);

            case "servers" -> travel.handleNetworkServers(p);

            case "rename" -> realm.handleRename(p, args);

            case "setdesc" -> realm.handleSetDesc(p, args);

            case "notice" -> realm.handleNotice(p, args);

            case "profile" -> plugin.gui().realmProfile().open(p);

            case "biome" -> claims.handleBiome(p);

            case "guide" -> plugin.gui().walkthrough().open(p, 0);

            case "market" -> market.openMarketMenu(p, args);

            case "sell" -> market.handleSell(p, args);

            case "unsell" -> market.handleUnsell(p);

            case "rent" -> market.handleRentListing(p, args);

            case "unrent" -> market.handleUnrent(p);

            case "rental" -> market.handleRentalContract(p, args);

            case "discover" -> market.handleDiscover(p, args);

            case "favorite" -> market.handleFavorite(p);

            case "activity" -> market.handleActivity(p);

            case "auction" -> plugin.gui().auction().open(p, 0);

            case "level" -> openLevelMenu(p);

            case "zone" -> openZoneMenu(p);

            case "subplot", "subzone" -> claims.handleCreateSubplot(p, args);

            case "like" -> market.handleLike(p);

            case "consume" -> plugin.selection().manualConsumeWand(p);

            case "ledger" -> econ.showLedger(p);

            case "blocks" -> econ.handleBlocks(p, args);

            case "giftblocks" -> econ.handleGiftBlocks(p, args);

            case "merge" -> {
                if (!plugin.getConfig().getBoolean("claims.merging.enabled", true)) {
                    sendKey(p, "claim_merge_disabled", "&cClaim merging is disabled on this server.");
                } else {
                    plugin.gui().claimMerge().open(p);
                }
            }

            case "group" -> social.handleGroup(p, args);
            case "transfer" -> social.handleTransfer(p, args);
            case "settlements" -> plugin.gui().settlementsInbox().open(p);

            case "alliance" -> social.handleAlliance(p, args);
            case "arena" -> {
                if (plugin.arena() == null) {
                    sendKey(p, "arena_unavailable", "&cArena module is unavailable.");
                    return true;
                }
                String[] rest = args.length <= 1 ? new String[0] : java.util.Arrays.copyOfRange(args, 1, args.length);
                return new com.aegisguard.arena.ArenaCommand(plugin, plugin.arena()).handle(p, rest);
            }

            case "beacon" -> {
                if (plugin.beacons() == null || !plugin.beacons().isEnabled()
                        || plugin.gui() == null || plugin.gui().beacons() == null) {
                    sendKey(p, "beacon_unavailable", "&cTeleport Beacons are unavailable.");
                    return true;
                }
                if (args.length >= 2 && (args[1].equalsIgnoreCase("give")
                        || args[1].equalsIgnoreCase("pad")
                        || args[1].equalsIgnoreCase("kit"))) {
                    plugin.beacons().giveStarterPads(p);
                    plugin.gui().beacons().openManager(p);
                    break;
                }
                plugin.gui().beacons().openManager(p);
            }

            case "caravan", "caravans" -> travel.handleCaravan(p, args);
            case "backup", "backups" -> backups.handleBackup(p, args);

            case "gathering", "gatherings", "openhouse" -> travel.handleGathering(p, args);

            // 1.4: per-plot public arrival choice (classic vs beacon) for the plot you manage.
            case "arrival" -> realm.handleArrival(p, args);

            case "roles" -> realm.handleRoles(p, args);
            case "heir" -> realm.handleHeir(p, args);
            case "succession" -> realm.handleSuccession(p, args);

            // ✅ Added: /aegis reload [soft|nogui]
            case "reload", "refresh" -> handleReload(p, args);

            // ✅ NEW: /aegis cost - Preview claim cost
            case "cost" -> claims.handleCostPreview(p);

            // ✅ NEW: /aegis notify - Manage player notifications (v1.2.6 enhanced)
            case "notify" -> chat.handleNotify(p, args);

            case "chat", "frequency" -> chat.handlePlotChat(p, args);

            case "staff", "staffchat" -> chat.handleStaffChat(p, args);

            case "help" -> sendHelp(p, args.length > 1 ? args[1] : null);

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


    // --------------------------------------------------
    // Help
    // --------------------------------------------------

    // Help categories — each maps to the subcommand tokens that belong to it. The codex
    // help_lines list is filtered by extracting the "/aegis <sub>" token from each line,
    // so per-locale packs need no restructuring; new lines categorize automatically.
    private static final Map<String, List<String>> HELP_CATEGORIES = Map.of(
            "claims", List.of("wand", "menu", "claim", "quickclaim", "qc", "unclaim", "expand",
                    "merge", "transfer", "rename", "setdesc", "welcome", "farewell", "guide",
                    "setspawn", "border", "cost", "consume", "backup", "backups"),
            "members", List.of("trust", "untrust", "roles", "member", "members", "ban", "unban",
                    "kick", "guest", "guestpass", "alliance", "group", "heir", "succession", "notify"),
            "flags", List.of("flags", "flag", "preset", "biome", "zone", "subplot", "subzone", "cosmetics"),
            "economy", List.of("market", "auction", "sell", "unsell", "rent", "unrent", "rental",
                    "claimblocks", "blocks", "giftblocks", "exchange", "settlements", "stall", "shop", "ledger"),
            "travel", List.of("visit", "travel", "home", "spawn", "beacon", "routes", "route",
                    "stuck", "discover", "favorite", "activity", "hub", "lobby", "servers"),
            "social", List.of("chat", "frequency", "staff", "staffchat", "gathering", "gatherings",
                    "openhouse", "notice", "hearth", "caravan", "caravans", "like", "level", "profile"),
            "admin", List.of("admin", "agadmin", "doctor", "inspect", "reload", "refresh",
                    "bypass", "import", "export", "migrate")
    );

    private static final List<String> HELP_CATEGORY_ORDER =
            List.of("claims", "members", "flags", "economy", "travel", "social", "admin");

    private void sendHelp(CommandSender sender) {
        sendHelp(sender, null);
    }

    private void sendHelp(CommandSender sender, String category) {
        sendKey(sender, "help_header", "&bAegisGuard Help");

        List<String> helpLines = new ArrayList<>();
        try {
            if (plugin.codex() != null) {
                List<String> lines = plugin.codex().trList(sender, "help_lines");
                if (lines != null) helpLines.addAll(lines);
                List<String> extra = plugin.codex().trList(sender, "help_lines_extra");
                if (extra != null) helpLines.addAll(extra);
            }
        } catch (Throwable ignored) {}

        if (category != null && !category.isBlank()) {
            String cat = category.toLowerCase(Locale.ROOT);
            List<String> subs = HELP_CATEGORIES.get(cat);
            if (subs == null) {
                sendKey(sender, "help_unknown_category", "&cUnknown help category. Try: &fclaims, members, flags, economy, travel, social, admin");
                return;
            }
            sendKey(sender, "help_category_" + cat, "&e— {CATEGORY} —",
                    Map.of("CATEGORY", cat));
            List<String> matched = filterHelpLines(helpLines, subs);
            if (matched.isEmpty()) {
                sendKey(sender, "help_category_empty",
                        "&7Nothing documented here yet — see &e/ag help&7 for all commands.");
            } else {
                for (String line : matched) sendMsg(sender, line);
            }
            return;
        }

        // Overview: category index + the essential lines (first-time commands).
        sendKey(sender, "help_hint",
                "&7Use &e/ag help <category> &7— &fclaims, members, flags, economy, travel, social, admin");
        sendKey(sender, "help_essentials_header", "&6Essential commands:");
        List<String> essentials = filterHelpLines(helpLines,
                List.of("wand", "menu", "claim", "visit", "travel", "home", "help"));
        if (essentials.isEmpty() && helpLines != null && !helpLines.isEmpty()) {
            for (String line : helpLines) sendMsg(sender, line);
            return;
        }
        for (String line : essentials) sendMsg(sender, line);
    }

    /**
     * Keep only help lines whose "/aegis <sub>" (or "/ag <sub>") token belongs to the given
     * category. Lines with no recognizable command token stay out of category views.
     */
    private List<String> filterHelpLines(List<String> lines, List<String> subs) {
        List<String> out = new ArrayList<>();
        if (lines == null) return out;
        for (String line : lines) {
            if (line == null) continue;
            String token = extractHelpSubcommand(line);
            if (token != null && subs.contains(token)) out.add(line);
        }
        return out;
    }

    private static String extractHelpSubcommand(String line) {
        String plain = line.toLowerCase(Locale.ROOT);
        int idx = plain.indexOf("/aegis ");
        int len = 7;
        if (idx < 0) {
            idx = plain.indexOf("/ag ");
            len = 4;
        }
        if (idx < 0) return null;
        String rest = plain.substring(idx + len).trim();
        int space = rest.indexOf(' ');
        String sub = space < 0 ? rest : rest.substring(0, space);
        // Strip any leading non-letter (e.g. "<", "[") so usage args don't leak in.
        sub = sub.replaceAll("[^a-z]", "");
        return sub.isEmpty() ? null : sub;
    }


    // --------------------------------------------------
    // Tab completion
    // --------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> visible = new ArrayList<>();
            for (String sub : SUB_COMMANDS) {
                if (("staff".equals(sub) || "staffchat".equals(sub))
                        && (plugin.plotChat() == null || !plugin.plotChat().canHearStaff(
                        sender instanceof Player player ? player : null))) {
                    continue;
                }
                com.aegisguard.config.Modules.Id gated = com.aegisguard.config.Modules.commandModule(sub);
                if (gated != null && !plugin.modules().on(gated)) continue;
                String requiredNode = SUB_PERMISSIONS.get(sub);
                if (requiredNode != null && !sender.hasPermission(requiredNode)) continue;
                visible.add(sub);
            }
            StringUtil.copyPartialMatches(args[0], visible, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("help")) {
                List<String> filtered = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], HELP_CATEGORY_ORDER, filtered);
                Collections.sort(filtered);
                return filtered;
            }
            if (args[0].equalsIgnoreCase("subplot") || args[0].equalsIgnoreCase("subzone")) {
                return Arrays.asList("Market Stall", "Room", "Hotel Suite", "Storage", "Booth");
            }

            if (args[0].equalsIgnoreCase("quickclaim") || args[0].equalsIgnoreCase("qc")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("5", "10", "15", "25"), completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("roles")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("lock", "unlock", "undo"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args[0].equalsIgnoreCase("succession")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("assume", "rollback", "menu"), completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("beacon")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("give", "pad", "kit"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args[0].equalsIgnoreCase("caravan") || args[0].equalsIgnoreCase("caravans")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("menu", "list", "status", "cancel"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args[0].equalsIgnoreCase("gathering") || args[0].equalsIgnoreCase("gatherings")
                    || args[0].equalsIgnoreCase("openhouse")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("start", "stop", "menu"), completions);
                Collections.sort(completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("chat") || args[0].equalsIgnoreCase("frequency")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("off", "alliance", "group"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args[0].equalsIgnoreCase("staff") || args[0].equalsIgnoreCase("staffchat")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("off"), completions);
                Collections.sort(completions);
                return completions;
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

            if (args[0].equalsIgnoreCase("alliance")) {
                List<String> completions = new ArrayList<>();
                List<String> subs = Arrays.asList("create", "invite", "accept", "leave", "disband", "menu", "status");
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
            if (args[0].equalsIgnoreCase("notice")) {
                return StringUtil.copyPartialMatches(args[1], List.of("add", "remove", "list"), new ArrayList<>());
            }
        }

        if (args.length == 3) {
            if ((args[0].equalsIgnoreCase("chat") || args[0].equalsIgnoreCase("frequency"))
                    && (args[1].equalsIgnoreCase("alliance") || args[1].equalsIgnoreCase("group"))) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], List.of("off", "name", "rename"), completions);
                Collections.sort(completions);
                return completions;
            }
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

            if (args[0].equalsIgnoreCase("alliance") && args[1].equalsIgnoreCase("invite")) {
                List<String> options = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], options, completions);
                return completions;
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

    /** Gives one ordinary claim wand. */
    public boolean giveClaimWand(Player player) {
        if (player == null || SelectionService.playerHasAnyWand(player)) {
            if (player != null) {
                sendKey(player, "wand_already_on", "&eYou already have the Aegis Scepter in your inventory.");
                plugin.effects().playError(player);
            }
            return false;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createScepter(player));
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        sendKey(player, "wand_given", "&a⚡ You received the Aegis Scepter.");
        plugin.effects().playConfirm(player);
        return true;
    }


}
