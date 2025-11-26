package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
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

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    
    private static final String[] SUB_COMMANDS = { "reload", "bypass", "menu", "convert", "wand" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aegis.admin")) {
            plugin.msg().send(sender, "no_perm");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player p) {
                plugin.gui().admin().open(p);
            } else {
                sender.sendMessage("Console usage: /agadmin reload");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.cfg().reload();
                plugin.msg().reload();
                plugin.worldRules().reload();
                plugin.store().load();
                sender.sendMessage(ChatColor.GREEN + "[AegisGuard] Configuration reloaded.");
                break;
                
            case "bypass":
                if (sender instanceof Player p) {
                    // Toggle permission attachment or metadata logic
                    // For v1.0 simple logic: check permission node directly in listeners
                    // If you want a toggle command, you'd need a way to store state.
                    // For now, we just remind them they have bypass if they are OP/Admin.
                    p.sendMessage(ChatColor.YELLOW + "Admin Bypass is active via permission 'aegis.admin'.");
                }
                break;
                
            case "menu":
                if (sender instanceof Player p) {
                    plugin.gui().admin().open(p);
                }
                break;
                
            case "convert":
                if (sender instanceof Player p) {
                    Plot plot = plugin.store().getPlotAt(p.getLocation());
                    if (plot == null) {
                        p.sendMessage(ChatColor.RED + "You must stand in a plot to convert it.");
                        return true;
                    }
                    plugin.store().changePlotOwner(plot, Plot.SERVER_OWNER_UUID, "Server");
                    plot.setFlag("pvp", false);
                    plot.setFlag("mobs", false);
                    plot.setFlag("build", false);
                    plugin.store().setDirty(true);
                    p.sendMessage(ChatColor.GREEN + "Plot converted to Server Zone.");
                }
                break;
                
            // --- NEW: SENTINEL'S SCEPTER ---
            case "wand":
                if (sender instanceof Player p) {
                    p.getInventory().addItem(createAdminScepter());
                    p.sendMessage(ChatColor.RED + "⚡ You have received the Sentinel's Scepter.");
                    p.sendMessage(ChatColor.GRAY + "Use this to claim Server Zones directly.");
                }
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /agadmin <reload|bypass|menu|convert|wand>");
        }
        return true;
    }

    private ItemStack createAdminScepter() {
        ItemStack rod = new ItemStack(Material.BLAZE_ROD); // Different visual
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c&lSentinel's Scepter"));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "A tool of absolute authority.",
                    " ",
                    ChatColor.YELLOW + "Right-Click: " + ChatColor.WHITE + "Select Pos 1",
                    ChatColor.YELLOW + "Left-Click: " + ChatColor.WHITE + "Select Pos 2",
                    " ",
                    ChatColor.RED + "⚠ Creates SERVER ZONES directly."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            // This tag tells SelectionService "This is a Server Claim"
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
