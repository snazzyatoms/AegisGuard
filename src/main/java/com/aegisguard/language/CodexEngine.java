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
 *
 * Supports TWO modes:
 *  1) ✅ Split Bundles (NEW 1.2.4+):
 *      /codex/<style>/guis.yml
 *      /codex/<style>/system.yml
 *      /codex/<style>/upgrades.yml
 *      /codex/<style>/expansions.yml
 *
 *  2) 📜 Legacy Single File per style (codex.yml file_map):
 *      /codex/old_english.yml, /codex/modern_english.yml, etc.
 *
 * Also loads:
 * - codex.yml (style list + optional legacy file_map)
 * - core.yml (shared/global keys)
 * - overrides.yml (per-server overrides)
 *
 * ✅ Per-player style persistence (NEW):
 * - Stored in config.yml: localization.player_styles.<uuid> = <style>
 */
public class CodexEngine {

    private static final String PLAYER_STYLE_PATH = "localization.player_styles";

    private final AegisGuard plugin;

    private String defaultStyle;
    private String fallbackStyle;

    // Preserve codex.yml ordering
    private final List<String> availableStyles = new ArrayList<>();

    // Combined per-style config (merged from bundles or legacy file)
    private final Map<String, YamlConfiguration> styleBundles = new HashMap<>();

    private YamlConfiguration coreBundle;
    private YamlConfiguration overridesBundle;

    /** Simple in-memory per-player style map (hydrated from config.yml). */
    private final Map<UUID, String> playerStyles = new HashMap<>();

    // Folder name under plugin data folder (usually "codex")
    private String folderName = "codex";

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

