package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.selection.SelectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
        "consume", "kick", "ban", "unban", "visit", 
        "level", "zone", "like",
        "rename", "stuck", "setdesc" // --- NEW v1.1.1 ---
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
                handleResize(p, args);
                break;

            // --- MODERATION ---
            case "kick":
                handleKick(p, args);
                break;

            case "ban":
                handleBan(p, args);
                break;

            case "unban":
                handleUnban(p, args);
                break;

            // --- UTILITY ---
            case "consume":
                plugin.selection().manualConsumeWand(p);
                break;
            
            case "visit":
                if (!plugin.cfg().isTravelSystemEnabled()) { sendMsg(p, "&cTravel system is disabled."); return true; }
                plugin.gui().visit().open(p, 0, false);
                break;

            case "setspawn":
                handleSetSpawn(p);
                break;
            
            case "home":
                handleHome(p);
                break;
            
            case "welcome":
                handleWelcomeFarewell(p, args, true);
                break;
            
            case "farewell":
                handleWelcomeFarewell(p, args, false);
                break;
            
            // --- ECONOMY ---
            case "market":
                plugin.gui().market().open(p, 0);
                break;
            
            case "sell":
                handleSell(p, args);
                break;
            
            case "unsell":
                handleUnsell(p);
                break;
            
            case "auction":
                plugin.gui().auction().open(p, 0);
                break;

            // --- v1.1.0 SHORTCUTS ---
            case "level":
                openLevelMenu(p);
                break;
                
            case "zone":
                openZoneMenu(p);
                break;
                
            case "like":
                handleLike(p);
                break;

            // --- v1.1.1 IDENTITY & UTILITY ---
            case "rename":
                handleRename(p, args);
                break;

            case "setdesc":
                handleSetDesc(p, args);
                break;

            case "stuck":
                handleStuck(p);
                break;

            case "help":
            default:
                sendHelp(p);
        }
        return true;
    }

    // --- HANDLERS ---
    
    private void handleRename(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }
        if (!plot.getOwner().equals(p.getUniqueId()) && !plugin.isAdmin(p)) {
            plugin.msg().send(p, "no_perm");
            return;
        }
        
        if (args.length < 2) {
            sendMsg(p, "&cUsage: /ag rename <Name>");
            return;
        }
        
        String name = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        name = ChatColor.translateAlternateColorCodes('&', name);
        
        plot.setEntryTitle(name);
        plugin.store().setDirty(true);
        
        sendMsg(p, "&a✔ Plot renamed to: &r" + name);
        plugin.effects().playConfirm(p);
    }

    private void handleSetDesc(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }
        if (!plot.getOwner().equals(p.getUniqueId()) && !plugin.isAdmin(p)) {
            plugin.msg().send(p, "no_perm");
            return;
        }
        
        if (args.length < 2) {
            sendMsg(p, "&cUsage: /ag setdesc <Description>");
            return;
        }
        
        String desc = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        desc = ChatColor.translateAlternateColorCodes('&', desc);
        
        plot.setDescription(desc);
        plugin.store().setDirty(true);
        
        sendMsg(p, "&a✔ Plot description updated.");
        plugin.effects().playConfirm(p);
    }

    private void handleStuck(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendMsg(p, "&cYou are not inside a plot.");
            return;
        }
        
        // Find nearest safe spot outside
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
        
        // Get safe Y
        World world = loc.getWorld();
        int safeY = world.getHighestBlockYAt(target);
        target.setY(safeY + 1);
        
        p.teleport(target);
        sendMsg(p, "&e✨ You have been moved to safety.");
        plugin.effects().playTeleport(p);
    }

    private void handleResize(Player p, String[] args) {
        if (args.length < 3) {
            sendMsg(p, "&cUsage: /aegis resize <direction> <amount>");
            sendMsg(p, "&eDirections: &f" + String.join(", ", RESIZE_DIRECTIONS));
            return;
        }
        String direction = args[1].toLowerCase();
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendMsg(p, "&cAmount must be a number.");
            return;
        }
        
        if (amount <= 0) {
            sendMsg(p, "&cAmount must be positive.");
            return;
        }

        if (!Arrays.asList(RESIZE_DIRECTIONS).contains(direction)) {
            sendMsg(p, "&cInvalid direction. Use: &f" + String.join(", ", RESIZE_DIRECTIONS));
            return;
        }

        plugin.selection().resizePlot(p, direction, amount);
    }

    private void handleKick(Player p, String[] args) {
        if (args.length < 2) { sendMsg(p, "&cUsage: /ag kick <player>"); return; }
        Plot kPlot = plugin.store().getPlotAt(p.getLocation());
        if (kPlot == null || !kPlot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here"); return;
        }
        Player kTarget = Bukkit.getPlayer(args[1]);
        if (kTarget == null) { sendMsg(p, "&cPlayer not found."); return; }
        if (!kPlot.isInside(kTarget.getLocation())) { sendMsg(p, "&cPlayer is not in your plot."); return; }
        
        kTarget.teleport(kTarget.getWorld().getSpawnLocation());
        kTarget.sendMessage("§cYou were kicked from " + kPlot.getOwnerName() + "'s plot.");
        sendMsg(p, "&eKicked " + kTarget.getName());
    }

    private void handleBan(Player p, String[] args) {
        if (args.length < 2) { sendMsg(p, "&cUsage: /ag ban <player>"); return; }
        Plot bPlot = plugin.store().getPlotAt(p.getLocation());
        if (bPlot == null || !bPlot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here"); return;
        }
        OfflinePlayer bTarget = Bukkit.getOfflinePlayer(args[1]);
        if (bTarget.getUniqueId().equals(p.getUniqueId())) { sendMsg(p, "&cCannot ban yourself."); return; }
        
        bPlot.addBan(bTarget.getUniqueId());
        plugin.store().setDirty(true);
        sendMsg(p, "&cBanned " + bTarget.getName());
        
        if (bTarget.isOnline() && bPlot.isInside(bTarget.getPlayer().getLocation())) {
            bTarget.getPlayer().teleport(bTarget.getPlayer().getWorld().getSpawnLocation());
            bTarget.getPlayer().sendMessage("§4You have been BANNED from this plot.");
        }
    }

    private void handleUnban(Player p, String[] args) {
        if (args.length < 2) { sendMsg(p, "&cUsage: /ag unban <player>"); return; }
        Plot uPlot = plugin.store().getPlotAt(p.getLocation());
        if (uPlot == null || !uPlot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here"); return;
        }
        OfflinePlayer uTarget = Bukkit.getOfflinePlayer(args[1]);
        uPlot.removeBan(uTarget.getUniqueId());
        plugin.store().setDirty(true);
        sendMsg(p, "&aUnbanned " + uTarget.getName());
    }

    private void handleSetSpawn(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        if (!plot.isInside(p.getLocation())) {
            plugin.msg().send(p, "home-fail-outside");
            plugin.effects().playError(p);
            return;
        }
        plot.setSpawnLocation(p.getLocation());
        plugin.store().setDirty(true);
        plugin.msg().send(p, "home-set-success");
        plugin.effects().playConfirm(p);
    }

    private void handleHome(Player p) {
        if (!plugin.cfg().allowHomeTeleport()) { sendMsg(p, "&cHome teleport is disabled."); return; }
        Plot homePlot = plugin.store().getPlotAt(p.getLocation());
        if (homePlot == null || !homePlot.getOwner().equals(p.getUniqueId())) {
            List<Plot> plots = plugin.store().getPlots(p.getUniqueId());
            if (plots != null && !plots.isEmpty()) homePlot = plots.get(0);
            else {
                plugin.msg().send(p, "no_plot_here");
                plugin.effects().playError(p);
                return;
            }
        }
        if (homePlot.getSpawnLocation() == null) {
            plugin.msg().send(p, "home-fail-no-spawn");
            plugin.effects().playError(p);
            return;
        }
        p.teleport(homePlot.getSpawnLocation());
        plugin.effects().playConfirm(p);
    }

    private void handleWelcomeFarewell(Player p, String[] args, boolean isWelcome) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }
        if (args.length < 2) {
            if (isWelcome) plot.setWelcomeMessage(null);
            else plot.setFarewellMessage(null);
            plugin.store().setDirty(true);
            plugin.msg().send(p, isWelcome ? "welcome-cleared" : "farewell-cleared");
            return;
        }
        String msg = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (isWelcome) plot.setWelcomeMessage(msg);
        else plot.setFarewellMessage(msg);
        plugin.store().setDirty(true);
        plugin.msg().send(p, isWelcome ? "welcome-set" : "farewell-set");
    }

    private void handleSell(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }
        if (args.length < 2) {
            sendMsg(p, "&cUsage: /ag sell <price>");
            return;
        }
        try {
            double price = Double.parseDouble(args[1]);
            if (price <= 0) { sendMsg(p, "&cPrice must be positive."); return; }
            plot.setForSale(true, price);
            plugin.store().setDirty(true);
            plugin.msg().send(p, "market-for-sale", Map.of("PRICE", plugin.vault().format(price)));
        } catch (NumberFormatException e) { sendMsg(p, "&cInvalid number."); }
    }

    private void handleUnsell(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            return;
        }
        plot.setForSale(false, 0);
        plugin.store().setDirty(true);
        plugin.msg().send(p, "market-not-for-sale");
    }
    
    private void openLevelMenu(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here"); return;
        }
        if (plugin.cfg().isLevelingEnabled()) plugin.gui().leveling().open(p, plot);
        else sendMsg(p, "&cLeveling is disabled.");
    }
    
    private void openZoneMenu(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here"); return;
        }
        if (plugin.cfg().isZoningEnabled()) plugin.gui().zoning().open(p, plot);
        else sendMsg(p, "&cZoning is disabled.");
    }
    
    private void handleLike(Player p) {
        if (!plugin.cfg().isLikesEnabled()) { plugin.msg().send(p, "like_disabled"); return; }
        
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) { plugin.msg().send(p, "no_plot_here"); return; }
        
        if (plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "like_own_plot");
            return;
        }
        
        if (plugin.cfg().oneLikePerPlayer() && plot.hasLiked(p.getUniqueId())) {
             plot.toggleLike(p.getUniqueId()); // Toggle OFF
             plugin.msg().send(p, "like_removed");
        } else {
             plot.toggleLike(p.getUniqueId()); // Toggle ON
             plugin.msg().send(p, "like_success", Map.of("AMOUNT", String.valueOf(plot.getLikes())));
             plugin.effects().playConfirm(p);
        }
        plugin.store().setDirty(true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), completions);
            Collections.sort(completions);
            return completions;
        }
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
            for (String line : helpLines) sendMsg(player, line);
        }
    }

    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
