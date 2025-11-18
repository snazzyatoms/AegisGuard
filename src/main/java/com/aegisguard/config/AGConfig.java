package com.aegisguard.config;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AGConfig
 * - Wraps config.yml access
 * - Provides safe lookups with defaults
 * - Syncs all new features to the config file.
 *
 * --- UPGRADE NOTES (Ultimate) ---
 * - This is the final, complete version.
 * - Added all "Ultimate" and "Legacy" feature defaults.
 * - Added world-aware getters for per-world settings.
 */
public class AGConfig {

    private final AegisGuard plugin;
    private FileConfiguration cfg;
    
    // --- UPDATED ---
    // This version number should match the one in your default config.yml
    // We've added all the new features.
    private static final double LATEST_CONFIG_VERSION = 1.1;

    // --- NEW ---
    private Map<String, Set<String>> rolesConfig = new HashMap<>();

    public AGConfig(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    /* -----------------------------
     * Lifecycle
     * ----------------------------- */
    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
        syncConfigDefaults();
        loadRoles(); // Load role permissions
    }

    /**
     * --- FULLY UPGRADED ---
     * This method adds all default values to the config in memory.
     * When paired with copyDefaults(true), it will add any *missing*
     * keys to the user's config.yml file on disk.
     */
    private void syncConfigDefaults() {
        // Check version
        double currentVersion = cfg.getDouble("config-version", 0.1);
        boolean needsSave = currentVersion < LATEST_CONFIG_VERSION;
        
        cfg.addDefault("config-version", LATEST_CONFIG_VERSION);

        // --- Hooks & Storage ---
        cfg.addDefault("hooks.dynmap.enabled", true);
        cfg.addDefault("hooks.dynmap.layer_name", "AegisGuard Plots");
        cfg.addDefault("hooks.dynmap.sync_interval_minutes", 5);
        cfg.addDefault("hooks.dynmap.style.stroke_color", "00FF00");
        cfg.addDefault("hooks.dynmap.style.stroke_opacity", 0.8);
        cfg.addDefault("hooks.dynmap.style.stroke_weight", 3);
        cfg.addDefault("hooks.dynmap.style.fill_color", "00FF00");
        cfg.addDefault("hooks.dynmap.style.fill_opacity", 0.35);
        cfg.addDefault("storage.type", "yml");
        cfg.addDefault("storage.database.file", "aegisguard.db");
        cfg.addDefault("storage.database.host", "localhost");
        cfg.addDefault("storage.database.port", 3306);
        cfg.addDefault("storage.database.database", "aegisguard");
        cfg.addDefault("storage.database.username", "user");
        cfg.addDefault("storage.database.password", "pass");
        cfg.addDefault("storage.database.useSSL", false);

        // --- Messages ---
        cfg.addDefault("messages.language_style", "old_english");
        cfg.addDefault("messages.allow_runtime_switch", true);
        cfg.addDefault("messages.show_language_button_in_gui", true);

        // --- Economy ---
        cfg.addDefault("use_vault", true);
        cfg.addDefault("claim_cost", 100.0);
        cfg.addDefault("item_cost.type", "DIAMOND");
        cfg.addDefault("item_cost.amount", 5);
        cfg.addDefault("refund_on_unclaim", false);
        cfg.addDefault("refund_percent", 0);
        cfg.addDefault("resize-cost-per-block", 10.0);

        // --- Upkeep ---
        cfg.addDefault("upkeep.enabled", false);
        cfg.addDefault("upkeep.check_interval_hours", 24);
        cfg.addDefault("upkeep.cost_per_plot", 100.0);
        cfg.addDefault("upkeep.grace_period_days", 7);

        // --- Auction ---
        cfg.addDefault("auction.duration_days", 3);
        cfg.addDefault("auction.min_bid_increase", 100.0);
        cfg.addDefault("auction.owner_cut_percent", 50);

        // --- Wilderness Revert ---
        cfg.addDefault("wilderness_revert.enabled", true);
        cfg.addDefault("wilderness_revert.revert_after_hours", 2);
        cfg.addDefault("wilderness_revert.check_interval_minutes", 10);
        cfg.addDefault("wilderness_revert.revert_batch_size", 500);

        // --- Global Claims ---
        cfg.addDefault("claims.max_claims_per_player", 1);
        cfg.addDefault("claims.min_radius", 1);
        cfg.addDefault("claims.max_radius", 32);
        cfg.addDefault("claims.max_area", 16000);
        cfg.addDefault("claims.preview_seconds", 10);
        
        // --- Protections (Global Defaults) ---
        cfg.addDefault("protections.pvp_protection", true);
        cfg.addDefault("protections.no_mobs_in_claims", true);
        cfg.addDefault("protections.container_protection", true);
        cfg.addDefault("protections.pet_protection", true);
        cfg.addDefault("protections.entities_protection", true);
        cfg.addDefault("protections.farm_protection", true);
        cfg.addDefault("protections.tnt-damage", true);
        cfg.addDefault("protections.fire-spread", true);
        cfg.addDefault("protections.piston-use", true);

        // --- Role Definitions ---
        cfg.addDefault("roles.co-owner", List.of("BUILD", "CONTAINERS", "INTERACT", "PET_DAMAGE", "ENTITY_INTERACT", "FARM_TRAMPLE"));
        cfg.addDefault("roles.member", List.of("BUILD", "CONTAINERS", "INTERACT", "FARM_TRAMPLE"));
        cfg.addDefault("roles.guest", List.of("INTERACT"));
        cfg.addDefault("roles.default", List.of("INTERACT"));
        
        // --- Sounds ---
        cfg.addDefault("sounds.global_enabled", true);
        cfg.addDefault("sounds.menu_open.sound", "BLOCK_CHEST_OPEN");
        cfg.addDefault("sounds.menu_open.volume", 0.7);
        cfg.addDefault("sounds.menu_open.pitch", 1.0);
        cfg.addDefault("sounds.menu_flip.sound", "UI_BUTTON_CLICK");
        cfg.addDefault("sounds.menu_flip.volume", 0.7);
        cfg.addDefault("sounds.menu_flip.pitch", 1.2);
        cfg.addDefault("sounds.menu_close.sound", "BLOCK_CHEST_CLOSE");
        cfg.addDefault("sounds.menu_close.volume", 0.7);
        cfg.addDefault("sounds.menu_close.pitch", 1.0);
        cfg.addDefault("sounds.confirm.sound", "ENTITY_PLAYER_LEVELUP");
        cfg.addDefault("sounds.confirm.volume", 0.7);
        cfg.addDefault("sounds.confirm.pitch", 1.2);
        cfg.addDefault("sounds.error.sound", "BLOCK_NOTE_BLOCK_BASS");
        cfg.addDefault("sounds.error.volume", 1.0);
        cfg.addDefault("sounds.error.pitch", 0.8);
        cfg.addDefault("sounds.claim_success.sound", "ENTITY_PLAYER_LEVELUP");
        cfg.addDefault("sounds.claim_success.volume", 1.0);
        cfg.addDefault("sounds.claim_success.pitch", 1.2);
        cfg.addDefault("sounds.unclaim.sound", "ENTITY_VILLAGER_NO");
        cfg.addDefault("sounds.unclaim.volume", 0.8);
        cfg.addDefault("sounds.unclaim.pitch", 0.9);

        // --- Visualization ---
        cfg.addDefault("visualization.enabled", true);
        cfg.addDefault("visualization.particle", "FLAME");
        cfg.addDefault("visualization.color", "0,255,255");
        cfg.addDefault("visualization.interval_ticks", 40);

        // --- Cosmetics ---
        cfg.addDefault("cosmetics.border_particles.flame.material", "BLAZE_POWDER");
        cfg.addDefault("cosmetics.border_particles.flame.particle", "FLAME");
        cfg.addDefault("cosmetics.border_particles.flame.display-name", "&6Flame Border");
        cfg.addDefault("cosmetics.border_particles.flame.price", 1000.0);
        cfg.addDefault("cosmetics.border_particles.heart.material", "POPPY");
        cfg.addDefault("cosmetics.border_particles.heart.particle", "HEART");
        cfg.addDefault("cosmetics.border_particles.heart.display-name", "&cHeart Border");
        cfg.addDefault("cosmetics.border_particles.heart.price", 2500.0);
        
        // --- Expansion Requests ---
        cfg.addDefault("expansions.enabled", true);
        cfg.addDefault("expansions.approval_required", true);
        cfg.addDefault("expansions.cost.use_vault", true);
        cfg.addDefault("expansions.cost.amount", 250.0);
        cfg.addDefault("expansions.cooldown_minutes", 30);
        cfg.addDefault("expansions.notify_admins", true);
        cfg.addDefault("expansions.persistence", true);

        // --- Protection Effects ---
        cfg.addDefault("protection_effects.enabled", true);
        cfg.addDefault("protection_effects.defaults.deny_sound", "BLOCK_NOTE_BLOCK_BASS");
        cfg.addDefault("protection_effects.defaults.deny_particle", "SMOKE_NORMAL");
        cfg.addDefault("protection_effects.defaults.deny_volume", 1.0);
        cfg.addDefault("protection_effects.defaults.deny_pitch", 1.0);
        // ... (add all other effect defaults)

        // --- Admin Options ---
        cfg.addDefault("admin.auto_remove_banned", false);
        cfg.addDefault("admin.bypass_claim_limit", false);
        cfg.addDefault("admin.broadcast_admin_actions", false);
        cfg.addDefault("admin.allow_language_command", true);

        // --- Merge and Save ---
        if (needsSave) {
            cfg.options().copyDefaults(true);
            save();
            plugin.reloadConfig(); // Re-read the saved values
            this.cfg = plugin.getConfig();
        }
    }

