# AegisGuard API

This page documents the public integration API for **AegisGuard** `v1.3.0`.

The API is intended for plugin developers who need to read plot data, perform supported economy and ClaimBlock operations, manage selections, check protection state, or listen for plot events.

> **Scope:** This page focuses on runtime integration. Use the public API facade rather than internal managers so integrations remain compatible as AegisGuard evolves.

> **1.3.0 note:** Guest Passes, Alliance Access, Emergency Lockdown, Realm Profiles, Routes, and the Staff Audit Ledger are part of the plugin runtime. For access checks, continue to use `Plot.hasPermission(...)` and the public protection helpers rather than reimplementing those systems. Existing 1.2.7 integrations that already use the public facade remain the supported approach.

---

## Adding the API to Your Plugin

Compile against `AegisGuard-1.3.5-api.jar` produced from the `V1.3.5` source branch. Do **not** put the API jar in a Minecraft server's `/plugins` folder. Server owners install only `AegisGuard-1.3.5.jar`. Published artifacts will be attached separately when a GitHub Release is intentionally created.

Build with **Java 21**. Add AegisGuard as a softdepend in `plugin.yml`:

```yaml
softdepend: [AegisGuard]
```

---

## Public API Entry Point

Use `AegisGuardProvider` to resolve the live API instance.

```java
import com.aegisguard.api.AegisGuardAPI;
import com.aegisguard.api.AegisGuardProvider;

AegisGuardAPI api = AegisGuardProvider.get();

if (api == null) {
    // AegisGuard is not installed, not enabled, or its API is unavailable.
    return;
}
```

You can also use the optional helper:

```java
AegisGuardProvider.optional().ifPresent(api -> {
    // Use the AegisGuard API here.
});
```

Check whether the API is currently available:

```java
if (AegisGuardProvider.isAvailable()) {
    // AegisGuard is enabled and the public API is available.
}
```

---

## API Services

The `AegisGuardAPI` interface is the supported integration facade.

| Method | Purpose |
| :--- | :--- |
| `plugin()` | Returns the live AegisGuard plugin instance. |
| `version()` | Returns the active AegisGuard version string. |
| `plots()` | Provides plot lookup, listing, overlap, and save operations. |
| `claimBlocks()` | Provides ClaimBlock balance and transaction operations. |
| `economy()` | Provides supported economy operations and currency formatting. |
| `selections()` | Provides selection and supported claim-confirmation operations. |
| `protection()` | Provides supported flag, mob-protection, and safe-zone checks. |

```java
AegisGuardAPI api = AegisGuardProvider.get();

if (api != null) {
    String version = api.version();
    // Use the current AegisGuard version if needed.
}
```

There is no separate public module-switchboard service. If a server turns a module off, related menus and commands are hidden, but plot records and `hasPermission(...)` remain the source of truth for land access.

---

## Plot Access

Use `api.plots()` for supported plot lookups and persistence operations.

### Look Up a Plot at a Location

```java
import com.aegisguard.data.Plot;
import org.bukkit.Location;

Location location = player.getLocation();
Plot plot = api.plots().getPlotAt(location);

if (plot != null) {
    player.sendMessage("You are standing in a protected plot.");
} else {
    player.sendMessage("This land is unclaimed.");
}
```

### Look Up Plots by Owner or ID

```java
import java.util.UUID;

UUID ownerId = player.getUniqueId();

// All plots owned by a player.
var ownedPlots = api.plots().getPlots(ownerId);

// A specific plot owned by a player.
Plot ownedPlot = api.plots().getPlot(ownerId, plotId);

// A plot by its unique plot ID, regardless of owner.
Plot plotById = api.plots().getPlotById(plotId);
```

### Browse Plot Collections

```java
var allPlots = api.plots().getAllPlots();
var plotsForSale = api.plots().getPlotsForSale();
var plotsForAuction = api.plots().getPlotsForAuction();
var worldPlots = api.plots().getPlotsInWorld("world");
```

### Check for Overlap

```java
boolean overlaps = api.plots().isAreaOverlapping(
        null,
        "world",
        100, 100,
        120, 120
);

if (overlaps) {
    // The requested area overlaps an existing plot.
}
```

Pass an existing plot as the first argument when checking a resize or edit that should ignore that plot's own area.

### Save a Modified Plot

When an integration changes supported plot data directly, save it through the public plot service.

```java
Plot plot = api.plots().getPlotAt(player.getLocation());

if (plot != null) {
    plot.setFlag("pvp", false);
    api.plots().savePlot(plot);
}
```

Use `savePlotSync(plot)` only when a synchronous save is explicitly required. Prefer `savePlot(plot)` during normal gameplay operations.

---

## Plot Permissions and Protection

Use AegisGuard as the source of truth for claim access decisions. Do not recreate role, member, guest, Guest Pass, Alliance Access, or owner logic in an external plugin.

### Check a Player Permission in a Plot

```java
Plot plot = api.plots().getPlotAt(block.getLocation());

if (plot != null) {
    boolean canBuild = plot.hasPermission(
            player.getUniqueId(),
            "BUILD",
            api.plugin()
    );

    if (canBuild) {
        // The player has permission to build in this plot.
    } else {
        // The player does not have permission to build in this plot.
    }
}
```

