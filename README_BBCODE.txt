[CENTER][IMG]https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95[/IMG]

[SIZE=5][B]Simple. Steadfast. Eternal.[/B][/SIZE]

[URL='https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/'][IMG]https://img.shields.io/badge/Spigot-Download-orange?style=for-the-badge[/IMG][/URL] [URL='https://hangar.papermc.io/snazzyatoms/AegisGuard'][IMG]https://img.shields.io/badge/Hangar-Download-green?style=for-the-badge[/IMG][/URL] [URL='https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy'][IMG]https://img.shields.io/badge/CurseForge-Download-purple?style=for-the-badge[/IMG][/URL] [URL='https://github.com/snazzyatoms/AegisGuard/wiki'][IMG]https://img.shields.io/badge/Wiki-Documentation-blue?style=for-the-badge[/IMG][/URL] [URL='https://discord.gg/Y2NpuR7UZE'][IMG]https://img.shields.io/badge/Discord-Join%20Community-7289da?style=for-the-badge&logo=discord&logoColor=white[/IMG][/URL][/CENTER]

[CENTER][COLOR=rgb(255, 80, 80)][SIZE=5][B]⚠ Project Status Notice ⚠[/B][/SIZE][/COLOR]

[B]This project is currently on the back burner due to unforeseen circumstances related to family matters.[/B]
I apologize for any inconvenience. Version 1.2.5 will remain active and will not be removed from any platform.
However, any updates regarding AegisGuard will be postponed until further notice.
Thank you for your understanding and patience.[/CENTER]

[HR][/HR]

[CENTER][I]"Forged to shield thy lands from peril and strife. With the Sacred Scepter of Aegis, you claim, shape, and safeguard your realm with precision."[/I][/CENTER]

[HR][/HR]

[SIZE=6][B]AegisGuard v1.2.5[/B][/SIZE]

AegisGuard is a [B]modern, Folia-optimized land protection and economy ecosystem[/B] for Minecraft servers running [B]Spigot[/B], [B]Paper[/B], [B]Purpur[/B], or [B]Folia[/B] (1.20+).

It's not just another claim plugin. AegisGuard transforms land ownership into a [B]living system[/B] with progression, governance, safety nets, and a complete economy loop designed for long-running survival and SMP worlds.

[HR][/HR]

[SIZE=5][B]Server Compatibility[/B][/SIZE]

[LIST]
[*][B]Spigot[/B] — Yes (1.20+)
[*][B]Paper[/B] — Yes (Recommended)
[*][B]Purpur[/B] — Yes (Full support)
[*][B]Folia[/B] — Yes (Native multi-threaded support)
[/LIST]

[HR][/HR]

[SIZE=5][B]Core Features[/B][/SIZE]

[SIZE=4][B]Land Protection & Claiming[/B][/SIZE]

[LIST]
[*][B]Wand-based selection system[/B] — Use the Sacred Scepter to select corners and claim land
[*][B]Flexible claim sizes[/B] — Configurable minimum/maximum radius and area limits
[*][B]Per-world rules[/B] — Different claiming rules, costs, and limits for each world
[*][B]Claim merging[/B] — Combine adjacent plots into larger territories
[*][B]Claim resizing[/B] — Expand or shrink existing claims
[*][B]Buffer zones[/B] — Automatic spacing between claims to prevent disputes
[*][B]Visual boundaries[/B] — Particle-based border visualization when holding the wand
[/LIST]

[SIZE=4][B]ClaimBlocks Economy[/B][/SIZE]

AegisGuard features a complete ClaimBlocks currency system:

[LIST]
[*][B]Starting blocks[/B] — Configurable initial balance for new players (default: 500)
[*][B]Playtime rewards[/B] — Earn ClaimBlocks passively while playing
[*][B]Anti-AFK protection[/B] — Prevents idle players from farming blocks
[*][B]Level-up bonuses[/B] — Earn blocks when upgrading plot levels
[*][B]First claim limits[/B] — Optional area cap on first claims to encourage gradual expansion
[/LIST]

[SIZE=4][B]ClaimBlocks Exchange (Vault Integration)[/B][/SIZE]

Trade ClaimBlocks for server currency with built-in anti-abuse protections:

[LIST]
[*][B]Buy/Sell rates[/B] — Configurable prices per block
[*][B]Transaction fees[/B] — Percentage and flat fees to prevent arbitrage
[*][B]Cooldowns[/B] — Minimum time between trades
[*][B]Hourly limits[/B] — Maximum trades per hour
[*][B]Daily caps[/B] — Maximum blocks bought/sold per day
[*][B]Sell lock[/B] — Hold timer prevents instant buy-sell flipping
[*][B]Preset profiles[/B] — safe_small, balanced_mid, fast_large, or custom
[/LIST]

