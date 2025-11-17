package com.aegisguard.util;

import com.aegisguard.AegisGuard;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * EffectUtil
 * - Replaces the old SoundUtil.
 * - Centralizes all sound and particle effects for the plugin.
 * - Handles config-driven effects for protections.
 * - Handles simple UI sounds.
 */
public class EffectUtil {

    private final AegisGuard plugin;

    public EffectUtil(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------
     * Simple UI Sounds
     * ----------------------------- */

    private boolean isSoundEnabled(Player p) {
        // Use the method from the main class
        return plugin.isSoundEnabled(p);
    }

    public void playMenuOpen(Player p) {
        if (isSoundEnabled(p)) p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    public void playMenuClose(Player p) {
        if (isSoundEnabled(p)) p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
    }

    public void playMenuFlip(Player p) {
        if (isSoundEnabled(p)) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    public void playConfirm(Player p) {
        if (isSoundEnabled(p)) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    public void playError(Player p) {
        if (isSoundEnabled(p)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
    }


    /* -----------------------------
     * Config-driven Protection Effects
     * (Moved from ProtectionManager)
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

        try {
            Sound sound = Sound.valueOf(soundKey.toUpperCase());
            p.playSound(loc, sound, 1f, 1f);
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
}