In `v1.3.0`, `hasPermission(...)` already accounts for:

- Owner and elevated admin/bypass access
- Permanent plot roles
- Full-plot renter permissions where configured
- Active **Guest Passes**
- Opt-in **Alliance Access** grants for the plot

Alliance Access never grants `MANAGE` or `MANAGE_MEMBERS`. Guest Passes never overwrite permanent roles.

### Alliance Entry and Friendly PvP Helpers

When an integration needs the same decisions AegisGuard uses for private-plot entry or ally-safe PvP:

```java
Plot plot = api.plots().getPlotAt(player.getLocation());

if (plot != null) {
    boolean allianceEntry = plot.allowsAllianceEntry(
            player.getUniqueId(),
            api.plugin()
    );

    boolean friendlyAllies = plot.areAllianceAllies(
            attackerId,
            victimId,
            api.plugin()
    );
}
```

Both helpers default to denied. They require the plot to have joined an alliance and the matching toggle to be enabled.

### Check Supported Protection State

```java
Plot plot = api.plots().getPlotAt(player.getLocation());

if (plot != null) {
    boolean pvpEnabled = api.protection().isFlagEnabled(plot, "pvp");
    boolean mobsProtected = api.protection().isMobProtectionEnabled(plot);
    boolean safeZone = api.protection().isSafeZoneEnabled(plot);
}
```

### Toggle a Safe Zone

```java
Plot plot = api.plots().getPlotAt(player.getLocation());

if (plot != null) {
    api.protection().toggleSafeZone(plot, true);
    api.plots().savePlot(plot);
}
```

### Emergency Lockdown

Emergency Lockdown is a plot-level safety state managed by AegisGuard. Integrations that perform sensitive actions on player land should treat an active lockdown as a reason to deny or defer non-essential edits unless the actor is an authorized admin bypassing protection.

```java
Plot plot = api.plots().getPlotAt(player.getLocation());

if (plot != null && plot.isLockdownActive()) {
    // Defer non-essential edits unless this actor is an authorized bypass.
}
```

Prefer AegisGuard's own protection and management flows over inventing a second lockdown system.

---

## ClaimBlocks API

Use `api.claimBlocks()` for supported ClaimBlock balances, affordability checks, and balance changes.

```java
import java.util.UUID;

UUID playerId = player.getUniqueId();

long total = api.claimBlocks().getTotalBlocks(playerId);
long used = api.claimBlocks().getUsedBlocks(playerId);
long available = api.claimBlocks().getAvailableBlocks(playerId);
```

### Spend or Refund ClaimBlocks

```java
UUID playerId = player.getUniqueId();
long cost = 250L;

if (api.claimBlocks().canAfford(playerId, cost)) {
    boolean spent = api.claimBlocks().spend(playerId, cost);

    if (spent) {
        // Complete the purchase or reward flow.
    }
}

// Refund a previously charged amount when appropriate.
api.claimBlocks().refund(playerId, 250L);
```

### Award ClaimBlocks

```java
UUID playerId = player.getUniqueId();

api.claimBlocks().addEarned(playerId, 50L);
api.claimBlocks().addBonus(playerId, 100L);
api.claimBlocks().addBought(playerId, 500L);
api.claimBlocks().addBoughtFromExchange(playerId, 500L);
```

### Manage Playtime Earnings

```java
boolean enabled = api.claimBlocks().isPlaytimeEarningEnabled(player.getUniqueId());
api.claimBlocks().setPlaytimeEarningEnabled(player.getUniqueId(), false);
```

Call `invalidateOwnerCache(playerId)` only when an external integration has a valid reason to force a ClaimBlock owner-cache refresh.

Optional route-completion rewards in 1.3.0 may award Vault money or ClaimBlocks through AegisGuard itself. External plugins should not double-pay for the same discovery event unless that is an intentional custom design.

---

## Economy API

Use `api.economy()` for supported currency checks, withdrawals, deposits, balances, and formatted output.

Available currency types are:

- `CurrencyType.VAULT`
- `CurrencyType.CLAIM_BLOCKS`
- `CurrencyType.EXP`
- `CurrencyType.LEVEL`
- `CurrencyType.ITEM`

### Check and Withdraw Currency

```java
import com.aegisguard.economy.CurrencyType;

double price = 100.0D;

if (api.economy().isVaultReady()
        && api.economy().has(player, price, CurrencyType.VAULT)) {

    boolean withdrawn = api.economy().withdraw(player, price, CurrencyType.VAULT);

    if (withdrawn) {
        // Complete the purchase or service.
    }
}
```

### Deposit and Format Currency

```java
import com.aegisguard.economy.CurrencyType;

api.economy().deposit(player, 25.0D, CurrencyType.VAULT);

double balance = api.economy().getBalance(player, CurrencyType.VAULT);
String formatted = api.economy().format(balance, CurrencyType.VAULT);
```

