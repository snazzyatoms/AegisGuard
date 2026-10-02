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

## What stays local (by design)

- **Plot chat** — bound to the world the plot lives in.
- **Protection indexing** — remote plots never enter local protection,
  upkeep, or claim-limit code paths; each backend owns its rows.
- **Claim limits** — counted per backend, not pooled.
- **Beacons** — pads are managed on the owning server; remote plots still
  honor beacon-arrival when visitors land (enforced at the destination).

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
`aegis_player_data`, `aegis_network_alliances`,
`aegis_network_alliance_members`, `aegis_network_groups`,
`aegis_network_group_members` — plus a `server` column on `aegis_plots`.

## Tuning

```yaml
network:
  heartbeat_seconds: 30       # registry liveness write
  offline_after_seconds: 120  # heartbeat staleness → shown offline
  arrival_ttl_seconds: 90     # pending-arrival validity window
```
