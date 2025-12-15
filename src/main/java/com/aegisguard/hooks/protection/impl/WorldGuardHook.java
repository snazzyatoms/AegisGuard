package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import com.aegisguard.hooks.protection.ProtectionResult;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard compatibility:
 * - If WorldGuard is present and denies BUILD, we deny.
 * - Otherwise ABSTAIN/ALLOW.
 */
public class WorldGuardHook implements ProtectionHook {

    private final AegisGuard plugin;

    public WorldGuardHook(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public ProtectionResult canBuild(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return ProtectionResult.ABSTAIN;
        }
        if (!isAvailable()) return ProtectionResult.ABSTAIN;

        try {
            // WorldGuard uses WorldEdit locations
            com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(location);

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            // Checks BUILD flag and membership etc.
            boolean canBuild = query.testState(
                    weLoc,
                    WorldGuardPlugin.inst().wrapPlayer(player),
                    Flags.BUILD
            );

            return canBuild ? ProtectionResult.ABSTAIN : ProtectionResult.DENY;
        } catch (Throwable t) {
            // If WG API changes or fails, do not hard-break AegisGuard.
            return ProtectionResult.ABSTAIN;
        }
    }
}
