package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;

/**
 * Single-plot inspection card for staff ("/agadmin inspect").
 *
 * Shows the claim's owner, location, flags, membership, level, biome, and zone state with
 * teleport / open-in-registry / delete actions. The holder carries the live Plot reference, so
 * no PDC round-trip is needed for actions. Callers open this on the player's region thread after
 * resolving the plot (asynchronously where required) — same convention as AdminPlotListGUI.
 */
public class AdminPlotInspectGUI {

    private final AegisGuard plugin;

    public AdminPlotInspectGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class InspectHolder implements InventoryHolder {
        private final Plot plot;
        private Inventory inventory;

        public InspectHolder(Plot plot) {
            this.plot = plot;
        }

        public Plot getPlot() {
            return plot;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            player.sendMessage(plugin.gui().tr(player, "admin_inspect_none", "&cNot standing in a claim."));
            return;
        }

        String ownerName = plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown";
        String title = plugin.gui().title(player, "admin_inspect_title", "&4Inspect: {OWNER}",
                Map.of("OWNER", ownerName));

        InspectHolder holder = new InspectHolder(plot);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Owner head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta headMeta = head.getItemMeta();
        if (headMeta instanceof SkullMeta skull) {
            try {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(plot.getOwner()));
            } catch (Throwable ignored) {}
            skull.setDisplayName(GUIManager.color(
                    plugin.gui().tr(player, "admin_inspect_owner", "&bOwner: &f{OWNER}",
                            Map.of("OWNER", ownerName))));
            head.setItemMeta(skull);
        }
        inv.setItem(13, head);

        // Location / bounds card
        String world = plot.getWorld() == null || plot.getWorld().isBlank()
                ? plugin.gui().tr(player, "admin_plot_world_unknown", "Unknown") : plot.getWorld();
        int sizeX = Math.abs(plot.getX2() - plot.getX1()) + 1;
        int sizeZ = Math.abs(plot.getZ2() - plot.getZ1()) + 1;
        inv.setItem(20, card(player, Material.COMPASS, "admin_inspect_location", "&eLocation",
                "admin_inspect_location_lore",
                List.of("&7World: &f{WORLD}", "&7From: &a{X1}, {Z1}", "&7To: &a{X2}, {Z2}",
                        "&7Size: &f{SIZE}"),
                Map.of("WORLD", world, "X1", Integer.toString(plot.getX1()),
                        "Z1", Integer.toString(plot.getZ1()), "X2", Integer.toString(plot.getX2()),
                        "Z2", Integer.toString(plot.getZ2()), "SIZE", sizeX + "x" + sizeZ)));

        // Identity card
        String shortId = plot.getPlotId().toString();
        if (shortId.length() > 8) shortId = shortId.substring(0, 8);
        String biome = plot.getCustomBiome() == null || plot.getCustomBiome().isBlank()
                ? "-" : plot.getCustomBiome();
        inv.setItem(22, card(player, Material.NAME_TAG, "admin_inspect_identity", "&ePlot",
                "admin_inspect_identity_lore",
                List.of("&7ID: &f{ID}", "&7Level: &b{LEVEL}", "&7Biome: &f{BIOME}",
                        "&7Trusted: &f{TRUSTED}", "&7Flags set: &f{FLAGS}"),
                Map.of("ID", shortId, "LEVEL", Integer.toString(plot.getLevel()), "BIOME", biome,
                        "TRUSTED", Integer.toString(plot.countTrustedMembers()),
                        "FLAGS", Integer.toString(plot.getFlags().size()))));

        // Status card
        inv.setItem(24, card(player, Material.OBSERVER, "admin_inspect_status", "&eStatus",
                "admin_inspect_status_lore",
                List.of("{ZONE}", "{LOCKDOWN}"),
                Map.of("ZONE", plot.isServerZone()
                                ? plugin.gui().tr(player, "admin_inspect_zone_server", "&cServer Zone")
                                : plugin.gui().tr(player, "admin_inspect_zone_player", "&7Player claim"),
                        "LOCKDOWN", plot.isLockdownActive()
                                ? plugin.gui().tr(player, "admin_inspect_lockdown_on", "&4Lockdown active")
                                : plugin.gui().tr(player, "admin_inspect_lockdown_off", "&7No lockdown"))));

