package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InfoGUI {

    private final AegisGuard plugin;

    public InfoGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private enum CodexSection {
        ROOT,
        QUICKSTART,
        CLAIMING,
        TRAVEL,
        MENUS,
        SECURITY,
        ECONOMY,
        IDENTITY,
        ADVANCED
    }

    public static class InfoHolder implements InventoryHolder {
        private final CodexSection section;

        public InfoHolder(CodexSection section) {
            this.section = section;
        }

        public CodexSection getSection() {
            return section;
        }

        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, CodexSection.ROOT);
    }

    private void open(Player player, CodexSection section) {
        String title = plugin.gui().title(
                player,
                titleKey(section),
                fallbackTitle(section)
        );

        Inventory inv = Bukkit.createInventory(new InfoHolder(section), 45, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        if (section == CodexSection.ROOT) {
            buildRoot(player, inv);
        } else {
            buildSection(player, inv, section);
        }

        inv.setItem(40, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.gui().tr(player, section == CodexSection.ROOT ? "button_back_menu" : "button_back",
                        section == CodexSection.ROOT ? "&fBack to Menu" : "&e⟵ Back"),
                plugin.gui().trList(
                        player,
                        section == CodexSection.ROOT ? "back_menu_lore" : "back_lore",
                        List.of(section == CodexSection.ROOT ? "&7Return to the main panel." : "&7Return to the guide overview.")
                )
        ));

        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void buildRoot(Player player, Inventory inv) {
        inv.setItem(4, GUIManager.createItem(
                Material.KNOWLEDGE_BOOK,
                plugin.gui().tr(player, "codex_root_welcome_name", "&bWelcome to the Guardian's Guide"),
                plugin.gui().trList(player, "codex_root_welcome_lore", List.of(
                        "&7Open any chapter below to read a",
                        "&7focused guide for claiming, travel,",
                        "&7security, economy, and more.",
                        " ",
                        "&eEach chapter opens its own help menu."
                ))
        ));

        inv.setItem(10, sectionItem(player, Material.GOLDEN_HOE, "codex_claim_title", "&e§lI. Claiming", "codex_claim_lore",
                List.of(
                        "&7Learn how to claim land,",
                        "&7select corners, and create",
                        "&7shared group claims."
                )));

        inv.setItem(12, sectionItem(player, Material.ENDER_PEARL, "codex_travel_title", "&b§lII. Travel", "codex_travel_lore",
                List.of(
                        "&7Review homes, plot spawn,",
                        "&7visit travel, and server",
                        "&7warp-style movement."
                )));

        inv.setItem(14, sectionItem(player, Material.WRITABLE_BOOK, "codex_menus_title", "&d§lIII. Menus", "codex_menus_lore",
                List.of(
                        "&7Learn what each major",
                        "&7AegisGuard menu does and",
                        "&7where to manage your plot."
                )));

        inv.setItem(16, sectionItem(player, Material.SHIELD, "codex_security_title", "&c§lIV. Security", "codex_security_lore",
                List.of(
                        "&7See how trust, bans,",
                        "&7flags, and protection rules",
                        "&7keep your plot secure."
                )));

        inv.setItem(22, sectionItem(player, Material.GOLD_INGOT, "codex_economy_title", "&6§lV. Economy", "codex_economy_lore",
                List.of(
                        "&7Read about ClaimBlocks,",
                        "&7upkeep, expansion costs,",
                        "&7and market systems."
                )));

        inv.setItem(24, sectionItem(player, Material.NAME_TAG, "codex_identity_title", "&3§lVI. Identity", "codex_identity_lore",
                List.of(
                        "&7Customize plot names,",
                        "&7descriptions, cosmetics,",
                        "&7and presentation."
                )));

        inv.setItem(31, sectionItem(player, Material.EXPERIENCE_BOTTLE, "codex_advanced_title", "&5§lVII. Advanced", "codex_advanced_lore",
                List.of(
                        "&7Explore leveling, zones,",
                        "&7rentals, TradeStalls, and",
                        "&7other advanced systems."
                )));

        inv.setItem(36, GUIManager.createItem(
                Material.BOOK,
                plugin.gui().tr(player, "codex_root_tip_name", "&eQuick Start"),
                plugin.gui().trList(player, "codex_root_tip_lore", List.of(
                        "&7Open a short getting-started guide",
                        "&7for claiming, menus, travel, and",
                        "&7the most useful early commands.",
                        " ",
                        "&eClick to open quick help."
                ))
        ));
    }

    private ItemStack sectionItem(Player player, Material material, String titleKey, String titleFallback, String loreKey, List<String> fallbackLore) {
        List<String> lore = new ArrayList<>(plugin.gui().trList(player, loreKey, fallbackLore));
        lore.add(" ");
        lore.add(plugin.gui().tr(player, "codex_open_section_lore", "&eClick to open this guide section."));
        return GUIManager.createItem(material, plugin.gui().tr(player, titleKey, titleFallback), lore);
    }

    private void buildSection(Player player, Inventory inv, CodexSection section) {
        inv.setItem(4, GUIManager.createItem(
                headerMaterial(section),
                plugin.gui().tr(player, headerKey(section), headerFallback(section)),
                plugin.gui().trList(player, headerLoreKey(section), headerLoreFallback(section))
        ));

        switch (section) {
            case QUICKSTART -> buildQuickstartSection(player, inv);
            case CLAIMING -> buildClaimingSection(player, inv);
            case TRAVEL -> buildTravelSection(player, inv);
            case MENUS -> buildMenusSection(player, inv);
            case SECURITY -> buildSecuritySection(player, inv);
            case ECONOMY -> buildEconomySection(player, inv);
            case IDENTITY -> buildIdentitySection(player, inv);
            case ADVANCED -> buildAdvancedSection(player, inv);
            default -> { }
        }
    }

    private void buildQuickstartSection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.COMPASS, "codex_start_menu_name", "&bOpen Your Main Menu",
                "codex_start_menu_lore", List.of(
                        "&7Use &b/ag menu &7to open the main",
                        "&7AegisGuard control panel at any time.",
                        " ",
                        "&7That menu is your fastest route to",
                        "&7settings, travel, markets, leveling,",
                        "&7the codex, and most daily tools."
                )));
        inv.setItem(12, infoCard(player, Material.LIGHTNING_ROD, "codex_start_wand_name", "&eGet The Aegis Scepter",
                "codex_start_wand_lore", List.of(
                        "&7Use &b/ag wand &7to receive the",
                        "&7Aegis Scepter for land selection.",
                        " ",
                        "&7Right-click the first corner block.",
                        "&7Left-click the opposite corner block."
                )));
        inv.setItem(14, infoCard(player, Material.GRASS_BLOCK, "codex_start_claim_name", "&aClaim Your First Plot",
                "codex_start_claim_lore", List.of(
                        "&7After selecting both corners, use",
                        "&b/ag claim &7to create your plot.",
                        " ",
                        "&7The plugin checks overlap, world",
                        "&7limits, and required costs before",
                        "&7confirming the land."
                )));
        inv.setItem(16, infoCard(player, Material.WRITABLE_BOOK, "codex_start_group_name", "&6Start A Group Plot",
                "codex_start_group_lore", List.of(
                        "&7Want shared land? Create a group with",
                        "&b/ag group create <name>&7 first.",
                        " ",
                        "&7Invite players, fund the treasury,",
                        "&7then use &b/ag group claim&7 when",
                        "&7your team is ready."
                )));
        inv.setItem(22, infoCard(player, Material.ENDER_PEARL, "codex_start_travel_name", "&bTravel & Return",
                "codex_start_travel_lore", List.of(
                        "&7Use &b/ag home &7to return to your",
                        "&7plot and &b/ag visit &7to reach trusted",
                        "&7or server-listed destinations.",
                        " ",
                        "&7If you ever get stuck, try",
                        "&b/ag stuck &7for recovery."
                )));
        inv.setItem(24, infoCard(player, Material.REDSTONE_TORCH, "codex_start_settings_name", "&cSecure Your Plot",
                "codex_start_settings_lore", List.of(
                        "&7Open Plot Settings from the main menu",
                        "&7to review entry, containers, mobs,",
                        "&7redstone, and other protections.",
                        " ",
                        "&7Open Members & Roles to trust friends",
                        "&7or remove unwanted visitors."
                )));
        inv.setItem(28, infoCard(player, Material.KNOWLEDGE_BOOK, "codex_start_codex_name", "&dUse The Codex",
                "codex_start_codex_lore", List.of(
                        "&7Each chapter in this guide explains",
                        "&7a different part of AegisGuard.",
                        " ",
                        "&7Come back here whenever you forget",
                        "&7a command, a menu, or how one of",
                        "&7the newer systems works."
                )));
        inv.setItem(30, infoCard(player, Material.PAPER, "codex_start_help_name", "&fHelpful Commands",
                "codex_start_help_lore", List.of(
                        "&b/ag menu &7- open the main menu",
                        "&b/ag wand &7- get the claim wand",
                        "&b/ag claim &7- confirm your plot",
                        "&b/ag visit &7- open travel options",
                        "&b/ag help &7- view command help"
                )));
    }

    private void buildClaimingSection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.LIGHTNING_ROD, "codex_claim_step1_name", "&eGet Your Scepter",
                "codex_claim_step1_lore", List.of(
                        "&7Use &b/ag wand &7to receive",
                        "&7the Aegis Scepter.",
                        " ",
                        "&7Right-click selects the first corner,",
                        "&7left-click selects the second corner.",
                        "&8The wand should mark land, not place itself."
                )));
        inv.setItem(12, infoCard(player, Material.GRASS_BLOCK, "codex_claim_step2_name", "&aSelect Land",
                "codex_claim_step2_lore", List.of(
                        "&7Choose two corners in the same world.",
                        "&7Your selection becomes the outline",
                        "&7for the claim you are about to create.",
                        " ",
                        "&7Pick enough room for your base, paths,",
                        "&7and any early storage or farms."
                )));
        inv.setItem(14, infoCard(player, Material.EMERALD, "codex_claim_step3_name", "&bConfirm The Claim",
                "codex_claim_step3_lore", List.of(
                        "&7Use &a/ag claim &7after selecting",
                        "&7both corners.",
                        " ",
                        "&7AegisGuard checks overlap, limits,",
                        "&7and cost before creating the plot.",
                        "&8If the claim fails, read the error line in chat."
                )));
        inv.setItem(16, infoCard(player, Material.PLAYER_HEAD, "codex_claim_group_name", "&6Group Claims",
                "codex_claim_group_lore", List.of(
                        "&7Create a group first with",
                        "&e/ag group create <name>&7.",
                        " ",
                        "&7Invite nearby players, build your treasury,",
                        "&7then use &e/ag group claim&7.",
                        "&8Group plots are ideal for shared towns or guild bases."
                )));
        inv.setItem(22, infoCard(player, Material.SHIELD, "codex_claim_limits_name", "&cClaim Safety",
                "codex_claim_limits_lore", List.of(
                        "&7Claims cannot overlap existing plots",
                        "&7or external protected areas when",
                        "&7compatibility checks are enabled.",
                        " ",
                        "&7This helps stop accidental griefing",
                        "&7and messy border conflicts."
                )));
        inv.setItem(24, infoCard(player, Material.CHEST, "codex_claim_starter_name", "&aStarter Claims",
                "codex_claim_starter_lore", List.of(
                        "&7Many servers offer a starter claim",
                        "&7or starter group land based on",
                        "&7the configured first-claim rules.",
                        " ",
                        "&7Check whether your server uses free",
                        "&7starter land or a treasury requirement."
                )));
        inv.setItem(28, infoCard(player, Material.GOLD_INGOT, "codex_claim_group_money_name", "&6Group Treasury",
                "codex_claim_group_money_lore", List.of(
                        "&7Use &e/ag group deposit <amount> &7to",
                        "&7fund the group treasury before",
                        "&7claiming or expanding shared land.",
                        " ",
                        "&7The treasury can help cover growth",
                        "&7instead of charging one member alone."
                )));
        inv.setItem(30, infoCard(player, Material.PAPER, "codex_claim_fail_name", "&cIf Claiming Fails",
                "codex_claim_fail_lore", List.of(
                        "&7Check for overlap, world limits,",
                        "&7claim size rules, required funds,",
                        "&7or external protection conflicts.",
                        " ",
                        "&7If needed, try a smaller area or",
                        "&7move farther from nearby claims."
                )));
        inv.setItem(32, infoCard(player, Material.WRITABLE_BOOK, "codex_claim_commands_name", "&bClaim Commands",
                "codex_claim_commands_lore", List.of(
                        "&e/ag wand &7- get the scepter",
                        "&e/ag claim &7- create a personal plot",
                        "&e/ag group claim &7- create a shared plot",
                        "&e/ag unclaim &7- remove your current plot"
                )));
    }

    private void buildTravelSection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.ENDER_PEARL, "codex_travel_home_name", "&bPlot Home",
                "codex_travel_home_lore", List.of(
                        "&e/ag home &7teleports you to your plot.",
                        " ",
                        "&e/ag setspawn &7sets a custom plot spawn",
                        "&7for yourself and your visitors.",
                        "&8Set this after building an entrance or foyer."
                )));
        inv.setItem(12, infoCard(player, Material.COMPASS, "codex_travel_visit_name", "&eVisit Menu",
                "codex_travel_visit_lore", List.of(
                        "&e/ag visit &7opens travel options for",
                        "&7trusted plots, your own plots,",
                        "&7and server travel locations.",
                        " ",
                        "&7This is the easiest way to jump between",
                        "&7friendly claims without running across the map."
                )));
        inv.setItem(14, infoCard(player, Material.NETHER_STAR, "codex_travel_server_name", "&6Server Locations",
                "codex_travel_server_lore", List.of(
                        "&7Server owners can create managed",
                        "&7warp-style plot destinations for",
                        "&7spawn, market, or showcase areas.",
                        " ",
                        "&7If your server uses public hubs,",
                        "&7they may appear here."
                )));
        inv.setItem(16, infoCard(player, Material.FEATHER, "codex_travel_tip_name", "&fTravel Tips",
                "codex_travel_tip_lore", List.of(
                        "&7Set your plot spawn after claiming.",
                        " ",
                        "&7Use visit mode to reach friendly",
                        "&7or trusted plots faster.",
                        "&8Keep your spawn clear so arrivals are safe."
                )));
        inv.setItem(22, infoCard(player, Material.PLAYER_HEAD, "codex_travel_trusted_name", "&aTrusted Plot Travel",
                "codex_travel_trusted_lore", List.of(
                        "&7If another player trusts you, their",
                        "&7plot can appear in the visit menu",
                        "&7for quicker travel.",
                        " ",
                        "&7This is useful for shared builds,",
                        "&7town projects, and allied groups."
                )));
        inv.setItem(24, infoCard(player, Material.OAK_DOOR, "codex_travel_room_name", "&6Rooms & Rentals",
                "codex_travel_room_lore", List.of(
                        "&7Rented rooms and subplots can have",
                        "&7their own room spawn, making hotels",
                        "&7and markets easier to navigate.",
                        " ",
                        "&7If you rent a room, open its controls",
                        "&7to manage room teleport behavior."
                )));
        inv.setItem(28, infoCard(player, Material.LADDER, "codex_travel_unstuck_name", "&eUnstuck & Recovery",
                "codex_travel_unstuck_lore", List.of(
                        "&e/ag stuck &7can help if you become",
                        "&7trapped inside a build, room,",
                        "&7or awkward corner of a claim.",
                        " ",
                        "&7Use it before asking staff for help",
                        "&7if you are simply wedged in terrain."
                )));
    }

    private void buildMenusSection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.BOOK, "codex_menus_main_name", "&dMain Menu",
                "codex_menus_main_lore", List.of(
                        "&e/ag menu &7opens the main AegisGuard",
                        "&7control panel for most player actions.",
                        " ",
                        "&7From here you can reach settings,",
                        "&7travel, codex help, and plot tools."
                )));
        inv.setItem(12, infoCard(player, Material.REDSTONE_TORCH, "codex_menus_settings_name", "&6Plot Settings",
                "codex_menus_settings_lore", List.of(
                        "&7Use the Settings menu to control",
                        "&7PvP, entry, containers, mobs,",
                        "&7redstone, and related protections.",
                        " ",
                        "&7Review these after claiming so your",
                        "&7plot behaves the way you want."
                )));
        inv.setItem(14, infoCard(player, Material.PLAYER_HEAD, "codex_menus_members_name", "&eMembers & Roles",
                "codex_menus_members_lore", List.of(
                        "&7Use the Members menu to add friends,",
                        "&7change roles, and manage who can",
                        "&7build or interact on your plot.",
                        " ",
                        "&7This is safer than sharing everything",
                        "&7with every visitor."
                )));
        inv.setItem(16, infoCard(player, Material.SPYGLASS, "codex_menus_status_name", "&bClaim Status",
                "codex_menus_status_lore", List.of(
                        "&7Claim Status shows key plot info,",
                        "&7protections, bonuses, and current",
                        "&7state in one place.",
                        " ",
                        "&7Use it when you want a quick check",
                        "&7without opening several menus."
                )));
        inv.setItem(22, infoCard(player, Material.COMPARATOR, "codex_menus_preferences_name", "&3Preferences",
                "codex_menus_preferences_lore", List.of(
                        "&7Player preferences let you manage",
                        "&7language, sounds, and notification",
                        "&7behavior.",
                        " ",
                        "&7If a menu feels too noisy, this is",
                        "&7where you adjust your personal settings."
                )));
        inv.setItem(24, infoCard(player, Material.CHEST, "codex_menus_market_name", "&6Local Market",
                "codex_menus_market_lore", List.of(
                        "&7Market-related plots can expose a",
                        "&7Local Market button for stalls,",
                        "&7rentals, and linked shop systems.",
                        " ",
                        "&7Use it to browse rentable zones or",
                        "&7manage your own trade space."
                )));
        inv.setItem(28, infoCard(player, Material.KNOWLEDGE_BOOK, "codex_menus_info_name", "&bGuide & Info",
                "codex_menus_info_lore", List.of(
                        "&7This codex is meant to be your",
                        "&7in-game reference whenever you",
                        "&7forget how a feature works.",
                        " ",
                        "&7If you are unsure what a button does,",
                        "&7check this guide before experimenting."
                )));
    }

    private void buildSecuritySection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.IRON_DOOR, "codex_security_access_name", "&cTrusted Access",
                "codex_security_access_lore", List.of(
                        "&7Trusted players can be added from",
                        "&7the Members menu.",
                        " ",
                        "&7Roles decide what each member can do,",
                        "&7from simple access to full management."
                )));
        inv.setItem(12, infoCard(player, Material.BARRIER, "codex_security_ban_name", "&4Kick & Ban",
                "codex_security_ban_lore", List.of(
                        "&e/ag kick <player> &7removes someone",
                        "&7from the plot temporarily.",
                        " ",
                        "&e/ag ban <player> &7blocks entry until",
                        "&7you decide to lift the restriction."
                )));
        inv.setItem(14, infoCard(player, Material.SHIELD, "codex_security_flags_name", "&6Protection Flags",
                "codex_security_flags_lore", List.of(
                        "&7Flags control things like PvP,",
                        "&7entry, containers, redstone,",
                        "&7vehicles, and animals.",
                        " ",
                        "&7These are the main rules that shape",
                        "&7how safe or open your plot feels."
                )));
        inv.setItem(16, infoCard(player, Material.BELL, "codex_security_notify_name", "&eNotifications",
                "codex_security_notify_lore", List.of(
                        "&7Use notification settings to control",
                        "&7claim greetings and admin updates",
                        "&7separately.",
                        " ",
                        "&7This helps you keep useful alerts while",
                        "&7turning off the ones you do not want."
                )));
        inv.setItem(22, infoCard(player, Material.CHEST, "codex_security_room_name", "&6Rooms & Subplots",
                "codex_security_room_lore", List.of(
                        "&7Rented subplots protect the renter's",
                        "&7space like a managed mini-claim,",
                        "&7while the landlord keeps oversight.",
                        " ",
                        "&7This is useful for hotels, apartments,",
                        "&7market stalls, and guild compounds."
                )));
        inv.setItem(24, infoCard(player, Material.ARMOR_STAND, "codex_security_entities_name", "&eEntity Protection",
                "codex_security_entities_lore", List.of(
                        "&7Plots can protect containers,",
                        "&7decorative entities, vehicles,",
                        "&7and many common grief vectors.",
                        " ",
                        "&7That includes the kinds of small grief",
                        "&7attempts people often overlook."
                )));
        inv.setItem(28, infoCard(player, Material.NETHER_STAR, "codex_security_staff_name", "&bServer Staff Access",
                "codex_security_staff_lore", List.of(
                        "&7Server owners can allow trusted",
                        "&7staff groups to manage server zones,",
                        "&7market plots, and special areas.",
                        " ",
                        "&7This reduces the need to rely on",
                        "&7manual bypass for routine staff work."
                )));
    }

    private void buildEconomySection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.GOLD_INGOT, "codex_economy_blocks_name", "&6ClaimBlocks",
                "codex_economy_blocks_lore", List.of(
                        "&7ClaimBlocks are used for progression",
                        "&7and expansion-related systems.",
                        " ",
                        "&e/ag blocks &7shows your balance and",
                        "&7related claim-block commands."
                )));
        inv.setItem(12, infoCard(player, Material.CLOCK, "codex_economy_upkeep_name", "&eUpkeep",
                "codex_economy_upkeep_lore", List.of(
                        "&7Servers can require upkeep or taxes",
                        "&7to keep land active.",
                        " ",
                        "&7Warnings can be sent through",
                        "&7AegisGuard notifications."
                )));
        inv.setItem(14, infoCard(player, Material.CHEST, "codex_economy_market_name", "&aMarkets",
                "codex_economy_market_lore", List.of(
                        "&7Use the market and local market",
                        "&7systems to sell plots, rent zones,",
                        "&7or browse TradeStalls.",
                        " ",
                        "&7Some servers keep this global, while",
                        "&7others let players build house markets."
                )));
        inv.setItem(16, infoCard(player, Material.EMERALD_BLOCK, "codex_economy_expand_name", "&bExpansions",
                "codex_economy_expand_lore", List.of(
                        "&7Expansion requests may be queued",
                        "&7for admin review or approved instantly,",
                        "&7depending on server configuration.",
                        " ",
                        "&7If your frontier feels cramped, this is",
                        "&7how you grow it safely."
                )));
        inv.setItem(22, infoCard(player, Material.BARREL, "codex_economy_stalls_name", "&dTradeStalls",
                "codex_economy_stalls_lore", List.of(
                        "&7TradeStalls let sellers create",
                        "&7protected chest-based storefronts",
                        "&7inside local markets and rentable areas.",
                        " ",
                        "&7They work even if the server does not",
                        "&7use a separate third-party shop plugin."
                )));
        inv.setItem(24, infoCard(player, Material.HOPPER, "codex_economy_stall_setup_name", "&eTradeStall Setup",
                "codex_economy_stall_setup_lore", List.of(
                        "&7Place a supported chest or barrel,",
                        "&7add a stall sign, then manage item",
                        "&7prices and currency from the stall UI.",
                        " ",
                        "&7Servers can allow money, ClaimBlocks,",
                        "&7or both for stall pricing."
                )));
        inv.setItem(28, infoCard(player, Material.DIAMOND, "codex_economy_currency_name", "&bCurrencies",
                "codex_economy_currency_lore", List.of(
                        "&7Servers can allow money, ClaimBlocks,",
                        "&7or both depending on the feature",
                        "&7and the configured economy rules.",
                        " ",
                        "&7Read each menu carefully so you know",
                        "&7which currency that action will use."
                )));
        inv.setItem(30, infoCard(player, Material.CLOCK, "codex_economy_warning_name", "&cKeep An Eye On Warnings",
                "codex_economy_warning_lore", List.of(
                        "&7Low treasury warnings, upkeep notices,",
                        "&7and expansion costs can be sent",
                        "&7through AegisGuard notifications.",
                        " ",
                        "&7If you ignore them for too long, your",
                        "&7next action may fail unexpectedly."
                )));
    }

    private void buildIdentitySection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.NAME_TAG, "codex_identity_name_name", "&3Plot Identity",
                "codex_identity_name_lore", List.of(
                        "&e/ag rename <name> &7sets a display name",
                        "&7for your plot.",
                        " ",
                        "&e/ag setdesc <text> &7adds a description",
                        "&7that helps visitors understand the plot."
                )));
        inv.setItem(12, infoCard(player, Material.PAINTING, "codex_identity_cosmetics_name", "&dCosmetics",
                "codex_identity_cosmetics_lore", List.of(
                        "&7Cosmetics let you adjust borders,",
                        "&7particles, and other plot visuals.",
                        " ",
                        "&7Use them to make your plot feel more",
                        "&7distinct without changing its purpose."
                )));
        inv.setItem(14, infoCard(player, Material.GRASS_BLOCK, "codex_identity_biome_name", "&aBiome Style",
                "codex_identity_biome_lore", List.of(
                        "&7The biome menu can change how",
                        "&7your plot feels visually, depending",
                        "&7on server configuration.",
                        " ",
                        "&7This can help match a build theme",
                        "&7or improve the mood of a district."
                )));
        inv.setItem(16, infoCard(player, Material.LADDER, "codex_identity_stuck_name", "&eUnstuck",
                "codex_identity_stuck_lore", List.of(
                        "&e/ag stuck &7is available if you get",
                        "&7trapped in a claim or awkward build area.",
                        " ",
                        "&7It is a practical safety command, even",
                        "&7though it also affects player flow."
                )));
        inv.setItem(22, infoCard(player, Material.OAK_SIGN, "codex_identity_greeting_name", "&bGreetings & Titles",
                "codex_identity_greeting_lore", List.of(
                        "&7Plots can greet visitors with chat",
                        "&7messages or titles when entry and",
                        "&7notification settings allow it.",
                        " ",
                        "&7This is a good place to welcome guests",
                        "&7or label shops and special areas."
                )));
        inv.setItem(24, infoCard(player, Material.GLOW_ITEM_FRAME, "codex_identity_showcase_name", "&ePresentation",
                "codex_identity_showcase_lore", List.of(
                        "&7A clear name, good description,",
                        "&7and a tidy spawn point help visitors",
                        "&7remember your plot more easily.",
                        " ",
                        "&7Small presentation details make your",
                        "&7plot feel much more complete."
                )));
    }

    private void buildAdvancedSection(Player player, Inventory inv) {
        inv.setItem(10, infoCard(player, Material.EXPERIENCE_BOTTLE, "codex_advanced_level_name", "&5Plot Leveling",
                "codex_advanced_level_lore", List.of(
                        "&7Leveling can unlock perks, bonuses,",
                        "&7and progression benefits for your plot.",
                        " ",
                        "&7Open Plot Ascension to preview what",
                        "&7the next tier will actually give you."
                )));
        inv.setItem(12, infoCard(player, Material.OAK_DOOR, "codex_advanced_zones_name", "&6Zones & Rentals",
                "codex_advanced_zones_lore", List.of(
                        "&7Zones let you divide your plot into",
                        "&7rooms, rental areas, or market sections.",
                        " ",
                        "&7This is one of the best tools for inns,",
                        "&7hotels, malls, and apartment builds."
                )));
        inv.setItem(14, infoCard(player, Material.CHEST, "codex_advanced_local_market_name", "&eLocal Market",
                "codex_advanced_local_market_lore", List.of(
                        "&7Local Market helps server owners and",
                        "&7players build shop districts, stalls,",
                        "&7and rentable business spaces.",
                        " ",
                        "&7If a plot supports market features,",
                        "&7this becomes the local business hub."
                )));
        inv.setItem(16, infoCard(player, Material.RECOVERY_COMPASS, "codex_advanced_recovery_name", "&bRecovery & Safety",
                "codex_advanced_recovery_lore", List.of(
                        "&7Admins can use diagnostics, migration,",
                        "&7snapshots, and restore tools to recover",
                        "&7from mistakes or server issues.",
                        " ",
                        "&7These tools are especially important",
                        "&7on long-running or public servers."
                )));
        inv.setItem(22, infoCard(player, Material.TOTEM_OF_UNDYING, "codex_advanced_tip_name", "&fGood Practice",
                "codex_advanced_tip_lore", List.of(
                        "&7Review your settings after claiming,",
                        "&7keep your spawn updated, and use",
                        "&7group tools carefully on shared plots.",
                        " ",
                        "&7A small review early can save a lot",
                        "&7of confusion later."
                )));
        inv.setItem(24, infoCard(player, Material.MAP, "codex_advanced_migration_name", "&6Migration",
                "codex_advanced_migration_lore", List.of(
                        "&7Admins can preview and import land",
                        "&7from supported protection plugins",
                        "&7through the migration tools.",
                        " ",
                        "&7Use the migration wand and admin UI",
                        "&7to review before converting land."
                )));
        inv.setItem(28, infoCard(player, Material.WRITABLE_BOOK, "codex_advanced_snapshot_name", "&bSnapshots",
                "codex_advanced_snapshot_lore", List.of(
                        "&7Snapshots and restore tools help",
                        "&7staff recover claims after mistakes,",
                        "&7damage, or rollback situations.",
                        " ",
                        "&7They are especially useful after bad",
                        "&7expansions, grief, or server issues."
                )));
        inv.setItem(30, infoCard(player, Material.BEACON, "codex_advanced_diagnostics_name", "&eDiagnostics",
                "codex_advanced_diagnostics_lore", List.of(
                        "&7Server owners can use doctor reports",
                        "&7and admin tools to inspect setup",
                        "&7issues more easily.",
                        " ",
                        "&7If something feels wrong, diagnostics",
                        "&7are the first place staff should look."
                )));
    }

    private ItemStack infoCard(Player player, Material material, String nameKey, String nameFallback, String loreKey, List<String> loreFallback) {
        return GUIManager.createItem(
                material,
                plugin.gui().tr(player, nameKey, nameFallback),
                plugin.gui().trList(player, loreKey, loreFallback)
        );
    }

    private String titleKey(CodexSection section) {
        return switch (section) {
            case ROOT -> "codex_gui_title";
            case QUICKSTART -> "codex_quickstart_page_title";
            case CLAIMING -> "codex_claim_page_title";
            case TRAVEL -> "codex_travel_page_title";
            case MENUS -> "codex_menus_page_title";
            case SECURITY -> "codex_security_page_title";
            case ECONOMY -> "codex_economy_page_title";
            case IDENTITY -> "codex_identity_page_title";
            case ADVANCED -> "codex_advanced_page_title";
        };
    }

    private String fallbackTitle(CodexSection section) {
        return switch (section) {
            case ROOT -> "&b✦ Guardian's Guide ✦";
            case QUICKSTART -> "&e✦ Quick Start";
            case CLAIMING -> "&e✦ Claiming Guide";
            case TRAVEL -> "&b✦ Travel Guide";
            case MENUS -> "&d✦ Menu Guide";
            case SECURITY -> "&c✦ Security Guide";
            case ECONOMY -> "&6✦ Economy Guide";
            case IDENTITY -> "&3✦ Identity Guide";
            case ADVANCED -> "&5✦ Advanced Guide";
        };
    }

    private String headerKey(CodexSection section) {
        return switch (section) {
            case QUICKSTART -> "codex_quickstart_header";
            case CLAIMING -> "codex_claim_header";
            case TRAVEL -> "codex_travel_header";
            case MENUS -> "codex_menus_header";
            case SECURITY -> "codex_security_header";
            case ECONOMY -> "codex_economy_header";
            case IDENTITY -> "codex_identity_header";
            case ADVANCED -> "codex_advanced_header";
            default -> "codex_gui_title";
        };
    }

    private String headerFallback(CodexSection section) {
        return switch (section) {
            case QUICKSTART -> "&eStart Here";
            case CLAIMING -> "&eClaiming & Group Plots";
            case TRAVEL -> "&bTravel & Visiting";
            case MENUS -> "&dMenus & Controls";
            case SECURITY -> "&cSecurity & Protections";
            case ECONOMY -> "&6Economy & Markets";
            case IDENTITY -> "&3Identity & Presentation";
            case ADVANCED -> "&5Advanced Systems";
            default -> "&bGuardian's Guide";
        };
    }

    private String headerLoreKey(CodexSection section) {
        return switch (section) {
            case QUICKSTART -> "codex_quickstart_header_lore";
            case CLAIMING -> "codex_claim_header_lore";
            case TRAVEL -> "codex_travel_header_lore";
            case MENUS -> "codex_menus_header_lore";
            case SECURITY -> "codex_security_header_lore";
            case ECONOMY -> "codex_economy_header_lore";
            case IDENTITY -> "codex_identity_header_lore";
            case ADVANCED -> "codex_advanced_header_lore";
            default -> "codex_gui_header_lore";
        };
    }

    private List<String> headerLoreFallback(CodexSection section) {
        return switch (section) {
            case QUICKSTART -> List.of("&7A simple starting path for new players", "&7who want to claim, secure, and travel quickly.");
            case CLAIMING -> List.of("&7Everything you need to know about", "&7claiming land and starting group plots.");
            case TRAVEL -> List.of("&7Teleportation, visits, homes, and", "&7server travel options in AegisGuard.");
            case MENUS -> List.of("&7A quick explanation of the main", "&7menus you will use most often.");
            case SECURITY -> List.of("&7How access, bans, and plot", "&7protection rules work together.");
            case ECONOMY -> List.of("&7ClaimBlocks, upkeep, markets,", "&7TradeStalls, and progression costs.");
            case IDENTITY -> List.of("&7Ways to make your plot feel", "&7personal and easier to recognize.");
            case ADVANCED -> List.of("&7Deeper systems for growth,", "&7rentals, admin safety, and more.");
            default -> List.of("&7Browse help topics and refresh", "&7your memory whenever you need.");
        };
    }

    private Material headerMaterial(CodexSection section) {
        return switch (section) {
            case QUICKSTART -> Material.BOOK;
            case CLAIMING -> Material.GOLDEN_HOE;
            case TRAVEL -> Material.ENDER_PEARL;
            case MENUS -> Material.WRITABLE_BOOK;
            case SECURITY -> Material.SHIELD;
            case ECONOMY -> Material.GOLD_INGOT;
            case IDENTITY -> Material.NAME_TAG;
            case ADVANCED -> Material.EXPERIENCE_BOTTLE;
            default -> Material.BOOK;
        };
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getInventory().getHolder() instanceof InfoHolder holder)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;

        int slot = e.getSlot();
        CodexSection section = holder.getSection();

        if (slot == 40) {
            if (section == CodexSection.ROOT) {
                plugin.gui().openMain(player);
            } else {
                open(player, CodexSection.ROOT);
            }
            plugin.effects().playMenuFlip(player);
            return;
        }

        if (slot == 44) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        if (section != CodexSection.ROOT) {
            if (e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                plugin.effects().playMenuFlip(player);
            }
            return;
        }

        CodexSection target = switch (slot) {
            case 36 -> CodexSection.QUICKSTART;
            case 10 -> CodexSection.CLAIMING;
            case 12 -> CodexSection.TRAVEL;
            case 14 -> CodexSection.MENUS;
            case 16 -> CodexSection.SECURITY;
            case 22 -> CodexSection.ECONOMY;
            case 24 -> CodexSection.IDENTITY;
            case 31 -> CodexSection.ADVANCED;
            default -> null;
        };

        if (target != null) {
            open(player, target);
            plugin.effects().playMenuFlip(player);
        }
    }
}
