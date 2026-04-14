package com.aegisguard.api;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Static helper for resolving the live AegisGuard API from another plugin.
 */
public final class AegisGuardProvider {

    private AegisGuardProvider() {
    }

    public static @Nullable AegisGuardAPI get() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AegisGuard");
        if (plugin instanceof AegisGuard aegisGuard) {
            return aegisGuard.getApi();
        }
        return null;
    }

    public static Optional<AegisGuardAPI> optional() {
        return Optional.ofNullable(get());
    }

    public static boolean isAvailable() {
        return get() != null;
    }
}
