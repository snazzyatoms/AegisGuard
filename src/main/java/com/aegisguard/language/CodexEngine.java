package com.aegisguard.language;

import com.aegisguard.AegisGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * CodexEngine (AegisGuard v1.2.4+)
 *
 * Goal:
 * - ✅ Read ALL translations primarily from: plugins/AegisGuard/<primaryFolder>/
 *   Example (recommended):
 *     plugins/AegisGuard/lang/<style>/guis.yml
 *     plugins/AegisGuard/lang/<style>/system.yml
 *     plugins/AegisGuard/lang/<style>/upgrades.yml
 *     plugins/AegisGuard/lang/<style>/expansions.yml
 *
 * - ✅ Use jar resources ONLY as "seed defaults" on first install (extract_defaults=true):
 *     src/main/resources/lang/<style>/guis.yml  -> extracted to plugins/AegisGuard/lang/<style>/guis.yml
 *
 * - ✅ Use /codex as FALLBACK only (legacy single-file or split bundles),
 *   so servers can survive missing keys / missing lang packs.
 *
 * Notes:
 * - codex.yml is OPTIONAL in lang mode. If missing, we build settings from config.yml.
 * - codex.yml is still supported in fallback codex folder for legacy file_map mode.
 */
public class CodexEngine {

    private static final String PLAYER_STYLE_PATH = "localization.player_styles";

    private final AegisGuard plugin;

    // Primary language folder (config: localization.folder)
    private String primaryFolderName = "lang";

    // Fallback folder (optional, default "codex")
    private String fallbackFolderName = "codex";

    // Extract defaults from jar resources
    private boolean extractDefaults = true;

    private String defaultStyle;
    private String fallbackStyle;

    // Preserve ordering
    private final List<String> availableStyles = new ArrayList<>();

    // Primary per-style merged bundle map (lang)
    private final Map<String, YamlConfiguration> primaryStyleBundles = new HashMap<>();

    // Fallback per-style merged bundle map (codex)
    private final Map<String, YamlConfiguration> fallbackStyleBundles = new HashMap<>();

    // Primary/global bundles
    private YamlConfiguration primaryCoreBundle = new YamlConfiguration();
    private YamlConfiguration primaryOverridesBundle = new YamlConfiguration();

    // Fallback/global bundles
    private YamlConfiguration fallbackCoreBundle = new YamlConfiguration();
    private YamlConfiguration fallbackOverridesBundle = new YamlConfiguration();

    /** Per-player style cache (persisted in config.yml). */
    private final Map<UUID, String> playerStyles = new HashMap<>();

    public CodexEngine(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reload language packs.
     */
    public void reload() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();
        }

        // Read config
        this.primaryFolderName = nvl(plugin.getConfig().getString("localization.folder"), "lang").trim();
        this.fallbackFolderName = nvl(plugin.getConfig().getString("localization.fallback_folder"), "codex").trim();
        this.extractDefaults = plugin.getConfig().getBoolean("localization.extract_defaults", true);

        File primaryDir = new File(dataFolder, primaryFolderName);
        if (!primaryDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            primaryDir.mkdirs();
        }

        File fallbackDir = new File(dataFolder, fallbackFolderName);
        if (!fallbackDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            fallbackDir.mkdirs();
        }

        // Bundles list: prefer config.yml, else defaults
        List<String> bundles = plugin.getConfig().getStringList("localization.bundles");
        if (bundles == null || bundles.isEmpty()) {
            bundles = Arrays.asList("guis.yml", "system.yml", "upgrades.yml", "expansions.yml");
        }

        // ✅ IMPORTANT FIX:
        // Seed fallback root files FIRST (codex.yml + core.yml + legacy style ymls),
        // so fallbackIndex/fallbackCoreBundle can actually load on first boot.
        if (extractDefaults) {
            seedFallbackRootFilesEarly();
            // Optional: seed lang-level global files if you ever ship them in /lang/
            maybeExtract(primaryFolderName + "/core.yml");
            maybeExtract(primaryFolderName + "/overrides.yml");
            maybeExtract(primaryFolderName + "/codex.yml");
        }

        // Load optional index files
        YamlConfiguration primaryIndex = loadYamlIfExists(new File(primaryDir, "codex.yml"));
        YamlConfiguration fallbackIndex = loadYamlIfExists(new File(fallbackDir, "codex.yml"));

