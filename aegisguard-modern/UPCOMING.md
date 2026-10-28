# 🔮 The Oracle: Roadmap to AegisGuard 2.0

## AegisGuard 1.4.5 status — "The Hearthstone Update"

1.4.5 is the **in-development** point release on the 1.4.0 line (current public release: 1.4.0). Theme: **recovery, polish, and completing the network's last room** — "1.4.0 connected the servers; 1.4.5 protects what makes them home."

Planned scope:

- **Player self-backups** (`/ag backup`) — owner-initiated restore points for plot data (settings, roles, members, noticeboards, bounds). Optional WorldEdit/FAWE block copies behind an owner-controlled opt-in. History browsing, self-service or staff-approved restore mode, cooldowns, per-plot caps, and optional Vault cost so it can't be abused.
- **Plasmo Voice hook** — Hearth rooms become isolated voice groups on Plasmo Voice as well as Simple Voice Chat; whichever provider is present is auto-detected. AegisGuard still ships no microphone client.
- **Network plot frequencies** — `/ag chat` plot channels relay to members logged in on other backends, completing the alliance/group/staff radio set from 1.4.0. Fails closed on single servers and non-network storage.
- **Arena polish** — live match HUD (wave counter, run timer, party status), configurable per-preset rewards (Vault, XP, items), party persistence between runs, and a per-arena record board.
- **Caravan polish** — return deliveries, a shipment history/ledger tab in the caravan GUI, and per-route pricing controls.

Draft release notes: `AegisGuard 1.4.5 changelog.txt` (repo root). Draft Spigot update post: `AegisGuard Spigot update post 1.4.5.txt`. Everything new rides the `modules:` switchboard and defaults to the safest posture, same as every 1.4.x system.

-----

The 2.0 concepts below are future work. They are not part of 1.4.0 or 1.4.5 and must not alter existing claim ownership, rental, market, progression, or protection behaviour without a dedicated migration and release plan.


> *"The Aegis has secured the present, but the future calls for expansion. We gaze now toward the horizon, where realms unite and cities rise from the dust."*

This document outlines the planned evolution of **AegisGuard**. Our focus is shifting from single-plot management to **Network Ecosystems**, **Social Economy**, and **Visual Immersion**.

-----

## 🌐 The "Empire" Sync (Cross-Server)

### *Breaking the walls between worlds.*

> **Status:** `[High Priority]`  •  **Target:** `v2.0.0`

If you run a **BungeeCord** or **Velocity** network, AegisGuard will become a singular, unified system. No more isolated gameplay.

### 💬 The "Aegis Frequency" (Global Plot Chat)

We are solving the chaos of global chat spam.

> **Partially shipped:** `/ag chat` plot channels and network-relayed alliance/group/staff radios landed in 1.4.0; **plot** frequency relay across backends is the 1.4.5 item above.

  * **The Problem:** Players coordinating builds or raids clutter the main chat, annoying others.
  * **The Solution:** A dedicated, cross-server channel restricted to plot members.
  * **Command:** `/ag chat` toggles a private frequency.
  * **Result:** A player farming on *Survival* can strategize instantly with their architect on *Creative* without whispering or using Discord.

### 🔗 Unified Player Data

  * **Global Claim Blocks:** A player's claim limit tracks across the entire network.
  * **Economy Sync:** Upkeep paid on one server updates the status on all others.

<br>

-----

## 🏙️ The Township System

### *From Owner to Landlord.*

> **Status:** `[In Design]`  •  **Target:** `v2.1.0`

Current plots are static. Townships allow for a "Real Estate" economy within a single claim.

  * **Sub-Zoning:** Use the Golden Hoe to define smaller areas *inside* your main plot (e.g., "Shop A", "Apartment 4B").
  * **Rent Automation:** List these sub-zones on the Marketplace. Other players can rent *just that room* from you.
  * **Tax Collection:** The plugin automatically collects rent from tenants and deposits it into the landlord's Vault account.

<br>

-----

## 💎 The Nexus: Plot Progression

### *Land that grows in power.*

> **Status:** `[Concept]`  •  **Target:** `v2.2.0`

Give players a reason to stay and grind. Every plot gains a "Nexus" block that acts as its heart.

  * **Leveling Up:** Owners invest XP, Money, or custom items into the Nexus.
  * **Unlockable Perks:**
      * **Lvl 5:** 🕊️ Flight enabled within plot borders. *(Already shipped in 1.4.0 as Ascension/Horizon level 30 — a 2.0 Nexus would unify progression, not duplicate it.)*
      * **Lvl 10:** ⚡ Permanent Haste/Speed II beacon effects (no beacon required).
      * **Lvl 20:** 🛡️ Increased member slots and radius expansion limits.

<br>

-----

## 🔮 Augmented Reality (Visuals)

### *Information without the clutter.*

> **Status:** `[Planned]`  •  **Target:** `v2.0.0`

We are moving away from chat spam and towards modern, holographic interfaces.

  * **Holographic Entry:** Entering a plot displays a floating Title/Subtitle or Hologram showing the **Owner** and **Plot Name**.
  * **Smart Borders:** Hovering over a border with the Scepter projects a floating label indicating the cardinal direction.
  * **"For Sale" Signs:** Plots listed on the marketplace will automatically project a floating hologram above their center, showing the **Price** and **Seller**.

<br>

-----

## ⚔️ The Siege Module (Optional)

### *Conflict amidst order.*

> **Status:** `[Concept]`  •  **Target:** `v2.3.0`

For servers that desire conflict. Completely optional.

  * **Siege Banners:** An expensive, craftable item that can be placed on an enemy border.
  * **The Mechanic:** Once placed, a timer begins (e.g., 15 minutes).
  * **The Raid:** If the defenders fail to destroy the banner in time, the plot's **PVP** and **Container** protections drop for 1 hour.

<br>

-----


Have a vision for the Aegis? Submit a Feature Request in our Issues tab.
