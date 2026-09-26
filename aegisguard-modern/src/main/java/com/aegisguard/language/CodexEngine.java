package com.aegisguard.language;

import com.aegisguard.AegisGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
 */
public class CodexEngine {

    private static final String PLAYER_STYLE_PATH = "localization.player_styles";

    // Supports "&#RRGGBB" hex colors (Paper/Spigot 1.16+ typically)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final AegisGuard plugin;

    // Thread safety: reload vs reads (admin refresh/reload)
    private final ReadWriteLock rw = new ReentrantReadWriteLock();

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

    // Flat leaf maps for O(1) lookups. YamlConfiguration.contains() walks
    // MemorySection on every GUI string and can stall the server thread.
    private Map<String, Object> primaryCoreLeaves = new HashMap<>();
    private Map<String, Object> primaryOverrideLeaves = new HashMap<>();
    private Map<String, Object> fallbackCoreLeaves = new HashMap<>();
    private Map<String, Object> fallbackOverrideLeaves = new HashMap<>();
    private final Map<String, Map<String, Object>> primaryStyleLeaves = new HashMap<>();
    private final Map<String, Map<String, Object>> fallbackStyleLeaves = new HashMap<>();

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
        rw.writeLock().lock();
        try {
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

            // Seed fallback root files FIRST
            if (extractDefaults) {
                seedFallbackRootFilesEarly();
                maybeExtract(primaryFolderName + "/core.yml");
                maybeExtract(primaryFolderName + "/overrides.yml");
                maybeExtract(primaryFolderName + "/codex.yml");
            }

            // Load optional index files
            YamlConfiguration primaryIndex = loadYamlIfExists(new File(primaryDir, "codex.yml"));
            YamlConfiguration fallbackIndex = loadYamlIfExists(new File(fallbackDir, "codex.yml"));

            // Styles list precedence
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
                availableStyles.addAll(Arrays.asList(
                        "old_english", "modern_english", "spanish_mx", "spanish_ar",
                        "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl"));
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

            // Load core/overrides
            this.primaryCoreBundle = loadPrimaryOrSeed(primaryDir, "core.yml");
            this.primaryOverridesBundle = loadPrimaryOrSeed(primaryDir, "overrides.yml");

            this.fallbackCoreBundle = loadFallbackOrSeed(fallbackDir, "core.yml");
            this.fallbackOverridesBundle = loadFallbackOrSeed(fallbackDir, "overrides.yml");

            // Load PRIMARY (lang) bundles
            primaryStyleBundles.clear();

            for (String style : availableStyles) {
                YamlConfiguration merged = new YamlConfiguration();
                File styleDir = new File(primaryDir, style);

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

            // Load FALLBACK (codex) bundles
            fallbackStyleBundles.clear();
            loadFallbackCodexBundles(fallbackDir, fallbackIndex, bundles);
            rebuildLeafMaps();

            boolean primaryHasAnyKeys = primaryStyleLeaves.values().stream()
                    .anyMatch(leaves -> leaves != null && !leaves.isEmpty());

            boolean fallbackHasAnyKeys = fallbackStyleLeaves.values().stream()
                    .anyMatch(leaves -> leaves != null && !leaves.isEmpty());

            plugin.getLogger().info("[Codex] Primary=" + primaryFolderName + " (bundles) styles=" + String.join(", ", availableStyles)
                    + " | keys=" + (primaryHasAnyKeys ? "yes" : "no")
                    + " | Fallback=" + fallbackFolderName + " keys=" + (fallbackHasAnyKeys ? "yes" : "no"));

            pruneInvalidPlayerStyles();
        } finally {
            rw.writeLock().unlock();
        }
    }