[SIZE=4][B]Fair Initial Pricing[/B][/SIZE]

v1.2.5 introduces a fair pricing model that prevents players from claiming massive areas cheaply:

[CODE]Total Cost = Base Cost + (Area - Base Area) x Expansion Rate[/CODE]

[B]Example with defaults:[/B]
[LIST]
[*]256 blocks (1 chunk): $100
[*]512 blocks (2 chunks): $2,660
[*]1024 blocks (4 chunks): $7,780
[/LIST]

[SIZE=4][B]Snapshots & Rollback System[/B][/SIZE]

Safety nets for risky operations:

[LIST]
[*][B]Automatic snapshots[/B] before expansions and merges
[*][B]Admin rollback GUI[/B] to restore previous claim states
[*][B]Configurable retention[/B] — Set maximum snapshots and expiration time
[*][B]Audit logging[/B] — Track all snapshot operations
[/LIST]

[SIZE=4][B]Expansion Request System[/B][/SIZE]

Two approval modes for claim expansions:

[LIST]
[*][B]QUEUE[/B] — Players submit requests, admins approve/deny via GUI
[*][B]INSTANT[/B] — Automatic approval with optional admin notifications
[/LIST]

[SIZE=4][B]Plot Leveling (RPG Progression)[/B][/SIZE]

Upgrade plots through 30 levels to unlock rewards:

[LIST]
[*][B]Potion effects[/B] — Speed, Haste, Jump Boost, Night Vision, and more
[*][B]Member slots[/B] — Increase maximum trusted players per level
[*][B]Special flags[/B] — Unlock flight at level 10, enhanced abilities at level 30
[*][B]Dual payment[/B] — Pay with Vault currency or ClaimBlocks
[/LIST]

[SIZE=4][B]Zoning (Sub-Claims)[/B][/SIZE]

Create zones within your plots:

[LIST]
[*]Up to 10 zones per plot (configurable)
[*][B]Rentable rooms[/B] — Landlords can charge rent for sub-zones
[*][B]Independent permissions[/B] — Each zone can have different access rules
[/LIST]

[SIZE=4][B]Marketplace & Auctions[/B][/SIZE]

[LIST]
[*][B]Plot marketplace[/B] — List plots for sale at fixed prices
[*][B]Auction house[/B] — Timed bidding system with minimum bid increases
[*][B]Configurable fees[/B] — Owner cut percentage and listing fees
[/LIST]

[SIZE=4][B]Protection Flags[/B][/SIZE]

Granular control over what happens in your claims:

[LIST]
[*][B]pvp[/B] — Player vs player combat
[*][B]mob-spawning[/B] — Hostile mob spawns
[*][B]container-access[/B] — Chest/barrel/hopper access
[*][B]entity-protection[/B] — Item frames, armor stands, etc.
[*][B]farm-protection[/B] — Crop trampling and harvesting
[*][B]tnt-damage[/B] — Explosion damage
[*][B]fire-spread[/B] — Fire propagation
[*][B]piston-use[/B] — Piston mechanics
[*][B]fly[/B] — Creative-style flight in claims
[*][B]entry[/B] — Who can enter the claim
[*][B]shop-interact[/B] — Villager/shop interactions
[/LIST]

[SIZE=4][B]Role Management[/B][/SIZE]

Three-tier permission system:

[LIST]
[*][B]Owner[/B] (Priority 100) — All permissions
[*][B]Member[/B] (Priority 50) — Build, break, interact, containers
[*][B]Visitor[/B] (Priority 0) — Doors, buttons only
[/LIST]

[SIZE=4][B]Biome Changing[/B][/SIZE]

Transform the biome within your claim:

[LIST]
[*]11 biomes available: Plains, Forest, Desert, Jungle, Taiga, Swamp, Cherry Grove, Badlands, Mushroom Fields, Meadow, Lush Caves
[*]Configurable cost per change
[/LIST]

[SIZE=4][B]Cosmetics[/B][/SIZE]

Personalize your claims:

[LIST]
[*][B]Border particles[/B] — Flame, Heart, Soul Fire, Enchantment effects
[*][B]Entry effects[/B] — Lightning strikes and sounds when entering claims
[/LIST]

[SIZE=4][B]Welcome/Exit Messages[/B][/SIZE]

