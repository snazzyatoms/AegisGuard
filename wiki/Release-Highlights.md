# Release Highlights

What each public release added to AegisGuard, newest first. For the player-facing view of these systems, see the [Player Handbook](Player-Handbook); for raw detail, see the `AegisGuard 1.4.0 changelog.txt` shipped with the source.

> **Upgrade note:** Existing `1.2.7` and `1.3.x` data remain valid through automatic schema migration — see [Updating](Installation#updating-from-127-130-or-135-to-140).

---

## 1.4.5 — The Hearthstone Update *(in development)*

The next point release is about recovery and polish — "1.4.0 connected the servers; 1.4.5 protects what makes them home." Not yet shipped; scope may change before release.

- **Self-backups** — `/ag backup` lets plot owners snapshot their land (settings, roles, members, noticeboards, bounds) and roll back themselves or through staff approval — now with a paginated GUI and a clear at-your-own-risk warning. Cooldowns, per-plot caps, and optional Vault cost keep it honest.
- **Built-in block restores** — AegisGuard ships its own capture engine, so full block restores work on every platform — Folia included — with no extra plugins. WorldEdit/FAWE stays supported when a server has it (`snapshots.build_backup.engine: auto|native|worldedit`); containers and signs restore contents and text.
- **Parties** — `/ag party` forms open-ended groups with a live sidebar roster (health, leader star, offline members greyed, remote members labeled with their server). Parties survive restarts, the leader plants rally points members answer on their own call, and rosters sync across the network.
- **Network plot frequencies** — the `/ag chat` plot channel relays to members on other backends, completing the cross-server radio set; fails closed on single servers.
- **Arena polish** — live match HUD (wave counter, run clock, survivors) plus owner-tunable reward pots, MVP share, and XP from `arena.rewards:`.
- **Caravan polish** — return deliveries, a shipment ledger in the caravan GUI, and per-route pricing and cargo caps.

---

## 1.4.0 — The Territory Platform

The biggest release yet. It completes the travel layer, adds an economy loop on top of beacons, builds a real event space around arenas, and — the flagship addition — links multiple servers into one network.

- **Cross-server travel** — on BungeeCord/Velocity networks, `/ag hub`, `/ag lobby`, `/ag servers`, remote arena joins, shared discovery, network radios, roaming player data, shared rosters, and staff broadcasts turn linked backends into one AegisGuard network. See [the network section](#the-multi-server-network) below.
- **Travel Atlas** — Destinations, My Beacons, Arrival, and Caravans in one menu. `/ag beacon` opens My Beacons. `/ag arrival <classic|beacon>` sets listing landings; travelers may override when allowed. Beacon listings fail closed (`beacon_no_public_arrival`) if no public pad exists.
- **Arena Dungeons** — `/ag arena` browses, joins, and parties for PvE/PvP runs on bound plots: waves, boss finales, custom floors and schematics, staging hubs, matchmaking queues, and sealed runs, with a ready `lava_dungeon` preset. Steward and admin roles manage arenas.
- **Caravans** — `/ag caravan` charge-then-deliver shipments on public beacon hops, with insurance, route events, hireable escorts, and resume after restart.
- **Guardian Succession** — `/ag heir`, `/ag succession`, Stewardship GUI, transfer cooldown and rollback.
- **Quick-Claim** — `/ag quickclaim [radius]` claims a square around you through the existing claim pipeline; honors `max_claims_per_player`.
- **Restore-safe roles** — snapshot restore merges members/roles by default; `/ag roles lock|unlock|undo`.
- **Gatherings & Open House** — `/ag gathering` (alias `/ag openhouse`) hosts timed events on managed plots with optional visitor Guest Passes.
- **Plot chat frequencies** — `/ag chat` toggles the plot channel; alliance and group radios with leader naming; `/ag staff` for staff.
- **ClaimBlocks exchange** — `/ag blocks buy|sell` with rates, cooldowns, and sell locks; `/ag ledger` history; `/ag settlements` inbox.
- **Server sanctuary** — staff can toggle **Keep Health**, **Keep Hunger**, **Keep XP**, and **Keep Inventory** on a server/spawn plot in Claim Settings → Safety. Off by default. Player plots never get this. No hub-flight Safety flag.
- **Hearth rooms** — owners and server-plot stewards can seal public chat to a 3D subplot or the rest of the plot. Off by default. Staff with `aegis.admin.hearth` still hear every room. If Simple Voice Chat (`voicechat`) is installed, the same rooms become isolated voice groups.
- **Claim presets** — after claim, choose Home / Shop / Arena / Farm (or Spawn / Hub / Shop / Arena on server plots).
- **Staff seasons** — `/agadmin season` pins featured Atlas plots and Routes.
- **Flight** — Ascension/Horizon level 30, staff fly, or `/agadmin skill fly <player> [seconds]`.
- **Nine language packs** — every player-facing message translated; full `/ag help` coverage; console logs stay plain English.
- **Schema migration** — `config_schema` moves `1294` → `1316`; new keys auto-merge on upgrade with a backup.

### The multi-server network

The headline of 1.4.0 is that everything above can span a proxy-linked network: with `network.enabled` and one shared MySQL/MariaDB database, backend servers share discovery listings, radios, rosters, beacon links, arena catalogs, and player data — no proxy-side jar required. Every network piece is gated and fails closed. See [Integrations → BungeeCord / Velocity Network Setup](Integrations-and-Compatibility#bungeecord--velocity-network-setup) for configuration.

---

## 1.3.5 — Beacons, Backups, and the Soak Pass

- **Teleport Beacons** — linked pads on claims you manage; Safe Travel; optional public arrival for visit, market, and auction; configurable fees (`owner_choice`, `always`, or `off`). `/ag home` stays personal plot spawn.
- **Complete claim-data snapshots** — rollback restores guest passes, lockdown, alliance access, and noticeboards with owner, flags, members, and bounds.
- **Plot backups** — optional full-height WorldEdit/FAWE copies with atomic manifests, SHA-256 checksums, Folia-safe chunk tasks, and protected retention. Default **off**; Folia requires FAWE by default.
- **Automatic plot backups** — optional player-plot and server-zone snapshots in small batches. Default **off**.
- **Recovery operations** — preview/selective restore, maintenance locking, restart pause/retry, health reporting, and optional Discord failure warnings.
- **Bedrock / Geyser GUIs** — Floodgate and Geyser players are auto-detected (`gui.bedrock.detect`, default on). Java keeps right-click second actions; Bedrock uses left-click and sneak-left.
- **Language placeholders** — `{PLOT}`, `{MIN}`, `{PLAYER}`, and other `{KEY}` tokens now fill in on `lang/` translations, not only English fallbacks.
- **ClaimBlocks** — expanding land no longer drives the wallet negative; extra area is refused if it cannot be covered. Group claims check the leader's ClaimBlocks.
- **Minimum claim size** — `claims.min_radius` is enforced on both width and depth for wand claims, group claims, expansion, and Ascension growth.
- **1.3.0 soak fixes** — correct plot lookup after ownership transfer, Folia-safe restore, snapshot prune, role nicknames, player-menu footer, hidden Staff Tools modules, lockdown no longer auto-lifts on save, and schema migration no longer re-enables a legacy `*.enabled: false`.

---

## 1.3.0 — Staff Oversight and Player Guidance

Module switchboard, Staff Audit Ledger, Guest Passes, Emergency Lockdown, Realm Profiles, the first-claim walkthrough, Routes, Alliance Access, the language picker, server-zone stewardship, and the first cooperative-PvE arena module.

The 1.2.7 territory platform underneath remains: Ascension Hall, Expansion Horizons, rentals, Travel Atlas, ClaimBlocks, markets, TradeStalls, Territory Doctor, and recovery tools.

---

> *Simple. Steadfast. Eternal.*
