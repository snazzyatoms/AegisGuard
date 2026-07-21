package com.aegisguard.horizons;

import org.bukkit.Material;

public enum HorizonRank {
    DAWNREACH(1, "dawnreach", 2_500L, 60, Material.COPPER_BLOCK),
    SKYBOUND(2, "skybound", 7_500L, 75, Material.LAPIS_BLOCK),
    REALMFORGE(3, "realmforge", 17_500L, 90, Material.AMETHYST_BLOCK),
    STARWARD(4, "starward", 35_000L, 110, Material.DIAMOND_BLOCK),
    ETERNAL_AEGIS(5, "eternal_aegis", 60_000L, 130, Material.NETHER_STAR);

    private final int index;
    private final String key;
    private final long defaultRenown;
    private final int defaultRadiusGain;
    private final Material defaultMaterial;

    HorizonRank(int index, String key, long defaultRenown, int defaultRadiusGain, Material defaultMaterial) {
        this.index = index;
        this.key = key;
        this.defaultRenown = defaultRenown;
        this.defaultRadiusGain = defaultRadiusGain;
        this.defaultMaterial = defaultMaterial;
    }

    public int index() { return index; }
    public String key() { return key; }
    public long defaultRenown() { return defaultRenown; }
    public int defaultRadiusGain() { return defaultRadiusGain; }
    public Material defaultMaterial() { return defaultMaterial; }

    public static HorizonRank byIndex(int index) {
        for (HorizonRank rank : values()) {
            if (rank.index == index) return rank;
        }
        return null;
    }
}
