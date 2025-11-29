# 🛡️ AegisGuard

### The Next Generation of Land Protection & Economy  
**Simple. Steadfast. Eternal.**

![AegisGuard Banner](https://raw.githubusercontent.com/snazzyatoms/AegisGuard/main/.github/banner.png)

---

> “Forged to shield thy lands from peril and strife. With the Sacred Scepter of Aegis, you claim, shape, and safeguard your realm with precision.”

---

## 🔗 Quick Links

- **Downloads:** Spigot, Hangar, CurseForge  
- **Wiki:** Full Documentation & Guides  
- **Support:** Discord Community  
- **Issues:** GitHub Issue Tracker  

```text
Spigot:      https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/
Hangar:      https://hangar.papermc.io/snazzyatoms/AegisGuard
CurseForge:  https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy
Wiki:        https://github.com/snazzyatoms/AegisGuard/wiki
Discord:     https://discord.gg/Y2NpuR7UZE
⚡ What is AegisGuard?

AegisGuard is a modern, Folia-native land protection ecosystem for Minecraft servers (Paper, Folia, Spigot). It doesn’t just “lock chunks” – it turns land into a living asset tied into a server-wide economy loop. 
SpigotMC
+1

Players:

Claim land using a visual selection tool and GUI menus.

Pay upkeep taxes to keep their claims active.

List plots on a Global Marketplace or sell/rent sub-zones.

Watch inactive land automatically flow into a server-wide Auction House.

Admins:

Define Server Zones (spawn, warzones, jails, markets). 
SpigotMC

Control flags, roles, and plot behavior via GUIs.

Integrate land with Dynmap / BlueMap / Pl3xMap and Vault economies.

🎯 Key Features
⚡ Folia-Native Architecture

Automatically detects Folia / Paper / Spigot at runtime.

Uses GlobalRegionScheduler on Folia for true multi-threaded safety. 
SpigotMC
+1

Heavy tasks (upkeep, auctions, map sync) run off the main thread.

💰 Living Land Economy

AegisGuard is built around a Real Estate loop:

Upkeep System – Plots require taxes; unpaid land expires gracefully.

Auction House – Expired / seized plots go up for auction instead of rotting. 
CurseForge
+1

Global Marketplace – Players can list plots for sale or rent through a GUI.

Universal Economy Engine – Configure costs to use:

Vault money

Items (e.g. Diamonds)

XP

XP Levels 
Hangar

🏰 Empire Building (Player Features)

Claims & Sub-Claims (Zoning) – Create main plots, then sub-zones (rooms/shops) and rent them out to other players. 
SpigotMC
+1

Plot Leveling – Invest money / XP / items to level up your land and unlock buffs (Speed, Haste, Regen, etc.). 
CurseForge
+1

Biome Cosmetics – Change the visual biome atmosphere of your land (e.g. jungle feel in a plains region). 
Hangar
+1

Particle Borders & Holograms – Visualize borders and show plot titles on entry. 
CurseForge
+1

🛡️ Server Plot Architecture (Admin Tools)

Sentinel’s Scepter: /agadmin wand – Instantly create Server Zones that ignore normal limits & costs. 
SpigotMC

Plot Conversion: /agadmin convert – Stand in a player plot and seize it for the server (perfect for turning player markets into official hubs). 
SpigotMC

Master Key – A high-trust admin permission to edit any plot’s flags and roles simply by standing inside it. 
SpigotMC

🧭 GUI-First Design

Guardian Codex (/ag menu) – Central menu for everything:

Claims & Upgrades

Flags & Roles

Cosmetics & Travel

Travel Menu – Warp to your plots, server hubs, and public landmarks.

Player-Friendly – Very few commands; nearly everything is clickable.

🗺️ Map & Ecosystem Integrations

Dynmap – Render claims on your web map asynchronously. 
GitHub

BlueMap – 3D map integration with plot overlays.

Pl3xMap – Modern map integration for Paper/Folia.

Vault – Economy backbone for upkeep, auctions, and plot fees.

PlaceholderAPI – Placeholders for HUDs, scoreboards, and external GUIs.

🧪 Try Before You Install

You can test AegisGuard live on a public showcase server.
IP: 72.5.47.116:25570
Version: 1.20.4+ (Daily World Reset • Sandbox Mode)
Command: /ag menu
Tip: Open the Guardian Codex inside the menu to learn how to claim land.

✅ Compatibility

Supported Server Software

Folia (native)

Paper

Spigot

Supported Minecraft Versions

1.16 – 1.21+ (tested on 1.16–1.20.6 and 1.21.x) 
SpigotMC
+1

Not Supported

1.8 – 1.12 (AegisGuard is intentionally modern-only.) 
Hangar

📦 Installation

Download the latest JAR from:

Spigot / Hangar / CurseForge (see Quick Links above).

Drop AegisGuard-x.y.z.jar into your server’s plugins/ folder.

(Optional but recommended) Install:

Vault for currency support.

An economy plugin (e.g. EssentialsX Eco, CMI, etc.).

Dynmap / BlueMap / Pl3xMap for map visualisation.

PlaceholderAPI for placeholders in scoreboards and menus.

Restart your server.

Check /plugins – AegisGuard should be green and enabled.

For detailed configuration, see the Installation and Configuration pages in the wiki:
https://github.com/snazzyatoms/AegisGuard/wiki

🚀 Quick Start (Players)

From the wiki’s “Quick Start” section. 
GitHub

Get the Scepter

/ag wand


Select Land

Right-click one corner.

Left-click the opposite corner.

Claim the Plot

/ag claim


Open the Guardian Codex

/ag menu


Manage flags, roles, cosmetics, travel, expansion, and more.

🧰 Admin Overview

Some useful admin commands:

/agadmin wand      - Get the Sentinel's Scepter (create Server Zones)
/agadmin convert   - Convert the current plot into a Server Zone
/ag reload         - Reload configuration & messages


Permissions and detailed command breakdown are documented here:

https://github.com/snazzyatoms/AegisGuard/wiki/Permissions-and-Commands

📚 Documentation

The AegisGuard Wiki is your Codex:

Start Here: Overview & concepts

Installation: Setup paths for Folia, Paper, Spigot

Player’s Handbook: How to claim, manage, and grow your land

Land Economy: Upkeep, auctions, plot levels, and currencies

Integrations: Dynmap, BlueMap, Pl3xMap, Vault, PlaceholderAPI

Developer API: Hooks and examples for integration

https://github.com/snazzyatoms/AegisGuard/wiki

🛠️ Development & Contributing

Contributions, suggestions, and PRs are welcome.

Bug Reports / Feature Requests: GitHub Issues

Discussions & Support: Discord

Roadmap & Upcoming Features: UPCOMING.md in the repo

If you open an issue, please include:

Server version (e.g. Paper 1.20.4, Folia 1.21.1)

AegisGuard version (e.g. v1.1.1)

Relevant logs / stack traces

Steps to reproduce

❤️ Support & Community

You don’t have to debug alone:

Discord: Fast config help, tickets, and chat

Wiki: Guides for players & admins

GitHub Issues: Formal bug reports and suggestions

Discord: https://discord.gg/Y2NpuR7UZE
Wiki:    https://github.com/snazzyatoms/AegisGuard/wiki
Issues:  https://github.com/snazzyatoms/AegisGuard/issues

📜 License

AegisGuard is released under the MIT License.
You are free to use it on public or private servers, and to contribute improvements via pull requests.

“Simple. Steadfast. Eternal.”
Forged with ❤️ for the Minecraft community.

