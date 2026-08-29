package com.aegisguard.season;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Staff-authored season overlay for the Travel Atlas and Routes browser.
 * Featured plots and routes are pinned first. Stored in the data folder.
 */
public final class SeasonService {
    private final AegisGuard plugin;
    private final File file;
    private String title = "";
    private String description = "";
    private final LinkedHashSet<UUID> featuredPlots = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> featuredRoutes = new LinkedHashSet<>();

    public SeasonService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "seasons.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("seasons.enabled", true);
    }

    public int maxFeaturedPlots() {
        return Math.max(1, plugin.getConfig().getInt("seasons.max_featured_plots", 5));
    }

    public int maxFeaturedRoutes() {
        return Math.max(1, plugin.getConfig().getInt("seasons.max_featured_routes", 5));
    }

    public String title() {
        return title == null ? "" : title;
    }

    public String description() {
        return description == null ? "" : description;
    }

    public boolean hasSeason() {
        return !title().isBlank() || !featuredPlots.isEmpty() || !featuredRoutes.isEmpty();
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title.trim();
        save();
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
        save();
    }

    public List<UUID> featuredPlots() {
        return List.copyOf(featuredPlots);
    }

    public List<UUID> featuredRoutes() {
        return List.copyOf(featuredRoutes);
    }

    public boolean isFeaturedPlot(UUID plotId) {
        return plotId != null && featuredPlots.contains(plotId);
    }

    public boolean isFeaturedRoute(UUID routeId) {
        return routeId != null && featuredRoutes.contains(routeId);
    }

    public boolean featurePlot(UUID plotId) {
        if (plotId == null) return false;
        if (featuredPlots.contains(plotId)) return true;
        if (featuredPlots.size() >= maxFeaturedPlots()) return false;
        featuredPlots.add(plotId);
        save();
        return true;
    }

    public boolean unfeaturePlot(UUID plotId) {
        boolean removed = plotId != null && featuredPlots.remove(plotId);
        if (removed) save();
        return removed;
    }

    public boolean featureRoute(UUID routeId) {
        if (routeId == null) return false;
        if (featuredRoutes.contains(routeId)) return true;
        if (featuredRoutes.size() >= maxFeaturedRoutes()) return false;
        featuredRoutes.add(routeId);
        save();
        return true;
    }

    public boolean unfeatureRoute(UUID routeId) {
        boolean removed = routeId != null && featuredRoutes.remove(routeId);
        if (removed) save();
        return removed;
    }

    public void clear() {
        title = "";
        description = "";
        featuredPlots.clear();
        featuredRoutes.clear();
        save();
    }

    public int comparePlots(UUID left, UUID right) {
        boolean a = isFeaturedPlot(left);
        boolean b = isFeaturedPlot(right);
        if (a == b) return 0;
        return a ? -1 : 1;
    }

    public List<com.aegisguard.routes.Route> sortRoutes(List<com.aegisguard.routes.Route> routes) {
        if (routes == null || routes.isEmpty()) return routes == null ? List.of() : routes;
        List<com.aegisguard.routes.Route> copy = new ArrayList<>(routes);
        copy.sort((left, right) -> {
            boolean a = left != null && isFeaturedRoute(left.getId());
            boolean b = right != null && isFeaturedRoute(right.getId());
            if (a == b) {
                String n1 = left == null ? "" : left.getName();
                String n2 = right == null ? "" : right.getName();
                return n1.compareToIgnoreCase(n2);
            }
            return a ? -1 : 1;
        });
        return copy;
    }

    public UUID resolveRoute(String raw) {
        if (raw == null || raw.isBlank() || plugin.routes() == null) return null;
        String needle = raw.trim();
        try {
            UUID id = UUID.fromString(needle);
            if (plugin.routes().getRoute(id) != null) return id;
        } catch (IllegalArgumentException ignored) {
        }
        for (com.aegisguard.routes.Route route : plugin.routes().enabledRoutes()) {
            if (route.getName() != null && route.getName().equalsIgnoreCase(needle)) {
                return route.getId();
            }
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        for (com.aegisguard.routes.Route route : plugin.routes().enabledRoutes()) {
            if (route.getName() != null && route.getName().toLowerCase(Locale.ROOT).contains(lower)) {
                return route.getId();
            }
        }
        return null;
    }

    private void load() {
        title = "";
        description = "";
        featuredPlots.clear();
        featuredRoutes.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        title = yaml.getString("title", "");
        description = yaml.getString("description", "");
        readIds(yaml.getStringList("featured_plots"), featuredPlots);
        readIds(yaml.getStringList("featured_routes"), featuredRoutes);
    }

    private void readIds(List<String> raw, Set<UUID> target) {
        if (raw == null) return;
        for (String value : raw) {
            try {
                target.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("title", title());
        yaml.set("description", description());
        yaml.set("featured_plots", featuredPlots.stream().map(UUID::toString).toList());
        yaml.set("featured_routes", featuredRoutes.stream().map(UUID::toString).toList());
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(file);
        } catch (IOException error) {
            plugin.getLogger().warning("Could not save seasons.yml: " + error.getMessage());
        }
    }
}
