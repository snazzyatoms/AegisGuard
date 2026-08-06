package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import com.aegisguard.arena.preset.LavaDungeonPreset;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Staff Arena admin tools: list, create preset, enable/disable, abort live runs, diagnostics.
 */
public final class ArenaAdminGUI {

    private final AegisGuard plugin;
    private final ArenaService service;

    public ArenaAdminGUI(AegisGuard plugin, ArenaService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public static class ArenaAdminHolder implements InventoryHolder {
        private final int page;
        public ArenaAdminHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class ArenaAdminEditHolder implements InventoryHolder {
        private final String arenaId;
        public ArenaAdminEditHolder(String arenaId) { this.arenaId = arenaId; }
        public String getArenaId() { return arenaId; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class ArenaAdminRunsHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        return plugin.gui().tr(p, key, fallback, vars);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private void sendFail(Player player, String key) {
        if (key == null) return;
        Map<String, String> vars = service.takeFailVars();
        if (vars.isEmpty()) plugin.msg().send(player, key);
        else plugin.msg().send(player, key, vars);
    }

    private boolean canEdit(Player player) {
        return player != null && (player.hasPermission("aegis.arena.admin") || plugin.isAdmin(player));
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!canEdit(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        List<ArenaDefinition> arenas = new ArrayList<>(service.allArenas());
        arenas.sort(Comparator.comparing(ArenaDefinition::getId));
        int perPage = 21;
        int maxPages = Math.max(1, (int) Math.ceil(arenas.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, maxPages - 1));

        String title = plugin.gui().title(player, "arena_admin_title", "&cArena Admin");
        Inventory inv = Bukkit.createInventory(new ArenaAdminHolder(safePage), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int start = safePage * perPage;
        for (int i = 0; i < perPage && start + i < arenas.size(); i++) {
            ArenaDefinition def = arenas.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(GUIManager.color(def.isEnabledFlag()
                    ? t(player, "arena_enabled", "&aEnabled")
                    : t(player, "arena_disabled", "&cDisabled")));
            lore.add(GUIManager.color(def.isConfigValid()
                    ? t(player, "arena_admin_config_ok", "&aConfig OK")
                    : "&c" + def.getConfigError()));
            lore.add(GUIManager.color(t(player, "arena_admin_active_line",
                    Map.of("COUNT", String.valueOf(service.countActiveRuns(def.getId()))),
                    "&7Active: &f{COUNT}")));
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "arena_admin_click_edit", "&eClick to edit.")));
            inv.setItem(i, GUIManager.createItem(
                    def.isEnabled() ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD,
                    "&e" + def.getId(), lore));
        }

        inv.setItem(39, GUIManager.createItem(Material.LAVA_BUCKET,
                t(player, "arena_admin_preset", "&6＋ Create lava_dungeon"),
                tl(player, "arena_admin_preset_lore", List.of(
                        "&7Creates a new arena from the",
                        "&7lava_dungeon preset template."))));
        inv.setItem(40, GUIManager.createItem(Material.REDSTONE_TORCH,
                t(player, "arena_admin_runs", "&cLive Runs"),
                tl(player, "arena_admin_runs_lore", List.of("&7View and abort active runs."))));
        inv.setItem(41, GUIManager.createItem(Material.BOOK,
                t(player, "arena_admin_diag", "&eDiagnostics"),
                tl(player, "arena_admin_diag_lore", List.of("&7Print arena diagnostics."))));

        inv.setItem(45, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the admin panel."))));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                t(player, "button_close", "&cClose"),
                List.of()));

        player.openInventory(inv);
    }

    public void openEdit(Player player, String arenaId) {
        ArenaDefinition def = service.getArena(arenaId);
        if (def == null) {
            open(player);
            return;
        }
        String title = plugin.gui().title(player, "arena_admin_edit_title",
                "&cEdit: {ID}", Map.of("ID", def.getId()));
        Inventory inv = Bukkit.createInventory(new ArenaAdminEditHolder(def.getId()), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(4, GUIManager.createItem(Material.NAME_TAG, "&e" + def.getDisplayName(), List.of(
                GUIManager.color("&7" + def.getId()),
                GUIManager.color(def.isConfigValid()
                        ? t(player, "arena_admin_valid", "&aValid")
                        : "&c" + def.getConfigError()))));

        inv.setItem(10, GUIManager.createItem(Material.LIME_DYE,
                def.isEnabledFlag()
                        ? t(player, "arena_admin_toggle_disable", "&cDisable")
                        : t(player, "arena_admin_toggle_enable", "&aEnable"),
                tl(player, "arena_admin_toggle_lore", List.of("&7Toggle arena enabled flag."))));
        inv.setItem(12, GUIManager.createItem(Material.COMPASS,
                t(player, "arena_admin_set_lobby", "&eSet lobby plot"),
                tl(player, "arena_admin_set_lobby_lore", List.of("&7Bind standing plot as lobby."))));
        inv.setItem(14, GUIManager.createItem(Material.NETHER_BRICK,
                t(player, "arena_admin_set_floor", "&eSet floor plot"),
                tl(player, "arena_admin_set_floor_lore", List.of("&7Bind standing plot as combat floor."))));
        inv.setItem(16, GUIManager.createItem(Material.ENDER_PEARL,
                t(player, "arena_admin_set_entry", "&eSet entry spawn"),
                tl(player, "arena_admin_set_entry_lore", List.of("&7Use your current location."))));
        inv.setItem(22, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"), List.of()));

        player.openInventory(inv);
    }

    public void openRuns(Player player) {
        String title = plugin.gui().title(player, "arena_admin_runs_title", "&cLive Arena Runs");
        Inventory inv = Bukkit.createInventory(new ArenaAdminRunsHolder(), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        for (ArenaRun run : service.allActiveRuns()) {
            if (slot >= 45) break;
            List<String> lore = new ArrayList<>();
            lore.add(GUIManager.color(t(player, "arena_admin_run_arena_line",
                    Map.of("ID", run.getArenaId()), "&7Arena: &f{ID}")));
            lore.add(GUIManager.color(t(player, "arena_admin_run_state_line",
                    Map.of("STATE", String.valueOf(run.getState())), "&7State: &f{STATE}")));
            lore.add(GUIManager.color(t(player, "arena_admin_run_wave_line",
                    Map.of("WAVE", String.valueOf(run.getDeepestWave())), "&7Wave: &f{WAVE}")));
            lore.add(GUIManager.color(t(player, "arena_admin_run_fighters_line",
                    Map.of("COUNT", String.valueOf(run.countFighting())), "&7Fighters: &f{COUNT}")));
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "arena_admin_run_abort_hint", "&cClick to abort.")));
            inv.setItem(slot++, GUIManager.createItem(Material.TNT,
                    "&c" + run.getRunId().toString().substring(0, 8), lore));
        }
        if (slot == 0) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "arena_admin_empty_runs", "&7No live runs."), List.of()));
        }
        inv.setItem(49, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"), List.of()));
        player.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        e.setCancelled(true);
        if (!canEdit(player)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        int slot = e.getSlot();

        if (holder instanceof ArenaAdminHolder menu) {
            if (slot == 45) {
                plugin.gui().admin().open(player);
                return;
            }
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot == 39) {
                String id = "lava_dungeon_" + (service.allArenas().size() + 1);
                ArenaDefinition def = service.applyLavaPreset(id);
                plugin.msg().send(player, "arena_created_from_preset", Map.of(
                        "ID", def.getId(),
                        "PRESET", LavaDungeonPreset.PRESET_ID));
                openEdit(player, def.getId());
                return;
            }
            if (slot == 40) {
                openRuns(player);
                return;
            }
            if (slot == 41) {
                player.closeInventory();
                plugin.msg().send(player, "arena_diag_header");
                for (String line : service.diagnostics().split("\n")) {
                    player.sendMessage("§7" + line);
                }
                return;
            }
            if (slot >= 0 && slot < 21) {
                List<ArenaDefinition> arenas = new ArrayList<>(service.allArenas());
                arenas.sort(Comparator.comparing(ArenaDefinition::getId));
                int index = menu.getPage() * 21 + slot;
                if (index >= 0 && index < arenas.size()) {
                    openEdit(player, arenas.get(index).getId());
                }
            }
            return;
        }

        if (holder instanceof ArenaAdminEditHolder edit) {
            ArenaDefinition def = service.getArena(edit.getArenaId());
            if (def == null) {
                open(player);
                return;
            }
            if (slot == 22) {
                open(player);
                return;
            }
            if (slot == 10) {
                String err = service.setArenaEnabled(def.getId(), !def.isEnabledFlag());
                if (err != null) sendFail(player, err);
                openEdit(player, def.getId());
                return;
            }
            if (slot == 12) {
                String err = service.bindLobbyFromPlayer(player, def.getId());
                if (err != null) sendFail(player, err);
                else plugin.msg().send(player, "arena_lobby_bound");
                openEdit(player, def.getId());
                return;
            }
            if (slot == 14) {
                String err = service.bindFloorFromPlayer(player, def.getId());
                if (err != null) sendFail(player, err);
                else plugin.msg().send(player, "arena_floor_bound");
                openEdit(player, def.getId());
                return;
            }
            if (slot == 16) {
                String err = service.setSpawn(player, def.getId(), "entry");
                if (err != null) sendFail(player, err);
                else plugin.msg().send(player, "arena_entry_spawn_set");
                openEdit(player, def.getId());
            }
            return;
        }

        if (holder instanceof ArenaAdminRunsHolder) {
            if (slot == 49) {
                open(player);
                return;
            }
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType() != Material.TNT) return;
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName().replaceAll("§.", "")
                    : "";
            for (ArenaRun run : service.allActiveRuns()) {
                if (run.getRunId().toString().startsWith(name.replace("§c", "").trim())
                        || run.getRunId().toString().substring(0, 8).equalsIgnoreCase(
                        name.replaceAll("§.", "").trim())) {
                    service.endRun(run, ArenaEndReason.ADMIN_ABORT);
                    plugin.msg().send(player, "arena_aborted_run_id",
                            Map.of("ID", run.getRunId().toString()));
                    openRuns(player);
                    return;
                }
            }
        }
    }
}