Alliance Access never exposes treasury, rental, or market ownership through permission grants. Keep money and rental flows on the economy, market, and group systems.

---

## Selection API

Use `api.selections()` to work with the current player selection through the supported selection service.

```java
import org.bukkit.Location;

Location firstCorner = new Location(player.getWorld(), 100, 64, 100);
Location secondCorner = new Location(player.getWorld(), 120, 64, 120);

api.selections().setLoc1(player, firstCorner);
api.selections().setLoc2(player, secondCorner);

if (api.selections().hasSelection(player)) {
    long area = api.selections().getSelectionArea(player);
    // Review the selected area before confirming a claim.
}
```

### Confirm a Claim

```java
// Confirm a standard player claim using the current selection.
api.selections().confirmClaim(player);

// Confirm a server claim using the current selection.
api.selections().confirmClaim(player, true);
```

### Clear or Resize a Selection

```java
api.selections().clearSelection(player);

// Use a supported resize mode and amount.
api.selections().resizePlot(player, "north", 5);
```

---

## Event API

Custom events are located in:

```text
com.aegisguard.api.events
```

All plot events expose common plot information through their shared event base classes.

| Method | Available On | Purpose |
| :--- | :--- | :--- |
| `getPlot()` | All plot events | Returns the affected `Plot`. |
| `getPlotId()` | All plot events | Returns the plot UUID. |
| `getOwnerId()` | All plot events | Returns the plot owner UUID. |
| `getWorldName()` | All plot events | Returns the plot world name. |
| `getPlayer()` | Player plot events | Returns the player involved in the event. |
| `getPlayerId()` | Player plot events | Returns the involved player's UUID. |

### PlotClaimEvent

`PlotClaimEvent` is fired when a player attempts to create a plot through the supported claim flow.

- **Cancellable:** Yes.
- **Timing:** Fired before the plot is added to the store.
- **Use Cases:** Restrict claims in custom areas, enforce custom limits, or charge external currencies.

### PlotDeleteEvent

`PlotDeleteEvent` is fired when a plot is removed by a player or administrator.

- **Cancellable:** No.
- **Timing:** Fired after the plot is removed from AegisGuard's in-memory indexes and before storage processing completes.
- **Use Cases:** Record audit data, remove external visuals, or perform external cleanup.

### PlotEnterEvent

`PlotEnterEvent` is fired when a player crosses into a different plot.

- **Cancellable:** Yes.
- **Timing:** Fired before the movement transition completes.
- **Cancellation Behavior:** Cancelling the event cancels the underlying movement transition.
- **Use Cases:** Restrict entry, enforce custom access rules, apply entry fees, or play custom effects.

> AegisGuard itself already evaluates private-plot entry, permanent trust, Guest Passes, and Alliance Entry. Prefer those systems for normal access control, and use `PlotEnterEvent` for custom add-on rules.

### PlotLeaveEvent

`PlotLeaveEvent` is fired when a player leaves a plot.

- **Cancellable:** No.
- **Use Cases:** Remove external effects, send messages, or clear custom scoreboards.

### PlotLevelUpEvent

`PlotLevelUpEvent` is fired during a successful Plot Ascension upgrade.

- **Cancellable:** No.
- **Use Cases:** Broadcast achievements, award external rewards, or unlock third-party features.
- **Important:** Use `getNewLevel()` for the target level. The event is fired before the plot's stored level is updated.

---

## Example Event Listener

Register Bukkit listeners normally in your plugin. The following example blocks entry for players without a custom permission and rewards a Plot Ascension milestone.

```java
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLevelUpEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class MyAegisListener implements Listener {

    @EventHandler
    public void onPlotEnter(PlotEnterEvent event) {
        if (!event.getPlayer().hasPermission("myplugin.plot.entry")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("You need permission to enter this area.");
        }
    }

    @EventHandler
    public void onPlotLevelUp(PlotLevelUpEvent event) {
        if (event.getNewLevel() == 50) {
            Bukkit.broadcastMessage(
                    event.getPlayer().getName() + " reached Plot Ascension level 50."
            );
        }
    }
}
```

---

## Developer Guidelines

- Resolve AegisGuard through `AegisGuardProvider`; do not depend on internal manager classes.
- Compile against `AegisGuard-1.3.5-api.jar`. Never ship the API jar as a server plugin.
- Check for API availability before using AegisGuard services.
- Use `api.plots()` for plot lookups and saves.
- Use `plot.hasPermission(..., api.plugin())` for plot access checks rather than duplicating AegisGuard permission logic.
- Treat Guest Passes and Alliance Access as additive AegisGuard grants already reflected by `hasPermission(...)` and the alliance helper methods.
- Check `plot.isLockdownActive()` before non-essential land edits.
- Do not grant ownership, treasury, rental, or management rights through custom alliance or guest logic.
- Use public services for ClaimBlocks, economy, selections, and protection state.
- Keep event handlers fast. Do not perform long-running database or network work on the server thread.
- Test integrations on a staging server before deploying them to production, including private-plot entry and open-plot PvP with Alliance toggles both OFF and ON.

---

> *Simple. Steadfast. Eternal.*
