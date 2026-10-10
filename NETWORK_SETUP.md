# AegisGuard Network Setup (BungeeCord / Waterfall / Velocity)

AegisGuard 1.4.0 can link multiple backend servers into one network — shared
plots, cross-server travel, relayed alliance/group/staff chat, and shared
claim-block balances — **without a proxy-side plugin**. Everything runs through
the shared database plus the proxy's standard plugin-messaging channel.

## What you need

1. **BungeeCord, Waterfall, or Velocity** proxy, already routing players
   between your backend servers. Velocity must accept the legacy `BungeeCord`
   plugin channel (it does by default — `bungeecord` or `modern` forwarding).
2. **Paper/Spigot/Purpur/Folia backends** — install `AegisGuard-1.4.0.jar`
   in `plugins/` on **every** backend that joins the network.
3. **One shared MySQL or MariaDB database** that every backend can reach.
   YAML and SQLite storage are per-server and cannot network.

## Step-by-step

### 1. Point every backend at the same database

In each server's `plugins/AegisGuard/config.yml`:

```yaml
storage:
  backend: mysql        # or mariadb — must be identical on every backend
  type: mysql
  database:
    host: your.db.host
    port: 3306
    database: aegisguard      # SAME database on every backend
    username: aegis
    password: secret
    useSSL: false
```

### 2. Name each backend exactly like the proxy calls it

```yaml
network:
  enabled: true
  server_name: "survival1"    # this backend's name in the proxy config
  display_name: "Survival"    # optional label in listings
```

`server_name` must match the name the proxy uses for that backend —
BungeeCord `config.yml` `servers:` entry, or Velocity `velocity.toml`
`[servers]` key. `/agadmin network` lists the registry; the plugin also warns
once in console if the proxy doesn't report a server by your configured name.

### 3. First shared boot — claim order matters

When you first point several backends at one database, **bring them up one at
a time**: start the server that owns the existing plots first. On load, that
backend atomically claims every untagged plot row (`server` column = its
`server_name`). Plots created later are tagged automatically.

If you start two fresh backends simultaneously with untagged data, whichever
loads first claims all of it. Re-tag rows by hand if that happens:

```sql
UPDATE aegis_plots SET server = 'survival2' WHERE world = 'world_nether';
```

### 4. Restart and verify

- Console: `[Network] Online as 'survival1' (Survival).`
- In game: `/agadmin network` shows every registered backend, online
  state, player counts, and pending arrivals.

## What crosses the network

| Feature | Behavior |
|---|---|
| Plot discovery | Travel Atlas / Discover / Warps list remote plots with a `Server:` badge (toggle: `network.travel.remote_destinations`). |
| Cross-server travel | Clicking a remote destination writes a pending arrival, sends you via proxy `Connect`, and lands you honoring that plot's arrival rules (classic spawn vs. public beacon pad) after lockdown/entry checks (toggle: `network.travel.cross_server_travel`). |
| Chat relay | Alliance, group, and staff channels relay network-wide over the shared event table — no players needed online on either end (toggle: `network.chat.relay_channels`). |
| Claim blocks | Balances, earned/bought/bonus/spent, starter claims, sell locks, language style, and notification prefs follow players (toggle: `network.shared_player_data.enabled`). |
| Alliances & groups | Rosters merge through shared tables — kicks on one backend apply everywhere. The network roster wins on boot. |
| Network hub | `/aegis hub` (+ optional `/hub` `/lobby`) sends players to the configured hub backend — world-spawn or an exact landing point (config: `network.hub.*`). Falls back to local world spawn when unset or when already on the hub. |
| Server selector | `/aegis servers` or the Travel Hub compass lists every registered backend with live online state, player counts, and versions — click to hop. |
| Cross-server beacons | Link a local pad to a **public** pad on another backend from the Link picker (pearls with a `Server:` line). Links are one-way only; remote rows are never written by your backend. |
| First-join redirect | With `network.hub.redirect_new_players: true`, brand-new network players (no shared data row) are forwarded to the hub ~2s after joining. Off by default. |
| Admin broadcast | `/agadmin network broadcast <message>` publishes to every backend's online players via the event bus (`aegis.admin.broadcast`). `/agadmin network find <player>` reports which backend last hosted a player. |
| Arenas | Each backend advertises its enabled arenas in `aegis_network_arenas` on every heartbeat; `/ag arena list` and the arena GUI show remote arenas with a server badge, and `/ag arena join <server>:<arena>` (or a click) writes an `ARENA_JOIN` arrival and hops the player. The host routes the player into the arena's lobby/run and sends them back to their origin backend when it ends — falling back to `network.arena_return.fallback_server`, then the hub, then the arena's own exit if the origin went offline (toggle: `network.arenas.publish`). |

