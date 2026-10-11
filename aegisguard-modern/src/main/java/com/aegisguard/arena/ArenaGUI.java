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
import java.util.Map;

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
        private final java.util.Map<Integer, com.aegisguard.network.NetworkModels.RemoteArena> remoteSlots;
        public ArenaMenuHolder(int page,
                               java.util.Map<Integer, com.aegisguard.network.NetworkModels.RemoteArena> remoteSlots) {
            this.page = page;
            this.remoteSlots = remoteSlots == null ? java.util.Map.of() : remoteSlots;
        }
        public int getPage() { return page; }
        public com.aegisguard.network.NetworkModels.RemoteArena remoteAt(int slot) {
            return remoteSlots.get(slot);
        }
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

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!service.isEnabled()) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        // Remote arenas come from the shared SQL registry — fetch async, then
        // render on the entity thread (same pattern as the beacon GUI).
        var net = plugin.network();
        if (net == null || !net.isNetworked() || net.store() == null) {
            render(player, page, List.of());
            return;
        }
        String localName = net.serverName();
        plugin.scheduler().runAsync(() -> {
            List<com.aegisguard.network.NetworkModels.RemoteArena> remotes =
                    net.store().remoteArenas(localName);
            plugin.scheduler().runEntity(player,
                    () -> render(player, page, remotes == null ? List.of() : remotes), null);
        });
    }

    private void render(Player player, int page,
                        List<com.aegisguard.network.NetworkModels.RemoteArena> remotes) {
        List<ArenaDefinition> arenas = new ArrayList<>(service.allArenas());
        arenas.sort(Comparator.comparing(ArenaDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        int perPage = 21;
        int maxPages = Math.max(1, (int) Math.ceil(arenas.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, maxPages - 1));

        String title = plugin.gui().title(player, "arena_menu_title", "&6Arenas");
        java.util.Map<Integer, com.aegisguard.network.NetworkModels.RemoteArena> remoteSlots =
                new java.util.HashMap<>();
        Inventory inv = Bukkit.createInventory(new ArenaMenuHolder(safePage, remoteSlots), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (arenas.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "arena_none_title", "&7No Arenas Yet"),
                    tl(player, "arena_none_lore", List.of(
                            "&7Staff have not configured any",
                            "&7dungeon arenas yet."))));
        }

        int start = safePage * perPage;
        for (int i = 0; i < perPage && start + i < arenas.size(); i++) {
            ArenaDefinition def = arenas.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(GUIManager.color(def.isEnabled()
                    ? t(player, "arena_enabled", "&aEnabled")
                    : t(player, "arena_status_disabled", "&cDisabled")));
            lore.add(GUIManager.color(t(player, "arena_mode_line",
                    Map.of("MODE", def.getMode().name()), "&7Mode: &f{MODE}")));
            lore.add(GUIManager.color(t(player, "arena_players_line",
                    Map.of("MIN", String.valueOf(def.getMinPlayers()),
                            "MAX", String.valueOf(def.getMaxPlayers())),
                    "&7Players: &f{MIN}-{MAX}")));
            lore.add(GUIManager.color(t(player, "arena_active_runs_line",
                    Map.of("COUNT", String.valueOf(service.countActiveRuns(def.getId()))),
                    "&7Active runs: &f{COUNT}")));
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "arena_click_detail", "&eClick for details.")));
            inv.setItem(i, GUIManager.createItem(
                    def.isEnabled() ? Material.NETHERITE_SWORD : Material.STONE_SWORD,
                    "&e" + def.getDisplayName(), lore));
        }

        // Remote arena band — bottom content row (36-44). Clicking one hops the
        // player to the hosting backend and lands them in that arena.
        if (remotes != null && !remotes.isEmpty()) {
            var net = plugin.network();
            int slot = 36;
            for (com.aegisguard.network.NetworkModels.RemoteArena ra : remotes) {
                if (ra == null || ra.arenaId() == null) continue;
                if (slot > 44) break;
                boolean online = net != null && net.isServerOnline(ra.serverName());
                List<String> lore = new ArrayList<>();
                lore.add(GUIManager.color(t(player, "arena_remote_server_line",
                        Map.of("SERVER", ra.serverName() == null ? "?" : ra.serverName()),
                        "&7Server: &f{SERVER}")));
                lore.add(GUIManager.color(t(player, "arena_mode_line",
                        Map.of("MODE", ra.mode() == null ? "?" : ra.mode()), "&7Mode: &f{MODE}")));
                lore.add(GUIManager.color(t(player, "arena_players_line",
                        Map.of("MIN", String.valueOf(ra.minPlayers()),
                                "MAX", String.valueOf(ra.maxPlayers())),
                        "&7Players: &f{MIN}-{MAX}")));
                lore.add(GUIManager.color(online
                        ? t(player, "arena_remote_online", "&aOnline — click to travel")
                        : t(player, "arena_remote_offline_lore", "&cServer offline")));
                remoteSlots.put(slot, ra);
                inv.setItem(slot, GUIManager.createItem(
                        online ? Material.ENDER_PEARL : Material.GRAY_DYE,
                        "&d" + ra.label(), lore));
                slot++;
            }
        }

        inv.setItem(45, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the player menu."))));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                t(player, "button_close", "&cClose"),
                tl(player, "close_lore", List.of("&7Close this menu."))));
        if (safePage > 0) {
            inv.setItem(48, GUIManager.createItem(Material.ARROW,
                    t(player, "arena_prev_page", "&fPrevious"), List.of()));
        }
        if (safePage < maxPages - 1) {
            inv.setItem(50, GUIManager.createItem(Material.ARROW,
                    t(player, "arena_next_page", "&fNext"), List.of()));
        }
        var queueSvc = plugin.arenaQueue();
        if (queueSvc != null && queueSvc.isEnabled()) {
            boolean queued = queueSvc.isQueued(player.getUniqueId());
            inv.setItem(53, GUIManager.createItem(Material.NETHER_STAR,
                    t(player, "arena_queue_any_button",
                            queued ? "&cLeave Matchmaking" : "&dQuick Match — Any Arena"),
                    tl(player, "arena_queue_any_lore", List.of(
                            "&7Join whichever arena queue fills",
                            "&7first. Parties queue together."))));
        }

        player.openInventory(inv);
    }

    public void openDetail(Player player, String arenaId) {
        ArenaDefinition def = service.getArena(arenaId);
        if (def == null) {
            open(player);
            return;
        }
        String title = plugin.gui().title(player, "arena_detail_title",
                "&6{NAME}", Map.of("NAME", def.getDisplayName()));
        Inventory inv = Bukkit.createInventory(new ArenaDetailHolder(def.getId()), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> header = new ArrayList<>();
        header.add(GUIManager.color(t(player, "arena_id_line",
                Map.of("ID", def.getId()), "&7Id: &f{ID}")));
        header.add(GUIManager.color(t(player, "arena_mode_line",
                Map.of("MODE", def.getMode().name()), "&7Mode: &f{MODE}")));
        header.add(GUIManager.color(def.isEnabled()
                ? t(player, "arena_ready", "&aReady")
                : t(player, "arena_not_available", "&cNot available")));
        if (def.getConfigError() != null) {
            header.add(GUIManager.color("&c" + def.getConfigError()));
        }
        inv.setItem(4, GUIManager.createItem(Material.MAP, "&e" + def.getDisplayName(), header));

        inv.setItem(10, GUIManager.createItem(Material.EMERALD,
                t(player, "arena_join", "&aJoin / Start"),
                tl(player, "arena_join_lore", List.of("&7Start a run with your party."))));
        var queue = plugin.arenaQueue();
        boolean queued = queue != null
                && def.getId().equalsIgnoreCase(queue.queuedArena(player.getUniqueId()));
        inv.setItem(12, GUIManager.createItem(Material.CLOCK,
                t(player, "arena_queue_button",
                        queued ? "&cLeave Queue" : "&eQueue for Matchmaking"),
                tl(player, "arena_queue_lore", List.of(
                        "&7Wait for other players to queue",
                        "&7and run this arena together."))));
        inv.setItem(14, GUIManager.createItem(Material.ENDER_EYE,
                t(player, "arena_spectate", "&bSpectate"),
                tl(player, "arena_spectate_lore", List.of("&7Teleport to the spectator spawn."))));
        inv.setItem(16, GUIManager.createItem(Material.PLAYER_HEAD,
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
            if (slot == 53) {
                player.closeInventory();
                toggleQueue(player, "any");
                return;
            }
            com.aegisguard.network.NetworkModels.RemoteArena remote = menu.remoteAt(slot);
            if (remote != null) {
                player.closeInventory();
                var net = plugin.network();
                var travel = plugin.networkTravel();
                if (net == null || travel == null
                        || !net.isServerOnline(remote.serverName())) {
                    plugin.msg().send(player, "arena_remote_offline",
                            Map.of("SERVER", remote.serverName() == null ? "?" : remote.serverName()));
                    return;
                }
                travel.sendToArenaJoin(player, remote.serverName(), remote.arenaId());
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
            if (slot == 10) {
                player.closeInventory();
                // Same routing as /ag arena join: own lobby → start now,
                // someone else's open lobby → attach, else fresh run.
                ArenaRun mine = service.getRunForPlayer(player.getUniqueId());
                if (mine != null && mine.isInLobby()
                        && mine.getArenaId().equalsIgnoreCase(def.getId())) {
                    if (service.startLobbyNow(player)) {
                        plugin.msg().send(player, "arena_lobby_started_early");
                    } else {
                        plugin.msg().send(player, "arena_not_leader");
                    }
                    return;
                }
                ArenaRun lobby = service.findOpenLobby(def.getId());
                if (lobby != null) {
                    String err = service.joinLobbyRun(player, lobby, null);
                    if (err != null) sendFail(player, err);
                    return;
                }
                String err = service.tryStart(player, def.getId());
                if (err != null) sendFail(player, err);
                else plugin.msg().send(player, "arena_run_started");
                return;
            }
            if (slot == 12) {
                player.closeInventory();
                toggleQueue(player, def.getId());
                return;
            }
            if (slot == 14) {
                player.closeInventory();
                var loc = service.toLocation(def.getSpectatorSpawn() != null
                        ? def.getSpectatorSpawn() : def.getExitSpawn());
                if (loc == null) {
                    plugin.msg().send(player, "arena_no_spectator_spawn");
                    return;
                }
                service.teleportPlayerAllowed(player, loc);
                return;
            }
            if (slot == 16) {
                plugin.msg().send(player, "arena_party_invite_hint");
            }
        }
    }

    /** Queue toggle shared by the detail button and the menu quick-match. */
    private void toggleQueue(Player player, String arenaId) {
        var queue = plugin.arenaQueue();
        if (queue == null || !queue.isEnabled()) {
            plugin.msg().send(player, "arena_queue_disabled");
            return;
        }
        if (queue.isQueued(player.getUniqueId())) {
            if (queue.leaveQueue(player)) plugin.msg().send(player, "arena_queue_left");
            return;
        }
        String err = queue.joinQueue(player, arenaId);
        if (err != null) {
            sendFail(player, err);
            return;
        }
        String queuedFor = queue.queuedArena(player.getUniqueId());
        plugin.msg().send(player, "arena_queue_joined",
                Map.of("ARENA", queuedFor == null ? arenaId : queuedFor));
    }
}
