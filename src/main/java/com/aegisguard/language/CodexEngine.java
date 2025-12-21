package com.aegisguard.language;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 * Upgrades preserved + added:
 * - ✅ Atomic reload swap (prevents half-loaded state during refresh)
 * - ✅ style_order support (cycle order separate from available styles)
 * - ✅ Player style path compat (reads/writes both legacy + canonical)
 */
public class CodexEngine {

    // Legacy path you already used
    private static final String PLAYER_STYLE_PATH = "localization.player_styles";

    // Canonical path used by newer systems (compat)
    private static final String CANON_PLAYER_STYLE_FMT = "player_prefs.%s.style";

    /** Supports hex colors like &#12ABEF */
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private final AegisGuard plugin;

    // Primary language folder (config: localization.folder)
    private String primaryFolderName = "lang";

    // Fallback folder (optional, default "codex")
    private String fallbackFolderName = "codex";

    // Extract defaults from jar resources
    private boolean extractDefaults = true;

    private String defaultStyle;
    private String fallbackStyle;

    /**
     * Styles that exist / are allowed on this server (used for validation + loading).
     * NOTE: this list is swapped atomically on reload.
     */
    private volatile List<String> availableStyles = Collections.emptyList();

    /**
     * Optional style cycling order. If empty, falls back to availableStyles.
     * NOTE: swapped atomically on reload.
     */
    private volatile List<String> styleOrder = Collections.emptyList();

    // Primary per-style merged bundle map (lang) - swapped atomically
    private volatile Map<String, YamlConfiguration> primaryStyleBundles = Collections.emptyMap();

    // Fallback per-style merged bundle map (codex) - swapped atomically
    private volatile Map<String, YamlConfiguration> fallbackStyleBundles = Collections.emptyMap();

    // Primary/global bundles - swapped atomically
    private volatile YamlConfiguration primaryCoreBundle = new YamlConfiguration();
    private volatile YamlConfiguration primaryOverridesBundle = new YamlConfiguration();

    // Fallback/global bundles - swapped atomically
    private volatile YamlConfiguration fallbackCoreBundle = new YamlConfiguration();
    private volatile YamlConfiguration fallbackOverridesBundle = new YamlConfiguration();

    /** Per-player style cache (persisted in config.yml). */
    private final Map<UUID, String> playerStyles = new ConcurrentHashMap<>();

    public CodexEngine(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reload language packs (atomic swap to avoid half-loaded state).
     */
    public void reload() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();
        }

        // ----------------------------
        // Read config (locals first)
        // ----------------------------
        String newPrimaryFolder = nvl(plugin.getConfig().getString("localization.folder"), "lang").trim();
        String newFallbackFolder = nvl(plugin.getConfig().getString("localization.fallback_folder"), "codex").trim();
        boolean newExtractDefaults = plugin.getConfig().getBoolean("localization.extract_defaults", true);

        File primaryDir = new File(dataFolder, newPrimaryFolder);
        if (!primaryDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            primaryDir.mkdirs();
        }

        File fallbackDir = new File(dataFolder, newFallbackFolder);
        if (!fallbackDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            fallbackDir.mkdirs();
        }

        // Bundles list: prefer config.yml, else defaults
        List<String> bundles = plugin.getConfig().getStringList("localization.bundles");
        if (bundles == null || bundles.isEmpty()) {
            bundles = Arrays.asList("guis.yml", "system.yml", "upgrades.yml", "expansions.yml");
        }

        // Seed fallback root files FIRST so fallback index/core exist on first boot
        if (newExtractDefaults) {
            // set temporaries so maybeExtract uses correct folder values
            this.primaryFolderName = newPrimaryFolder;
            this.fallbackFolderName = newFallbackFolder;
            this.extractDefaults = true;

            seedFallbackRootFilesEarly();
            maybeExtract(newPrimaryFolder + "/core.yml");
            maybeExtract(newPrimaryFolder + "/overrides.yml");
            maybeExtract(newPrimaryFolder + "/codex.yml");
        }

