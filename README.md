> **Project Status Notice:**
> This project is currently on the back burner due to unforeseen circumstances related to family matters. I apologize for any inconvenience. Version 1.2.5 will remain active and will not be removed from any platform. However, any updates regarding AegisGuard will be postponed until further notice. Thank you for your understanding and patience.

<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong>
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/"><img src="https://img.shields.io/badge/Spigot-Download-orange?style=for-the-badge"></a>
  <a href="https://hangar.papermc.io/snazzyatoms/AegisGuard"><img src="https://img.shields.io/badge/Hangar-Download-green?style=for-the-badge"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy"><img src="https://img.shields.io/badge/CurseForge-Download-purple?style=for-the-badge"></a>
  <a href="https://github.com/snazzyatoms/AegisGuard/wiki"><img src="https://img.shields.io/badge/Wiki-Documentation-blue?style=for-the-badge"></a>
</p>

---

<p align="center">
  <em>"Forged to shield thy lands from peril and strife. With the Sacred Scepter of Aegis, you claim, shape, and safeguard your realm with precision."</em>
</p>

---

# AegisGuard v1.2.5

AegisGuard is a **modern, Folia-optimized land protection and economy ecosystem** for Minecraft servers running **Spigot**, **Paper**, **Purpur**, or **Folia** (1.20+).

It's not just another claim plugin. AegisGuard transforms land ownership into a **living system** with progression, governance, safety nets, and a complete economy loop designed for long-running survival and SMP worlds.

---

## Table of Contents