        // Teleport
        inv.setItem(38, actionItem(Material.ENDER_PEARL, "inspect_teleport", player,
                "admin_inspect_teleport", "&aTeleport to Plot",
                "admin_inspect_teleport_lore",
                List.of("&7Teleport to this claim's center.")));

        // Open in registry (owner-filtered list)
        inv.setItem(40, actionItem(Material.BOOKSHELF, "inspect_registry", player,
                "admin_inspect_registry", "&fOpen in Registry",
                "admin_inspect_registry_lore",
                List.of("&7Browse this owner's plots.")));

        // Delete (destructive click only)
        inv.setItem(42, actionItem(Material.TNT, "inspect_delete", player,
                "admin_inspect_delete", "&cDelete Plot",
                "admin_inspect_delete_lore",
                List.of("&7Drop (Q) or sneak-click to delete.", "&cThis cannot be undone.")));

        // Footer
        inv.setItem(49, actionItem(Material.NETHER_STAR, "back_admin", player,
                "button_back_admin", "&fBack to Admin",
                "back_admin_lore", List.of("&7Return to Admin Menu.")));
        inv.setItem(50, actionItem(Material.BARRIER, "close_menu", player,
                "button_exit", "&c✖ Close",
                "exit_lore", List.of("&7Close this menu.")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, InspectHolder holder) {
        e.setCancelled(true);
        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String action = plugin.gui().getAction(clicked);
        if (action == null) return;
        Plot plot = holder.getPlot();

        switch (action) {
            case "inspect_teleport" -> {
                Location loc = plot.getCenter(plugin);
                if (loc == null || loc.getWorld() == null) {
                    player.sendMessage(plugin.gui().tr(player, "admin_plot_invalid_location",
                            "&cInvalid world or location."));
                    plugin.effects().playError(player);
                    return;
                }
                var result = plugin.safeTravel().travel(player, loc,
                        com.aegisguard.travel.SafeTravelService.Kind.STAFF);
                if (!result.isSuccess()) return;
                plugin.msg().send(player, "admin_plot_teleport", Map.of("PLAYER", plot.getOwnerName()));
                plugin.effects().playConfirm(player);
                player.closeInventory();
            }
            case "inspect_registry" -> {
                if (plot.getOwner() != null) {
                    plugin.gui().plotList().openFor(player, plot.getOwner(), plot.getOwnerName(), 0);
                } else {
                    plugin.gui().plotList().open(player);
                }
            }
            case "inspect_delete" -> {
                if (!GuiClicks.destructive(e)) {
                    player.sendMessage(plugin.gui().tr(player, "admin_plot_delete_hint",
                            "&cDrop (Q) or sneak-click to delete this plot."));
                    plugin.effects().playError(player);
                    return;
                }
                plugin.runMain(player, () -> {
                    try {
                        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[AdminPlotInspectGUI] removePlot failed: " + t.getMessage());
                    }
                    plugin.msg().send(player, "admin_plot_deleted", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playUnclaim(player);
                    player.closeInventory();
                });
            }
            case "back_admin" -> plugin.gui().admin().open(player);
            case "close_menu" -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            default -> {}
        }
    }

    /** Info card: localized name + localized lore with {PLACEHOLDER} substitution. */
    private ItemStack card(Player player, Material material, String nameKey, String nameFallback,
                           String loreKey, List<String> loreFallback, Map<String, String> vars) {
        return GUIManager.createItem(material,
                plugin.gui().tr(player, nameKey, nameFallback, vars),
                plugin.gui().trList(player, loreKey, loreFallback, vars));
    }

    /** Clickable item carrying a PDC action tag. */
    private ItemStack actionItem(Material material, String action, Player player,
                                 String nameKey, String nameFallback,
                                 String loreKey, List<String> loreFallback) {
        ItemStack stack = GUIManager.createItem(material,
                plugin.gui().tr(player, nameKey, nameFallback),
                plugin.gui().trList(player, loreKey, loreFallback));
        plugin.gui().tagAction(stack, action);
        return stack;
    }
}
