package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockData;
import com.aegisguard.data.Plot;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.migration.MigrationManager.MigrationOptions;
import com.aegisguard.migration.MigrationManager.SourcePlugin;
import com.aegisguard.selection.SelectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AdminCommand - v1.2.6
 * 
 * Subcommands:
 *   reload   - Reload configuration and data
 *   bypass   - Check bypass mode status
 *   menu     - Open admin GUI
 *   convert  - Convert plot to server zone
 *   wand     - Get admin scepter
 *   blocks   - Manage player claim blocks
 *   migrate  - Import claims from other plugins (NEW)
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final MigrationManager migrationManager;

    private static final String[] SUB_COMMANDS = { 
        "reload", "bypass", "menu", "convert", "wand", "blocks", "migrate" 
    };

    private static final String[] MIGRATE_ACTIONS = { 
        "list", "preview", "import", "help" 
    };

    private static final String[] MIGRATE_SOURCES = { 
        "griefprevention", "gp", "griefdefender", "gd", "lands" 
    };

    private static final String[] MIGRATE_OPTIONS = {
        "--dry-run", "--force", "--no-trusted", "--no-flags", "--world="
    };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
        this.migrationManager = new MigrationManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // --- CONSOLE HANDLING ---
        if (!(sender instanceof Player p)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.cfg().reload();
                plugin.msg().reload();
                plugin.worldRules().reload();
                plugin.store().load();
                sender.sendMessage("[AegisGuard] Reload complete.");
                return true;
            } 
            if (args.length > 0 && args[0].equalsIgnoreCase("blocks")) {
                handleBlocks(sender, args);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("migrate")) {
                handleMigrate(sender, args);
                return true;
            }
            
            sender.sendMessage("[AegisGuard] GUI commands are player-only. Use 'aegisadmin reload', 'aegisadmin blocks', or 'aegisadmin migrate'.");
            return true;
        }

        // --- PERMISSION CHECK ---
        if (!p.hasPermission("aegis.admin")) {
            plugin.msg().send(p, "no_perm");
            return true;
        }

        // --- DEFAULT: OPEN MENU ---
        if (args.length == 0) {
            plugin.gui().admin().open(p);
            return true;
        }

        // --- SUBCOMMANDS ---
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.cfg().reload();
                plugin.msg().reload();
                plugin.worldRules().reload();
                plugin.store().load();
                p.sendMessage(ChatColor.GREEN + "✔ [AegisGuard] Configuration & Data reloaded.");
                plugin.effects().playConfirm(p);
                break;
                
            case "bypass":
                if (p.hasPermission("aegis.admin.bypass")) {
                    p.sendMessage(ChatColor.YELLOW + "⚠ You currently have Bypass Mode enabled via permissions.");
                } else {
                    p.sendMessage(ChatColor.RED + "❌ You do not have 'aegis.admin.bypass'.");
                }
                break;
                
            case "menu":
                plugin.gui().admin().open(p);
                break;
                
            // --- CONVERT TO SERVER ZONE ---
            case "convert":
                if (!p.hasPermission("aegis.convert")) { plugin.msg().send(p, "no_perm"); return true; }
                
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null) {
                    p.sendMessage(ChatColor.RED + "❌ You must be standing in a plot to convert it.");
                    return true;
                }
                
                // 1. Change Owner to Server UUID
                plugin.store().changePlotOwner(plot, Plot.SERVER_OWNER_UUID, "Server");
                
                // 2. Lock Down Flags
                plot.setFlag("pvp", false);
                plot.setFlag("mobs", false);
                plot.setFlag("build", false);
                plot.setFlag("safe_zone", true);
                
                plugin.store().setDirty(true);
                p.sendMessage(ChatColor.GREEN + "✔ Plot '" + plot.getPlotId().toString().substring(0,8) + "' converted to Server Zone.");
                plugin.effects().playConfirm(p);
                break;
                
            // --- ADMIN WAND ---
            case "wand":
                if (!p.hasPermission("aegis.admin.wand")) { plugin.msg().send(p, "no_perm"); return true; }
                
                p.getInventory().addItem(createAdminScepter());
                p.sendMessage(ChatColor.RED + "⚡ You have received the Sentinel's Scepter.");
                p.sendMessage(ChatColor.GRAY + "Use this to create Server Zones instantly.");
                plugin.effects().playClaimSuccess(p);
                break;

            // --- CLAIM BLOCKS MANAGEMENT ---
            case "blocks":
                handleBlocks(sender, args);
                break;

            // --- MIGRATION ---
            case "migrate":
                if (!p.hasPermission("aegis.admin.migrate")) { 
                    plugin.msg().send(p, "no_perm"); 
                    return true; 
                }
                handleMigrate(sender, args);
                break;

            default:
                p.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /agadmin <reload|bypass|menu|convert|wand|blocks|migrate>");
        }
        return true;
    }

    /**
     * Logic for /agadmin blocks <give|take|set> <player> <amount>
     */
    private void handleBlocks(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /agadmin blocks <give|take|set> <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[2] + "' not found (must be online).");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return;
        }

        ClaimBlockData data = plugin.getClaimBlockManager().getOrCreate(target.getUniqueId());
        String mode = args[1].toLowerCase();

        switch (mode) {
            case "give":
            case "add":
                data.addBonusBlocks(amount);
                sender.sendMessage(ChatColor.GREEN + "✔ Added " + amount + " bonus blocks to " + target.getName());
                break;
            case "take":
            case "remove":
                data.addBonusBlocks(-amount);
                sender.sendMessage(ChatColor.YELLOW + "✔ Removed " + amount + " bonus blocks from " + target.getName());
                break;
            case "set":
                data.setBonusBlocks(amount);
                sender.sendMessage(ChatColor.GOLD + "✔ Set " + target.getName() + "'s bonus blocks to " + amount);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Invalid action. Use: give, take, set");
                return;
        }

        // Save immediately so it persists even if server crashes
        plugin.getClaimBlockManager().saveAsync();
    }

    /**
     * Logic for /agadmin migrate <list|preview|import|help> [source] [options]
     */
    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMigrateHelp(sender);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "list" -> handleMigrateList(sender);
            case "preview" -> handleMigratePreview(sender, args);
            case "import" -> handleMigrateImport(sender, args);
            case "help" -> sendMigrateHelp(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown migrate action: " + action);
                sendMigrateHelp(sender);
            }
        }
    }

    private void handleMigrateList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "    Available Migration Sources");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        List<SourcePlugin> available = migrationManager.getAvailableSources();

        if (available.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No compatible plugin data detected.");
            sender.sendMessage(ChatColor.GRAY + "Supported plugins:");
            sender.sendMessage(ChatColor.WHITE + "  • GriefPrevention (gp)");
            sender.sendMessage(ChatColor.WHITE + "  • GriefDefender (gd)");
            sender.sendMessage(ChatColor.WHITE + "  • Lands");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Detected data from:");
            for (SourcePlugin source : available) {
                sender.sendMessage(ChatColor.WHITE + "  • " + source.getDisplayName());
            }
        }

        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.GRAY + "Use: /agadmin migrate preview <source>");
    }

    private void handleMigratePreview(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /agadmin migrate preview <griefprevention|griefdefender|lands>");
            return;
        }

        SourcePlugin source = SourcePlugin.fromString(args[2]);
        if (source == null) {
            sender.sendMessage(ChatColor.RED + "Unknown source plugin: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "Valid options: griefprevention (gp), griefdefender (gd), lands");
            return;
        }

        if (migrationManager.isMigrationInProgress()) {
            sender.sendMessage(ChatColor.RED + "A migration is already in progress. Please wait.");
            return;
        }

        // Parse options
        MigrationOptions options = parseOptions(args, 3);
        options.dryRun = true;

        sender.sendMessage(ChatColor.YELLOW + "Starting preview scan for " + source.getDisplayName() + "...");

        migrationManager.previewMigration(sender, source, options)
                .exceptionally(ex -> {
                    sender.sendMessage(ChatColor.RED + "Preview failed: " + ex.getMessage());
                    plugin.getLogger().warning("Migration preview error: " + ex.getMessage());
                    return null;
                });
    }

    private void handleMigrateImport(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /agadmin migrate import <griefprevention|griefdefender|lands> [options]");
            sender.sendMessage(ChatColor.GRAY + "Options:");
            sender.sendMessage(ChatColor.GRAY + "  --force      Import even if overlapping");
            sender.sendMessage(ChatColor.GRAY + "  --no-trusted Skip importing trusted players");
            sender.sendMessage(ChatColor.GRAY + "  --no-flags   Skip importing flag settings");
            sender.sendMessage(ChatColor.GRAY + "  --world=name Only import from specific world");
            return;
        }

        SourcePlugin source = SourcePlugin.fromString(args[2]);
        if (source == null) {
            sender.sendMessage(ChatColor.RED + "Unknown source plugin: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "Valid options: griefprevention (gp), griefdefender (gd), lands");
            return;
        }

        if (migrationManager.isMigrationInProgress()) {
            sender.sendMessage(ChatColor.RED + "A migration is already in progress. Please wait.");
            return;
        }

        // Check for confirmation
        boolean confirmed = false;
        for (int i = 3; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--confirm") || args[i].equalsIgnoreCase("-y")) {
                confirmed = true;
                break;
            }
        }

        if (!confirmed) {
            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            sender.sendMessage(ChatColor.YELLOW + "         ⚠ Migration Warning ⚠");
            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            sender.sendMessage(ChatColor.WHITE + "You are about to import claims from:");
            sender.sendMessage(ChatColor.AQUA + "  " + source.getDisplayName());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "This action will:");
            sender.sendMessage(ChatColor.WHITE + "  • Create new AegisGuard claims");
            sender.sendMessage(ChatColor.WHITE + "  • Import owner and trusted players");
            sender.sendMessage(ChatColor.WHITE + "  • Skip overlapping areas (unless --force)");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "Run the command again with --confirm to proceed:");
            sender.sendMessage(ChatColor.GRAY + "/agadmin migrate import " + args[2] + " --confirm");
            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            return;
        }

        // Parse options
        MigrationOptions options = parseOptions(args, 3);

        sender.sendMessage(ChatColor.GREEN + "Starting import from " + source.getDisplayName() + "...");
        sender.sendMessage(ChatColor.GRAY + "This may take a while for large datasets.");

        migrationManager.startMigration(sender, source, options)
                .thenAccept(result -> {
                    if (result.imported > 0) {
                        sender.sendMessage(ChatColor.GREEN + "✔ Migration complete! " + result.imported + " claims imported.");
                        
                        // Force save
                        plugin.store().setDirty(true);
                        plugin.runGlobalAsync(() -> plugin.store().save());
                    }
                })
                .exceptionally(ex -> {
                    sender.sendMessage(ChatColor.RED + "Migration failed: " + ex.getMessage());
                    plugin.getLogger().severe("Migration error: " + ex.getMessage());
                    return null;
                });
    }

    private MigrationOptions parseOptions(String[] args, int startIndex) {
        MigrationOptions options = new MigrationOptions();

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);

            if (arg.equals("--dry-run")) {
                options.dryRun = true;
            } else if (arg.equals("--force")) {
                options.forceOverlap = true;
            } else if (arg.equals("--no-trusted")) {
                options.importTrusted = false;
            } else if (arg.equals("--no-flags")) {
                options.importFlags = false;
            } else if (arg.startsWith("--world=")) {
                options.worldFilter = arg.substring(8);
            } else if (arg.startsWith("--owner=")) {
                try {
                    options.ownerFilter = UUID.fromString(arg.substring(8));
                } catch (IllegalArgumentException e) {
                    // Try player name
                    Player p = Bukkit.getPlayer(arg.substring(8));
                    if (p != null) {
                        options.ownerFilter = p.getUniqueId();
                    }
                }
            }
        }

        return options;
    }

    private void sendMigrateHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "       AegisGuard Migration Tool");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.WHITE + "Import claims from other protection plugins");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Commands:");
        sender.sendMessage(ChatColor.WHITE + "  /agadmin migrate list");
        sender.sendMessage(ChatColor.GRAY + "    Show detected migration sources");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "  /agadmin migrate preview <source>");
        sender.sendMessage(ChatColor.GRAY + "    Preview what would be imported");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "  /agadmin migrate import <source> [options]");
        sender.sendMessage(ChatColor.GRAY + "    Actually import claims");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Supported Sources:");
        sender.sendMessage(ChatColor.WHITE + "  griefprevention (gp) - GriefPrevention");
        sender.sendMessage(ChatColor.WHITE + "  griefdefender (gd)   - GriefDefender");
        sender.sendMessage(ChatColor.WHITE + "  lands                - Lands");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Options:");
        sender.sendMessage(ChatColor.WHITE + "  --force      " + ChatColor.GRAY + "Import overlapping claims");
        sender.sendMessage(ChatColor.WHITE + "  --no-trusted " + ChatColor.GRAY + "Skip trusted players");
        sender.sendMessage(ChatColor.WHITE + "  --no-flags   " + ChatColor.GRAY + "Skip flag settings");
        sender.sendMessage(ChatColor.WHITE + "  --world=name " + ChatColor.GRAY + "Filter by world");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
    }

    private ItemStack createAdminScepter() {
        Material mat = plugin.cfg().getAdminWandMaterial();
        if (mat == null) mat = Material.BLAZE_ROD;
        
        ItemStack rod = new ItemStack(mat);
        ItemMeta meta = rod.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(plugin.cfg().getAdminWandName());
            meta.setLore(plugin.cfg().getAdminWandLore());
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            
            // KEY: This NBT tag tells SelectionService that this is a SERVER claim tool
            meta.getPersistentDataContainer().set(SelectionService.SERVER_WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            
            rod.setItemMeta(meta);
        }
        return rod;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), completions);
            Collections.sort(completions);
            return completions;
        }

        // Tab complete for "blocks"
        if (args.length == 2 && args[0].equalsIgnoreCase("blocks")) {
            List<String> sub = Arrays.asList("give", "take", "set");
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], sub, completions);
            return completions;
        }

        // Tab complete player names for blocks command
        if (args.length == 3 && args[0].equalsIgnoreCase("blocks")) {
            return null; // Bukkit default player list
        }

        // Tab complete for "migrate"
        if (args[0].equalsIgnoreCase("migrate")) {
            if (args.length == 2) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], Arrays.asList(MIGRATE_ACTIONS), completions);
                Collections.sort(completions);
                return completions;
            }

            if (args.length == 3) {
                String action = args[1].toLowerCase();
                if (action.equals("preview") || action.equals("import")) {
                    List<String> completions = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[2], Arrays.asList(MIGRATE_SOURCES), completions);
                    Collections.sort(completions);
                    return completions;
                }
            }

            // Options for import
            if (args.length >= 4 && args[1].equalsIgnoreCase("import")) {
                List<String> completions = new ArrayList<>();
                List<String> options = new ArrayList<>(Arrays.asList(MIGRATE_OPTIONS));
                options.add("--confirm");

                // Filter out already used options
                for (int i = 3; i < args.length - 1; i++) {
                    options.remove(args[i]);
                }

                // Add world names for --world=
                if (args[args.length - 1].startsWith("--world=")) {
                    String partial = args[args.length - 1].substring(8);
                    return Bukkit.getWorlds().stream()
                            .map(w -> "--world=" + w.getName())
                            .filter(s -> s.toLowerCase().startsWith(("--world=" + partial).toLowerCase()))
                            .collect(Collectors.toList());
                }

                StringUtil.copyPartialMatches(args[args.length - 1], options, completions);
                return completions;
            }
        }

        return null;
    }
}
