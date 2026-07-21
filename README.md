<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<h1 align="center">AegisGuard 1.2.7</h1>

<p align="center">
  <strong>Protect your world. Empower your players. Ascend.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.2.7-d9a441?style=for-the-badge" alt="Version 1.2.7" />
  <img src="https://img.shields.io/badge/Minecraft-1.20%2B-56a64b?style=for-the-badge" alt="Minecraft 1.20 or newer" />
  <img src="https://img.shields.io/badge/Java-17%2B-e76f00?style=for-the-badge" alt="Java 17 or newer" />
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

Version `1.2.7` brings the plugin's progression, economy, administration, and interface together into its most cohesive release yet.

## What Is New In 1.2.7

### Ascension Hall

Plot progression now has a complete identity. Thirty levels are presented through six visual chapters, a Guardian's Ascension Guide, clear reward previews, exact payment confirmation, and a safe level-up ceremony.

Plots may specialize in one balanced utility discipline:

- **Stonewright** supports builders and large construction projects.
- **Verdant Keeper** supports cultivated land and protected farmland.
- **Wayfinder** supports movement and exploration inside developed territory.

External potion effects and existing flight permissions are preserved safely. Plot flight is earned through the final Ascension milestone rather than exposed as a manual claim toggle.

### Expansion Horizons

Level 30 opens a slower, long-term territory journey built around Renown, five named ranks, bound Sigils, guarded radius growth, and advanced plot abilities. Every expansion continues to use overlap checks, world limits, snapshots, and ownership validation.

### Rental Contracts 2.0

Plots and sub-zones can support structured rental activity with fixed terms, deposits, renewals, reminders, tenant controls, hotel-style guest access, and durable offline settlement handling.

### Discovery And Territory Life

Public territories can be discovered through categories, favorites, featured listings, visit tracking, likes, and activity history. The redesigned Travel Atlas provides direct access to server waypoints, owned plots, trusted destinations, discovery, and favorites.

### Professional Territory Interfaces

The major management screens now share clearer navigation, stronger visual hierarchy, and complete guidance:

- Frontier Expansion Hall
- Local Market district hub
- Zone planning and rental management
- Roles, capacity, and permission controls
- Claim protection settings
- ClaimBlock Exchange guidance
- Ascension Hall and Guardian guide
- Staff Command Center and World Controls

Back and exit controls remain consistent, actions are routed safely, and all supported language packs share matching keys and placeholders.

### Administration And Recovery

Server owners receive a stronger operational toolkit:

- `/agadmin doctor` diagnostics and confirmation-gated repairs
- snapshot creation and restoration
- automatic configuration migration with safety backups
- migration previews for supported protection plugins
- territory activity records and admin inspection
- per-world claim and protection defaults
- live controls for mob spawning, daylight, weather, keep-inventory, and mob griefing

## Core Systems

| System | Capabilities |
|---|---|
| Protection | Claims, server zones, sub-zones, interactions, containers, entities, vehicles, hostile mobs, and boundary enforcement |
| Progression | Plot Ascension, utility disciplines, Frontier Expansion, Expansion Horizons, Renown, and Sigils |
| Economy | ClaimBlocks, Vault exchange, real-estate listings, auctions, local markets, TradeStalls, and rentals |
| Community | Roles, trusted members, group plots, shared treasury, greetings, discovery, likes, favorites, and travel |
| Administration | Doctor tools, snapshots, restoration, migration, diagnostics, world controls, bypass tools, and activity history |
| Presentation | Modern English, Old English, Mexican Spanish, and Argentinian Spanish |

## Compatibility

| Requirement | Support |
|---|---|
| Minecraft | `1.20+` |
| Java | `17+` plugin bytecode; newer server-required Java runtimes are supported |
| Platforms | Spigot, Paper, Purpur, Folia, and compatible Bukkit server forks |
| Economy | Vault with a supported economy provider |
| Maps | Dynmap, BlueMap, and Pl3xMap integration paths |
| Extensions | PlaceholderAPI and the public AegisGuard API |

Server implementations evolve independently. Test new Minecraft server releases in a staging environment before updating a public server.

## Installation

1. Stop the Minecraft server.
2. Place `AegisGuard-1.2.7.jar` in the server's `plugins` directory.
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

## Building From Source

Requirements:

- JDK 17 or newer
- Maven 3.9 or newer

From the repository root:

```text
mvn clean package
```

The server plugin and developer API artifacts are copied into the local `releases/` directory. Install only `AegisGuard-1.2.7.jar` on a Minecraft server; API artifacts are intended for developers.

## Release Confidence

The release workflow performs clean Java compilation, automated tests, language parity checks, YAML validation, permission metadata checks, navigation contracts, storage contracts, Folia safety checks, and artifact verification. A local smoke-test script is also provided for startup, admin reload, shutdown, and exception scanning against server fixtures.

---

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong><br />
  Forged by Aegis Divine.
</p>
