package com.aegisguard.progression;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

public enum AscensionFocus {
    UNCHOSEN("unchosen", Material.GLASS_BOTTLE),
    STONEWRIGHT("stonewright", Material.IRON_PICKAXE),
    VERDANT_KEEPER("verdant_keeper", Material.WHEAT),
    WAYFINDER("wayfinder", Material.COMPASS);

    private final String key;
    private final Material icon;

    AscensionFocus(String key, Material icon) {
        this.key = key;
        this.icon = icon;
    }

    public String key() { return key; }
    public Material icon() { return icon; }

    public PotionEffectType effectType() {
        return switch (this) {
            case STONEWRIGHT -> PotionEffectType.FAST_DIGGING;
            case VERDANT_KEEPER -> PotionEffectType.LUCK;
            case WAYFINDER -> PotionEffectType.SPEED;
            default -> null;
        };
    }

    public int amplifierForLevel(int level) {
        if (this == UNCHOSEN || level < 5) return -1;
        return level >= 20 ? 1 : 0;
    }

    public static AscensionFocus parse(String value) {
        if (value == null || value.isBlank()) return UNCHOSEN;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UNCHOSEN;
        }
    }
}