    private void seedFallbackRootFilesEarly() {
        List<String> roots = plugin.getConfig().getStringList("localization.fallback_root_files");
        if (roots == null || roots.isEmpty()) {
            roots = Arrays.asList(
                    "codex.yml",
                    "core.yml",
                    "overrides.yml",
                    "old_english.yml",
                    "modern_english.yml",
                    "spanish_mx.yml",
                    "spanish_ar.yml",
                    "portuguese_br.yml",
                    "french_fr.yml",
                    "italian_it.yml",
                    "german_de.yml",
                    "polish_pl.yml"
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
        final String style;
        final String raw;
        rw.readLock().lock();
        try {
            style = resolveStyle(sender);
            raw = resolve(style, key);
        } finally {
            rw.readLock().unlock();
        }
        return colorize(applyPlaceholders(raw, placeholders));
    }

    public String tr(String key) {
        return tr(key, Collections.emptyMap());
    }

    public String tr(String key, Map<String, String> placeholders) {
        final String raw;
        rw.readLock().lock();
        try {
            raw = resolve(defaultStyle, key);
        } finally {
            rw.readLock().unlock();
        }
        return colorize(applyPlaceholders(raw, placeholders));
    }

    /**
     * Translate with an explicit language style without changing or persisting a
     * player's preference. Public-beta onboarding uses this to render the
     * detected-language confirmation before the player accepts that language.
     */
    public String trStyle(String style, String key) {
        return trStyle(style, key, Collections.emptyMap());
    }

    public String trStyle(String style, String key, Map<String, String> placeholders) {
        final String raw;
        rw.readLock().lock();
        try {
            String normalized = normalizeStyleId(style);
            if (!availableStyles.contains(normalized)) normalized = safeDefaultStyle();
            raw = resolve(normalized, key);
        } finally {
            rw.readLock().unlock();
        }
        return colorize(applyPlaceholders(raw, placeholders));
    }

    public List<String> trList(CommandSender sender, String key) {
        return trList(sender, key, Collections.emptyMap());
    }

    public List<String> trList(CommandSender sender, String key, Map<String, String> placeholders) {
        final String style;
        final List<String> rawList;
        rw.readLock().lock();
        try {
            style = resolveStyle(sender);
            rawList = resolveList(style, key);
        } finally {
            rw.readLock().unlock();
        }

        if (rawList == null || rawList.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(rawList.size());
        for (String line : rawList) out.add(colorize(applyPlaceholders(line, placeholders)));
        return out;
    }

    public List<String> trList(Player player, String key) {
        return trList((CommandSender) player, key);
    }

    public List<String> trList(Player player, String key, Map<String, String> placeholders) {
        return trList((CommandSender) player, key, placeholders);
    }

    public List<String> trListStyle(String style, String key) {
        return trListStyle(style, key, Collections.emptyMap());
    }

    public List<String> trListStyle(String style, String key, Map<String, String> placeholders) {
        final List<String> rawList;
        rw.readLock().lock();
        try {
            String normalized = normalizeStyleId(style);
            if (!availableStyles.contains(normalized)) normalized = safeDefaultStyle();
            rawList = resolveList(normalized, key);
        } finally {
            rw.readLock().unlock();
        }
        if (rawList == null || rawList.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(rawList.size());
        for (String line : rawList) out.add(colorize(applyPlaceholders(line, placeholders)));
        return out;
    }

    // Compat aliases
    public List<String> list(Player player, String key) { return trList(player, key); }
    public List<String> list(Player player, String key, Map<String, String> placeholders) { return trList((CommandSender) player, key, placeholders); }
    public List<String> list(CommandSender sender, String key) { return trList(sender, key); }
    public List<String> list(CommandSender sender, String key, Map<String, String> placeholders) { return trList(sender, key, placeholders); }

    public String getDefaultStyle() { return defaultStyle; }
    public String getFallbackStyle() { return fallbackStyle; }

    public List<String> getAvailableStyles() {
        rw.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(availableStyles));
        } finally {
            rw.readLock().unlock();
        }
    }

    public String getNextStyle(String currentStyle) {
        rw.readLock().lock();
        try {
            if (availableStyles.isEmpty()) return defaultStyle;

            currentStyle = normalizeStyleId(currentStyle);
            int index = availableStyles.indexOf(currentStyle);
            if (index == -1 || index >= availableStyles.size() - 1) return availableStyles.get(0);
            return availableStyles.get(index + 1);
        } finally {
            rw.readLock().unlock();
        }
    }

    // ----------------------------
    // Per-player styles (config.yml)
    // ----------------------------

    public String getPlayerStyle(Player player) {
        if (player == null) return safeDefaultStyle();

        UUID id = player.getUniqueId();

        rw.readLock().lock();
        try {
            String cached = playerStyles.get(id);
            if (cached != null && availableStyles.contains(cached)) return cached;
        } finally {
            rw.readLock().unlock();
        }

        String stored = plugin.getConfig().getString(PLAYER_STYLE_PATH + "." + id, null);
        stored = normalizeStyleId(stored);

        rw.writeLock().lock();
        try {
            if (!stored.isEmpty() && availableStyles.contains(stored)) {
                playerStyles.put(id, stored);
                return stored;
            }
        } finally {
            rw.writeLock().unlock();
        }

        return safeDefaultStyle();
    }

    /** Drop a player's cached style on quit; the config value remains the source of truth. */
    public void evictPlayerStyle(UUID id) {
        if (id == null) return;
        rw.writeLock().lock();
        try {
            playerStyles.remove(id);
        } finally {
            rw.writeLock().unlock();
        }
    }

    public boolean setPlayerStyle(Player player, String style) {
        if (player == null || style == null) return false;

        style = normalizeStyleId(style);

        rw.readLock().lock();
        try {
            if (style.isEmpty() || !availableStyles.contains(style)) return false;
        } finally {
            rw.readLock().unlock();
        }

        UUID id = player.getUniqueId();

        rw.writeLock().lock();
        try {
            playerStyles.put(id, style);
        } finally {
            rw.writeLock().unlock();
        }

        plugin.getConfig().set(PLAYER_STYLE_PATH + "." + id, style);

        try {
            // Bukkit's live YamlConfiguration is not safe to serialize while
            // another thread may be reading or mutating it. Keep config saves
            // on the server/global thread (Paper/Folia compatible).
            plugin.runMainGlobal(plugin::saveConfig);
        } catch (Throwable ignored) {}

        return true;
    }

    public boolean hasSavedPlayerStyle(Player player) {
        if (player == null) return false;
        String stored = plugin.getConfig().getString(PLAYER_STYLE_PATH + "." + player.getUniqueId(), null);
        return stored != null && !stored.isBlank();
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

        Object found = lookupStringLeaf(style, key);
        return found == null ? key : String.valueOf(found);
    }

    private List<String> resolveList(String style, String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();
        Object found = lookupAnyLeaf(style, key);
        return listFromLeaf(found);
    }

    private Object lookupStringLeaf(String style, String key) {
        Object found = lookupAnyLeaf(style, key);
        if (found instanceof List<?>) return null;
        return found;
    }

    private Object lookupAnyLeaf(String style, String key) {
        Object found = leaf(primaryOverrideLeaves, key);
        if (found != ABSENT) return found;

        found = leaf(primaryStyleLeaves.get(style), key);
        if (found != ABSENT) return found;

        for (String relatedStyle : relatedLanguageStyles(style)) {
            found = leaf(primaryStyleLeaves.get(relatedStyle), key);
            if (found != ABSENT) return found;
        }

        found = leaf(primaryCoreLeaves, key);
        if (found != ABSENT) return found;

        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            found = leaf(primaryStyleLeaves.get(fallbackStyle), key);
            if (found != ABSENT) return found;
        }

        found = leaf(fallbackOverrideLeaves, key);
        if (found != ABSENT) return found;

        found = leaf(fallbackStyleLeaves.get(style), key);
        if (found != ABSENT) return found;

        if (fallbackStyle != null && !fallbackStyle.equalsIgnoreCase(style)) {
            found = leaf(fallbackStyleLeaves.get(fallbackStyle), key);
            if (found != ABSENT) return found;
        }

        found = leaf(fallbackCoreLeaves, key);
        return found == ABSENT ? null : found;
    }

    private static final Object ABSENT = new Object();

    private Object leaf(Map<String, Object> leaves, String key) {
        if (leaves == null || leaves.isEmpty()) return ABSENT;
        for (String k : keyCandidates(key)) {
            if (leaves.containsKey(k)) return leaves.get(k);
        }
        return ABSENT;
    }

    private List<String> listFromLeaf(Object val) {
        if (val instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) out.add(String.valueOf(item));
            }
            if (!out.isEmpty()) return out;
        }
        if (val != null) return Collections.singletonList(String.valueOf(val));
        return Collections.emptyList();
    }

    private void rebuildLeafMaps() {
        primaryCoreLeaves = toLeaves(primaryCoreBundle);
        primaryOverrideLeaves = toLeaves(primaryOverridesBundle);
        fallbackCoreLeaves = toLeaves(fallbackCoreBundle);
        fallbackOverrideLeaves = toLeaves(fallbackOverridesBundle);

        primaryStyleLeaves.clear();
        for (Map.Entry<String, YamlConfiguration> entry : primaryStyleBundles.entrySet()) {
            primaryStyleLeaves.put(entry.getKey(), toLeaves(entry.getValue()));
        }
        fallbackStyleLeaves.clear();
        for (Map.Entry<String, YamlConfiguration> entry : fallbackStyleBundles.entrySet()) {
            fallbackStyleLeaves.put(entry.getKey(), toLeaves(entry.getValue()));
        }
    }

    private Map<String, Object> toLeaves(YamlConfiguration cfg) {
        Map<String, Object> leaves = new HashMap<>();
        if (cfg == null) return leaves;
        for (String key : cfg.getKeys(false)) {
            Object val = cfg.get(key);
            if (val instanceof ConfigurationSection section) {
                for (String nested : section.getKeys(true)) {
                    Object nestedVal = section.get(nested);
                    if (nestedVal instanceof ConfigurationSection) continue;
                    leaves.put(key + "." + nested, nestedVal);
                }
            } else {
                leaves.put(key, val);
            }
        }
        return leaves;
    }

    private List<String> relatedLanguageStyles(String style) {
        if ("spanish_ar".equalsIgnoreCase(style)) {
            return Collections.singletonList("spanish_mx");
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

    private String colorize(String input) {
        if (input == null || input.isEmpty()) return input;

        // Standard & colors
        String out = org.bukkit.ChatColor.translateAlternateColorCodes('&', input);

        // Hex &#RRGGBB
        Matcher m = HEX_PATTERN.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            String rep;
            try {
                rep = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
            } catch (Throwable t) {
                rep = ""; // if hex unsupported, just strip the token
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
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
