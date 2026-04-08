<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong>
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/"><img src="https://img.shields.io/badge/Spigot-Download-orange?style=for-the-badge"></a>
  <a href="https://hangar.papermc.io/snazzyatoms/AegisGuard"><img src="https://img.shields.io/badge/Hangar-Download-green?style=for-the-badge"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy"><img src="https://img.shields.io/badge/CurseForge-Download-purple?style=for-the-badge"></a>
  <a href="https://github.com/snazzyatoms/AegisGuard/wiki"><img src="https://img.shields.io/badge/Wiki-Documentation-blue?style=for-the-badge"></a>
</p>

---

# AegisGuard v1.2.6

AegisGuard is a modern land protection and claim-management plugin for **Spigot**, **Paper**, **Purpur**, and **Folia** servers on **Minecraft 1.20+**.

Version **1.2.6** is a major return update. It expands AegisGuard beyond basic claiming with stronger protections, cleaner GUIs, better admin recovery, group and rental systems, built-in TradeStalls, migration tooling, richer notifications, and a much more polished language and configuration surface.

---

## Table of Contents

- [Server Compatibility](#server-compatibility)
- [What Is New In 1.2.6](#what-is-new-in-126)
- [Feature Overview](#feature-overview)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Plugin Integrations](#plugin-integrations)
- [Installation](#installation)
- [Quick Links](#quick-links)

---

## Server Compatibility

| Platform | Supported | Notes |
|----------|-----------|-------|
| **Spigot** | Yes | 1.20+ |
| **Paper** | Yes | Recommended |
| **Purpur** | Yes | Full support |
| **Folia** | Yes | Supported with Folia-aware scheduling |

---

## What Is New In 1.2.6

### Major Highlights

- Improved protection coverage for claims, rooms, rentals, server zones, and interaction-heavy edge cases
- Reworked GUI flow with better titles, lore, handbook guidance, back/exit behavior, and cleaner player/admin navigation
- Added a stronger **group plot** flow with treasury-backed progression and safer anti-abuse starter rules
- Expanded the **Frontier Expansion** system with better request review, unattended approval options, and clearer audit history
- Added a visual **migration wand** and migration admin GUI for supported external protection plugins
- Strengthened **snapshots**, rollback, recovery, and `/agadmin doctor` diagnostics
- Added the built-in **TradeStalls** system and improved **Local Market** support
- Improved rentable zones, subplots, rooms, and hotel-style management
- Restored **Sentinel's Scepter** server-zone claiming for staff through `/agadmin wand server` and `/agadmin claim`
- Cleaned up and synchronized **Old English**, **Spanish (MX)**, and **Spanish (AR)** with the active language and fallback layers

### Protection, Stability, and Recovery

- Safer movement-sensitive logic and better hot-path handling
- Improved hostile-mob cleanup inside protected claims
- Better protection handling for decorative entities, vehicles, containers, and interactions
- Cleaner staff and server-zone access without relying on bypass for normal management
- Snapshot-based recovery for plot restoration and administrative maintenance
- Better diagnostics and support reporting for live servers

### Groups, Social Flow, and Notifications

- Separate greeting notifications from admin notifications
- More event-aware notifications for:
  - group joins and leaves
  - treasury changes and low-balance warnings
  - upkeep and tax-related alerts
  - plot rename events
  - admin and review actions
- Group-first claiming flow with treasury support before the shared plot is created
- Safer starter sizing and anti-abuse timing around early member removal

### Frontier Expansion and Progression

- Improved expansion request GUI with clearer tier presentation and request context
- Better admin review flow with audit history and handled-request visibility
- Optional unattended auto-approval when no eligible reviewers are online
- Improved plot ascension presentation, active bonuses, and preview messaging
- `Expansion Horizons` is now teased as a future direction for frontier growth and larger progression paths

### Migration, Recovery, and Admin Tools

- Migration wand and visual migration workflow
- Focused-claim preview and import flow for supported sources
- Better metadata preservation during migration
- Manual snapshots and restoration tools
- `/agadmin doctor` report generation for troubleshooting and support intake

### Subplots, Rentals, and Shared Spaces

- Better subplot and rentable-zone protections
- Renter room controls with guest permissions and room spawn support
- Better landlord oversight and eviction flow
- Direct subplot creation support through `/ag subplot` and `/ag subzone`
- Better support for market stalls, rooms, hotels, and managed server spaces

### TradeStalls and Local Market

- Built-in **TradeStalls** for servers that do not want a separate market plugin
- Sign + chest or barrel storefront flow
- Buy and sell support using:
  - money
  - ClaimBlocks
- Better Local Market flow for plot-based browsing
- Configurable coexistence or priority handling with:
  - QuickShop
  - Shopkeepers
  - ChestShop
  - ExcellentShop

### UX, Language, and Documentation

- Better Guardian's Guide / Codex coverage for players learning the plugin
- Better starter experience with first-join wand + quickstart note
- More consistent GUI labels and lower lore text
- `lang/` remains the public editable folder
- `codex/` remains the internal fallback layer
- Hybrid English removed entirely
- README, config layout, and plugin metadata refreshed for 1.2.6

---

## Feature Overview

### Claiming and Land Protection

- Wand-based claim selection using the **Aegis Scepter**
- Restored admin server-zone selection using the **Sentinel's Scepter**
- Claim resize, unclaim, and recovery flow
- Plot flags for PvP, containers, entry, shops, vehicles, fire, redstone, safe-zone behavior, and more
- Better protection handling for rooms, rentals, and subzones inside larger plots

### ClaimBlocks Economy

- Starting ClaimBlock support for new players
- Passive ClaimBlock earning with anti-AFK checks
- Player opt-in or opt-out support for passive earnings
- ClaimBlocks exchange with Vault support
- Buy, sell, cooldown, lock, and exchange controls

### Groups and Shared Ownership

- Group creation before shared claiming
- Shared treasury for growth and progression
- Group-claim creation rules based on member count
- Safer anti-abuse logic for early-member padding
- Group-aware notifications and ownership flow

### Frontier Expansion and Plot Ascension

- Expansion request submission and review flow
- Queue or instant-style behavior depending on configuration
- Unattended review mode for quieter admin periods
- Plot Ascension with progression rewards and better bonus previews
- `Expansion Horizons` teased as a future progression branch

### Subplots, Rentals, and Rooms

- Subzones inside larger claims
- Rentable market stalls, rooms, hotel suites, and managed spaces
- Renter controls for guests, room access, and room spawn
- Zone browser and room control GUIs
- Direct subplot creation via command after wand selection

### TradeStalls and Local Market

- Native stall system for servers without a third-party shop plugin
- GUI browsing for stall buyers
- Listing management for sellers
- Support for money or ClaimBlocks as sale currency
- Local Market hub for rentals, stalls, and linked market integrations

### Migration, Diagnostics, and Recovery

- Visual migration tools for supported protection sources
- Snapshot creation and restoration
- Recovery tooling for crashes, mistakes, or moderation follow-up
- Diagnostics reporting for admins and support workflows

### Language and Accessibility

- Modern English
- Old English
- Spanish (MX)
- Spanish (AR)
- Synced active packs and fallback packs for cleaner language switching

---

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/aegis` | Main command |
| `/aegis menu` | Open the main GUI |
| `/aegis wand` | Receive the Aegis Scepter |
| `/aegis claim` | Claim selected land |
| `/aegis unclaim` | Remove your plot |
| `/aegis resize` | Resize an existing plot |
| `/aegis merge` | Merge adjacent plots |
| `/aegis cost` | Check claim cost |
| `/aegis home` | Teleport to plot spawn |
| `/aegis setspawn` | Set plot spawn point |
| `/aegis welcome <message>` | Set plot welcome text |
| `/aegis farewell <message>` | Set plot farewell text |
| `/aegis notify` | Manage greeting/admin notification preferences |
| `/aegis visit` | Open the travel menu |
| `/aegis market` | Open the market menu |
| `/aegis market local` | Open the Local Market for the current area |
| `/aegis sell <price>` | List a plot for sale |
| `/aegis unsell` | Remove a plot from sale |
| `/aegis auction` | Open the auction browser |
| `/aegis zone` | Open zone and rental management |
| `/aegis subplot [name]` | Create a subplot from your current selection |
| `/aegis subzone [name]` | Alias for subplot creation |
| `/aegis level` | Open plot ascension |
| `/aegis rename <name>` | Set plot display name |
| `/aegis setdesc <description>` | Set plot description |
| `/aegis blocks` | View ClaimBlock balance and options |
| `/aegis blocks earnings <on|off|status>` | Manage passive ClaimBlock earnings |
| `/aegis group create <name>` | Create a group |
| `/aegis group status` | View group status |
| `/aegis group deposit <amount>` | Deposit into the group treasury |
| `/aegis group claim` | Claim the first group plot |
| `/aegis stuck` | Escape being trapped in a claim |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/aegisadmin` | Main admin command |
| `/aegisadmin menu` | Open the admin GUI |
| `/aegisadmin reload` | Reload configuration and language data |
| `/aegisadmin bypass` | Toggle protection bypass |
| `/aegisadmin wand server` | Receive the Sentinel's Scepter for server-zone claims |
| `/aegisadmin claim` | Confirm the current selection as a server zone |
| `/aegisadmin wand migration` | Receive the migration wand |
| `/aegisadmin doctor` | Generate a diagnostics report |
| `/aegisadmin migrate` | Open the migration flow |
| `/aegisadmin snapshot here [reason]` | Create a manual recovery snapshot |
| `/aegisadmin restore here` | Restore the latest snapshot for the current plot |
| `/aegisadmin blocks <player> <add|remove|set> <amount>` | Manage player ClaimBlocks |

---

## Permissions

Player-facing permissions are normally bundled through `aegis.user`, and staff/admin access is normally bundled through `aegis.admin`.

Important 1.2.6 nodes include:

- `aegis.user`
- `aegis.admin`
- `aegis.admin.manage`
- `aegis.serverzone.manage`
- `aegis.admin.wand`
- `aegis.market.manage`
- `aegis.staff.co_owner`
- `aegis.staff.market_steward`
- `aegis.notify`
- `aegis.notify.bypass`
- `aegis.notify.others`
- `aegis.admin.migrate`
- `aegis.claimblocks.exchange`

For the full permission list, see:
- [plugin.yml](src/main/resources/plugin.yml)

---

## Configuration

AegisGuard 1.2.6 ships with a cleaner `config.yml` layout intended to be easier for server owners to understand and tune.

Key configuration areas include:

- storage backend and persistence
- economy and ClaimBlocks
- first-join starter kit
- group plots and treasury rules
- frontier expansion review and unattended approval behavior
- TradeStalls and Local Market bridge settings
- staff and server-zone access rules
- upkeep and tax notification behavior
- language folder and fallback behavior

### Language Folders

- `plugins/AegisGuard/lang/` is the main editable language folder
- `plugins/AegisGuard/codex/` is the fallback language layer

### Supported Language Packs

- `modern_english`
- `old_english`
- `spanish_mx`
- `spanish_ar`

For setup guidance and tuning recommendations:
- [CONFIGURATION.md](CONFIGURATION.md)

---

## Plugin Integrations

### Core Integrations

| Plugin | Purpose |
|--------|---------|
| **Vault** | Money economy support |
| **PlaceholderAPI** | Placeholder integration |
| **CoreProtect** | Logging compatibility |

### Map Integrations

| Plugin | Purpose |
|--------|---------|
| **Dynmap** | Claim overlays |
| **BlueMap** | Claim markers and labels |
| **Pl3xMap** | Claim visualization |

### Migration and Compatibility

| Plugin | Purpose |
|--------|---------|
| **GriefPrevention** | Claim import and migration |
| **GriefDefender** | Claim import and migration |
| **Lands** | Claim import and migration |
| **WorldGuard** | Region overlap and compatibility handling |
| **Residence** | Region detection support |
| **Towny** | Town-area awareness |

### Market Bridges

| Plugin | Purpose |
|--------|---------|
| **QuickShop** | Local Market bridge option |
| **Shopkeepers** | Local Market bridge option |
| **ChestShop** | Local Market bridge option |
| **ExcellentShop** | Local Market bridge option |

---

## Installation

1. Download AegisGuard from [Spigot](https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/), [Hangar](https://hangar.papermc.io/snazzyatoms/AegisGuard), or [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy).
2. Place the JAR in your server's `plugins/` folder.
3. Start the server once to generate the configuration files.
4. Review `plugins/AegisGuard/config.yml`.
5. Adjust language files under `plugins/AegisGuard/lang/` if needed.
6. Reload with `/agadmin reload` or restart the server.

### Optional Dependencies

- Vault with a supported economy plugin
- PlaceholderAPI
- Dynmap, BlueMap, or Pl3xMap
- QuickShop, Shopkeepers, ChestShop, or ExcellentShop if you want external market bridges

---

## Quick Links

| Resource | Link |
|----------|------|
| Wiki | [AegisGuard Wiki](https://github.com/snazzyatoms/AegisGuard/wiki) |
| Configuration Guide | [CONFIGURATION.md](CONFIGURATION.md) |
| Issues | [GitHub Issues](https://github.com/snazzyatoms/AegisGuard/issues) |
| Releases | [GitHub Releases](https://github.com/snazzyatoms/AegisGuard/releases) |
| Spigot | [SpigotMC](https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/) |
| Hangar | [PaperMC Hangar](https://hangar.papermc.io/snazzyatoms/AegisGuard) |
| CurseForge | [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy) |

---

<p align="center">
  <strong>AegisGuard v1.2.6</strong> | Made with care by <a href="https://github.com/snazzyatoms">snazzyatoms</a>
</p>