[LIST]
[*][B]Title notifications[/B] — Display messages when players enter/exit claims
[*][B]Three modes[/B]: PER_PLAYER (toggle), FORCE_ON, FORCE_OFF
[*][B]Chat messages[/B] — Optional chat-based notifications
[*][B]Permission bypass[/B] — Admins can receive messages even when disabled
[/LIST]

[SIZE=4][B]Additional Features[/B][/SIZE]

[LIST]
[*][B]Unstuck command[/B] — Escape from claims you're trapped in
[*][B]Wilderness revert[/B] — Automatically unclaim abandoned plots
[*][B]Plot upkeep[/B] — Optional rent/tax system for claim maintenance
[*][B]Social system[/B] — Like/rate other players' plots
[*][B]Mob barrier[/B] — Active system that removes hostile mobs from claims
[*][B]Plot teleportation[/B] — Set spawn points and visit other players' claims
[/LIST]

[HR][/HR]

[SIZE=5][B]Commands[/B][/SIZE]

[SIZE=4][B]Player Commands[/B][/SIZE]

[LIST]
[*][B]/aegis[/B] (aliases: /ag, /guard) — Main command
[*][B]/aegis menu[/B] — Open the main GUI
[*][B]/aegis wand[/B] — Receive the claiming wand
[*][B]/aegis claim[/B] — Claim selected area
[*][B]/aegis unclaim[/B] — Remove your claim
[*][B]/aegis resize[/B] — Resize existing claim
[*][B]/aegis merge[/B] — Merge adjacent plots
[*][B]/aegis cost[/B] — Check claim cost
[*][B]/aegis home[/B] — Teleport to plot spawn
[*][B]/aegis setspawn[/B] — Set plot spawn point
[*][B]/aegis welcome <msg>[/B] — Set welcome message
[*][B]/aegis farewell <msg>[/B] — Set farewell message
[*][B]/aegis notify[/B] — Toggle enter/exit notifications
[*][B]/aegis visit[/B] — Open travel menu
[*][B]/aegis market[/B] — View plot marketplace
[*][B]/aegis sell <price>[/B] — List plot for sale
[*][B]/aegis unsell[/B] — Remove plot from market
[*][B]/aegis auction[/B] — View auction house
[*][B]/aegis zone[/B] — Manage sub-zones
[*][B]/aegis level[/B] — Open leveling menu
[*][B]/aegis like[/B] — Give reputation to a plot
[*][B]/aegis rename <name>[/B] — Set plot display name
[*][B]/aegis setdesc <desc>[/B] — Set plot description
[*][B]/aegis stuck[/B] — Unstuck from a claim
[*][B]/aegis blocks[/B] (alias: /aegis ledger) — View ClaimBlock balance
[/LIST]

[SIZE=4][B]Admin Commands[/B][/SIZE]

[LIST]
[*][B]/aegisadmin[/B] (aliases: /agadmin, /aga) — Admin command
[*][B]/aegisadmin menu[/B] — Open admin GUI
[*][B]/aegisadmin reload[/B] — Reload configuration
[*][B]/aegisadmin bypass[/B] — Toggle protection bypass
[*][B]/aegisadmin blocks <player> <add/remove/set> <amount>[/B] — Manage player ClaimBlocks
[*][B]/aegisadmin migrate <plugin>[/B] — Import from other claim plugins
[/LIST]

[HR][/HR]

[SIZE=5][B]Permissions[/B][/SIZE]

[SIZE=4][B]Player Permissions[/B][/SIZE]

All player permissions are granted by default via the [B]aegis.user[/B] parent node.

