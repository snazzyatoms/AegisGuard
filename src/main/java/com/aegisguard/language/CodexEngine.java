package com.aegisguard.language;

import com.aegisguard.AegisGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * CodexEngine
 *
 * Centralized language / style system for AegisGuard.
 * Loads:
 * - codex.yml (style map + file map)
 * - core.yml (shared/global keys)
 * - overrides.yml (per-server overrides)
 * - one style file per style (old_english, hybrid, spanish_mx, etc.)
 */
public class CodexEngine {

    private final AegisGuard plugin;

    private String defaultStyle;
    private String fallbackStyle;

    // ✅ CHANGED: Use List to preserve the order defined in codex.yml
    private final List<String> availableStyles = new ArrayList<>();

    private final Map<String, YamlConfiguration> styleBundles = new HashMap<>();

    private YamlConfiguration coreBundle;
    private YamlConfiguration overridesBundle;

    /** Simple in-memory per-player style map (1.2.4). */
    private final Map<UUID, String> playerStyles = new HashMap<>();

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

        // Main codex config
        String codexFileName = "codex.yml";

        ensureResourceExists("codex/" + codexFileName); // packaged under /codex in JAR
        File codexFile = new File(dataFolder, "codex" + File.separator + codexFileName);

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
        ensureResourceExists("codex/" + coreFileName);
        this.coreBundle = loadYaml("codex" + File.separator + coreFileName);

        // Overrides (server-owner tweaks, optional)
        File overridesFile = new File(dataFolder, "codex" + File.separator + overridesFileName);
        if (overridesFile.exists()) {
            this.overridesBundle = loadYaml("codex" + File.separator + overridesFileName);
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
            ensureResourceExists("codex/" + styleFileName);

            YamlConfiguration raw = loadYaml("codex" + File.separator + styleFileName);
            YamlConfiguration normalized = normalizeStyleYaml(style, raw);

            this.styleBundles.put(style, normalized);
        }

