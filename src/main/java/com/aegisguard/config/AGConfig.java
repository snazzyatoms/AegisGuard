package com.aegisguard.config;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * AGConfig
 * - Wraps config.yml access
 * - Provides safe lookups with defaults
 * - Syncs new features (limits, admin options, sounds, protections)
 */
public class AGConfig {

    private final AegisGuard plugin;
    private FileConfiguration cfg;

    // --- NEW ---
    // This version number should match the one in your default config.yml
    // When you add new options, you bump this number.
    private static final double LATEST_CONFIG_VERSION = 1.0;

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

        // --- NEW ---
        // Sync defaults to add any new options to the user's config
        syncConfigDefaults();
    }

    /**
     * --- NEW ---
     * This method adds all default values to the config in memory.
     * When paired with copyDefaults(true), it will add any *missing*
     * keys to the user's config.yml file on disk.
     */
    private void syncConfigDefaults() {
        // Add the version key
        cfg.addDefault("config-version", LATEST_CONFIG_VERSION);

        // --- Claiming & Limits ---
        cfg.addDefault("claims.max_claims_per_player", 1);
        cfg.addDefault("limits.min_radius", 1);
        cfg.addDefault("limits.max_radius", 32);
        cfg.addDefault("limits.max_area", 16000);
        cfg.addDefault("limits.preview_seconds", 10);

        // --- Economy ---
        cfg.addDefault("use_vault", true);
        cfg.addDefault("claim_cost", 100.0);
        cfg.addDefault("item_cost.type", "DIAMOND");
        cfg.addDefault("item_cost.amount", 5);
        cfg.addDefault("refund_on_unclaim", false);
        cfg.addDefault("refund_percent", 0);

        // --- Effects & Visuals ---
        cfg.addDefault("effects.on_claim.lightning_visual", true);
        cfg.addDefault("effects.on_claim.particle", "TOTEM");

        // --- Protections ---
        cfg.addDefault("protections.no_mobs_in_claims", true);
        cfg.addDefault("protections.pvp_protection", true);
        cfg.addDefault("protections.container_protection", true);

        // --- Sounds ---
        cfg.addDefault("sounds.global_enabled", true);

        // --- Admin Options ---
        cfg.addDefault("admin.auto_remove_banned_plots", false);
        cfg.addDefault("admin.bypass_claim_limit", false);
        cfg.addDefault("admin.broadcast_admin_actions", false);

        // --- Merge and Save ---
        // This copies all the defaults we just added into the in-memory config
        cfg.options().copyDefaults(true);
        // This saves the merged config back to disk
        save();

        // Re-read the config one last time to ensure we are using the saved values
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }


    /* -----------------------------
     * Claiming & Limits
     * ----------------------------- */
// ... existing code ...
    public int getMaxClaimsPerPlayer() {
// ... existing code ...
    }

    public int getMinRadius() {
// ... existing code ...
    }

    public int getMaxRadius() {
// ... existing code ...
    }
// ... existing code ...
    public int getPreviewSeconds() {
// ... existing code ...
    }

    /* -----------------------------
     * Economy
     * ----------------------------- */
// ... existing code ...
    public boolean useVault() {
// ... existing code ...
    }

_ ... existing code ..._
    public double getClaimCost() {
// ... existing code ...
    }
// ... existing code ...
    public String getItemCostType() {
// ... existing code ...
    }

    public int getItemCostAmount() {
// ... existing code ...
    }

    public boolean refundOnUnclaim() {
// ... existing code ...
    }

    public int getRefundPercent() {
// ... existing code ...
    }

    /* -----------------------------
     * Effects & Visuals
     * ----------------------------- */
// ... existing code ...
    public boolean lightningOnClaim() {
// ... existing code ...
    }

    public String getClaimParticle() {
// ... existing code ...
    }

    /* -----------------------------
     * Protections
     * ----------------------------- */
// ... existing code ...
    public boolean noMobsInClaims() {
// ... existing code ...
    }
// ... existing code ...
    public boolean pvpProtectionDefault() {
// ... existing code ...
    }

    public boolean containerProtectionDefault() {
// ... existing code ...
    }

    /* -----------------------------
     * Sounds
     * ----------------------------- */
// ... existing code ...
    public boolean globalSoundsEnabled() {
// ... existing code ...
    }

    /* -----------------------------
     * Admin Options
     * ----------------------------- */
// ... existing code ...
    public boolean autoRemoveBannedPlots() {
// ... existing code ...
    }

    public boolean adminBypassClaimLimit() {
// ... existing code ...
    }
// ... existing code ...
    public boolean broadcastAdminActions() {
// ... existing code ...
    }

    /* -----------------------------
     * Helpers
     * ----------------------------- */
// ... existing code ...
    public FileConfiguration raw() {
// ... existing code ...
    }

    public void save() {
// ... existing code ...
    }
}
