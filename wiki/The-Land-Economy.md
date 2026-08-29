# The Land Economy

In **AegisGuard** `v1.4.0`, land is more than protected space. Depending on server configuration, it can be expanded, maintained, sold, rented, shared, and traded through connected land and economy systems.

The AegisGuard land economy can include:

- ClaimBlocks (including gifts and exchange).
- Upkeep and tax-style systems.
- Direct market sales.
- Adjacent claim merge.
- Auctions.
- Subplots and rentals.
- Group treasuries.
- TradeStalls.
- Local Market access.
- Optional Teleport Beacon travel fees and owner payouts.
- Optional route-completion rewards where enabled.

> **Server configuration:** Economy features, costs, limits, permissions, and currencies are controlled by the server owner. Not every system is enabled on every server. If an economy icon is missing from `/ag menu`, that module is turned off.

> **Upgrade note:** Existing 1.2.7 and 1.3.0 economy data and configuration remain valid in 1.3.5. Teleport Beacons, Guest Passes, Lockdown, Realm Profiles, Routes, and Alliance Access do not take ownership of plots, treasuries, rentals, or market listings.

---

## Economy Models

AegisGuard supports flexible land-economy setups. A server may use one or more of the following systems.

| Economy Model | Typical Uses |
| :--- | :--- |
| **Vault Money** | Claiming, expansion, markets, upkeep, optional beacon fees, optional route rewards, and other configured land costs. |
| **ClaimBlocks** | Land progression, claim costs, exchanges, gifts, TradeStalls, and related systems. |
| **Group Treasury** | Shared group claims, cooperative growth, and group-funded expenses. |
| **TradeStalls** | Player storefronts using server money or ClaimBlocks where supported. |

Use `/ag menu` and the available economy menus to see which systems are active on your server.

---

## ClaimBlocks

ClaimBlocks are a core land-progression and trading resource in AegisGuard `v1.4.0`.

They may be used for:

- Occupying land (counted as **used** plot area).
- Paying configured non-land costs (beacon fees, exchange, and similar **spent** amounts).
- Progressing through server-configured growth systems.
- Buying and selling through the ClaimBlocks Exchange.
- Gifting unused blocks to another player.
- Acting as a TradeStall currency on supported servers.
- Receiving optional route-completion rewards when the server enables them.

Available ClaimBlocks are `total − used − spent`, never below zero. Expanding a plot is refused if you cannot cover the extra area. Group claims check the **leader's** ClaimBlocks. `claims.min_radius` still applies on both axes, so a tiny or skinny plot cannot be used to farm cheap land.

### ClaimBlocks Commands

```text
/ag ledger
/ag blocks
/ag blocks buy <amount>
/ag blocks sell <amount>
/ag blocks earnings <on|off|status>
/ag giftblocks
```

### ClaimBlocks Notes

- Some servers allow ClaimBlocks to be earned over time while playing.
- Some servers allow players to opt out of passive earnings.
- Servers can configure exchange cooldowns, sell locks, daily or hourly limits, and buy or sell permissions.
- Gift limits and permissions are server-controlled (`/ag giftblocks` or Gift ClaimBlocks on the menu).
- Use `/ag ledger` to review ClaimBlock transaction history.

---

## Upkeep and Taxes

Servers can enable **upkeep** to help keep land active and reduce abandoned claims. Upkeep ships **on** in the module switchboard; owners can still turn it off.

### How Upkeep Works

- Upkeep is charged automatically when the system is enabled.
- Claim owners may receive warnings when payment is due or fails.
- Group plots can notify group members about upcoming or unpaid upkeep.
- Claims may enter a grace or unpaid state when configured payment requirements are not met.

### What Upkeep Helps With

- Keeping inactive land from remaining claimed indefinitely.
- Encouraging active communities and responsible land ownership.
- Supporting abandoned-land recovery or auction flows on participating servers.

> **Important:** Upkeep is fully server-controlled. Some servers use it extensively, while others disable it entirely.

---

## Auction House

Some servers use the AegisGuard auction system to recycle expired, abandoned, or staff-managed land.

```text
/ag auction
```

Players may be able to:

- Browse active auction listings.
- Check current bids.
- Compete for land that is no longer under player control.

When configured by the server, auctioned land may include:

- Expired claims.
- Abandoned plots.
- Staff-managed land rotation.

If Auctions are turned off, that icon is not on the menu.

---

## Marketplace

AegisGuard supports direct land sales through the marketplace.

### Sell Your Plot

1. Stand inside the claim you want to list.
2. Run:

```text
/ag sell <price>
```

### Remove a Listing

```text
/ag unsell
```

### Merge Adjacent Claims

When claim merge is enabled:

```text
/ag merge
```

This combines adjacent owned claims into one larger plot. Staff can also merge eligible server zones.

### Browse Available Plots

```text
/ag market
```

When allowed by the server, players can use the market to:

