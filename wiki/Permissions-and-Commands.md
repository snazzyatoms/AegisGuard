# Permissions and Commands

This reference lists the main commands and recommended permissions for **AegisGuard** `v1.4.0`.

> **Important notes**
>
> - **Claim Status** stays on the Territory row of `/ag menu`. It is a plot snapshot (owner, protections, growth, access), not a separate primary flow.
> - **Biome Studio** is not part of the active plugin flow.
> - **Frontier Expansion** is AegisGuard's current presentation for land expansion. It remains the plot-expansion system, presented through a more distinct identity and GUI flow.
> - Many 1.4.0 features are available through `/ag menu` as well as commands: Guest Passes, Emergency Lockdown, Realm Profile, Routes, Alliance Access, Teleport Beacons, Travel Atlas, Aegis Frequency, alliance radio, staff chat, Visual Presence, Quick-Claim, Stewardship, Caravans, and the first-claim walkthrough.
> - If a menu icon is missing, that module is turned off on this server.
> - The recommended permission bundles are `aegis.user` for regular players and `aegis.admin` for administrators.

---

## Player Essentials

Basic commands for claiming, travel, and personal plot management.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag wand` | `aegis.user` | Receive the **Aegis Scepter** for normal claim selection. |
| `/ag claim` | `aegis.user` | Claim the selected area as a personal plot. |
| `/ag quickclaim [radius]` | `aegis.user` | Claim a square around you using the same validation as `/ag claim`. Alias `/ag qc`. |
| `/ag unclaim` | `aegis.user` | Remove the plot you are standing in. |
| `/ag menu` | `aegis.user` | Open the main AegisGuard menu / Guardian Codex. |
| `/ag help` | `aegis.user` | View the command and help summary. |
| `/ag cost` | `aegis.user` | Preview a claim cost before confirming. |
| `/ag resize <direction> <amount>` | `aegis.user` | Expand or shrink a plot. Directions are `north`, `south`, `east`, or `west`. |
| `/ag setspawn` | `aegis.user` | Set a plot home point. |
| `/ag home` | `aegis.user` | Teleport to a plot home point. |
| `/ag visit` | `aegis.user` | Open the Travel Atlas. |
| `/ag beacon` | `aegis.beacon` | Open the Atlas **My Beacons** tab on a claim you manage. Included in `aegis.user`. |
| `/ag arrival <classic\|beacon>` | `aegis.user` | Choose how visitors arrive at the plot you manage. Run with no argument to see the current mode. |
| `/ag heir [player\|clear]` | `aegis.user` | Name or clear the succession heir for the plot you manage. |
| `/ag succession [assume\|rollback\|menu]` | `aegis.user` | Open Stewardship, assume an inactive owner's plot as heir, or roll back a recent transfer. |
| `/ag caravan` | `aegis.user` | Open the Atlas Caravans tab; list, dispatch, or cancel trade shipments. |
| `/ag stuck` | `aegis.user` | Escape to a safer nearby location. |
| `/ag rename <name>` | `aegis.user` | Set a custom plot name. |
| `/ag setdesc <text>` | `aegis.user` | Set a plot description. |
| `/ag welcome <message>` | `aegis.user` | Set a plot welcome message. Leave the message blank to clear it. |
| `/ag farewell <message>` | `aegis.user` | Set a plot farewell message. Leave the message blank to clear it. |
| `/ag guide` | `aegis.user` | Open or replay the first-claim walkthrough. |
| `/ag profile` | `aegis.user` | Open Realm Profile for the current plot. |
| `/ag chat` | `aegis.chat` | Toggle plot Frequency (opt-in plot-member radio). `/ag chat off` turns off any Aegis channel. |
| `/ag chat alliance` | `aegis.chat.alliance` | Toggle alliance radio. `/ag chat alliance name <title>` lets the leader name it. |
| `/ag chat group` | `aegis.chat.group` | Toggle group radio. `/ag chat group name <title>` lets the leader name it. |
| `/ag staff` | `aegis.admin.staffchat` | Toggle staff chat. Aliases `/ag staffchat`. Bedrock uses the same command. |

---

## Realm Profile and Noticeboard

Public plot identity tools. Noticeboard posts restore with claim-data snapshots in 1.3.5.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag profile` | `aegis.user` | Open Realm Profile for name, category, greeting, and noticeboard tools. |
| `/ag rename <name>` | `aegis.user` | Set a custom plot name. |
| `/ag setdesc <text>` | `aegis.user` | Set a plot description. |
| `/ag notice add <text>` | `aegis.user` | Post a noticeboard entry on the current plot. |
| `/ag notice list` | `aegis.user` | List noticeboard entries. |
| `/ag notice remove <#>` | `aegis.user` | Remove a noticeboard entry by number. |

