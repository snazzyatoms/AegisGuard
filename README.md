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

AegisGuard is a land protection, progression, market, rental, and admin recovery plugin for Minecraft servers running **Spigot**, **Paper**, **Purpur**, or **Folia** on **1.20+**.

Version 1.2.6 is a major polish and expansion pass. It improves protection coverage, GUI flow, notifications, group ownership, subplot/rental management, migration tools, diagnostics, and player-facing market systems while keeping the plugin approachable for survival and SMP servers.

---

## Table of Contents

- [Server Compatibility](#server-compatibility)
- [What Is New In 1.2.6](#what-is-new-in-126)
- [Core Features](#core-features)
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

### Protection, Stability, and Recovery

- Improved protection coverage for claims, zones, decorative entities, vehicles, and interaction paths
- Better movement-event handling and safer scheduler usage in hot paths
- Stronger snapshot and recovery flow for expansions, rollbacks, and admin maintenance
- Cleaner server-zone and elevated staff access without relying on bypass for routine management
- Better diagnostics with `/agadmin doctor` support and report generation

### Groups, Social Flow, and Notifications

- Separate player greeting notifications from admin notifications
- Expanded notification system for group events, treasury changes, upkeep warnings, plot rename events, and review activity
- Group-first ownership flow with treasury support before claiming a shared plot
- Safer group starter rules with anti-abuse protections around member count and early removal

### Expansions, Migration, and Admin Review

- Stronger expansion request review flow with improved audit history
- Optional unattended auto-approval when no reviewers are online
- Migration wand and migration admin GUI for supported protection-plugin imports
- Focused-claim migration preview and improved metadata preservation during import

### Markets, Rentals, and Subplots

- Improved subplot and rentable-zone protections so renters can use their space more like a real mini-claim
- Renter room controls, guest access, room spawn management, and hotel-style behavior
- Local Market flow for plot-level selling and rental experiences
- Built-in **TradeStalls** system with chest/sign storefront registration and browse GUI
- Configurable coexistence with external shop plugins such as QuickShop, Shopkeepers, ChestShop, and ExcellentShop

### GUI, Language, and Documentation

- Synchronized GUI listener and menu handling across the plugin
- Cleaner back/exit behavior across menus
- Updated titles and lore text for a more readable, less cluttered interface
- `lang` kept as the main editable language folder, `codex` retained as fallback
- Hybrid English removed; supported packs are now `modern_english`, `old_english`, `spanish_mx`, and `spanish_ar`

---

## Core Features

### Claiming and Land Protection

- Wand-based selection and claiming
- Configurable minimum and maximum claim size rules
- Per-world rules and limits
- Claim resize, merge, unclaim, and safe admin recovery
- Visual claim boundaries and guided management menus
- Plot flags for PvP, containers, redstone, entry, animals, vehicles, shops, and more

### ClaimBlocks Economy

- Configurable starting balance for new players
- Passive ClaimBlock earnings with anti-AFK protections
- Per-player earnings opt-in or opt-out support
- ClaimBlocks exchange with Vault integration
- Costs, fees, sell locks, cooldowns, hourly limits, and server presets

### Groups and Shared Ownership

- Group creation before shared claiming
- Group treasury support for expansions and progression
- Group-aware starter claim sizing rules
- Member-aware notifications and better shared ownership flow

### Expansions and Progression

- Expansion request queue or instant mode
- Optional unattended approval mode when reviewers are offline
- Plot leveling with rewards, progression unlocks, and optional territory growth
- Expansion snapshots and audit history for safer admin review

### Subplots, Rentals, and Rooms

- Zone creation inside existing plots
- Rentable rooms, stalls, and sub-areas
- Renter self-management with guest access and room spawn support
- Landlord oversight and eviction flow
- Market-style or hotel-style setups for shared claims

### TradeStalls and Local Market

- Built-in stall system for servers that do not want a third-party market plugin
- Sign + chest or barrel registration into a TradeStall
- Local Market GUI for plot-based shopping and rental browsing
- Supports money or ClaimBlocks as the trade currency
- Configurable plugin bridge behavior when external shop plugins are installed

### Migration, Diagnostics, and Recovery

- Migration wizard for supported protection sources
- Migration wand for claim detection and visual preview
- Snapshot browser and rollback tools
- `/agadmin doctor` reporting for support and troubleshooting
- Better admin recovery workflow after expansion, plot issues, or maintenance mistakes

---

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/aegis` | Main command |
| `/aegis menu` | Open the main GUI |
| `/aegis wand` | Receive the claiming wand |
| `/aegis claim` | Claim selected land |
| `/aegis unclaim` | Remove your plot |
| `/aegis resize` | Resize an existing plot |
| `/aegis merge` | Merge adjacent plots |
| `/aegis cost` | Check claim cost |
| `/aegis home` | Teleport to plot spawn |
| `/aegis setspawn` | Set plot spawn point |
| `/aegis welcome <message>` | Set plot welcome text |
| `/aegis farewell <message>` | Set plot farewell text |
| `/aegis notify` | Manage greetings/admin notification preferences |
| `/aegis visit` | Open the travel menu |
| `/aegis market` | Open the market menu |
| `/aegis market local` | Open the local market for the current area |
| `/aegis sell <price>` | List a plot for sale |
| `/aegis unsell` | Remove a plot from sale |
| `/aegis auction` | Open the auction browser |
| `/aegis zone` | Manage zones and rentals |
| `/aegis level` | Open plot leveling |
| `/aegis rename <name>` | Set plot display name |
| `/aegis setdesc <description>` | Set plot description |
| `/aegis blocks` | View ClaimBlock balance and related options |
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
| `/aegisadmin doctor` | Generate a diagnostics report |
| `/aegisadmin migrate <plugin>` | Start migration from a supported plugin |
| `/aegisadmin snapshot here [reason]` | Create a manual recovery snapshot |
| `/aegisadmin restore here` | Restore the latest snapshot for the current plot |
| `/aegisadmin blocks <player> <add|remove|set> <amount>` | Manage player ClaimBlocks |

---

## Permissions

Player-facing permissions are normally provided through `aegis.user`, and staff/admin permissions through `aegis.admin`.

Important nodes in 1.2.6 include:

- `aegis.user`
- `aegis.admin`
- `aegis.admin.manage`
- `aegis.serverzone.manage`
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

AegisGuard uses a structured `config.yml` with clearer section ordering in 1.2.6.

Notable configuration areas include:

- storage backend and persistence
- economy and ClaimBlocks settings
- group plot and treasury rules
- expansion approval and unattended review behavior
- market, TradeStalls, and local market bridge settings
- upkeep and tax notifications
- staff/server-zone access rules
- localization folder and fallback behavior

### Language Folders

- `plugins/AegisGuard/lang/` is the primary editable folder for server owners
- `plugins/AegisGuard/codex/` is the fallback bundle layer

### Supported Language Packs

- `modern_english`
- `old_english`
- `spanish_mx`
- `spanish_ar`

For setup guidance, tuning recommendations, and deployment notes:
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
