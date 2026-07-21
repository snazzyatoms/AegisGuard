package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.GameRule;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class WorldControlsGUI {
    private static final int PAGE_SIZE = 36;
    private static final List<Rule> RULES = List.of(
            new Rule("pvp", "world_controls_pvp_name", "&cPvP Protection", Material.IRON_SWORD),
            new Rule("mobs", "world_controls_mobs_name", "&aHostile Mob Protection", Material.ZOMBIE_HEAD),
            new Rule("containers", "world_controls_containers_name", "&6Container Protection", Material.CHEST),
            new Rule("pets", "world_controls_pets_name", "&dPet Protection", Material.BONE),
            new Rule("farms", "world_controls_farms_name", "&eFarm Protection", Material.WHEAT),
            new Rule("animals", "world_controls_animals_name", "&aAnimal Protection", Material.LEAD),
            new Rule("redstone", "world_controls_redstone_name", "&cRedstone Protection", Material.REDSTONE),
            new Rule("vehicles", "world_controls_vehicles_name", "&bVehicle Protection", Material.MINECART),
            new Rule("entry", "world_controls_entry_name", "&bEntry Default", Material.OAK_DOOR)
    );
    private static final int[] RULE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20};
    private static final List<RuntimeRule> RUNTIME_RULES = List.of(
            new RuntimeRule("mob_spawning", "world_controls_spawn_mobs_name", "&aNatural Mob Spawning", Material.CREEPER_HEAD, GameRule.DO_MOB_SPAWNING),
            new RuntimeRule("daylight_cycle", "world_controls_daylight_name", "&eDaylight Cycle", Material.CLOCK, GameRule.DO_DAYLIGHT_CYCLE),
            new RuntimeRule("weather_cycle", "world_controls_weather_name", "&bWeather Cycle", Material.WATER_BUCKET, GameRule.DO_WEATHER_CYCLE),
            new RuntimeRule("keep_inventory", "world_controls_keep_inventory_name", "&6Keep Inventory", Material.TOTEM_OF_UNDYING, GameRule.KEEP_INVENTORY),
            new RuntimeRule("mob_griefing", "world_controls_mob_griefing_name", "&cMob Griefing", Material.TNT, GameRule.MOB_GRIEFING)
    );
    private static final int[] RUNTIME_SLOTS = {29, 30, 31, 32, 33};

    private final AegisGuard plugin;

    public WorldControlsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class WorldControlsHolder implements InventoryHolder {
        private final String worldName;
        private final int page;

        private WorldControlsHolder(String worldName, int page) {
            this.worldName = worldName;
            this.page = page;
        }

        public String worldName() { return worldName; }
        public int page() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        openList(player, 0);
    }

    private void openList(Player player, int requestedPage) {
        if (!canManage(player)) return;

        List<World> worlds = Bukkit.getWorlds().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = Math.max(1, (worlds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                new WorldControlsHolder(null, page),
                54,
                plugin.gui().titleWithPageSuffix(player, "world_controls_title", "&bWorld Controls", page + 1, pages)
        );
        fill(inventory);

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(worlds.size(), start + PAGE_SIZE); index++) {
            World world = worlds.get(index);
            boolean claimsAllowed = plugin.worldRules().allowClaims(world);
            Material material = switch (world.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            inventory.setItem(index - start + 9, GUIManager.createItem(
                    material,
                    plugin.gui().tr(player, "world_controls_world_name", "&b{WORLD}", Map.of("WORLD", world.getName())),
                    plugin.gui().trList(player, "world_controls_world_lore", List.of(
                            "&7Claims: {STATE}",
                            "&7Environment: &f{ENVIRONMENT}",
                            " ",
                            "&eClick to manage this world."
                    ), Map.of(
                            "STATE", state(player, claimsAllowed),
                            "ENVIRONMENT", environmentName(player, world)
                    ))
            ));
        }

        if (page > 0) inventory.setItem(45, nav(player, Material.ARROW, "button_previous_page", "&ePrevious Page"));
        inventory.setItem(48, nav(player, Material.NETHER_STAR, "button_back", "&eBack"));
        inventory.setItem(49, nav(player, Material.BARRIER, "button_exit", "&cClose"));
        if (page + 1 < pages) inventory.setItem(53, nav(player, Material.ARROW, "button_next_page", "&eNext Page"));

        player.openInventory(inventory);
        plugin.effects().playMenuOpen(player);
    }

    private void openWorld(Player player, World world, int listPage) {
        if (!canManage(player) || world == null) return;
        Inventory inventory = Bukkit.createInventory(
                new WorldControlsHolder(world.getName(), listPage),
                54,
                plugin.gui().title(player, "world_controls_detail_title", "&bWorld: {WORLD}", Map.of("WORLD", world.getName()))
        );
        fill(inventory);

        boolean claimsAllowed = plugin.worldRules().allowClaims(world);
        inventory.setItem(4, GUIManager.createItem(
                claimsAllowed ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                plugin.gui().tr(player, "world_controls_allow_claims_name", "&eAllow New Claims"),
                plugin.gui().trList(player, "world_controls_allow_claims_lore", List.of(
                        "&7Current: {STATE}",
                        "&7Immediately controls whether players",
                        "&7can create claims in this world.",
                        " ",
                        "&eClick to toggle."
                ), Map.of("STATE", state(player, claimsAllowed)))
        ));

        inventory.setItem(22, GUIManager.createItem(
                Material.SHIELD,
                plugin.gui().tr(player, "world_controls_protection_section_name", "&bProtection Defaults"),
                plugin.gui().trList(player, "world_controls_protection_section_lore", List.of(
                        "&7These defaults apply to newly created plots.",
                        "&8Existing plot settings remain untouched."
                ))
        ));

        for (int i = 0; i < RULES.size(); i++) {
            Rule rule = RULES.get(i);
            boolean enabled = plugin.worldRules().isProtectionEnabled(world, rule.key());
            inventory.setItem(RULE_SLOTS[i], GUIManager.createItem(
                    rule.material(),
                    plugin.gui().tr(player, rule.nameKey(), rule.fallback()),
                    plugin.gui().trList(player, "world_controls_rule_lore", List.of(
                            "&7Current: {STATE}",
                            "&7Default for newly created plots.",
                            "&8Existing plot settings are not overwritten.",
                            " ",
                            "&eClick to toggle."
                    ), Map.of("STATE", state(player, enabled)))
            ));
        }

        inventory.setItem(27, GUIManager.createItem(
                Material.COMMAND_BLOCK,
                plugin.gui().tr(player, "world_controls_runtime_section_name", "&6Live World Rules"),
                plugin.gui().trList(player, "world_controls_runtime_section_lore", List.of(
                        "&7These are real Minecraft game rules.",
                        "&7Changes affect this world immediately."
                ))
        ));
        for (int i = 0; i < RUNTIME_RULES.size(); i++) {
            RuntimeRule rule = RUNTIME_RULES.get(i);
            boolean enabled = Boolean.TRUE.equals(world.getGameRuleValue(rule.gameRule()));
            inventory.setItem(RUNTIME_SLOTS[i], GUIManager.createItem(
                    rule.material(),
                    plugin.gui().tr(player, rule.nameKey(), rule.fallback()),
                    plugin.gui().trList(player, "world_controls_gamerule_lore", List.of(
                            "&7Current: {STATE}",
                            "&7Applies immediately to this world.",
                            " ",
                            "&eClick to toggle."
                    ), Map.of("STATE", state(player, enabled)))
            ));
        }

        inventory.setItem(48, nav(player, Material.ARROW, "world_controls_back_worlds", "&eBack to Worlds"));
        inventory.setItem(50, nav(player, Material.BARRIER, "button_exit", "&cClose"));
        player.openInventory(inventory);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, WorldControlsHolder holder) {
        event.setCancelled(true);
        if (!canManage(player)
                || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (holder.worldName() == null) {
            if (slot == 45) openList(player, holder.page() - 1);
            else if (slot == 48) plugin.gui().admin().open(player);
            else if (slot == 49) close(player);
            else if (slot == 53) openList(player, holder.page() + 1);
            else if (slot >= 9 && slot < 45) {
                List<World> worlds = Bukkit.getWorlds().stream()
                        .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
                int index = holder.page() * PAGE_SIZE + slot - 9;
                if (index < worlds.size()) openWorld(player, worlds.get(index), holder.page());
            }
            return;
        }

        World world = Bukkit.getWorld(holder.worldName());
        if (world == null) {
            openList(player, holder.page());
            return;
        }
        if (slot == 48) {
            openList(player, holder.page());
            return;
        }
        if (slot == 50) {
            close(player);
            return;
        }
        if (slot == 4) {
            setRule(player, world, "allow_claims", !plugin.worldRules().allowClaims(world));
            openWorld(player, world, holder.page());
            return;
        }
        for (int i = 0; i < RULE_SLOTS.length; i++) {
            if (slot == RULE_SLOTS[i]) {
                Rule rule = RULES.get(i);
                setRule(player, world, "protections." + rule.key(), !plugin.worldRules().isProtectionEnabled(world, rule.key()));
                openWorld(player, world, holder.page());
                return;
            }
        }
        for (int i = 0; i < RUNTIME_SLOTS.length; i++) {
            if (slot == RUNTIME_SLOTS[i]) {
                RuntimeRule rule = RUNTIME_RULES.get(i);
                setGameRule(player, world, rule.gameRule());
                openWorld(player, world, holder.page());
                return;
            }
        }
    }

    private void setGameRule(Player player, World world, GameRule<Boolean> rule) {
        Boolean previous = world.getGameRuleValue(rule);
        boolean next = !Boolean.TRUE.equals(previous);
        try {
            if (!world.setGameRule(rule, next)) throw new IllegalStateException("Server rejected game rule " + rule.getName());
            plugin.effects().playConfirm(player);
        } catch (Throwable error) {
            if (previous != null) world.setGameRule(rule, previous);
            plugin.getLogger().warning("Could not update game rule " + rule.getName() + " for " + world.getName() + ": " + error.getMessage());
            player.sendMessage(plugin.gui().tr(player, "world_controls_save_failed",
                    "&cThat world rule could not be saved. Please check the console."));
            plugin.effects().playError(player);
        }
    }

    private void setRule(Player player, World world, String path, boolean value) {
        String fullPath = "claims.per_world." + world.getName() + "." + path;
        Object previous = plugin.getConfig().get(fullPath);
        try {
            plugin.getConfig().set(fullPath, value);
            plugin.saveConfig();
            plugin.worldRules().reload();
            plugin.effects().playConfirm(player);
        } catch (Throwable error) {
            plugin.getConfig().set(fullPath, previous);
            plugin.worldRules().reload();
            plugin.getLogger().warning("Could not save world control " + fullPath + ": " + error.getMessage());
            player.sendMessage(plugin.gui().tr(
                    player,
                    "world_controls_save_failed",
                    "&cThat world rule could not be saved. Please check the console."
            ));
            plugin.effects().playError(player);
        }
    }

    private boolean canManage(Player player) {
        if (player != null && (plugin.isAdmin(player) || player.hasPermission("aegis.admin.world"))) return true;
        if (player != null) {
            player.sendMessage(plugin.gui().tr(player, "no_perm", "&cYou do not have permission for this."));
            plugin.effects().playError(player);
        }
        return false;
    }

    private String state(Player player, boolean enabled) {
        return plugin.gui().tr(player, enabled ? "status_enabled" : "status_disabled", enabled ? "&aEnabled" : "&cDisabled");
    }

    private String environmentName(Player player, World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> plugin.gui().tr(player, "world_controls_environment_nether", "Nether");
            case THE_END -> plugin.gui().tr(player, "world_controls_environment_end", "The End");
            default -> plugin.gui().tr(player, "world_controls_environment_normal", "Overworld");
        };
    }

    private ItemStack nav(Player player, Material material, String key, String fallback) {
        return GUIManager.createItem(material, plugin.gui().tr(player, key, fallback), List.of());
    }

    private void fill(Inventory inventory) {
        ItemStack filler = GUIManager.getFiller();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private void close(Player player) {
        plugin.effects().playMenuClose(player);
        player.closeInventory();
    }

    private record Rule(String key, String nameKey, String fallback, Material material) { }
    private record RuntimeRule(String key, String nameKey, String fallback, Material material,
                               GameRule<Boolean> gameRule) { }
}
