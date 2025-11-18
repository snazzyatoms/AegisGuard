package com.aegisguard;

import com.aegisguard.data.Plot;
import com.aegisguard.selection.SelectionService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles all player-facing /aegis commands.
 * --- UPGRADE NOTES ---
 * - This is the "Ultimate" version.
 * - Added: resize, setspawn, home, welcome, farewell, sell, unsell, market, auction
 * - Removed: sound (now in its own file)
 */
public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    // --- MODIFIED ---
    private static final String[] SUB_COMMANDS = {
        "wand", "menu", "claim", "unclaim", "resize", "help",
        "setspawn", "home", "welcome", "farewell",
        "sell", "unsell", "rent", "unrent", "market",
        "auction"
    };
    // Admin list just adds "sound"
    private static final String[] ADMIN_SUB_COMMANDS = {
        "wand", "menu", "claim", "unclaim", "resize", "help",
        "setspawn", "home", "welcome", "farewell",
        "sell", "unsell", "rent", "unrent", "market",
        "auction", "sound"
    };
    private static final String[] RESIZE_DIRECTIONS = { "north", "south", "east", "west" };

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "players_only");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().openMain(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "wand": {
                p.getInventory().addItem(createScepter());
                plugin.msg().send(p, "wand_given");
                break;
            }
            case "menu": {
                plugin.gui().openMain(p);
                break;
            }
            case "claim": {
                plugin.selection().confirmClaim(p);
                break;
            }
            case "unclaim": {
                plugin.selection().unclaimHere(p);
                break;
            }
            
            case "resize": {
                if (args.length < 3) {
                    sendMsg(p, "&cUsage: /aegis resize <direction> <amount>");
                    sendMsg(p, "&eDirections: &f" + String.join(", ", RESIZE_DIRECTIONS));
                    return true;
                }
                String direction = args[1].toLowerCase();
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sendMsg(p, "&cAmount must be a number.");
                    return true;
                }
                
                if (amount <= 0) {
                    sendMsg(p, "&cAmount must be positive.");
                    return true;
                }

                if (!Arrays.asList(RESIZE_DIRECTIONS).contains(direction)) {
                    sendMsg(p, "&cInvalid direction. Use: &f" + String.join(", ", RESIZE_DIRECTIONS));
                    return true;
                }

                // Let SelectionService handle the logic
                plugin.selection().resizePlot(p, direction, amount);
                break;
            }

            /* -----------------------------
             * Welcome/TP Commands
             * ----------------------------- */
            case "setspawn": {
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                
                // Check if spawn is inside the plot
                if (!plot.isInside(p.getLocation())) {
                    plugin.msg().send(p, "home-fail-outside");
                    plugin.effects().playError(p);
                    return true;
                }

                plot.setSpawnLocation(p.getLocation());
                plugin.store().setDirty(true); // Mark for async save
                plugin.msg().send(p, "home-set-success");
                plugin.effects().playConfirm(p);
                break;
            }
            case "home": {
                // For now, only owners can use /home. We can add a flag later.
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                
                if (plot.getSpawnLocation() == null) {
                    plugin.msg().send(p, "home-fail-no-spawn");
                    plugin.effects().playError(p);
                    return true;
                }
                
                p.teleport(plot.getSpawnLocation());
                plugin.effects().playConfirm(p); // Use a teleport sound effect
                break;
            }
            case "welcome": {
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                
                if (args.length < 2) {
                    plot.setWelcomeMessage(null); // Clear the message
                    plugin.store().setDirty(true);
                    plugin.msg().send(p, "welcome-cleared");
                    plugin.effects().playMenuFlip(p);
                    return true;
                }
                
                String msg = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
                plot.setWelcomeMessage(msg);
                plugin.store().setDirty(true);
                plugin.msg().send(p, "welcome-set");
                plugin.effects().playMenuFlip(p);
                break;
            }
            case "farewell": {
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                
                if (args.length < 2) {
                    plot.setFarewellMessage(null); // Clear the message
                    plugin.store().setDirty(true);
                    plugin.msg().send(p, "farewell-cleared");
                    plugin.effects().playMenuFlip(p);
                    return true;
                }
                
                String msg = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
                plot.setFarewellMessage(msg);
                plugin.store().setDirty(true);
                plugin.msg().send(p, "farewell-set");
                plugin.effects().playMenuFlip(p);
                break;
            }
            
            /* -----------------------------
             * Marketplace Commands
             * ----------------------------- */
            case "market": {
                plugin.gui().market().open(p, 0); // Open page 0
                break;
            }
            case "sell": {
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                if (args.length < 2) {
                    sendMsg(p, "&cUsage: /ag sell <price>");
                    return true;
                }
                
                try {
                    double price = Double.parseDouble(args[1]);
                    if (price <= 0) {
                        sendMsg(p, "&cPrice must be greater than 0.");
                        return true;
                    }
                    plot.setForSale(true, price);
                    plugin.store().setDirty(true);
                    plugin.msg().send(p, "market-for-sale", Map.of("PRICE", plugin.vault().format(price)));
                    plugin.effects().playConfirm(p);
                } catch (NumberFormatException e) {
                    sendMsg(p, "&cPrice must be a valid number.");
                }
                break;
            }
            case "unsell": {
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                plot.setForSale(false, 0);
                plugin.store().setDirty(true);
                plugin.msg().send(p, "market-not-for-sale");
                plugin.effects().playMenuFlip(p);
                break;
            }
            // "rent" and "unrent" are stubs for now, we'll implement them later.
            case "rent": {
                plugin.msg().send(p, "market-rent-soon");
                break;
            }
            case "unrent": {
                plugin.msg().send(p, "market-rent-soon");
                break;
            }
            
            case "auction": {
                plugin.gui().auction().open(p, 0);
                break;
            }

            
            case "sound": {
                // This command is now delegated to SoundCommand
                if (!p.hasPermission("aegis.admin")) {
                    plugin.msg().send(p, "no_perm");
                    return true;
                }
                // Pass all args *except* "sound"
                String[] soundArgs = Arrays.copyOfRange(args, 1, args.length);
                plugin.soundCommand().onCommand(p, cmd, label, soundArgs);
                break;
            }

            case "help": {
                sendHelp(p);
                break;
            }

            default:
                sendHelp(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            // Use different command lists based on permission
            List<String> commands = sender.hasPermission("aegis.admin") ?
                    Arrays.asList(ADMIN_SUB_COMMANDS) :
                    Arrays.asList(SUB_COMMANDS);

            StringUtil.copyPartialMatches(args[0], commands, completions);
            Collections.sort(completions);
            return completions;
        }

        // --- Tab Completion for Resize ---
        if (args.length == 2 && args[0].equalsIgnoreCase("resize")) {
            final List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], Arrays.asList(RESIZE_DIRECTIONS), completions);
            return completions;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resize")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10");
        }

        // --- Tab Completion for Sound (delegate) ---
        if (args.length >= 2 && args[0].equalsIgnoreCase("sound") && sender.hasPermission("aegis.admin")) {
            // Create a new args array with "sound" removed
            String[] soundArgs = Arrays.copyOfRange(args, 1, args.length);
            return plugin.soundCommand().onTabComplete(sender, command, alias, soundArgs);
        }
        
        // --- Welcome/Farewell Tab Complete ---
        if (args.length >= 2 && (args[0].equalsIgnoreCase("welcome") || args[0].equalsIgnoreCase("farewell"))) {
            return Arrays.asList("Your message here (allows &colors)");
        }
        
        // --- Sell Tab Complete ---
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Arrays.asList("1000", "5000", "10000");
        }

        return null; // No other completions
    }

    /**
     * Utility: Create Aegis Scepter
     * (Moved from main class)
     * Now adds a PersistentDataContainer tag to the wand.
     */
    private ItemStack createScepter() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            // We translate color codes here for consistency
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bAegis Scepter"));
            meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', "&7Right-click: Open Aegis Menu"),
                    ChatColor.translateAlternateColorCodes('&', "&7Left/Right-click: Select corners")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            // --- NEW ---
            // Add the persistent NBT tag so we can identify this item reliably
            meta.getPersistentDataContainer().set(SelectionService.WAND_KEY, PersistentDataType.BYTE, (byte) 1);

            rod.setItemMeta(meta);
        }
        return rod;
    }
    
    /**
     * Sends the localized help message to the player.
     */
    private void sendHelp(Player player) {
        sendMsg(player, plugin.msg().get(player, "help_header"));
        List<String> helpLines = plugin.msg().getList(player, "help_lines");
        for (String line : helpLines) {
            sendMsg(player, line);
        }
    }

    /**
     * Helper method to send a color-formatted message.
     */
    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
