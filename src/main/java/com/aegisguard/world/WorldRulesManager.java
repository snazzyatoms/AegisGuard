package com.aegisguard.world;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.AGConfig; // --- NEW IMPORT ---
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * ==============================================================
 * WorldRulesManager
 * ... (existing comments) ...
 * ==============================================================
 *
 * --- UPGRADE NOTES ---
 * - CRITICAL: reload() no longer calls plugin.reloadConfig(), which
 * bypassed the AGConfig sync. AdminCommand now handles the reload order.
 * - DESIGN FIX: Default rules are now loaded from AGConfig (Single
 * Source of Truth) instead of being hardcoded.
 *
 */
public class WorldRulesManager {

    private final AegisGuard plugin;
    private final Map<String, WorldRuleSet> rules = new HashMap<>();
    private WorldRuleSet defaultRuleSet; // --- NEW ---

    public WorldRulesManager(AegisGuard plugin) {
        this.plugin = plugin;
        load();
    }

    /* -----------------------------
     * Reload configuration
     * ----------------------------- */
    public void reload() {
        // --- CRITICAL FIX ---
        // plugin.reloadConfig(); // <-- REMOVED! This bypasses AGConfig's sync.
        // AdminCommand now calls plugin.cfg().reload() *before* calling this.
        load();
    }

    /* -----------------------------
     * Load world-specific rules
     * ----------------------------- */
    public void load() {
        rules.clear();
        
        // --- NEW ---
        // Create the default ruleset from the central config wrapper
        AGConfig cfg = plugin.cfg();
        this.defaultRuleSet = new WorldRuleSet(
                true, // allowClaims defaults to true
                cfg.pvpProtectionDefault(),
                cfg.noMobsInClaims(),
                cfg.containerProtectionDefault(),
                cfg.petProtectionDefault(),  // Reads from AGConfig
                cfg.farmProtectionDefault()  // Reads from AGConfig
        );
        
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("claims.per_world");
        if (section == null) {
            plugin.getLogger().warning("[AegisGuard] No per-world configuration found. Using defaults.");
            return;
        }

        for (String worldName : section.getKeys(false)) {
            ConfigurationSection worldSec = section.getConfigurationSection(worldName);
// ... (existing code) ...

            // Nested structure support (protections: ...)
            ConfigurationSection prot = worldSec.getConfigurationSection("protections");
            if (prot == null) prot = worldSec; // Fallback to same level

            // --- MODIFIED ---
            // Per-world settings now use the new defaultRuleSet as their fallback
            WorldRuleSet set = new WorldRuleSet(
                    worldSec.getBoolean("allow_claims", defaultRuleSet.allowClaims),
                    prot.getBoolean("pvp", defaultRuleSet.pvp),
                    prot.getBoolean("mobs", defaultRuleSet.mobs),
                    prot.getBoolean("containers", defaultRuleSet.containers),
                    prot.getBoolean("pets", defaultRuleSet.pets),
                    prot.getBoolean("farms", defaultRuleSet.farms)
            );

            rules.put(worldName, set);

// ... (existing logging) ...
        }

        plugin.getLogger().info("[AegisGuard] Loaded " + rules.size() + " per-world rule sets.");
    }

    /* -----------------------------
     * Accessors
     * ----------------------------- */
    private WorldRuleSet getRules(World world) {
        // --- MODIFIED ---
        return rules.getOrDefault(world.getName(), defaultRuleSet);
    }

    public boolean allowClaims(World world) {
// ... (existing code) ...
    }

    public boolean isPvPAllowed(World world) {
// ... (existing code) ...
    }

    public boolean allowMobs(World world) {
// ... (existing code) ...
    }
// ... (existing code) ...
    public boolean allowFarms(World world) {
// ... (existing code) ...
    }

    /**
     * Generic protection lookup for dynamic checks (used by ProtectionManager)
     */
// ... (existing code) ...
    public boolean isProtectionEnabled(World world, String key) {
// ... (existing code) ...
    }

    /* -----------------------------
     * Inner Class: WorldRuleSet
     * ----------------------------- */
    public static class WorldRuleSet {
// ... (existing fields) ...

        public WorldRuleSet(boolean allowClaims, boolean pvp, boolean mobs,
                            boolean containers, boolean pets, boolean farms) {
// ... (existing constructor) ...
        }

        // --- REMOVED ---
        // public static WorldRuleSet defaultRules() { ... }
        // (Defaults are now loaded from AGConfig)
    }
}
