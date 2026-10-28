<p align="center">
  <img width="100%" alt="AegisGuard" src="listing/aegisguard-spigot-header-v2.png" />
</p>

<h1 align="center">AegisGuard 1.4.0</h1>

<p align="center">
  <strong>Protect your world. Empower your players. Ascend.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.4.0-d9a441?style=for-the-badge" alt="Version 1.4.0" />
  <img src="https://img.shields.io/badge/Minecraft-1.20%2B-56a64b?style=for-the-badge" alt="Minecraft 1.20 or newer" />
  <img src="https://img.shields.io/badge/Java-21%2B-e76f00?style=for-the-badge" alt="Java 21 or newer" />
  <img src="https://img.shields.io/badge/Folia-Supported-2d7ff9?style=for-the-badge" alt="Folia supported" />
  <a href="https://discord.gg/nHwdhKzeKR"><img src="https://img.shields.io/badge/Discord-Join-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join Discord" /></a>
  <a href="https://github.com/snazzyatoms/AegisGuard"><img src="https://img.shields.io/badge/OPEN_SOURCE-GITHUB-24292f?style=for-the-badge&logo=github&logoColor=white" alt="Open Source on GitHub" /></a>
</p>

<p align="center">
  <a href="https://github.com/snazzyatoms/AegisGuard/wiki">Wiki</a> &bull;
  <a href="https://discord.gg/nHwdhKzeKR">Discord</a> &bull;
  <a href="https://github.com/snazzyatoms/AegisGuard/issues">Issues</a> &bull;
  <a href="https://github.com/snazzyatoms/AegisGuard/releases">Releases</a>
</p>

---

## A Living Territory System

**AegisGuard** is a Minecraft land-protection plugin that turns claims into a full territory experience. Players secure land, develop plots, manage roles and rentals, run local markets, host events, and pursue long-term progression. Staff get recovery snapshots, migration tools, diagnostics, world controls, and an audit trail for high-impact actions.

It runs on **Paper, Purpur, Spigot, and Folia** with **Java 21+**. **1.4.0** is the current public release. Existing `1.2.7` and `1.3.x` data and configuration remain valid through automatic schema migration.

> Full notes and downloads live on the [1.4.0 GitHub Release](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.4.0) page.

> **Next up — 1.4.5 "The Hearthstone Update"** *(in development)*: player self-backups (`/ag backup`), Hearth voice on Plasmo Voice, arena and caravan polish, and plot channels that relay across the network — finishing the last radio room. See [`UPCOMING.md`](aegisguard-modern/UPCOMING.md) for the roadmap and `AegisGuard 1.4.5 changelog.txt` for draft release notes.

## Highlights In 1.4.0

**Travel Atlas.** One travel menu replaces the scattered ones: Destinations, My Beacons, Arrival, and Caravans in a single interface. Plot managers pick **classic** or **beacon** visitor arrival (`/ag arrival`), travelers can override where allowed, and a listing that requires a public pad fails closed rather than dropping visitors somewhere unexpected.

**Caravans & Trade Routes.** The beacon network becomes an economy. `/ag caravan` dispatches charge-then-deliver shipments along public pads — stake and fee up front, payout on arrival — with insurance, route events, and hireable player escorts who halve ambush risk for a cut. Shipments resume cleanly after restarts, even on Folia.

**Gatherings & Open House.** `/ag gathering` (or Explore → Open House) lists a timed house on the Atlas's **Live** tab; visitor Guest Passes expire when the event ends, so events run themselves.

**Aegis radios.** Opt-in text channels — plot Frequency (`/ag chat`), alliance, group, and staff (`/ag staff`) — that pull tuned players' messages out of public chat. One channel at a time, no client mod, identical commands on Java and Bedrock.

**Hearth rooms.** Seal public chat to the room you're standing in — a house, pit, or lobby subplot. With the optional Simple Voice Chat plugin present, the same rooms become isolated voice groups.

**Quick-Claim and clearer menus.** `/ag quickclaim [radius]` claims a square around you through the normal pipeline (limits still apply). Settings lives on the main hubs only; Back always returns you to where you came from.

