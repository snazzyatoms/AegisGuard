# Player Handbook

Welcome to **AegisGuard** `v1.3.5`.

This handbook explains how to claim land, manage access, grow your territory, use travel and economy features, and protect your community.

> **Server configuration:** AegisGuard features can be enabled, disabled, or customized by the server owner. If a command, menu option, or feature is unavailable, it may be turned off on this server — contact staff. Disabled modules do not appear on the AegisGuard menu.

---

## Quick Start Guide

1. Run `/ag wand` to receive your **Aegis Scepter**.
2. **Right-click** the first corner of your desired plot.
3. **Left-click** the opposite diagonal corner.
4. Run `/ag claim` to create the claim.
5. Run `/ag menu` to manage claim settings, roles, and permissions.
6. Run `/ag setspawn` to set your claim home point.

New plots are fully protected when created.

Optional first steps after claiming:

- Open **Settings** to choose a language and tune greetings, notifications, and sounds.
- Use **Realm Profile** to set your plot's public name, category, greeting, and noticeboard.
- Use **Guest Passes** for short-term helpers without permanent trust.
- Replay the first-claim walkthrough any time with `/ag guide`.

---

## Claiming Land

### Create a Claim

1. Run `/ag wand` to receive the **Aegis Scepter**.
2. **Right-click** the first corner of the area you want to protect.
3. **Left-click** the opposite corner.
4. Run `/ag claim` to confirm the selection.

> The **Aegis Scepter** is used exclusively to select land. It is not intended to be placed as a block.

Depending on server configuration, new players may receive a scepter automatically. The scepter may also be consumed when a claim is successfully created.

If your server enables the first-claim walkthrough, AegisGuard may offer a short, skippable guide after your first claim. You can reopen it later from Settings or with `/ag guide`.

---

## Managing Your Claim

Run `/ag menu` while inside your claim to open the AegisGuard management menu. The framed dashboard stays the same on every server; optional icons only appear when that module is on.

### Claim Settings

Use **Claim Settings** to control how players and world mechanics interact with your land.

| Setting | Purpose |
| :--- | :--- |
| **PvP** | Allow or prevent player combat inside the claim. |
| **Mob Protection** | Restrict hostile mobs in protected areas. |
| **Fire and Explosions** | Reduce griefing risks from fire, TNT, and creepers. |
| **Containers and Interactions** | Control access to chests, doors, switches, blocks, and other interactions. |
| **Shop Interact** | Allow market or TradeStall interactions where supported. |
| **Safe Zone** | Apply additional visitor-safety rules to the claim. |

> Some advanced settings, including flight-related controls, may require progression, permissions, or staff access.

### Claim Status

**Claim Status** on the Territory row is a snapshot of the plot you are standing in: owner, protections, growth, ClaimBlocks, and access. Stand inside a plot to open it.

### Roles and Members

Open **Roles** from the management menu to control access to your claim.

You can:

- Add or remove trusted players.
- Assign roles such as **Member**, **Guest**, or **Helper**.
- Fine-tune which actions each role can perform.
- Restrict unwanted players from entering the claim boundary.

Use roles and claim settings together to create clear access rules for friends, tenants, visitors, and community members.

### Guest Passes

**Guest Passes** grant temporary, self-expiring access without changing permanent roles.

From `/ag menu`, open **Guest Passes** while standing in a claim you manage to:

- Issue a pass to a nearby player.
- Choose a preset such as Visitor, Event Guest, Temporary Builder, or Temporary Trusted Guest.
- Set a duration and confirm the grant.
- View or revoke active passes.

Guest Passes expire automatically, even after a server restart, and never overwrite permanent trust.

### Realm Profile and Noticeboard

Use **Realm Profile** to manage your claim's public identity:

- Plot name and category
- Greeting / welcome presentation
- Noticeboard posts for visitors

Players browsing travel destinations may see profile details and noticeboard previews where the server allows discovery.

Useful commands:

| Command | Action |
| :--- | :--- |
| `/ag profile` | Open Realm Profile for the current plot. |
| `/ag rename <name>` | Rename the current plot. |
| `/ag setdesc <text>` | Set the public description. |
| `/ag notice add <text>` | Post a noticeboard entry. |
| `/ag notice list` | List noticeboard entries. |
| `/ag notice remove <#>` | Remove a notice by number. |

