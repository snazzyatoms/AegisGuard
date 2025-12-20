package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.flags.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RolesGUI
 * - Manage trusted players and their permission levels.
 * - Also manages per-role flag permissions via a submenu.
 * - Fully localized (Codex-backed) with safe fallbacks + title clamp.
 */
public class RolesGUI {

    private final AegisGuard plugin;

    private static final List<String> ROLE_FLAG_KEYS = List.of(
            "PVP",
            "CONTAINERS",
            "MOBS",
            "PETS",
            "ENTITIES",
            "FARM",
            "TNT",
            "FIRE",
            "PISTON",
            "ENTRY",
            "SHOP",
            "FLY",
            "ANIMALS",
            "REDSTONE",
            "VEHICLES"
    );

    private static final int PLOTS_PER_PAGE = 45;
    private static final int ROLES_VISIBLE = 18;

    public RolesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------
    // HOLDERS
    // --------------------------------------------------

    public static class PlotSelectorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public static class RolesMenuHolder implements InventoryHolder {
        private final Plot plot;
        public RolesMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleAddHolder implements InventoryHolder {
        private final Plot plot;
        public RoleAddHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleManageHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        public RoleManageHolder(Plot plot, OfflinePlayer target) {
            this.plot = plot;
            this.target = target;
        }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleFlagsHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final String roleName;

        public RoleFlagsHolder(Plot plot, OfflinePlayer target, String roleName) {
            this.plot = plot;
            this.target = target;
            this.roleName = roleName;
        }

        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public String getRoleName() { return roleName; }

        @Override public Inventory getInventory() { return null; }
    }

