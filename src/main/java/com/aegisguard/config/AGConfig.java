package com.aegisguard.config;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AGConfig {

    private final AegisGuard plugin;
    private FileConfiguration config;

    // --- CACHED VALUES ---
    private boolean zoningEnabled;
    private boolean levelingEnabled;
    private boolean titlesEnabled;
    private boolean biomesEnabled;
    private boolean likesEnabled;
    private boolean unstuckEnabled;
    private boolean travelEnabled;
    private boolean upkeepEnabled;

    // v1.1.2 New Features
    private boolean discordEnabled;
    private boolean mergeEnabled;
    private boolean bluemapEnabled;
    private boolean pl3xmapEnabled;

    // --- Leveling Expansion Toggle ---
    private boolean levelingExpansionEnabled;
    private int levelingExpansionAmount;

    // --- Leveling Payment Routing (Vault vs Claim Blocks) ---
    private boolean allowLevelVaultPayment;
    private boolean allowLevelBlockPayment;
    private boolean fallbackToBlocksIfVaultUnavailable;

    // --- Claim Blocks (1.2.4+) ---
    private boolean claimBlocksEnabled;

    /**
     * "Starter" claim blocks added to every player total.
     * NOTE: historically you used claim_blocks.starter_amount.
     * ClaimBlockManager currently reads claim_blocks.starting_blocks.
     * We support BOTH and auto-migrate starter_amount -> starting_blocks if needed.
     */
    private long claimBlocksStarterAmount;

    private boolean claimBlocksPlaytimeEnabled;
    private long claimBlocksPlaytimeIntervalMinutes;
    private long claimBlocksPlaytimeAmount;

    // Optional: reward on plot level-up
    private boolean claimBlocksLevelUpEnabled;
    private long claimBlocksLevelUpAmount;

    private boolean firstClaimLimitEnabled;
    private long firstClaimLimitMaxArea;

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

        // -------------------------------
        // Defaults (safe, non-invasive)
        // -------------------------------

        // Leveling expansion defaults
        config.addDefault("leveling.expand_plot_on_levelup", false); // Default OFF as requested
        config.addDefault("leveling.expansion_amount", 5);

        // Hooks
        config.addDefault("hooks.bluemap.enabled", true);
        config.addDefault("hooks.pl3xmap.enabled", true);
        config.addDefault("hooks.discord.enabled", false);
        config.addDefault("claims.merging.enabled", true);

        // ✅ Leveling upgrade payment options (Vault vs Claim Blocks)
        config.addDefault("leveling.upgrades.allow_vault_payment", true);
        config.addDefault("leveling.upgrades.allow_block_payment", true);
        config.addDefault("leveling.upgrades.fallback_to_blocks_if_vault_unavailable", true);

        // -------------------------------
        // Claim Blocks Defaults (1.2.4+)
        // -------------------------------
        config.addDefault("claim_blocks.enabled", true);

        // Starter blocks (primary)
        config.addDefault("claim_blocks.starting_blocks", 1000L);
        // Legacy alias (kept for older configs)
        config.addDefault("claim_blocks.starter_amount", 1000L);

        // Playtime earn (used by ClaimBlockTask)
        config.addDefault("claim_blocks.earn.playtime.enabled", true);
        config.addDefault("claim_blocks.earn.playtime.interval_minutes", 10L);
        config.addDefault("claim_blocks.earn.playtime.amount", 1L);

        // Level-up earn (optional; language packs include messages)
        config.addDefault("claim_blocks.earn.level_up.enabled", false);
        config.addDefault("claim_blocks.earn.level_up.amount", 0L);

        // First-claim limiter (optional; language packs include messages)
        config.addDefault("claim_blocks.first_claim_limit.enabled", true);
        config.addDefault("claim_blocks.first_claim_limit.max_area", 2500L); // 50x50 default cap

        // -------------------------------
        // Tiny migration helper
        // -------------------------------
        // If server owners used the older key, map it to the newer one.
        if (config.isSet("claim_blocks.starter_amount") && !config.isSet("claim_blocks.starting_blocks")) {
            config.set("claim_blocks.starting_blocks", config.getLong("claim_blocks.starter_amount", 1000L));
        }

        config.options().copyDefaults(true);
        plugin.saveConfig();

        // -------------------------------
        // Cache Updates
        // -------------------------------
        this.zoningEnabled = config.getBoolean("zoning.enabled", true);
        this.levelingEnabled = config.getBoolean("leveling.enabled", true);
        this.titlesEnabled = config.getBoolean("titles.enabled", true);
        this.biomesEnabled = config.getBoolean("biomes.enabled", true);
        this.likesEnabled = config.getBoolean("social.likes_enabled", true);
        this.unstuckEnabled = config.getBoolean("unstuck.enabled", true);
        this.travelEnabled = config.getBoolean("travel_system.enabled", true);
        this.upkeepEnabled = config.getBoolean("upkeep.enabled", false);

        this.discordEnabled = config.getBoolean("hooks.discord.enabled", false);
        this.mergeEnabled = config.getBoolean("claims.merging.enabled", true);
        this.bluemapEnabled = config.getBoolean("hooks.bluemap.enabled", true);
        this.pl3xmapEnabled = config.getBoolean("hooks.pl3xmap.enabled", true);

        // --- Leveling Expansion Settings ---
        this.levelingExpansionEnabled = config.getBoolean("leveling.expand_plot_on_levelup", false);
        this.levelingExpansionAmount = config.getInt("leveling.expansion_amount", 5);

        // --- Leveling Payment Routing ---
        this.allowLevelVaultPayment = config.getBoolean("leveling.upgrades.allow_vault_payment", true);
        this.allowLevelBlockPayment = config.getBoolean("leveling.upgrades.allow_block_payment", true);
        this.fallbackToBlocksIfVaultUnavailable =
                config.getBoolean("leveling.upgrades.fallback_to_blocks_if_vault_unavailable", true);

        // --- Claim Blocks Cache ---
        this.claimBlocksEnabled = config.getBoolean("claim_blocks.enabled", true);

        // Prefer starting_blocks (current), fall back to starter_amount (legacy)
        long starter = config.getLong("claim_blocks.starting_blocks",
                config.getLong("claim_blocks.starter_amount", 1000L));
        this.claimBlocksStarterAmount = Math.max(0L, starter);

        this.claimBlocksPlaytimeEnabled = config.getBoolean("claim_blocks.earn.playtime.enabled", true);
        this.claimBlocksPlaytimeIntervalMinutes = Math.max(1L, config.getLong("claim_blocks.earn.playtime.interval_minutes", 10L));
        this.claimBlocksPlaytimeAmount = Math.max(0L, config.getLong("claim_blocks.earn.playtime.amount", 1L));

        this.claimBlocksLevelUpEnabled = config.getBoolean("claim_blocks.earn.level_up.enabled", false);
        this.claimBlocksLevelUpAmount = Math.max(0L, config.getLong("claim_blocks.earn.level_up.amount", 0L));

        this.firstClaimLimitEnabled = config.getBoolean("claim_blocks.first_claim_limit.enabled", true);
        this.firstClaimLimitMaxArea = Math.max(0L, config.getLong("claim_blocks.first_claim_limit.max_area", 2500L));

        // Protections
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
    // 💱 Currency System
    // ======================================

    public CurrencyType getCurrencyFor(String feature) {
        String type = config.getString("currencies." + feature, "VAULT");
        if (type == null) return CurrencyType.VAULT;

        String t = type.trim().toUpperCase();
        // Friendly aliases
        if (t.equals("MONEY") || t.equals("ECONOMY")) t = "VAULT";

        try {
            return CurrencyType.valueOf(t);
        } catch (IllegalArgumentException ignored) {
            return CurrencyType.VAULT;
        }
    }

    // ======================================
    // --- Leveling Getters ---
    // ======================================

    public boolean isLevelingEnabled() { return levelingEnabled; }

    public boolean isLevelingExpansionEnabled() { return levelingExpansionEnabled; }
    public int getLevelingExpansionAmount() { return levelingExpansionAmount; }

    public double getLevelBaseCost() { return config.getDouble("leveling.base_cost", 1000.0); }
    public double getLevelCostMultiplier() { return config.getDouble("leveling.cost_multiplier", 1.5); }
    public int getMaxLevel() { return config.getInt("leveling.max_level", 10); }

    /**
     * ✅ Level cost currency selection (supports BOTH styles):
     *  - leveling.cost_type (legacy/explicit)
     *  - currencies.leveling (your modern currency map)
     */
    public CurrencyType getLevelCostType() {
        // Prefer explicit leveling.cost_type if present
        if (config.isSet("leveling.cost_type")) {
            String type = config.getString("leveling.cost_type", "VAULT");
            if (type != null) {
                String t = type.trim().toUpperCase();
                if (t.equals("MONEY") || t.equals("ECONOMY")) t = "VAULT";
                try { return CurrencyType.valueOf(t); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        // Otherwise use currencies.leveling
        return getCurrencyFor("leveling");
    }

    public List<String> getLevelRewards(int level) {
        return config.getStringList("leveling.rewards." + level);
    }

    // --- Level upgrade payment routing (Vault vs Claim Blocks) ---

    public boolean allowLevelVaultPayment() { return allowLevelVaultPayment; }
    public boolean allowLevelBlockPayment() { return allowLevelBlockPayment; }
    public boolean fallbackToBlocksIfVaultUnavailable() { return fallbackToBlocksIfVaultUnavailable; }

    /**
     * Claim-block cost for leveling up (next level).
     * Supports explicit table:
     *   leveling.upgrades.blocks_costs.<level>: <amount>
     * and fallback formula:
     *   250*L^2 - 250
     */
    public long getLevelBlocksCost(int nextLevel) {
        long explicit = config.getLong("leveling.upgrades.blocks_costs." + nextLevel, -1);
        if (explicit >= 0) return explicit;

        long lvl = Math.max(1, nextLevel);
        return Math.max(0, (250L * lvl * lvl) - 250L);
    }

    // ======================================
    // --- Claim Blocks (1.2.4+) ---
    // ======================================

    public boolean isClaimBlocksEnabled() { return claimBlocksEnabled; }

    /** Alias-safe getter (starting_blocks + legacy starter_amount). */
    public long getClaimBlocksStarterAmount() { return claimBlocksStarterAmount; }

    public boolean isClaimBlocksPlaytimeEnabled() { return claimBlocksPlaytimeEnabled; }
    public long getClaimBlocksPlaytimeIntervalMinutes() { return claimBlocksPlaytimeIntervalMinutes; }
    public long getClaimBlocksPlaytimeAmount() { return claimBlocksPlaytimeAmount; }

    public boolean isClaimBlocksLevelUpEnabled() { return claimBlocksLevelUpEnabled; }
    public long getClaimBlocksLevelUpAmount() { return claimBlocksLevelUpAmount; }

    public boolean isFirstClaimLimitEnabled() { return firstClaimLimitEnabled; }
    public long getFirstClaimLimitMaxArea() { return firstClaimLimitMaxArea; }

    // ======================================
    // --- Standard Getters ---
    // ======================================

    public boolean useVault(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".use_vault";
            if (config.isSet(path)) return config.getBoolean(path);
        }
        return config.getBoolean("economy.use_vault", true);
    }

    public boolean useVault() { return config.getBoolean("economy.use_vault", true); }

    public double getWorldVaultCost(World world) {
        if (world != null) {
            String path = "claims.per_world." + world.getName() + ".vault_cost";
            if (config.isSet(path)) return config.getDouble(path);
        }
        return config.getDouble("economy.claim_cost", 100.0);
    }

    public Material getWorldItemCostType(World world) {
        String path = "economy.item_cost.type";
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".item_cost.type")) {
            path = "claims.per_world." + world.getName() + ".item_cost.type";
        }
        Material m = Material.matchMaterial(config.getString(path, "DIAMOND"));
        return m != null ? m : Material.DIAMOND;
    }

    public int getWorldItemCostAmount(World world) {
        String path = "economy.item_cost.amount";
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".item_cost.amount")) {
            path = "claims.per_world." + world.getName() + ".item_cost.amount";
        }
        return config.getInt(path, 5);
    }

    public double getFlightCost() { return config.getDouble("economy.flag_costs.fly", 5000.0); }
    public double getShopInteractCost() { return config.getDouble("economy.flag_costs.shop-interact", 0.0); }

    public boolean isTravelSystemEnabled() { return travelEnabled; }
    public boolean allowHomeTeleport() { return config.getBoolean("travel_system.allow_home_teleport", true); }
    public boolean allowVisitTeleport() { return config.getBoolean("travel_system.allow_visit_teleport", true); }

    public boolean isUpkeepEnabled() { return upkeepEnabled; }
    public long getUpkeepCheckHours() { return config.getLong("upkeep.check_interval_hours", 24); }
    public double getUpkeepCost() { return config.getDouble("upkeep.cost_per_plot", 100.0); }
    public int getUpkeepGraceDays() { return config.getInt("upkeep.grace_period_days", 7); }

    public List<String> getRoleNames() {
        ConfigurationSection sec = config.getConfigurationSection("roles");
        if (sec == null) return Collections.emptyList();
        return new ArrayList<>(sec.getKeys(false));
    }

    public List<String> getRolePermissions(String role) {
        return config.getStringList("roles." + role);
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

    public Material getAdminWandMaterial() {
        Material m = Material.matchMaterial(config.getString("admin.wand.material", "BLAZE_ROD"));
        return m != null ? m : Material.BLAZE_ROD;
    }

    public String getAdminWandName() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("admin.wand.name", "&c&lSentinel's Scepter"));
    }

    public List<String> getAdminWandLore() {
        List<String> lore = config.getStringList("admin.wand.lore");
        if (lore == null || lore.isEmpty()) return new ArrayList<>();
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return out;
    }

    public int getWorldMaxRadius(World world) {
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".max_radius")) {
            return config.getInt("claims.per_world." + world.getName() + ".max_radius");
        }
        return config.getInt("claims.max_radius", 32);
    }

    public int getWorldMinRadius(World world) {
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".min_radius")) {
            return config.getInt("claims.per_world." + world.getName() + ".min_radius");
        }
        return config.getInt("claims.min_radius", 1);
    }

    public int getWorldMaxClaims(World world) {
        if (world != null && config.isSet("claims.per_world." + world.getName() + ".max_claims_per_player")) {
            return config.getInt("claims.per_world." + world.getName() + ".max_claims_per_player");
        }
        return config.getInt("claims.max_claims_per_player", 1);
    }

    public boolean isZoningEnabled() { return zoningEnabled; }
    public int getMaxZonesPerPlot() { return config.getInt("zoning.max_zones_per_plot", 10); }
    public boolean landlordGetsFullRent() { return config.getBoolean("zoning.landlord_gets_full_rent", true); }

    public boolean isTitleEnabled() { return titlesEnabled; }
    public int getTitleFadeIn() { return config.getInt("titles.fade_in", 10); }
    public int getTitleStay() { return config.getInt("titles.stay", 40); }
    public int getTitleFadeOut() { return config.getInt("titles.fade_out", 10); }

    public boolean isBiomesEnabled() { return biomesEnabled; }
    public double getBiomeChangeCost() { return config.getDouble("biomes.cost_per_change", 2000.0); }
    public List<String> getAllowedBiomes() { return config.getStringList("biomes.allowed"); }

    public boolean isLikesEnabled() { return likesEnabled; }
    public boolean oneLikePerPlayer() { return config.getBoolean("social.one_like_per_player", true); }

    public String getNotificationLocation() { return config.getString("titles.notification_location", "ACTION_BAR"); }
    public boolean isUnstuckEnabled() { return unstuckEnabled; }
    public int getUnstuckWarmup() { return config.getInt("unstuck.warmup_seconds", 5); }

    public boolean isDiscordEnabled() { return discordEnabled; }
    public boolean isBlueMapEnabled() { return bluemapEnabled; }
    public boolean isPl3xMapEnabled() { return pl3xmapEnabled; }

    public boolean isMergeEnabled() { return mergeEnabled; }
    public double getMergeCost() { return config.getDouble("claims.merging.cost", 500.0); }
}