        // Allow config.yml to override folder (defaults to "codex")
        this.folderName = plugin.getConfig().getString("localization.folder", "codex");
        File baseDir = new File(dataFolder, folderName);
        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }

        // codex.yml (always expected)
        ensureResourceExists("codex/codex.yml"); // resources packaged under /codex in jar
        File codexFile = new File(baseDir, "codex.yml");

        if (!codexFile.exists()) {
            plugin.getLogger().warning("[Codex] codex.yml not found in /" + folderName + "/. Codex engine will be inactive.");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(codexFile);

        // Styles: prefer config.yml localization overrides if present, else codex.yml
        String cfgDefault = plugin.getConfig().getString("localization.default_style", "").trim();
        String cfgFallback = plugin.getConfig().getString("localization.fallback_style", "").trim();

        this.defaultStyle = !cfgDefault.isEmpty()
                ? cfgDefault
                : cfg.getString("default_style", "old_english");

        this.fallbackStyle = !cfgFallback.isEmpty()
                ? cfgFallback
                : cfg.getString("fallback_style", "modern_english");

        // Available styles: prefer config.yml list if present, else codex.yml
        List<String> fromConfig = plugin.getConfig().getStringList("localization.available_languages");
        List<String> fromCodex = cfg.getStringList("available_styles");

        this.availableStyles.clear();
        if (fromConfig != null && !fromConfig.isEmpty()) {
            this.availableStyles.addAll(fromConfig);
        } else if (fromCodex != null && !fromCodex.isEmpty()) {
            this.availableStyles.addAll(fromCodex);
        } else {
            // last-resort fallback
            this.availableStyles.addAll(Arrays.asList("old_english", "hybrid_english", "modern_english", "spanish_mx", "spanish_ar"));
        }

        // Normalize style IDs (lowercase)
        normalizeAvailableStyles();

        // Ensure default/fallback are valid
        this.defaultStyle = normalizeStyleId(this.defaultStyle);
        this.fallbackStyle = normalizeStyleId(this.fallbackStyle);

        if (!availableStyles.contains(defaultStyle)) {
            defaultStyle = availableStyles.isEmpty() ? "old_english" : availableStyles.get(0);
        }
        if (!availableStyles.contains(fallbackStyle)) {
            fallbackStyle = defaultStyle;
        }

        // Core + overrides file names (from codex.yml, with defaults)
        String coreFileName = cfg.getString("core_file", "core.yml");
        String overridesFileName = cfg.getString("overrides_file", "overrides.yml");

        ensureResourceExists("codex/" + coreFileName);
        this.coreBundle = loadYaml(new File(baseDir, coreFileName));

        File overridesFile = new File(baseDir, overridesFileName);
        if (overridesFile.exists()) {
            this.overridesBundle = loadYaml(overridesFile);
        } else {
            this.overridesBundle = new YamlConfiguration();
        }

        // Bundles list: prefer config.yml, else codex.yml, else defaults
        List<String> bundles = plugin.getConfig().getStringList("localization.bundles");
        if (bundles == null || bundles.isEmpty()) {
            bundles = cfg.getStringList("bundles");
        }
        if (bundles == null || bundles.isEmpty()) {
            bundles = Arrays.asList("guis.yml", "system.yml", "upgrades.yml", "expansions.yml");
        }

        // Mode: config.yml can force, otherwise codex.yml can force, otherwise auto-detect
        String mode = plugin.getConfig().getString("localization.mode", "").trim();
        if (mode.isEmpty()) mode = cfg.getString("mode", "auto");
        mode = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);

        boolean forceLegacy = mode.equals("legacy");
        boolean forceSplit = mode.equals("split");

        // Auto-detect: if ANY style folder contains any bundle file, we treat it as split mode
        boolean splitDetected = false;
        if (!forceLegacy) {
            for (String style : availableStyles) {
                if (hasAnySplitBundle(baseDir, style, bundles)) {
                    splitDetected = true;
                    break;
                }
            }
        }

        boolean useSplit = forceSplit || (!forceLegacy && splitDetected);

        // Load styles
        this.styleBundles.clear();

        if (useSplit) {
            for (String style : availableStyles) {
                YamlConfiguration merged = new YamlConfiguration();

                File styleDir = new File(baseDir, style);
                if (!styleDir.exists()) {
                    plugin.getLogger().warning("[Codex] Missing style folder: " + folderName + "/" + style + "/");
                }

                for (String bundleFile : bundles) {
                    ensureResourceExists("codex/" + style + "/" + bundleFile);

                    File f = new File(styleDir, bundleFile);
                    if (!f.exists()) continue;

                    YamlConfiguration partRaw = loadYaml(f);
                    YamlConfiguration part = normalizeStyleYaml(style, partRaw);

                    mergeYamlLeaves(merged, part);
                }

                this.styleBundles.put(style, merged);
            }

            plugin.getLogger().info("[Codex] Mode=split (bundles). Loaded styles: " + String.join(", ", availableStyles));
        } else {
            // Legacy file_map mode (codex.yml)
            for (String style : availableStyles) {
                String styleFileName = cfg.getString("file_map." + style);
                if (styleFileName == null || styleFileName.isEmpty()) {
                    plugin.getLogger().warning("[Codex] No file_map entry for style '" + style + "'.");
                    continue;
                }

                ensureResourceExists("codex/" + styleFileName);

                File f = new File(baseDir, styleFileName);
                if (!f.exists()) {
                    plugin.getLogger().warning("[Codex] Legacy style file missing: " + folderName + "/" + styleFileName);
                    continue;
                }

                YamlConfiguration raw = loadYaml(f);
                YamlConfiguration normalized = normalizeStyleYaml(style, raw);
                this.styleBundles.put(style, normalized);
            }

            plugin.getLogger().info("[Codex] Mode=legacy (file_map). Loaded styles: " + String.join(", ", availableStyles));
        }

        // ✅ Clean up any cached player styles that no longer exist.
        pruneInvalidPlayerStyles();
    }

    private void normalizeAvailableStyles() {
        for (int i = 0; i < availableStyles.size(); i++) {
            availableStyles.set(i, normalizeStyleId(availableStyles.get(i)));
        }
        // remove dupes while preserving order
        LinkedHashSet<String> deduped = new LinkedHashSet<>(availableStyles);
        availableStyles.clear();
        availableStyles.addAll(deduped);
    }

    private String normalizeStyleId(String style) {
        if (style == null) return "";
        return style.trim().toLowerCase(Locale.ROOT);
    }

    private void pruneInvalidPlayerStyles() {
        if (playerStyles.isEmpty()) return;
        playerStyles.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || !availableStyles.contains(e.getValue()));
    }

    private boolean hasAnySplitBundle(File baseDir, String style, List<String> bundles) {
        File styleDir = new File(baseDir, style);
        if (!styleDir.exists()) return false;
        for (String b : bundles) {
            if (new File(styleDir, b).exists()) return true;
        }
        return false;
    }

    private void ensureResourceExists(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) return;

        if (!resourcePath.startsWith("codex/")) return;

        File target = new File(plugin.getDataFolder(), resourcePath.replace("/", File.separator));
        if (target.exists()) return;

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        try {
            plugin.saveResource(resourcePath, false);
        } catch (IllegalArgumentException ignored) {
            // Not packaged in jar; server owner may provide manually.
        }
    }

    private YamlConfiguration loadYaml(File f) {
        return YamlConfiguration.loadConfiguration(f);
    }

    /**
     * Normalize style YAMLs so translations can be wrapped or unwrapped.
     */
    private YamlConfiguration normalizeStyleYaml(String style, YamlConfiguration cfg) {
        if (cfg == null) return new YamlConfiguration();

        Set<String> top = cfg.getKeys(false);
        if (top == null || top.isEmpty()) return cfg;

        if (style != null && cfg.isConfigurationSection(style)) {
            ConfigurationSection sec = cfg.getConfigurationSection(style);
            return flattenSectionLeaves(sec);
        }

        if (top.size() == 1) {
            String only = top.iterator().next();
            if (cfg.isConfigurationSection(only)) {
                ConfigurationSection sec = cfg.getConfigurationSection(only);
                return flattenSectionLeaves(sec);
            }
        }

        return cfg;
    }

    private YamlConfiguration flattenSectionLeaves(ConfigurationSection sec) {
        YamlConfiguration out = new YamlConfiguration();
        if (sec == null) return out;

        for (String key : sec.getKeys(true)) {
            Object val = sec.get(key);
            if (val instanceof ConfigurationSection) continue;
            out.set(key, val);
        }
        return out;
    }

    private void mergeYamlLeaves(YamlConfiguration target, YamlConfiguration src) {
        if (target == null || src == null) return;

        for (String key : src.getKeys(true)) {
            Object val = src.get(key);
            if (val instanceof ConfigurationSection) continue;
            target.set(key, val);
        }
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

    public String getDefaultStyle() { return defaultStyle; }
    public String getFallbackStyle() { return fallbackStyle; }

    public List<String> getAvailableStyles() {
        return Collections.unmodifiableList(availableStyles);
    }

    public String getNextStyle(String currentStyle) {
        if (availableStyles.isEmpty()) return defaultStyle;

        currentStyle = normalizeStyleId(currentStyle);
        int index = availableStyles.indexOf(currentStyle);
        if (index == -1 || index >= availableStyles.size() - 1) {
            return availableStyles.get(0);
        }
        return availableStyles.get(index + 1);
    }

    /* --------------------------------------------------------
     * List API (for lores / multi-line text)
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

    public List<String> trList(Player player, String key) {
        return trList((CommandSender) player, key);
    }

    public List<String> trList(Player player, String key, Map<String, String> placeholders) {
        return trList((CommandSender) player, key, placeholders);
    }

    public List<String> list(Player player, String key) { return trList(player, key); }
    public List<String> list(CommandSender sender, String key) { return trList(sender, key); }

    /* --------------------------------------------------------
     * Per-player style helpers (persisted in config.yml)
     * -------------------------------------------------------- */

    public String getPlayerStyle(Player player) {
        if (player == null) return (defaultStyle != null && !defaultStyle.isEmpty()) ? defaultStyle : "old_english";

        UUID id = player.getUniqueId();

        // 1) in-memory
        String cached = playerStyles.get(id);
        if (cached != null && availableStyles.contains(cached)) return cached;

        // 2) persisted config
        String stored = plugin.getConfig().getString(PLAYER_STYLE_PATH + "." + id, null);
        stored = normalizeStyleId(stored);
        if (!stored.isEmpty() && availableStyles.contains(stored)) {
            playerStyles.put(id, stored);
            return stored;
        }

        // 3) default
        return (defaultStyle != null && !defaultStyle.isEmpty()) ? defaultStyle : "old_english";
    }

    public boolean setPlayerStyle(Player player, String style) {
        if (player == null || style == null) return false;

        style = normalizeStyleId(style);
        if (style.isEmpty() || !availableStyles.contains(style)) return false;

        UUID id = player.getUniqueId();
        playerStyles.put(id, style);

        // ✅ Persist
        plugin.getConfig().set(PLAYER_STYLE_PATH + "." + id, style);

        // Save async (disk IO)
        try {
            plugin.runGlobalAsync(plugin::saveConfig);
        } catch (Throwable ignored) {}

        return true;
    }

    /* --------------------------------------------------------
     * Internal resolution helpers
     * -------------------------------------------------------- */

    private String resolveStyle(CommandSender sender) {
        if (sender instanceof Player p) {
            return getPlayerStyle(p);
        }
        return (defaultStyle != null && !defaultStyle.isEmpty()) ? defaultStyle : "old_english";
    }

    private List<String> keyCandidates(String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(key);

        if (key.indexOf('-') >= 0) out.add(key.replace('-', '_'));
        if (key.indexOf('_') >= 0) out.add(key.replace('_', '-'));

        out.add(key.replace('-', '_').replaceAll("__+", "_"));
        out.add(key.replace('_', '-').replaceAll("--+", "-"));

        return new ArrayList<>(out);
    }

    private String resolve(String style, String key) {
        if (key == null || key.isEmpty()) return "";

        for (String k : keyCandidates(key)) {
            if (overridesBundle != null && overridesBundle.contains(k)) {
                return overridesBundle.getString(k, k);
            }
        }

        for (String k : keyCandidates(key)) {
            if (style != null) {
                YamlConfiguration styleCfg = styleBundles.get(style);
                if (styleCfg != null && styleCfg.contains(k)) {
                    return styleCfg.getString(k, k);
                }
            }
        }

        for (String k : keyCandidates(key)) {
            if (coreBundle != null && coreBundle.contains(k)) {
                return coreBundle.getString(k, k);
            }
        }

        for (String k : keyCandidates(key)) {
            if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
                YamlConfiguration fbCfg = styleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) {
                    return fbCfg.getString(k, k);
                }
            }
        }

        return key;
    }

    private List<String> resolveList(String style, String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();

        List<String> result;

        for (String k : keyCandidates(key)) {
            if (overridesBundle != null && overridesBundle.contains(k)) {
                result = overridesBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = overridesBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

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

        for (String k : keyCandidates(key)) {
            if (coreBundle != null && coreBundle.contains(k)) {
                result = coreBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = coreBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

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
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) return input;

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String rawKey = e.getKey();
            if (rawKey == null || rawKey.isEmpty()) continue;

            String value = (e.getValue() == null) ? "" : e.getValue();

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
