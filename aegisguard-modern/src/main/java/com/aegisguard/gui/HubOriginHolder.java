package com.aegisguard.gui;

import org.bukkit.inventory.InventoryHolder;

/**
 * Marker for GUI holders that can remember whether they were reached from the
 * travel hub. When a flagged screen rebuilds (pagination, tab switches, detail
 * views), the new holder re-derives the flag via
 * {@link GUIManager#hubOriginActive(org.bukkit.entity.Player)} so "Back to
 * Travel Hub" keeps working no matter how deep the player navigates.
 */
public interface HubOriginHolder extends InventoryHolder {

    boolean isFromHub();

    void setFromHub(boolean fromHub);
}