- [Server Compatibility](#server-compatibility)
- [Core Features](#core-features)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Plugin Integrations](#plugin-integrations)
- [Installation](#installation)
- [Quick Links](#quick-links)
- [Support](#support)

---

## Server Compatibility

| Platform | Supported | Notes |
|----------|-----------|-------|
| **Spigot** | Yes | 1.20+ |
| **Paper** | Yes | Recommended |
| **Purpur** | Yes | Full support |
| **Folia** | Yes | Native multi-threaded support |

---

## Core Features

### Land Protection & Claiming

- **Wand-based selection system** - Use the Sacred Scepter to select corners and claim land
- **Flexible claim sizes** - Configurable minimum/maximum radius and area limits
- **Per-world rules** - Different claiming rules, costs, and limits for each world
- **Claim merging** - Combine adjacent plots into larger territories
- **Claim resizing** - Expand or shrink existing claims
- **Buffer zones** - Automatic spacing between claims to prevent disputes
- **Visual boundaries** - Particle-based border visualization when holding the wand

### ClaimBlocks Economy

AegisGuard features a complete ClaimBlocks currency system:

- **Starting blocks** - Configurable initial balance for new players (default: 500)
- **Playtime rewards** - Earn ClaimBlocks passively while playing
- **Anti-AFK protection** - Prevents idle players from farming blocks
- **Level-up bonuses** - Earn blocks when upgrading plot levels
- **First claim limits** - Optional area cap on first claims to encourage gradual expansion

### ClaimBlocks Exchange (Vault Integration)

Trade ClaimBlocks for server currency with built-in anti-abuse protections:

| Feature | Description |
|---------|-------------|
| **Buy/Sell rates** | Configurable prices per block |
| **Transaction fees** | Percentage and flat fees to prevent arbitrage |
| **Cooldowns** | Minimum time between trades |
| **Hourly limits** | Maximum trades per hour |
| **Daily caps** | Maximum blocks bought/sold per day |
| **Sell lock** | Hold timer prevents instant buy-sell flipping |
| **Preset profiles** | `safe_small`, `balanced_mid`, `fast_large`, or `custom` |

### Fair Initial Pricing

v1.2.5 introduces a fair pricing model that prevents players from claiming massive areas cheaply:

```
Total Cost = Base Cost + (Area - Base Area) x Expansion Rate
```

**Example with defaults:**
- 256 blocks (1 chunk): $100
- 512 blocks (2 chunks): $2,660
- 1024 blocks (4 chunks): $7,780

### Snapshots & Rollback System

Safety nets for risky operations:

- **Automatic snapshots** before expansions and merges
- **Admin rollback GUI** to restore previous claim states
- **Configurable retention** - Set maximum snapshots and expiration time
- **Audit logging** - Track all snapshot operations

### Expansion Request System

Two approval modes for claim expansions:

| Mode | Behavior |
|------|----------|
| **QUEUE** | Players submit requests, admins approve/deny via GUI |
| **INSTANT** | Automatic approval with optional admin notifications |

### Plot Leveling (RPG Progression)

Upgrade plots through 30 levels to unlock rewards:

- **Potion effects** - Speed, Haste, Jump Boost, Night Vision, and more
- **Member slots** - Increase maximum trusted players per level
- **Special flags** - Unlock flight at level 10, enhanced abilities at level 30
- **Dual payment** - Pay with Vault currency or ClaimBlocks

### Zoning (Sub-Claims)

Create zones within your plots:

- **Up to 10 zones per plot** (configurable)
- **Rentable rooms** - Landlords can charge rent for sub-zones
- **Independent permissions** - Each zone can have different access rules

### Marketplace & Auctions

- **Plot marketplace** - List plots for sale at fixed prices
- **Auction house** - Timed bidding system with minimum bid increases
- **Configurable fees** - Owner cut percentage and listing fees

### Protection Flags

Granular control over what happens in your claims:

| Flag | Description |
|------|-------------|
| `pvp` | Player vs player combat |
| `mob-spawning` | Hostile mob spawns |
| `container-access` | Chest/barrel/hopper access |
| `entity-protection` | Item frames, armor stands, etc. |
| `farm-protection` | Crop trampling and harvesting |
| `tnt-damage` | Explosion damage |
| `fire-spread` | Fire propagation |
| `piston-use` | Piston mechanics |
| `fly` | Creative-style flight in claims |
| `entry` | Who can enter the claim |
| `shop-interact` | Villager/shop interactions |

### Role Management

Three-tier permission system:

| Role | Priority | Default Permissions |
|------|----------|---------------------|
| **Owner** | 100 | All permissions |
| **Member** | 50 | Build, break, interact, containers |
| **Visitor** | 0 | Doors, buttons only |

### Biome Changing

Transform the biome within your claim:

- 11 biomes available (Plains, Forest, Desert, Jungle, Taiga, Swamp, Cherry Grove, Badlands, Mushroom Fields, Meadow, Lush Caves)
- Configurable cost per change

### Cosmetics

Personalize your claims:

- **Border particles** - Flame, Heart, Soul Fire, Enchantment effects
- **Entry effects** - Lightning strikes and sounds when entering claims

### Welcome/Exit Messages

- **Title notifications** - Display messages when players enter/exit claims
- **Three modes**: `PER_PLAYER` (toggle), `FORCE_ON`, `FORCE_OFF`
- **Chat messages** - Optional chat-based notifications
- **Permission bypass** - Admins can receive messages even when disabled

### Additional Features

- **Unstuck command** - Escape from claims you're trapped in
- **Wilderness revert** - Automatically unclaim abandoned plots
- **Plot upkeep** - Optional rent/tax system for claim maintenance
- **Social system** - Like/rate other players' plots
- **Mob barrier** - Active system that removes hostile mobs from claims
- **Plot teleportation** - Set spawn points and visit other players' claims

---

## Commands

### Player Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/aegis` | `/ag`, `/guard` | Main command |
| `/aegis menu` | | Open the main GUI |
| `/aegis wand` | | Receive the claiming wand |
| `/aegis claim` | | Claim selected area |
| `/aegis unclaim` | | Remove your claim |
| `/aegis resize` | | Resize existing claim |
| `/aegis merge` | | Merge adjacent plots |
| `/aegis cost` | | Check claim cost |
| `/aegis home` | | Teleport to plot spawn |
| `/aegis setspawn` | | Set plot spawn point |
| `/aegis welcome <msg>` | | Set welcome message |
| `/aegis farewell <msg>` | | Set farewell message |
| `/aegis notify` | | Toggle enter/exit notifications |
| `/aegis visit` | | Open travel menu |
| `/aegis market` | | View plot marketplace |
| `/aegis sell <price>` | | List plot for sale |
| `/aegis unsell` | | Remove plot from market |
| `/aegis auction` | | View auction house |
| `/aegis zone` | | Manage sub-zones |
| `/aegis level` | | Open leveling menu |
| `/aegis like` | | Give reputation to a plot |
| `/aegis rename <name>` | | Set plot display name |
| `/aegis setdesc <desc>` | | Set plot description |
| `/aegis stuck` | | Unstuck from a claim |
| `/aegis blocks` | `/aegis ledger` | View ClaimBlock balance |

### Admin Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/aegisadmin` | `/agadmin`, `/aga` | Admin command |
| `/aegisadmin menu` | | Open admin GUI |
| `/aegisadmin reload` | | Reload configuration |
| `/aegisadmin bypass` | | Toggle protection bypass |
| `/aegisadmin blocks <player> <add/remove/set> <amount>` | | Manage player ClaimBlocks |
| `/aegisadmin migrate <plugin>` | | Import from other claim plugins |

---

## Permissions

### Player Permissions

All player permissions are granted by default via the `aegis.user` parent node.

<details>
<summary><strong>Click to expand full permission list</strong></summary>

| Permission | Description | Default |
|------------|-------------|---------|
| `aegis.use` | Use the /aegis command | true |
| `aegis.menu` | Open the main GUI | true |
| `aegis.wand` | Receive and use the claim wand | true |
| `aegis.claim` | Claim land | true |
| `aegis.unclaim` | Unclaim land | true |
| `aegis.resize` | Resize claims | true |
| `aegis.merge` | Merge adjacent plots | true |
| `aegis.home` | Teleport to plot spawn | true |
| `aegis.spawn` | Teleport to world spawn | true |
| `aegis.setspawn` | Set plot spawn | true |
| `aegis.visit` | Open travel menu | true |
| `aegis.stuck` | Use unstuck command | true |
| `aegis.ledger` | View ClaimBlock ledger | true |
| `aegis.welcome` | Set welcome message | true |
| `aegis.farewell` | Set farewell message | true |
| `aegis.notify` | Toggle notifications | true |
| `aegis.rename` | Set plot name | true |
| `aegis.setdesc` | Set plot description | true |
| `aegis.like` | Give plot reputation | true |
| `aegis.market` | View marketplace | true |
| `aegis.sell` | List plot for sale | true |
| `aegis.unsell` | Remove from sale | true |
| `aegis.auction` | View auctions | true |
| `aegis.zone` | Manage sub-zones | true |
| `aegis.level` | Access leveling | true |
| `aegis.biome` | Access biome menu | true |
| `aegis.kick` | Kick from own plot | true |
| `aegis.ban` | Ban from own plot | true |
| `aegis.unban` | Unban from own plot | true |
| `aegis.earn.blocks` | Earn ClaimBlocks via playtime | true |
| `aegis.claimblocks.exchange` | Access ClaimBlocks exchange | true |
| `aegis.claimblocks.buy` | Buy ClaimBlocks | true |
| `aegis.claimblocks.sell` | Sell ClaimBlocks | true |

</details>

### Admin Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `aegis.admin` | All admin permissions | op |
| `aegis.admin.manage` | Edit other players' plots | op |
| `aegis.admin.bypass-limits` | Ignore plot limits | op |
| `aegis.admin.migrate` | Import from other plugins | op |
| `aegis.admin.wand` | Get Sentinel's Scepter | op |
| `aegis.reload` | Reload configuration | op |
| `aegis.bypass` | Bypass all protections | op |
| `aegis.convert` | Convert plot to Server Zone | op |
| `aegis.setwarp` | Set server warps | op |
| `aegis.delwarp` | Delete server warps | op |
| `aegis.claimblocks.exchange.bypass` | Bypass exchange limits | op |
| `aegis.claimblocks.selllock.bypass` | Bypass sell lock timer | op |
| `aegis.notify.others` | Toggle notifications for others | op |
| `aegis.notify.bypass` | Receive notifications when disabled | op |

---

## Configuration

AegisGuard uses a comprehensive `config.yml` with sections for:

### Data Storage

```yaml
storage:
  type: "yml"  # Options: yml, sqlite, mysql
  database:
    file: "aegisguard.db"
    # MySQL options available
```

### Economy Settings

```yaml
economy:
  enabled: true
  use_vault: true
  claim_cost: 100.0
  resize_cost_per_block: 10.0
  refund_on_unclaim: true
  refund_percent: 50.0
```

### ClaimBlocks Configuration

```yaml
claim_blocks:
  enabled: true
  starting_blocks: 500
  earn:
    playtime:
      enabled: true
      interval_minutes: 10
      blocks_per_interval: 50
      anti_afk:
        enabled: true
        required_activity_seconds: 300
```

### Localization

Multiple language support with split bundle files:

- `old_english` - Medieval/fantasy themed
- `hybrid_english` - Mix of old and modern
- `modern_english` - Contemporary language
- `spanish_mx` - Mexican Spanish
- `spanish_ar` - Argentine Spanish

---

## Plugin Integrations

### Economy & Utilities

| Plugin | Integration |
|--------|-------------|
| **Vault** | Economy transactions, ClaimBlocks exchange |
| **PlaceholderAPI** | Custom placeholders for scoreboards/chat |
| **CoreProtect** | Block logging compatibility |

### Map Visualization

| Plugin | Integration |
|--------|-------------|
| **Dynmap** | Claim overlay with customizable colors |
| **BlueMap** | Claim markers and labels |
| **Pl3xMap** | Claim visualization |

### Protection Compatibility

AegisGuard can coexist with other protection plugins:

| Plugin | Compatibility |
|--------|---------------|
| **WorldGuard** | Region overlap policy |
| **GriefPrevention** | Claim import/migration |
| **GriefDefender** | Claim import/migration |
| **Towny** | Town area detection |
| **Residence** | Region detection |
| **Lands** | Claim import/migration |

### Other Integrations

| Plugin | Integration |
|--------|-------------|
| **Discord** | Webhook notifications for claim events |
| **mcMMO** | Skill compatibility |
| **Jobs** | Job integration |

---

## Installation

1. **Download** AegisGuard from [Spigot](https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/), [Hangar](https://hangar.papermc.io/snazzyatoms/AegisGuard), or [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy)

2. **Place** the JAR file in your server's `plugins/` folder

3. **Start** your server to generate configuration files

4. **Configure** `plugins/AegisGuard/config.yml` to your preferences

5. **Reload** with `/agadmin reload` or restart the server

### Optional Dependencies

- **Vault** + Economy Provider (EssentialsX, CMI, etc.) - For monetary transactions
- **PlaceholderAPI** - For placeholder support
- **Dynmap/BlueMap/Pl3xMap** - For map visualization

---

## Quick Links

| Resource | Link |
|----------|------|
| Documentation | [Wiki & Guides](https://github.com/snazzyatoms/AegisGuard/wiki) |
| Bug Reports | [GitHub Issues](https://github.com/snazzyatoms/AegisGuard/issues) |
| Releases | [GitHub Releases](https://github.com/snazzyatoms/AegisGuard/releases) |
| Spigot | [SpigotMC](https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/) |
| Hangar | [PaperMC Hangar](https://hangar.papermc.io/snazzyatoms/AegisGuard) |
| CurseForge | [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy) |

---

## Support

Need help? Have suggestions?

- **Discord**: [Join our community](https://discord.gg/Y2NpuR7UZE)
- **Wiki**: [Read the documentation](https://github.com/snazzyatoms/AegisGuard/wiki)
- **Issues**: [Report bugs](https://github.com/snazzyatoms/AegisGuard/issues)

---

<p align="center">
  <strong>AegisGuard v1.2.5</strong> | Made with care by <a href="https://github.com/snazzyatoms">snazzyatoms</a>
</p>