        // Styles list precedence:
        //  1) config.yml localization.available_languages
        //  2) auto-detect installed styles in primary folder (lang)
        //  3) primary codex.yml available_styles (if present)
        //  4) fallback codex.yml available_styles (if present)
        //  5) hard fallback list
        List<String> fromConfig = plugin.getConfig().getStringList("localization.available_languages");
        List<String> detected = detectInstalledStyles(primaryDir, bundles);
        List<String> fromPrimaryIndex = (primaryIndex == null) ? Collections.emptyList() : primaryIndex.getStringList("available_styles");
        List<String> fromFallbackIndex = (fallbackIndex == null) ? Collections.emptyList() : fallbackIndex.getStringList("available_styles");

        availableStyles.clear();
        if (fromConfig != null && !fromConfig.isEmpty()) {
            availableStyles.addAll(fromConfig);
        } else if (detected != null && !detected.isEmpty()) {
            availableStyles.addAll(detected);
        } else if (fromPrimaryIndex != null && !fromPrimaryIndex.isEmpty()) {
            availableStyles.addAll(fromPrimaryIndex);
        } else if (fromFallbackIndex != null && !fromFallbackIndex.isEmpty()) {
            availableStyles.addAll(fromFallbackIndex);
        } else {
            availableStyles.addAll(Arrays.asList("old_english", "hybrid_english", "modern_english", "spanish_mx", "spanish_ar"));
        }

        normalizeAvailableStyles();

        // Default + fallback language (support BOTH sets of config keys)
        String cfgDefault = firstNonBlank(
                plugin.getConfig().getString("localization.default_language"),
                plugin.getConfig().getString("localization.default_style"),
                primaryIndex == null ? null : primaryIndex.getString("default_style"),
                fallbackIndex == null ? null : fallbackIndex.getString("default_style"),
                "old_english"
        );

        String cfgFallback = firstNonBlank(
                plugin.getConfig().getString("localization.fallback_language"),
                plugin.getConfig().getString("localization.fallback_style"),
                primaryIndex == null ? null : primaryIndex.getString("fallback_style"),
                fallbackIndex == null ? null : fallbackIndex.getString("fallback_style"),
                "modern_english"
        );

        this.defaultStyle = normalizeStyleId(cfgDefault);
        this.fallbackStyle = normalizeStyleId(cfgFallback);

        if (!availableStyles.contains(defaultStyle)) {
            defaultStyle = availableStyles.isEmpty() ? "old_english" : availableStyles.get(0);
        }
        if (!availableStyles.contains(fallbackStyle)) {
            fallbackStyle = defaultStyle;
        }

        // Load core/overrides:
        // Prefer primary folder versions, else fallback folder versions.
        this.primaryCoreBundle = loadPrimaryOrSeed(primaryDir, "core.yml");
        this.primaryOverridesBundle = loadPrimaryOrSeed(primaryDir, "overrides.yml");

        this.fallbackCoreBundle = loadFallbackOrSeed(fallbackDir, "core.yml");
        this.fallbackOverridesBundle = loadFallbackOrSeed(fallbackDir, "overrides.yml");

        // ---------------------------
        // Load PRIMARY (lang) bundles
        // ---------------------------
        primaryStyleBundles.clear();

        for (String style : availableStyles) {
            YamlConfiguration merged = new YamlConfiguration();
            File styleDir = new File(primaryDir, style);

            // Seed missing defaults from jar resources/lang/<style>/<bundle>.yml
            if (extractDefaults) {
                for (String bundleFile : bundles) {
                    maybeExtract(primaryFolderName + "/" + style + "/" + bundleFile);
                }
            }

            for (String bundleFile : bundles) {
                File f = new File(styleDir, bundleFile);
                if (!f.exists()) continue;

                YamlConfiguration partRaw = loadYaml(f);
                YamlConfiguration part = normalizeStyleYaml(style, partRaw);
                mergeYamlLeaves(merged, part);
            }

            primaryStyleBundles.put(style, merged);
        }

        // ------------------------------
        // Load FALLBACK (codex) bundles
        // ------------------------------
        fallbackStyleBundles.clear();
        loadFallbackCodexBundles(fallbackDir, fallbackIndex, bundles);

        boolean primaryHasAnyKeys = primaryStyleBundles.values().stream()
                .anyMatch(cfg -> cfg != null && !cfg.getKeys(true).isEmpty());

        boolean fallbackHasAnyKeys = fallbackStyleBundles.values().stream()
                .anyMatch(cfg -> cfg != null && !cfg.getKeys(true).isEmpty());

