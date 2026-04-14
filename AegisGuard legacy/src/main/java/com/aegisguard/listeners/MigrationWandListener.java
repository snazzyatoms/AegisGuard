package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class MigrationWandListener implements Listener {

    private final AegisGuard plugin;

    public MigrationWandListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || plugin.gui().migration() == null || !plugin.gui().migration().isMigrationWand(item)) {
            return;
        }
        if (!player.hasPermission("aegis.admin.migrate")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        event.setCancelled(true);
        if (event.getClickedBlock() != null) {
            plugin.gui().migration().openAt(player, event.getClickedBlock().getLocation());
        } else {
            plugin.gui().migration().open(player);
        }
        plugin.effects().playMenuOpen(player);
    }
}