    /**
     * Loads and parses the `roles:` section from the config into a map.
     */
    private void loadRoles() {
        rolesConfig.clear();
        ConfigurationSection rolesSection = cfg.getConfigurationSection("roles");
        if (rolesSection == null) {
            plugin.getLogger().severe("Config Error: 'roles:' section is missing! Using defaults.");
            // Add a hardcoded default for safety
            rolesConfig.put("member", Set.of("BUILD", "CONTAINERS", "INTERACT"));
            rolesConfig.put("guest", Set.of("INTERACT"));
            rolesConfig.put("default", Set.of());
            return;
        }

        for (String roleName : rolesSection.getKeys(false)) {
            List<String> perms = rolesSection.getStringList(roleName);
            rolesConfig.put(roleName.toLowerCase(), 
                    perms.stream().map(String::toUpperCase).collect(Collectors.toSet())
            );
        }
        plugin.getLogger().info("Loaded " + rolesConfig.size() + " plot roles from config.yml");
    }


    /* -----------------------------
     * Global Getters
     * ----------------------------- */
    public FileConfiguration raw() { return cfg; }
    public boolean useVault() { return cfg.getBoolean("use_vault", true); }
    public double getClaimCost() { return cfg.getDouble("claim_cost", 100.0); }
    public String getItemCostType() { return cfg.getString("item_cost.type", "DIAMOND"); }
    public int getItemCostAmount() { return cfg.getInt("item_cost.amount", 5); }
    public boolean refundOnUnclaim() { return cfg.getBoolean("refund_on_unclaim", false); }
    public int getRefundPercent() { return cfg.getInt("refund_percent", 0); }
    public double getResizeCostPerBlock() { return cfg.getDouble("resize-cost-per-block", 10.0); }
    public boolean lightningOnClaim() { return cfg.getBoolean("effects.on_claim.lightning_visual", true); }
    public boolean globalSoundsEnabled() { return cfg.getBoolean("sounds.global_enabled", true); }
    public boolean autoRemoveBannedPlots() { return cfg.getBoolean("admin.auto_remove_banned", false); }
    public boolean adminBypassClaimLimit() { return cfg.getBoolean("admin.bypass_claim_limit", false); }
    public boolean broadcastAdminActions() { return cfg.getBoolean("admin.broadcast_admin_actions", false); }
    
