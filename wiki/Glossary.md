# Glossary

AegisGuard names its systems so menus stay readable. This page translates each name into plain terms. Looking for how-to steps instead? Try the [Player Handbook](Player-Handbook) or the [FAQ](Frequently-Asked-Questions).

---

## Claiming and Land

| Term | Meaning |
| :--- | :--- |
| **Aegis Scepter** | The claim-selection wand from `/ag wand`. Right-click one corner, left-click the other, then `/ag claim`. |
| **Sentinel's Scepter** | The staff version of the wand (`/agadmin wand server`), used to mark server zones. |
| **Claim / Plot** | A protected rectangle of land owned by a player, group, or the server. The docs use both words interchangeably. |
| **Subplot / subzone** | A smaller managed area *inside* a claim — a room, stall, or wing — created with `/ag subplot`. Managed in the Zone Manager (`/ag zone`). |
| **Server zone / server plot** | A claim owned by the server rather than a player — spawn, hubs, event grounds. Managed by staff or the Steward role. |
| **Guardian Codex** | The in-game management menu opened with `/ag menu`. The framed dashboard where settings, roles, and modules live. |
| **Guardian's Guide / Info Book** | The built-in help book inside the Guardian Codex. |
| **Claim Status** | The Territory-row snapshot of the plot you're standing in: owner, protections, growth, access. |

## Travel

| Term | Meaning |
| :--- | :--- |
| **Safe Travel** | The arrival-checking layer under every teleport — destinations are validated before you land. |
| **Travel Atlas** | The unified travel menu (`/ag visit`): Destinations, My Beacons, Arrival, and Caravans. `/ag travel` opens the Travel Hub that contains it. |
| **Teleport Beacon** | A linked pad on a plot you manage (`/ag beacon`). Step on pad A, land at pad B. One pad per block, one directed A→B link. |
| **Arrival mode** | How public-listing visitors reach your plot: `classic` (Safe Travel to plot spawn) or `beacon` (must land on a public pad). Set with `/ag arrival`. |
| **Routes & Checkpoints** | Staff-authored exploration paths players discover by proximity; optional rewards. Found under Routes in `/ag menu`. |
| **Caravan** | A charge-then-deliver shipment dispatched along public beacon hops (`/ag caravan`). Insurance, route events, and hireable player **escorts** cover the risk. |

## Economy

| Term | Meaning |
| :--- | :--- |
| **ClaimBlocks** | The land currency. Your **used** amount is the live area of your plots; **spent** covers non-land costs; `available = total − used − spent`. Buy/sell at `/ag blocks`. |
| **Ledger** | Your ClaimBlock transaction history (`/ag ledger`). |
| **Settlements inbox** | `/ag settlements` — where rental and market payouts collect instead of getting lost in chat. |
| **TradeStall** | A protected, plot-based shopfront where visitors browse items through an interface. |
| **Local Market** | A plot- or zone-level market (`/ag market local`) with rentable stalls, My Rentals, and My Tenants. |
| **Upkeep** | Optional recurring claim tax; helps reclaim abandoned land. Server-configured. |

## Community and Access

| Term | Meaning |
| :--- | :--- |
| **Roles / Members** | Per-plot access levels (Member, Guest, Helper, custom) that control who can do what on your land. |
| **Guest Pass** | Temporary, self-expiring plot access — presets like Visitor or Temporary Builder. Never overwrites permanent roles. |
| **Group** | Shared ownership: a group claims land together and funds it from a shared treasury (`/ag group`). |
| **Alliance / Alliance Access** | A cross-plot pact. Alliances share only the opt-in toggles you enable per plot — never ownership, money, or rentals. |
| **Realm Profile** | A plot's public identity: name, category, greeting, description (`/ag profile`). |
| **Noticeboard** | The visitor-facing message board on a Realm Profile (`/ag notice`). |
| **Emergency Lockdown** | A fast, reversible safety switch on a managed plot — restricts sensitive actions until lifted. |
| **Gathering / Open House** | A timed public event on a managed plot (`/ag gathering`), with optional visitor Guest Passes. |
| **Aegis Radio / Plot Frequency** | Opt-in text chat channels: plot (`/ag chat`), alliance, group, and staff (`/ag staff`). One channel at a time; vanilla text, no client mod. |
| **Hearth** | A Safety toggle that scopes *public* chat to the 3D room you're standing in — a subplot, or the rest of the plot. With Simple Voice Chat installed, rooms become isolated voice groups too. |

## Progression and Stewardship

| Term | Meaning |
| :--- | :--- |
| **Plot Ascension / Ascension Hall** | The plot-leveling system (`/ag level`) — tiers unlock capacity, perks, and advanced features. |
| **Frontier Expansion** | The plot-expansion system — request more land, review tiers and costs. |
| **Expansion Horizons** | The post-level-30 continuation: Renown, named ranks, bound Sigils, controlled growth. |
| **Guardian Succession** | Plot continuity: name an heir (`/ag heir`), manage handover and inactivity assumption (`/ag succession`). |
| **Stewardship** | The role/page for managing server-owned plots and succession actions without blanket admin. |
| **Sanctuary** | Optional server-plot Safety toggles: Keep Health, Hunger, XP, and Inventory. Off by default; never on personal claims. |
| **Claim presets** | The post-claim chooser (Home/Shop/Arena/Farm) that applies a starting profile without touching safety settings. |
| **Staff seasons** | `/agadmin season` — featured plots and routes pinned by staff for a season. |

## Administration

| Term | Meaning |
| :--- | :--- |
| **Module switchboard** | The `modules:` section in `config.yml` — each optional system toggles on/off; menus hide disabled modules. |
| **Audit Ledger** | The staff audit trail for restores, repairs, migrations, bypass use, passes, lockdowns, and alliance changes (`/agadmin audit`). |
| **Territory Doctor** | The diagnostics/repair toolkit (`/agadmin doctor`). Optional after a normal upgrade. |
| **Snapshot** | A saved claim record (owner, bounds, flags, members, passes, notices) used for rollback. Optional WorldEdit/FAWE build copies add actual blocks. |
| **`config_schema`** | The config version number. Schema migration merges new keys on upgrade and writes a backup (`1294` → `1316` for 1.4.0). |
| **`/agadmin transition`** | Confirms an upgrade from 1.2.7/1.3.x completed; aliases `upgrade`, `v130`, `v140`. |
| **Migration** | Importing claims from GriefPrevention, GriefDefender, or Lands (`/agadmin migrate`). |

## Network

| Term | Meaning |
| :--- | :--- |
| **Network** | With `network.enabled` and shared MySQL/MariaDB, backend servers behind a BungeeCord/Velocity proxy act as one AegisGuard ecosystem. |
| **Hub** | The designated lobby backend; `/ag hub` and `/ag lobby` take players there. |
| **Server selector** | `/ag servers` — a live picker over the network's backends. |

---

> *Simple. Steadfast. Eternal.*
