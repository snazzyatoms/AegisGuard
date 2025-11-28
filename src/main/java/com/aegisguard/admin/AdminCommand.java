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
    private static final String[] SUB_COMMANDS = { "reload", "bypass", "menu", "convert", "wand" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
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
            } else {
                sender.sendMessage("[AegisGuard] GUI commands are player-only. Use 'aegisadmin reload' to reload config.");
            }
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
                // Toggle permission logic isn't usually done via command unless hooking into LuckPerms API.
                // For a simple plugin, we usually just inform them they have the permission node.
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

            default:
                p.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /agadmin <reload|bypass|menu|convert|wand>");
        }
        return true;
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
        return null;
    }
}
