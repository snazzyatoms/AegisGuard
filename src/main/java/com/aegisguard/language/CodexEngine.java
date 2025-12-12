package com.aegisguard.language;

import com.aegisguard.AegisGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * CodexEngine
 *
 * Centralized language / style system for AegisGuard.
 * Loads:
 *  - codex.yml (style map + file map)
 *  - core.yml (shared/global keys)
 *  - overrides.yml (per-server overrides)
 *  - one style file per style (old_english.yml, hybrid_english.yml, modern_english.yml)
 *
 * This is intentionally lightweight. It can be expanded later
 * with per-player style profiles without breaking callers.
 */
public class CodexEngine {

    private final AegisGuard plugin;

    private String defaultStyle;
    private String fallbackStyle;

    private final Set<String> availableStyles = new HashSet<>();
    private final Map<String, YamlConfiguration> styleBundles = new HashMap<>();

    private YamlConfiguration coreBundle;
    private YamlConfiguration overridesBundle;

    public CodexEngine(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reload all codex files from disk.
     * Safe to call from /aegis reload, etc.
     */
    public void reload() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();
        }

        // Decide the main codex config file name.
        // If you actually named it "codecs.yml", either:
        //  - rename it to "codex.yml", OR
        //  - change this constant to "codecs.yml".
        String codexFileName = "codex.yml";

        ensureResourceExists(codexFileName);
        File codexFile = new File(dataFolder, codexFileName);
        if (!codexFile.exists()) {
            plugin.getLogger().warning("[Codex] " + codexFileName + " not found. Codex engine will be inactive.");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(codexFile);

        this.defaultStyle = cfg.getString("default_style", "old_english");
        this.fallbackStyle = cfg.getString("fallback_style", "modern_english");

        this.availableStyles.clear();
        this.availableStyles.addAll(cfg.getStringList("available_styles"));

        String coreFileName = cfg.getString("core_file", "core.yml");
        String overridesFileName = cfg.getString("overrides_file", "overrides.yml");

        // Core (shared)
        ensureResourceExists(coreFileName);
        this.coreBundle = loadYaml(coreFileName);

        // Overrides (server-owner tweaks, optional)
        File overridesFile = new File(dataFolder, overridesFileName);
        if (overridesFile.exists()) {
            this.overridesBundle = loadYaml(overridesFileName);
        } else {
            this.overridesBundle = new YamlConfiguration();
        }

        // Style bundles
        this.styleBundles.clear();
        for (String style : availableStyles) {
            String styleFileName = cfg.getString("file_map." + style);
            if (styleFileName == null || styleFileName.isEmpty()) {
                plugin.getLogger().warning("[Codex] No file_map entry for style '" + style + "'.");
                continue;
            }
            ensureResourceExists(styleFileName);
            this.styleBundles.put(style, loadYaml(styleFileName));
        }

        plugin.getLogger().info("[Codex] Loaded styles: " + String.join(", ", availableStyles));
    }

    private void ensureResourceExists(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) return;

        File target = new File(plugin.getDataFolder(), resourceName);
        if (target.exists()) {
            return;
        }

        try {
            plugin.saveResource(resourceName, false);
        } catch (IllegalArgumentException ignored) {
            // Resource not packaged in the jar; server owner may be providing it manually.
        }
    }

    private YamlConfiguration loadYaml(String fileName) {
        File f = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(f);
    }

    /* --------------------------------------------------------
     * Public API
     * -------------------------------------------------------- */

    public String tr(CommandSender sender, String key) {
        return tr(sender, key, Collections.emptyMap());
    }

    public String tr(CommandSender sender, String key, Map<String, String> placeholders) {
        String style = resolveStyle(sender);
        String raw = resolve(style, key);
        return applyPlaceholders(raw, placeholders);
    }

    public String tr(String key) {
        return tr(key, Collections.emptyMap());
    }

    public String tr(String key, Map<String, String> placeholders) {
        String raw = resolve(defaultStyle, key);
        return applyPlaceholders(raw, placeholders);
    }

    public String getDefaultStyle() {
        return defaultStyle;
    }

    public String getFallbackStyle() {
        return fallbackStyle;
    }

    public Set<String> getAvailableStyles() {
        return Collections.unmodifiableSet(availableStyles);
    }

    /* --------------------------------------------------------
     * Internal resolution helpers
     * -------------------------------------------------------- */

    private String resolveStyle(CommandSender sender) {
        if (sender instanceof Player player) {
            // TODO: hook into per-player language/style preference later.
            // For now we always use the global default.
        }

        return (defaultStyle != null && !defaultStyle.isEmpty())
                ? defaultStyle
                : "old_english";
    }

    private String resolve(String style, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        // 1) Overrides (highest priority)
        if (overridesBundle != null && overridesBundle.contains(key)) {
            return overridesBundle.getString(key, key);
        }

        // 2) Style-specific bundle
        if (style != null) {
            YamlConfiguration styleCfg = styleBundles.get(style);
            if (styleCfg != null && styleCfg.contains(key)) {
                return styleCfg.getString(key, key);
            }
        }

        // 3) Core bundle (shared)
        if (coreBundle != null && coreBundle.contains(key)) {
            return coreBundle.getString(key, key);
        }

        // 4) Fallback style bundle
        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            YamlConfiguration fbCfg = styleBundles.get(fallbackStyle);
            if (fbCfg != null && fbCfg.contains(key)) {
                return fbCfg.getString(key, key);
            }
        }

        // 5) Nothing found: return key for easier debugging
        return key;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String value = (e.getValue() == null) ? "" : e.getValue();
            String brace = "{" + e.getKey() + "}";
            String percent = "%" + e.getKey() + "%";

            out = out.replace(brace, value);
            out = out.replace(percent, value);
        }
        return out;
    }
}
