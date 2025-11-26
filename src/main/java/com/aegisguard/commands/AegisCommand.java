package com.aegisguard.admin;

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

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    
    private static final String[] SUB_COMMANDS = { "reload", "bypass", "menu", "convert", "wand", "setwarp", "delwarp" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Console support for reload
        if (!(sender instanceof Player p)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.cfg().reload();
                plugin.msg().reload();
                plugin.worldRules().reload();
                plugin.store().load();
                sender.sendMessage("[AegisGuard] Reload complete.");
            } else {
                sender.sendMessage("Only players can use GUI commands.");
            }
            return true;
        }

        if (!p.hasPermission("aegis.admin")) {
            plugin.msg().send(p, "no_perm");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().admin().open(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.cfg().reload();
                plugin.msg().reload();
                plugin.worldRules().reload();
                plugin.store().load();
                p.sendMessage(ChatColor.GREEN + "✔ [AegisGuard] Configuration reloaded.");
                plugin.effects().playConfirm(p);
                break;
                
            case "bypass":
                p.sendMessage(ChatColor.YELLOW + "⚠ Admin Bypass is active via permission 'aegis.admin.bypass'.");
                break;
                
            case "menu":
                plugin.gui().admin().open(p);
                break;
                
            // --- CONVERT PLOT TO SERVER ZONE ---
            case "convert":
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null) {
                    p.sendMessage(ChatColor.RED + "You must be standing in a plot to convert it.");
                    plugin.effects().playError(p);
                    return true;
                }
                // Transfer to Server UUID
                plugin.store().changePlotOwner(plot, Plot.SERVER_OWNER_UUID, "Server");
                // Set safe defaults for a server zone
                plot.setFlag("pvp", false);
                plot.setFlag("mobs", false);
                plot.setFlag("build", false); // Protected
                plot.setFlag("safe_zone", true);
                
                plugin.store().setDirty(true);
                p.sendMessage(ChatColor.GREEN + "✔ Plot converted to Server Zone.");
                plugin.effects().playConfirm(p);
                break;
                
            // --- SENTINEL'S SCEPTER (Admin Wand) ---
            case "wand":
                p.getInventory().addItem(createAdminScepter());
                p.sendMessage(ChatColor.RED + "⚡ You have received the Sentinel's Scepter.");
                p.sendMessage(ChatColor.GRAY + "Use this to claim Server Zones directly.");
                plugin.effects().playClaimSuccess(p);
                break;
                
            // --- SERVER WARPS ---
            case "setwarp":
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /agadmin setwarp <Name>");
                    return true;
                }
                Plot wPlot = plugin.store().getPlotAt(p.getLocation());
                if (wPlot == null) {
                    plugin.msg().send(p, "no_plot_here");
                    return true;
                }
                // Set as server warp (Icon: BEACON default, can be changed via API later)
                wPlot.setServerWarp(true, args[1], Material.BEACON);
                plugin.store().setDirty(true);
                p.sendMessage("§a✔ Server Warp created: §e" + args[1]);
                plugin.effects().playConfirm(p);
                break;

            case "delwarp":
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /agadmin delwarp <Name>");
                    return true;
                }
                // Search for warp by name
                Plot targetWarp = null;
                for (Plot sPlot : plugin.store().getAllPlots()) {
                    if (sPlot.isServerWarp() && sPlot.getWarpName().equalsIgnoreCase(args[1])) {
                        targetWarp = sPlot;
                        break;
                    }
                }
                
                if (targetWarp != null) {
                    targetWarp.setServerWarp(false, null, null);
                    plugin.store().setDirty(true);
                    p.sendMessage("§c✖ Server Warp '" + args[1] + "' deleted.");
                    plugin.effects().playUnclaim(p);
                } else {
                    p.sendMessage("§cWarp not found.");
                }
                break;

            default:
                p.sendMessage(ChatColor.RED + "Unknown subcommand.");
        }
        return true;
    }

    private ItemStack createAdminScepter() {
        // Pull from config, default to BLAZE_ROD if missing
        Material mat = plugin.cfg().getAdminWandMaterial(); 
        if (mat == null) mat = Material.BLAZE_ROD;

        ItemStack rod = new ItemStack(mat);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.cfg().getAdminWandName());
            meta.setLore(plugin.cfg().getAdminWandLore());
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            
            // Critical NBT tag for SelectionService
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
        return null;
    }
}