[SPOILER="Click to expand full permission list"]
[LIST]
[*][B]aegis.use[/B] — Use the /aegis command [I](default: true)[/I]
[*][B]aegis.menu[/B] — Open the main GUI [I](default: true)[/I]
[*][B]aegis.wand[/B] — Receive and use the claim wand [I](default: true)[/I]
[*][B]aegis.claim[/B] — Claim land [I](default: true)[/I]
[*][B]aegis.unclaim[/B] — Unclaim land [I](default: true)[/I]
[*][B]aegis.resize[/B] — Resize claims [I](default: true)[/I]
[*][B]aegis.merge[/B] — Merge adjacent plots [I](default: true)[/I]
[*][B]aegis.home[/B] — Teleport to plot spawn [I](default: true)[/I]
[*][B]aegis.spawn[/B] — Teleport to world spawn [I](default: true)[/I]
[*][B]aegis.setspawn[/B] — Set plot spawn [I](default: true)[/I]
[*][B]aegis.visit[/B] — Open travel menu [I](default: true)[/I]
[*][B]aegis.stuck[/B] — Use unstuck command [I](default: true)[/I]
[*][B]aegis.ledger[/B] — View ClaimBlock ledger [I](default: true)[/I]
[*][B]aegis.welcome[/B] — Set welcome message [I](default: true)[/I]
[*][B]aegis.farewell[/B] — Set farewell message [I](default: true)[/I]
[*][B]aegis.notify[/B] — Toggle notifications [I](default: true)[/I]
[*][B]aegis.rename[/B] — Set plot name [I](default: true)[/I]
[*][B]aegis.setdesc[/B] — Set plot description [I](default: true)[/I]
[*][B]aegis.like[/B] — Give plot reputation [I](default: true)[/I]
[*][B]aegis.market[/B] — View marketplace [I](default: true)[/I]
[*][B]aegis.sell[/B] — List plot for sale [I](default: true)[/I]
[*][B]aegis.unsell[/B] — Remove from sale [I](default: true)[/I]
[*][B]aegis.auction[/B] — View auctions [I](default: true)[/I]
[*][B]aegis.zone[/B] — Manage sub-zones [I](default: true)[/I]
[*][B]aegis.level[/B] — Access leveling [I](default: true)[/I]
[*][B]aegis.biome[/B] — Access biome menu [I](default: true)[/I]
[*][B]aegis.kick[/B] — Kick from own plot [I](default: true)[/I]
[*][B]aegis.ban[/B] — Ban from own plot [I](default: true)[/I]
[*][B]aegis.unban[/B] — Unban from own plot [I](default: true)[/I]
[*][B]aegis.earn.blocks[/B] — Earn ClaimBlocks via playtime [I](default: true)[/I]
[*][B]aegis.claimblocks.exchange[/B] — Access ClaimBlocks exchange [I](default: true)[/I]
[*][B]aegis.claimblocks.buy[/B] — Buy ClaimBlocks [I](default: true)[/I]
[*][B]aegis.claimblocks.sell[/B] — Sell ClaimBlocks [I](default: true)[/I]
[/LIST]
[/SPOILER]

[SIZE=4][B]Admin Permissions[/B][/SIZE]

[LIST]
[*][B]aegis.admin[/B] — All admin permissions [I](default: op)[/I]
[*][B]aegis.admin.manage[/B] — Edit other players' plots [I](default: op)[/I]
[*][B]aegis.admin.bypass-limits[/B] — Ignore plot limits [I](default: op)[/I]
[*][B]aegis.admin.migrate[/B] — Import from other plugins [I](default: op)[/I]
[*][B]aegis.admin.wand[/B] — Get Sentinel's Scepter [I](default: op)[/I]
[*][B]aegis.reload[/B] — Reload configuration [I](default: op)[/I]
[*][B]aegis.bypass[/B] — Bypass all protections [I](default: op)[/I]
[*][B]aegis.convert[/B] — Convert plot to Server Zone [I](default: op)[/I]
[*][B]aegis.setwarp[/B] — Set server warps [I](default: op)[/I]
[*][B]aegis.delwarp[/B] — Delete server warps [I](default: op)[/I]
[*][B]aegis.claimblocks.exchange.bypass[/B] — Bypass exchange limits [I](default: op)[/I]
[*][B]aegis.claimblocks.selllock.bypass[/B] — Bypass sell lock timer [I](default: op)[/I]
[*][B]aegis.notify.others[/B] — Toggle notifications for others [I](default: op)[/I]
[*][B]aegis.notify.bypass[/B] — Receive notifications when disabled [I](default: op)[/I]
[/LIST]

[HR][/HR]

[SIZE=5][B]Configuration[/B][/SIZE]

AegisGuard uses a comprehensive config.yml with sections for:

[SIZE=4][B]Data Storage[/B][/SIZE]

[CODE]storage:
  type: "yml"  # Options: yml, sqlite, mysql
  database:
    file: "aegisguard.db"
    # MySQL options available[/CODE]

[SIZE=4][B]Economy Settings[/B][/SIZE]

[CODE]economy:
  enabled: true
  use_vault: true
  claim_cost: 100.0
  resize_cost_per_block: 10.0
  refund_on_unclaim: true
  refund_percent: 50.0[/CODE]

[SIZE=4][B]ClaimBlocks Configuration[/B][/SIZE]

[CODE]claim_blocks:
  enabled: true
  starting_blocks: 500
  earn:
    playtime:
      enabled: true
      interval_minutes: 10
      blocks_per_interval: 50
      anti_afk:
        enabled: true
        required_activity_seconds: 300[/CODE]

[SIZE=4][B]Localization[/B][/SIZE]

