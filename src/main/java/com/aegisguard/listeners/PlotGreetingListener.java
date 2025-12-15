package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.language.CodexEngine;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlotGreetingListener implements Listener {

    private final CodexEngine codex;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1200;

    public PlotGreetingListener(AegisGuard plugin) {
        this.codex = plugin.codex();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEnter(PlotEnterEvent e) {
        Player p = e.getPlayer();
        if (coolingDown(p)) return;

        Plot plot = e.getPlot();
        if (plot == null) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("OWNER", plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown");
        ph.put("PLOT_NAME", plot.getOwnerName() != null ? plot.getOwnerName() + "'s Claim" : "Claim");

        // Title/Sub: prefer per-plot custom if set, else codex keys
        String title = (plot.getEntryTitle() != null && !plot.getEntryTitle().isBlank())
                ? plot.getEntryTitle()
                : codex.tr(p, "title_entering", ph);

        String sub = (plot.getEntrySubtitle() != null && !plot.getEntrySubtitle().isBlank())
                ? plot.getEntrySubtitle()
                : codex.tr(p, "subtitle_entering", ph);

        sendTitle(p, color(title), color(sub));

        // Custom welcome message (chat)
        String welcome = plot.getWelcomeMessage();
        if (welcome != null && !welcome.isBlank()) {
            p.sendMessage(color(applyBasicPlotPlaceholders(welcome, ph)));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeave(PlotLeaveEvent e) {
        Player p = e.getPlayer();
        if (coolingDown(p)) return;

        Plot plot = e.getPlot();
        if (plot == null) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("OWNER", plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown");
        ph.put("PLOT_NAME", plot.getOwnerName() != null ? plot.getOwnerName() + "'s Claim" : "Claim");

        // Custom farewell message (chat)
        String farewell = plot.getFarewellMessage();
        if (farewell != null && !farewell.isBlank()) {
            p.sendMessage(color(applyBasicPlotPlaceholders(farewell, ph)));
        }

        // Optional wilderness title (if you like the “you left” feedback)
        String title = codex.tr(p, "title_entering_wilderness");
        String sub = codex.tr(p, "subtitle_entering_wilderness");
        sendTitle(p, color(title), color(sub));
    }

    private boolean coolingDown(Player p) {
        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MS) return true;
        cooldown.put(p.getUniqueId(), now);
        return false;
    }

    private void sendTitle(Player p, String title, String subtitle) {
        // timings: fadeIn, stay, fadeOut (ticks)
        p.sendTitle(title, subtitle, 10, 40, 10);
    }

    private String applyBasicPlotPlaceholders(String text, Map<String, String> ph) {
        String out = text;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
