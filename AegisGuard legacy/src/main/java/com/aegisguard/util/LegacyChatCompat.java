package com.aegisguard.util;

import java.lang.reflect.Method;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * CraftBukkit does not always expose the Spigot chat bridge at runtime.
 * This helper avoids hard crashes by using reflection and falling back to
 * normal chat messages when the bridge is unavailable.
 */
public final class LegacyChatCompat {

    private static final Method PLAYER_SPIGOT_METHOD = findPlayerSpigotMethod();
    private static final Method SPIGOT_CHAT_SEND_METHOD = findSpigotSendMethod(false);
    private static final Method SPIGOT_ACTION_BAR_SEND_METHOD = findSpigotSendMethod(true);

    private LegacyChatCompat() {
    }

    public static void sendChat(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }

        if (!trySendChat(player, message)) {
            player.sendMessage(message);
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }

        if (!trySendActionBar(player, message)) {
            player.sendMessage(message);
        }
    }

    private static boolean trySendChat(Player player, String message) {
        if (PLAYER_SPIGOT_METHOD == null || SPIGOT_CHAT_SEND_METHOD == null) {
            return false;
        }

        try {
            Object spigot = PLAYER_SPIGOT_METHOD.invoke(player);
            SPIGOT_CHAT_SEND_METHOD.invoke(spigot, (Object) TextComponent.fromLegacyText(message));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean trySendActionBar(Player player, String message) {
        if (PLAYER_SPIGOT_METHOD == null || SPIGOT_ACTION_BAR_SEND_METHOD == null) {
            return false;
        }

        try {
            Object spigot = PLAYER_SPIGOT_METHOD.invoke(player);
            SPIGOT_ACTION_BAR_SEND_METHOD.invoke(
                    spigot,
                    ChatMessageType.ACTION_BAR,
                    (Object) TextComponent.fromLegacyText(message)
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findPlayerSpigotMethod() {
        try {
            return Player.class.getMethod("spigot");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findSpigotSendMethod(boolean actionBar) {
        if (PLAYER_SPIGOT_METHOD == null) {
            return null;
        }

        try {
            Class<?> spigotType = PLAYER_SPIGOT_METHOD.getReturnType();
            for (Method method : spigotType.getMethods()) {
                if (!"sendMessage".equals(method.getName())) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();
                if (!actionBar && params.length == 1 && params[0].isArray()) {
                    return method;
                }

                if (actionBar
                        && params.length == 2
                        && params[0] == ChatMessageType.class
                        && params[1].isArray()) {
                    return method;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }

        return null;
    }
}
