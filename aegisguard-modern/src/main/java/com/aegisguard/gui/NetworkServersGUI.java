package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.network.NetworkModels.NetworkServer;
import com.aegisguard.network.NetworkService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cross-server selector ("compass") for BungeeCord-style networks — every backend
 * in {@code aegis_network_servers} rendered as a travel button. Online state comes
 * from heartbeat freshness on the shared DB clock; clicking hops through the same
 * pending-arrival + Connect path as remote plot visits.
 */
public class NetworkServersGUI {

    private static final int PAGE_SIZE = 45; // slots 0-44, nav row at bottom

    private final AegisGuard plugin;

    public NetworkServersGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class NetworkServersHolder implements InventoryHolder {
        private final List<NetworkServer> servers;
        private final int page;

        public NetworkServersHolder(List<NetworkServer> servers, int page) {
            this.servers = servers;
            this.page = page;
        }

        public List<NetworkServer> getServers() { return servers; }
        public int getPage() { return page; }

        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String out = plugin.gui().tr(p, key, fallback);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    public void open(Player player, int page) {
        NetworkService net = plugin.network();
        if (net == null || !net.isNetworked()) {
            plugin.msg().send(player, "network_disabled");
            plugin.effects().playError(player);
            return;
        }

        List<NetworkServer> servers = new ArrayList<>(net.servers());
        int pages = Math.max(1, (int) Math.ceil(servers.size() / (double) PAGE_SIZE));
        int clamped = Math.max(0, Math.min(page, pages - 1));

        String title = plugin.gui().title(player, "network_servers_title", "&bNetwork Servers");
        Inventory inv = Bukkit.createInventory(new NetworkServersHolder(servers, clamped), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        long now = net.networkNow();
        long offlineAfter = net.offlineAfterMillis();
        String self = net.serverName();

        int start = clamped * PAGE_SIZE;
        int end = Math.min(servers.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            NetworkServer server = servers.get(i);
            inv.setItem(i - start, serverItem(player, server, now, offlineAfter, self));
        }

        if (clamped > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW,
                    t(player, "button_prev_page", "&fPrevious Page"), null));
        }
        inv.setItem(49, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_to_hub_lore", List.of("&7Return to the Travel Hub."))));
        if (clamped < pages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW,
                    t(player, "button_next_page", "&fNext Page"), null));
        }

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private ItemStack serverItem(Player player, NetworkServer server,
                               long now, long offlineAfter, String self) {
        boolean isSelf = self != null && self.equalsIgnoreCase(server.serverName());
        boolean online = isSelf || server.isOnlineAt(now, offlineAfter);

        Material icon = isSelf ? Material.NETHER_STAR
                : online ? Material.ENDER_PEARL : Material.GRAY_DYE;

        String name = t(player,
                isSelf ? "network_server_self_name" : "network_server_name",
                Map.of("server", server.label()),
                isSelf ? "&a{server} &7(you are here)" : "&b{server}");

        List<String> lore = new ArrayList<>();
        lore.add(t(player,
                online ? "network_server_online" : "network_server_offline",
                online ? "&a● Online" : "&c● Offline"));
        if (online) {
            lore.add(t(player, "network_server_players",
                    Map.of("count", String.valueOf(Math.max(0, server.onlinePlayers()))),
                    "&7Players: &f{count}"));
        }
        if (server.pluginVersion() != null && !server.pluginVersion().isBlank()) {
            lore.add(t(player, "network_server_version",
                    Map.of("version", server.pluginVersion()),
                    "&7AegisGuard: &f{version}"));
        }
        lore.add("");
        lore.add(t(player, isSelf
                        ? "network_server_self_hint"
                        : online ? "network_server_click" : "network_server_offline_hint",
                isSelf ? "&7This is your current server."
                        : online ? "&eClick to travel there."
                        : "&7Unavailable while offline."));
        return GUIManager.createItem(icon, name, lore);
    }

    public void handleClick(Player player, InventoryClickEvent e, NetworkServersHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot == 45 && holder.getPage() > 0) {
            open(player, holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            plugin.gui().travelHub().open(player);
            return;
        }
        if (slot == 53) {
            open(player, holder.getPage() + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;

        int index = holder.getPage() * PAGE_SIZE + slot;
        if (index >= holder.getServers().size()) return;

        NetworkServer target = holder.getServers().get(index);
        NetworkService net = plugin.network();
        if (net == null || target == null || target.serverName() == null) return;

        if (target.serverName().equalsIgnoreCase(net.serverName())) {
            plugin.effects().playError(player);
            return;
        }
        var travel = plugin.networkTravel();
        if (travel == null || !travel.sendToServer(player, target.serverName())) {
            plugin.effects().playError(player);
        }
    }
}