    // --------------------------------------------------
    // TRANSLATION HELPERS
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    /**
     * Map-aware translate with safe fallback.
     * Tries Codex map API first (since many of your keys use {VARS}).
     */
    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, vars);
        } catch (Throwable ignored) {}

        String out = (raw == null || raw.isBlank() || raw.equalsIgnoreCase(key))
                ? (fallback == null ? "" : fallback)
                : raw;

        if (vars != null && !vars.isEmpty()) {
            for (Map.Entry<String, String> en : vars.entrySet()) {
                String k = en.getKey();
                String v = en.getValue() == null ? "" : en.getValue();
                out = out.replace("{" + k + "}", v).replace("{" + k.toLowerCase() + "}", v);
            }
        }

        return out;
    }

    /**
     * Map-aware list translate with safe fallback.
     * plugin.gui().trList doesn't know about {VARS}, so we apply replacements here.
     */
    private List<String> tl(Player p, String key, Map<String, String> vars, List<String> fallback) {
        List<String> base = plugin.gui().trList(p, key, fallback);
        if (base == null) base = List.of();

        List<String> out = new ArrayList<>(base.size());
        for (String line : base) {
            String s = (line == null) ? "" : line;

            if (vars != null && !vars.isEmpty()) {
                for (Map.Entry<String, String> en : vars.entrySet()) {
                    String k = en.getKey();
                    String v = en.getValue() == null ? "" : en.getValue();
                    s = s.replace("{" + k + "}", v).replace("{" + k.toLowerCase() + "}", v);
                }
            }

            out.add(GUIManager.color(s));
        }

        return out;
    }

    private String clampTitle(String raw, String fallback) {
        String tt = GUIManager.safeText(raw, fallback);
        tt = GUIManager.color(tt);
        if (tt.length() > 32) tt = tt.substring(0, 32);
        if (tt.endsWith("§")) tt = tt.substring(0, tt.length() - 1);
        return tt;
    }

    // --------------------------------------------------
    // ENTRY POINT
    // --------------------------------------------------

    public void open(Player player) {
        if (plugin.isAdmin(player)) {
            Plot standingPlot = plugin.store().getPlotAt(player.getLocation());
            if (standingPlot != null) {
                openRolesMenu(player, standingPlot);
                return;
            }
        }

        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        if (plots == null || plots.isEmpty()) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }

        if (plots.size() > 1) openPlotSelector(player, plots);
        else openRolesMenu(player, plots.get(0));
    }

    // --------------------------------------------------
    // GUI 1: SELECT PLOT
    // --------------------------------------------------

    private void openPlotSelector(Player player, List<Plot> plots) {
        String title = plugin.gui().title(player, "trusted_plot_selector_title", "&8Select Plot");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        for (Plot plot : plots) {
            if (slot >= PLOTS_PER_PAGE) break;

            List<String> lore = new ArrayList<>();

            String worldLine = t(player, "trusted_plot_world_line",
                    Map.of("WORLD", plot.getWorld()),
                    "&7World: &f{WORLD}"
            );
            lore.add(GUIManager.color(worldLine));

            int sizeX = (plot.getX2() - plot.getX1()) + 1;
            int sizeZ = (plot.getZ2() - plot.getZ1()) + 1;

            String sizeLine = t(player, "trusted_plot_size_line",
                    Map.of("X", String.valueOf(sizeX), "Z", String.valueOf(sizeZ)),
                    "&7Size: &e{X}x{Z}"
            );
            lore.add(GUIManager.color(sizeLine));

            lore.add(" ");

            String clickLine = t(player, "trusted_plot_click_manage", "&eClick to Manage Roles");
            lore.add(GUIManager.color(clickLine));

            String name = t(player, "trusted_plot_name",
                    Map.of("INDEX", String.valueOf(slot + 1)),
                    "&aPlot #{INDEX}"
            );

            inv.setItem(slot++, GUIManager.createItem(
                    Material.GRASS_BLOCK,
                    GUIManager.color(name),
                    lore
            ));
        }

        inv.setItem(49, GUIManager.createItem(
                Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --------------------------------------------------
    // GUI 2: ROLES LIST
    // --------------------------------------------------

    public void openRolesMenu(Player player, Plot plot) {
        String title = plugin.gui().title(player, "roles_gui_title", "&eManage Plot Roles");
        Inventory inv = Bukkit.createInventory(new RolesMenuHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        Map<UUID, String> roleMap = plot.getPlayerRoles();
        if (roleMap == null) roleMap = new LinkedHashMap<>();

        int slot = 0;
        for (Map.Entry<UUID, String> entry : roleMap.entrySet()) {
            if (slot >= 45) break;

            UUID uuid = entry.getKey();
            String role = entry.getValue();

            boolean isOwnerEntry = uuid.equals(plot.getOwner());
            boolean isViewerOwner = uuid.equals(player.getUniqueId());
            boolean isAdmin = plugin.isAdmin(player);

            if (isOwnerEntry && isViewerOwner) continue;
            if (isOwnerEntry && !isAdmin) continue;

            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = (member.getName() != null) ? member.getName() : "Unknown";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);

                String displayName = t(player, "roles_member_name",
                        Map.of("PLAYER", name),
                        "&e{PLAYER}"
                );
                meta.setDisplayName(GUIManager.color(displayName));

                List<String> lore = new ArrayList<>();

                String roleLine = t(player, "roles_member_role_line",
                        Map.of("ROLE", role),
                        "&7Role: &f{ROLE}"
                );
                lore.add(GUIManager.color(roleLine));
                lore.add(" ");

                String clickLine = t(player, "roles_member_click_lore", "&eClick to Edit Role & Permissions");
                lore.add(GUIManager.color(clickLine));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        inv.setItem(49, GUIManager.createItem(
                Material.EMERALD,
                t(player, "button_add_trusted", "&aAdd Trusted Player"),
                tl(player, "add_trusted_lore", List.of("&7Invite a nearby player to this dominion."))
        ));

        inv.setItem(48, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))
        ));

        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --------------------------------------------------
    // GUI 3: ADD PLAYER
    // --------------------------------------------------

    private void openAddMenu(Player player, Plot plot) {
        String title = plugin.gui().title(player, "add_trusted_title", "&8Add Trusted Player");
        Inventory inv = Bukkit.createInventory(new RoleAddHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (slot >= 45) break;
            if (nearby.getLocation().distance(player.getLocation()) > 50) continue;
            if (nearby.equals(player)) continue;
            if (plot.getPlayerRoles() != null && plot.getPlayerRoles().containsKey(nearby.getUniqueId())) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(nearby);

                String headName = t(player, "add_trusted_player_name",
                        Map.of("PLAYER", nearby.getName()),
                        "&a{PLAYER}"
                );

                String clickLore = t(player, "add_trusted_click_lore", "&7Click to add to plot.");
                meta.setDisplayName(GUIManager.color(headName));
                meta.setLore(List.of(GUIManager.color(clickLore)));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (slot == 0) {
            String noneName = t(player, "add_trusted_none_title", "&cNo Players Nearby");
            List<String> noneLore = tl(player, "add_trusted_none_lore", List.of("&7Ask your friend to stand closer!"));
            inv.setItem(22, GUIManager.createItem(Material.BARRIER, noneName, noneLore));
        }

        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))
        ));

        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 4: MANAGE SPECIFIC PLAYER
    // --------------------------------------------------

    private void openManageMenu(Player player, Plot plot, OfflinePlayer target) {
        String targetName = (target.getName() != null) ? target.getName() : "Unknown";

        String rawTitle = t(player, "roles_manage_title",
                Map.of("PLAYER", targetName),
                "&8Manage: {PLAYER}"
        );

        String title = clampTitle(rawTitle, "&8Manage: " + targetName);
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(plot, target), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> roles = plugin.cfg().getRoleNames();
        if (roles == null) roles = List.of();

        String currentRole = plot.getRole(target.getUniqueId());

        int max = Math.min(roles.size(), ROLES_VISIBLE);
        for (int i = 0; i < max; i++) {
            String roleName = roles.get(i);
            boolean isCurrent = roleName != null && roleName.equalsIgnoreCase(currentRole);

            Material icon = isCurrent ? Material.LIME_DYE : Material.GRAY_DYE;

            String displayRoleName = t(player, "roles_role_name",
                    Map.of("ROLE", roleName),
                    (isCurrent ? "&a" : "&7") + roleName
            );

            String loreLine = t(player,
                    isCurrent ? "roles_role_current_lore" : "roles_role_click_set_lore",
                    isCurrent ? "&a(Current Role)" : "&eClick to Set"
            );

            inv.setItem(i, GUIManager.createItem(
                    icon,
                    GUIManager.color(displayRoleName),
                    List.of(GUIManager.color(loreLine))
            ));
        }

        inv.setItem(18, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the roles list."))
        ));

        inv.setItem(22, GUIManager.createItem(
                Material.REDSTONE_BLOCK,
                t(player, "button_remove_trusted", "&cRemove Trusted"),
                tl(player, "remove_trusted_lore", List.of("&7Revoke all access."))
        ));

        String roleDisplay = (currentRole != null)
                ? currentRole
                : t(player, "roles_unassigned", "Unassigned");

        // ✅ NOW FULLY TRANSLATABLE LORE WITH {ROLE}
        List<String> permsLore = tl(player, "role_permissions_lore",
                Map.of("ROLE", roleDisplay),
                List.of(
                        "&7Role: &f{ROLE}",
                        " ",
                        "&7Adjust what this role may do",
                        "&7inside this dominion.",
                        " ",
                        "&eClick to open role flags"
                )
        );

        inv.setItem(24, GUIManager.createItem(
                Material.BOOK,
                t(player, "button_role_permissions", "&bEdit Role Permissions"),
                permsLore
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 5: ROLE FLAG PERMISSIONS
    // --------------------------------------------------

    private void openRoleFlagsMenu(Player player, Plot plot, OfflinePlayer target, String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            plugin.msg().send(player, "role_self");
            plugin.effects().playError(player);
            openManageMenu(player, plot, target);
            return;
        }

        String rawTitle = t(player, "role_flags_title",
                Map.of("ROLE", roleName),
                "&8Role Flags: {ROLE}"
        );
        String title = clampTitle(rawTitle, "&8Role Flags: " + roleName);

        Inventory inv = Bukkit.createInventory(new RoleFlagsHolder(plot, target, roleName), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int slot = 0;
        for (String flagKey : ROLE_FLAG_KEYS) {
            if (slot >= 18) break;

            TriState state = TriState.INHERIT;
            try {
                TriState stored = plugin.store().getRoleFlagState(plot, roleName, flagKey);
                if (stored != null) state = stored;
            } catch (NoSuchMethodError ignored) {}

            Material icon = mapFlagToIcon(flagKey);
            inv.setItem(slot++, buildRoleFlagItem(player, flagKey, state, icon));
        }

        inv.setItem(22, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))
        ));

        inv.setItem(26, GUIManager.createItem(
                Material.PAPER,
                t(player, "role_flags_legend_title", "&7Legend"),
                tl(player, "role_flags_legend_lore", List.of(
                        "&aAllow &7= Role may bypass this rule.",
                        "&cDeny  &7= Role is always blocked.",
                        "&7Inherit &7= Follow normal claim logic."
                ))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // HANDLERS
    // --------------------------------------------------

    public void handlePlotSelectorClick(Player player, InventoryClickEvent e, PlotSelectorHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); return; }

        if (slot >= PLOTS_PER_PAGE) return;

        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        if (plots == null || plots.isEmpty()) return;

        if (slot >= 0 && slot < plots.size()) openRolesMenu(player, plots.get(slot));
    }

    public void handleRolesMenuClick(Player player, InventoryClickEvent e, RolesMenuHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        int slot = e.getRawSlot();

        if (slot == 49) { openAddMenu(player, plot); return; }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) openManageMenu(player, plot, meta.getOwningPlayer());
        }
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, RoleAddHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        int slot = e.getRawSlot();

        if (slot == 49) { openRolesMenu(player, plot); return; }
        if (slot == 50) { player.closeInventory(); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) openManageMenu(player, plot, meta.getOwningPlayer());
        }
    }

    public void handleManageRoleClick(Player player, InventoryClickEvent e, RoleManageHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        int slot = e.getRawSlot();

        if (slot == 18) { openRolesMenu(player, plot); return; }

        if (slot == 22) {
            plugin.store().removePlayerRole(plot, target.getUniqueId());
            plugin.msg().send(player, "role_removed", Map.of("PLAYER", target.getName()));
            plugin.effects().playUnclaim(player);
            openRolesMenu(player, plot);
            return;
        }

        if (slot == 24) {
            String currentRole = plot.getRole(target.getUniqueId());
            if (currentRole == null) {
                plugin.msg().send(player, "role_self");
                plugin.effects().playError(player);
                return;
            }
            openRoleFlagsMenu(player, plot, target, currentRole);
            return;
        }

        List<String> roles = plugin.cfg().getRoleNames();
        if (roles == null) roles = List.of();

        int max = Math.min(roles.size(), ROLES_VISIBLE);
        if (slot >= 0 && slot < max) {
            String newRole = roles.get(slot);
            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", target.getName(), "ROLE", newRole));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot);
        }
    }

    public void handleRoleFlagsClick(Player player, InventoryClickEvent e, RoleFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        String roleName = holder.getRoleName();
        int slot = e.getRawSlot();

        if (slot == 22) { openManageMenu(player, plot, target); return; }
        if (slot < 0 || slot >= ROLE_FLAG_KEYS.size()) return;

        String flagKey = ROLE_FLAG_KEYS.get(slot);

        TriState current = TriState.INHERIT;
        try {
            TriState stored = plugin.store().getRoleFlagState(plot, roleName, flagKey);
            if (stored != null) current = stored;
        } catch (NoSuchMethodError ignored) {}

        TriState next = nextTriState(current);

        try {
            plugin.store().setRoleFlagState(plot, roleName, flagKey, next);
        } catch (NoSuchMethodError ignored) {}

        plugin.effects().playToggle(player);
        openRoleFlagsMenu(player, plot, target, roleName);
    }

    // --------------------------------------------------
    // SMALL HELPERS
    // --------------------------------------------------

    private TriState nextTriState(TriState current) {
        if (current == null || current == TriState.INHERIT) return TriState.ALLOW;
        if (current == TriState.ALLOW) return TriState.DENY;
        return TriState.INHERIT;
    }

    private Material mapFlagToIcon(String key) {
        switch (key.toUpperCase()) {
            case "PVP":        return Material.IRON_SWORD;
            case "CONTAINERS": return Material.CHEST;
            case "MOBS":       return Material.ZOMBIE_HEAD;
            case "PETS":       return Material.BONE;
            case "ENTITIES":   return Material.ARMOR_STAND;
            case "FARM":       return Material.WHEAT;
            case "TNT":        return Material.TNT;
            case "FIRE":       return Material.FLINT_AND_STEEL;
            case "PISTON":     return Material.PISTON;
            case "ENTRY":      return Material.OAK_DOOR;
            case "SHOP":       return Material.EMERALD;
            case "FLY":        return Material.FEATHER;
            case "ANIMALS":    return Material.COW_SPAWN_EGG;
            case "REDSTONE":   return Material.REDSTONE;
            case "VEHICLES":   return Material.MINECART;
            default:           return Material.PAPER;
        }
    }

    private ItemStack buildRoleFlagItem(Player player, String flagKey, TriState state, Material icon) {
        String pretty = flagKey.toLowerCase().replace("_", " ");
        if (!pretty.isEmpty()) pretty = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);

        String name = t(player, "role_flag_name_" + flagKey.toUpperCase(), "&b" + pretty);

        String allowLbl   = t(player, "role_flags_state_allow", "&aAllow");
        String denyLbl    = t(player, "role_flags_state_deny", "&cDeny");
        String inheritLbl = t(player, "role_flags_state_inherit", "&7Inherit");

        String stateLabel = (state == TriState.ALLOW) ? allowLbl : (state == TriState.DENY) ? denyLbl : inheritLbl;

        String currentLine = t(player, "role_flags_current_line",
                Map.of("STATE", stateLabel),
                "&7Current: {STATE}"
        );

        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(currentLine));
        lore.add(" ");

        lore.addAll(tl(player, "role_flags_item_lore", List.of(
                "&7Inherit: Follow normal plot/world rules.",
                "&7Allow: This role may bypass this restriction.",
                "&7Deny: This role is always blocked.",
                " ",
                "&eClick to cycle"
        )));

        return GUIManager.createItem(icon, GUIManager.color(name), lore);
    }
}
