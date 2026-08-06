package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * PersistentDataContainer keys for arena runs and temporary items.
 */
public final class ArenaKeys implements ArenaInventoryService.NamespacedKeys {

    public final NamespacedKey runId;
    public final NamespacedKey arenaId;
    public final NamespacedKey arenaItem;
    public final NamespacedKey teleportAllow;
    public final NamespacedKey elimToken;
    public final NamespacedKey bossMob;

    public ArenaKeys(AegisGuard plugin) {
        this.runId = new NamespacedKey(plugin, "arena_run");
        this.arenaId = new NamespacedKey(plugin, "arena_id");
        this.arenaItem = new NamespacedKey(plugin, "arena_item");
        this.teleportAllow = new NamespacedKey(plugin, "arena_tp_allow");
        this.elimToken = new NamespacedKey(plugin, "arena_elim");
        this.bossMob = new NamespacedKey(plugin, "arena_boss");
    }

    public void tagItem(ItemStack stack, UUID runIdValue) {
        if (stack == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(arenaItem, PersistentDataType.STRING, runIdValue.toString());
        stack.setItemMeta(meta);
    }

    @Override
    public boolean isArenaItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(arenaItem, PersistentDataType.STRING);
    }

    public void tagEntity(PersistentDataContainer pdc, UUID run, String arena) {
        tagEntity(pdc, run, arena, false);
    }

    public void tagEntity(PersistentDataContainer pdc, UUID run, String arena, boolean boss) {
        if (pdc == null) return;
        if (run != null) pdc.set(runId, PersistentDataType.STRING, run.toString());
        if (arena != null) pdc.set(arenaId, PersistentDataType.STRING, arena);
        if (boss) pdc.set(bossMob, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isBoss(PersistentDataContainer pdc) {
        return pdc != null && pdc.has(bossMob, PersistentDataType.BYTE);
    }

    public UUID readRunId(PersistentDataContainer pdc) {
        if (pdc == null || !pdc.has(runId, PersistentDataType.STRING)) return null;
        try {
            return UUID.fromString(pdc.get(runId, PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }

    public String readArenaId(PersistentDataContainer pdc) {
        if (pdc == null || !pdc.has(arenaId, PersistentDataType.STRING)) return null;
        return pdc.get(arenaId, PersistentDataType.STRING);
    }
}