        plugin.getLogger().info("[Codex] Primary=" + primaryFolderName + " (bundles) styles=" + String.join(", ", availableStyles)
                + " | keys=" + (primaryHasAnyKeys ? "yes" : "no")
                + " | Fallback=" + fallbackFolderName + " keys=" + (fallbackHasAnyKeys ? "yes" : "no"));

        pruneInvalidPlayerStyles();
    }

    private void seedFallbackRootFilesEarly() {
        // Optional config key (recommended):
        // localization.fallback_root_files:
        //  - codex.yml
        //  - core.yml
        //  - old_english.yml
        //  - hybrid_english.yml
        //  - modern_english.yml
        //  - spanish_mx.yml
        //  - spanish_ar.yml
        List<String> roots = plugin.getConfig().getStringList("localization.fallback_root_files");
        if (roots == null || roots.isEmpty()) {
            roots = Arrays.asList(
                    "codex.yml",
                    "core.yml",
                    "overrides.yml",
                    "old_english.yml",
                    "hybrid_english.yml",
                    "modern_english.yml",
                    "spanish_mx.yml",
                    "spanish_ar.yml"
            );
        }

        for (String f : roots) {
            if (f == null || f.isBlank()) continue;
            maybeExtract(fallbackFolderName + "/" + f.trim());
        }
    }

    private void loadFallbackCodexBundles(File fallbackDir, YamlConfiguration fallbackIndex, List<String> bundles) {
        if (fallbackDir == null) return;

        YamlConfiguration idx = fallbackIndex;
        if (idx == null) idx = loadYamlIfExists(new File(fallbackDir, "codex.yml"));
        if (idx == null) return;

        // mode: auto/split/legacy
        String mode = nvl(idx.getString("mode"), "auto").trim().toLowerCase(Locale.ROOT);
        boolean forceLegacy = mode.equals("legacy");
        boolean forceSplit = mode.equals("split");

        boolean splitDetected = false;
        if (!forceLegacy) {
            for (String style : availableStyles) {
                if (hasAnySplitBundle(fallbackDir, style, bundles)) {
                    splitDetected = true;
                    break;
                }
            }
        }

        boolean useSplit = forceSplit || (!forceLegacy && splitDetected);

        if (useSplit) {
            for (String style : availableStyles) {
                YamlConfiguration merged = new YamlConfiguration();
                File styleDir = new File(fallbackDir, style);

                // Optional: seed codex/<style>/<bundle>.yml only if you ship them
                if (extractDefaults) {
                    for (String bundleFile : bundles) {
                        maybeExtract(fallbackFolderName + "/" + style + "/" + bundleFile);
                    }
                }

                for (String bundleFile : bundles) {
                    File f = new File(styleDir, bundleFile);
                    if (!f.exists()) continue;

                    YamlConfiguration partRaw = loadYaml(f);
                    YamlConfiguration part = normalizeStyleYaml(style, partRaw);
                    mergeYamlLeaves(merged, part);
                }

                fallbackStyleBundles.put(style, merged);
            }
        } else {
            // Legacy file_map mode
            for (String style : availableStyles) {
                String styleFileName = idx.getString("file_map." + style);
                if (styleFileName == null || styleFileName.isBlank()) continue;

                if (extractDefaults) {
                    maybeExtract(fallbackFolderName + "/" + styleFileName);
                }

                File f = new File(fallbackDir, styleFileName);
                if (!f.exists()) continue;

                YamlConfiguration raw = loadYaml(f);
                YamlConfiguration normalized = normalizeStyleYaml(style, raw);
                fallbackStyleBundles.put(style, normalized);
            }
        }
    }

    // ----------------------------
    // Public API
    // ----------------------------

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

    public List<String> trList(CommandSender sender, String key) {
        return trList(sender, key, Collections.emptyMap());
    }

    public List<String> trList(CommandSender sender, String key, Map<String, String> placeholders) {
        String style = resolveStyle(sender);
        List<String> rawList = resolveList(style, key);
        if (rawList.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(rawList.size());
        for (String line : rawList) out.add(applyPlaceholders(line, placeholders));
        return out;
    }

    public List<String> trList(Player player, String key) {
        return trList((CommandSender) player, key);
    }

    public List<String> trList(Player player, String key, Map<String, String> placeholders) {
        return trList((CommandSender) player, key, placeholders);
    }

    /* --------------------------------------------------------
     * ✅ Compat aliases (bring back old GUI calls)
     * -------------------------------------------------------- */

    public List<String> list(Player player, String key) {
        return trList(player, key);
    }

    public List<String> list(Player player, String key, Map<String, String> placeholders) {
        return trList((CommandSender) player, key, placeholders);
    }

    public List<String> list(CommandSender sender, String key) {
        return trList(sender, key);
    }

    public List<String> list(CommandSender sender, String key, Map<String, String> placeholders) {
        return trList(sender, key, placeholders);
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
        if (index == -1 || index >= availableStyles.size() - 1) return availableStyles.get(0);
        return availableStyles.get(index + 1);
    }

    // ----------------------------
    // Per-player styles (config.yml)
    // ----------------------------

    public String getPlayerStyle(Player player) {
        if (player == null) return safeDefaultStyle();

        UUID id = player.getUniqueId();

        // 1) cached
        String cached = playerStyles.get(id);
        if (cached != null && availableStyles.contains(cached)) return cached;

        // 2) persisted
        String stored = plugin.getConfig().getString(PLAYER_STYLE_PATH + "." + id, null);
        stored = normalizeStyleId(stored);
        if (!stored.isEmpty() && availableStyles.contains(stored)) {
            playerStyles.put(id, stored);
            return stored;
        }

        // 3) default
        return safeDefaultStyle();
    }

    public boolean setPlayerStyle(Player player, String style) {
        if (player == null || style == null) return false;

        style = normalizeStyleId(style);
        if (style.isEmpty() || !availableStyles.contains(style)) return false;

        UUID id = player.getUniqueId();
        playerStyles.put(id, style);

        plugin.getConfig().set(PLAYER_STYLE_PATH + "." + id, style);

        try {
            plugin.runGlobalAsync(plugin::saveConfig);
        } catch (Throwable ignored) {}

        return true;
    }

    // ----------------------------
    // Internal resolution
    // ----------------------------

    private String resolveStyle(CommandSender sender) {
        if (sender instanceof Player p) return getPlayerStyle(p);
        return safeDefaultStyle();
    }

    private String safeDefaultStyle() {
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

        // 1) primary overrides
        for (String k : keyCandidates(key)) {
            if (primaryOverridesBundle != null && primaryOverridesBundle.contains(k)) {
                return primaryOverridesBundle.getString(k, k);
            }
        }

        // 2) primary style
        for (String k : keyCandidates(key)) {
            YamlConfiguration styleCfg = primaryStyleBundles.get(style);
            if (styleCfg != null && styleCfg.contains(k)) return styleCfg.getString(k, k);
        }

        // 3) primary core
        for (String k : keyCandidates(key)) {
            if (primaryCoreBundle != null && primaryCoreBundle.contains(k)) {
                return primaryCoreBundle.getString(k, k);
            }
        }

        // 4) primary fallback style
        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = primaryStyleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) return fbCfg.getString(k, k);
            }
        }

        // 5) fallback overrides (codex)
        for (String k : keyCandidates(key)) {
            if (fallbackOverridesBundle != null && fallbackOverridesBundle.contains(k)) {
                return fallbackOverridesBundle.getString(k, k);
            }
        }

        // 6) fallback style (codex)
        for (String k : keyCandidates(key)) {
            YamlConfiguration fbStyleCfg = fallbackStyleBundles.get(style);
            if (fbStyleCfg != null && fbStyleCfg.contains(k)) return fbStyleCfg.getString(k, k);
        }

        // 7) fallback fallback-style (codex)
        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = fallbackStyleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) return fbCfg.getString(k, k);
            }
        }

        // 8) fallback core (codex)
        for (String k : keyCandidates(key)) {
            if (fallbackCoreBundle != null && fallbackCoreBundle.contains(k)) {
                return fallbackCoreBundle.getString(k, k);
            }
        }

        return key;
    }

    private List<String> resolveList(String style, String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();

        List<String> result;

        // 1) primary overrides
        for (String k : keyCandidates(key)) {
            if (primaryOverridesBundle != null && primaryOverridesBundle.contains(k)) {
                result = primaryOverridesBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = primaryOverridesBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 2) primary style
        for (String k : keyCandidates(key)) {
            YamlConfiguration styleCfg = primaryStyleBundles.get(style);
            if (styleCfg != null && styleCfg.contains(k)) {
                result = styleCfg.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = styleCfg.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 3) primary core
        for (String k : keyCandidates(key)) {
            if (primaryCoreBundle != null && primaryCoreBundle.contains(k)) {
                result = primaryCoreBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = primaryCoreBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 4) primary fallback style
        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = primaryStyleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) {
                    result = fbCfg.getStringList(k);
                    if (!result.isEmpty()) return result;

                    String single = fbCfg.getString(k);
                    if (single != null) return Collections.singletonList(single);
                }
            }
        }

        // 5) fallback overrides
        for (String k : keyCandidates(key)) {
            if (fallbackOverridesBundle != null && fallbackOverridesBundle.contains(k)) {
                result = fallbackOverridesBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = fallbackOverridesBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 6) fallback style
        for (String k : keyCandidates(key)) {
            YamlConfiguration fbStyleCfg = fallbackStyleBundles.get(style);
            if (fbStyleCfg != null && fbStyleCfg.contains(k)) {
                result = fbStyleCfg.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = fbStyleCfg.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 7) fallback fallback-style
        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = fallbackStyleBundles.get(fallbackStyle);
                if (fbCfg != null && fbCfg.contains(k)) {
                    result = fbCfg.getStringList(k);
                    if (!result.isEmpty()) return result;

                    String single = fbCfg.getString(k);
                    if (single != null) return Collections.singletonList(single);
                }
            }
        }

        // 8) fallback core
        for (String k : keyCandidates(key)) {
            if (fallbackCoreBundle != null && fallbackCoreBundle.contains(k)) {
                result = fallbackCoreBundle.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = fallbackCoreBundle.getString(k);
                if (single != null) return Collections.singletonList(single);
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

    // ----------------------------
    // Helpers
    // ----------------------------

    private void normalizeAvailableStyles() {
        for (int i = 0; i < availableStyles.size(); i++) {
            availableStyles.set(i, normalizeStyleId(availableStyles.get(i)));
        }
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

    private List<String> detectInstalledStyles(File primaryDir, List<String> bundles) {
        if (primaryDir == null || !primaryDir.exists()) return Collections.emptyList();

        File[] dirs = primaryDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return Collections.emptyList();

        List<String> found = new ArrayList<>();
        for (File d : dirs) {
            String name = d.getName();
            if (name == null || name.isBlank()) continue;

            boolean ok = false;
            for (String b : bundles) {
                if (new File(d, b).exists()) { ok = true; break; }
            }
            if (ok) found.add(normalizeStyleId(name));
        }

        Collections.sort(found);
        return found;
    }

    /**
     * Checks if a resource exists inside the jar.
     */
    private boolean hasBundledResource(String jarPath) {
        if (jarPath == null || jarPath.isBlank()) return false;
        try (InputStream in = plugin.getResource(jarPath)) {
            return in != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void maybeExtract(String resourcePath) {
        if (!extractDefaults) return;
        if (resourcePath == null || resourcePath.isBlank()) return;

        // ✅ No exceptions, no noise: if jar doesn't contain it, skip.
        if (!hasBundledResource(resourcePath)) return;

        File target = new File(plugin.getDataFolder(), resourcePath.replace("/", File.separator));
        if (target.exists()) return;

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        try {
            plugin.saveResource(resourcePath, false);
        } catch (Throwable ignored) {
            // Should be extremely rare now.
        }
    }

    private YamlConfiguration loadYamlIfExists(File f) {
        if (f == null || !f.exists()) return null;
        return YamlConfiguration.loadConfiguration(f);
    }

    private YamlConfiguration loadYaml(File f) {
        return YamlConfiguration.loadConfiguration(f);
    }

    private YamlConfiguration loadPrimaryOrSeed(File primaryDir, String fileName) {
        File f = new File(primaryDir, fileName);
        if (!f.exists() && extractDefaults) {
            maybeExtract(primaryFolderName + "/" + fileName);
        }
        YamlConfiguration cfg = loadYamlIfExists(f);
        return (cfg == null) ? new YamlConfiguration() : cfg;
    }

    private YamlConfiguration loadFallbackOrSeed(File fallbackDir, String fileName) {
        File f = new File(fallbackDir, fileName);
        if (!f.exists() && extractDefaults) {
            maybeExtract(fallbackFolderName + "/" + fileName);
        }
        YamlConfiguration cfg = loadYamlIfExists(f);
        return (cfg == null) ? new YamlConfiguration() : cfg;
    }

    private YamlConfiguration normalizeStyleYaml(String style, YamlConfiguration cfg) {
        if (cfg == null) return new YamlConfiguration();

        Set<String> top = cfg.getKeys(false);
        if (top == null || top.isEmpty()) return cfg;

        // If wrapped under style key
        if (style != null && cfg.isConfigurationSection(style)) {
            ConfigurationSection sec = cfg.getConfigurationSection(style);
            return flattenSectionLeaves(sec);
        }

        // If wrapped under a single top-level section
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

    private static String nvl(String s, String def) {
        return (s == null) ? def : s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }
}
