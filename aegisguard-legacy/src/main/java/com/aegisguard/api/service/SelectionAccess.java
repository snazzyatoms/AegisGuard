package com.aegisguard.api.service;

import com.aegisguard.selection.Selection;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface SelectionAccess {

    @Nullable Selection get(UUID playerId);

    boolean hasSelection(Player player);

    long getSelectionArea(Player player);

    void clearSelection(Player player);

    void setLoc1(Player player, Location location);

    void setLoc2(Player player, Location location);

    void confirmClaim(Player player);

    void confirmClaim(Player player, boolean serverClaim);

    void resizePlot(Player player, String mode, int amount);
}
