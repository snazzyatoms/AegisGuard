<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<h1 align="center">AegisGuard 1.3.0</h1>

<p align="center">
  <strong>Protect your world. Empower your players. Ascend.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.3.0-d9a441?style=for-the-badge" alt="Version 1.3.0" />
  <img src="https://img.shields.io/badge/Minecraft-1.20%2B-56a64b?style=for-the-badge" alt="Minecraft 1.20 or newer" />
  <img src="https://img.shields.io/badge/Java-21%2B-e76f00?style=for-the-badge" alt="Java 21 or newer" />
  <img src="https://img.shields.io/badge/Folia-Supported-2d7ff9?style=for-the-badge" alt="Folia supported" />
</p>

<p align="center">
  <a href="https://github.com/snazzyatoms/AegisGuard/wiki">Wiki</a> &bull;
  <a href="https://github.com/snazzyatoms/AegisGuard/issues">Issues</a> &bull;
  <a href="https://github.com/snazzyatoms/AegisGuard/releases">Releases</a>
</p>

---

## A Living Territory System

**AegisGuard** turns land protection into a complete territory experience. Players can secure land, develop their plots, welcome communities, operate local markets, rent spaces, discover destinations, and pursue meaningful long-term progression. Staff receive the recovery, migration, diagnostics, and world-management tools needed to run the system confidently on public servers.

Version `1.3.0` builds on the established progression, economy, administration, and interface with safer staff operations and new territory experiences. Existing `1.2.7` data and configuration remain valid.

## What Is New In 1.3.0

### Staff Audit Ledger

Sensitive administrative actions are recorded in a structured audit trail with category filtering for restore, repair, migration, bypass, Guest Pass, Lockdown, and Alliance activity.

### Temporary Guest Passes

Issue time-limited, self-expiring plot access without granting permanent trust. Presets cover visitor, event guest, temporary builder, and temporary trusted guest. Expiry and revoke never rewrite permanent roles.

### Emergency Plot Lockdown

A fast, reversible safety switch for griefing, disputes, or maintenance. Lockdown restricts sensitive actions while remaining easy to lift from the plot menu.

### Realm Profiles and Noticeboards

Give each plot a public identity: display name, category, greeting, description, and a noticeboard visitors can read from travel and discovery experiences.

### Clearer Player Guidance

Blocked-action messages explain the next useful step. An optional first-claim walkthrough is skippable, never blocks claiming, and can be replayed from Settings or `/ag guide`.

### Routes and Checkpoints

Staff can publish named exploration routes with ordered checkpoints. Players browse progress, discover checkpoints by proximity, and may receive optional completion rewards. Optional teleport defaults **OFF**.

### Alliance Access

Player alliances are completely separate from ownership, money, rentals, and administration. Membership alone grants nothing. Each plot must opt into six Alliance Access toggles, all default **OFF**: Enter, Interact, Containers, Build, Animals, and Friendly PvP. Alliance Entry and Friendly PvP are wired into plot protection.

## Core Systems

| System | Capabilities |
|---|---|
| Protection | Claims, server zones, sub-zones, interactions, containers, entities, vehicles, hostile mobs, lockdown, and boundary enforcement |
| Progression | Plot Ascension, utility disciplines, Frontier Expansion, Expansion Horizons, Renown, and Sigils |
| Economy | ClaimBlocks, Vault exchange, real-estate listings, auctions, local markets, TradeStalls, and rentals |
| Community | Roles, trusted members, Guest Passes, Alliance Access, group plots, shared treasury, Realm Profiles, discovery, likes, favorites, travel, and routes |
| Administration | Doctor tools, snapshots, restoration, migration, Audit Ledger, diagnostics, world controls, bypass tools, and activity history |
| Presentation | Modern English, Old English, Mexican Spanish, and Argentinian Spanish |

## Compatibility

| Requirement | Support |
|---|---|
| Minecraft | `1.20+` |
| Java | `21+` |
| Platforms | Spigot, Paper, Purpur, Folia, and compatible Bukkit server forks |
| Economy | Vault with a supported economy provider |
| Maps | Dynmap, BlueMap, and Pl3xMap integration paths |
| Extensions | PlaceholderAPI and the public AegisGuard API |

Server implementations evolve independently. Test new Minecraft server releases in a staging environment before updating a public server.

## Installation

1. Stop the Minecraft server.
2. Place `AegisGuard-1.3.0.jar` in the server's `plugins` directory.
3. Install Vault and an economy provider if money-based features are required.
4. Start the server and allow AegisGuard to generate its editable files.
5. Review `plugins/AegisGuard/config.yml` and the files under `plugins/AegisGuard/lang/`.
6. Run `/agadmin doctor` before opening the server to players.

Do not use Bukkit's global `/reload` command. Use `/agadmin reload` for supported AegisGuard configuration and language reloads, and perform a full restart after changing integrations or storage settings.

## Quick Start

```text
/ag wand                     Get the Aegis Scepter
/ag claim                    Create a claim from the current selection
/ag menu                     Open the territory dashboard
/ag level                    Open the Ascension Hall
/ag market local             Open the Local Market
/ag visit                    Open the Travel Atlas
/ag zone                     Manage sub-zones and rentals

/agadmin menu                Open the Staff Command Center
/agadmin wand server         Get the server-zone wand
/agadmin claim               Create a server-owned protected zone
/agadmin doctor              Run diagnostics and repair tools
/agadmin reload              Reload editable settings and languages
```

See the [Wiki](https://github.com/snazzyatoms/AegisGuard/wiki) for detailed commands, permissions, configuration, economy, migration, and player guides.


The server plugin and developer API artifacts are copied into the local `releases/` directory. Install only `AegisGuard-1.3.0.jar` on a Minecraft server; API artifacts are intended for developers.

## Release Confidence

The release workflow performs clean Java compilation, automated tests, language parity checks, YAML validation, permission metadata checks, navigation contracts, storage contracts, Folia safety checks, and artifact verification. A local smoke-test script is also provided for startup, admin reload, shutdown, and exception scanning against server fixtures.

---

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong><br />
  Forged by Aegis Divine.
</p>