- Browse claims for sale.
- Preview listed land.
- Purchase plots directly.
- Review public plot identity details such as Realm Profile information where available.

---

## Local Market

The **Local Market** system gives a claim or zone its own visitor-facing market experience.

```text
/ag market local
```

Depending on the plot and server configuration, Local Market features may include:

- Rentable rooms or stalls.
- TradeStalls.
- Plot-level market browsing.
- **My Rentals** and **My Tenants** hubs.
- External market-plugin bridges.

If the current plot qualifies for Local Market features, it can offer a more focused shop, rental, and visitor experience than the global market alone.

---

## TradeStalls

**TradeStalls** are AegisGuard's built-in protected shopfront system.

They are designed for servers that want plot-based player shops without requiring a separate third-party market plugin.

### Supported Currencies

TradeStalls can use:

- Server money.
- **ClaimBlocks**.

### Best Uses for TradeStalls

- Town stalls.
- Market districts.
- Room shops.
- Small player storefronts.
- Rentable merchant spaces within larger claims.

### External Shop Plugins

AegisGuard can coexist with, or defer to, external systems depending on server configuration, including:

- QuickShop.
- Shopkeepers.
- ChestShop.
- ExcellentShop.

---

## Subplots, Rentals, and Landlord Flow

Subplots allow a larger claim to become a structured economy space instead of a single private plot.

Common uses include:

- Apartments.
- Hotels.
- Rentable storage wings.
- Market booths.
- Event rooms.
- Managed guild or community spaces.

### Create a Subplot

1. Stand inside your claim.
2. Use the Aegis Scepter to select an area within that claim.
3. Run one of the following commands:

```text
/ag subplot <name>
/ag subzone <name>
```

The new area appears in the **Zone Manager**, where it can be configured for rental, access control, and other server-supported functions.

### Economic Opportunities

Subplots can support landlord-style gameplay and community development:

- Rent rooms.
- Create merchant areas.
- Manage hotel-style spaces.
- Collect rent where enabled.
- Use **My Rentals** to renew, extend, or cancel your contracts.
- Use **My Tenants** to review renters on plots you manage.
- Use managed areas for Local Market activity.

> Temporary **Guest Passes** can help contractors or event helpers work on a plot without becoming permanent tenants or trusted members. Guest Passes never transfer ownership or rental contracts.

---

## Group Treasury

Groups provide a shared economic foundation for towns, guilds, building teams, and other cooperative projects.

```text
/ag group create <name>
/ag group deposit <amount>
/ag group status
/ag group claim
```

The shared treasury can support:

- First group-plot claiming.
- Group land progression.
- Expansion-related costs.
- Other server-configured group expenses.

This allows group land to operate as a cooperative project rather than relying on one player to own and fund everything alone.

### Groups vs Alliances

| System | Economy role |
| :--- | :--- |
| **Group Claims** | Shared ownership and a shared treasury for cooperative land costs. |
| **Alliance Access** | Optional plot-permission sharing only. It never grants money, rentals, ownership, or management rights. |

Use groups when players need to fund and own land together. Use alliances when plots should share limited access without merging economies.

---

## Frontier Expansion

**Frontier Expansion** is AegisGuard's land-growth system. It provides a clear progression path for expanding territory.

Players may use it to:

- Request more land.
- Review the next expansion tier.
- Compare cost and size impact.
- Track pending expansion requests.

Frontier Expansion gives land growth a distinct progression identity while retaining the familiar purpose of expanding a claim.

### Expansion Horizons

After Plot Ascension Level 30, **Expansion Horizons** continues territory progression with Renown, named ranks, bound Sigils, controlled radius growth, and advanced plot abilities. Overlap checks, world limits, snapshots, and ownership validation still apply. If leveling or expansions are turned off, those menu entries are hidden.

---

## Routes and Optional Rewards

If the server enables **Routes and Checkpoints**, exploration can include small optional rewards such as:

- Server money.
- ClaimBlocks.

Rewards are configured by staff and are never required to browse or discover routes. Routes do not expand claims, sell land, or alter market listings.

---

## Teleport Beacon fees

Teleport Beacons can charge Vault money when the server allows it. `teleport_beacons.charges.mode` is:

| Mode | Meaning |
| :--- | :--- |
| `owner_choice` | Pads may be free or paid. |
| `always` | A server-wide fee applies. |
| `off` | No beacon travel charges. |

Optional maintenance fees can pay the plot owner. Beacon spend is **not** plot area; it does not double-count against ClaimBlocks used for land.

---

## Final Thoughts

In AegisGuard `v1.4.0`, land is not something you claim once and forget.

Land can become:

- A protected home.
- A shared group project.
- A rentable business hub.
- A market district.
- A hotel.
- A TradeStall economy.
- A public destination with a Realm Profile.
- A Teleport Beacon hub for Safe Travel.
- A long-term progression path.

That is the foundation of the AegisGuard land economy.

> *Simple. Steadfast. Eternal.*