        plugin.getLogger().info("[Codex] Loaded styles: " + String.join(", ", availableStyles));
    }

    private void ensureResourceExists(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) return;

        // resourcePath already includes "codex/..." – mirror that structure in data folder
        String fileName = resourcePath.replace("codex/", "codex" + File.separator);
        File target = new File(plugin.getDataFolder(), fileName);
        if (target.exists()) {
            return;
        }

        try {
            plugin.saveResource(resourcePath, false);
        } catch (IllegalArgumentException ignored) {
            // Resource not packaged in the jar; server owner may be providing it manually.
        }
    }

    private YamlConfiguration loadYaml(String relativePath) {
        File f = new File(plugin.getDataFolder(), relativePath);
        return YamlConfiguration.loadConfiguration(f);
    }

    /**
     * ✅ NEW: Normalize style YAMLs so translations can be wrapped or unwrapped.
     *
     * Supports:
     * - Flat files: menu_title: ...
     * - Wrapped files: spanish_mx: { menu_title: ... }
     * - Single-root wrapper files (one top-level key): { some_root: { ... } }
     */
    private YamlConfiguration normalizeStyleYaml(String style, YamlConfiguration cfg) {
        if (cfg == null) return new YamlConfiguration();

        Set<String> top = cfg.getKeys(false);
        if (top == null || top.isEmpty()) return cfg;

        // Case 1: Exact wrapper matches style id (spanish_mx:, spanish_ar:, etc.)
        if (style != null && cfg.isConfigurationSection(style)) {
            ConfigurationSection sec = cfg.getConfigurationSection(style);
            return flattenSection(sec);
        }

        // Case 2: File has one single wrapper root (common in older messages.yml style blocks)
        if (top.size() == 1) {
            String only = top.iterator().next();
            if (cfg.isConfigurationSection(only)) {
                ConfigurationSection sec = cfg.getConfigurationSection(only);
                return flattenSection(sec);
            }
        }

        // Otherwise: assume already flat
        return cfg;
    }

    private YamlConfiguration flattenSection(ConfigurationSection sec) {
        YamlConfiguration out = new YamlConfiguration();
        if (sec == null) return out;

        for (String key : sec.getKeys(true)) {
            Object val = sec.get(key);
            out.set(key, val);
        }
        return out;
    }

    /* --------------------------------------------------------
     * Public string API
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

    /**
     * Returns the list of available styles in the order defined in codex.yml.
     */
    public List<String> getAvailableStyles() {
        return Collections.unmodifiableList(availableStyles);
    }

    /**
     * ✅ NEW: Helper to get the next style in the cycle.
     * Useful for Settings GUI buttons.
     */
    public String getNextStyle(String currentStyle) {
        if (availableStyles.isEmpty()) return defaultStyle;

        int index = availableStyles.indexOf(currentStyle);
        // If not found or at the end of the list, loop back to start
        if (index == -1 || index >= availableStyles.size() - 1) {
            return availableStyles.get(0);
        }
        return availableStyles.get(index + 1);
    }

    /* --------------------------------------------------------
     * NEW: List API (for lores / multi-line text)
     * -------------------------------------------------------- */

    public List<String> trList(CommandSender sender, String key) {
        return trList(sender, key, Collections.emptyMap());
    }

    public List<String> trList(CommandSender sender, String key, Map<String, String> placeholders) {
        String style = resolveStyle(sender);
        List<String> rawList = resolveList(style, key);
        if (rawList.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(rawList.size());
        for (String line : rawList) {
            out.add(applyPlaceholders(line, placeholders));
        }
        return out;
    }

    // Convenience overloads so you can pass Player directly
    public List<String> trList(Player player, String key) {
        return trList((CommandSender) player, key);
    }

    public List<String> trList(Player player, String key, Map<String, String> placeholders) {
        return trList((CommandSender) player, key, placeholders);
    }

    // Short alias used in a bunch of GUIs: codex().list(player, "path")
    public List<String> list(Player player, String key) {
        return trList(player, key);
    }

    public List<String> list(CommandSender sender, String key) {
        return trList(sender, key);
    }

    /* --------------------------------------------------------
     * NEW: Per-player style helpers (simple in-memory)
     * -------------------------------------------------------- */

    public String getPlayerStyle(Player player) {
        if (player == null) return defaultStyle != null ? defaultStyle : "old_english";
        String style = playerStyles.get(player.getUniqueId());
        if (style != null && availableStyles.contains(style)) {
            return style;
        }
        // Fallback to global resolution
        return resolveStyle(player);
    }

    /**
     * Set a player's style. Returns true if the style is valid and was applied.
     */
    public boolean setPlayerStyle(Player player, String style) {
        if (player == null || style == null) return false;
        style = style.toLowerCase(Locale.ROOT);
        if (!availableStyles.contains(style)) return false;
        playerStyles.put(player.getUniqueId(), style);
        return true;
    }

    /* --------------------------------------------------------
     * Internal resolution helpers
     * -------------------------------------------------------- */

    private String resolveStyle(CommandSender sender) {
        if (sender instanceof Player player) {
            String style = playerStyles.get(player.getUniqueId());
            if (style != null && availableStyles.contains(style)) {
                return style;
            }
        }

        return (defaultStyle != null && !defaultStyle.isEmpty())
                ? defaultStyle
                : "old_english";
    }

    /**
     * ✅ NEW: Generate candidate keys so BOTH hyphen-style and underscore-style keys work.
     */
    private List<String> keyCandidates(String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(key);

        if (key.indexOf('-') >= 0) out.add(key.replace('-', '_'));
        if (key.indexOf('_') >= 0) out.add(key.replace('_', '-'));

        // (Optional sanity) also allow accidental double separators
        out.add(key.replace('-', '_').replaceAll("__+", "_"));
        out.add(key.replace('_', '-').replaceAll("--+", "-"));

        return new ArrayList<>(out);
    }

    private String resolve(String style, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        for (String k : keyCandidates(key)) {
            // 1) Overrides (highest priority)
            if (overridesBundle != null && overridesBundle.contains(k)) {
                return overridesBundle.getString(k, k);
            }
        }

        for (String k : keyCandidates(key)) {
            // 2) Style-specific bundle
            if (style != null) {
                YamlConfiguration styleCfg = styleBundles.get(style);
                if (styleCfg != null && styleCfg.contains(k)) {
                    return styleCfg.getString(k, k);
                }
            }
        }

        for (String k : keyCandidates(key)) {
            // 3) Core bundle (shared)
            if (coreBundle != null && coreBundle.contains(k)) {
                return coreBundle.getString(k, k);
            }
        }

        for (String k : keyCandidates(key)) {
            // 4) Fallback style bundle
            if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
                YamlConfiguration fbCfg = styleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) {
                    return fbCfg.getString(k, k);
                }
            }
        }

        // 5) Nothing found: return key for easier debugging
        return key;
    }

    private List<String> resolveList(String style, String key) {
        if (key == null || key.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result;

        // 1) Overrides
        for (String k : keyCandidates(key)) {
            if (overridesBundle != null && overridesBundle.contains(k)) {
                result = overridesBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = overridesBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 2) Style-specific bundle
        for (String k : keyCandidates(key)) {
            if (style != null) {
                YamlConfiguration styleCfg = styleBundles.get(style);
                if (styleCfg != null && styleCfg.contains(k)) {
                    result = styleCfg.getStringList(k);
                    if (!result.isEmpty()) return result;

                    String single = styleCfg.getString(k);
                    if (single != null) return Collections.singletonList(single);
                }
            }
        }

        // 3) Core bundle
        for (String k : keyCandidates(key)) {
            if (coreBundle != null && coreBundle.contains(k)) {
                result = coreBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = coreBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 4) Fallback style
        for (String k : keyCandidates(key)) {
            if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
                YamlConfiguration fbCfg = styleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) {
                    result = fbCfg.getStringList(k);
                    if (!result.isEmpty()) return result;

                    String single = fbCfg.getString(k);
                    if (single != null) return Collections.singletonList(single);
                }
            }
        }

        return Collections.emptyList();
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String rawKey = e.getKey();
            if (rawKey == null || rawKey.isEmpty()) continue;

            String value = (e.getValue() == null) ? "" : e.getValue();

            // Support {KEY}, %KEY%, and ${KEY}
            String brace = "{" + rawKey + "}";
            String braceLower = "{" + rawKey.toLowerCase(Locale.ROOT) + "}";
            String braceUpper = "{" + rawKey.toUpperCase(Locale.ROOT) + "}";

            String percent = "%" + rawKey + "%";
            String percentLower = "%" + rawKey.toLowerCase(Locale.ROOT) + "%";
            String percentUpper = "%" + rawKey.toUpperCase(Locale.ROOT) + "%";

            String dollar = "${" + rawKey + "}";
            String dollarLower = "${" + rawKey.toLowerCase(Locale.ROOT) + "}";
            String dollarUpper = "${" + rawKey.toUpperCase(Locale.ROOT) + "}";

            out = out.replace(brace, value).replace(braceLower, value).replace(braceUpper, value);
            out = out.replace(percent, value).replace(percentLower, value).replace(percentUpper, value);
            out = out.replace(dollar, value).replace(dollarLower, value).replace(dollarUpper, value);
        }
        return out;
    }
}
