package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * EffectUtil
 * - Centralizes all CONFIGURABLE sound and particle effects.
 * - Handles UI sounds and protection feedback.
 */
public class EffectUtil {

    private final AegisGuard plugin;

    // Cache
    private String menuOpenSound, menuFlipSound, menuCloseSound;
    private String confirmSound, errorSound;
    private String claimSuccessSound, unclaimSound;
    
    private float vol, pitch; // Generic defaults

    public EffectUtil(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("sounds");
        if (cfg == null) return;

        // UI Sounds
        menuOpenSound = cfg.getString("menu_open.sound", "BLOCK_CHEST_OPEN");
        menuFlipSound = cfg.getString("menu_flip.sound", "UI_BUTTON_CLICK");
        menuCloseSound = cfg.getString("menu_close.sound", "BLOCK_CHEST_CLOSE");
        
        // Action Sounds
        confirmSound = cfg.getString("confirm.sound", "ENTITY_PLAYER_LEVELUP");
        errorSound = cfg.getString("error.sound", "BLOCK_NOTE_BLOCK_BASS");
        
        // Claim Sounds
        claimSuccessSound = cfg.getString("claim_success.sound", "ENTITY_PLAYER_LEVELUP");
        unclaimSound = cfg.getString("unclaim.sound", "ENTITY_VILLAGER_NO");
        
        // Default Volume/Pitch (Simplification)
        this.vol = 0.7f;
        this.pitch = 1.0f;
    }

    // --- UI SOUNDS ---

    public void playMenuOpen(Player p) { play(p, menuOpenSound, vol, 1.0f); }
    public void playMenuClose(Player p) { play(p, menuCloseSound, vol, 1.0f); }
    public void playMenuFlip(Player p) { play(p, menuFlipSound, 0.5f, 1.5f); }
    public void playConfirm(Player p) { play(p, confirmSound, vol, 1.5f); }
    public void playError(Player p) { play(p, errorSound, 1.0f, 0.5f); }

    // --- GAMEPLAY EFFECTS ---

    public void playClaimSuccess(Player p) {
        play(p, claimSuccessSound, 1.0f, 1.2f);
        try {
            // "Happy" Explosion
            p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 2, 0), 15, 0.5, 0.5, 0.5, 0);
            p.spawnParticle(Particle.FIREWORKS_SPARK, p.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception ignored) {}
    }

    public void playUnclaim(Player p) {
        play(p, unclaimSound, 1.0f, 0.8f);
        try {
            p.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        } catch (Exception ignored) {}
    }

    public void playTeleport(Player p) {
        // "Enderman" Style Teleport
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        try {
            p.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.5);
            p.spawnParticle(Particle.DRAGON_BREATH, p.getLocation(), 10, 0.2, 0.1, 0.2, 0.05);
        } catch (Exception ignored) {}
    }

    // --- PROTECTION & REGION EFFECTS ---

    public void playEffect(String category, String type, Player p, Location loc) {
        if (!plugin.getConfig().getBoolean("protection_effects.enabled", true)) return;
        if (!plugin.isSoundEnabled(p)) return;

        String base = "protection_effects." + category + "." + type;
        
        // Load from config or use defaults
        String soundName = plugin.getConfig().getString(base + "_sound", "BLOCK_NOTE_BLOCK_BASS");
        String particleName = plugin.getConfig().getString(base + "_particle", "SMOKE_NORMAL");

        try {
            if (soundName != null) {
                p.playSound(loc, Sound.valueOf(soundName.toUpperCase()), 1.0f, 1.0f);
            }
            if (particleName != null) {
                p.spawnParticle(Particle.valueOf(particleName.toUpperCase()), loc.add(0.5, 1.2, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
            }
        } catch (Exception ignored) {
            // Silently fail if config has invalid enum names
        }
    }

    public void playCustomEffect(Player p, String effectName, Location loc) {
        if (effectName == null || !plugin.isSoundEnabled(p)) return;

        // "Lightning" preset
        if (effectName.equalsIgnoreCase("lightning")) {
            p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2.0f);
            // Don't spawn actual lightning (damage), just sound/particles if needed
            return;
        }

        String base = "cosmetics.entry_effects." + effectName;
        String soundKey = plugin.getConfig().getString(base + ".sound");
        String particleKey = plugin.getConfig().getString(base + ".particle");

        try {
            if (soundKey != null) {
                p.playSound(loc, Sound.valueOf(soundKey.toUpperCase()), 1.0f, 1.0f);
            }
            if (particleKey != null) {
                p.spawnParticle(Particle.valueOf(particleKey.toUpperCase()), loc.add(0.5, 1, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception ignored) {}
    }

    // --- INTERNAL HELPER ---

    private void play(Player p, String soundName, float vol, float pitch) {
        if (!plugin.globalSoundsEnabled() || !plugin.isSoundEnabled(p)) return;
        try {
            p.playSound(p.getLocation(), Sound.valueOf(soundName.toUpperCase()), vol, pitch);
        } catch (Exception e) {
            // Prevent console spam on invalid sound
        }
    }
}
