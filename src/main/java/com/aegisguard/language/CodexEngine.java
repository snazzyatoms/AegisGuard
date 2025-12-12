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
            this.styleBundles.put(style, loadYaml("codex" + File.separator + styleFileName));
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

    public Set<String> getAvailableStyles() {
        return Collections.unmodifiableSet(availableStyles);
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
     * (Currently stored in memory only for 1.2.4 – no persistence yet.)
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

    /**
     * Resolve a list of lines (for lore, multi-line prompts, etc.)
     * Uses the same priority order as {@link #resolve(String, String)}.
     */
    private List<String> resolveList(String style, String key) {
        if (key == null || key.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result;

        // 1) Overrides
        if (overridesBundle != null && overridesBundle.contains(key)) {
            result = overridesBundle.getStringList(key);
            if (!result.isEmpty()) return result;

            String single = overridesBundle.getString(key);
            if (single != null) return Collections.singletonList(single);
        }

        // 2) Style-specific bundle
        if (style != null) {
            YamlConfiguration styleCfg = styleBundles.get(style);
            if (styleCfg != null && styleCfg.contains(key)) {
                result = styleCfg.getStringList(key);
                if (!result.isEmpty()) return result;

                String single = styleCfg.getString(key);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 3) Core bundle
        if (coreBundle != null && coreBundle.contains(key)) {
            result = coreBundle.getStringList(key);
            if (!result.isEmpty()) return result;

            String single = coreBundle.getString(key);
            if (single != null) return Collections.singletonList(single);
        }

        // 4) Fallback style
        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            YamlConfiguration fbCfg = styleBundles.get(fallbackStyle);
            if (fbCfg != null && fbCfg.contains(key)) {
                result = fbCfg.getStringList(key);
                if (!result.isEmpty()) return result;

                String single = fbCfg.getString(key);
                if (single != null) return Collections.singletonList(single);
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
            String value = (e.getValue() == null) ? "" : e.getValue();
            String brace = "{" + e.getKey() + "}";
            String percent = "%" + e.getKey() + "%";

            out = out.replace(brace, value);
            out = out.replace(percent, value);
        }
        return out;
    }
}
