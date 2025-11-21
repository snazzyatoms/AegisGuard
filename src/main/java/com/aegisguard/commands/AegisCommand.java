package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.selection.SelectionService;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles all player-facing /aegis commands.
 */
public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    private static final String[] SUB_COMMANDS = {
        "wand", "menu", "claim", "unclaim", "resize", "help",
        "setspawn", "home", "welcome", "farewell",
        "sell", "unsell", "rent", "unrent", "market",
        "auction"
    };
    
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
            case "wand":
                p.getInventory().addItem(createScepter());
                plugin.msg().send(p, "wand_given");
                break;
            
            case "menu":
                plugin.gui().openMain(p);
                break;
            
            case "claim":
                plugin.selection().confirmClaim(p);
                break;
            
            case "unclaim":
                plugin.selection().unclaimHere(p);
                break;
            
            case "resize":
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

                plugin.selection().resizePlot(p, direction, amount);
                break;
            
            /* -----------------------------
             * Welcome/TP Commands
             * ----------------------------- */
            case "setspawn":
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here");
                    plugin.effects().playError(p);
                    return true;
                }
                
                if (!plot.isInside(p.getLocation())) {
                    plugin.msg().send(p, "home-fail-outside");
                    plugin.effects().playError(p);
                    return true;
                }

                plot.setSpawnLocation(p.getLocation());
                plugin.store().setDirty(true);
                plugin.msg().send(p, "home-set-success");
                plugin.effects().playConfirm(p);
                break;
            
            case "home":
                Plot homePlot = plugin.store().getPlotAt(p.getLocation());
                // Logic: This probably should find the player's home, not the plot they are standing on?
                // Assuming current implementation intends to TP to spawn of current plot if owned
                if (homePlot == null || !homePlot.getOwner().equals(p.getUniqueId())) {
                    // Fallback: Try to find first plot owned by player
                    List<Plot> plots = plugin.store().getPlots(p.getUniqueId());
                    if (plots != null && !plots.isEmpty()) {
                        homePlot = plots.get(0);
                    } else {
                        plugin.msg().send(p, "no_plot_here");
                        plugin.effects().playError(p);
                        return true;
                    }
                }
                
                if (homePlot.getSpawnLocation() == null) {
                    plugin.msg().send(p, "home-fail-no-spawn");
                    plugin.effects().playError(p);
                    return true;
                }
                
                p.teleport(homePlot.getSpawnLocation());
                plugin.effects().playConfirm(p);
                break;
            
            case "welcome":
                handleWelcomeFarewell(p, args, true);
                break;
            
            case "farewell":
                handleWelcomeFarewell(p, args, false);
                break;
            
            /* -----------------------------
             * Marketplace Commands
             * ----------------------------- */
            case "market":
                plugin.gui().market().open(p, 0);
                break;
            
            case "sell":
                handleSell(p, args);
                break;
            
            case "unsell":
                handleUnsell(p);
                break;
            
            case "rent":
            case "unrent":
                plugin.msg().send(p, "market-rent-soon");
                break;
            
            case "auction":
                plugin.gui().auction().open(p, 0);
                break;

            
            case "sound":
                if (!p.hasPermission("aegis.admin")) {
                    plugin.msg().send(p, "no_perm");
                    return true;
                }
                // FIX: Commented out missing method call to allow compilation.
                // If you have SoundCommand, ensure plugin.soundCommand() exists in AegisGuard.java
                // plugin.soundCommand().onCommand(p, cmd, label, Arrays.copyOfRange(args, 1, args.length));
                plugin.msg().send(p, "&cSound command logic is currently disabled/missing.");
                break;

            case "help":
            default:
                sendHelp(p);
        }
        return true;
    }

    private void handleWelcomeFarewell(Player p, String[] args, boolean isWelcome) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        
        if (args.length < 2) {
            if (isWelcome) plot.setWelcomeMessage(null);
            else plot.setFarewellMessage(null);
            
            plugin.store().setDirty(true);
            plugin.msg().send(p, isWelcome ? "welcome-cleared" : "farewell-cleared");
            plugin.effects().playMenuFlip(p);
            return;
        }
        
        String msg = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (isWelcome) plot.setWelcomeMessage(msg);
        else plot.setFarewellMessage(msg);
        
        plugin.store().setDirty(true);
        plugin.msg().send(p, isWelcome ? "welcome-set" : "farewell-set");
        plugin.effects().playMenuFlip(p);
    }

    private void handleSell(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        if (args.length < 2) {
            sendMsg(p, "&cUsage: /ag sell <price>");
            return;
        }
        
        try {
            double price = Double.parseDouble(args[1]);
            if (price <= 0) {
                sendMsg(p, "&cPrice must be greater than 0.");
                return;
            }
            plot.setForSale(true, price);
            plugin.store().setDirty(true);
            plugin.msg().send(p, "market-for-sale", Map.of("PRICE", plugin.vault().format(price)));
            plugin.effects().playConfirm(p);
        } catch (NumberFormatException e) {
            sendMsg(p, "&cPrice must be a valid number.");
        }
    }

    private void handleUnsell(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        plot.setForSale(false, 0);
        plugin.store().setDirty(true);
        plugin.msg().send(p, "market-not-for-sale");
        plugin.effects().playMenuFlip(p);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> commands = sender.hasPermission("aegis.admin") ?
                    Arrays.asList(ADMIN_SUB_COMMANDS) :
                    Arrays.asList(SUB_COMMANDS);

            StringUtil.copyPartialMatches(args[0], commands, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("resize")) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], Arrays.asList(RESIZE_DIRECTIONS), completions);
            return completions;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resize")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10");
        }

        if (args.length >= 2 && (args[0].equalsIgnoreCase("welcome") || args[0].equalsIgnoreCase("farewell"))) {
            return Arrays.asList("Your message here (allows &colors)");
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Arrays.asList("1000", "5000", "10000");
        }

        return null;
    }

    private ItemStack createScepter() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bAegis Scepter"));
            meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', "&7Right-click: Open Aegis Menu"),
                    ChatColor.translateAlternateColorCodes('&', "&7Left/Right-click: Select corners")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            
            meta.getPersistentDataContainer().set(SelectionService.WAND_KEY, PersistentDataType.BYTE, (byte) 1);

            rod.setItemMeta(meta);
        }
        return rod;
    }
    
    private void sendHelp(Player player) {
        sendMsg(player, plugin.msg().get(player, "help_header"));
        List<String> helpLines = plugin.msg().getList(player, "help_lines");
        if (helpLines != null) {
            for (String line : helpLines) {
                sendMsg(player, line);
            }
        }
    }

    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