### Alliance Access

**Alliance Access** lets allied players share optional plot permissions. Membership alone grants nothing.

1. Create or join an alliance with `/ag alliance ...`.
2. Stand in a claim you manage and open **Alliance Access** from `/ag menu`.
3. Join the plot to your alliance.
4. Opt in only the toggles you want.

| Toggle | Effect when enabled |
| :--- | :--- |
| **Enter** | Allies may enter a private plot. |
| **Interact** | Allies may use doors and controls. |
| **Containers** | Allies may open containers. |
| **Build** | Allies may build and break. |
| **Animals** | Allies may use animals and farms. |
| **Friendly PvP** | Allies cannot hurt each other when plot PvP is otherwise open. |

All risky toggles default **OFF**. Alliance Access never grants ownership, money control, rentals, or management rights.

| Command | Action |
| :--- | :--- |
| `/ag alliance create <name>` | Form a new alliance. |
| `/ag alliance invite <player>` | Invite a player (leader). |
| `/ag alliance accept` | Accept a pending invite. |
| `/ag alliance leave` | Leave your alliance. |
| `/ag alliance disband` | Disband your alliance (leader). |
| `/ag alliance menu` | Open Alliance Access. |
| `/ag alliance status` | View alliance summary. |

### Emergency Lockdown

**Emergency Lockdown** is a fast, reversible safety switch for griefing, disputes, or maintenance.

Open it from `/ag menu` while standing in a claim you manage. While active, sensitive actions are restricted until you lift the lockdown. It does not delete the claim or permanent member roles.

---

## Subplots and Zones

Subplots allow you to divide a larger claim into smaller managed areas, including rental rooms, market stalls, shared base sections, hotel spaces, and private quarters.

### Create a Subplot

1. Stand inside a claim you manage.
2. Use the **Aegis Scepter** to select two corners within that claim.
3. Run one of the following commands:

```text
/ag subplot <name>
/ag subzone <name>
```

### Zone Manager

Run the following command to open the Zone Manager:

```text
/ag zone
```

The Zone Manager can be used to manage existing subplots and may support:

- Rental rooms and hotel-style spaces.
- Rentable market areas and TradeStalls.
- Guest and tenant access controls.
- Room-specific permissions and interaction settings.
- Shared base sections and community spaces.

**My Rentals** and **My Tenants** (when those modules are on) gather your contracts and renters in one place from the economy row or Local Market.

Available zone features depend on the server configuration.

---

## Group Claims

Group claims allow multiple players to build together, manage shared land, and contribute to a unified treasury.

| Command | Action |
| :--- | :--- |
| `/ag group create <name>` | Establish a new group identity. |
| `/ag group invite <player>` | Invite a player to your group. |
| `/ag group deposit <amount>` | Contribute funds to the shared treasury. |
| `/ag group claim` | Claim land under the group's ownership. |

Use group claims when a town, guild, building team, or community project needs shared ownership, collaborative resources, and group-level land management.

> Group claims are separate from **Alliance Access**. Groups share ownership and treasury. Alliances only share the opt-in plot permissions you enable.

---

## Territory Progression

### Plot Ascension

Plot Ascension allows your claim to level up and unlock enhanced capabilities over time.

```text
/ag level
```

Use the Plot Ascension menu to review your current tier, active benefits, upgrade requirements, and future rewards.

Possible progression benefits include:

- Additional member-capacity limits.
- Stronger claim perks and defense mechanics.
- Progression-based territory advantages.
- Access to advanced server-configured features.

### Frontier Expansion

Frontier Expansion is AegisGuard's claim-growth system.

Use the **Expansion** menu to review and apply for available growth options:

- Request additional land area.
- Review next-tier plot sizes.
- View costs and expansion requirements.
- Track pending expansion requests.

Expansion limits, requirements, and costs are configured by the server. If Expansion is turned off, that icon is not on the menu.

---

## Travel, Routes, and Navigation

AegisGuard includes built-in navigation tools so you can move between your claims and approved destinations easily.

| Command | Action |
| :--- | :--- |
| `/ag setspawn` | Set your claim's default teleport point. |
| `/ag home` | Teleport directly to your claim home. |
| `/ag visit <player or claim>` | Visit public plots or trusted player claims. |

