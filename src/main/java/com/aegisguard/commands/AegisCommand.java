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

public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private static final String[] SUB_COMMANDS = { "wand", "menu", "claim", "unclaim", "resize", "help", "setspawn", "home", "welcome", "farewell", "sell", "unsell", "market", "auction" };
    private static final String[] RESIZE_DIRECTIONS = { "north", "south", "east", "west" };

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
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
            case "home":
                handleHome(p);
                break;
            case "setspawn":
                handleSetSpawn(p);
                break;
            case "sell":
                handleSell(p, args);
                break;
            case "market":
                plugin.gui().market().open(p, 0);
                break;
            case "auction":
                plugin.gui().auction().open(p, 0);
                break;
            case "help":
            default:
                sendHelp(p);
                break;
        }
        return true;
    }

    private void handleResize(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage("§cUsage: /ag resize <direction> <amount>");
            return;
        }
        plugin.selection().resizePlot(p, args[1], Integer.parseInt(args[2]));
    }

    private void handleHome(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot != null && plot.getSpawnLocation() != null) {
            p.teleport(plot.getSpawnLocation());
            plugin.effects().playConfirm(p);
        } else {
            plugin.msg().send(p, "home-fail-no-spawn");
        }
    }

    private void handleSetSpawn(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot != null && plot.getOwner().equals(p.getUniqueId())) {
            plot.setSpawnLocation(p.getLocation());
            plugin.store().setDirty(true);
            plugin.msg().send(p, "home-set-success");
        } else {
            plugin.msg().send(p, "no_plot_here");
        }
    }

    private void handleSell(Player p, String[] args) {
        if (args.length < 2) return;
        try {
            double price = Double.parseDouble(args[1]);
            Plot plot = plugin.store().getPlotAt(p.getLocation());
            if (plot != null && plot.getOwner().equals(p.getUniqueId())) {
                plot.setForSale(true, price);
                plugin.store().setDirty(true);
                plugin.msg().send(p, "market-for-sale");
            }
        } catch (NumberFormatException ignored) {}
    }

    private ItemStack createScepter() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bAegis Scepter"));
            meta.setLore(Arrays.asList("§7Right-click to open menu", "§7Left/Right click to select"));
            meta.getPersistentDataContainer().set(SelectionService.WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            rod.setItemMeta(meta);
        }
        return rod;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§bAegisGuard Help:");
        p.sendMessage("§7/ag claim, /ag wand, /ag menu, /ag resize");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), new ArrayList<>());
        }
        return null;
    }
}
