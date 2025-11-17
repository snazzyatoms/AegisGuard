package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * EffectUtil
 * - Replaces the old SoundUtil and merges SoundManager.
 * - Centralizes all CONIGURABLE sound and particle effects.
 * - Handles config-driven effects for protections.
 * - Handles configurable UI sounds.
 * - Reads values from config.yml on reload.
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
        // Use the method from the main class
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
    }
    public void playUnclaim(Player p) {
        play(p, unclaimSound, unclaimVolume, unclaimPitch);
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
        if (!isSoundEnabled(p)) return; // Respect player's personal sound toggle

        String base = "protection_effects." + category + "." + type + "_";
        String def = "protection_effects." + type + "_";

        String soundKey = plugin.getConfig().getString(base + "sound",
                plugin.getConfig().getString(def + "sound", "BLOCK_NOTE_BLOCK_BASS"));
        String particleKey = plugin.getConfig().getString(base + "particle",
                plugin.getConfig().getString(def + "particle", "SMOKE_NORMAL"));
        
        float volume = (float) plugin.getConfig().getDouble(base + "volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble(base + "pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundKey.toUpperCase());
            p.playSound(loc, sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // invalid sound in config
        }

        try {
            Particle particle = Particle.valueOf(particleKey.toUpperCase());
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1, 0.5),
                    10, 0.3, 0.3, 0.3, 0.05);
        } catch (IllegalArgumentException ignored) {
            // invalid particle in config
        }
    }
}            Particle particle = Particle.valueOf(particleKey.toUpperCase());
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1, 0.5),
                    10, 0.3, 0.3, 0.3, 0.05);
        } catch (IllegalArgumentException ignored) {
            // invalid particle in config
        }
    }
}
