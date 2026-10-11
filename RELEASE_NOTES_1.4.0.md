# AegisGuard 1.4.0

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.4.0` builds on the public `1.3.5` release. It finishes the Travel Atlas, adds Quick-Claim and restore-safe roles, ships Guardian Succession, introduces Caravans & Trade Routes, and lets staff opt a server/spawn plot into Keep Health / Keep Hunger sanctuary.

Existing **1.2.7, 1.3.0, and 1.3.5 data remain valid**. `config_schema` moves from `1294` to `1310`; migration auto-merges new keys with a timestamped backup. Existing plots default to **classic** arrival, so enabling 1.4.0 never suddenly gates old servers on pads.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

This document describes the `V1.4.0` source line. It is **not** a GitHub Release.

---

## What's new

### Travel Hub, Atlas & Beacons

`/ag travel` opens one hub with the **Travel Atlas** (Warps, My Plots, Trusted, Discover, Favorites, Recent), a standalone **Beacons** menu (paginated pad list plus **Arrival Rules**), standalone **Routes** and **Caravans** browsers, and **Discover** — which gained separate filter, category, and sort controls plus live-event and market badges on entries. `/ag beacon` opens your pad list directly. Plot managers still choose how public listings land:

- **classic** — Safe Travel to the plot spawn / listing point (1.3.0 style), even when pads exist.
- **beacon** — visitors **must** land on a public arrival pad. If none exists, the trip **fails closed** (`beacon_no_public_arrival`).

Set the mode with `/ag arrival <classic|beacon>`. Travelers may override when the owner permits. `/ag home` stays personal plot spawn. Beacons stay one pad per block and one directed A→B link. `teleport_beacons.force_public_arrival` can require pad arrival network-wide.

### Quick-Claim and menu navigation

`/ag quickclaim [radius]` (alias `/ag qc`) and a Territory hub button claim a square around you through the existing claim pipeline. Settings appears only on the main player hub and the staff menu; Back returns to the screen that opened it. Claiming honors `max_claims_per_player`.

### Restore-safe roles

Snapshot restore **merges** members and roles by default (`snapshots.restore.protect_roles: true`). Owners can lock members so restore and role edits cannot silently drop them. `/ag roles lock|unlock|undo` writes to the audit ledger.

### Guardian Succession

Granting `co_owner` or `steward` auto-locks that member. `/ag heir`, `/ag succession assume|rollback|menu`, and the Access-page Stewardship GUI cover inactivity assume, transfer cooldown, and a short rollback window.

### Caravans & Trade Routes

`/ag caravan` dispatches charge-then-deliver shipments along public beacon hops. Insurance, weighted route events (safe, ambush, toll, boon, delay), Folia-safe ticks, YAML+SQL persistence, and resume-on-load are included. Gate with `modules.caravans`.

### Server sanctuary

Staff who can manage a **server plot** (spawn, hub, and other staff-owned land) can turn on **Keep Health**, **Keep Hunger**, **Keep XP**, and **Keep Inventory** in Claim Settings → Safety. Players standing in that plot then do not lose hearts or food, and deaths there can keep levels and items. Eating can still fill the hunger bar. Void and `/kill` still apply. All four toggles start **off**. Personal player claims never get them. There is **no hub-flight Safety flag**. Plot flight still unlocks at Ascension / Horizon level 30. Staff can fly when `flight_skill.staff_always` is on, or grant a temporary flight skill with `/agadmin skill fly <player> [seconds]`.

### Claim presets and staff seasons

After a successful claim, the main preset chooser opens (Home / Shop / Arena / Farm, or Spawn / Hub / Shop / Arena on server plots). Presets never overwrite sanctuary flags or Hearth. Staff can run a season overlay: `/agadmin season` pins featured plots on Atlas Discover and featured routes in the Routes browser.

### Hearth rooms

Plot owners and server-plot stewards can turn on **Hearth** in Claim Settings → Safety. Public chat then stays in the room you are standing in. A room is a 3D subplot (`/ag subplot`) — the house, the pit, the lobby — or the rest of that plot if you are not in a subplot. People outside cannot hear inside, and the room cannot hear the street. That is how a closed house and an arena safe-zone stay quiet without scanning doors. Staff with `aegis.admin.hearth` still hear every text room and are not muffled when they speak. If Simple Voice Chat is installed, the same rooms become isolated voice groups. The voice hook is Folia-safe (SVC threads hop to the global/entity scheduler; room changes are coalesced; reload resyncs groups). Opt-in Aegis radios sit beside Hearth: plot Frequency (`/ag chat`), alliance radio (`/ag chat alliance`), group chat (`/ag chat group`), and staff chat (`/ag staff` / `/agadmin staffchat`). Tuning one channel pulls you off public chat. Hearth starts **off**.

### Alliance, group, and staff chat

Vanilla multiplayer is still text-only (`T`). These Aegis channels are opt-in radios, separate from global/public chat. A server with its own chat plugin can leave them unused (`alliance_chat.enabled` / `group_chat.enabled` / `staff_chat.enabled`).

- **Plot Frequency** (`/ag chat`, permission `aegis.chat`) stays the plot-member radio. `/ag chat off` turns off any tuned channel.
- **Alliance radio** (`/ag chat alliance`, permission `aegis.chat.alliance`) reaches every online alliance member. The leader can name it with `/ag chat alliance name <title>` (max 32 characters, stored as `chat-title`).
- **Group chat** (`/ag chat group`, permission `aegis.chat.group`) reaches every online group member. The leader can name it with `/ag chat group name <title>` (max 32 characters, stored as `chat-title` on the group).
- **Staff chat** (`/ag staff`, `/ag staffchat`, `/agadmin staffchat`) reaches online players with `aegis.admin.staffchat` or `aegis.admin`. Bedrock uses the same commands (Floodgate/Geyser); there is no extra client.

Only one opt-in channel is active at a time. Public chat that is not intercepted still goes through Hearth rooms.

### BungeeCord networks (cross-server play)

AegisGuard can now link backend servers behind a BungeeCord/Waterfall/Velocity proxy into one network — no proxy-side jar required. With `network.enabled` and one shared MySQL/MariaDB database across every backend:

- **Shared discovery** — Travel Atlas, Discover, and Warps list remote plots with a `Server:` badge. Clicking one sends you through the proxy and lands you honoring that plot's arrival rules (classic spawn or public beacon pad) after the usual lockdown/entry checks.
- **Network radios** — alliance, group, and staff channels relay across the whole network through the shared event table, even with zero players online on the receiving end.
- **Player data follows** — claim-block balances, starter-claim state, language style, and notification preferences travel with the player.
- **Shared rosters** — alliances and groups merge through shared tables; kicks apply network-wide.
- **`/agadmin network`** — registry status, per-server online state, and pending arrivals.
- **Network hub** — `network.hub.server` names a dedicated hub/lobby backend. `/aegis hub` (and optional top-level `/hub`, `/lobby` via `network.hub.register_commands`) hops players there, landing at the configured point or the hub's world spawn. Without a hub configured — or when you're already on it — it falls back to the local world spawn, so the command is never dead.
- **Server selector** — `/aegis servers` and the Travel Hub's compass button open a live picker over the server registry: online state, player counts, and plugin versions, with click-to-travel hops.
- **Cross-server beacon links** — the Link picker lists **public** pads from every backend (pearls with a `Server:` badge). Remote links are one-way by design — your pad's `linked_id` points outward, the remote row is never touched, and the destination backend re-checks entry + pad state on landing. Unbinding a pad clears inbound links network-wide.
- **First-join redirect (opt-in)** — `network.hub.redirect_new_players: true` forwards players brand-new to the network (no shared data row) to the hub shortly after join.
- **Network broadcast** — `/agadmin network broadcast <message>` reaches every online player on every backend through the event bus; `/agadmin network find <player>` reports which backend last hosted someone (`aegis.admin.broadcast` for the former).
- **Cross-server arenas** — every backend advertises its enabled arenas in a shared catalog on each heartbeat. `/ag arena list` and the arena GUI show remote arenas with their hosting server; `/ag arena join <server>:<arena>` (or a GUI click) hops the player over and lands them in the host's run. Fighters are routed back to their origin backend when the run ends — falling back to `network.arena_return.fallback_server`, then the hub, then the arena exit if the origin went offline. Pair it with a dedicated arena backend plus `arena.lobby_seconds` so friends on other servers can join during the lobby window. Turn the catalog off per-backend with `network.arenas.publish: false`.
- **Dedicated arena world** — `arena.world.enabled` creates/loads a dedicated world (`arena.world.generator`: VOID / FLAT / NORMAL, default `aegis_arena`). Running `/ag arena create <id>` inside it auto-claims a protected server zone (`arena.world.auto_zone_radius`) and pre-binds the arena — works on standalone servers too, no network needed. **Wand-sized zones:** make an Aegis-wand selection first (right-click corner 1, left-click corner 2) and `arena create` claims exactly that area — any size, in any world — instead of the radius default. A bounded entity/item sweep (`arena.world.cleanup_on_run_end`) resets each floor when a run ends.
- **Arena lobby phase** — `arena.lobby_seconds` > 0 opens runs in a joinable lobby: `/ag arena join` attaches to an open lobby instead of starting a second run, and the leader (or an admin) re-running join starts waves immediately. `0` preserves the classic instant start.
- **Arena modes** — `/ag arena mode <id> <MODE>`: `PVE_WAVES` (classic), `PVE_BOSS` (waves into a boss finale; a wave spec with `boss: true`/`finalBoss: true` is the last fight and the run clears only when it dies), `PVP_FFA`, and `PVP_DUEL` (exactly two fighters, last one standing wins). PvP modes spawn no mobs, require `arena.pvp.enabled: true` plus `min_players >= 2`, and exempt same-run fighters from plot PvP protection — spectators, lobby members, and other participants stay protected.
- **Multi-floor arenas** — `/ag arena floors add <id>` (standing on another plot) binds extra map variants, each with its own entry spawn, mob spawns, and selection weight; the run picks a floor at start. Existing single-floor arenas keep working unchanged — the bound combat plot is simply floor 0.
- **Instanced custom maps** — with WorldEdit (FAWE on Folia), `/ag arena map save <id> [floor#]` captures a floor's region to `plugins/AegisGuard/arena-schematics/*.schem` and marks it instanced: each run pastes a fresh copy into `arena.schematics.world` (the dedicated arena world by default) at a per-run slot and wipes it afterwards — the same dungeon map can host concurrent runs. `arena map use` + `arena map setorigin` instances hand-built `.schem` files; `arena.schematics.max_instances` / `max_blocks` / `instance_spacing` bound the cost.
- **Sealed runs** (`arena.seal_runs`, default on) — while a run is live, its floor/lobby plots and instanced cuboids admit only that run's participants: outsiders can't walk, teleport, pearl, or respawn in, and outside players, spectators, stray mobs, and projectiles can't hurt participants or run-spawned mobs. Admin/bypass and the arena's own join/spectate/staging teleports are exempt.
- **Staging hub and matchmaking** — `/ag arena staging set` pins a shared meeting zone: `/ag arena hub` sends players there to browse arenas and form parties, and `arena.staging.return_on_end` returns local participants to the hub after a run (remote visitors still go home to their origin). With a wand selection made first, `staging set` also claims that exact area as a protected server zone, so the waiting hub can be any size and its blocks are grief-safe; `staging clear` (or a later `staging set` elsewhere) retires the auto-zone unless an arena is bound to it. `/ag arena queue <arena|any>` — or the queue buttons in the arena GUI — pools solos and whole parties into lobby runs once `min_players` is met; `arena.queue.tick_seconds` throttles matching.
- **Beacon arena gates** — `teleport_beacons.arena_gates.enabled` lets pad owners point a beacon at the arena system instead of another pad: the staging hub, a local arena, or a remote arena via `rarena:<server>:<id|hub>`. Pad trips keep the usual departure checks and hop players straight into a lobby or run.

Every network piece is gated and fails closed: YML/SQLite stays single-server, remote plots never enter local protection or upkeep, remote pads are read-only, stale arrivals expire, and `Connect` requests no-op without a proxy. See `NETWORK_SETUP.md` for setup and the one-time row-claim order.

### Open House and Roles GUI lock/undo

Owners, co-owners, and stewards can host a timed **Open House** with `/ag gathering start [minutes]` (or Explore → Open House). Live houses appear on Travel Atlas Discover under the **Live** filter. Optional visitor Guest Passes are issued to arrivals and revoked when the house ends. `modules.gatherings` can turn the system off.

The Members GUI now has **Lock / Unlock** on a member and **Undo last role change** on the roster, matching `/ag roles lock|unlock|undo`.

Hearth, Keep flags, Spawn/Hub presets, staff seasons, flight skills, Open House, and the voice-hook console lines are real language keys in all nine packs. Java English is only used when a key is missing everywhere. Copy `lang/modern_english/` to add your own pack; `lang/overrides.yml` wins over every style. Language sync never overwrites a translation you already wrote.

---

## Upgrade

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the plugin JAR with `AegisGuard-1.4.0.jar`.
4. Start the server. Config and language merge run on enable (`config_schema` `1294` → `1314`, with a backup). Existing plots load as-is and stay on classic arrival.
5. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`, `v140`). Doctor is optional.
6. Do **not** use Bukkit `/reload`.

---

## Compatibility

| Requirement | Support |
| :--- | :--- |
| **Java** | `21+` |
| **Minecraft** | `1.20+` |
| **Server software** | Spigot, Paper, Purpur, Folia, and compatible Bukkit forks |
| **Networks** | BungeeCord, Waterfall, Velocity (backend jar only; shared MySQL/MariaDB required) |
| **Upgrade path** | From AegisGuard `1.2.7`, `1.3.0`, or `1.3.5` with automatic config schema migration |
| **Languages** | Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish |
| **Optional** | Vault, PlaceholderAPI, WorldEdit/FAWE, Floodgate, Geyser-Spigot, Simple Voice Chat (`voicechat`) |

---

## Plugin file

Install this file in the server's `/plugins` folder:

`AegisGuard-1.4.0.jar`

API JARs (`AegisGuard-1.4.0-api.jar`, `AegisGuard-1.4.0-dev-api.jar`) are for developers and do **not** belong in `/plugins`.

---

## Quick commands

```text
/ag menu                     Open the territory dashboard
/ag quickclaim [radius]      Claim a square around you
/ag visit                    Open the Travel Atlas
/ag beacon                   Open your teleport-beacon pads
/ag arrival <classic|beacon> Choose how visitors arrive at the plot you manage
/ag heir [player|clear]      Name a succession heir
/ag succession               Open Stewardship / assume / rollback
/ag caravan                  Dispatch and track trade caravans
/agadmin season              Staff season featured plots and routes
/agadmin skill fly <player>  Grant a temporary flight skill
/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm upgrade status from 1.2.7, 1.3.0, or 1.3.5
/agadmin doctor              Optional diagnostics and repair tools
```

---

See also [`RELEASE_NOTES_1.3.5.md`](RELEASE_NOTES_1.3.5.md) and [`RELEASE_NOTES_1.3.0.md`](RELEASE_NOTES_1.3.0.md) for the systems this line still includes.

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
