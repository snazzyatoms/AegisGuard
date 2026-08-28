package com.aegisguard.gui;

import com.aegisguard.hooks.BedrockClients;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Chest-click mapping for Java and Geyser/Floodgate Bedrock clients.
 * The server is always Java; {@link BedrockClients} tells us which player is on Bedrock.
 */
public final class GuiClicks {
    private GuiClicks() {}

    public static boolean primary(InventoryClickEvent e) {
        return e.isLeftClick() && !e.isShiftClick();
    }

    /**
     * Second action on a slot (buy, bid, favorite, ban, deny).
     * Java: right-click. Bedrock: sneak+left, or swap-offhand (Geyser often sends that
     * when the player uses the "place" control in a chest).
     */
    public static boolean alternate(InventoryClickEvent e) {
        if (e.isRightClick() && !e.isShiftClick()) return true;
        if (!isBedrock(e.getWhoClicked())) return false;
        if (e.isLeftClick() && e.isShiftClick()) return true;
        ClickType click = e.getClick();
        return click == ClickType.SWAP_OFFHAND;
    }

    /** Cancel / delete / leave. Java shift-right, or drop (Q) — Geyser usually maps Q. */
    public static boolean destructive(InventoryClickEvent e) {
        ClickType click = e.getClick();
        return (e.isShiftClick() && e.isRightClick())
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP;
    }

    public static boolean isBedrock(HumanEntity entity) {
        return BedrockClients.isBedrock(entity);
    }
}