        // Load optional index files
        YamlConfiguration primaryIndex = loadYamlIfExists(new File(primaryDir, "codex.yml"));
        YamlConfiguration fallbackIndex = loadYamlIfExists(new File(fallbackDir, "codex.yml"));

        // ----------------------------
        // Styles list precedence
        // ----------------------------
        List<String> fromConfig = plugin.getConfig().getStringList("localization.available_languages");
        List<String> detected = detectInstalledStyles(primaryDir, bundles);

        List<String> fromPrimaryIndex = (primaryIndex == null) ? Collections.emptyList() : primaryIndex.getStringList("available_styles");
        List<String> fromFallbackIndex = (fallbackIndex == null) ? Collections.emptyList() : fallbackIndex.getStringList("available_styles");

        List<String> newAvailable = new ArrayList<>();
        if (fromConfig != null && !fromConfig.isEmpty()) {
            newAvailable.addAll(fromConfig);
        } else if (detected != null && !detected.isEmpty()) {
            newAvailable.addAll(detected);
        } else if (fromPrimaryIndex != null && !fromPrimaryIndex.isEmpty()) {
            newAvailable.addAll(fromPrimaryIndex);
        } else if (fromFallbackIndex != null && !fromFallbackIndex.isEmpty()) {
            newAvailable.addAll(fromFallbackIndex);
        } else {
            newAvailable.addAll(Arrays.asList("old_english", "hybrid_english", "modern_english", "spanish_mx", "spanish_ar"));
        }

        newAvailable = normalizeAndDedupStyles(newAvailable);

        // ----------------------------
        // Default + fallback language
        // ----------------------------
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

        String newDefaultStyle = normalizeStyleId(cfgDefault);
        String newFallbackStyle = normalizeStyleId(cfgFallback);

        if (!newAvailable.contains(newDefaultStyle)) {
            newDefaultStyle = newAvailable.isEmpty() ? "old_english" : newAvailable.get(0);
        }
        if (!newAvailable.contains(newFallbackStyle)) {
            newFallbackStyle = newDefaultStyle;
        }

        // ----------------------------
        // style_order (cycle order)
        // ----------------------------
        List<String> orderCfg = plugin.getConfig().getStringList("localization.style_order");
        List<String> orderPrimary = primaryIndex == null ? Collections.emptyList() : readStyleOrder(primaryIndex);
        List<String> orderFallback = fallbackIndex == null ? Collections.emptyList() : readStyleOrder(fallbackIndex);

        List<String> newOrder = null;
        if (orderCfg != null && !orderCfg.isEmpty()) newOrder = orderCfg;
        else if (orderPrimary != null && !orderPrimary.isEmpty()) newOrder = orderPrimary;
        else if (orderFallback != null && !orderFallback.isEmpty()) newOrder = orderFallback;

        if (newOrder == null) newOrder = Collections.emptyList();

        newOrder = normalizeAndDedupStyles(newOrder);

