package com.aegisguard.world;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.AGConfig;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * WorldRulesManager
 * -----------------------------------
 * Handles per-world settings for claim creation and protections.
 * Uses AGConfig for defaults and loads overrides from config.yml.
 */
public class WorldRulesManager {

    private final AegisGuard plugin;
    private final Map<String, WorldRuleSet> rules = new HashMap<>();
    private WorldRuleSet defaultRuleSet;

    public WorldRulesManager(AegisGuard plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Reload configuration.
     * Note: AGConfig reload happens in AdminCommand before calling this.
     */
    public void reload() {
        load();
    }

    /**
     * Load world-specific rules.
     */
    public void load() {
        rules.clear();
        
        // Create default ruleset from AGConfig
        AGConfig cfg = plugin.cfg();
        this.defaultRuleSet = new WorldRuleSet(
                true, // allowClaims defaults to true
                cfg.pvpProtectionDefault(),
                cfg.noMobsInClaims(),
                cfg.containerProtectionDefault(),
                cfg.petProtectionDefault(),
                cfg.farmProtectionDefault()
        );
        
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("claims.per_world");
        if (section == null) {
            plugin.getLogger().info("[AegisGuard] No per-world configuration found. Using defaults.");
            return;
        }

        for (String worldName : section.getKeys(false)) {
            ConfigurationSection worldSec = section.getConfigurationSection(worldName);
            if (worldSec == null) continue;

            // Nested structure support (protections: ...)
            ConfigurationSection prot = worldSec.getConfigurationSection("protections");
            if (prot == null) prot = worldSec; // Fallback to same level if flat structure

            WorldRuleSet set = new WorldRuleSet(
                    worldSec.getBoolean("allow_claims", defaultRuleSet.allowClaims),
                    prot.getBoolean("pvp", defaultRuleSet.pvp),
                    prot.getBoolean("mobs", defaultRuleSet.mobs),
                    prot.getBoolean("containers", defaultRuleSet.containers),
                    prot.getBoolean("pets", defaultRuleSet.pets),
                    prot.getBoolean("farms", defaultRuleSet.farms)
            );

            rules.put(worldName, set);
        }

        plugin.getLogger().info("[AegisGuard] Loaded " + rules.size() + " per-world rule sets.");
    }

    /* -----------------------------
     * Accessors
     * ----------------------------- */
    
    private WorldRuleSet getRules(World world) {
        if (world == null) return defaultRuleSet;
        return rules.getOrDefault(world.getName(), defaultRuleSet);
    }

    public boolean allowClaims(World world) {
        return getRules(world).allowClaims;
    }

    public boolean isPvPAllowed(World world) {
        return getRules(world).pvp;
    }

    public boolean allowMobs(World world) {
        return getRules(world).mobs;
    }
    
    public boolean allowContainers(World world) {
        return getRules(world).containers;
    }
    
    public boolean allowPets(World world) {
        return getRules(world).pets;
    }

    public boolean allowFarms(World world) {
        return getRules(world).farms;
    }

    /**
     * Generic protection lookup for dynamic checks.
     */
    public boolean isProtectionEnabled(World world, String key) {
        WorldRuleSet set = getRules(world);
        return switch (key.toLowerCase()) {
            case "pvp" -> set.pvp;
            case "mobs" -> set.mobs;
            case "containers" -> set.containers;
            case "pets" -> set.pets;
            case "farms", "farm" -> set.farms;
            default -> true;
        };
    }

    /* -----------------------------
     * Inner Class: WorldRuleSet
     * ----------------------------- */
    public static class WorldRuleSet {
        // These MUST be public for access by the manager
        public boolean allowClaims;
        public boolean pvp;
        public boolean mobs;
        public boolean containers;
        public boolean pets;
        public boolean farms;

        public WorldRuleSet(boolean allowClaims, boolean pvp, boolean mobs,
                            boolean containers, boolean pets, boolean farms) {
            this.allowClaims = allowClaims;
            this.pvp = pvp;
            this.mobs = mobs;
            this.containers = containers;
            this.pets = pets;
            this.farms = farms;
        }
        
        // Default constructor for fallback/empty init
        public WorldRuleSet() {
            this(true, true, true, true, true, true);
        }
    }
}
