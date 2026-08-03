package com.aegisguard.guidance;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Milestone 5 (Clearer Player Guidance) - an optional, skippable, and always-replayable
 * two-page walkthrough shown after a player's first-ever claim. Purely informational: it never
 * changes any plot, permission, or preference other than the "seen" flag that stops it from
 * auto-opening a second time (tracked in {@link com.aegisguard.notify.PlayerNotificationSettings},
 * exactly the same store the "Repeat Notifications" and "Plot Greetings" toggles use).
 *
 * Always reachable again afterward via the "Replay Walkthrough" button in
 * {@link com.aegisguard.gui.SettingsGUI}, so nothing here is ever a one-time-only surface.
 */
public class FirstClaimWalkthroughGUI {

    private final AegisGuard plugin;

    public FirstClaimWalkthroughGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class WalkthroughHolder implements InventoryHolder {
        private final int page;
        public WalkthroughHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    private static final int PAGE_COUNT = 2;

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    public void open(Player player, int page) {
        int safePage = Math.max(0, Math.min(PAGE_COUNT - 1, page));
        String title = plugin.gui().title(player, "walkthrough_menu_title_page" + (safePage + 1),
                safePage == 0 ? "&bWelcome to AegisGuard (1/2)" : "&bWelcome to AegisGuard (2/2)");
        Inventory inv = Bukkit.createInventory(new WalkthroughHolder(safePage), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        if (safePage == 0) {
            inv.setItem(10, GUIManager.createItem(Material.GRASS_BLOCK,
                    t(player, "walkthrough_claim_name", "&aYour Claim is Protected"),
                    tl(player, "walkthrough_claim_lore", List.of(
                            "&7Nobody else can build, break, or open",
                            "&7containers here unless you allow it.",
                            "&7Use /ag info to see your claim's details."))));
            inv.setItem(12, GUIManager.createItem(Material.PLAYER_HEAD,
                    t(player, "walkthrough_roles_name", "&bTrust Friends with Roles"),
                    tl(player, "walkthrough_roles_lore", List.of(
                            "&7Open the Roles menu to give friends and",
                            "&7allies permanent build/container access.",
                            "&7Permanent trust never expires on its own."))));
            inv.setItem(14, GUIManager.createItem(Material.CLOCK,
                    t(player, "walkthrough_guestpass_name", "&eTemporary Guest Passes"),
                    tl(player, "walkthrough_guestpass_lore", List.of(
                            "&7Hosting an event or hiring a builder?",
                            "&7Issue a time-limited Guest Pass instead -",
                            "&7it expires automatically and never",
                            "&7touches anyone's permanent role."))));
            inv.setItem(16, GUIManager.createItem(Material.IRON_BARS,
                    t(player, "walkthrough_lockdown_name", "&cEmergency Lockdown"),
                    tl(player, "walkthrough_lockdown_lore", List.of(
                            "&7If something goes wrong, you can",
                            "&7instantly restrict building/containers",
                            "&7for everyone but you - reversible",
                            "&7any time, and never traps players."))));
        } else {
            inv.setItem(11, GUIManager.createItem(Material.NAME_TAG,
                    t(player, "walkthrough_profile_name", "&3Realm Profile"),
                    tl(player, "walkthrough_profile_lore", List.of(
                            "&7Give your claim a public name,",
                            "&7description, category, and greeting",
                            "&7so visitors know what it is."))));
            inv.setItem(13, GUIManager.createItem(Material.WRITTEN_BOOK,
                    t(player, "walkthrough_notice_name", "&dNoticeboard"),
                    tl(player, "walkthrough_notice_lore", List.of(
                            "&7Post short rules, event details, or",
                            "&7shop info for visitors with",
                            "&7/ag notice add <text>."))));
            inv.setItem(15, GUIManager.createItem(Material.KNOWLEDGE_BOOK,
                    t(player, "walkthrough_guide_name", "&6Guardian's Guide"),
                    tl(player, "walkthrough_guide_lore", List.of(
                            "&7Forgot something? Open the full guide",
                            "&7any time from the main menu, or",
                            "&7replay this walkthrough from Settings."))));
        }

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "walkthrough_back_lore", List.of("&7Go to the previous page."))));
        inv.setItem(22, GUIManager.createItem(Material.LIME_STAINED_GLASS_PANE,
                safePage < PAGE_COUNT - 1
                        ? t(player, "walkthrough_next_name", "&aNext Page")
                        : t(player, "walkthrough_done_name", "&aFinish"),
                tl(player, "walkthrough_next_lore", List.of("&7Continue the walkthrough."))));
        inv.setItem(26, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cSkip"),
                tl(player, "walkthrough_skip_lore", List.of("&7Close - you can replay this", "&7any time from Settings."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /** Called from the {@code PlotClaimEvent} listener; only opens if the player has not seen it and it is enabled. */
    public void openIfFirstClaim(Player player) {
        if (!isEnabled()) return;
        if (plugin.getNotificationManager() != null && plugin.getNotificationManager().hasSeenWalkthrough(player.getUniqueId())) {
            return;
        }
        markSeen(player);
        open(player, 0);
    }

    private void markSeen(Player player) {
        if (plugin.getNotificationManager() != null) {
            plugin.getNotificationManager().markWalkthroughSeen(player.getUniqueId());
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("first_claim_walkthrough.enabled", true);
    }

    public void handleClick(Player player, InventoryClickEvent e, WalkthroughHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        int page = holder.getPage();

        if (slot == 18) {
            if (page > 0) open(player, page - 1);
            return;
        }
        if (slot == 22) {
            if (page < PAGE_COUNT - 1) open(player, page + 1);
            else player.closeInventory();
            return;
        }
        if (slot == 26) {
            player.closeInventory();
        }
    }
}
