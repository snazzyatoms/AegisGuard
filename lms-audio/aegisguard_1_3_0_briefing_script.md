# AegisGuard 1.3.0 spoken briefing

Two hosts: **Mira** (player-facing) and **Rex** (staff / ops).

Source window: public GitHub Release **1.2.7** through branch `V1.3.0` HEAD, including the Claim Status restyle. **No public 1.3.0 GitHub Release.** Config schema **1286**.

## How to listen

1. Open [LM Studio](https://lmstudio.ai/), load a local TTS model (Kokoro is a good default), and start the local server on port `1234`.
2. Open `AegisGuard_1.3.0_Briefing.ipynb` in Jupyter, VS Code, or Cursor, then run all cells. Audio plays in the notebook and is also written to `out/aegisguard-1.3.0-briefing.wav`.
3. Or from this folder: `python generate_briefing.py`

Voices default to Kokoro `af_bella` (Mira) and `am_michael` (Rex). Override with `LMS_VOICE_MIRA`, `LMS_VOICE_REX`, `LMS_TTS_MODEL`, or `LMS_BASE`.

## Dialogue

**Mira:** Welcome back. This is a spoken briefing of AegisGuard from the last public GitHub Release, one point two point seven, through the current one point three point zero work on the V one point three point zero branch.

**Rex:** Important context first. One point three point zero is soaking on that branch. It is not a public GitHub Release. The published release on GitHub stays one point two point seven. Existing one point two point seven plots, roles, economy data, and customized config remain valid. Config schema is twelve eighty-six, with automatic migration.

**Mira:** Java twenty-one or newer. Minecraft one point twenty or newer. Paper, Purpur, Spigot, and Folia. Do not use Bukkit global reload. Use AegisGuard reload after a proper restart when you swap the jar.

**Rex:** The original one point three point zero spine was seven milestones. Milestone one: the Staff Audit Ledger. Sensitive admin work lands in a structured trail you can filter. Restore, repair, migration, bypass, Guest Passes, Lockdown, and Alliance activity sit in one place.

**Mira:** Milestone two: Temporary Guest Passes. Time-limited plot access that expires by itself. Presets cover visitor, event guest, temporary builder, and temporary trusted guest. You can use wall-clock time, or Active Playtime, where the clock only ticks while that player is online. Expiry and revoke never rewrite permanent roles.

**Rex:** Milestone three: Emergency Plot Lockdown. Fast, reversible, plot-scoped. Grief, a dispute, or maintenance: lock the sensitive actions, then lift it from the plot menu. It is not a world wipe and it is not a permanent ban.

**Mira:** Milestone four: Realm Profiles and Noticeboards. A plot can have a public identity: display name, category, greeting, description, and a noticeboard visitors can read from travel and discovery.

**Rex:** Milestone five: clearer player guidance. When a blocked action fires, the message now points to the next useful step, including Guest Pass guidance where that applies. The first-claim walkthrough is optional, skippable, never blocks claiming, and you can replay it from Settings or slash A G guide.

**Mira:** Milestone six: Routes and Checkpoints. Staff publish named exploration routes with ordered stops. Players discover checkpoints by proximity, track progress, and may get optional completion rewards. Optional teleport defaults off, so discovery never requires a teleport.

**Rex:** Milestone seven: Alliance Access. Alliances are completely separate from ownership, money, rentals, and administration. Membership alone grants nothing. Each plot must opt in, toggle by toggle. Enter, Interact, Containers, Build, Animals, Friendly PvP. All default off. Server-wide disallow guardrails can block owners from turning on risky ones. Alliance Entry and Friendly PvP are wired into plot protection.

**Mira:** Around that spine came a lot of everyday territory polish. My Rentals and My Tenants for contracts. A Settlements Inbox. ClaimBlocks gifts with slash A G giftblocks. Adjacent claim merge with slash A G merge. Rent confirm before Vault charges. Role nicknames, a trusted catalog role, co-owner gaining manage-members, and member capacity.

**Rex:** Staff side: Instant Approvals history is now separate from the Pending Review queue. Convert-to-server has a dedicated GUI. Wand-create and convert share one stewardship pipeline. Convert wipes old access, grants Steward to the acting staffer, and can open Claim Settings. Managing a server zone needs server-zone manage permission or the Steward role, not blanket admin.

**Mira:** Safe Travel wraps voluntary teleports: visit, markets, spawn, staff destinations. Cooldowns, confirmation, combat tagging, and safe-point search. Protection also picked up hopper, liquid, teleport, and storm wards. Hooks and protection-compat plugins stay off until a server opts in.

**Rex:** Now the later one point three point zero shop work: TradeStalls and Local Market. Local Market is the plot hub. TradeStalls are the native shop: one chest or barrel, a sign beside it, not a double-chest mall. Create from the menu or bind a sign with stall or shop on the first line.

**Mira:** Buying is not a surprise click. Preview opens first. Confirm buy is its own screen, and the purchase path uses a per-slot lock so two players cannot double-charge the same listing. Hoppers cannot siphon a stall. You can Visit Stall through Safe Travel and land at the shop.

**Rex:** External shops are not banned. MarketBridge defaults to COEXIST, so QuickShop, ChestShop, Shopkeepers, and ExcellentShop can sit beside TradeStalls. Bridge slots live around the Trade Stalls button and do not steal Merge Claims. You can tighten that to disable on bridged plots, or globally if an external shop plugin is present. Default is coexist.

**Mira:** Claim Settings stopped being one crowded chest of toggles. It is a hub now: Safety, Mechanics, Wards, and Presets. Personal plots still get HOME, FARM, and cosmetics. Server plots hide those personal extras so staff are not offered home cosmetics on spawn land.

**Rex:** Staff Tools grew to a fifty-four slot chest with the same colored section bands as the main menu. Policy, Territory, Recovery, and the Guardian Toolbelt. Expansion Queue versus Instant sits in policy and no longer collides with the Arena button. Arena is in the modules row, slot thirty-eight. Expansion mode is slot sixteen.

**Mira:** Snapshots got restored as working staff tools. The Snapshot GUI initializes lazily, only when the snapshot manager exists, so a missing recovery service does not brick menus. From the menu you can create a snapshot here, or snapshot all server zones. Scheduled snapshots exist too, default off, default interval three hundred sixty minutes, targeting server zones.

**Rex:** Hear this clearly: those snapshots are plot-data metadata. Owner, flags, access, warp, that class of record. They do not copy world builds. The success text even says builds were not copied. Do not treat a scheduled pass as a WorldEdit backup.

**Mira:** First-claim onboarding now asks language first. If the player has no saved language style, Choose Your Language opens before the walkthrough. After they pick, the walkthrough continues. Settings also stopped cycling packs one click at a time. You get a real picker of every installed pack.

**Rex:** There are nine packs in parity: Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish. Codex fallbacks stay in sync, so switching should not dump English placeholders into menus. Console logs, Discord embeds, Guardian Guide, Help, and leftover staff chat were pushed through those packs too.

**Mira:** The Guardian Guide, the in-game guidebook, was refreshed for one point three point zero. Cards cover what is new, Safe Travel, routes, the language picker, Guest Passes, Lockdown, Alliance Access, Realm identity, and Arena, including the off-by-default warning. Schema twelve eighty-six is the config contract behind that.

**Rex:** Claim Status stayed on Territory. It was not moved to another dashboard. The button still sits with plot tools. The GUI was restyled to match the one point three point zero band language: cyan Overview, orange Owner Actions, plus an access snapshot. Merge, transfer, gift ClaimBlocks, and pay upkeep early remain owner actions. All nine languages and Codex received those keys.

**Mira:** Arena did ship in this window as an optional module. Cooperative party PvE on bound server plots. Disabled by default. When you enable it, scheduling goes through an internal Arena Scheduler that is Folia-safe: entity, region, global, and async paths. Use slash A G arena diag if it will not activate on Folia. Language packs cover Arena too.

**Rex:** Other real work in the same window: Folia-safe scheduling for exchanges and GUI closes, GUI click safety, Doctor health signals, YML to SQL plot migrator with backups, richer PlaceholderAPI, opt-in Discord webhooks for market, rental, lockdown, and Guest Pass events, map marker colors for For Rent, and route guidance on the action bar.

**Mira:** If you are coming from one point two point seven: stop the server, back up plugins AegisGuard and the worlds, confirm Java twenty-one, drop the one point three point zero jar, start, let migration run, refresh the lang folder, then run doctor before you reopen. The public GitHub Release page will still show one point two point seven until a public release is deliberately cut.

**Rex:** That is the product story. AegisGuard one point three point zero is a territory platform: ClaimBlocks, TradeStalls, Guest Passes, Lockdown, optional Arena, nine languages, and staff recovery that snapshots data, not builds. Simple. Steadfast. Eternal.

**Mira:** Thanks for listening. Load the jar on a soak server, walk a claim, open Local Market, open Claim Status, and only then decide when public one point three point zero is ready.
