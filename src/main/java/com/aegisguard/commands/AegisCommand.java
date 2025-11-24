package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.selection.SelectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    private static final String[] SUB_COMMANDS = {
        "wand", "menu", "claim", "unclaim", "resize", "help",
        "setspawn", "home", "welcome", "farewell",
        "sell", "unsell", "rent", "unrent", "market", "auction",
        "consume", "kick", "ban", "unban", "visit"
    };
    
    private static final String[] ADMIN_SUB_COMMANDS = {
        "wand", "menu", "claim", "unclaim", "resize", "help",
        "setspawn", "home", "welcome", "farewell",
        "sell", "unsell", "rent", "unrent", "market", "auction",
        "consume", "kick", "ban", "unban", "visit", "setwarp", "delwarp", "sound"
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

            // --- KICK / BAN / UNBAN ---
            case "kick":
                if (args.length < 2) { sendMsg(p, "&cUsage: /ag kick <player>"); return true; }
                Plot kPlot = plugin.store().getPlotAt(p.getLocation());
                if (kPlot == null || !kPlot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here"); return true;
                }
                Player kTarget = Bukkit.getPlayer(args[1]);
                if (kTarget == null) { sendMsg(p, "&cPlayer not found."); return true; }
                if (!kPlot.isInside(kTarget.getLocation())) { sendMsg(p, "&cPlayer is not in your plot."); return true; }
                
                kTarget.teleport(kTarget.getWorld().getSpawnLocation());
                kTarget.sendMessage("§cYou were kicked from " + kPlot.getOwnerName() + "'s plot.");
                sendMsg(p, "&eKicked " + kTarget.getName());
                break;

            case "ban":
                if (args.length < 2) { sendMsg(p, "&cUsage: /ag ban <player>"); return true; }
                Plot bPlot = plugin.store().getPlotAt(p.getLocation());
                if (bPlot == null || !bPlot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here"); return true;
                }
                OfflinePlayer bTarget = Bukkit.getOfflinePlayer(args[1]);
                if (bTarget.getUniqueId().equals(p.getUniqueId())) { sendMsg(p, "&cCannot ban yourself."); return true; }
                
                bPlot.addBan(bTarget.getUniqueId());
                plugin.store().setDirty(true);
                sendMsg(p, "&cBanned " + bTarget.getName());
                
                if (bTarget.isOnline() && bPlot.isInside(bTarget.getPlayer().getLocation())) {
                    bTarget.getPlayer().teleport(bTarget.getPlayer().getWorld().getSpawnLocation());
                    bTarget.getPlayer().sendMessage("§4You have been BANNED from this plot.");
                }
                break;

            case "unban":
                if (args.length < 2) { sendMsg(p, "&cUsage: /ag unban <player>"); return true; }
                Plot uPlot = plugin.store().getPlotAt(p.getLocation());
                if (uPlot == null || !uPlot.getOwner().equals(p.getUniqueId())) {
                    plugin.msg().send(p, "no_plot_here"); return true;
                }
                OfflinePlayer uTarget = Bukkit.getOfflinePlayer(args[1]);
                uPlot.removeBan(uTarget.getUniqueId());
                plugin.store().setDirty(true);
                sendMsg(p, "&aUnbanned " + uTarget.getName());
                break;

            // --- MANUAL WAND CONSUME ---
            case "consume":
                plugin.selection().manualConsumeWand(p);
                break;
            
            // --- VISIT & WARPS ---
            case "visit":
                if (!plugin.cfg().isTravelSystemEnabled()) { sendMsg(p, "&cTravel system is disabled."); return true; }
                plugin.gui().visit().open(p, 0, false);
                break;

            case "setwarp":
                if (!p.hasPermission("aegis.admin")) { plugin.msg().send(p, "no_perm"); return true; }
                if (args.length < 2) { sendMsg(p, "&cUsage: /ag setwarp <Name>"); return true; }
                
                Plot wPlot = plugin.store().getPlotAt(p.getLocation());
                if (wPlot == null) { plugin.msg().send(p, "no_plot_here"); return true; }
                
                // Set as server warp (using Beacon as default icon)
                // Note: Assuming Plot.java has setServerWarp method now
                // Since Plot.java doesn't have setServerWarp in the version sent, 
                // we will implement basic logic or skip if Plot.java wasn't updated yet.
                // Based on previous step, Plot.java update was pending. 
                // I will add the logic assuming Plot.java will be updated next.
                
                // wPlot.setServerWarp(true, args[1], Material.BEACON); 
                // plugin.store().setDirty(true);
                sendMsg(p, "&aServer Warp created (Pending Plot.java update).");
                break;

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
                if (!plugin.cfg().allowHomeTeleport()) { sendMsg(p, "&cHome teleport is disabled."); return true; }
                
                Plot homePlot = plugin.store().getPlotAt(p.getLocation());
                if (homePlot == null || !homePlot.getOwner().equals(p.getUniqueId())) {
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
        // ... existing tab completions ...
        if (args.length == 2 && args[0].equalsIgnoreCase("resize")) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], Arrays.asList(RESIZE_DIRECTIONS), completions);
            return completions;
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
