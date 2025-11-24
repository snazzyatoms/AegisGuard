package com.aegisguard.config;

import com.aegisguard.AegisGuard;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class AGConfig {

    private final AegisGuard plugin;
    private FileConfiguration config;

    public AGConfig(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    // --- Economy ---
    public boolean useVault(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".use_vault";
            if (config.isSet(path)) return config.getBoolean(path);
        }
        return config.getBoolean("economy.use_vault", true);
    }
    
    public boolean useVault() {
        return config.getBoolean("economy.use_vault", true);
    }

    public double getWorldVaultCost(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".vault_cost";
            if (config.isSet(path)) return config.getDouble(path);
        }
        return config.getDouble("economy.claim_cost", 100.0);
    }

    public org.bukkit.Material getWorldItemCostType(World world) {
        String path = "economy.item_cost.type";
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".item_cost.type")) {
            path = "claims.per_world." + world.getName() + ".item_cost.type";
        }
        return org.bukkit.Material.matchMaterial(config.getString(path, "DIAMOND"));
    }
    
    public int getWorldItemCostAmount(World world) {
        String path = "economy.item_cost.amount";
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".item_cost.amount")) {
            path = "claims.per_world." + world.getName() + ".item_cost.amount";
        }
        return config.getInt(path, 5);
    }

    // --- NEW: Flight Cost (Fixes Build Error) ---
    public double getFlightCost() {
        return config.getDouble("economy.flag_costs.fly", 5000.0);
    }

    // --- Claims ---
    public int getWorldMaxRadius(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".max_radius";
            if (config.isSet(path)) return config.getInt(path);
        }
        return config.getInt("claims.max_radius", 32);
    }

    public int getWorldMinRadius(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".min_radius";
            if (config.isSet(path)) return config.getInt(path);
        }
        return config.getInt("claims.min_radius", 1);
    }
    
    public int getWorldMaxClaims(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".max_claims_per_player";
            if (config.isSet(path)) return config.getInt(path);
        }
        return config.getInt("claims.max_claims_per_player", 1);
    }

    // --- Upkeep ---
    public boolean isUpkeepEnabled() { return config.getBoolean("upkeep.enabled", false); }
    public long getUpkeepCheckHours() { return config.getLong("upkeep.check_interval_hours", 24); }
    public double getUpkeepCost() { return config.getDouble("upkeep.cost_per_plot", 100.0); }
    public int getUpkeepGraceDays() { return config.getInt("upkeep.grace_period_days", 7); }
    
    // --- Roles ---
    public List<String> getRoleNames() {
        if (config.isConfigurationSection("roles")) {
             return new ArrayList<>(config.getConfigurationSection("roles").getKeys(false));
        }
        return List.of("co-owner", "member", "guest");
    }

    public List<String> getRolePermissions(String role) {
        return config.getStringList("roles." + role);
    }

    // --- Misc ---
    public boolean autoRemoveBannedPlots() { return config.getBoolean("admin.auto_remove_banned", false); }
    public boolean globalSoundsEnabled() { return config.getBoolean("sounds.global_enabled", true); }
    
    public boolean pvpProtectionDefault() { return config.getBoolean("protections.pvp_protection", true); }
    public boolean noMobsInClaims() { return config.getBoolean("protections.no_mobs_in_claims", true); }
    public boolean containerProtectionDefault() { return config.getBoolean("protections.container_protection", true); }
    public boolean petProtectionDefault() { return config.getBoolean("protections.pets_protection", true); }
    public boolean farmProtectionDefault() { return config.getBoolean("protections.farm_protection", true); }
    
    // --- NEW: Feature Defaults (Fixes Build Error) ---
    public boolean flyDefault() { return config.getBoolean("protections.fly", false); }
    public boolean entryDefault() { return config.getBoolean("protections.entry", true); }
}
