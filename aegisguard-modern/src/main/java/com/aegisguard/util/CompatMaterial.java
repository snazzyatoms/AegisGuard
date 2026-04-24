package com.aegisguard.util;

import org.bukkit.Material;

import java.util.Locale;

public final class CompatMaterial {

    private CompatMaterial() {
    }

    public static Material resolve(String... candidates) {
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                Material material = Material.matchMaterial(candidate.trim().toUpperCase(Locale.ROOT));
                if (material != null) {
                    return material;
                }
            }
        }
        return Material.PAPER;
    }

    public static boolean is(Material material, String... candidates) {
        return material != null && material == resolve(candidates);
    }
}
