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

> Full notes live in [`RELEASE_NOTES_1.4.0.md`](RELEASE_NOTES_1.4.0.md). Download from the [1.4.0 GitHub Release](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.4.0).

## Highlights In 1.4.0

**Travel Atlas.** Visit is one travel menu: Destinations, My Beacons, Arrival, and Caravans. `/ag beacon` opens **My Beacons**. Plot managers choose **classic** vs **beacon** arrival (`/ag arrival`); travelers may override when the owner allows. Public listings fail closed (`beacon_no_public_arrival`) when beacon arrival has no public pad. `/ag home` remains personal plot spawn.

**Cross-server travel.** On BungeeCord/Velocity networks, `/ag hub`, `/ag lobby`, and `/ag servers` move players between backends through the cross-server event bus, with Safe Travel arrivals and an opt-in network broadcast channel for staff.

**Quick-Claim and clearer menus.** `/ag quickclaim [radius]` (and a Territory hub button) claims a square around you through the existing claim pipeline. Settings appears only on the main player hub and the staff menu; Back returns to wherever opened it. Claiming honors `max_claims_per_player`.

**Restore-safe roles.** Snapshot restore **merges** members and roles by default (`snapshots.restore.protect_roles`). Owners can lock members; `/ag roles lock|unlock|undo` records to the audit ledger.

**Guardian Succession.** Co-owner and steward grants auto-lock. `/ag heir`, `/ag succession assume|rollback|menu`, and a Stewardship page cover inactivity assume, transfer cooldown, and a short rollback window.

**Caravans & Trade Routes.** `/ag caravan` dispatches charge-then-deliver shipments along public beacon hops, with insurance, weighted route events, Folia-safe ticks, and resume after restart. Hire an online player as **escort** from the dispatch menu — escorts halve ambush risk for a configurable cut (`caravans.escorts_enabled`, `escort_cut_rate`).

**Gatherings & Open House.** `/ag gathering start [minutes]` (or Explore → Open House) lists a timed house on Travel Atlas Discover → **Live**. Arrivals can receive a visitor Guest Pass that expires when the house ends.

**Server sanctuary.** On a server or spawn plot, staff with manage/steward can turn on **Keep Health**, **Keep Hunger**, **Keep XP**, and **Keep Inventory** in Claim Settings → Safety. All start **off**; personal plots cannot use them.

**Flight.** Plot flight unlocks at Ascension / Horizon **level 30** for owner and trusted on that plot. Staff can fly when `flight_skill.staff_always` is on (`aegis.admin.fly`). Temporary grants use `/agadmin skill fly <player> [seconds]`.

**Aegis radios.** Plot members toggle Plot Frequency with `/ag chat` (or `/ag frequency`) — one channel per player, which pulls their messages out of global chat. `/ag chat alliance` and `/ag chat group` provide opt-in alliance and group radios with leader naming. Staff use `/ag staff` (`aegis.admin.staffchat`). Java and Bedrock players use the same commands; no client mod required.

**Hearth rooms.** Owners and server-plot stewards can enable **Hearth** in Safety, scoping public text chat to the 3D subplot a player stands in. With the optional Simple Voice Chat hook installed, Hearth rooms become isolated SVC groups — Folia-safe and fully optional.

**Claim presets and staff seasons.** A successful claim opens the preset chooser (Home / Shop / Arena / Farm, or Spawn / Hub / Shop / Arena on server plots). Presets never overwrite sanctuary flags, Hearth, or flight. `/agadmin season` pins featured plots on the Atlas and featured routes in the Routes browser.

**Hardened protection.** A **Mob Griefing** ward stops endermen, sheep, ravagers, zombie door-breaking, frost-walker trails, turtle-egg trample, and falling blocks. An **Interactables** ward gates cake, bells, jukeboxes, note blocks, candles, pots, composters, cauldrons, lecterns, chiseled bookshelves, campfires, beehives, and dragon eggs. Sponges and dispenser bonemeal can no longer alter blocks across a claim border, harmful potions are filtered, vehicle and end-crystal placement require build permission, and dispensers can no longer inject liquids into claims.

**Per-plot biomes.** `/ag biome` opens a localized picker over the server's allowed list, charges an optional Vault fee, and applies the biome in batched, region-safe passes.

**Staff inspection and grief alerts.** `/agadmin inspect` opens a detail card for the claim you stand in — or resolves a player's claims — with teleport, registry, and protected delete actions. `/agadmin plots <player>` filters the registry to one owner. Repeated denied actions raise rate-limited alerts to online staff (`protections.grief_alert_*`).

**Complete localization.** Every player-facing message ships in all nine packs — Modern English, Old English, Spanish (Mexico), Spanish (Argentina), Brazilian Portuguese, French, Italian, German, and Polish — with `{KEY}` placeholders resolved on translations and full `/ag help` coverage. Console logging stays plain English.

**Documented API.** The public `com.aegisguard.api` events and service interfaces carry Javadocs; `AegisGuard-1.4.0-api.jar` ships with each release as a compile-time dependency.

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
/ag arrival <classic|beacon> Choose how visitors arrive at your plot
/ag arena                    Browse, join, and party for arena runs
/ag caravan                  Dispatch and track trade caravans
/ag gathering                Host or browse open-house gatherings
/ag heir [player|clear]      Name a succession heir
/ag chat                     Toggle plot chat (alliance and group radios available)
/ag staff                    Toggle staff chat
/ag level                    Open the Ascension Hall
/ag zone                     Manage sub-zones and rentals
/ag blocks buy|sell          Trade ClaimBlocks through the exchange
/ag ledger                   View your ClaimBlock ledger
/ag biome                    Pick a custom biome for the plot you manage
/ag guide                    Replay the first-claim walkthrough

/agadmin menu                Open the Staff Command Center
/agadmin wand server         Get the server-zone wand
/agadmin claim               Create a server-owned protected zone
/agadmin inspect [player]    Inspect the claim here, or a player's claims
/agadmin plots [player]      Registry of every claim, or one owner's
/agadmin season              Staff season featured Atlas plots and routes
/agadmin skill fly <player>  Grant a temporary flight skill
/agadmin staffchat           Toggle staff radio (alias /agadmin sc)
/agadmin network             Cross-server find and broadcast tools
/agadmin transition          Confirm upgrade status from 1.2.7 or 1.3.x
/agadmin doctor              Optional diagnostics and repair tools
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
| [`wiki/`](wiki/) | Pages to paste into the GitHub Wiki |
| [`listing/`](listing/) | Spigot listing art |
| [`RELEASE_NOTES_1.4.0.md`](RELEASE_NOTES_1.4.0.md) | 1.4.0 release notes |
| [`aegisguard-modern/UPCOMING.md`](aegisguard-modern/UPCOMING.md) | Roadmap beyond the current line |

## For Developers

`AegisGuard-1.4.0-api.jar` ships with each [release](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.4.0) as a compile-time dependency — it does not belong in a server's `plugins` folder. Public events and service accessors live under `com.aegisguard.api` with Javadocs.

---

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong><br />
  Forged by Aegis Divine.
</p>
