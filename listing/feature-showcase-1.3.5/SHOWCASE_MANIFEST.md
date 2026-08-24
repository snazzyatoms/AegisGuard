# AegisGuard 1.3.5 Feature Showcase

This folder contains a 16-panel visual presentation set for the AegisGuard
SpigotMC resource page. All images are 1536 x 1024 PNG files.

## Recommended presentation order

1. `01-claim-scepter.png` - visual claiming and the Aegis Scepter
2. `05-access-roles-guest-passes.png` - roles, timed Guest Passes, and Lockdown
3. `06-zones-shared-territories.png` - sub-zones, group plots, and claim merge
4. `02-progress-ascension.png` - Ascension, disciplines, and Expansion Horizons
5. `07-claimblocks-land-economy.png` - ClaimBlocks, exchange, expansion, and upkeep
6. `08-markets-auctions-trade.png` - Local Markets, TradeStalls, auctions, and listings
7. `09-rentals-real-estate.png` - plot, room, shop, and zone rental contracts
8. `10-realm-identity-profiles.png` - Realm Profiles, greetings, noticeboards, and titles
9. `11-discovery-travel-atlas.png` - discovery, favorites, visits, and destinations
10. `12-routes-safe-travel.png` - routes, checkpoints, cooldowns, and safe arrivals
11. `13-alliances-social-realms.png` - opt-in Alliance Access and shared projects
12. `14-arena-cooperative-dungeons.png` - parties, mob waves, rewards, and recovery
13. `16-player-experience-guidance-style.png` - walkthroughs, languages, titles, cosmetics, and biome variety
14. `15-server-control-worlds-modules.png` - server zones, world rules, and module controls
15. `04-staff-recovery-audit.png` - snapshots, Doctor, restoration, and Audit Ledger

`03-economy-markets-rentals-legacy.png` is an optional broad economy section
opener. It can be omitted when the three detailed economy panels are used.

## Coverage notes

The panels group related configuration switches into player-readable systems.
Together they cover the active protection, access, zoning, group, progression,
expansion, ClaimBlock, economy, market, stall, auction, upkeep, rental, profile,
noticeboard, discovery, travel, route, alliance, social, activity, arena,
language, guidance, cosmetic, title, biome, snapshot, audit, server-zone, and
world-control areas. Wilderness revert is not advertised because it ships off
by default and is SQL-only.

These are presentation illustrations, not literal screenshots of every menu.
Pair them with accurate text describing the implemented behavior.

## Visual prompt system

The twelve new panels were generated with the built-in image generator using
the existing AegisGuard cards as style references: cinematic voxel-game
scenes, midnight vignettes, navy-and-gold heraldry, white condensed headings,
gold feature subtitles, and one clear mechanic story per panel. Unsupported or
future concepts were excluded.

## SpigotMC publishing workflow

1. Host the selected PNG files on a stable HTTPS image host.
2. Insert each hosted URL with an `[IMG]...[/IMG]` tag.
3. Put only a short paragraph or compact feature list below each panel.
4. Keep the detailed command and installation sections in collapsible spoilers.
5. Preview the final BBCode at desktop and mobile widths before publishing.