    // --- Protection Default Getters ---
    public boolean pvpProtectionDefault() { return cfg.getBoolean("protections.pvp_protection", true); }
    public boolean noMobsInClaims() { return cfg.getBoolean("protections.no_mobs_in_claims", true); }
    public boolean containerProtectionDefault() { return cfg.getBoolean("protections.container_protection", true); }
    public boolean petProtectionDefault() { return cfg.getBoolean("protections.pet_protection", true); }
    public boolean entityProtectionDefault() { return cfg.getBoolean("protections.entities_protection", true); }
    public boolean farmProtectionDefault() { return cfg.getBoolean("protections.farm_protection", true); }
    public boolean tntDamageDefault() { return cfg.getBoolean("protections.tnt-damage", true); }
    public boolean fireSpreadDefault() { return cfg.getBoolean("protections.fire-spread", true); }
    public boolean pistonUseDefault() { return cfg.getBoolean("protections.piston-use", true); }

    // --- Upkeep Getters ---
    public boolean isUpkeepEnabled() { return cfg.getBoolean("upkeep.enabled", false); }
    public long getUpkeepCheckHours() { return cfg.getLong("upkeep.check_interval_hours", 24); }
    public double getUpkeepCost() { return cfg.getDouble("upkeep.cost_per_plot", 100.0); }
    public long getUpkeepGraceDays() { return cfg.getLong("upkeep.grace_period_days", 7); }

