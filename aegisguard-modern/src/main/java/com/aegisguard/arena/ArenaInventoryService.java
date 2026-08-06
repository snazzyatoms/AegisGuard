package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Atomic inventory snapshots for protected arena policies.
 */
public final class ArenaInventoryService {

    private final AegisGuard plugin;
    private final File invDir;

    public ArenaInventoryService(AegisGuard plugin) {
        this.plugin = plugin;
        this.invDir = new File(plugin.getDataFolder(), "arena-inv");
        //noinspection ResultOfMethodCallIgnored
        this.invDir.mkdirs();
    }

    public File directory() { return invDir; }

    public String snapshotPath(UUID playerId, UUID runId) {
        return new File(invDir, playerId + "-" + runId + ".yml").getAbsolutePath();
    }

    /**
     * Write tmp → validate → atomic replace, then return path. Caller clears inventory after success.
     */
    public String saveSnapshotAtomic(Player player, UUID runId, ArenaPersistenceQueue queue) throws Exception {
        if (player == null || runId == null) throw new IllegalArgumentException("player/runId");
        File target = new File(snapshotPath(player.getUniqueId(), runId));
        YamlConfiguration yaml = capture(player);
        // Synchronous atomic write for start-of-run safety (must complete before clear)
        queue.saveYamlAtomic(target, yaml, check -> {
            if (!check.contains("uuid")) throw new IllegalStateException("invalid snapshot");
        });
        return target.getAbsolutePath();
    }

    public YamlConfiguration capture(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", player.getUniqueId().toString());
        yaml.set("name", player.getName());
        yaml.set("gameMode", player.getGameMode().name());
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("exp", player.getExp());
        yaml.set("level", player.getLevel());
        yaml.set("totalExp", player.getTotalExperience());
        yaml.set("heldSlot", player.getInventory().getHeldItemSlot());
        yaml.set("world", player.getWorld().getName());
        yaml.set("worldId", player.getWorld().getUID().toString());
        yaml.set("x", player.getLocation().getX());
        yaml.set("y", player.getLocation().getY());
        yaml.set("z", player.getLocation().getZ());
        yaml.set("yaw", player.getLocation().getYaw());
        yaml.set("pitch", player.getLocation().getPitch());

        PlayerInventory inv = player.getInventory();
        yaml.set("contents", serializeItems(inv.getContents()));
        yaml.set("armor", serializeItems(inv.getArmorContents()));
        yaml.set("extra", serializeItems(new ItemStack[]{inv.getItemInOffHand()}));
        yaml.set("ender", serializeItems(player.getEnderChest().getContents()));

        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(effect.getType().getName() + ":" + effect.getAmplifier() + ":" + effect.getDuration());
        }
        yaml.set("effects", effects);
        return yaml;
    }

    public boolean restoreFromFile(Player player, String path) {
        if (player == null || path == null) return false;
        File file = new File(path);
        if (!file.exists()) return false;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            clearPlayer(player);
            player.setGameMode(GameMode.valueOf(yaml.getString("gameMode", "SURVIVAL")));
            player.setHealth(Math.min(player.getMaxHealth(), yaml.getDouble("health", 20.0D)));
            player.setFoodLevel(yaml.getInt("food", 20));
            player.setSaturation((float) yaml.getDouble("saturation", 5.0D));
            player.setLevel(yaml.getInt("level", 0));
            player.setExp((float) yaml.getDouble("exp", 0.0D));
            player.setTotalExperience(yaml.getInt("totalExp", 0));

            PlayerInventory inv = player.getInventory();
            inv.setContents(deserializeItems(yaml.getList("contents"), inv.getContents().length));
            inv.setArmorContents(deserializeItems(yaml.getList("armor"), 4));
            ItemStack[] extra = deserializeItems(yaml.getList("extra"), 1);
            if (extra.length > 0) inv.setItemInOffHand(extra[0]);
            player.getEnderChest().setContents(deserializeItems(yaml.getList("ender"), player.getEnderChest().getSize()));
            int held = yaml.getInt("heldSlot", 0);
            if (held >= 0 && held < 9) inv.setHeldItemSlot(held);

            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            List<String> effects = yaml.getStringList("effects");
            for (String raw : effects) {
                String[] parts = raw.split(":");
                if (parts.length < 3) continue;
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type == null) continue;
                player.addPotionEffect(new PotionEffect(type, Integer.parseInt(parts[2]), Integer.parseInt(parts[1])));
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to restore arena inventory snapshot " + path, e);
            return false;
        }
    }

    public void clearPlayer(Player player) {
        if (player == null) return;
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void stripArenaTaggedItems(Player player, NamespacedKeys keys) {
        if (player == null || keys == null) return;
        stripContainer(player.getInventory().getContents(), keys);
        stripContainer(player.getInventory().getArmorContents(), keys);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && keys.isArenaItem(off)) player.getInventory().setItemInOffHand(null);
        stripContainer(player.getEnderChest().getContents(), keys);
    }

    private void stripContainer(ItemStack[] items, NamespacedKeys keys) {
        if (items == null) return;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && keys.isArenaItem(items[i])) items[i] = null;
        }
    }

    private List<ItemStack> serializeItems(ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>();
        if (items == null) return list;
        for (ItemStack item : items) {
            list.add(item == null ? null : item.clone());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private ItemStack[] deserializeItems(List<?> raw, int size) {
        ItemStack[] out = new ItemStack[size];
        if (raw == null) return out;
        for (int i = 0; i < Math.min(size, raw.size()); i++) {
            Object o = raw.get(i);
            if (o instanceof ItemStack stack) out[i] = stack.clone();
        }
        return out;
    }

    /** Small helper for PDC arena-item checks without circular deps. */
    public interface NamespacedKeys {
        boolean isArenaItem(ItemStack stack);
    }
}
