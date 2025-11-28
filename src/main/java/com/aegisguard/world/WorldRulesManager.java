package com.aegisguard.world;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class WorldRulesManager {

    private final AegisGuard plugin;
    private final Map<String, WorldRuleSet> rules = new HashMap<>();
    private WorldRuleSet defaultRuleSet;

    public WorldRulesManager(AegisGuard plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    public void load() {
        rules.clear();
        AGConfig cfg = plugin.cfg();

        // 1. Establish Global Defaults from Config
        this.defaultRuleSet = new WorldRuleSet(
            true, // Allow claims by default
            cfg.pvpProtectionDefault(),
            cfg.noMobsInClaims(),
            cfg.containerProtectionDefault(),
            cfg.petProtectionDefault(),
            cfg.farmProtectionDefault(),
            cfg.flyDefault(),
            cfg.entryDefault()
        );
        
        // 2. Load Per-World Overrides
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("claims.per_world");
        if (section == null) {
            plugin.getLogger().info("[AegisGuard] No per-world configuration found. Using defaults.");
            return;
        }

        for (String worldName : section.getKeys(false)) {
            ConfigurationSection worldSec = section.getConfigurationSection(worldName);
            if (worldSec == null) continue;

            // Check for specific "protections" subsection, otherwise check root of world section
            ConfigurationSection prot = worldSec.getConfigurationSection("protections");
            if (prot == null) prot = worldSec;

            WorldRuleSet set = new WorldRuleSet(
                worldSec.getBoolean("allow_claims", defaultRuleSet.allowClaims),
                prot.getBoolean("pvp", defaultRuleSet.pvp),
                prot.getBoolean("mobs", defaultRuleSet.mobs),
                prot.getBoolean("containers", defaultRuleSet.containers),
                prot.getBoolean("pets", defaultRuleSet.pets),
                prot.getBoolean("farms", defaultRuleSet.farms),
                prot.getBoolean("fly", defaultRuleSet.fly),
                prot.getBoolean("entry", defaultRuleSet.entry)
            );

            rules.put(worldName, set);
        }

        plugin.getLogger().info("[AegisGuard] Loaded rules for " + rules.size() + " worlds.");
    }

    /**
     * Applies the specific world's default flags to a newly created plot.
     */
    public void applyDefaults(Plot plot) {
        if (plot == null) return;
        
        World world = Bukkit.getWorld(plot.getWorld());
        WorldRuleSet set = getRules(world);
        
        // Apply Main Protections
        plot.setFlag("pvp", set.pvp);
        plot.setFlag("mobs", set.mobs);
        plot.setFlag("containers", set.containers);
        plot.setFlag("pets", set.pets);
        plot.setFlag("farm", set.farms);
        plot.setFlag("fly", set.fly);
        plot.setFlag("entry", set.entry);
        
        // Hardcoded safe defaults (usually always false/protected initially)
        plot.setFlag("tnt-damage", false);
        plot.setFlag("fire-spread", false);
        plot.setFlag("piston-use", false);
        plot.setFlag("interact", true); // Usually allow interaction by default for members
        plot.setFlag("build", true);
    }

    private WorldRuleSet getRules(World world) {
        if (world == null) return defaultRuleSet;
        return rules.getOrDefault(world.getName(), defaultRuleSet);
    }

    // --- Public API ---

    public boolean allowClaims(World world) {
        return getRules(world).allowClaims;
    }

    public boolean isPvPAllowed(World world) { return getRules(world).pvp; }
    public boolean allowMobs(World world) { return getRules(world).mobs; }
    public boolean allowContainers(World world) { return getRules(world).containers; }
    public boolean allowPets(World world) { return getRules(world).pets; }
    public boolean allowFarms(World world) { return getRules(world).farms; }

    public boolean isProtectionEnabled(World world, String key) {
        WorldRuleSet set = getRules(world);
        return switch (key.toLowerCase()) {
            case "pvp" -> set.pvp;
            case "mobs" -> set.mobs;
            case "containers" -> set.containers;
            case "pets" -> set.pets;
            case "farms", "farm" -> set.farms;
            case "fly" -> set.fly;
            case "entry" -> set.entry;
            default -> true;
        };
    }

    // --- Data Class ---
    public static class WorldRuleSet {
        public boolean allowClaims;
        public boolean pvp;
        public boolean mobs;
        public boolean containers;
        public boolean pets;
        public boolean farms;
        public boolean fly;
        public boolean entry;

        public WorldRuleSet(boolean allowClaims, boolean pvp, boolean mobs,
                            boolean containers, boolean pets, boolean farms, 
                            boolean fly, boolean entry) {
            this.allowClaims = allowClaims;
            this.pvp = pvp;
            this.mobs = mobs;
            this.containers = containers;
            this.pets = pets;
            this.farms = farms;
            this.fly = fly;
            this.entry = entry;
        }
    }
}