Managing Realm Profile and notices generally requires standing in a plot you can manage. Visitors may still view public profile details where discovery or travel menus expose them.

---

## Notifications and Preferences

AegisGuard separates claim greetings from administrative updates. Players can also manage related preferences in **Settings** from `/ag menu`, including **Choose Your Language**.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag notify` | `aegis.notify` | Show notification help and current behavior. |
| `/ag notify greetings <on or off>` | `aegis.notify` | Toggle claim enter and exit notifications. |
| `/ag notify admin <on or off>` | `aegis.notify` | Toggle administrative updates for eligible players. |
| `/ag notify all <on or off>` | `aegis.notify` | Toggle greeting and administrative notifications together. |
| `/ag guide` | `aegis.user` | Replay the optional first-claim walkthrough. |

### Notification Notes

- Servers using per-player notification mode allow players to manage their own preferences.
- Servers that force notifications globally may limit player toggles.
- Settings may also include language, sound preferences, and repeat-notification behavior.
- Staff may also use `aegis.notify.bypass` and `aegis.notify.others`.

---

## Empire Building

Commands that grow, organize, and improve land.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag merge <direction>` | `aegis.user` | Merge a plot with an adjacent plot you own. Directions are `north`, `south`, `east`, or `west`. |
| `/ag zone` | `aegis.user` | Open the Zone Manager for rentals, rooms, and sub-areas. |
| `/ag subplot <name>` | `aegis.user` | Create a subplot from the current wand selection inside your plot. |
| `/ag subzone <name>` | `aegis.user` | Alias for `/ag subplot`. |
| `/ag level` | `aegis.user` | Open **Plot Ascension**. |
| `/ag like` | `aegis.user` | Like the plot you are currently visiting. |
| `/ag arena` | `aegis.user` / `aegis.arena.use` | Browse Arena runs when the Arena module is on. |

### Frontier Expansion

**Frontier Expansion** is the AegisGuard name for the land-expansion system.

It allows players to:

- Expand a plot.
- Request additional land.
- Grow territory through the available expansion flow.

The system retains the purpose of standard plot expansion while providing a clearer identity and more polished GUI presentation. If Expansions are turned off, that menu icon is hidden.

### Routes and Checkpoints

Players browse staff-authored routes from **Routes** in `/ag menu`.

| Access | Recommended Permission | Description |
| :--- | :--- | :--- |
| Routes menu | `aegis.user` | Browse routes, view the next checkpoint, and track discovery progress. |
| Route Editor | `aegis.admin.routes` | Create and edit routes and checkpoints from the Admin GUI. |

Routes do not change claim boundaries. Optional teleports and rewards are server-configured and default safely.

### Teleport Beacons