The **Travel Menu** may provide access to:

- Your personal claims.
- Trusted player plots.
- Public server waypoints.
- Rental zones.
- Market destinations.
- Other server-approved locations.

### Routes and Checkpoints

If your server publishes exploration routes, open **Routes** from `/ag menu` to:

- Browse named routes.
- See your next checkpoint.
- Track discovery progress.
- Earn optional completion rewards when configured.

Routes are discovery-focused. They do not change claim boundaries, and optional teleports are only available if the server enables them.

---

## Economy, Markets, and TradeStalls

### Market Commands

| Command | Action |
| :--- | :--- |
| `/ag market` | Open the global market menu. |
| `/ag market local` | Access the local market for the current plot or zone. |

### Selling Land

| Command | Action |
| :--- | :--- |
| `/ag sell <price>` | List your active claim for sale. |
| `/ag unsell` | Remove your claim listing from the market. |
| `/ag merge` | Combine adjacent owned claims when the server allows it. |

### TradeStalls

TradeStalls provide protected, plot-based shopfronts where players can browse items through a dedicated interface.

Depending on server configuration, TradeStalls may support:

- Server economy currency.
- **ClaimBlocks**.
- Compatible external shop plugins.
- Rental stalls and market zones.

### ClaimBlocks

ClaimBlocks can be used for land expansion, claim upgrades, and economic trading.

| Command | Action |
| :--- | :--- |
| `/ag blocks` | View your current ClaimBlock balance. |
| `/ag blocks buy <amount>` | Purchase additional ClaimBlocks. |
| `/ag blocks sell <amount>` | Sell unused ClaimBlocks. |
| `/ag blocks earnings <on, off, or status>` | Manage passive earning tracking. |
| `/ag giftblocks` | Gift available ClaimBlocks to another player. |
| `/ag ledger` | Review your ClaimBlock transaction history. |

Some servers may also allow ClaimBlocks to be earned through playtime or other configured activities.

### Arenas

If the Arena module is on, `/ag arena` (or **Arenas** on the Explore row) lets you join cooperative dungeon runs and parties. If it is off, that icon is not on the menu.

---

## Security and Moderation

Use these tools to manage unwanted visitors and secure your territory.

| Command / Tool | Action |
| :--- | :--- |
| `/ag kick <player>` | Remove a player from your claim. |
| `/ag ban <player>` | Prevent a player from entering your claim. |
| `/ag unban <player>` | Remove an entry ban from a player. |
| **Guest Passes** | Grant temporary access that expires automatically. |
| **Emergency Lockdown** | Quickly restrict sensitive actions during an incident. |
| **Alliance Access** | Share limited ally permissions only where you opt in. |

> **Best practice:** Combine moderation commands with **Roles**, **Claim Settings**, **Guest Passes**, and **Lockdown** through `/ag menu` to control entry, combat, and interaction rules over the long term.

When an action is blocked, AegisGuard often explains the next step — for example, asking for a Guest Pass, waiting for lockdown to end, or requesting trust from the owner.

---

## Preferences and Guidance

Open **Settings** from `/ag menu` to manage personal preferences such as:

- Language (Choose Your Language lists every installed pack)
- Greetings and notifications
- Sound feedback
- Repeat notification behavior
- Replaying the first-claim walkthrough

| Command | Action |
| :--- | :--- |
| `/ag guide` | Open or replay the first-claim walkthrough. |
| `/ag notify ...` | Adjust notification preferences. |
| `/ag menu` | Open the Guardian Codex and Settings. |

---

## In-Game Help and Resources

Need help while playing?

1. Run `/ag menu`.
2. Select the **Guardian's Guide** or **Info Book**.

The guide contains step-by-step information for AegisGuard systems, including:

- Claiming land.
- Managing settings and roles.
- Guest Passes, Lockdown, and Alliance Access.
- Realm Profiles and noticeboards.
- Routes and checkpoints.
- Subplots and zones.
- Group claims.
- Territory progression.
- Travel and navigation.
- Economy and ClaimBlocks.
- Markets and TradeStalls.
- Security and moderation.

Guide chapters for optional systems are hidden when those modules are off.

---

> *Simple. Steadfast. Eternal.*