Multiple language support with split bundle files:

[LIST]
[*][B]old_english[/B] — Medieval/fantasy themed
[*][B]hybrid_english[/B] — Mix of old and modern
[*][B]modern_english[/B] — Contemporary language
[*][B]spanish_mx[/B] — Mexican Spanish
[*][B]spanish_ar[/B] — Argentine Spanish
[/LIST]

[HR][/HR]

[SIZE=5][B]Plugin Integrations[/B][/SIZE]

[SIZE=4][B]Economy & Utilities[/B][/SIZE]

[LIST]
[*][B]Vault[/B] — Economy transactions, ClaimBlocks exchange
[*][B]PlaceholderAPI[/B] — Custom placeholders for scoreboards/chat
[*][B]CoreProtect[/B] — Block logging compatibility
[/LIST]

[SIZE=4][B]Map Visualization[/B][/SIZE]

[LIST]
[*][B]Dynmap[/B] — Claim overlay with customizable colors
[*][B]BlueMap[/B] — Claim markers and labels
[*][B]Pl3xMap[/B] — Claim visualization
[/LIST]

[SIZE=4][B]Protection Compatibility[/B][/SIZE]

AegisGuard can coexist with other protection plugins:

[LIST]
[*][B]WorldGuard[/B] — Region overlap policy
[*][B]GriefPrevention[/B] — Claim import/migration
[*][B]GriefDefender[/B] — Claim import/migration
[*][B]Towny[/B] — Town area detection
[*][B]Residence[/B] — Region detection
[*][B]Lands[/B] — Claim import/migration
[/LIST]

[SIZE=4][B]Other Integrations[/B][/SIZE]

[LIST]
[*][B]Discord[/B] — Webhook notifications for claim events
[*][B]mcMMO[/B] — Skill compatibility
[*][B]Jobs[/B] — Job integration
[/LIST]

[HR][/HR]

[SIZE=5][B]Installation[/B][/SIZE]

[LIST=1]
[*][B]Download[/B] AegisGuard from [URL='https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/']Spigot[/URL], [URL='https://hangar.papermc.io/snazzyatoms/AegisGuard']Hangar[/URL], or [URL='https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy']CurseForge[/URL]
[*][B]Place[/B] the JAR file in your server's plugins/ folder
[*][B]Start[/B] your server to generate configuration files
[*][B]Configure[/B] plugins/AegisGuard/config.yml to your preferences
[*][B]Reload[/B] with /agadmin reload or restart the server
[/LIST]

[SIZE=4][B]Optional Dependencies[/B][/SIZE]

[LIST]
[*][B]Vault[/B] + Economy Provider (EssentialsX, CMI, etc.) — For monetary transactions
[*][B]PlaceholderAPI[/B] — For placeholder support
[*][B]Dynmap/BlueMap/Pl3xMap[/B] — For map visualization
[/LIST]

[HR][/HR]

[SIZE=5][B]Quick Links[/B][/SIZE]

[LIST]
[*][B]Documentation[/B] — [URL='https://github.com/snazzyatoms/AegisGuard/wiki']Wiki & Guides[/URL]
[*][B]Support[/B] — [URL='https://discord.gg/Y2NpuR7UZE']Discord Community[/URL]
[*][B]Bug Reports[/B] — [URL='https://github.com/snazzyatoms/AegisGuard/issues']GitHub Issues[/URL]
[*][B]Releases[/B] — [URL='https://github.com/snazzyatoms/AegisGuard/releases']GitHub Releases[/URL]
[*][B]Spigot[/B] — [URL='https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/']SpigotMC[/URL]
[*][B]Hangar[/B] — [URL='https://hangar.papermc.io/snazzyatoms/AegisGuard']PaperMC Hangar[/URL]
[*][B]CurseForge[/B] — [URL='https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy']CurseForge[/URL]
[/LIST]

[HR][/HR]

[SIZE=5][B]Support[/B][/SIZE]

Need help? Have suggestions?

[LIST]
[*][B]Discord[/B]: [URL='https://discord.gg/Y2NpuR7UZE']Join our community[/URL]
[*][B]Wiki[/B]: [URL='https://github.com/snazzyatoms/AegisGuard/wiki']Read the documentation[/URL]
[*][B]Issues[/B]: [URL='https://github.com/snazzyatoms/AegisGuard/issues']Report bugs[/URL]
[/LIST]

[HR][/HR]

[CENTER][B]AegisGuard v1.2.5[/B] | Made with care by [URL='https://github.com/snazzyatoms']snazzyatoms[/URL][/CENTER]