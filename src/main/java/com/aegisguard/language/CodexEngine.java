package com.aegisguard.language;

import com.aegisguard.AegisGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * CodexEngine
 *
 * Centralized language / style system for AegisGuard.
 * Data layout on disk:
 *   plugins/AegisGuard/codex/codex.yml
 *   plugins/AegisGuard/codex/core.yml
 *   plugins/AegisGuard/codex/overrides.yml (optional)
 *   plugins/AegisGuard/codex/old_english.yml
 *   plugins/AegisGuard/codex/hybrid_english.yml
 *   plugins/AegisGuard/codex/modern_english.yml
 *
 * Resources in the JAR live under /codex/* (src/main/resources/codex/*).
 */
public class CodexEngine {

    private static final String DATA_SUBDIR = "codex";

    private final AegisGuard plugin;
    private final File codexFolder;

    private String defaultStyle;
    private String fallbackStyle;

    private final Set<String> availableStyles = new HashSet<>();
    private final Map<String, YamlConfiguration> styleBundles = new HashMap<>();

    private YamlConfiguration coreBundle;
    private YamlConfiguration overridesBundle;

    public CodexEngine(AegisGuard plugin) {
        this.plugin = plugin;
        this.codexFolder = new File(plugin.getDataFolder(), DATA_SUBDIR);
        reload();
    }

    /**
     * Reload all codex files from disk.
     * Safe to call from /aegis reload, etc.
     */
    public void reload() {
        if (!codexFolder.exists() && !codexFolder.mkdirs()) {
            plugin.getLogger().warning("[Codex] Failed to create codex folder at " + codexFolder.getPath());
            return;
        }

        String codexFileName = "codex.yml";

        ensureResourceExists(codexFileName);
        File codexFile = new File(codexFolder, codexFileName);
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

        // Overrides (optional)
        File overridesFile = new File(codexFolder, overridesFileName);
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

    /** Ensure plugins/AegisGuard/codex/<resourceName> exists, copying from JAR if bundled. */
    private void ensureResourceExists(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) return;

        File target = new File(codexFolder, resourceName);
        if (target.exists()) return;

        try {
            // Look inside the JAR at /codex/<resourceName>
            plugin.saveResource("codex/" + resourceName, false);
        } catch (IllegalArgumentException ignored) {
            // Not bundled; server owner may provide their own file.
        }
    }

    private YamlConfiguration loadYaml(String fileName) {
        File f = new File(codexFolder, fileName);
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
            // Future: per-player style or language preference
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
