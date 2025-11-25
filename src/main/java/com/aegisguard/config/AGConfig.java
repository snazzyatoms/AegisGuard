package com.aegisguard.config;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType; 
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
        
        // --- AUTO-UPDATE LOGIC ---
        // This ensures missing keys are added automatically without deleting the file.
        this.config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    // ======================================
    // 💱 Currency System
    // ======================================
    public CurrencyType getCurrencyFor(String feature) {
        String type = config.getString("currencies." + feature, "VAULT").toUpperCase();
        try {
            return CurrencyType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return CurrencyType.VAULT; // Fallback
        }
    }

    // ======================================
    // 🏗️ Zoning (Sub-Claims)
    // ======================================
    public boolean isZoningEnabled() {
        return config.getBoolean("zoning.enabled", true);
    }

    public int getMaxZonesPerPlot() {
        return config.getInt("zoning.max_zones_per_plot", 10);
    }

    public boolean landlordGetsFullRent() {
        return config.getBoolean("zoning.landlord_gets_full_rent", true);
    }

    // ======================================
    // 📈 Plot Leveling
    // ======================================
    public boolean isLevelingEnabled() {
        return config.getBoolean("leveling.enabled", true);
    }
    
    public CurrencyType getLevelCostType() {
        String type = config.getString("leveling.cost_type", "VAULT").toUpperCase();
        try { return CurrencyType.valueOf(type); } 
        catch (IllegalArgumentException e) { return CurrencyType.VAULT; }
    }

    public double getLevelBaseCost() {
        return config.getDouble("leveling.base_cost", 1000.0);
    }

    public double getLevelCostMultiplier() {
        return config.getDouble("leveling.cost_multiplier", 1.5);
    }

    public int getMaxLevel() {
        return config.getInt("leveling.max_level", 10);
    }

    public List<String> getLevelRewards(int level) {
        return config.getStringList("leveling.rewards." + level);
    }

    // ======================================
    // 🔮 Visuals, Biomes & Social
    // ======================================
    public boolean isTitleEnabled() { return config.getBoolean("titles.enabled", true); }
    public int getTitleFadeIn() { return config.getInt("titles.fade_in", 10); }
    public int getTitleStay() { return config.getInt("titles.stay", 40); }
    public int getTitleFadeOut() { return config.getInt("titles.fade_out", 10); }

    public boolean isBiomesEnabled() { return config.getBoolean("biomes.enabled", true); }
    public double getBiomeChangeCost() { return config.getDouble("biomes.cost_per_change", 2000.0); }
    public List<String> getAllowedBiomes() { return config.getStringList("biomes.allowed"); }

    public boolean isLikesEnabled() { return config.getBoolean("social.likes_enabled", true); }
    public boolean oneLikePerPlayer() { return config.getBoolean("social.one_like_per_player", true); }

    // ======================================
    // 💰 Economy
    // ======================================
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

    public double getFlightCost() {
        return config.getDouble("economy.flag_costs.fly", 5000.0);
    }

    // ======================================
    // 🧱 Claims
    // ======================================
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

    // --- Travel System ---
    public boolean isTravelSystemEnabled() { 
        return config.getBoolean("travel_system.enabled", true); 
    }
    public boolean allowHomeTeleport() { 
        return config.getBoolean("travel_system.allow_home_teleport", true); 
    }
    public boolean allowVisitTeleport() { 
        return config.getBoolean("travel_system.allow_visit_teleport", true); 
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
    
    // --- Protections ---
    public boolean pvpProtectionDefault() { return config.getBoolean("protections.pvp_protection", true); }
    public boolean noMobsInClaims() { return config.getBoolean("protections.no_mobs_in_claims", true); }
    public boolean containerProtectionDefault() { return config.getBoolean("protections.container_protection", true); }
    public boolean petProtectionDefault() { return config.getBoolean("protections.pets_protection", true); }
    public boolean farmProtectionDefault() { return config.getBoolean("protections.farm_protection", true); }
    
    public boolean flyDefault() { return config.getBoolean("protections.fly", false); }
    public boolean entryDefault() { return config.getBoolean("protections.entry", true); }
}