**Continuity that outlives players.** Snapshot restores **merge** members and roles instead of wiping current access decisions, owners can lock members (`/ag roles lock|unlock|undo`), and **Guardian Succession** (`/ag heir`, `/ag succession`) hands a plot to an heir with inactivity assume, transfer cooldowns, and a rollback window — a community plot doesn't die with its founder.

**Server sanctuary.** Spawn and other server plots can opt into Keep Health, Hunger, XP, and Inventory — all off by default, never available on personal claims.

**Flight as progression.** Plot flight unlocks at Ascension/Horizon level 30 for the owner and trusted — a reward, not a toggle. Staff can grant temporary flight (`/agadmin skill fly`).

**Presets, seasons, and polish.** Claim presets (Home/Shop/Arena/Farm) speed setup without overwriting safety choices; `/agadmin season` pins featured plots and routes; `/ag biome` restyles a plot with an optional Vault fee.

**Hardened protection.** New **Mob Griefing** and **Interactables** wards close real grief vectors — endermen, falling blocks, bells, lecterns, beehives, and more — and cross-border sponge, dispenser, and potion tricks no longer leak into claims.

**Staff inspection and grief alerts.** `/agadmin inspect` opens a detail card for the claim under you; repeated denied actions raise rate-limited alerts to online staff.

**Complete localization.** Every player-facing message ships in all nine packs — Modern English, Old English, Spanish (Mexico), Spanish (Argentina), Brazilian Portuguese, French, Italian, German, and Polish — with `{KEY}` placeholders resolved on translations. Console logs stay plain English.

**Documented API.** Public `com.aegisguard.api` events and services carry Javadocs; `AegisGuard-1.4.0-api.jar` ships with each release as a compile-time dependency.

**The flagship — one network of servers.** Everything above scales across a proxy. With `network.enabled` and one shared MySQL/MariaDB database, backend servers behind BungeeCord/Velocity become a single AegisGuard ecosystem — no proxy jar required: the Atlas lists remote plots, radios relay network-wide, players carry ClaimBlocks and preferences between backends, alliances and groups merge, beacons link across servers, and `/ag arena join <server>:<arena>` drops a player into a run on another backend and routes them home when it ends. Proven on a live Velocity + two-Paper + MariaDB rig before release — real remote arena hops, sealed-run boundaries, and Vault caravan payouts that sum exactly to cargo. `/ag hub`, `/ag lobby`, `/ag servers`, `/agadmin network`.

## Core Systems

| System | Capabilities |
|---|---|
| Protection | Claims, server zones, sub-zones, interactions, containers, entities, vehicles, hostile-mob controls, lockdown, hopper/liquid/teleport/storm/mob-griefing/interactables wards, potion and entity-placement filtering, and boundary enforcement |
| Progression | Plot Ascension, utility disciplines, Frontier Expansion, Expansion Horizons, Renown, and Sigils |
| Economy | ClaimBlocks with buy/sell exchange and ledger, Vault support, real-estate listings, auctions, local markets, TradeStalls, GiftBlocks, and rentals |
| Community | Roles, locked members, Guest Passes, Alliance Access, group plots, Realm Profiles, noticeboards, Safe Travel, Teleport Beacons, Travel Atlas, Guardian Succession, trade caravans, open-house gatherings, Aegis Frequency radios, Hearth text-chat rooms, and an optional Simple Voice Chat hook |
| Arenas | Optional party dungeons on bound server plots — PvE waves, boss finales, opt-in PvP, custom floors and schematics, staging hubs, matchmaking queues, sealed runs, and Folia-safe scheduling |
| Administration | Doctor tools, recovery snapshots, restoration, migration, Audit Ledger, `/agadmin health`, `/agadmin inspect`, grief-attempt alerts, staff seasons and flight skills, diagnostics, world controls, bypass tools, convert-to-server, and activity history |
| Modularity | `modules:` switchboard — optional systems default **on** except wilderness revert (**off**, SQL-only); menus hide disabled modules |
| Localization | Direct language picker across nine packs with synced Codex fallbacks; console output stays plain English |