        // keep only known styles
        if (!newOrder.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String s : newOrder) if (newAvailable.contains(s)) filtered.add(s);
            newOrder = filtered;
        }

        if (newOrder.isEmpty()) {
            newOrder = new ArrayList<>(newAvailable);
        }

        // ----------------------------
        // Load core/overrides (locals)
        // Prefer primary folder versions, else fallback folder versions.
        // ----------------------------
        // Temporarily set these for helpers (which reference fields)
        this.primaryFolderName = newPrimaryFolder;
        this.fallbackFolderName = newFallbackFolder;
        this.extractDefaults = newExtractDefaults;

        YamlConfiguration newPrimaryCore = loadPrimaryOrSeed(primaryDir, "core.yml");
        YamlConfiguration newPrimaryOverrides = loadPrimaryOrSeed(primaryDir, "overrides.yml");

        YamlConfiguration newFallbackCore = loadFallbackOrSeed(fallbackDir, "core.yml");
        YamlConfiguration newFallbackOverrides = loadFallbackOrSeed(fallbackDir, "overrides.yml");

        // ---------------------------
        // Load PRIMARY (lang) bundles
        // ---------------------------
        Map<String, YamlConfiguration> newPrimaryStyleBundles = new HashMap<>();

        for (String style : newAvailable) {
            YamlConfiguration merged = new YamlConfiguration();
            File styleDir = new File(primaryDir, style);

            // Seed missing defaults from jar resources/lang/<style>/<bundle>.yml
            if (newExtractDefaults) {
                for (String bundleFile : bundles) {
                    maybeExtract(newPrimaryFolder + "/" + style + "/" + bundleFile);
                }
            }

            for (String bundleFile : bundles) {
                File f = new File(styleDir, bundleFile);
                if (!f.exists()) continue;

                YamlConfiguration partRaw = loadYaml(f);
                YamlConfiguration part = normalizeStyleYaml(style, partRaw);
                mergeYamlLeaves(merged, part);
            }

            newPrimaryStyleBundles.put(style, merged);
        }

        // ------------------------------
        // Load FALLBACK (codex) bundles
        // ------------------------------
        Map<String, YamlConfiguration> newFallbackStyleBundles = new HashMap<>();
        loadFallbackCodexBundlesInto(newFallbackStyleBundles, fallbackDir, fallbackIndex, bundles, newAvailable, newExtractDefaults, newFallbackFolder);

        boolean primaryHasAnyKeys = newPrimaryStyleBundles.values().stream()
                .anyMatch(cfg -> cfg != null && !cfg.getKeys(true).isEmpty());

        boolean fallbackHasAnyKeys = newFallbackStyleBundles.values().stream()
                .anyMatch(cfg -> cfg != null && !cfg.getKeys(true).isEmpty());

        plugin.getLogger().info("[Codex] Primary=" + newPrimaryFolder + " (bundles) styles=" + String.join(", ", newAvailable)
                + " | keys=" + (primaryHasAnyKeys ? "yes" : "no")
                + " | Fallback=" + newFallbackFolder + " keys=" + (fallbackHasAnyKeys ? "yes" : "no"));

        // ----------------------------
        // ✅ ATOMIC SWAP (one shot)
        // ----------------------------
        this.primaryFolderName = newPrimaryFolder;
        this.fallbackFolderName = newFallbackFolder;
        this.extractDefaults = newExtractDefaults;

        this.defaultStyle = newDefaultStyle;
        this.fallbackStyle = newFallbackStyle;

        this.availableStyles = Collections.unmodifiableList(new ArrayList<>(newAvailable));
        this.styleOrder = Collections.unmodifiableList(new ArrayList<>(newOrder));

        this.primaryCoreBundle = newPrimaryCore == null ? new YamlConfiguration() : newPrimaryCore;
        this.primaryOverridesBundle = newPrimaryOverrides == null ? new YamlConfiguration() : newPrimaryOverrides;

        this.fallbackCoreBundle = newFallbackCore == null ? new YamlConfiguration() : newFallbackCore;
        this.fallbackOverridesBundle = newFallbackOverrides == null ? new YamlConfiguration() : newFallbackOverrides;

        this.primaryStyleBundles = Collections.unmodifiableMap(new HashMap<>(newPrimaryStyleBundles));
        this.fallbackStyleBundles = Collections.unmodifiableMap(new HashMap<>(newFallbackStyleBundles));

        pruneInvalidPlayerStyles();
    }

    private void seedFallbackRootFilesEarly() {
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

    private void loadFallbackCodexBundlesInto(
            Map<String, YamlConfiguration> outMap,
            File fallbackDir,
            YamlConfiguration fallbackIndex,
            List<String> bundles,
            List<String> styles,
            boolean extractDefaultsFlag,
            String fallbackFolder
    ) {
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
            for (String style : styles) {
                if (hasAnySplitBundle(fallbackDir, style, bundles)) {
                    splitDetected = true;
                    break;
                }
            }
        }

        boolean useSplit = forceSplit || (!forceLegacy && splitDetected);

        if (useSplit) {
            for (String style : styles) {
                YamlConfiguration merged = new YamlConfiguration();
                File styleDir = new File(fallbackDir, style);

                if (extractDefaultsFlag) {
                    for (String bundleFile : bundles) {
                        maybeExtract(fallbackFolder + "/" + style + "/" + bundleFile);
                    }
                }

                for (String bundleFile : bundles) {
                    File f = new File(styleDir, bundleFile);
                    if (!f.exists()) continue;

                    YamlConfiguration partRaw = loadYaml(f);
                    YamlConfiguration part = normalizeStyleYaml(style, partRaw);
                    mergeYamlLeaves(merged, part);
                }

                outMap.put(style, merged);
            }
        } else {
            // Legacy file_map mode
            for (String style : styles) {
                String styleFileName = idx.getString("file_map." + style);
                if (styleFileName == null || styleFileName.isBlank()) continue;

                if (extractDefaultsFlag) {
                    maybeExtract(fallbackFolder + "/" + styleFileName);
                }

                File f = new File(fallbackDir, styleFileName);
                if (!f.exists()) continue;

                YamlConfiguration raw = loadYaml(f);
                YamlConfiguration normalized = normalizeStyleYaml(style, raw);
                outMap.put(style, normalized);
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
        String out = applyPlaceholders(raw, placeholders);
        return colorize(out);
    }

    public String tr(String key) {
        return tr(key, Collections.emptyMap());
    }

    public String tr(String key, Map<String, String> placeholders) {
        String raw = resolve(defaultStyle, key);
        String out = applyPlaceholders(raw, placeholders);
        return colorize(out);
    }

    public List<String> trList(CommandSender sender, String key) {
        return trList(sender, key, Collections.emptyMap());
    }

    public List<String> trList(CommandSender sender, String key, Map<String, String> placeholders) {
        String style = resolveStyle(sender);
        List<String> rawList = resolveList(style, key);
        if (rawList.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(rawList.size());
        for (String line : rawList) {
            out.add(colorize(applyPlaceholders(line, placeholders)));
        }
        return out;
    }

    public List<String> trList(Player player, String key) {
        return trList((CommandSender) player, key);
    }

    public List<String> trList(Player player, String key, Map<String, String> placeholders) {
        return trList((CommandSender) player, key, placeholders);
    }

    /**
     * ✅ Symmetry: default-style list translators
     */
    public List<String> trList(String key) {
        return trList(key, Collections.emptyMap());
    }

    /**
     * ✅ Symmetry: default-style list translators (placeholders)
     */
    public List<String> trList(String key, Map<String, String> placeholders) {
        List<String> rawList = resolveList(defaultStyle, key);
        if (rawList.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(rawList.size());
        for (String line : rawList) {
            out.add(colorize(applyPlaceholders(line, placeholders)));
        }
        return out;
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
        return availableStyles;
    }

    /**
     * ✅ New: style order (cycle order)
     */
    public List<String> getStyleOrder() {
        return styleOrder;
    }

    public String getNextStyle(String currentStyle) {
        List<String> order = (styleOrder == null || styleOrder.isEmpty()) ? availableStyles : styleOrder;
        if (order == null || order.isEmpty()) return safeDefaultStyle();

        currentStyle = normalizeStyleId(currentStyle);
        int index = order.indexOf(currentStyle);
        if (index == -1 || index >= order.size() - 1) return order.get(0);
        return order.get(index + 1);
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

        // 2) canonical (compat)
        String canon = plugin.getConfig().getString(String.format(CANON_PLAYER_STYLE_FMT, id), null);
        canon = normalizeStyleId(canon);
        if (!canon.isEmpty() && availableStyles.contains(canon)) {
            playerStyles.put(id, canon);
            return canon;
        }

        // 3) legacy path (your original)
        String stored = plugin.getConfig().getString(PLAYER_STYLE_PATH + "." + id, null);
        stored = normalizeStyleId(stored);
        if (!stored.isEmpty() && availableStyles.contains(stored)) {
            playerStyles.put(id, stored);
            return stored;
        }

        // 4) default
        return safeDefaultStyle();
    }

    public boolean setPlayerStyle(Player player, String style) {
        if (player == null || style == null) return false;

        style = normalizeStyleId(style);
        if (style.isEmpty() || !availableStyles.contains(style)) return false;

        UUID id = player.getUniqueId();
        playerStyles.put(id, style);

        // ✅ Write BOTH paths to preserve compatibility forever.
        plugin.getConfig().set(PLAYER_STYLE_PATH + "." + id, style);
        plugin.getConfig().set(String.format(CANON_PLAYER_STYLE_FMT, id), style);

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
        String ds = normalizeStyleId(defaultStyle);
        if (!ds.isEmpty()) return ds;
        return "old_english";
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

        // snapshot references (atomic reload safety)
        YamlConfiguration pOverrides = this.primaryOverridesBundle;
        YamlConfiguration pCore = this.primaryCoreBundle;
        Map<String, YamlConfiguration> pStyles = this.primaryStyleBundles;

        YamlConfiguration fOverrides = this.fallbackOverridesBundle;
        YamlConfiguration fCore = this.fallbackCoreBundle;
        Map<String, YamlConfiguration> fStyles = this.fallbackStyleBundles;

        String fbStyle = this.fallbackStyle;

        // 1) primary overrides
        for (String k : keyCandidates(key)) {
            if (pOverrides != null && pOverrides.contains(k)) {
                return pOverrides.getString(k, k);
            }
        }

        // 2) primary style
        for (String k : keyCandidates(key)) {
            YamlConfiguration styleCfg = (pStyles == null) ? null : pStyles.get(style);
            if (styleCfg != null && styleCfg.contains(k)) return styleCfg.getString(k, k);
        }

        // 3) primary core
        for (String k : keyCandidates(key)) {
            if (pCore != null && pCore.contains(k)) {
                return pCore.getString(k, k);
            }
        }

        // 4) primary fallback style
        if (fbStyle != null && !fbStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = (pStyles == null) ? null : pStyles.get(fbStyle);
                if (fbCfg != null && fbCfg.contains(k)) return fbCfg.getString(k, k);
            }
        }

        // 5) fallback overrides (codex)
        for (String k : keyCandidates(key)) {
            if (fOverrides != null && fOverrides.contains(k)) {
                return fOverrides.getString(k, k);
            }
        }

        // 6) fallback style (codex)
        for (String k : keyCandidates(key)) {
            YamlConfiguration fbStyleCfg = (fStyles == null) ? null : fStyles.get(style);
            if (fbStyleCfg != null && fbStyleCfg.contains(k)) return fbStyleCfg.getString(k, k);
        }

        // 7) fallback fallback-style (codex)
        if (fbStyle != null && !fbStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = (fStyles == null) ? null : fStyles.get(fbStyle);
                if (fbCfg != null && fbCfg.contains(k)) return fbCfg.getString(k, k);
            }
        }

        // 8) fallback core (codex)
        for (String k : keyCandidates(key)) {
            if (fCore != null && fCore.contains(k)) {
                return fCore.getString(k, k);
            }
        }

        return key;
    }

    private List<String> resolveList(String style, String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();

        // snapshot references (atomic reload safety)
        YamlConfiguration pOverrides = this.primaryOverridesBundle;
        YamlConfiguration pCore = this.primaryCoreBundle;
        Map<String, YamlConfiguration> pStyles = this.primaryStyleBundles;

        YamlConfiguration fOverrides = this.fallbackOverridesBundle;
        YamlConfiguration fCore = this.fallbackCoreBundle;
        Map<String, YamlConfiguration> fStyles = this.fallbackStyleBundles;

        String fbStyle = this.fallbackStyle;

        List<String> result;

        // 1) primary overrides
        for (String k : keyCandidates(key)) {
            if (pOverrides != null && pOverrides.contains(k)) {
                result = pOverrides.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = pOverrides.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 2) primary style
        for (String k : keyCandidates(key)) {
            YamlConfiguration styleCfg = (pStyles == null) ? null : pStyles.get(style);
            if (styleCfg != null && styleCfg.contains(k)) {
                result = styleCfg.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = styleCfg.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 3) primary core
        for (String k : keyCandidates(key)) {
            if (pCore != null && pCore.contains(k)) {
                result = pCore.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = pCore.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 4) primary fallback style
        if (fbStyle != null && !fbStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = (pStyles == null) ? null : pStyles.get(fbStyle);
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
            if (fOverrides != null && fOverrides.contains(k)) {
                result = fOverrides.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = fOverrides.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 6) fallback style
        for (String k : keyCandidates(key)) {
            YamlConfiguration fbStyleCfg = (fStyles == null) ? null : fStyles.get(style);
            if (fbStyleCfg != null && fbStyleCfg.contains(k)) {
                result = fbStyleCfg.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = fbStyleCfg.getString(k);
                if (single != null) return Collections.singletonList(single);
            }
        }

        // 7) fallback fallback-style
        if (fbStyle != null && !fbStyle.equalsIgnoreCase(style)) {
            for (String k : keyCandidates(key)) {
                YamlConfiguration fbCfg = (fStyles == null) ? null : fStyles.get(fbStyle);
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
            if (fCore != null && fCore.contains(k)) {
                result = fCore.getStringList(k);
                if (!result.isEmpty()) return result;

                String single = fCore.getString(k);
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

    /**
     * Converts:
     * - &#RRGGBB  -> §x§R§R§G§G§B§B (via Bungee ChatColor)
     * - &a &b etc -> §a §b etc
     */
    private String colorize(String input) {
        if (input == null || input.isEmpty()) return input;

        String out = input;

        // Hex first
        try {
            Matcher m = HEX_PATTERN.matcher(out);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String hex = m.group(1);
                String repl = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
                m.appendReplacement(sb, Matcher.quoteReplacement(repl));
            }
            m.appendTail(sb);
            out = sb.toString();
        } catch (Throwable ignored) {}

        // Standard & codes
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private static List<String> normalizeAndDedupStyles(List<String> styles) {
        if (styles == null || styles.isEmpty()) return new ArrayList<>();

        List<String> normalized = new ArrayList<>(styles.size());
        for (String s : styles) {
            String n = normalizeStyleId(s);
            if (!n.isEmpty()) normalized.add(n);
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<>(normalized);
        return new ArrayList<>(deduped);
    }

    private static String normalizeStyleId(String style) {
        if (style == null) return "";
        return style.trim().toLowerCase(Locale.ROOT);
    }

    private void pruneInvalidPlayerStyles() {
        if (playerStyles.isEmpty()) return;
        List<String> styles = this.availableStyles;
        playerStyles.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || !styles.contains(e.getValue()));
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

        // No exceptions, no noise: if jar doesn't contain it, skip.
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
        } catch (Throwable ignored) {}
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

    private static List<String> readStyleOrder(YamlConfiguration idx) {
        if (idx == null) return Collections.emptyList();

        List<String> order = idx.getStringList("style_order");
        if (order != null && !order.isEmpty()) return order;

        order = idx.getStringList("styles.order");
        if (order != null && !order.isEmpty()) return order;

        order = idx.getStringList("order");
        if (order != null && !order.isEmpty()) return order;

        return Collections.emptyList();
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
