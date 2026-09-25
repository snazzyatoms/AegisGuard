package com.aegisguard.publicbeta;

import com.aegisguard.AegisGuard;
import com.aegisguard.publicbeta.PublicBetaWorldService.Role;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Fail-closed inventory isolation between Public Beta world roles.
 *
 * <p>Each player has a separate state for Hub, Play, Test Lab, and any
 * unregistered world. A source state must be safely persisted before a
 * cross-scope teleport is permitted.</p>
 */
public final class PublicBetaInventoryService implements Listener {

    private static final String ROOT = "public-beta-mode";
    private final AegisGuard plugin;
    private final File directory;
    private final ConcurrentHashMap<UUID, Transition> transitions = new ConcurrentHashMap<>();

    public PublicBetaInventoryService(AegisGuard plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "public-beta/inventories");
    }

    public boolean isEnabled() {
        return plugin.publicBeta() != null && plugin.publicBeta().isEnabled()
                && plugin.getConfig().getBoolean(ROOT + ".inventory-isolation.enabled", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        Scope scope = scope(player.getWorld().getName());
        File stored = file(player.getUniqueId(), scope);
        if (stored.isFile()) {
            if (!restore(player, scope)) {
                player.kickPlayer(color("&cAegisGuard could not safely restore your isolated inventory. Please contact staff."));
                return;
            }
        } else if (!save(player, scope)) {
            player.kickPlayer(color("&cAegisGuard could not safely protect your inventory. Please contact staff."));
            return;
        }
        ensureGuide(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beforeTeleport(PlayerTeleportEvent event) {
        if (!isEnabled() || event.getTo() == null || event.getTo().getWorld() == null) return;
        Scope from = scope(event.getFrom().getWorld().getName());
        Scope to = scope(event.getTo().getWorld().getName());
        if (from == to) return;
        Player player = event.getPlayer();
        if (!save(player, from)) {
            event.setCancelled(true);
            player.sendMessage(color(tr(player, "public_beta_inventory_save_failed",
                    "&cTravel was cancelled because your current inventory could not be protected.")));
            return;
        }
        transitions.put(player.getUniqueId(), new Transition(from, to));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterTeleport(PlayerTeleportEvent event) {
        Transition transition = transitions.remove(event.getPlayer().getUniqueId());
        if (transition == null || event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) return;
        Player player = event.getPlayer();
        String targetWorld = event.getTo().getWorld().getName();
        plugin.runEntityLater(player, () -> {
            if (!player.isOnline() || scope(player.getWorld().getName()) != transition.to()
                    || !player.getWorld().getName().equalsIgnoreCase(targetWorld)) return;
            if (!restore(player, transition.to())) {
                player.kickPlayer(color(tr(player, "public_beta_inventory_restore_failed",
                        "&cAegisGuard could not safely load your destination inventory. Please contact staff.")));
                return;
            }
            ensureGuide(player);
            player.sendMessage(color(tr(player, "public_beta_inventory_switched",
                    "&7Your isolated {SCOPE} inventory is now active.")
                    .replace("{SCOPE}", scopeDisplay(player, transition.to()))));
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!isEnabled()) return;
        transitions.remove(event.getPlayer().getUniqueId());
        save(event.getPlayer(), scope(event.getPlayer().getWorld().getName()));
    }

    public void saveOnlinePlayers() {
        if (!isEnabled()) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            save(player, scope(player.getWorld().getName()));
        }
    }

    private boolean save(Player player, Scope scope) {
        if (player == null || scope == null) return false;
        try {
            File target = file(player.getUniqueId(), scope);
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
            YamlConfiguration yaml = capture(player, scope);
            File temporary = new File(parent, target.getName() + ".tmp");
            yaml.save(temporary);
            YamlConfiguration check = YamlConfiguration.loadConfiguration(temporary);
            if (!instanceId().equals(check.getString("instance-id"))
                    || !scope.name().equals(check.getString("scope"))) {
                throw new IOException("Inventory snapshot identity validation failed");
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save isolated Public Beta inventory for "
                    + player.getName() + " in " + scope, error);
            return false;
        }
    }

    private YamlConfiguration capture(Player player, Scope scope) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("instance-id", instanceId());
        yaml.set("player-uuid", player.getUniqueId().toString());
        yaml.set("player-name", player.getName());
        yaml.set("scope", scope.name());
        yaml.set("saved-at", System.currentTimeMillis());
        yaml.set("world", player.getWorld().getName());
        yaml.set("game-mode", player.getGameMode().name());
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("exhaustion", player.getExhaustion());
        yaml.set("exp", player.getExp());
        yaml.set("level", player.getLevel());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("held-slot", player.getInventory().getHeldItemSlot());
        PlayerInventory inventory = player.getInventory();
        yaml.set("contents", cloneItems(inventory.getContents()));
        yaml.set("armor", cloneItems(inventory.getArmorContents()));
        yaml.set("off-hand", inventory.getItemInOffHand().clone());
        yaml.set("ender-chest", cloneItems(player.getEnderChest().getContents()));
        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(effect.getType().getName() + ":" + effect.getAmplifier() + ":" + effect.getDuration()
                    + ":" + effect.isAmbient() + ":" + effect.hasParticles() + ":" + effect.hasIcon());
        }
        yaml.set("effects", effects);
        return yaml;
    }

    private boolean restore(Player player, Scope scope) {
        File source = file(player.getUniqueId(), scope);
        if (!source.isFile()) {
            clear(player);
            applySafeDefaults(player, scope);
            return save(player, scope);
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
            if (!instanceId().equals(yaml.getString("instance-id"))
                    || !player.getUniqueId().toString().equals(yaml.getString("player-uuid"))
                    || !scope.name().equals(yaml.getString("scope"))) {
                throw new IOException("Inventory snapshot identity does not match this player, scope, and beta instance");
            }
            clear(player);
            player.setGameMode(parseGameMode(yaml.getString("game-mode")));
            double maximumHealth = 20.0D;
            AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) maximumHealth = max.getValue();
            player.setHealth(Math.max(1.0D, Math.min(maximumHealth, yaml.getDouble("health", 20.0D))));
            player.setFoodLevel(Math.max(0, Math.min(20, yaml.getInt("food", 20))));
            player.setSaturation((float) Math.max(0.0D, yaml.getDouble("saturation", 5.0D)));
            player.setExhaustion((float) Math.max(0.0D, yaml.getDouble("exhaustion", 0.0D)));
            player.setLevel(Math.max(0, yaml.getInt("level", 0)));
            player.setExp((float) Math.max(0.0D, Math.min(1.0D, yaml.getDouble("exp", 0.0D))));
            player.setTotalExperience(Math.max(0, yaml.getInt("total-exp", 0)));
            PlayerInventory inventory = player.getInventory();
            inventory.setContents(items(yaml.getList("contents"), inventory.getContents().length));
            inventory.setArmorContents(items(yaml.getList("armor"), 4));
            ItemStack offHand = yaml.getItemStack("off-hand");
            inventory.setItemInOffHand(offHand == null ? new ItemStack(org.bukkit.Material.AIR) : offHand.clone());
            player.getEnderChest().setContents(items(yaml.getList("ender-chest"), player.getEnderChest().getSize()));
            int held = yaml.getInt("held-slot", 0);
            inventory.setHeldItemSlot(Math.max(0, Math.min(8, held)));
            restoreEffects(player, yaml.getStringList("effects"));
            player.updateInventory();
            return true;
        } catch (Exception error) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore isolated Public Beta inventory for "
                    + player.getName() + " in " + scope, error);
            return false;
        }
    }

    private void clear(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
        player.getEnderChest().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setLevel(0);
        player.setExp(0.0F);
        player.setTotalExperience(0);
    }

    private void applySafeDefaults(Player player, Scope scope) {
        String configured = plugin.getConfig().getString(ROOT + ".inventory-isolation.default-game-mode",
                scope == Scope.HUB ? "ADVENTURE" : "SURVIVAL");
        player.setGameMode(parseGameMode(configured));
        double maximumHealth = 20.0D;
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) maximumHealth = max.getValue();
        player.setHealth(maximumHealth);
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setExhaustion(0.0F);
    }

    private void restoreEffects(Player player, List<String> encoded) {
        for (String raw : encoded) {
            try {
                String[] parts = raw.split(":");
                if (parts.length < 3) continue;
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type == null) continue;
                boolean ambient = parts.length > 3 && Boolean.parseBoolean(parts[3]);
                boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4]);
                boolean icon = parts.length <= 5 || Boolean.parseBoolean(parts[5]);
                player.addPotionEffect(new PotionEffect(type, Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[1]), ambient, particles, icon));
            } catch (RuntimeException ignored) {
                // Ignore one malformed effect without discarding the protected inventory.
            }
        }
    }

    private void ensureGuide(Player player) {
        if (plugin.publicBeta() != null) plugin.publicBeta().ensureGuide(player);
    }

    private Scope scope(String worldName) {
        PublicBetaWorldService worlds = plugin.publicBetaWorlds();
        if (worlds == null || worldName == null) return Scope.OTHER;
        if (worldName.equalsIgnoreCase(worlds.worldName(Role.WELCOME_HUB))) return Scope.HUB;
        if (worldName.equalsIgnoreCase(worlds.worldName(Role.PLAY_WORLD))) return Scope.PLAY;
        if (worldName.equalsIgnoreCase(worlds.worldName(Role.TEST_LAB))) return Scope.TEST_LAB;
        return Scope.OTHER;
    }

    private String scopeDisplay(Player player, Scope scope) {
        return switch (scope) {
            case HUB -> tr(player, "public_beta_inventory_scope_hub", "Welcome Hub");
            case PLAY -> tr(player, "public_beta_inventory_scope_play", "Play World");
            case TEST_LAB -> tr(player, "public_beta_inventory_scope_lab", "Test Lab");
            case OTHER -> tr(player, "public_beta_inventory_scope_other", "external world");
        };
    }

    private File file(UUID playerId, Scope scope) {
        return new File(new File(directory, playerId.toString()), scope.name().toLowerCase(Locale.ROOT) + ".yml");
    }

    private String instanceId() {
        return plugin.getConfig().getString(ROOT + ".isolation.instance-id", "aegisguard-public-beta").trim();
    }

    private GameMode parseGameMode(String value) {
        try { return GameMode.valueOf(value == null ? "SURVIVAL" : value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return GameMode.SURVIVAL; }
    }

    private List<ItemStack> cloneItems(ItemStack[] items) {
        List<ItemStack> copy = new ArrayList<>(items.length);
        for (ItemStack item : items) copy.add(item == null ? null : item.clone());
        return copy;
    }

    private ItemStack[] items(List<?> values, int size) {
        ItemStack[] result = new ItemStack[size];
        if (values == null) return result;
        for (int index = 0; index < Math.min(values.size(), size); index++) {
            Object value = values.get(index);
            if (value instanceof ItemStack item) result[index] = item.clone();
        }
        return result;
    }

    private String tr(Player player, String key, String fallback) {
        String value = plugin.gui().tr(player, key, fallback);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private String color(String value) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private enum Scope { HUB, PLAY, TEST_LAB, OTHER }
    private record Transition(Scope from, Scope to) { }
}
