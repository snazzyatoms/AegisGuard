package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlotStatusGUI {

    private final AegisGuard plugin;

    public PlotStatusGUI(AegisGuard AegisGuard) {
        this.plugin = AegisGuard;
    }

    public static class PlotStatusHolder implements InventoryHolder {
        private final Plot plot;
        private final int returnWalkthroughPage;
        public PlotStatusHolder(Plot plot) { this(plot, -1); }
        public PlotStatusHolder(Plot plot, int returnWalkthroughPage) {
            this.plot = plot;
            this.returnWalkthroughPage = returnWalkthroughPage;
        }
        public Plot getPlot() { return plot; }
        public int getReturnWalkthroughPage() { return returnWalkthroughPage; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        open(player, plot, -1);
    }

    /** Opens status with an optional walkthrough page to return to. */
    public void open(Player player, Plot plot, int returnWalkthroughPage) {
        if (plot == null) {
            // ✅ IMPORTANT FIX: System keys should come from system.yml (plugin.msg()),
            // not guis.yml (plugin.gui()).
            sendSystem(player, "no_plot_here", null, "&cYou are not standing in a protected plot.");
            try { plugin.effects().playError(player); } catch (Throwable ignored) {}
            return;
        }

        // Title matches the main-menu button ("Claim Status") in every language pack.
        String title = plugin.gui().title(player, "plot_status_gui_title",
                plugin.gui().tr(player, "plot_status_button_title", "&b✦ Claim Status"));
        Inventory inv = Bukkit.createInventory(new PlotStatusHolder(plot, returnWalkthroughPage), 54, title);

        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {
                0, 1, 2, 3, 5, 6, 7, 8,
                18, 26,
                27, 35,
                36, 44,
                45, 46, 47, 50, 51, 52, 53
        };
        for (int slot : borderSlots) inv.setItem(slot, filler);

        addSectionFrame(player, inv, Material.CYAN_STAINED_GLASS_PANE,
                "plot_status_section_overview_name", "&bOverview",
                "plot_status_section_overview_lore",
                List.of("&7A snapshot of this plot: protections,", "&7blessings, growth, and ClaimBlocks."),
                9, 10, 16, 17);
        addSectionFrame(player, inv, Material.ORANGE_STAINED_GLASS_PANE,
                "plot_status_section_actions_name", "&6Owner Actions",
                "plot_status_section_actions_lore",
                List.of("&7Merge, transfer, gift ClaimBlocks,", "&7or pay upkeep early."),
                19, 24, 25);

        int level = plot.getLevel();
        int maxLevel = plugin.cfg().getMaxLevel();
        String owner = plot.getOwnerName();
        String world = plot.getWorld();

        List<String> headerLore = new ArrayList<>();
        headerLore.add(plot.isServerZone()
                ? tr(player, "plot_status_kind_server", null, "&bServer / spawn plot")
                : tr(player, "plot_status_kind_personal", null, "&aPersonal plot"));
        headerLore.add(tr(player,
                "plot_status_owner_line", "plot_status_header_owner",
                "&7Owner: &f{OWNER}",
                Map.of("OWNER", owner == null || owner.isBlank() ? "—" : owner)
        ));
        headerLore.add(tr(player,
                "plot_status_world_line", "plot_status_header_world",
                "&7World: &f{WORLD}",
                Map.of("WORLD", world == null ? "—" : world)
        ));
        headerLore.add(tr(player,
                "plot_status_level_line", "plot_status_header_level",
                "&7Plot Level: &b{LEVEL}&7 / &f{MAX}",
                Map.of("LEVEL", String.valueOf(level), "MAX", String.valueOf(maxLevel))
        ));
        if (plot.getPlotName() != null && !plot.getPlotName().isBlank()) {
            headerLore.add(tr(player, "plot_status_name_line", null, "&7Name: &f{NAME}",
                    Map.of("NAME", plot.getPlotName())));
        }
        if (plot.isServerZone()) {
            headerLore.add(tr(player, "plot_status_server_zone_banner", null,
                    "&bServer Zone &7— managed by staff / Steward."));
        }

        String headerTitle = tr(player, "plot_status_header_title", "plot_status_button_title", "&b✦ Claim Status");
        inv.setItem(4, GUIManager.createItem(Material.NETHER_STAR, headerTitle, colorList(headerLore)));

        String protTitle = tr(player, "plot_status_protection_title", null, "&cProtections & Risks");
        inv.setItem(11, GUIManager.createItem(Material.SHIELD, protTitle, buildProtectionLore(player, plot)));

        List<String> buffsLore = new ArrayList<>();
        buffsLore.add(tr(player, "plot_status_blessings_header_line", "plot_status_blessings_header", "&7Active Blessings:"));
        buffsLore.add("");
        List<String> buffs = buildBuffList(player, level);
        if (buffs.isEmpty()) {
            buffsLore.add(tr(player, "plot_status_blessings_none_line", "plot_status_blessings_none", "&8- None unlocked yet."));
        } else {
            buffsLore.addAll(buffs);
            buffsLore.add("");
            buffsLore.add(tr(player, "plot_status_blessings_footer_line", "plot_status_blessings_footer",
                    "&8Only your highest tier of each blessing is shown."));
        }
        String blessingsTitle = tr(player, "plot_status_blessings_title", null, "&dActive Blessings");
        inv.setItem(12, GUIManager.createItem(Material.ENCHANTED_BOOK, blessingsTitle, colorList(buffsLore)));

        String territoryTitle = tr(player, "plot_status_territory_title", null, "&aTerritory & Growth");
        String expandName = tr(player, "button_expand", null, "&bExpand");
        String menuName = tr(player, "plot_status_menu_name", "menu_title", "&bAegis Menu");
        List<String> territoryLore = new ArrayList<>();
        territoryLore.add(tr(player, "plot_status_territory_rules_line", "plot_status_territory_rules", "&7Territory Rules:"));
        territoryLore.add(tr(player,
                "plot_status_territory_hint_line", "plot_status_territory_path",
                "&b{MENU} &7→ &b{EXPAND}",
                Map.of("MENU", menuName, "EXPAND", expandName)
        ));
        inv.setItem(13, GUIManager.createItem(Material.GRASS_BLOCK, territoryTitle, colorList(territoryLore)));

        inv.setItem(14, GUIManager.createItem(
                Material.PAPER,
                tr(player, "ledger_title", null, "&6ClaimBlocks"),
                buildLedgerLore(player, plot)
        ));
        inv.setItem(15, GUIManager.createItem(
                Material.PLAYER_HEAD,
                tr(player, "plot_status_access_title", null, "&eAccess Snapshot"),
                buildAccessLore(player, plot)
        ));

        boolean canOwn = plot.isOwner(player.getUniqueId());
        boolean mergeEnabled = plugin.getConfig().getBoolean("claims.merging.enabled", false);
        inv.setItem(20, GUIManager.createItem(
                canOwn && mergeEnabled ? Material.SLIME_BALL : Material.GRAY_DYE,
                tr(player, "button_claim_merge", null, "&aMerge Claims"),
                colorList(plugin.gui().trList(player, canOwn && mergeEnabled
                                ? "claim_merge_button_lore" : "claim_merge_button_locked_lore",
                        canOwn && mergeEnabled
                                ? List.of("&7Combine adjacent owned claims.")
                                : List.of("&7Owners can merge aligned claims", "&7when merging is enabled.")))
        ));
        inv.setItem(21, GUIManager.createItem(
                canOwn ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                tr(player, "button_transfer", null, "&eTransfer Ownership"),
                colorList(plugin.gui().trList(player, canOwn ? "transfer_button_lore" : "transfer_button_locked_lore",
                        canOwn
                                ? List.of("&7Transfer this plot with /ag transfer <player>",
                                "&7or confirm from chat after targeting.")
                                : List.of("&cOnly the owner can transfer this plot.")))
        ));
        inv.setItem(22, GUIManager.createItem(
                Material.GOLD_INGOT,
                tr(player, "button_giftblocks", null, "&aGift ClaimBlocks"),
                colorList(plugin.gui().trList(player, "giftblocks_button_lore",
                        List.of("&7Open the ClaimBlocks gift menu.")))
        ));

        if (plugin.getConfig().getBoolean("upkeep.enabled", false)
                || plugin.getConfig().getBoolean("economy.upkeep.enabled", false)) {
            double cost = plugin.getConfig().getDouble("upkeep.cost_per_plot",
                    plugin.getConfig().getDouble("economy.upkeep.cost", 0.0D));
            inv.setItem(23, GUIManager.createItem(Material.GOLD_NUGGET,
                    tr(player, "plot_status_upkeep_name", null, "&6Upkeep"),
                    colorList(List.of(
                            tr(player, "plot_status_upkeep_cost", null, "&7Cost: &6{COST}",
                                    Map.of("COST", plugin.eco().format(cost, com.aegisguard.economy.CurrencyType.VAULT))),
                            tr(player, "plot_status_upkeep_paid", null, "&7Last paid: &f{TIME}",
                                    Map.of("TIME", Long.toString(plot.getLastUpkeepPayment()))),
                            tr(player, "plot_status_upkeep_click", null, "&eClick to pay early.")
                    ))));
        } else {
            addSectionFrame(player, inv, Material.ORANGE_STAINED_GLASS_PANE,
                    "plot_status_section_actions_name", "&6Owner Actions",
                    "plot_status_section_actions_lore",
                    List.of("&7Merge, transfer, gift ClaimBlocks,", "&7or pay upkeep early."),
                    23);
        }

        String backName = tr(player, "button_back", null, "&fBack");
        List<String> backLore = plugin.gui().trList(player, "back_lore", List.of("&7Return to the main menu."));
        inv.setItem(48, GUIManager.createItem(Material.ARROW, backName, colorList(backLore)));
        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", null, "&cClose"),
                colorList(plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu.")))
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotStatusHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 48) {
            GUIManager.playClick(player);
            if (holder.getReturnWalkthroughPage() >= 0) {
                plugin.gui().walkthrough().open(player, holder.getReturnWalkthroughPage());
            } else {
                plugin.gui().openMain(player);
            }
            return;
        }

        if (e.getSlot() == 49) {
            try { plugin.effects().playMenuClose(player); } catch (Throwable ignored) {}
            player.closeInventory();
            return;
        }
        if (e.getSlot() == 20) {
            if (holder.getPlot().isOwner(player.getUniqueId())
                    && plugin.getConfig().getBoolean("claims.merging.enabled", false)) {
                plugin.gui().claimMerge().open(player);
            } else {
                sendSystem(player, "claim_merge_disabled", null, "&cClaim merging is unavailable.");
                plugin.effects().playError(player);
            }
            return;
        }
        if (e.getSlot() == 21) {
            if (holder.getPlot().isOwner(player.getUniqueId())) {
                sendSystem(player, "transfer_usage", null, "&eUsage: /ag transfer <player>");
                player.closeInventory();
            } else {
                sendSystem(player, "transfer_not_owner", null, "&cOnly the owner can transfer this plot.");
                plugin.effects().playError(player);
            }
            return;
        }
        if (e.getSlot() == 22) {
            plugin.gui().giftBlocks().open(player);
            return;
        }
        if (e.getSlot() == 23) {
            boolean upkeepOn = plugin.getConfig().getBoolean("upkeep.enabled", false)
                    || plugin.getConfig().getBoolean("economy.upkeep.enabled", false);
            if (!upkeepOn) return;
            if (!plotCanManage(player, holder.getPlot())) {
                sendSystem(player, "no_perm", null, "&cYou cannot manage this plot.");
                plugin.effects().playError(player);
                return;
            }
            double cost = plugin.getConfig().getDouble("upkeep.cost_per_plot",
                    plugin.getConfig().getDouble("economy.upkeep.cost", 0.0D));
            if (cost <= 0.0D || plugin.vault() == null || holder.getPlot().getOwner() == null
                    || !plugin.vault().charge(org.bukkit.Bukkit.getOfflinePlayer(holder.getPlot().getOwner()), cost)) {
                sendSystem(player, "upkeep_pay_failed", null, "&cUnable to collect upkeep payment.");
                plugin.effects().playError(player);
                return;
            }
            holder.getPlot().setLastUpkeepPayment(System.currentTimeMillis());
            plugin.store().savePlotSync(holder.getPlot());
            plugin.territoryLife().logKey(holder.getPlot().getPlotId(), player.getUniqueId(), "UPKEEP_PAID_EARLY",
                    "activity_detail_upkeep_paid",
                    "Early upkeep payment collected: " + cost + ".",
                    java.util.Map.of("AMOUNT", String.valueOf(cost)));
            sendSystem(player, "upkeep_pay_success", null, "&aUpkeep payment collected.");
            plugin.effects().playConfirm(player);
            open(player, holder.getPlot(), holder.getReturnWalkthroughPage());
        }
    }

    private boolean plotCanManage(Player player, Plot plot) {
        return plot != null && plot.canManage(player, plugin);
    }

    // --------------------------------------------------
    // Domain Registry Lore (language aware)
    // --------------------------------------------------

    private List<String> buildAccessLore(Player player, Plot plot) {
        List<String> lore = new ArrayList<>();
        int members = plot.getPlayerRoles() == null ? 0 : plot.getPlayerRoles().size();
        int guests = 0;
        try {
            if (plot.getGuestPasses() != null) guests = plot.getGuestPasses().size();
        } catch (Throwable ignored) {}
        lore.add(tr(player, "plot_status_members_line", null, "&7Members: &f{COUNT}",
                Map.of("COUNT", String.valueOf(members))));
        lore.add(tr(player, "plot_status_guests_line", null, "&7Guest Passes: &f{COUNT}",
                Map.of("COUNT", String.valueOf(guests))));
        lore.add(plot.isLockdownActive()
                ? tr(player, "plot_status_lockdown_on", null, "&cLockdown: &fActive")
                : tr(player, "plot_status_lockdown_off", null, "&7Lockdown: &fOff"));
        lore.add("");
        lore.addAll(plugin.gui().trList(player, "plot_status_access_hint",
                List.of("&8Open Roles, Guest Passes, or Lockdown", "&8from the main menu to change this.")));
        return colorList(lore);
    }

    private void addSectionFrame(Player player, Inventory inv, Material material,
                                 String titleKey, String titleFallback,
                                 String loreKey, List<String> loreFallback,
                                 int... slots) {
        String title = tr(player, titleKey, null, titleFallback);
        List<String> lore = colorList(plugin.gui().trList(player, loreKey, loreFallback));
        for (int slot : slots) {
            ItemStack marker = GUIManager.createItem(material, title, lore);
            try { plugin.gui().tagAction(marker, "section_marker"); } catch (Throwable ignored) {}
            inv.setItem(slot, marker);
        }
    }

    private List<String> buildLedgerLore(Player player, Plot plot) {
        List<String> lore = new ArrayList<>();

        if (plugin.getClaimBlockManager() == null) {
            lore.add(tr(player, "ledger_disabled_1", "plot_status_ledger_disabled_1", "&8Claim blocks are disabled."));
            lore.add(tr(player, "ledger_disabled_2", "plot_status_ledger_disabled_2", "&7Ask an admin to enable:"));
            lore.add(tr(player, "ledger_disabled_3", "plot_status_ledger_disabled_3", "&fclaim_blocks.enabled: &atrue"));
            return colorList(lore);
        }

        UUID ownerUUID = plot.getOwner();
        String ownerName = plot.getOwnerName();

        long totalBlocks = plugin.getClaimBlockManager().getTotalBlocks(ownerUUID);
        long usedBlocks  = plugin.getClaimBlockManager().getUsedBlocks(ownerUUID);
        long availBlocks = plugin.getClaimBlockManager().getAvailableBlocks(ownerUUID);

        String availLabel = tr(player, "ledger_available", null, "&7Available: &a");
        String usedLabel  = tr(player, "ledger_used", null, "&7Used: &c");
        String totalLabel = tr(player, "ledger_total", null, "&7Total Capacity: &e");

        lore.add(availLabel + availBlocks);
        lore.add(usedLabel + usedBlocks);
        lore.add(totalLabel + totalBlocks);
        lore.add("");

        lore.add(tr(player, "ledger_applies_1", "plot_status_ledger_footer_1", "&8This budget applies to all plots"));
        lore.add(tr(player,
                "ledger_applies_2", "plot_status_ledger_footer_2",
                "&8owned by &f{OWNER}&8.",
                Map.of("OWNER", ownerName)
        ));

        return colorList(lore);
    }

    // --------------------------------------------------
    // Protection Overview
    // --------------------------------------------------

    private List<String> buildProtectionLore(Player player, Plot plot) {
        List<String> lore = new ArrayList<>();

        boolean pvpProtected        = plugin.protection().isFlagEnabled(plot, "pvp");
        boolean mobProtected        = plugin.protection().isMobProtectionEnabled(plot);
        boolean animalsProtected    = plugin.protection().isFlagEnabled(plot, "animals");
        boolean containersProtected = plugin.protection().isFlagEnabled(plot, "containers");
        boolean redstoneProtected   = plugin.protection().isFlagEnabled(plot, "redstone");
        boolean vehiclesProtected   = plugin.protection().isFlagEnabled(plot, "vehicles");
        boolean safeZone            = plugin.protection().isSafeZoneEnabled(plot);

        boolean shopEnabled         = plot.getFlag("shop-interact", false);
        boolean flyEnabled          = hasAscensionFlight(plot);
        boolean entryOpen           = plot.getFlag("entry", true);

        lore.add(tr(player, "plot_status_section_combat", null, "&7Combat & Hostiles:"));
        lore.add(bullet(player,
                tr(player, "plot_status_label_pvp", null, "PvP"),
                stateProtected(player, pvpProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_hostile_mobs", null, "Hostile mobs"),
                stateProtected(player, mobProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_animals_pets", "plot_status_label_animals", "Animals & pets"),
                stateProtected(player, animalsProtected)
        ));
        lore.add("");

        lore.add(tr(player, "plot_status_section_environment", null, "&7Environment & Access:"));
        lore.add(bullet(player,
                tr(player, "plot_status_label_safe_zone", null, "Safe zone"),
                safeZone
                        ? tr(player, "plot_status_state_enabled", "plot_status_state_enabled_simple", "&aEnabled")
                        : tr(player, "plot_status_state_disabled", null, "&7Disabled")
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_entry_gate", null, "Entry gate"),
                entryOpen
                        ? tr(player, "plot_status_state_open", null, "&aOpen")
                        : tr(player, "plot_status_state_closed", null, "&cClosed")
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_containers", null, "Containers"),
                stateProtected(player, containersProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_redstone_doors", "plot_status_label_redstone", "Redstone & doors"),
                stateProtected(player, redstoneProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_vehicles", null, "Vehicles"),
                stateProtected(player, vehiclesProtected)
        ));
        lore.add("");

        lore.add(tr(player, "plot_status_section_perks", null, "&7Perks & Services:"));
        lore.add(bullet(player,
                tr(player, "plot_status_label_shop_interact", null, "Market shop interact"),
                shopEnabled
                        ? tr(player, "plot_status_state_active", "plot_status_state_enabled_simple", "&aEnabled")
                        : tr(player, "plot_status_state_inactive", null, "&7Inactive")
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_flight", null, "Flight inside this plot"),
                flyEnabled
                        ? tr(player, "plot_status_state_active", "plot_status_state_enabled_simple", "&aEnabled")
                        : tr(player, "plot_status_state_inactive", null, "&7Inactive")
        ));

        return colorList(lore);
    }

    private boolean hasAscensionFlight(Plot plot) {
        for (int level = 1; level <= plot.getLevel(); level++) {
            List<String> rewards = plugin.cfg().getLevelRewards(level);
            if (rewards != null && rewards.stream().anyMatch(reward -> reward != null
                    && (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLY")
                    || reward.equalsIgnoreCase("FLAG:fly")))) return true;
        }
        return false;
    }

    private String stateProtected(Player p, boolean isProtected) {
        return isProtected
                ? tr(p, "plot_status_state_protected", null, "&aProtected")
                : tr(p, "plot_status_state_vulnerable", null, "&cVulnerable");
    }

    private String bullet(Player p, String label, String state) {
        String fmt = tr(p, "plot_status_line_bullet", null, "&f- {LABEL}: {STATE}");
        return apply(fmt, Map.of("LABEL", label, "STATE", state));
    }

    // --------------------------------------------------
    // Blessings (formats)
    // --------------------------------------------------

    private List<String> buildBuffList(Player player, int level) {
        List<String> result = new ArrayList<>();
        if (level <= 0) return result;

        Map<String, Integer> highestTier = new LinkedHashMap<>();
        Map<String, String> rewardByKey = new LinkedHashMap<>();

        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (reward == null) continue;
                reward = reward.trim();
                if (reward.isEmpty()) continue;

                String[] parts = reward.split(":");
                String key;
                Integer tier = null;

                if (parts.length == 3 && isInteger(parts[2])) {
                    key = (parts[0] + ":" + parts[1]).toUpperCase();
                    tier = Integer.parseInt(parts[2]);
                } else if (parts.length == 2 && isInteger(parts[1])) {
                    key = parts[0].toUpperCase();
                    tier = Integer.parseInt(parts[1]);
                } else {
                    key = reward.toUpperCase();
                }

                Integer current = highestTier.get(key);
                if (current == null || (tier != null && tier > current)) {
                    rewardByKey.put(key, reward);
                    if (tier != null) highestTier.put(key, tier);
                }
            }
        }

        if (rewardByKey.isEmpty()) return result;

        for (Map.Entry<String, String> entry : rewardByKey.entrySet()) {
            String reward = entry.getValue();
            String[] parts = reward.split(":");

            if (parts.length == 3 && isInteger(parts[2]) && parts[0].equalsIgnoreCase("EFFECT")) {
                String effectKey = parts[1];
                int tier = Integer.parseInt(parts[2]);
                String effectName = formatName(effectKey);
                String roman = toRoman(tier);

                String color = (tier >= 4) ? "&d" : (tier >= 2) ? "&b" : "&a";

                String fmt = tr(player,
                        "plot_status_blessing_effect_format", "plot_status_buff_effect",
                        "{COLOR}✦ &f{EFFECT} &7(Effect &f{TIER}&7)",
                        Map.of("COLOR", color, "EFFECT", effectName, "TIER", roman)
                );

                result.add(fmt);
                continue;
            }

            if (parts.length == 2 && isInteger(parts[1]) && parts[0].equalsIgnoreCase("MEMBERS")) {
                int amount = Integer.parseInt(parts[1]);

                String fmt = tr(player,
                        "plot_status_blessing_member_slots_format", "plot_status_buff_members",
                        "&a✦ &fTrusted member slots: &b+{AMOUNT}",
                        Map.of("AMOUNT", String.valueOf(amount))
                );

                result.add(fmt);
                continue;
            }

            String pretty = reward.replace("EFFECT:", "")
                    .replace("MEMBERS:", "")
                    .replace(":", " ");
            pretty = formatName(pretty);

            String gen = tr(player, "plot_status_blessing_generic_format", null, "&a✦ &f{TEXT}",
                    Map.of("TEXT", pretty));
            result.add(gen);
        }

        return result;
    }

    // --------------------------------------------------
    // Language helpers (modern + legacy keys)
    // --------------------------------------------------

    private String tr(Player player, String key, String legacyKey, String fallback) {
        return tr(player, key, legacyKey, fallback, null);
    }

    private String tr(Player player, String key, String legacyKey, String fallback, Map<String, String> vars) {
        String out = safeGuiGet(player, key);
        if (out.isBlank() && legacyKey != null) out = safeGuiGet(player, legacyKey);
        if (out.isBlank()) out = (fallback == null ? "" : fallback);

        if (vars != null && !vars.isEmpty()) out = apply(out, vars);
        return GUIManager.color(out);
    }

    private String safeGuiGet(Player player, String key) {
        if (key == null || key.isBlank()) return "";
        String out = "";
        try {
            out = plugin.gui().tr(player, key, "");
        } catch (Throwable ignored) {}

        if (out == null) return "";
        String t = out.trim();
        if (t.isEmpty()) return "";
        if (t.equalsIgnoreCase(key)) return "";
        if (t.toLowerCase().contains("missing") && t.contains(key)) return "";
        return out;
    }

    private void sendSystem(Player player, String key, String legacyKey, String fallback) {
        String msg = safeSystemGet(player, key);
        if (msg.isBlank() && legacyKey != null) msg = safeSystemGet(player, legacyKey);

        // ultra-safe fallback: if system lookup fails, try gui lookup
        if (msg.isBlank()) msg = safeGuiGet(player, key);
        if (msg.isBlank() && legacyKey != null) msg = safeGuiGet(player, legacyKey);

        if (msg.isBlank()) msg = (fallback == null ? "" : fallback);
        player.sendMessage(GUIManager.color(msg));
    }

    private String safeSystemGet(Player player, String key) {
        if (key == null || key.isBlank()) return "";
        String out = "";
        try {
            // plugin.msg() should be backed by your system.yml now
            out = plugin.msg().get(player, key);
        } catch (Throwable ignored) {}

        if (out == null) return "";
        String t = out.trim();
        if (t.isEmpty()) return "";
        if (t.equalsIgnoreCase(key)) return "";
        if (t.toLowerCase().contains("missing") && t.contains(key)) return "";
        return out;
    }

    private String apply(String s, Map<String, String> vars) {
        if (s == null) return "";
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + e.getKey() + "}", v);
            out = out.replace("{" + e.getKey().toLowerCase() + "}", v);
        }
        return out;
    }

    private List<String> colorList(List<String> in) {
        if (in == null) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) out.add(GUIManager.color(s));
        return out;
    }

    // --------------------------------------------------
    // Small utils
    // --------------------------------------------------

    private boolean isInteger(String s) {
        try { Integer.parseInt(s); return true; }
        catch (NumberFormatException ex) { return false; }
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private String formatName(String key) {
        String lower = key.toLowerCase().replace("_", " ");
        String[] words = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
