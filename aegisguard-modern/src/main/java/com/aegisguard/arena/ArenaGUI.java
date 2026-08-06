package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
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

/**
 * Player-facing Arena hub: list arenas, join, spectate, party hint.
 */
public final class ArenaGUI {

    private final AegisGuard plugin;
    private final ArenaService service;

    public ArenaGUI(AegisGuard plugin, ArenaService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public static class ArenaMenuHolder implements InventoryHolder {
        private final int page;
        public ArenaMenuHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class ArenaDetailHolder implements InventoryHolder {
        private final String arenaId;
        public ArenaDetailHolder(String arenaId) { this.arenaId = arenaId; }
        public String getArenaId() { return arenaId; }
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!service.isEnabled()) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        List<ArenaDefinition> arenas = new ArrayList<>(service.allArenas());
        arenas.sort(Comparator.comparing(ArenaDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        int perPage = 21;
        int maxPages = Math.max(1, (int) Math.ceil(arenas.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, maxPages - 1));

        String title = plugin.gui().title(player, "arena_menu_title", "&6Arenas");
        Inventory inv = Bukkit.createInventory(new ArenaMenuHolder(safePage), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int start = safePage * perPage;
        for (int i = 0; i < perPage && start + i < arenas.size(); i++) {
            ArenaDefinition def = arenas.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(GUIManager.color(def.isEnabled()
                    ? t(player, "arena_enabled", "&aEnabled")
                    : t(player, "arena_disabled", "&cDisabled")));
            lore.add(GUIManager.color("&7Mode: &f" + def.getMode().name()));
            lore.add(GUIManager.color("&7Players: &f" + def.getMinPlayers() + "-" + def.getMaxPlayers()));
            lore.add(GUIManager.color("&7Active runs: &f" + service.countActiveRuns(def.getId())));
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "arena_click_detail", "&eClick for details.")));
            inv.setItem(i, GUIManager.createItem(
                    def.isEnabled() ? Material.NETHERITE_SWORD : Material.STONE_SWORD,
                    "&e" + def.getDisplayName(), lore));
        }

        inv.setItem(45, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the player menu."))));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                t(player, "button_close", "&cClose"),
                tl(player, "close_lore", List.of("&7Close this menu."))));
        if (safePage > 0) {
            inv.setItem(48, GUIManager.createItem(Material.ARROW, "&fPrevious", List.of()));
        }
        if (safePage < maxPages - 1) {
            inv.setItem(50, GUIManager.createItem(Material.ARROW, "&fNext", List.of()));
        }

        player.openInventory(inv);
    }

    public void openDetail(Player player, String arenaId) {
        ArenaDefinition def = service.getArena(arenaId);
        if (def == null) {
            open(player);
            return;
        }
        String title = plugin.gui().title(player, "arena_detail_title", "&6" + def.getDisplayName());
        Inventory inv = Bukkit.createInventory(new ArenaDetailHolder(def.getId()), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> header = new ArrayList<>();
        header.add(GUIManager.color("&7Id: &f" + def.getId()));
        header.add(GUIManager.color("&7Mode: &f" + def.getMode().name()));
        header.add(GUIManager.color(def.isEnabled() ? "&aReady" : "&cNot available"));
        if (def.getConfigError() != null) {
            header.add(GUIManager.color("&c" + def.getConfigError()));
        }
        inv.setItem(4, GUIManager.createItem(Material.MAP, "&e" + def.getDisplayName(), header));

        inv.setItem(11, GUIManager.createItem(Material.EMERALD,
                t(player, "arena_join", "&aJoin / Start"),
                tl(player, "arena_join_lore", List.of("&7Start a run with your party."))));
        inv.setItem(13, GUIManager.createItem(Material.ENDER_EYE,
                t(player, "arena_spectate", "&bSpectate"),
                tl(player, "arena_spectate_lore", List.of("&7Teleport to the spectator spawn."))));
        inv.setItem(15, GUIManager.createItem(Material.PLAYER_HEAD,
                t(player, "arena_party", "&eParty"),
                tl(player, "arena_party_lore", List.of(
                        "&7Use &f/ag arena party invite <player>",
                        "&7then accept to form a party."))));
        inv.setItem(22, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Back to arena list."))));

        player.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        int slot = e.getSlot();

        if (holder instanceof ArenaMenuHolder menu) {
            if (slot == 45) {
                plugin.gui().player().open(player);
                return;
            }
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot == 48) {
                open(player, menu.getPage() - 1);
                return;
            }
            if (slot == 50) {
                open(player, menu.getPage() + 1);
                return;
            }
            if (slot >= 0 && slot < 21) {
                ItemStack item = e.getCurrentItem();
                if (item == null || item.getType() == Material.AIR || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                    return;
                }
                List<ArenaDefinition> arenas = new ArrayList<>(service.allArenas());
                arenas.sort(Comparator.comparing(ArenaDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
                int index = menu.getPage() * 21 + slot;
                if (index >= 0 && index < arenas.size()) {
                    openDetail(player, arenas.get(index).getId());
                }
            }
            return;
        }

        if (holder instanceof ArenaDetailHolder detail) {
            ArenaDefinition def = service.getArena(detail.getArenaId());
            if (def == null) {
                open(player);
                return;
            }
            if (slot == 22) {
                open(player);
                return;
            }
            if (slot == 11) {
                player.closeInventory();
                String err = service.tryStart(player, def.getId());
                if (err != null) player.sendMessage("§c" + err);
                else player.sendMessage("§aArena run started.");
                return;
            }
            if (slot == 13) {
                player.closeInventory();
                var loc = service.toLocation(def.getSpectatorSpawn() != null
                        ? def.getSpectatorSpawn() : def.getExitSpawn());
                if (loc == null) {
                    player.sendMessage("§cNo spectator spawn set.");
                    return;
                }
                service.teleportPlayerAllowed(player, loc);
                return;
            }
            if (slot == 15) {
                player.sendMessage("§eUse /ag arena party invite <player>");
            }
        }
    }
}
