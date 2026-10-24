# AegisGuard 1.4.0

![AegisGuard Header](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/listing/aegisguard-spigot-header-v2.png)

### _Protect your world. Empower your players. Ascend._

**⚡ Folia Supported** • **Player-Owned Territories** • **Economy & Progression**

[Read the Wiki](https://github.com/snazzyatoms/AegisGuard/wiki) | [Source Code](https://github.com/snazzyatoms/AegisGuard) | [Releases](https://github.com/snazzyatoms/AegisGuard/releases)

***

## THE LIVING REALM UPDATE

**AegisGuard 1.4.0** is the biggest release yet — protected land is no longer just safe, it's alive. Host events on your claim, run party dungeons, name an heir to your realm, chat on private plot frequencies, and travel across entire BungeeCord/Velocity networks.

It runs on **Paper, Spigot, Purpur, and Folia** with **Java 21+** and **Minecraft 1.20+**. Existing **1.2.7** and **1.3.x** data and configuration remain valid through automatic schema migration — upgrading is a JAR swap followed by `/agadmin transition`.

***

## HEADLINE FEATURES

### 🏰 Arena Dungeons

Turn any map into a PvE/PvP arena run. Define bounds with the Aegis Scepter, party up, gather in staging areas, launch together, and return safely when the run ends.

*   `/ag arena` — browse, join, and party for runs
*   PvE waves, boss finales, and opt-in PvP (FFA / duels)
*   Custom floors and WorldEdit/FAWE schematics with instanced pastes
*   Matchmaking queues, sealed runs, and a ready-made `lava_dungeon` preset
*   Steward and admin roles for arena management

### 🐫 Trade Caravans

Dispatch charge-then-deliver caravans between linked Teleport Beacon pads — with insurance, weighted route events, Folia-safe travel, and resume-after-restart. Hire an online player as **escort** to halve ambush risk for a configurable cut. `/ag caravan`

### 🏠 Gatherings & Open Houses

Host timed open-house events on plots you manage, or browse what the community is running. Arrivals can receive a visitor Guest Pass that expires when the event ends. `/ag gathering` • `/ag openhouse`

### 👑 Realm Succession

Name an heir, manage the handover, or transfer ownership outright — your realm outlives you.

*   `/ag heir` — name a successor
*   `/ag succession` — inactivity assume, transfer cooldown, rollback window
*   `/ag transfer` — pass ownership to another player

### 📻 Plot Chat Frequencies

Per-plot chat channels plus alliance, group, and staff radios — one channel per player, pulled cleanly out of public chat. No client mod needed; Java and Bedrock players use the same commands.

*   `/ag chat` — plot frequency (alliance and group channels included)
*   `/ag staff` — staff radio

### 🌐 Cross-Server Travel

BungeeCord/Velocity support with network hubs, a server selector GUI, Safe Travel arrivals, remote arena joins, and staff broadcasts.

*   `/ag hub` • `/ag lobby` • `/ag servers`

### 💱 ClaimBlocks Exchange

Buy and sell ClaimBlocks with configurable rates, cooldowns, and sell locks — plus a full transaction ledger.

*   `/ag blocks buy|sell <amount>` • `/ag ledger` • `/ag giftblocks`

***

## ALSO NEW IN 1.4.0

*   Realm noticeboards, plot profiles (`/ag profile`), and subplots
*   Visitor arrival styles — classic teleport or public beacon pad (`/ag arrival`)
*   Per-plot biome picker with optional Vault fee (`/ag biome`)
*   New protection wards: **Mob Griefing** (endermen, tramples, falling blocks) and **Interactables** (bells, jukeboxes, lecterns, beehives, and more) — plus cross-border liquid/sponge/dispenser fixes
*   Server sanctuary flags — Keep Health, Hunger, XP, and Inventory on server zones
*   Plot flight unlocked through Ascension progression; staff flight grants via `/agadmin skill`
*   Claim presets and staff-curated seasons (`/agadmin season`)
*   Discord integration — account linking and event webhooks
*   Settlements inbox for rental and market payouts
*   **Nine language packs** — every player-facing message translated, `{KEY}` placeholders resolved on translations, plain-English console logs
*   Full `/ag help` coverage — every command documented in-game

***

## SEE AEGISGUARD IN ACTION

### 🏡 CLAIM YOUR TERRITORY

Select two corners with the Aegis Scepter, confirm the claim, and protect your land in seconds.

![Claim your territory](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/01-claim-your-territory.gif)

### 🧭 COMMAND YOUR REALM

Manage territory identity, guidance, members, notices, and everyday actions from a clear player dashboard.

![Command your realm](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/02-dashboard-and-profile.gif)

### 🛡️ PROTECT, SHAPE & EXPAND

Configure protections, create specialized zones, and grow beyond your original borders.

![Protection settings](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/03-protection-settings.gif)
![Expansion and zones](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/04-expansion-and-zones.gif)

### 🤝 BUILD A TRUSTED COMMUNITY

Create custom roles, issue temporary Guest Passes, connect alliances, name an heir, and secure territories with Emergency Lockdown.

![Roles and guest passes](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/05-roles-and-guest-passes.gif)
![Alliance and lockdown](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/06-alliance-and-lockdown.gif)

### 💰 CREATE A LIVING LAND ECONOMY

Turn territory into opportunity through ClaimBlocks, markets, rentals, progression, and expansion. Extra land is refused if the wallet cannot cover it — ClaimBlocks stay honest.

![Markets and ClaimBlocks](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/07-markets-and-claimblocks.gif)
![Progression and rentals](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/08-progression-and-rentals.gif)

### 🌐 CONNECT THE ENTIRE SERVER

Bring territories into the wider world with travel, Teleport Beacons, arenas, caravans, discovery, player preferences, and staff operations. `/ag beacon` places linked pads on claims you manage. `/ag arena` launches dungeon runs with your party.

![Travel, arena, and staff](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/V1.4.0/media/spigot/09-travel-arena-and-staff.gif)

***

## FOR SERVER OWNERS

*   Every new system is a **module** — disable it in `modules:` and it vanishes from menus
*   Feature-gated permissions actually enforce now (`aegis.wand`, `aegis.claim`, `aegis.gathering`, and more)
*   Documented public API — `AegisGuard-1.4.0-api.jar` ships with each release
*   Config comments rewritten — every option explains its units, defaults, and dependencies
*   Verified on a live multi-region **Folia** rig: cross-region mob purge, Vault charge/refund on denied claims, region-safe stall purchases — zero scheduler violations
*   Fixed: Vault claim charge is now refunded if validation rejects the claim

***

## COMMANDS

### ✅ Player Commands

```
/ag wand                     - Receive the Aegis Scepter
/ag claim                    - Claim selected land
/ag quickclaim [radius]      - Claim a square area around you, no wand needed
/ag unclaim                  - Remove the current plot
/ag menu                     - Open the territory dashboard
/ag travel                   - Open the Travel Hub
/ag visit                    - Open the Travel Atlas
/ag beacon                   - Manage Teleport Beacons
/ag arrival <classic|beacon> - Choose how visitors arrive
/ag hub /ag lobby            - Travel to the network hub
/ag servers                  - Open the cross-server selector
/ag arena                    - Browse, join, and party for arena runs
/ag caravan                  - Dispatch a trade caravan
/ag gathering                - Host or browse open-house gatherings
/ag chat                     - Toggle plot chat (alliance/group channels too)
/ag staff                    - Toggle the staff chat channel
/ag heir <player>            - Name an heir to your plot
/ag succession               - Manage succession and handover
/ag transfer <player>        - Transfer plot ownership
/ag roles                    - Manage custom plot roles
/ag profile                  - Open your realm profile
/ag notice                   - Manage the plot noticeboard
/ag setspawn /ag home        - Plot home point and travel
/ag welcome /ag farewell     - Entry and exit messages
/ag rename /ag setdesc       - Plot identity
/ag biome                    - Pick a custom plot biome
/ag level                    - Open the Ascension Hall
/ag zone /ag subplot         - Sub-zones and rental tools
/ag rent /ag unrent          - Rental listings
/ag sell /ag unsell          - Sale listings
/ag market /ag auction       - Markets and auctions
/ag blocks buy|sell          - ClaimBlocks exchange
/ag ledger                   - ClaimBlock transaction history
/ag giftblocks               - Gift ClaimBlocks to another player
/ag settlements              - Settlements inbox
/ag group /ag alliance       - Group plots and alliances
/ag discover                 - Browse public territories
/ag like /ag favorite        - Rate and favorite plots
/ag notify                   - Notification controls
/ag stuck                    - Move to a safe nearby location
/ag kick /ag ban /ag unban   - Plot access enforcement
/ag guide                    - Replay the first-claim walkthrough
```

### 👑 Admin Commands

```
/agadmin menu                - Open the Staff Command Center
/agadmin reload              - Reload config and language packs
/agadmin transition          - Confirm 1.2.7/1.3.x → 1.4.0 upgrade (aliases: upgrade, v130, v140)
/agadmin health              - Quick staff health check
/agadmin doctor              - Diagnostics and repair tools
/agadmin bypass              - Toggle protection bypass
/agadmin wand server         - Receive the server-zone wand
/agadmin claim               - Create a server-owned protected zone
/agadmin manage /agadmin convert - Server territory tools
/agadmin blocks              - View or manage player ClaimBlocks
/agadmin merge               - Merge eligible server zones
/agadmin rentals             - Manage rental contracts and settlements
/agadmin discover            - Moderate public discovery listings
/agadmin activity            - Inspect territory activity
/agadmin audit               - Staff Audit Ledger
/agadmin plots <player>      - Browse a player's plots
/agadmin inspect [player]    - Inspect a claim in detail
/agadmin season              - Staff season featured plots and routes
/agadmin skill fly <player>  - Grant temporary flight
/agadmin staffchat           - Toggle staff radio (alias: sc)
/agadmin discord             - Discord account links
/agadmin network             - Cross-server find and broadcast
/agadmin snapshot /agadmin restore - Territory recovery
/agadmin migrate             - Import supported external claims
```

***

## PERMISSIONS (QUICK REFERENCE)

```
aegis.user                   - Recommended player bundle
aegis.admin                  - Recommended admin bundle
aegis.use / aegis.menu       - /ag command and dashboard
aegis.wand / aegis.claim     - Scepter and claiming
aegis.chat / aegis.chat.alliance / aegis.chat.group - Chat channels
aegis.gathering              - Host open-house events
aegis.arena.use / aegis.arena.spectate - Arena participation
aegis.arena.steward / aegis.arena.admin - Arena management
aegis.beacon                 - Teleport Beacon pads
aegis.claimblocks.*          - Exchange, buy, sell, gift, bypass nodes
aegis.admin.*                - Staff tools (see plugin.yml for the full list)
```

***

## INSTALLATION

1.  Stop the server completely.
2.  Confirm the host runs **Java 21** or newer.
3.  Place **AegisGuard-1.4.0.jar** in your `/plugins` folder.
4.  Install **Vault** plus a supported economy provider for money-based features.
5.  Start the server — config and language merge run automatically; existing plots load as-is.
6.  Confirm the upgrade with `/agadmin transition`.
7.  Run `/agadmin doctor` only if something looks wrong.

**Do not use Bukkit's global `/reload` command.** Use `/agadmin reload` for AegisGuard settings, and fully restart after changing storage or integrations.

**Supported:** Minecraft 1.20+ • Java 21+ • Spigot, Paper, Purpur, Folia • Vault • BungeeCord/Velocity networks • Dynmap, BlueMap, Pl3xMap • PlaceholderAPI • WorldEdit/FAWE (optional plot backups) • Geyser/Floodgate (Bedrock GUIs) • Simple Voice Chat (optional Hearth rooms)

**For developers:** use `AegisGuard-1.4.0-api.jar` as a compile-time dependency — it does not belong in `/plugins`. Public events and service accessors live under `com.aegisguard.api` with Javadocs.

***

## FEEDBACK & DOCUMENTATION

*   **Wiki:** [github.com/snazzyatoms/AegisGuard/wiki](https://github.com/snazzyatoms/AegisGuard/wiki)
*   **Issues:** [github.com/snazzyatoms/AegisGuard/issues](https://github.com/snazzyatoms/AegisGuard/issues)
*   **Discord:** [discord.gg/nHwdhKzeKR](https://discord.gg/nHwdhKzeKR)

### _Forged by Aegis Divine — Simple. Steadfast. Eternal._
