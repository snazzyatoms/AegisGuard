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
 * - Handles config-driven effects for protections.
 * - Handles configurable UI sounds.
 */
public class EffectUtil {

    private final AegisGuard plugin;

    // Config values for UI sounds
    private String menuOpenSound;
    private float menuOpenVolume;
    private float menuOpenPitch;
    private String menuFlipSound;
    private float menuFlipVolume;
    private float menuFlipPitch;
    private String menuCloseSound;
    private float menuCloseVolume;
    private float menuClosePitch;
    private String confirmSound;
    private float confirmVolume;
    private float confirmPitch;
    private String errorSound;
    private float errorVolume;
    private float errorPitch;
    private String claimSuccessSound;
    private float claimSuccessVolume;
    private float claimSuccessPitch;
    private String unclaimSound;
    private float unclaimVolume;
    private float unclaimPitch;

    public EffectUtil(AegisGuard plugin) {
        this.plugin = plugin;
        reload(); // Load all values on startup
    }

    /**
     * Loads/reloads all sound and particle settings from the config.yml
     */
    public void reload() {
        ConfigurationSection sounds = plugin.getConfig().getConfigurationSection("sounds");
        if (sounds == null) sounds = plugin.getConfig().createSection("sounds");

        menuOpenSound = sounds.getString("menu_open.sound", "BLOCK_CHEST_OPEN");
        menuOpenVolume = (float) sounds.getDouble("menu_open.volume", 0.7);
        menuOpenPitch = (float) sounds.getDouble("menu_open.pitch", 1.0);

        menuFlipSound = sounds.getString("menu_flip.sound", "UI_BUTTON_CLICK");
        menuFlipVolume = (float) sounds.getDouble("menu_flip.volume", 0.7);
        menuFlipPitch = (float) sounds.getDouble("menu_flip.pitch", 1.2);

        menuCloseSound = sounds.getString("menu_close.sound", "BLOCK_CHEST_CLOSE");
        menuCloseVolume = (float) sounds.getDouble("menu_close.volume", 0.7);
        menuClosePitch = (float) sounds.getDouble("menu_close.pitch", 1.0);

        confirmSound = sounds.getString("confirm.sound", "ENTITY_PLAYER_LEVELUP");
        confirmVolume = (float) sounds.getDouble("confirm.volume", 0.7);
        confirmPitch = (float) sounds.getDouble("confirm.pitch", 1.2);

        errorSound = sounds.getString("error.sound", "BLOCK_NOTE_BLOCK_BASS");
        errorVolume = (float) sounds.getDouble("error.volume", 1.0);
        errorPitch = (float) sounds.getDouble("error.pitch", 0.8);

        claimSuccessSound = sounds.getString("claim_success.sound", "ENTITY_PLAYER_LEVELUP");
        claimSuccessVolume = (float) sounds.getDouble("claim_success.volume", 1.0);
        claimSuccessPitch = (float) sounds.getDouble("claim_success.pitch", 1.2);

        unclaimSound = sounds.getString("unclaim.sound", "ENTITY_VILLAGER_NO");
        unclaimVolume = (float) sounds.getDouble("unclaim.volume", 0.8);
        unclaimPitch = (float) sounds.getDouble("unclaim.pitch", 0.9);
    }

    /* -----------------------------
     * Simple UI Sounds
     * ----------------------------- */

    private boolean isSoundEnabled(Player p) {
        return plugin.isSoundEnabled(p);
    }

    public void playMenuOpen(Player p) {
        play(p, menuOpenSound, menuOpenVolume, menuOpenPitch);
    }
    public void playMenuClose(Player p) {
        play(p, menuCloseSound, menuCloseVolume, menuClosePitch);
    }
    public void playMenuFlip(Player p) {
        play(p, menuFlipSound, menuFlipVolume, menuFlipPitch);
    }
    public void playConfirm(Player p) {
        play(p, confirmSound, confirmVolume, confirmPitch);
    }
    public void playError(Player p) {
        play(p, errorSound, errorVolume, errorPitch);
    }
    public void playClaimSuccess(Player p) {
        play(p, claimSuccessSound, claimSuccessVolume, claimSuccessPitch);
        // Spawn happy particles
        p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);
    }
    public void playUnclaim(Player p) {
        play(p, unclaimSound, unclaimVolume, unclaimPitch);
        p.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);
    }

    // --- NEW: Teleport Effect (Required for /ag stuck) ---
    public void playTeleport(Player p) {
        // Enderman sound
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        // Purple particles
        p.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.1);
        p.spawnParticle(Particle.DRAGON_BREATH, p.getLocation(), 10, 0.2, 0.1, 0.2, 0.05);
    }

    /**
     * Core sound-playing logic
     */
    private void play(Player player, String soundName, float volume, float pitch) {
        if (!plugin.cfg().globalSoundsEnabled() || !isSoundEnabled(player)) return;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name in config.yml: " + soundName);
        }
    }

    /* -----------------------------
     * Config-driven Protection Effects
     * ----------------------------- */

    public void playEffect(String category, String type, Player p, Location loc) {
        if (!plugin.getConfig().getBoolean("protection_effects.enabled", true)) return;
        if (!isSoundEnabled(p)) return;

        String base = "protection_effects." + category + "." + type;
        String def = "protection_effects.defaults." + type;

        // Get sound/particle from config or fallback
        String soundKey = plugin.getConfig().getString(base + "_sound",
                plugin.getConfig().getString(def + "_sound", "BLOCK_NOTE_BLOCK_BASS"));
        String particleKey = plugin.getConfig().getString(base + "_particle",
                plugin.getConfig().getString(def + "_particle", "SMOKE_NORMAL"));
        
        float volume = (float) plugin.getConfig().getDouble(base + "_volume",
                plugin.getConfig().getDouble(def + "_volume", 1.0));
        float pitch = (float) plugin.getConfig().getDouble(base + "_pitch",
                plugin.getConfig().getDouble(def + "_pitch", 1.0));

        if (soundKey != null) {
            try {
                Sound sound = Sound.valueOf(soundKey.toUpperCase());
                p.playSound(loc, sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {}
        }

        if (particleKey != null) {
            try {
                Particle particle = Particle.valueOf(particleKey.toUpperCase());
                loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1, 0.5),
                        10, 0.3, 0.3, 0.3, 0.05);
            } catch (IllegalArgumentException ignored) {}
        }
    }
    
    /**
     * Plays a custom effect defined in the cosmetics section of config.
     */
    public void playCustomEffect(Player p, String effectName, Location loc) {
        if (!plugin.getConfig().getBoolean("protection_effects.enabled", true)) return;
        if (!isSoundEnabled(p)) return;
        if (effectName == null) return;

        // Try to load from config
        String base = "cosmetics.entry_effects." + effectName;
        String soundKey = plugin.getConfig().getString(base + ".sound");
        String particleKey = plugin.getConfig().getString(base + ".particle");
        
        // Fallback for hardcoded "lightning" just in case config is missing it
        if (effectName.equalsIgnoreCase("lightning") && soundKey == null) {
            p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2.0f);
            return;
        }

        if (soundKey != null) {
            try {
                Sound sound = Sound.valueOf(soundKey.toUpperCase());
                p.playSound(loc, sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }
        
        if (particleKey != null) {
            try {
                Particle particle = Particle.valueOf(particleKey.toUpperCase());
                loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
