package com.aegisguard.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Static helper for resolving the live AegisGuard API from another plugin.
 */
public final class AegisGuardProvider {

    private AegisGuardProvider() {
    }

    public static @Nullable AegisGuardAPI get() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AegisGuard");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        try {
            Method method = plugin.getClass().getMethod("getApi");
            Object value = method.invoke(plugin);
            return (value instanceof AegisGuardAPI api) ? api : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static Optional<AegisGuardAPI> optional() {
        return Optional.ofNullable(get());
    }

    public static boolean isAvailable() {
        return get() != null;
    }
}