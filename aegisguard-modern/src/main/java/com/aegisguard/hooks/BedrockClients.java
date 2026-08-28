package com.aegisguard.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper is always a Java server. Bedrock players join through Geyser and Floodgate.
 * This helper detects those clients so chest GUIs can use sneak-left instead of right-click.
 */
public final class BedrockClients {

    private static final ConcurrentHashMap<UUID, Boolean> CACHE = new ConcurrentHashMap<>();

    private static volatile JavaPlugin plugin;
    private static volatile boolean floodgatePresent;
    private static volatile boolean geyserPresent;
    private static volatile Object floodgateApi;
    private static volatile Method floodgateIsPlayer;
    private static volatile Method floodgatePrefix;
    private static volatile Object geyserApi;
    private static volatile Method geyserIsBedrock;

    private BedrockClients() {}

    public static void bind(JavaPlugin host) {
        plugin = host;
        CACHE.clear();
        floodgatePresent = false;
        geyserPresent = false;
        floodgateApi = null;
        floodgateIsPlayer = null;
        floodgatePrefix = null;
        geyserApi = null;
        geyserIsBedrock = null;

        if (host != null && !host.getConfig().getBoolean("gui.bedrock.detect", true)) {
            host.getLogger().info("[AegisGuard] Bedrock client detection is disabled in config (gui.bedrock.detect).");
            return;
        }

        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate != null && floodgate.isEnabled()) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                try {
                    floodgatePrefix = apiClass.getMethod("getPlayerPrefix");
                } catch (NoSuchMethodException ignored) {}
                floodgatePresent = floodgateApi != null && floodgateIsPlayer != null;
            } catch (Throwable ignored) {
                floodgatePresent = false;
            }
        }

        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyser == null) geyser = Bukkit.getPluginManager().getPlugin("Geyser-Paper");
        if (geyser != null && geyser.isEnabled()) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Method apiMethod = apiClass.getMethod("api");
                geyserApi = apiMethod.invoke(null);
                try {
                    geyserIsBedrock = apiClass.getMethod("isBedrockPlayer", UUID.class);
                } catch (NoSuchMethodException missing) {
                    geyserIsBedrock = null;
                }
                geyserPresent = geyserApi != null;
            } catch (Throwable ignored) {
                geyserPresent = false;
            }
        }

        if (host != null) {
            if (floodgatePresent || geyserPresent) {
                host.getLogger().info("[AegisGuard] Bedrock players detected via "
                        + (floodgatePresent ? "Floodgate" : "")
                        + (floodgatePresent && geyserPresent ? " + " : "")
                        + (geyserPresent ? "Geyser" : "")
                        + ". Chest GUIs will use left-click and sneak-left for those clients.");
            } else {
                host.getLogger().info("[AegisGuard] No Floodgate/Geyser found. GUI clicks stay Java-style until a Bedrock proxy is installed.");
            }
        }
    }

    public static boolean isBedrock(HumanEntity entity) {
        return entity instanceof Player player && isBedrock(player);
    }

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        if (plugin != null && !plugin.getConfig().getBoolean("gui.bedrock.detect", true)) return false;
        UUID id = player.getUniqueId();
        Boolean cached = CACHE.get(id);
        if (cached != null) return cached;
        boolean bedrock = probe(player);
        CACHE.put(id, bedrock);
        return bedrock;
    }

    public static void forget(UUID playerId) {
        if (playerId != null) CACHE.remove(playerId);
    }

    public static boolean floodgateHooked() {
        return floodgatePresent;
    }

    public static boolean geyserHooked() {
        return geyserPresent;
    }

    private static boolean probe(Player player) {
        UUID id = player.getUniqueId();
        if (floodgateApi != null && floodgateIsPlayer != null) {
            try {
                Object result = floodgateIsPlayer.invoke(floodgateApi, id);
                if (Boolean.TRUE.equals(result)) return true;
            } catch (Throwable ignored) {}
        }
        if (geyserApi != null && geyserIsBedrock != null) {
            try {
                Object result = geyserIsBedrock.invoke(geyserApi, id);
                if (Boolean.TRUE.equals(result)) return true;
            } catch (Throwable ignored) {}
        }
        if (floodgatePresent && floodgatePrefix != null) {
            try {
                Object prefix = floodgatePrefix.invoke(floodgateApi);
                if (prefix instanceof String pfx && !pfx.isEmpty()) {
                    String name = player.getName();
                    if (name != null && name.startsWith(pfx)) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