Linked pads on claims you manage. Travel uses Safe Travel. `/ag home` stays plot spawn.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag beacon` | `aegis.beacon` | Open the pad manager, place pads, link them, and set public-arrival / fee options the server allows. |
| `/ag arrival <classic\|beacon>` | `aegis.user` | Set how visitors arrive at the plot you manage; run with no argument to report the current mode. |

Fee policy is `teleport_beacons.charges.mode`: `owner_choice`, `always`, or `off`.

### Travel Atlas arrival choice (1.4.0)

Each plot manager chooses how their **public listings** (Visit, market jump, and auction visit) let visitors land:

- **classic** — Safe Travel to the plot spawn, even when pads exist (1.3.0 style).
- **beacon** — visitors must land on a public arrival pad; if none exists the trip fails closed (`beacon_no_public_arrival`) instead of falling back to spawn.

Set the mode with `/ag arrival <classic|beacon>`. Existing plots default to **classic**. A server-wide `teleport_beacons.force_public_arrival: true` requires pad arrival network-wide. `/ag beacon` opens the Atlas **My Beacons** tab. The `config_schema` bumps from `1294` to `1310` on upgrade, with a backup.

---

## Groups and Shared Plots

Commands for shared progression and group-owned land.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag group create <name>` | `aegis.user` | Create a new plot group. |
| `/ag group invite <player>` | `aegis.user` | Invite a nearby player to the group. |
| `/ag group accept <name>` | `aegis.user` | Accept a pending group invitation. |
| `/ag group status` | `aegis.user` | View group treasury and membership details. |
| `/ag group deposit <amount>` | `aegis.user` | Deposit money into the shared group treasury. |
| `/ag group claim` | `aegis.user` | Claim the first group plot from the current wand selection. |
| `/ag group leave` | `aegis.user` | Leave the current group. |
| `/ag group kick <player>` | `aegis.user` | Remove a player from the group when you are the leader. |
| `/ag group disband` | `aegis.user` | Disband the current group when allowed. |

> Groups provide shared ownership and treasury. They are separate from **Alliance Access**.

---

## Alliance Access

Alliances share optional plot permissions. Membership alone grants nothing, and all risky toggles default **OFF**.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag alliance create <name>` | `aegis.user` | Create a new alliance. |
| `/ag alliance invite <player>` | `aegis.user` | Invite a player (leader only). |
| `/ag alliance accept` | `aegis.user` | Accept a pending alliance invite. |
| `/ag alliance leave` | `aegis.user` | Leave your current alliance. |
| `/ag alliance disband` | `aegis.user` | Disband the alliance (leader only). |
| `/ag alliance menu` | `aegis.user` | Open Alliance Access for the current context. |
| `/ag alliance status` | `aegis.user` | View alliance name, members, and leader. |
| `/ag chat alliance` | `aegis.chat.alliance` | Toggle the alliance radio. Online members hear sends. Leader may `/ag chat alliance name <title>`. |

Plot owners/managers join a plot to an alliance and enable only the toggles they want:

- Enter
- Interact
- Containers
- Build
- Animals
- Friendly PvP

Alliance Access never grants ownership, money control, rentals, or management rights.

---

## Plot Moderation and Safety

Owner-level tools for managing behavior on your land.

| Command / Tool | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag kick <player>` | `aegis.user` | Remove a player from your plot. |
| `/ag ban <player>` | `aegis.user` | Block a player from entering your plot. |
| `/ag unban <player>` | `aegis.user` | Remove a player from your plot ban list. |
| Guest Passes menu | `aegis.user` | Issue temporary, self-expiring access from `/ag menu`. |
| Emergency Lockdown menu | `aegis.user` | Quickly restrict sensitive actions from `/ag menu`. |

Guest Passes and Emergency Lockdown require standing in a plot you can manage. Guest Passes never overwrite permanent roles. Lockdown is reversible and does not delete the claim.

---

## Economy and Marketplace

Commands for selling land, browsing markets, ClaimBlocks, and related economy tools.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag sell <price>` | `aegis.user` | List the current plot for sale. |
| `/ag unsell` | `aegis.user` | Remove a plot from sale. |
| `/ag market` | `aegis.user` | Open the main market menu. |
| `/ag market local` | `aegis.user` | Open the Local Market for the current plot or area. |
| `/ag market global` | `aegis.user` | Open the global market view. |
| `/ag auction` | `aegis.user` | Open the auction browser. |
| `/ag giftblocks` | `aegis.claimblocks.gift` | Gift available ClaimBlocks to another player. |

`aegis.claimblocks.gift` is included in `aegis.user`.

### ClaimBlocks

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/ag ledger` | `aegis.user` | View the land ledger and ClaimBlock summary. |
| `/ag blocks` | `aegis.user` | View ClaimBlock information and help. |
| `/ag blocks buy <amount>` | `aegis.claimblocks.buy` | Buy ClaimBlocks with Vault money. |
| `/ag blocks sell <amount>` | `aegis.claimblocks.sell` | Sell ClaimBlocks when enabled by the server. |
| `/ag blocks earnings <on, off, or status>` | `aegis.user` | Manage passive ClaimBlock earnings when the server allows opt-out. |

