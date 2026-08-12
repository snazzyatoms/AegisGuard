package com.aegisguard.hooks.market;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketBridgeManager {

    private final AegisGuard plugin;

    public MarketBridgeManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean preferLocalWhenInPlot() {
        return plugin.getConfig().getBoolean("market_hub.prefer_local_when_in_plot", true);
    }

    public boolean allowHomeMarkets() {
        return plugin.getConfig().getBoolean("market_hub.allow_home_markets", true);
    }

    public boolean externalBridgesEnabled() {
        return plugin.getConfig().getBoolean("market_hub.external_bridges.enabled", true);
    }

    public List<String> getInstalledShopPluginNames() {
        List<String> names = new ArrayList<>();
        for (String pluginName : List.of("QuickShop", "ChestShop", "Shopkeepers", "ExcellentShop")) {
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                names.add(pluginName);
            }
        }
        return names;
    }

    public boolean hasInstalledBridge() {
        if (!externalBridgesEnabled()) return false;

        ConfigurationSection entriesSec = plugin.getConfig().getConfigurationSection("market_hub.external_bridges.entries");
        if (entriesSec == null) return false;

        for (String key : entriesSec.getKeys(false)) {
            ConfigurationSection sec = entriesSec.getConfigurationSection(key);
            if (sec == null) continue;

            String pluginName = sec.getString("plugin", "").trim();
            if (pluginName.isEmpty()) continue;
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBridgeForPlot(Plot plot, boolean includeDisabledForFlag) {
        return !getActiveBridgeEntries(plot, includeDisabledForFlag).isEmpty();
    }

    public boolean plotQualifiesForLocalMarket(Plot plot, Player player) {
        if (plot == null || player == null) return false;
        if (!allowHomeMarkets()) return false;

        return (plugin.tradeStalls() != null && plugin.tradeStalls().isEnabledFor(plot) && plot.hasBrowsableStalls())
                || plot.hasBrowsableZonesFor(player)
                || plot.getFlag("shop-interact", false)
                || plot.canManage(player, plugin)
                || !getActiveBridgeEntries(plot, false).isEmpty();
    }

    public List<BridgeEntry> getActiveBridgeEntries(Plot plot, boolean includeDisabledForFlag) {
        if (plot == null || !externalBridgesEnabled()) return List.of();

        ConfigurationSection entriesSec = plugin.getConfig().getConfigurationSection("market_hub.external_bridges.entries");
        if (entriesSec == null) return List.of();

        List<BridgeEntry> entries = new ArrayList<>();
        for (String key : entriesSec.getKeys(false)) {
            ConfigurationSection sec = entriesSec.getConfigurationSection(key);
            if (sec == null) continue;

            String pluginName = sec.getString("plugin", "").trim();
            if (pluginName.isEmpty()) continue;
            if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) continue;

            boolean requiresShopFlag = sec.getBoolean("requires_shop_flag",
                    plugin.getConfig().getBoolean("market_hub.require_shop_flag_for_external_bridges", true));
            boolean accessible = !requiresShopFlag || plot.getFlag("shop-interact", false);
            if (!accessible && !includeDisabledForFlag) continue;

            List<String> commands = sec.getStringList("commands");
            if (commands == null || commands.isEmpty()) continue;

            String displayName = sec.getString("display_name", "&a" + pluginName);
            Material icon = parseMaterial(sec.getString("icon"), Material.CHEST);
            List<String> lore = sec.getStringList("lore");

            entries.add(new BridgeEntry(
                    key,
                    pluginName,
                    displayName,
                    icon,
                    lore == null ? List.of() : lore,
                    requiresShopFlag,
                    accessible,
                    commands
            ));
        }

        return entries;
    }

    public BridgeEntry getBridgeEntry(String id, Plot plot, boolean includeDisabledForFlag) {
        if (id == null || id.isBlank()) return null;
        for (BridgeEntry entry : getActiveBridgeEntries(plot, includeDisabledForFlag)) {
            if (id.equalsIgnoreCase(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    public boolean dispatchBridge(Player player, Plot plot, BridgeEntry entry) {
        if (player == null || plot == null || entry == null) return false;
        if (!entry.accessible()) return false;

        Map<String, String> vars = Map.of(
                "player", player.getName(),
                "plot_owner", safe(plot.getOwnerName()),
                "plot_name", safe(plot.getPlotName()),
                "plot_id", String.valueOf(plot.getPlotId()),
                "world", safe(plot.getWorld()),
                "x", String.valueOf(player.getLocation().getBlockX()),
                "y", String.valueOf(player.getLocation().getBlockY()),
                "z", String.valueOf(player.getLocation().getBlockZ())
        );

        for (String rawCmd : entry.commands()) {
            String rendered = applyPlaceholders(rawCmd, vars).trim();
            if (rendered.isEmpty()) continue;

            if (rendered.regionMatches(true, 0, "console:", 0, 8)) {
                String cmd = rendered.substring(8).trim();
                if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(cmd));
                continue;
            }

            if (rendered.regionMatches(true, 0, "player:", 0, 7)) {
                rendered = rendered.substring(7).trim();
            }

            if (!rendered.isEmpty()) {
                Bukkit.dispatchCommand(player, stripSlash(rendered));
            }
        }

        return true;
    }

    private String applyPlaceholders(String input, Map<String, String> vars) {
        if (input == null || input.isEmpty()) return "";
        String out = input;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    private String stripSlash(String command) {
        if (command == null) return "";
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material exact = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return exact == null ? fallback : exact;
    }

    public record BridgeEntry(
            String id,
            String pluginName,
            String displayName,
            Material icon,
            List<String> lore,
            boolean requiresShopFlag,
            boolean accessible,
            List<String> commands
    ) {
        public List<String> loreOrEmpty() {
            return lore == null ? Collections.emptyList() : lore;
        }
    }
}
