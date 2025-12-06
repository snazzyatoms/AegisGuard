package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LanguageManager {

    private final AegisGuard plugin;
    private FileConfiguration langConfig;
    private File langFile;
    private final Map<String, String> terminology = new HashMap<>();

    public LanguageManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadAllLocales();
    }

    public void loadAllLocales() {
        // Raw value from config (settings.locale)
        String raw = plugin.getConfig().getString("settings.locale", "modern");
        String base = normalizeBaseLocale(raw); // e.g. "modern" -> "en_modern"
        String localeName = base + ".yml";

        File localeDir = new File(plugin.getDataFolder(), "locales");
        if (!localeDir.exists()) {
            localeDir.mkdirs();
        }

        // Target file in plugins/AegisGuard/locales
        this.langFile = new File(localeDir, localeName);

        // 1) Ensure the locale file exists on disk, with safe fallbacks
        if (!langFile.exists()) {
            String resourcePath = "locales/" + localeName;

            if (plugin.getResource(resourcePath) != null) {
                // We have this locale embedded in the JAR
                try {
                    plugin.saveResource(resourcePath, false);
                    plugin.getLogger().info("Extracted embedded locale: " + resourcePath);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Failed to save embedded locale " + resourcePath + ": " + ex.getMessage());
                }
            } else {
                // No matching embedded file – fall back to en_modern.yml
                plugin.getLogger().warning("Locale '" + localeName + "' is not embedded in the JAR. Falling back to 'en_modern.yml'.");

                String fallbackBase = "en_modern";
                String fallbackName = fallbackBase + ".yml";
                File fallbackFile = new File(localeDir, fallbackName);

                if (!fallbackFile.exists()) {
                    String fallbackResource = "locales/" + fallbackName;
                    if (plugin.getResource(fallbackResource) != null) {
                        try {
                            plugin.saveResource(fallbackResource, false);
                            plugin.getLogger().info("Extracted fallback embedded locale: " + fallbackResource);
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("Failed to save fallback locale " + fallbackResource + ": " + ex.getMessage());
                        }
                    } else {
                        // Even the fallback isn't embedded – generate a tiny default file
                        plugin.getLogger().severe("No embedded fallback locale 'locales/" + fallbackName + "' found. Creating minimal default locale file.");
                        createMinimalDefaultLocale(fallbackFile);
                    }
                }

                // Swap to fallback
                this.langFile = fallbackFile;
                localeName = fallbackName;
            }
        }

        // 2) Load configuration from the selected file
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 3) Cache terminology for getTerm()
        terminology.clear();
        if (langConfig != null && langConfig.contains("terminology")) {
            for (String key : langConfig.getConfigurationSection("terminology").getKeys(false)) {
                terminology.put(key, langConfig.getString("terminology." + key));
            }
        }

        plugin.getLogger().info("Loaded language file: " + localeName + " (requested: " + raw + ")");
    }

    /**
     * Normalizes whatever is in settings.locale to one of the real filenames you have:
     *   modern / en_modern(.yml) / english / en  -> en_modern
     *   old / en_old(.yml)                        -> en_old
     *   hybrid / en_hybrid(.yml)                  -> en_hybrid
     *   anything else starting with en_           -> left as-is (without .yml)
     */
    private String normalizeBaseLocale(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "en_modern";
        }

        String base = raw.toLowerCase(Locale.ROOT).trim();

        // Strip ".yml" if user included it
        if (base.endsWith(".yml")) {
            base = base.substring(0, base.length() - 4);
        }

        switch (base) {
            case "modern":
            case "english":
            case "en":
                return "en_modern";
            case "old":
                return "en_old";
            case "hybrid":
                return "en_hybrid";
            default:
                // If they already gave something like "en_modern" or "en_custom"
                if (base.startsWith("en_")) {
                    return base;
                }
                // Last resort: prefix with en_
                return "en_" + base;
        }
    }

    /**
     * Creates a minimal, safe default locale file so the plugin does not crash
     * even if no embedded locales are present.
     */
    private void createMinimalDefaultLocale(File target) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("prefix", "&8[&bAegis&8] ");

        // A couple of safe defaults you can expand later
        yml.set("messages.aegis_loaded", "&aAegisGuard enabled.");
        yml.set("messages.aegis_disabled", "&cAegisGuard disabled.");
        yml.set("terminology.plot", "&aEstate");
        yml.set("terminology.guild", "&bGuild");

        try {
            yml.save(target);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write minimal default locale to " + target.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Core Message Methods ---

    public String getMsg(Player p, String key) {
        if (langConfig == null) return "§c[Lang Error]";
        String msg = lookupKey(key);
        if (msg == null) return "§c[Missing: " + key + "]";

        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (msg.contains("%prefix%")) {
            String prefix = langConfig.getString("prefix", "&8[&bAegis&8] ");
            msg = msg.replace("%prefix%", ChatColor.translateAlternateColorCodes('&', prefix));
        }
        return msg;
    }

    // Overload for placeholders
    public String getMsg(Player p, String key, Map<String, String> placeholders) {
        String msg = getMsg(p, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace(entry.getKey(), entry.getValue());
        }
        return msg;
    }

    public String getMsg(String key) {
        return getMsg(null, key);
    }

    // --- GUI & List Methods ---

    public String getGui(String key) {
        // Looks specifically in the 'gui' section, then falls back to root
        String val = langConfig.getString("gui." + key);
        if (val == null) val = langConfig.getString(key);
        if (val == null) return "§c" + key;
        return ChatColor.translateAlternateColorCodes('&', val);
    }

    public List<String> getMsgList(Player p, String key) {
        List<String> list = langConfig.getStringList("gui." + key);
        if (list.isEmpty()) list = langConfig.getStringList("messages." + key);
        if (list.isEmpty()) list = langConfig.getStringList(key);

        if (list.isEmpty()) {
            List<String> err = new ArrayList<>();
            err.add("§c[Missing List: " + key + "]");
            return err;
        }

        return list.stream()
                .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                .collect(Collectors.toList());
    }

    // --- Utility Methods ---

    public String getTerm(String key) {
        String term = terminology.getOrDefault(key, key);
        return ChatColor.translateAlternateColorCodes('&', term);
    }

    public void sendTitle(Player p, String titleKey, String subtitleKey) {
        if (p == null) return;
        String title = titleKey != null && !titleKey.isEmpty() ? getMsg(p, titleKey) : "";
        String subtitle = subtitleKey != null && !subtitleKey.isEmpty() ? getMsg(p, subtitleKey) : "";
        p.sendTitle(title, subtitle, 10, 70, 20);
    }

    // Stub for player-specific language (not fully implemented yet but required by AdminCommand)
    public void setPlayerLang(Player p, String lang) {
        // In the future, save this to a database or player config
        p.sendMessage(ChatColor.GREEN + "Language set to " + lang + " (Session only).");
    }

    // --- Private Helper ---
    private String lookupKey(String key) {
        String val = langConfig.getString(key);
        if (val == null) val = langConfig.getString("messages." + key);
        if (val == null) val = langConfig.getString("gui." + key);
        return val;
    }
}