### ClaimBlocks Permission Notes

Exchange access is controlled by:

- `aegis.claimblocks.exchange`
- `aegis.claimblocks.buy`
- `aegis.claimblocks.sell`

Playtime earnings are controlled by:

- `aegis.earn.blocks`

Additional staff or server-owner bypass nodes include:

- `aegis.claimblocks.exchange.bypass`
- `aegis.claimblocks.selllock.bypass`
- `aegisguard.claimblocks.selllock.bypass`

---

## Server Administration

High-level commands for administrators, owners, and trusted staff.

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/agadmin menu` | `aegis.admin` | Open the administrator control panel. |
| `/agadmin reload` | `aegis.admin` or `aegis.reload` | Reload configuration and language files. |
| `/agadmin transition` | `aegis.admin` | Confirm 1.2.7, 1.3.0, or 1.3.5 to 1.4.0 upgrade status (aliases `upgrade`, `v130`). |
| `/agadmin health` | `aegis.admin` | Quick staff health check. |
| `/agadmin staffchat` | `aegis.admin.staffchat` | Toggle staff radio. Alias `/agadmin sc`. Same as `/ag staff`. |
| `/agadmin bypass` | `aegis.admin.bypass` | Toggle protection-bypass mode. |
| `/agadmin wand` | `aegis.admin.wand` | Receive the **Sentinel's Scepter** by default. |
| `/agadmin wand server` | `aegis.admin.wand` | Explicitly receive the Sentinel's Scepter for server-zone claiming. |
| `/agadmin claim` | `aegis.serverzone.manage` or `aegis.admin.manage` | Confirm the current selection as a server zone. |
| `/agadmin wand migration` | `aegis.admin.wand` | Receive the migration wand. |
| `/agadmin convert` | `aegis.convert` | Convert a supported plot into a server-controlled zone. |
| `/agadmin doctor` | `aegis.admin` | Optional diagnostics report. Not required after a 1.2.7 JAR swap. |
| `/agadmin snapshot here [reason]` | `aegis.admin` | Create a manual recovery snapshot for the current plot. |
| `/agadmin restore here` | `aegis.admin` | Preview or restore a snapshot for the current plot. |
| `/agadmin restore here confirm [scope]` | `aegis.admin` | Confirm a restore after preview. |
| `/agadmin restore operation <id> retry\|release` | `aegis.admin` | Retry or release a paused restore transaction. |
| `/agadmin audit` | `aegis.admin.audit` | Open the Staff Audit Ledger. |

Snapshots always store **claim data** (owner, bounds, flags, members, guest passes, lockdown, alliance access, noticeboard, and 1.3.5 plot maps). Optional WorldEdit/FAWE **build** copies require `snapshots.build_backup.enabled`.

### Admin GUI Tools in 1.3.5

From `/agadmin menu`, trusted staff may also access:

| Tool | Recommended Permission | Description |
| :--- | :--- | :--- |
| Route Editor | `aegis.admin.routes` | Create and maintain exploration routes and checkpoints. |
| Audit Ledger | `aegis.admin.audit` | Review audited staff and safety actions such as restore, repair, migration, bypass, Guest Pass, Lockdown, and Alliance changes. |
| Doctor / Repair | `aegis.admin` / `aegis.admin.doctor.repair` | Scan and repair common data issues. Optional after upgrade. |
| Snapshot tools | `aegis.admin` | Create and restore recovery snapshots (claim data; optional WorldEdit build copy when enabled). |
| Arena Admin | `aegis.arena.admin` or `aegis.arena.steward` | Configure arenas when the Arena module is on. |

### Server Zone Claiming

Create a server zone with the following process:

1. Run `/agadmin wand server`.
2. Right-click the first corner.
3. Left-click the second corner.
4. Run `/agadmin claim`.

This creates a **server zone**, not a personal player claim. Wand-create and convert-to-server share one Steward pipeline.

### Server Plot Management

Server plots and server zones are staff-owned protected areas. Use them for spawn, public hubs, marketplaces, event grounds, staff facilities, infrastructure, and other areas that should not belong to an individual player.

On a server plot, Claim Settings → Safety includes optional **Keep Health**, **Keep Hunger**, **Keep XP**, and **Keep Inventory** toggles. All start **off**. Player plots never show them. Steward / `aegis.serverzone.manage` can change those; Safe Zone stays admin-only. There is no hub-flight Safety flag. Flight is Ascension/Horizon level 30, staff fly (`aegis.admin.fly`), or `/agadmin skill fly`. `/agadmin season` pins featured Atlas plots and Routes. **Hearth** is on Safety for player and server plots: public chat stays in the 3D subplot (or the rest of the plot). Staff with `aegis.admin.hearth` still hear every room. If Simple Voice Chat is installed, those rooms also become isolated voice groups.

Use the administrator panel to access available server-plot controls:

```text
/agadmin menu
```

The following permissions are recommended for staff who need to create or manage server plots.

| Permission | Purpose |
| :--- | :--- |
| `aegis.admin` | Grants the standard administrator command bundle and access to the admin control panel. |
| `aegis.serverzone.manage` | Allows management of server zones and staff-owned protected areas. |
| `aegis.admin.manage` | Allows management of plots owned by other players when staff intervention is required. |
| `aegis.admin.wand` | Allows use of the Sentinel's Scepter for server-zone and migration selections. |
| `aegis.admin.bypass` | Allows protection-bypass mode when needed for authorized maintenance. |
| `aegis.admin.bypass-limits` | Allows staff to ignore normal plot-size and limit restrictions. |
| `aegis.admin.routes` | Allows the Route Editor for exploration routes and checkpoints. |
| `aegis.admin.audit` | Allows the Staff Audit Ledger. |

### Recommended Server-Plot Workflow

1. Open `/agadmin menu` to review available administrative controls.
2. Use `/agadmin wand server` to receive the Sentinel's Scepter.
3. Select the required server area with a right-click first corner and left-click second corner.
4. Run `/agadmin claim` to create the server zone.
5. Use the administrator panel and `aegis.serverzone.manage` permission to maintain server-owned areas.
6. Use `/agadmin bypass` only for authorized maintenance inside protected land.

> **Staff guidance:** Grant `aegis.serverzone.manage` only to trusted staff. Server zones protect shared server infrastructure and should be managed separately from regular player claims.

---

## Migration Tools

> **Required permission:** `aegis.admin.migrate`

| Command | Recommended Permission | Description |
| :--- | :--- | :--- |
| `/agadmin migrate` | `aegis.admin.migrate` | Open the migration wizard or GUI. |
| `/agadmin migrate help` | `aegis.admin.migrate` | Show migration help. |
| `/agadmin migrate list` | `aegis.admin.migrate` | List supported detected migration sources. |
| `/agadmin migrate preview <source>` | `aegis.admin.migrate` | Preview what would be imported. |
| `/agadmin migrate import <source>` | `aegis.admin.migrate` | Start an import for a supported source. |
| `/agadmin migrate import <source> --force` | `aegis.admin.migrate` | Import with overlap-forcing behavior. |
| `/agadmin migrate import <source> --world=<name>` | `aegis.admin.migrate` | Import one world only. |
| `/agadmin migrate import <source> --no-trusted` | `aegis.admin.migrate` | Skip trusted members during import. |
| `/agadmin migrate import <source> --no-flags` | `aegis.admin.migrate` | Skip flags during import. |

### Migration Examples

```text
/agadmin migrate list
/agadmin migrate preview griefprevention
/agadmin migrate import griefprevention
/agadmin migrate import gd --force
/agadmin migrate import lands --world=survival
```

### Migration Notes

- Preview migrations before importing on larger servers.
- Use world filters for gradual migrations.
- Use `--force` carefully because it permits overlap-forcing behavior.
- Supported import sources include:
  - `griefprevention` or `gp`
  - `griefdefender` or `gd`
  - `lands`

---

## Staff Bypass and Utility Permissions

Useful optional nodes for trusted staff and elevated server roles.

| Permission | Description |
| :--- | :--- |
| `aegis.beacon` | Open `/ag beacon` and manage pads on claims you can manage. Included in `aegis.user`. |
| `aegis.chat` | Use `/ag chat` Aegis Frequency on claims you belong to. Included in `aegis.user`. |
| `aegis.chat.alliance` | Use `/ag chat alliance` alliance radio. Included in `aegis.user`. |
| `aegis.chat.group` | Use `/ag chat group` group radio. Included in `aegis.user`. |
| `aegis.admin.staffchat` | Use `/ag staff` and `/agadmin staffchat`. Included in `aegis.admin`. |
| `aegis.notify.bypass` | Receive plot notifications when normal toggles are restricted. |
| `aegis.notify.others` | Manage notification preferences for other players. |
| `aegis.claimblocks.gift` | Gift ClaimBlocks (`/ag giftblocks`). Included in `aegis.user`. |
| `aegis.claimblocks.exchange.bypass` | Bypass ClaimBlocks exchange cooldowns and limits. |
| `aegis.claimblocks.selllock.bypass` | Bypass ClaimBlocks sell-lock restrictions. |
| `aegisguard.claimblocks.selllock.bypass` | Legacy compatibility bypass node for older setups. |
| `aegis.admin.bypass-limits` | Ignore normal plot-size and limit restrictions. |
| `aegis.admin.manage` | Manage plots owned by other players. |
| `aegis.serverzone.manage` | Manage server zones and other staff-owned protected areas. |
| `aegis.admin.routes` | Create and edit Routes and Checkpoints. |
| `aegis.admin.audit` | View the Staff Audit Ledger. |
| `aegis.admin.doctor.repair` | Run supported Doctor repairs. |
| `aegis.arena.use` | Browse and join Arena runs. Included in `aegis.user`. |
| `aegis.arena.spectate` | Spectate Arena runs. Included in `aegis.user`. |
| `aegis.arena.steward` | Configure arenas on managed server plots. |
| `aegis.arena.admin` | Bind arenas, abort runs, and resolve rewards. |
| `aegis.market.manage` | Manage public market or utility plots. |
| `aegis.staff.co_owner` | Grant co-owner-style staff access for elevated server roles. |
| `aegis.staff.market_steward` | Grant market-steward-style staff access for managed market areas. |

---

## Recommended LuckPerms Setup

### Regular Players

Grant the standard player bundle:

```text
/lp group default permission set aegis.user true
```

If the server uses ClaimBlocks exchange and passive earnings, also consider:

```text
/lp group default permission set aegis.claimblocks.exchange true
/lp group default permission set aegis.claimblocks.buy true
/lp group default permission set aegis.claimblocks.sell true
/lp group default permission set aegis.earn.blocks true
```

If notification toggles are permission-gated on your server:

```text
/lp group default permission set aegis.notify true
```

### Administrators and Owners

Grant the standard administrator bundle:

```text
/lp group admin permission set aegis.admin true
```

For staff who should edit exploration routes or read the Audit Ledger:

```text
/lp group admin permission set aegis.admin.routes true
/lp group admin permission set aegis.admin.audit true
```

For a full owner or development-test setup:

```text
/lp user <name> permission set aegis.* true
```

### Recommended Permission Bundles

- `aegis.user`
- `aegis.admin`

### Important Special Nodes

- `aegis.claimblocks.exchange`
- `aegis.claimblocks.buy`
- `aegis.claimblocks.sell`
- `aegis.claimblocks.gift`
- `aegis.earn.blocks`
- `aegis.admin.migrate`
- `aegis.serverzone.manage`
- `aegis.admin.wand`
- `aegis.admin.routes`
- `aegis.admin.audit`
- `aegis.beacon`

---

> *Simple. Steadfast. Eternal.*
