package com.yourname.aegisguard.config;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class AGConfig {

    private final AegisGuard plugin;
    private FileConfiguration config;
    
    // --- CACHED VALUES ---
    private boolean privateEstatesEnabled;
    private boolean guildSystemEnabled;
    private boolean economyEnabled;
    private boolean liquidationEnabled;
    private boolean progressionEnabled;
    
    private boolean zoningEnabled;
    private boolean titlesEnabled;
    private boolean biomesEnabled;
    private boolean likesEnabled;
    private boolean unstuckEnabled;
    private boolean travelEnabled;
    private boolean upkeepEnabled;
    
    // v1.3.0 New Features
    private boolean discordEnabled;
    private boolean bluemapEnabled;
    private boolean pl3xmapEnabled;
    
    // Protections Cache
    private boolean pvpDefault;
    private boolean mobDefault;
    private boolean containerDefault;
    private boolean petDefault;
    private boolean farmDefault;
    private boolean flyDefault;
    private boolean entryDefault;

    public AGConfig(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        
        // Cache Updates
        this.privateEstatesEnabled = config.getBoolean("modules.private_estates", true);
        this.guildSystemEnabled = config.getBoolean("modules.guild_system", true);
        this.economyEnabled = config.getBoolean("modules.economy_engine", true);
        this.liquidationEnabled = config.getBoolean("modules.asset_liquidation", true);
        this.progressionEnabled = config.getBoolean("modules.progression_system", true);
        
        this.zoningEnabled = config.getBoolean("zoning.enabled", true);
        this.titlesEnabled = config.getBoolean("visuals.titles.enabled", true);
        this.biomesEnabled = config.getBoolean("biomes.enabled", true);
        this.likesEnabled = config.getBoolean("social.likes_enabled", true);
        this.unstuckEnabled = config.getBoolean("unstuck.enabled", true);
        
        // Note: Changed path in v1.3.0 config
        this.travelEnabled = config.getBoolean("estates.travel_system.enabled", true); 
        this.upkeepEnabled = config.getBoolean("economy.upkeep.enabled", false);
        
        this.discordEnabled = config.getBoolean("hooks.discord.enabled", false);
        this.bluemapEnabled = config.getBoolean("maps.bluemap.enabled", true);
        this.pl3xmapEnabled = config.getBoolean("maps.pl3xmap.enabled", true);
        
        this.pvpDefault = config.getBoolean("protections.pvp_protection", true);
        this.mobDefault = config.getBoolean("protections.no_mobs_in_claims", true);
        this.containerDefault = config.getBoolean("protections.container_protection", true);
        this.petDefault = config.getBoolean("protections.pets_protection", true);
        this.farmDefault = config.getBoolean("protections.farm_protection", true);
        this.flyDefault = config.getBoolean("protections.fly", false);
        this.entryDefault = config.getBoolean("protections.entry", true);
    }

    public FileConfiguration raw() { return config; }

    // ======================================
    // 🎛️ MODULE GETTERS
    // ======================================
    public boolean isPrivateEstatesEnabled() { return privateEstatesEnabled; }
    public boolean isGuildSystemEnabled() { return guildSystemEnabled; }
    public boolean isEconomyEnabled() { return economyEnabled; }
    public boolean isLiquidationEnabled() { return liquidationEnabled; }
    public boolean isProgressionEnabled() { return progressionEnabled; }

    // ======================================
    // 💱 Currency System
    // ======================================
    public CurrencyType getCurrencyFor(String feature) {
        // Default to Vault if not specified
        return CurrencyType.VAULT; 
    }

    // ======================================
    // --- Standard Getters ---
    // ======================================
    
    public boolean useVault(World world) {
        if (world != null) {
            String path = "estates.per_world." + world.getName() + ".use_vault";
            if (config.isSet(path)) return config.getBoolean(path);
        }
        return config.getBoolean("economy.use_vault", true);
    }
    
    public boolean useVault() { return config.getBoolean("economy.use_vault", true); }
    
    public double getWorldVaultCost(World world) {
        if (world != null) {
            String path = "estates.per_world." + world.getName() + ".vault_cost";
            if (config.isSet(path)) return config.getDouble(path);
        }
        return config.getDouble("economy.costs.private_estate_creation", 100.0);
    }
    
    public boolean isTravelSystemEnabled() { return travelEnabled; }
    public boolean allowHomeTeleport() { return config.getBoolean("estates.travel_system.allow_home_teleport", true); }
    
    public boolean isUpkeepEnabled() { return upkeepEnabled; }
    public long getUpkeepCheckHours() { return config.getLong("economy.upkeep.check_interval_hours", 24); }
    public double getUpkeepCost() { return config.getDouble("economy.upkeep.base_cost", 100.0); }
    public int getUpkeepGraceDays() { return config.getInt("economy.upkeep.grace_period_days", 7); }
    
    // CRITICAL FIX: Safely get roles (Legacy support)
    public List<String> getRoleNames() { 
        ConfigurationSection section = config.getConfigurationSection("roles");
        if (section == null) return new ArrayList<>(); // Return empty list instead of crashing
        return new ArrayList<>(section.getKeys(false)); 
    }
    
    public boolean autoRemoveBannedPlots() { return config.getBoolean("admin.auto_remove_banned", false); }
    public boolean globalSoundsEnabled() { return config.getBoolean("sounds.global_enabled", true); }
    
    public boolean pvpProtectionDefault() { return pvpDefault; }
    public boolean noMobsInClaims() { return mobDefault; }
    public boolean containerProtectionDefault() { return containerDefault; }
    public boolean petProtectionDefault() { return petDefault; }
    public boolean farmProtectionDefault() { return farmDefault; }
    public boolean flyDefault() { return flyDefault; }
    public boolean entryDefault() { return entryDefault; }
    
    public Material getAdminWandMaterial() { return Material.matchMaterial(config.getString("admin.wand_material", "BLAZE_ROD")); }
    public String getAdminWandName() { return ChatColor.translateAlternateColorCodes('&', config.getString("admin.wand_name", "&c&lSentinel's Scepter")); }
    public List<String> getAdminWandLore() { return new ArrayList<>(); }
    
    public int getWorldMaxRadius(World world) {
        if (world != null && config.isSet("estates.per_world." + world.getName() + ".max_radius")) return config.getInt("estates.per_world." + world.getName() + ".max_radius");
        return config.getInt("estates.max_radius", 100);
    }
    
    public int getWorldMinRadius(World world) {
        if (world != null && config.isSet("estates.per_world." + world.getName() + ".min_radius")) return config.getInt("estates.per_world." + world.getName() + ".min_radius");
        return config.getInt("estates.min_radius", 5);
    }
    
    public int getWorldMaxClaims(World world) {
        if (world != null && config.isSet("estates.per_world." + world.getName() + ".max_estates_per_player")) return config.getInt("estates.per_world." + world.getName() + ".max_estates_per_player");
        return config.getInt("estates.max_estates_per_player", 3);
    }
    
    public boolean isZoningEnabled() { return zoningEnabled; }
    public int getMaxZonesPerPlot() { return config.getInt("zoning.max_zones_per_estate", 15); }
    public boolean landlordGetsFullRent() { return config.getBoolean("zoning.landlord_gets_full_rent", true); }
    
    public boolean isTitleEnabled() { return titlesEnabled; }
    public int getTitleFadeIn() { return 10; }
    public int getTitleStay() { return 40; }
    public int getTitleFadeOut() { return 10; }
    
    public boolean isBiomesEnabled() { return biomesEnabled; }
    public double getBiomeChangeCost() { return config.getDouble("biomes.cost_per_change", 2000.0); }
    public List<String> getAllowedBiomes() { return config.getStringList("biomes.allowed"); }
    
    public boolean isLikesEnabled() { return likesEnabled; }
    public boolean oneLikePerPlayer() { return config.getBoolean("social.one_like_per_player", true); }
    
    public String getNotificationLocation() { return config.getString("visuals.titles.notification_location", "ACTION_BAR"); }
    public boolean isUnstuckEnabled() { return unstuckEnabled; }
    public int getUnstuckWarmup() { return config.getInt("unstuck.warmup_seconds", 5); }
    
    public boolean isDiscordEnabled() { return discordEnabled; }
    public boolean isBlueMapEnabled() { return bluemapEnabled; }
    public boolean isPl3xMapEnabled() { return pl3xmapEnabled; }
    
    public boolean isMergeEnabled() { return true; } // Default to true for now
    public double getMergeCost() { return 500.0; }
}