## Compatibility

| Requirement | Support |
|---|---|
| Minecraft | `1.20+` |
| Java | `21+` |
| Platforms | Spigot, Paper, Purpur, Folia, and compatible Bukkit server forks |
| Economy | Vault with a supported economy provider |
| Networks | BungeeCord or Velocity for cross-server travel, remote arenas, and broadcasts |
| Maps | Dynmap, BlueMap, and Pl3xMap integration paths |
| Extensions | PlaceholderAPI, the public AegisGuard API, optional WorldEdit/FAWE plot backups, and an optional Simple Voice Chat hook for Hearth rooms |
| Upgrade path | From AegisGuard `1.2.7` or `1.3.x` with automatic config schema migration (`config_schema` → `1316`) |

Server implementations evolve independently. Test new Minecraft server releases in a staging environment before updating a public server.

## Installation

1. Stop the Minecraft server.
2. Confirm the host is running **Java 21 or newer**.
3. Place `AegisGuard-1.4.0.jar` in the server's `plugins` directory.
4. Install Vault and an economy provider if money-based features are required.
5. Start the server. On a JAR swap from 1.2.7 or 1.3.x, config and language merge run automatically and existing plots load as-is.
6. Review `plugins/AegisGuard/config.yml` and the files under `plugins/AegisGuard/lang/`.
7. Confirm status with `/agadmin transition` (aliases `upgrade`, `v130`, `v140`). A folder backup of `plugins/AegisGuard/` is recommended, not required to keep claims.
8. Run `/agadmin doctor` only if something looks wrong.

Do not use Bukkit's global `/reload` command. Use `/agadmin reload` for supported AegisGuard configuration and language reloads, and perform a full restart after changing integrations or storage settings.

## Quick Start

```text
/ag wand                     Get the Aegis Scepter
/ag claim                    Create a claim from the current selection
/ag quickclaim [radius]      Claim a square around you, no wand needed
/ag menu                     Open the territory dashboard
/ag travel                   Open the Travel Hub
/ag visit                    Open the Travel Atlas
/ag beacon                   Manage Teleport Beacons
/ag arena                    Browse, join, and party for arena runs
/ag caravan                  Dispatch and track trade caravans
/ag gathering                Host or browse open-house gatherings
/ag chat                     Toggle plot chat (alliance and group radios available)
/ag blocks buy|sell          Trade ClaimBlocks through the exchange
/ag guide                    Replay the first-claim walkthrough

/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm upgrade status from 1.2.7 or 1.3.x
/agadmin reload              Reload editable settings and languages
```

See the [Wiki](https://github.com/snazzyatoms/AegisGuard/wiki) for detailed commands, permissions, configuration, economy, migration, and player guides.

## Repository Layout

| Place | What it is |
|---|---|
| [`V1.4.0`](https://github.com/snazzyatoms/AegisGuard/tree/V1.4.0) | Current release line — GitHub Release **1.4.0** |
| [`V1.3.5`](https://github.com/snazzyatoms/AegisGuard/tree/V1.3.5) | Previous public line — GitHub Release **1.3.5** |
| [`V1.3.0`](https://github.com/snazzyatoms/AegisGuard/tree/V1.3.0) | Earlier public 1.3.0 line |
| [`aegisguard-modern/`](aegisguard-modern/) | Plugin source (Maven, Java 21) |
| [`examples/api-addon/`](examples/api-addon/) | Example plugin that compiles against the public API |
| [`wiki/`](wiki/) | Pages to paste into the GitHub Wiki |
| [`listing/`](listing/) | Spigot/CurseForge listing art |
| [`media/`](media/) | Showcase GIFs hotlinked by the listing pages |
| [`aegisguard-modern/UPCOMING.md`](aegisguard-modern/UPCOMING.md) | Roadmap beyond the current line |

## For Developers

`AegisGuard-1.4.0-api.jar` ships with each [release](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.4.0) as a compile-time dependency — it does not belong in a server's `plugins` folder. Public events and service accessors live under `com.aegisguard.api` with Javadocs.

---

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong><br />
  Forged by Aegis Divine.
</p>