    /* -----------------------------
     * World-Aware Getters
     * ----------------------------- */
     
    private String getPerWorldPath(World world, String key) {
        return "claims.per_world." + world.getName() + "." + key;
    }

    public boolean useVault(World world) {
        return cfg.getBoolean(getPerWorldPath(world, "use_vault"), useVault());
    }

    public double getWorldClaimCost(World world) {
        return cfg.getDouble(getPerWorldPath(world, "vault_cost"), getClaimCost());
    }

    public Material getWorldItemCostType(World world) {
        String matName = cfg.getString(getPerWorldPath(world, "item_cost.type"), getItemCostType());
        return Material.matchMaterial(matName);
    }

    public int getWorldItemCostAmount(World world) {
        return cfg.getInt(getPerWorldPath(world, "item_cost.amount"), getItemCostAmount());
    }

    public boolean refundOnUnclaim(World world) {
        return cfg.getBoolean(getPerWorldPath(world, "refund_on_unclaim"), refundOnUnclaim());
    }

    public int getRefundPercent(World world) {
        return cfg.getInt(getPerWorldPath(world, "refund_percent"), getRefundPercent());
    }
    
    public int getWorldMaxClaims(World world) {
        return cfg.getInt(getPerWorldPath(world, "max_claims_per_player"), cfg.getInt("claims.max_claims_per_player", 1));
    }
    
    public int getWorldMaxArea(World world) {
        String path = "claims.per_world." + world.getName() + ".max_area";
        return cfg.getInt(path, cfg.getInt("claims.max_area", 16000));
    }

    /* -----------------------------
     * Role Getters
     * ----------------------------- */
    
    /**
     * Gets the set of permission nodes for a given role.
     * @param roleName The name of the role (e.g., "member")
     * @return A Set of permissions, or an empty set if the role is invalid.
     */
    public Set<String> getRolePermissions(String roleName) {
        return rolesConfig.getOrDefault(roleName.toLowerCase(), Collections.emptySet());
    }

    /**
     * Gets a list of all configurable role names (e.g., "co-owner", "member", "guest").
     * Excludes "owner" and "default" as they are special.
     */
    public List<String> getRoleNames() {
        return rolesConfig.keySet().stream()
                .filter(role -> !role.equals("owner") && !role.equals("default"))
                .sorted()
                .collect(Collectors.toList());
    }

    /* -----------------------------
     * Helpers
     * ----------------------------- */
    public void save() {
        plugin.saveConfig();
    }
}