## Arenas across the network

A dedicated arena backend is just a normal Paper backend running AegisGuard
with the arena module on. There is no special mode to enable:

1. Give it a distinct `network.server_name` (e.g. `arena1`) exactly as the
   proxy calls it.
2. Optionally set `arena.world.enabled: true` so `arena create` auto-claims a
   protected server zone inside a dedicated `aegis_arena` world (`generator`
   supports VOID / FLAT / NORMAL). This also works on a single server — no
   network required — whenever you want arenas off the live map.
3. Build the arena: `/ag arena create <id>` inside the arena world, then set
   mob spawns (`/ag arena setspawn <id> mob`), apply a preset, and enable it.
4. Raise `arena.lobby_seconds` (e.g. `30`) on the arena backend so friends on
   other servers have time to hop over and join the open lobby. `0` keeps the
   classic instant-start behavior.
5. Players on any backend run `/ag arena list` (remote rows show the hosting
   server) or `/ag arena join <server>:<arena>` to travel over.

Runs always execute on the hosting backend — the run, mobs, snapshots, and
leaderboards stay local to it. What crosses the wire is only the catalog row
and the player's arrival. If a player disconnects mid-run, their inventory
snapshot and a pending-recovery record remain on the host; relogging there
restores the inventory and routes them home.

## What stays local (by design)

- **Plot chat** — bound to the world the plot lives in.
- **Protection indexing** — remote plots never enter local protection,
  upkeep, or claim-limit code paths; each backend owns its rows.
- **Claim limits** — counted per backend, not pooled.
- **Beacons** — pads are created, edited, and unbound on the owning server
  only; remote pads appear in the link picker read-only. Remote plots still
  honor beacon-arrival when visitors land (enforced at the destination).
- **Remote links** — a pad may link outbound to a remote public pad, but
  remote pads never link back through your backend and never appear in
  edit/manage screens.

## Failure behavior

- `network.enabled: true` on YML/SQLite → warning logged, networking
  disables itself, everything works single-server.
- Wrong `server_name` → console warning listing the proxy's known names.
- Destination server offline → the click fails closed with a message; no
  arrival is consumed.
- Arrival expires (`network.arrival_ttl_seconds`, default 90s) or the plot
  vanished → safe failure on join, no teleport.
- No proxy/channel (plain Bukkit or unproxied Paper) → `Connect` sends
  silently fail; the player stays put.

## Tables it creates

`aegis_network_servers`, `aegis_network_arrivals`, `aegis_network_events`,
`aegis_network_arenas`, `aegis_player_data`, `aegis_network_alliances`,
`aegis_network_alliance_members`, `aegis_network_groups`,
`aegis_network_group_members` — plus a `server` column on `aegis_plots`.

## Tuning

```yaml
network:
  heartbeat_seconds: 30       # registry liveness write
  offline_after_seconds: 120  # heartbeat staleness → shown offline
  arrival_ttl_seconds: 90     # pending-arrival validity window
  hub:
    server: ""                # proxy name of the hub backend ("" = no hub)
    world: ""                 # optional landing world ("" = hub's spawn world)
    x: 0
    y: 0
    z: 0                      # optional exact landing point when world set
    register_commands: true   # register /hub and /lobby aliases
    redirect_new_players: false  # send brand-new network players to the hub
  arenas:
    publish: true             # advertise this backend's arenas network-wide
  arena_return:
    fallback_server: ""       # where fighters land if their origin went dark
                              # ("" = hub server, then the arena's local exit)
```

Arena-side knobs live under `arena:` — `lobby_seconds` (joinable window before
waves start, `0` = instant) and `world:` (dedicated arena world + auto-zone).
