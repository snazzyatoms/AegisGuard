package com.aegisguard.travel;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable Safe Travel settings loaded from {@code travel.*}.
 * Defaults intentionally preserve existing 1.3.0 behavior: no cooldown,
 * no confirmation prompt, no combat block, and the historical 4-block
 * safe-search radius already used by {@code TeleportUtil}.
 */
public final class SafeTravelSettings {

    public static final int DEFAULT_SAFE_SEARCH_RADIUS = 4;
    public static final int DEFAULT_COOLDOWN_SECONDS = 0;
    public static final boolean DEFAULT_REQUIRE_CONFIRMATION = false;
    public static final boolean DEFAULT_BLOCK_WHILE_IN_COMBAT = false;
    public static final int DEFAULT_COMBAT_SECONDS = 10;
    public static final int DEFAULT_CONFIRMATION_SECONDS = 10;

    private final boolean enabled;
    private final int cooldownSeconds;
    private final boolean requireConfirmation;
    private final int confirmationSeconds;
    private final boolean blockWhileInCombat;
    private final int combatSeconds;
    private final int safeSearchRadius;
    private final boolean applyToStaff;
    private final boolean bypassPermissionHonored;

    public SafeTravelSettings(
            boolean enabled,
            int cooldownSeconds,
            boolean requireConfirmation,
            int confirmationSeconds,
            boolean blockWhileInCombat,
            int combatSeconds,
            int safeSearchRadius,
            boolean applyToStaff,
            boolean bypassPermissionHonored
    ) {
        this.enabled = enabled;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.requireConfirmation = requireConfirmation;
        this.confirmationSeconds = Math.max(1, confirmationSeconds);
        this.blockWhileInCombat = blockWhileInCombat;
        this.combatSeconds = Math.max(1, combatSeconds);
        this.safeSearchRadius = Math.max(0, safeSearchRadius);
        this.applyToStaff = applyToStaff;
        this.bypassPermissionHonored = bypassPermissionHonored;
    }

    public static SafeTravelSettings defaults() {
        return new SafeTravelSettings(
                true,
                DEFAULT_COOLDOWN_SECONDS,
                DEFAULT_REQUIRE_CONFIRMATION,
                DEFAULT_CONFIRMATION_SECONDS,
                DEFAULT_BLOCK_WHILE_IN_COMBAT,
                DEFAULT_COMBAT_SECONDS,
                DEFAULT_SAFE_SEARCH_RADIUS,
                false,
                true
        );
    }

    public static SafeTravelSettings fromConfig(FileConfiguration config) {
        if (config == null) return defaults();
        ConfigurationSection section = config.getConfigurationSection("travel");
        if (section == null) return defaults();
        return new SafeTravelSettings(
                section.getBoolean("enabled", true),
                section.getInt("cooldown_seconds", DEFAULT_COOLDOWN_SECONDS),
                section.getBoolean("require_confirmation", DEFAULT_REQUIRE_CONFIRMATION),
                section.getInt("confirmation_seconds", DEFAULT_CONFIRMATION_SECONDS),
                section.getBoolean("block_while_in_combat", DEFAULT_BLOCK_WHILE_IN_COMBAT),
                section.getInt("combat_tag_seconds", DEFAULT_COMBAT_SECONDS),
                section.getInt("safe_search_radius", DEFAULT_SAFE_SEARCH_RADIUS),
                section.getBoolean("apply_to_staff", false),
                section.getBoolean("honor_bypass_permission", true)
        );
    }

    public boolean isEnabled() { return enabled; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public boolean isRequireConfirmation() { return requireConfirmation; }
    public int getConfirmationSeconds() { return confirmationSeconds; }
    public boolean isBlockWhileInCombat() { return blockWhileInCombat; }
    public int getCombatSeconds() { return combatSeconds; }
    public int getSafeSearchRadius() { return safeSearchRadius; }
    public boolean isApplyToStaff() { return applyToStaff; }
    public boolean isBypassPermissionHonored() { return bypassPermissionHonored; }

    /** True when every opt-in restriction is off and radius matches historical behavior. */
    public boolean preservesLegacyBehavior() {
        return cooldownSeconds == 0
                && !requireConfirmation
                && !blockWhileInCombat
                && safeSearchRadius == DEFAULT_SAFE_SEARCH_RADIUS;
    }
}
