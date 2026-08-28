package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.progression.AscensionFocus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LevelingGUI {
    private enum Page { HALL, GUIDE, DISCIPLINES, CONFIRM }
    private enum Payment { MONEY, BLOCKS }

    private final AegisGuard plugin;

    public LevelingGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class LevelingHolder implements InventoryHolder {
        private final Plot plot;
        private final Page page;
        private final Payment payment;

        private LevelingHolder(Plot plot, Page page, Payment payment) {
            this.plot = plot;
            this.page = page;
            this.payment = payment;
        }

        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        openHall(player, plot);
    }

    private void openHall(Player player, Plot plot) {
        if (!canOpen(player, plot)) return;
        Inventory inventory = Bukkit.createInventory(new LevelingHolder(plot, Page.HALL, null), 54,
                plugin.gui().title(player, "ascension_hall_title", "&6✦ Ascension Hall ✦"));
        paintHall(inventory);

        int level = plot.getLevel();
        int maxLevel = plugin.cfg().getMaxLevel();
        String chapter = chapterName(player, level);
        inventory.setItem(4, item(Material.BEACON,
                tr(player, "ascension_hall_status_name", "&6&l{PLOT} Ascension").replace("{PLOT}", plotName(plot)),
                List.of(
                        tr(player, "ascension_hall_status_level", "&7Level: &e{LEVEL}&7/&f{MAX}")
                                .replace("{LEVEL}", String.valueOf(level)).replace("{MAX}", String.valueOf(maxLevel)),
                        tr(player, "ascension_hall_status_chapter", "&7Chapter: &f{CHAPTER}").replace("{CHAPTER}", chapter),
                        tr(player, "ascension_hall_status_progress", "&7Progress: {BAR}")
                                .replace("{BAR}", progressBar(level, maxLevel)),
                        " ",
                        tr(player, "ascension_hall_status_footer", "&8Build a lasting territory through measured growth."))));

        inventory.setItem(10, item(Material.WRITTEN_BOOK,
                tr(player, "ascension_guide_name", "&eGuardian's Ascension Guide"),
                trList(player, "ascension_guide_open_lore", List.of(
                        "&7Learn how levels, blessings, disciplines,",
                        "&7payments, and Horizons work.", " ", "&eClick to read."))));

        AscensionFocus focus = AscensionFocus.parse(plot.getAscensionFocus());
        inventory.setItem(12, item(focus.icon(),
                tr(player, "ascension_discipline_name", "&bAscension Discipline"),
                List.of(
                        tr(player, "ascension_discipline_current", "&7Current: &f{FOCUS}")
                                .replace("{FOCUS}", focusName(player, focus)),
                        tr(player, "ascension_discipline_summary", "&7Choose one modest utility specialty for this plot."),
                        " ", tr(player, "ascension_discipline_open", "&eClick to choose or review."))));

        inventory.setItem(14, item(Material.ENCHANTED_BOOK,
                tr(player, "ascension_blessings_name", "&dActive Blessings"),
                activeBlessingLore(player, plot)));

        boolean horizonsReady = level >= plugin.horizons().unlockLevel();
        inventory.setItem(16, item(horizonsReady ? Material.END_CRYSTAL : Material.ENDER_EYE,
                tr(player, "ascension_horizon_gateway_name", "&5Horizon Gateway"),
                horizonsReady
                        ? trList(player, "ascension_horizon_ready_lore", List.of(
                                "&dPlot Ascension is complete.", "&7Expansion Horizons now await.", " ", "&eClick to continue."))
                        : trList(player, "ascension_horizon_locked_lore", List.of(
                                "&7Complete Plot Level 30 to unlock", "&7the long-term Horizon journey.", " ", "&8Still sealed."))));

        int start = trackStart(level, maxLevel);
        for (int index = 0; index < 7; index++) {
            int trackLevel = start + index;
            if (trackLevel <= maxLevel) inventory.setItem(19 + index, levelTrackItem(player, plot, trackLevel));
        }

        renderUpgradeAltar(player, plot, inventory);
        inventory.setItem(48, item(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the main menu."))));
        inventory.setItem(50, item(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inventory);
        plugin.effects().playMenuOpen(player);
    }

    private void openGuide(Player player, Plot plot) {
        Inventory inventory = Bukkit.createInventory(new LevelingHolder(plot, Page.GUIDE, null), 54,
                plugin.gui().title(player, "ascension_guide_title", "&eGuardian's Ascension Guide"));
        paintGuide(inventory);
        inventory.setItem(4, item(Material.WRITTEN_BOOK, tr(player, "ascension_guide_header", "&6&lThe Ascension Codex"),
                trList(player, "ascension_guide_header_lore", List.of(
                        "&7A concise guide to building a stronger", "&7plot without skipping server progression."))));
        guideCard(inventory, 10, Material.AMETHYST_SHARD, player, "purpose", "&dWhat Is Ascension?",
                List.of("&7Thirty plot levels form six chapters.", "&7Each step grants safe utility, capacity,", "&7or progression rewards within this plot."));
        guideCard(inventory, 12, Material.GOLD_INGOT, player, "payment", "&6Paying for a Level",
                List.of("&7Servers may allow money, ClaimBlocks,", "&7or both. The confirmation altar shows", "&7the exact cost before anything is charged."));
        guideCard(inventory, 14, Material.POTION, player, "blessings", "&bPlot Blessings",
                List.of("&7Utility blessings affect owners and trusted", "&7members only while they remain inside.", "&7Outside effects are preserved and restored."));
        guideCard(inventory, 16, Material.SMITHING_TABLE, player, "disciplines", "&aDisciplines",
                List.of("&7Choose Stonewright, Verdant Keeper,", "&7or Wayfinder for one restrained specialty.", "&7Changing focus has a server cooldown."));
        guideCard(inventory, 28, Material.PLAYER_HEAD, player, "members", "&eTerritory Capacity",
                List.of("&7Selected milestones increase how many", "&7players can be trusted without granting", "&7unsafe administrative control."));
        guideCard(inventory, 30, Material.FEATHER, player, "flight", "&fZenith Flight",
                List.of("&7Flight is the final Level 30 reward.", "&7It applies only inside the eligible plot", "&7and never removes pre-existing flight."));
        guideCard(inventory, 32, Material.END_CRYSTAL, player, "horizons", "&5Beyond Level 30",
                List.of("&7Level 30 opens Horizon Ascension:", "&7a slower Renown journey with bound", "&7Sigils and advanced territory abilities."));
        inventory.setItem(48, item(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the Ascension Hall."))));
        inventory.setItem(50, item(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this guide."))));
        player.openInventory(inventory);
        plugin.effects().playMenuFlip(player);
    }

    private void openDisciplines(Player player, Plot plot) {
        Inventory inventory = Bukkit.createInventory(new LevelingHolder(plot, Page.DISCIPLINES, null), 45,
                plugin.gui().title(player, "ascension_disciplines_title", "&bAscension Disciplines"));
        paintDiscipline(inventory);
        AscensionFocus current = AscensionFocus.parse(plot.getAscensionFocus());
        inventory.setItem(4, item(Material.ENCHANTED_BOOK,
                tr(player, "ascension_disciplines_header", "&b&lChoose a Calling"),
                List.of(
                        tr(player, "ascension_discipline_current", "&7Current: &f{FOCUS}")
                                .replace("{FOCUS}", focusName(player, current)),
                        tr(player, "ascension_disciplines_header_lore", "&7One utility focus may serve this plot at a time."),
                        " ", focusCooldownLine(player, plot))));
        inventory.setItem(20, focusItem(player, plot, AscensionFocus.STONEWRIGHT));
        inventory.setItem(22, focusItem(player, plot, AscensionFocus.VERDANT_KEEPER));
        inventory.setItem(24, focusItem(player, plot, AscensionFocus.WAYFINDER));
        inventory.setItem(39, item(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the Ascension Hall."))));
        inventory.setItem(40, item(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inventory);
        plugin.effects().playMenuFlip(player);
    }

    private void openConfirmation(Player player, Plot plot, Payment payment) {
        if (!paymentAvailable(payment)) {
            plugin.effects().playError(player);
            return;
        }
        int nextLevel = plot.getLevel() + 1;
        if (nextLevel > plugin.cfg().getMaxLevel()) return;
        double cost = payment == Payment.MONEY ? moneyCost(nextLevel) : blockCost(nextLevel);
        CurrencyType type = payment == Payment.MONEY ? plugin.cfg().getLevelCostType() : blocksType();
        Inventory inventory = Bukkit.createInventory(new LevelingHolder(plot, Page.CONFIRM, payment), 27,
                plugin.gui().title(player, "ascension_confirm_title", "&6Confirm Ascension"));
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, item(Material.BEACON, tr(player, "ascension_confirm_header", "&6Ascension Oath"),
                trList(player, "ascension_confirm_header_lore", List.of(
                        "&7Review this permanent plot upgrade.", "&7Nothing is charged until confirmation."))));
        inventory.setItem(10, item(Material.EXPERIENCE_BOTTLE,
                tr(player, "ascension_confirm_transition", "&eLevel {CURRENT} &8→ &aLevel {NEXT}")
                        .replace("{CURRENT}", String.valueOf(plot.getLevel())).replace("{NEXT}", String.valueOf(nextLevel)),
                rewardsForLevel(player, nextLevel)));
        inventory.setItem(13, item(payment == Payment.MONEY ? Material.GOLD_INGOT : Material.AMETHYST_SHARD,
                tr(player, "ascension_confirm_cost_name", "&6Offering"),
                List.of(
                        tr(player, "ascension_confirm_cost", "&7Cost: &f{COST}").replace("{COST}", formatCost(player, cost, type)),
                        tr(player, "ascension_confirm_balance", "&7Balance: &f{BALANCE}")
                                .replace("{BALANCE}", formatBalance(player, type)),
                        " ", tr(player, "ascension_confirm_charge_note", "&8Charged only after all safety checks pass."))));
        inventory.setItem(16, item(Material.ENCHANTED_BOOK,
                tr(player, "ascension_confirm_after_name", "&dAfter Ascension"), activeBlessingLore(player, plot, nextLevel)));
        inventory.setItem(21, item(Material.ARROW, tr(player, "ascension_confirm_cancel", "&eCancel"),
                trList(player, "ascension_confirm_cancel_lore", List.of("&7Return without spending anything."))));
        inventory.setItem(23, glow(item(Material.LIME_DYE, tr(player, "ascension_confirm_accept", "&a&lConfirm Ascension"),
                trList(player, "ascension_confirm_accept_lore", List.of(
                        "&7Validate, pay, and awaken this level.", " ", "&aClick to confirm.")))));
        inventory.setItem(26, item(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close without purchasing."))));
        player.openInventory(inventory);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, LevelingHolder holder) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getInventory().getSize()) return;
        Plot plot = holder.getPlot();
        if (!ownsPlot(player, plot)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        switch (holder.page) {
            case HALL -> handleHall(player, plot, slot);
            case GUIDE -> {
                if (slot == 48) openHall(player, plot);
                else if (slot == 50) close(player);
            }
            case DISCIPLINES -> handleDiscipline(player, plot, slot);
            case CONFIRM -> {
                if (slot == 21) openHall(player, plot);
                else if (slot == 23) performUpgrade(player, plot, holder.payment);
                else if (slot == 26) close(player);
            }
        }
    }

    private void handleHall(Player player, Plot plot, int slot) {
        if (slot == 10) openGuide(player, plot);
        else if (slot == 12) openDisciplines(player, plot);
        else if (slot == 16) {
            if (plot.getLevel() >= plugin.horizons().unlockLevel()) plugin.gui().expansionRequest().open(player);
            else plugin.effects().playError(player);
        } else if (slot == 30 && moneyAvailable()) openConfirmation(player, plot, Payment.MONEY);
        else if (slot == 32 && blocksAvailable()) openConfirmation(player, plot, Payment.BLOCKS);
        else if (slot == 48) plugin.gui().openMain(player);
        else if (slot == 50) close(player);
        else if (slot >= 19 && slot <= 25) plugin.effects().playMenuFlip(player);
    }

    private void handleDiscipline(Player player, Plot plot, int slot) {
        AscensionFocus focus = switch (slot) {
            case 20 -> AscensionFocus.STONEWRIGHT;
            case 22 -> AscensionFocus.VERDANT_KEEPER;
            case 24 -> AscensionFocus.WAYFINDER;
            default -> null;
        };
        if (focus != null) selectFocus(player, plot, focus);
        else if (slot == 39) openHall(player, plot);
        else if (slot == 40) close(player);
    }

    private void selectFocus(Player player, Plot plot, AscensionFocus focus) {
        AscensionFocus current = AscensionFocus.parse(plot.getAscensionFocus());
        if (current == focus) {
            plugin.effects().playError(player);
            return;
        }
        long remaining = focusCooldownRemaining(plot);
        if (current != AscensionFocus.UNCHOSEN && remaining > 0L) {
            send(player, "ascension_focus_cooldown", "&eThis plot may change discipline again in {DAYS} day(s).",
                    Map.of("DAYS", String.valueOf((remaining + 86_399_999L) / 86_400_000L)));
            plugin.effects().playError(player);
            return;
        }
        plot.setAscensionFocus(focus.name());
        plot.setAscensionFocusChangedAt(System.currentTimeMillis());
        plugin.store().savePlotSync(plot);
        if (plugin.ascensionEffects() != null) plugin.ascensionEffects().refresh(player, plot);
        send(player, "ascension_focus_selected", "&a{FOCUS} now guides this plot's Ascension.",
                Map.of("FOCUS", focusName(player, focus)));
        plugin.effects().playConfirm(player);
        openDisciplines(player, plot);
    }

    private void performUpgrade(Player player, Plot plot, Payment payment) {
        if (payment == null || !ownsPlot(player, plot) || !paymentAvailable(payment)) {
            plugin.effects().playError(player);
            return;
        }
        int nextLevel = plot.getLevel() + 1;
        if (nextLevel > plugin.cfg().getMaxLevel()) return;
        CurrencyType type = payment == Payment.MONEY ? plugin.cfg().getLevelCostType() : blocksType();
        double cost = payment == Payment.MONEY ? moneyCost(nextLevel) : blockCost(nextLevel);
        Bounds expanded = validateExpansion(player, plot);
        if (expanded == Bounds.INVALID) return;
        if (!plugin.eco().withdraw(player, cost, type)) {
            send(player, "level_up_fail_cost", "&cYou do not have enough currency to ascend.", Map.of());
            plugin.effects().playError(player);
            return;
        }

        int oldLevel = plot.getLevel();
        int oldX1 = plot.getX1();
        int oldZ1 = plot.getZ1();
        int oldX2 = plot.getX2();
        int oldZ2 = plot.getZ2();
        try {
            if (expanded != null) {
                plugin.store().updatePlotBounds(plot, expanded.x1, expanded.z1, expanded.x2, expanded.z2);
            }
            plot.setLevel(nextLevel);
            plugin.store().savePlotSync(plot);
            if (plugin.getClaimBlockManager() != null) {
                plugin.getClaimBlockManager().invalidateOwnerCache(player.getUniqueId());
            }
            Bukkit.getPluginManager().callEvent(new PlotLevelUpEvent(plot, player, nextLevel));
            plugin.store().setDirty(true);
        } catch (Throwable error) {
            plot.setLevel(oldLevel);
            if (expanded != null) {
                try {
                    plugin.store().updatePlotBounds(plot, oldX1, oldZ1, oldX2, oldZ2);
                } catch (Throwable rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            plugin.store().savePlotSync(plot);
            plugin.eco().deposit(player, cost, type);
            plugin.getLogger().severe("Ascension transaction rolled back for " + player.getName() + ": " + error.getMessage());
            send(player, "ascension_transaction_rollback", "&cAscension could not complete. Your payment was returned.", Map.of());
            plugin.effects().playError(player);
            openHall(player, plot);
            return;
        }

        send(player, "level_up_success", "&dYour plot has ascended to Level &f{LEVEL}&d!",
                Map.of("LEVEL", String.valueOf(nextLevel)));
        ceremony(player, plot, nextLevel);
    }

    private Bounds validateExpansion(Player player, Plot plot) {
        if (!plugin.cfg().isLevelingExpansionEnabled()) return null;
        int amount = Math.max(1, plugin.cfg().getLevelingExpansionAmount());
        Bounds bounds = new Bounds(plot.getX1() - amount, plot.getZ1() - amount,
                plot.getX2() + amount, plot.getZ2() + amount);
        if (plugin.store().isAreaOverlapping(plot, plot.getWorld(), bounds.x1, bounds.z1, bounds.x2, bounds.z2)) {
            send(player, "level_up_fail_overlap", "&cThe new boundary would overlap another plot.", Map.of());
            plugin.effects().playError(player);
            return Bounds.INVALID;
        }
        int halfWidth = Math.max(0, bounds.x2 - bounds.x1) / 2;
        int halfDepth = Math.max(0, bounds.z2 - bounds.z1) / 2;
        int radius = Math.max(halfWidth, halfDepth);
        int limit = plugin.cfg().getWorldMaxRadius(player.getWorld());
        if (radius > limit && !player.hasPermission("aegis.admin.bypass-limits")) {
            send(player, "level_up_fail_world_limit", "&cThis world permits a maximum radius of {LIMIT}.",
                    Map.of("LIMIT", String.valueOf(limit)));
            plugin.effects().playError(player);
            return Bounds.INVALID;
        }
        int minRadius = Math.max(1, plugin.cfg().getWorldMinRadius(player.getWorld()));
        if ((halfWidth < minRadius || halfDepth < minRadius)
                && !player.hasPermission("aegis.admin.bypass-limits")) {
            send(player, "claim_too_small", "&cThis world requires a minimum radius of {MIN}.",
                    Map.of("MIN", String.valueOf(minRadius)));
            plugin.effects().playError(player);
            return Bounds.INVALID;
        }
        if (plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)
                && plugin.cfg().raw().getBoolean("claim_blocks.require_per_block", true)
                && plugin.getClaimBlockManager() != null
                && !player.hasPermission("aegis.admin.bypass-limits")) {
            long added = Math.max(0L, ((long) (bounds.x2 - bounds.x1 + 1) * (bounds.z2 - bounds.z1 + 1)) - plot.getArea());
            if (added > 0L && !plugin.getClaimBlockManager().canAfford(player.getUniqueId(), added)) {
                send(player, "claim_blocks_not_enough", "&cYou do not have enough ClaimBlocks.", Map.of());
                plugin.effects().playError(player);
                return Bounds.INVALID;
            }
        }
        return bounds;
    }

    private void ceremony(Player player, Plot plot, int level) {
        player.closeInventory();
        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 75, 1.1, 1.0, 1.1, 0.15);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 35, 0.8, 1.0, 0.8, 0.06);
        player.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
        plugin.effects().playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F + Math.min(0.5F, level / 100F));
        plugin.effects().playConfirm(player);
        player.sendTitle(color(tr(player, "ascension_ceremony_title", "&6&lPLOT ASCENDED")),
                color(tr(player, "ascension_ceremony_subtitle", "&fLevel {LEVEL} &8- &e{CHAPTER}")
                        .replace("{LEVEL}", String.valueOf(level)).replace("{CHAPTER}", chapterName(player, level))), 10, 60, 20);
        plugin.runEntityLater(player, () -> openHall(player, plot), 35L);
    }

    private void renderUpgradeAltar(Player player, Plot plot, Inventory inventory) {
        int nextLevel = plot.getLevel() + 1;
        if (nextLevel > plugin.cfg().getMaxLevel()) {
            inventory.setItem(31, glow(item(Material.NETHER_STAR,
                    tr(player, "ascension_zenith_name", "&6&lZenith Ascension Complete"),
                    trList(player, "ascension_zenith_lore", List.of(
                            "&7All thirty plot levels are mastered.", "&dThe Horizon Gateway is now available.")))));
            return;
        }
        List<String> preview = new ArrayList<>();
        preview.add(tr(player, "ascension_altar_next", "&7Next: &fLevel {LEVEL} &8- &e{CHAPTER}")
                .replace("{LEVEL}", String.valueOf(nextLevel)).replace("{CHAPTER}", chapterName(player, nextLevel)));
        preview.add(" ");
        preview.add(tr(player, "ascension_altar_rewards", "&dNew Rewards:"));
        preview.addAll(rewardsForLevel(player, nextLevel));
        preview.add(" ");
        preview.add(tr(player, "ascension_altar_hint", "&8Choose an offering on either side."));
        inventory.setItem(31, item(Material.SMITHING_TABLE,
                tr(player, "ascension_altar_name", "&6&lAscension Altar"), preview));

        inventory.setItem(30, paymentItem(player, Payment.MONEY, nextLevel));
        inventory.setItem(32, paymentItem(player, Payment.BLOCKS, nextLevel));
    }

    private ItemStack paymentItem(Player player, Payment payment, int nextLevel) {
        boolean available = paymentAvailable(payment);
        CurrencyType type = payment == Payment.MONEY ? plugin.cfg().getLevelCostType() : blocksType();
        double cost = payment == Payment.MONEY ? moneyCost(nextLevel) : blockCost(nextLevel);
        Material material = available ? (payment == Payment.MONEY ? Material.GOLD_INGOT : Material.AMETHYST_SHARD) : Material.GRAY_DYE;
        String name = payment == Payment.MONEY
                ? tr(player, "ascension_offer_money_name", "&6Treasury Offering")
                : tr(player, "ascension_offer_blocks_name", "&bClaimBlock Offering");
        if (!available) return item(material, name, trList(player, "ascension_offer_unavailable_lore", List.of("&8Unavailable on this server.")));
        return item(material, name, List.of(
                tr(player, "ascension_offer_cost", "&7Cost: &f{COST}").replace("{COST}", formatCost(player, cost, type)),
                tr(player, "ascension_offer_balance", "&7Balance: &f{BALANCE}").replace("{BALANCE}", formatBalance(player, type)),
                " ", tr(player, "ascension_offer_click", "&eClick to review and confirm.")));
    }

    private ItemStack levelTrackItem(Player player, Plot plot, int level) {
        int current = plot.getLevel();
        String state;
        if (level < current) state = tr(player, "ascension_track_mastered", "&aMastered");
        else if (level == current) state = tr(player, "ascension_track_current", "&eCurrent");
        else if (level == current + 1) state = tr(player, "ascension_track_next", "&bNext");
        else state = tr(player, "ascension_track_locked", "&8Locked");
        List<String> lore = new ArrayList<>();
        lore.add(tr(player, "ascension_track_chapter", "&7{CHAPTER}").replace("{CHAPTER}", chapterName(player, level)));
        lore.add(tr(player, "ascension_track_state", "&7Status: {STATE}").replace("{STATE}", state));
        lore.add(" ");
        lore.addAll(rewardsForLevel(player, level));
        if (level > current + 1) {
            lore.add(" ");
            lore.add(tr(player, "ascension_track_prerequisite", "&8Master earlier levels first."));
        }
        ItemStack item = item(rewardMaterial(level),
                tr(player, "ascension_track_level_name", "&fLevel {LEVEL}").replace("{LEVEL}", String.valueOf(level)), lore);
        return level <= current ? glow(item) : item;
    }

    private ItemStack focusItem(Player player, Plot plot, AscensionFocus focus) {
        boolean selected = AscensionFocus.parse(plot.getAscensionFocus()) == focus;
        String base = "ascension_focus_" + focus.key();
        List<String> lore = new ArrayList<>(trList(player, base + "_lore", focusFallback(focus)));
        lore.add(" ");
        lore.add(selected ? tr(player, "ascension_focus_active", "&aCurrently guiding this plot.")
                : tr(player, "ascension_focus_select", "&eClick to choose this discipline."));
        ItemStack item = item(focus.icon(), tr(player, base + "_name", focusName(player, focus)), lore);
        return selected ? glow(item) : item;
    }

    private List<String> focusFallback(AscensionFocus focus) {
        return switch (focus) {
            case STONEWRIGHT -> List.of("&7Haste I from Level 5; Haste II at 20.", "&8Useful for building without combat bonuses.");
            case VERDANT_KEEPER -> List.of("&7Luck I from Level 5; Luck II at 20.", "&7Trusted residents cannot trample farmland.");
            case WAYFINDER -> List.of("&7Speed I from Level 5; Speed II at 20.", "&8A restrained travel and exploration focus.");
            default -> List.of("&7No discipline selected.");
        };
    }

    private List<String> activeBlessingLore(Player player, Plot plot) {
        return activeBlessingLore(player, plot, plot.getLevel());
    }

    private List<String> activeBlessingLore(Player player, Plot plot, int level) {
        Map<String, Integer> strongest = new LinkedHashMap<>();
        int memberSlots = 0;
        boolean flight = false;
        for (int tier = 1; tier <= level; tier++) {
            List<String> rewards = plugin.cfg().getLevelRewards(tier);
            if (rewards == null) continue;
            for (String reward : rewards) {
                if (reward == null) continue;
                if (reward.startsWith("EFFECT:")) {
                    String[] parts = reward.split(":");
                    if (parts.length >= 3) try { strongest.merge(parts[1], Integer.parseInt(parts[2]), Math::max); }
                    catch (NumberFormatException ignored) {}
                } else if (reward.startsWith("MEMBERS:")) {
                    try { memberSlots += Integer.parseInt(reward.substring(8)); } catch (NumberFormatException ignored) {}
                } else if (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLAG:fly")) flight = true;
            }
        }
        AscensionFocus focus = AscensionFocus.parse(plot.getAscensionFocus());
        int focusAmp = focus.amplifierForLevel(level);
        if (focus.effectType() != null && focusAmp >= 0) strongest.merge(focus.effectType().getName(), focusAmp + 1, Math::max);

        List<String> lore = new ArrayList<>();
        if (strongest.isEmpty() && memberSlots == 0 && !flight) {
            lore.add(tr(player, "ascension_blessings_none", "&8No blessings are active yet."));
        } else {
            strongest.forEach((effect, tier) -> lore.add(formatEffect(player, effect, tier)));
            if (memberSlots > 0) lore.add(tr(player, "level_reward_members_line", "&a+{AMOUNT} member slots")
                    .replace("{AMOUNT}", String.valueOf(memberSlots)).replace("{SUFFIX}", memberSlots == 1 ? "" : "s"));
            if (flight) lore.add(tr(player, "level_reward_flag_line", "&dUnlocks {FLAG}").replace("{FLAG}", tr(player, "ascension_reward_flight", "Plot Flight")));
        }
        lore.add(" ");
        lore.add(tr(player, "ascension_blessings_scope", "&8Active only for trusted residents inside this plot."));
        return lore;
    }

    private List<String> rewardsForLevel(Player player, int level) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        List<String> lore = new ArrayList<>();
        if (rewards != null) for (String reward : rewards) lore.add(formatReward(player, reward));
        if (lore.isEmpty()) lore.add(level == 1
                ? tr(player, "ascension_reward_foundation", "&7Founding territory established")
                : tr(player, "ascension_reward_claimblocks", "&eClaimBlock progression bonus"));
        return lore;
    }

    private String formatReward(Player player, String reward) {
        if (reward == null) return "";
        if (reward.startsWith("EFFECT:")) {
            String[] parts = reward.split(":");
            try { return formatEffect(player, parts[1], Integer.parseInt(parts[2])); }
            catch (Exception ignored) { return color("&b" + humanize(reward)); }
        }
        if (reward.startsWith("MEMBERS:")) {
            String amount = reward.substring("MEMBERS:".length()).trim();
            return tr(player, "level_reward_members_line", "&a+{AMOUNT} member slot{SUFFIX}")
                    .replace("{AMOUNT}", amount).replace("{SUFFIX}", "1".equals(amount) ? "" : "s");
        }
        if (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLAG:fly")) {
            return tr(player, "level_reward_flag_line", "&dUnlocks {FLAG}")
                    .replace("{FLAG}", tr(player, "ascension_reward_flight", "Plot Flight"));
        }
        if (reward.startsWith("FLAG:")) {
            return tr(player, "level_reward_flag_line", "&dUnlocks {FLAG}")
                    .replace("{FLAG}", humanize(reward.substring(5)));
        }
        return color("&a" + humanize(reward));
    }

    private String formatEffect(Player player, String effect, int tier) {
        String normalized = effect == null ? "" : effect.toUpperCase(Locale.ROOT);
        String name = tr(player, "ascension_effect_" + normalized.toLowerCase(Locale.ROOT), humanize(normalized));
        return tr(player, "level_reward_effect_line", "&b{EFFECT} {TIER}")
                .replace("{EFFECT}", name).replace("{TIER}", roman(tier));
    }

    private String chapterName(Player player, int level) {
        String key;
        String fallback;
        if (level <= 5) { key = "foundation"; fallback = "Foundation"; }
        else if (level <= 10) { key = "pathfinder"; fallback = "Pathfinder"; }
        else if (level <= 15) { key = "bastion"; fallback = "Bastion"; }
        else if (level <= 20) { key = "sovereign"; fallback = "Sovereign"; }
        else if (level <= 25) { key = "mythic"; fallback = "Mythic Dominion"; }
        else { key = "zenith"; fallback = "Zenith Ascension"; }
        return tr(player, "ascension_chapter_" + key, fallback);
    }

    private Material rewardMaterial(int level) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        if (rewards != null) {
            if (rewards.stream().anyMatch(value -> value != null && value.toUpperCase(Locale.ROOT).contains("FLIGHT"))) return Material.FEATHER;
            if (rewards.stream().anyMatch(value -> value != null && value.startsWith("MEMBERS:"))) return Material.PLAYER_HEAD;
            if (rewards.stream().anyMatch(value -> value != null && value.contains("FAST_DIGGING"))) return Material.IRON_PICKAXE;
            if (rewards.stream().anyMatch(value -> value != null && value.contains("WATER"))) return Material.HEART_OF_THE_SEA;
            if (rewards.stream().anyMatch(value -> value != null && value.contains("FIRE"))) return Material.BLAZE_POWDER;
            if (rewards.stream().anyMatch(value -> value != null && value.contains("SLOW_FALLING"))) return Material.PHANTOM_MEMBRANE;
        }
        return switch ((level - 1) / 5) {
            case 0 -> Material.OAK_SAPLING;
            case 1 -> Material.COMPASS;
            case 2 -> Material.SHIELD;
            case 3 -> Material.GOLDEN_HELMET;
            case 4 -> Material.AMETHYST_CLUSTER;
            default -> Material.NETHER_STAR;
        };
    }

    private String focusName(Player player, AscensionFocus focus) {
        return tr(player, "ascension_focus_" + focus.key() + "_name", switch (focus) {
            case STONEWRIGHT -> "Stonewright";
            case VERDANT_KEEPER -> "Verdant Keeper";
            case WAYFINDER -> "Wayfinder";
            default -> "Unchosen";
        });
    }

    private long focusCooldownRemaining(Plot plot) {
        if (AscensionFocus.parse(plot.getAscensionFocus()) == AscensionFocus.UNCHOSEN) return 0L;
        long days = Math.max(0L, plugin.getConfig().getLong("leveling.disciplines.change_cooldown_days", 7L));
        return Math.max(0L, plot.getAscensionFocusChangedAt() + days * 86_400_000L - System.currentTimeMillis());
    }

    private String focusCooldownLine(Player player, Plot plot) {
        long remaining = focusCooldownRemaining(plot);
        if (remaining <= 0L) return tr(player, "ascension_focus_change_ready", "&aA discipline may be selected now.");
        return tr(player, "ascension_focus_change_wait", "&eChange available in {DAYS} day(s).")
                .replace("{DAYS}", String.valueOf((remaining + 86_399_999L) / 86_400_000L));
    }

    private int trackStart(int level, int maxLevel) {
        int start = Math.max(1, level - 3);
        return Math.min(start, Math.max(1, maxLevel - 6));
    }

    private String progressBar(int level, int maxLevel) {
        int filled = Math.max(0, Math.min(10, (int) Math.round(level * 10.0D / Math.max(1, maxLevel))));
        return "&a" + "|".repeat(filled) + "&8" + "|".repeat(10 - filled);
    }

    private double moneyCost(int level) {
        return Math.max(0.0D, plugin.cfg().getLevelBaseCost() * (level * plugin.cfg().getLevelCostMultiplier()));
    }

    private double blockCost(int level) {
        double configured = plugin.getConfig().getDouble("leveling.upgrades.blocks_costs." + level, -1.0D);
        if (configured > 0.0D) return configured;
        double legacy = plugin.getConfig().getDouble("leveling.blocks_costs." + level, -1.0D);
        if (legacy > 0.0D) return legacy;
        return 250.0D * level;
    }

    private boolean moneyAvailable() {
        if (!plugin.getConfig().getBoolean("leveling.upgrades.allow_vault_payment", true)) return false;
        CurrencyType type = plugin.cfg().getLevelCostType();
        if (type == null) return false;
        String name = type.name().toUpperCase(Locale.ROOT);
        if (!name.contains("VAULT") && !name.contains("MONEY")) return true;
        try {
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            return vault != null && vault.isEnabled() && plugin.eco().isVaultReady();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean blocksAvailable() {
        return plugin.getConfig().getBoolean("leveling.upgrades.allow_block_payment", true)
                && plugin.getConfig().getBoolean("economy.blocks.enabled", true) && blocksType() != null;
    }

    private boolean paymentAvailable(Payment payment) {
        return payment == Payment.MONEY ? moneyAvailable() : blocksAvailable();
    }

    private CurrencyType blocksType() {
        for (String name : List.of("CLAIM_BLOCKS", "BLOCKS")) {
            try { return CurrencyType.valueOf(name); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private String formatCost(Player player, double cost, CurrencyType type) {
        if (type == null) return String.valueOf(Math.round(cost));
        if (type == blocksType()) return Math.round(cost) + " " + tr(player, "ascension_currency_claimblocks", "ClaimBlocks");
        return plugin.eco().format(cost, type);
    }

    private String formatBalance(Player player, CurrencyType type) {
        if (type == null) return "?";
        if (type == blocksType() && plugin.getClaimBlockManager() != null) {
            return String.valueOf(plugin.getClaimBlockManager().getAvailableBlocks(player.getUniqueId()));
        }
        try {
            Object economy = plugin.eco();
            for (String methodName : List.of("getBalance", "balance")) {
                try {
                    Method method = economy.getClass().getMethod(methodName, Player.class, CurrencyType.class);
                    Object result = method.invoke(economy, player, type);
                    if (result instanceof Number number) return plugin.eco().format(number.doubleValue(), type);
                } catch (ReflectiveOperationException ignored) {}
            }
        } catch (Throwable ignored) {}
        return "?";
    }

    private void guideCard(Inventory inventory, int slot, Material material, Player player, String id,
                           String fallbackName, List<String> fallbackLore) {
        inventory.setItem(slot, item(material, tr(player, "ascension_guide_" + id + "_name", fallbackName),
                trList(player, "ascension_guide_" + id + "_lore", fallbackLore)));
    }

    private boolean canOpen(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return false;
        }
        if (!ownsPlot(player, plot)) {
            plugin.msg().send(player, "no_perm");
            return false;
        }
        return true;
    }

    private boolean ownsPlot(Player player, Plot plot) {
        return player != null && plot != null && player.getUniqueId().equals(plot.getOwner());
    }

    private void close(Player player) {
        plugin.effects().playMenuClose(player);
        player.closeInventory();
    }

    private void paintHall(Inventory inventory) {
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : new int[]{0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 52, 53}) {
            inventory.setItem(slot, GUIManager.createItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));
        }
    }

    private void paintGuide(Inventory inventory) {
        fill(inventory, Material.BROWN_STAINED_GLASS_PANE);
        for (int slot : new int[]{0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 52, 53}) {
            inventory.setItem(slot, GUIManager.createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
    }

    private void paintDiscipline(Inventory inventory) {
        fill(inventory, Material.CYAN_STAINED_GLASS_PANE);
        for (int slot : new int[]{0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 43, 44}) {
            inventory.setItem(slot, GUIManager.createItem(Material.BLUE_STAINED_GLASS_PANE, " ", List.of()));
        }
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = GUIManager.createItem(material, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return GUIManager.createItem(material, color(name), lore.stream().map(this::color).toList());
    }

    private ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String plotName(Plot plot) {
        return plot.getPlotName() == null || plot.getPlotName().isBlank() ? plot.getOwnerName() + "'s Plot" : plot.getPlotName();
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }

    private void send(Player player, String key, String fallback, Map<String, String> replacements) {
        String message = tr(player, key, fallback);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(color(message));
    }

    private String color(String value) { return GUIManager.color(value == null ? "" : value); }

    private String humanize(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        StringBuilder output = new StringBuilder();
        for (String part : value.replace('-', ' ').replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.isBlank()) continue;
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return output.toString();
    }

    private String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    private record Bounds(int x1, int z1, int x2, int z2) {
        private static final Bounds INVALID = new Bounds(0, 0, 0, 0);
    }
}
