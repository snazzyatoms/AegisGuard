package com.aegisguard.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class CompatSound {

    private CompatSound() {
    }

    public static Sound resolve(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return Sound.valueOf(candidate.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public static void play(Player player, Location location, float volume, float pitch, String... candidates) {
        if (player == null || location == null) {
            return;
        }
        Sound sound = resolve(candidates);
        if (sound == null) {
            return;
        }
        try {
            EffectUtil.playIfEnabled(player, location, sound, volume, pitch);
        } catch (Throwable ignored) {
        }
    }
}
