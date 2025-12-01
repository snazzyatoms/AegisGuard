package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SidebarManager
 * - Manages the visual scoreboard for plot info.
 * - Allows players to toggle it on/off.
 */
public class SidebarManager {

    private final AegisGuard plugin;
    private final ScoreboardManager manager;
    
    // session-based toggle (resets on restart, or save to config if you prefer persistence)
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public SidebarManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.manager = Bukkit.getScoreboardManager();
    }

    /**
     * Toggles the sidebar for a specific player via command.
     */
    public void toggle(Player player) {
        if (hiddenPlayers.contains(player.getUniqueId())) {
            hiddenPlayers.remove(player.getUniqueId());
            plugin.msg().send(player, "sidebar_enabled"); // Add to messages.yml
            
            // If standing in a plot, show it immediately
            Plot plot = plugin.store().getPlotAt(player.getLocation());
            if (plot != null) showSidebar(player, plot);
        } else {
            hiddenPlayers.add(player.getUniqueId());
            plugin.msg().send(player, "sidebar_disabled"); // Add to messages.yml
            hideSidebar(player);
        }
    }

    public void showSidebar(Player player, Plot plot) {
        // 1. Check global config
        if (!plugin.cfg().raw().getBoolean("sidebar.enabled", true)) return;
        
        // 2. Check player preference
        if (hiddenPlayers.contains(player.getUniqueId())) return;

        Scoreboard board = manager.getNewScoreboard();
        // "AegisInfo" is internal ID, "AegisGuard" is display title
        Objective obj = board.registerNewObjective("AegisInfo", "dummy", "§b§lAegisGuard");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Dynamic Title (e.g. "Snazzy's Plot")
        String ownerName = plot.getOwnerName();
        if (ownerName.length() > 12) ownerName = ownerName.substring(0, 12); // Prevent overflow
        obj.setDisplayName("§3§l" + ownerName + "'s");

        List<String> lines = new ArrayList<>();
        lines.add("§7----------------");
        lines.add("§fLevel: §e" + plot.getLevel());
        
        String biome = plot.getCustomBiome();
        if (biome == null) biome = "Natural";
        // Clean up biome name (e.g. CHERRY_GROVE -> Cherry Grove)
        biome = biome.toLowerCase().replace("_", " ");
        // Capitalize first letter
        if (biome.length() > 0) biome = biome.substring(0, 1).toUpperCase() + biome.substring(1);
        
        lines.add("§fBiome: §a" + biome);
        
        // Add Active Buffs
        List<String> buffs = new ArrayList<>();
        int level = plot.getLevel();
        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;
            for (String reward : rewards) {
                if (reward.startsWith("EFFECT:")) {
                    try {
                        String[] parts = reward.split(":");
                        String name = parts[1].toLowerCase().replace("_", " ");
                        // Abbreviate common names
                        if (name.contains("regeneration")) name = "Regen";
                        if (name.contains("resistance")) name = "Resist";
                        if (name.contains("night vision")) name = "Night Vis";
                        
                        // Capitalize
                        name = name.substring(0, 1).toUpperCase() + name.substring(1);
                        String roman = toRoman(Integer.parseInt(parts[2]));
                        buffs.add("§a+ " + name + " " + roman);
                    } catch (Exception ignored) {}
                } else if (reward.startsWith("FLAG:fly")) {
                    buffs.add("§b+ Flight");
                }
            }
        }
        
        if (!buffs.isEmpty()) {
            lines.add(" ");
            lines.add("§6§lBuffs:");
            // Limit to 5 lines to prevent screen clutter
            for (int i = 0; i < Math.min(buffs.size(), 5); i++) {
                lines.add(buffs.get(i));
            }
            if (buffs.size() > 5) lines.add("§7+ " + (buffs.size() - 5) + " more...");
        } else {
             lines.add(" ");
             lines.add("§7(No Buffs)");
        }

        lines.add("§7----------------");

        // Scoreboard lines are ordered by score (Descending)
        int score = lines.size();
        for (String line : lines) {
            // Prevent duplicate line errors by adding invisible spaces if needed
            // (Simple implementation here assumes unique lines for brevity)
            Score s = obj.getScore(line);
            s.setScore(score--);
        }

        player.setScoreboard(board);
    }

    public void hideSidebar(Player player) {
        player.setScoreboard(manager.getMainScoreboard());
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
