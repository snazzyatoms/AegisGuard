<p align="center">
  <img src="https://github.com/user-attachments/assets/1cab9fe4-6135-4280-8892-896ae495c530" width="260" alt="AegisGuard Logo">
</p>
                       <strong>The Next Generation of Land Protection & Economy</strong><br>
  Simple. Steadfast. Eternal.
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/"><img src="https://img.shields.io/badge/Spigot-Download-orange?style=for-the-badge"></a>
  <a href="https://hangar.papermc.io/snazzyatoms/AegisGuard"><img src="https://img.shields.io/badge/Hangar-Download-green?style=for-the-badge"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy"><img src="https://img.shields.io/badge/CurseForge-Download-purple?style=for-the-badge"></a>
  <a href="https://github.com/snazzyatoms/AegisGuard/wiki"><img src="https://img.shields.io/badge/Wiki-Documentation-blue?style=for-the-badge"></a>
  <a href="https://discord.gg/Y2NpuR7UZE"><img src="https://img.shields.io/badge/Discord-Join%20Community-7289da?style=for-the-badge&logo=discord&logoColor=white"></a>
</p>

---

> “Forged to shield thy lands from peril and strife. With the Sacred Scepter of Aegis, you claim, shape, and safeguard your realm with precision.”

---


## 🔗 Quick Links

**Pages**

- **Wiki:** https://github.com/snazzyatoms/AegisGuard/wiki  
- **Issues:** https://github.com/snazzyatoms/AegisGuard/issues  
- **Releases:** https://github.com/snazzyatoms/AegisGuard/releases  

**Plugin Listings**

- **Spigot:** https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/  
- **Hangar:** https://hangar.papermc.io/snazzyatoms/AegisGuard  
- **CurseForge:** https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy  

**Community**

- **Discord:** https://discord.gg/Y2NpuR7UZE  

---

## ⚡ What is AegisGuard?

AegisGuard is a **modern, Folia-native land protection and economy ecosystem** for Minecraft servers.  
It doesn’t just lock chunks – it turns land into a **living asset** in your server’s economy.

### Players can

- Claim land using a **visual selection wand** and intuitive GUI menus.  
- Pay **upkeep taxes** to keep their claims active.  
- List plots on a **Global Marketplace** for sale or rent.  
- Watch inactive land automatically flow into a server-wide **Auction House**.

### Admins can

- Define **Server Zones** (spawn, warzones, markets, jails, etc.).  
- Manage flags, roles, zoning, and protections through GUIs instead of commands.  
- Integrate with **Dynmap / BlueMap / Pl3xMap** and **Vault** for a full ecosystem.  

---

## 🎯 Key Features

### ⚡ Folia-Native Architecture

- Automatically detects **Folia / Paper / Spigot** at runtime.  
- Uses `GlobalRegionScheduler` on Folia for true multi-threaded safety.  
- Heavy tasks (upkeep, auctions, data saves, map sync) are offloaded from the main thread.

---

### 💰 Living Land Economy

Turn land into a **real estate loop**:

- **Upkeep System:** Plots require periodic taxes.  
- **Auction House:** Expired or seized plots go up for public auction.  
- **Global Marketplace:** Players buy, sell, and rent plots through GUI menus.  
- **Flexible Costs:** Use Vault money, items, XP, or XP levels as currency.

---

### 🏰 Empire Building (Player Features)

- **Claims & Sub-Claims (Zoning):**  
  Create main plots, then define sub-zones (rooms, shops, apartments) to rent out.

- **Plot Leveling:**  
  Invest resources to level up plots and unlock buffs (Speed, Haste, Regen, etc.).

- **Biome Cosmetics:**  
  Change the biome atmosphere of a plot for visual flair.

- **Particle Borders & Holograms:**  
  Show borders and plot info without spamming chat.

---

### 🛡️ Server Plot Architecture (Admin Tools)

- **Sentinel’s Scepter:**  
  `/agadmin wand` – create server-owned zones that ignore normal limits/costs.

- **Plot Conversion:**  
  `/agadmin convert` – stand in a player plot and seize it as a server zone.

- **Master Key Mode:**  
  High-trust permission that lets staff adjust any plot simply by standing in it.

---

### 🧭 GUI-First Design

Almost everything is driven by menus instead of raw commands:

- **Guardian Codex:** `/ag menu` – central hub for claims, settings, travel, and upgrades.  
- **Flags & Roles:** Configure protections and trust levels visually.  
- **Cosmetics & Travel:** Warp between plots and style your land without typing long commands.

---

### 🗺️ Map & Ecosystem Integrations

- **Dynmap:** Async plot overlays on your web map.  
- **BlueMap:** 3D map overlays for claims.  
- **Pl3xMap:** Lightweight modern map support.  
- **Vault:** Economy backbone for upkeep and transactions.  
- **PlaceholderAPI:** Placeholders for scoreboards, GUIs, and HUDs.

---

## 🧪 Try AegisGuard Live

You can test AegisGuard on a public demo server:

```text
IP:      72.5.47.116:25570
Version: 1.20.4+ (Daily World Reset • Sandbox Mode)
Command: /ag menu
Tip:     Open the Guardian Codex inside the menu to learn how to claim land.
