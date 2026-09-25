# NotebookLM Source Brief

## AegisGuard 1.4.0: What Changed Since 1.3.5 and 1.3.0

### Purpose of this source

This document is a factual source brief for a long-form, conversational audio overview. It is designed to help two NotebookLM hosts discuss how AegisGuard evolved from the 1.3.0 generation, through the stabilizing 1.3.5 follow-up, into the 1.4.0 source line.

The discussion should be useful to three audiences at once:

- players who want to understand what they can now do;
- server owners deciding whether and how to update;
- staff members who care about safety, recovery, permissions, compatibility, and defaults.

The central framing should be:

> AegisGuard 1.3.0 established the modern territory-management generation. Version 1.3.5 kept that foundation, added Teleport Beacons, completed and hardened recovery, improved Bedrock usability, and fixed issues found during 1.3.0 soak and review. The 1.4.0 source line builds outward from that stabilized base with a unified Travel Atlas, faster claiming, safer role continuity, succession, caravans, server sanctuary controls, independent Aegis text radios, Hearth rooms, optional voice-room integration, and additional staff/player workflow improvements.

Do not call 1.3.5 “only bug fixes and beacons.” That is directionally close if the point is that it remained in the same broad generation as 1.3.0, but it omits meaningful recovery, backup, Bedrock, language, ClaimBlock, and claim-sizing work.

Do not describe the current 1.4.0 checkout as proof that a public GitHub Release page was published. Its release-notes file explicitly describes the `V1.4.0` source line and says it is not a GitHub Release. It is accurate to discuss the verified 1.4.0 source, documentation, build, and upgrade path.

---

## Short version history

### 1.3.0: the baseline generation

Version 1.3.0 was the public follow-up to 1.2.7. It established many systems that remain part of AegisGuard in 1.3.5 and 1.4.0 rather than being newly invented in 1.4.0.

The verified 1.3.0 baseline included:

- automatic migration from 1.2.7 without replacing existing plots, owners, flags, members, or customized settings;
- a module switchboard, with most listed systems on by default, wilderness revert off by default, and disabled-module entries hidden from menus;
- a structured staff Audit Ledger for sensitive actions;
- temporary Guest Passes with wall-clock or Active Playtime expiry;
- Emergency Plot Lockdown;
- Realm Profiles, categories, greetings, descriptions, and visitor Noticeboards;
- clearer blocked-action guidance and an optional replayable first-claim walkthrough;
- staff-published Routes and proximity-discovered checkpoints;
- Alliance Access that was separate from ownership, money, rentals, and administration, with plot-level access toggles defaulting off;
- a direct language picker covering nine language packs;
- server-zone stewardship, with manage access based on permission or the Steward role rather than blanket administration;
- shared Safe Travel behavior and managed destinations;
- staff health and recovery tools;
- an optional cooperative PvE Arena module with Folia-aware scheduling;
- continuing core systems such as claims, protection, roles, rentals, markets, auctions, ClaimBlocks, Ascension, progression, group plots, and staff administration.

The discussion should emphasize that 1.3.0 already had a substantial territory platform. In particular, Safe Travel, destinations, routes, Alliance Access, group plots, recovery snapshots, language support, and the overall player/staff menu system predate 1.4.0.

### 1.3.0 limitations later addressed by 1.3.5

The 1.3.0 documentation listed known soak issues. These included restore correctness after ownership changes, restore scheduling safety on Folia, snapshot pruning, role nickname rollback, empty player-menu footer slots, staff-menu entries for disabled modules, and timed-lockdown state changing as a side effect of plot saving.

These are useful in the audio story because they explain why 1.3.5 matters even though it was not a wholesale redesign. It was the release where the 1.3.0 generation was made safer and more operationally complete.

### 1.3.5: same broad generation, more complete and stable

Version 1.3.5 was explicitly described as the public follow-up to 1.3.0. The most visible new player feature was Teleport Beacons, but the release did considerably more.

#### Teleport Beacons

Players could place linked teleport pads on claims they manage, confirm them through a GUI, and land at the paired pad. Visit, market, and auction travel could require a public arrival beacon. Charging policy could be owner choice, a server-wide fee, or off; optional fees could pay the plot owner. Beacon travel used the existing Safe Travel system, while `/ag home` remained the personal plot-spawn command.

Later 1.3.5 hardening prevented double charging, kept pads through claim merges, and unbound pads when plots were deleted.

#### Recovery and backups

The 1.3.0 snapshot foundation became a much more complete recovery system in 1.3.5:

- claim-data snapshots covered guest passes, lockdown, alliance access, noticeboards, ownership, flags, members, bounds, market, rental, auction, progression, social, spawn, cosmetic, warp, zone, stall, listing, and other versioned plot state;
- optional WorldEdit or FastAsyncWorldEdit build backups could copy a plot's block volume;
- build backup remained off by default, and Folia required FAWE by default;
- automatic player-plot and server-zone snapshots could run gradually in bounded batches, also off by default;
- restore showed a preview and created a rescue snapshot before changing live data;
- only one restore could run per plot, with a maintenance lock during the operation;
- interrupted operations paused for staff review instead of automatically replaying;
- restore paths failed closed rather than reporting a false completion.

This is one reason “1.3.5 was just beacons and fixes” is incomplete. Recovery moved from a useful 1.3.0 feature into a safer operational system.

#### Stability and usability refinements

Version 1.3.5 also:

- made language placeholders such as `{PLOT}`, `{MIN}`, and `{PLAYER}` work in translated `lang/` strings rather than only fallback English;
- fixed ClaimBlock accounting so expansion could not drive the wallet negative;
- checked the group leader's ClaimBlocks for group claims;
- enforced `claims.min_radius` on both axes, preventing long, skinny claims from passing a one-axis check;
- added a carefully scoped repair for old ClaimBlock double counting;
- improved Geyser/Floodgate chest-GUI controls so Bedrock players did not depend on right-click behavior unavailable or unreliable on their client;
- corrected the known 1.3.0 snapshot, menu, module-visibility, lockdown, and scheduler issues.

The fair spoken summary is: 1.3.5 stayed within the 1.3.0 generation, but it added the beacon travel layer and made recovery, cross-platform interaction, claiming, localization, and live-server behavior notably more mature.

---

## What 1.4.0 changes

### Upgrade and compatibility foundation

The 1.4.0 source line builds on public 1.3.5. Existing 1.2.7, 1.3.0, and 1.3.5 data remain valid. The documented configuration schema moves from `1294` to `1310`; migration merges new keys and makes a timestamped backup rather than replacing existing customized configuration.

The documented platform baseline remains:

- Java 21 or newer;
- Minecraft 1.20 or newer;
- Spigot, Paper, Purpur, Folia, and compatible Bukkit forks.

The recommended update remains a full-stop JAR swap, followed by a complete server start. The documentation warns against Bukkit's global `/reload`. Existing plots load as-is. Existing plots also retain classic visitor arrival by default, which avoids unexpectedly requiring old servers to have beacon pads.

This compatibility-first approach is important. The story is not “1.4.0 throws away 1.3.” It is “1.4.0 adds systems while preserving existing land and defaults.”

### Travel Atlas: from separate travel features to one travel surface

Version 1.3.0 already had Safe Travel, destinations, routes, and a visit experience. Version 1.3.5 added Teleport Beacons. Version 1.4.0 brings those ideas together in a clearer Travel Atlas.

The Atlas now presents:

- Destinations;
- My Beacons;
- Arrival;
- Caravans.

`/ag beacon` opens My Beacons rather than creating a competing travel interface. Plot managers choose how visitors arriving through public listings should land:

- `classic` uses the established Safe Travel path to the plot spawn or listing point;
- `beacon` requires a public arrival pad.

The command is `/ag arrival <classic|beacon>`. A traveler can override the plot's choice when the owner permits it. If beacon arrival is selected but no valid public arrival pad exists, public-listing travel fails closed instead of silently sending the visitor somewhere else. Existing plots stay on classic arrival after upgrading. `/ag home` remains the personal plot-spawn command.

Beacons retain clear limits: one pad per block and one directed A-to-B link. This is an integration and safety refinement, not a claim that 1.4.0 invented beacons.

### Quick-Claim and clearer navigation

`/ag quickclaim [radius]`, with `/ag qc` as an alias, claims a square around the player through the existing claim pipeline. There is also a Territory-hub entry. Because it uses the normal pipeline, it still respects normal checks, costs, and `max_claims_per_player` rather than bypassing protection rules.

Navigation is more origin-aware. Settings is shown only from the main player hub and staff menu, and Back returns to the interface that opened the current page. This sounds small next to caravans or radios, but it reduces menu duplication and dead-end navigation during everyday use.

### Restore-safe roles and member locks

Recovery in 1.3.5 became much more complete. Version 1.4.0 addresses a different recovery concern: a technically successful restore should not silently undo current access decisions.

Snapshot restore merges members and roles by default when `snapshots.restore.protect_roles` is enabled. Owners can lock members. The commands `/ag roles lock`, `/ag roles unlock`, and `/ag roles undo` protect or reverse recent role changes and record the action in the audit trail.

The player/server-owner value is continuity: restoring land or plot data is less likely to surprise current trusted members.

### Guardian Succession and stewardship continuity

Version 1.4.0 adds an explicit continuity path for plots whose primary owner becomes inactive or hands off responsibility.

The system includes:

- `/ag heir [player|clear]` to name or clear an heir;
- `/ag succession assume`, `/ag succession rollback`, and `/ag succession menu`;
- a Stewardship page in the access workflow;
- inactivity-based assumption rules;
- transfer cooldowns;
- a short rollback window;
- automatic member locking when `co_owner` or `steward` is granted.

The useful discussion angle is governance rather than drama: succession reduces the need for an administrator to improvise ownership recovery when a community plot outlives one player's activity.

### Caravans and Trade Routes

Caravans turn the existing public beacon network into an economic delivery path. `/ag caravan` dispatches a shipment along public beacon hops. The documented flow charges and then delivers, rather than pretending delivery occurred before its cost and route state were recorded.

Features include:

- route planning across public beacon hops;
- insurance;
- weighted route events such as safe travel, ambush, toll, boon, and delay;
- YAML and SQL persistence;
- resume after restart;
- Folia-safe ticking;
- a module gate under `modules.caravans`.

Avoid describing this as a real-time vehicle simulation unless further evidence is supplied. The release documentation supports shipment dispatch, route events, persistence, and delivery over beacon routes.

### Server sanctuary controls

On server-owned plots such as spawn, hub, or other staff-managed land, authorized staff can opt into four Safety settings:

- Keep Health;
- Keep Hunger;
- Keep XP;
- Keep Inventory.

Keep Health and Keep Hunger prevent ordinary loss while the player is inside the server plot. Eating can still raise hunger. Keep XP and Keep Inventory affect deaths in the protected server plot. Void damage and `/kill` still apply.

Important boundaries:

- all four settings start off;
- they apply to server plots, not ordinary personal player claims;
- staff need appropriate manage or Steward authority;
- there is no general hub-flight toggle in the Safety page.

These boundaries prevent “sanctuary” from being misrepresented as universal invulnerability or a personal-claim perk.

### Flight skills

Plot flight is tied to progression rather than being a sanctuary flag. It unlocks at Ascension or Horizon level 30 for the plot owner and trusted players on that plot. Staff can have always-available plot flight when `flight_skill.staff_always` is enabled and the appropriate permission is present. Staff can also grant or clear a temporary flight skill with `/agadmin skill fly <player> [seconds]` and `/agadmin skill clear <player>`.

Keep this topic separate from server sanctuary in the discussion. Sanctuary does not own or automatically enable flight.

### Claim presets and staff seasons

After a successful claim, a preset chooser offers Home, Shop, Arena, or Farm for player plots, and Spawn, Hub, Shop, or Arena for server plots. Presets apply a starting profile but do not overwrite sanctuary settings, Hearth, or flight.

Staff seasons are a presentation layer rather than a replacement for plots or routes. `/agadmin season` can feature selected plots in Atlas discovery and selected routes in the Routes browser.

### Independent Aegis Radio and Plot Frequency system

This must be a standalone segment in the audio. Aegis Radio is not a Hearth subfeature. The radios work whether Hearth is on or off.

These are same-server, opt-in vanilla text channels. Minecraft's normal `T` chat remains text; the radio system does not add microphone audio and does not require a client mod.

#### Plot Frequency

Plot Frequency is the plot-member radio.

- `/ag chat` toggles the player's current Plot Frequency.
- `/ag frequency` is an alias.
- `/ag chat <message>` sends a one-time message to the plot Frequency without requiring the player to remain tuned.
- `/ag chat off` turns off whichever Aegis channel the player currently has selected and returns their normal messages to public/global chat.
- Permission: `aegis.chat`.

The intended audience is plot members, not arbitrary visitors. Online members of that plot receive the Frequency message.

#### Alliance radio

- `/ag chat alliance` toggles alliance radio.
- `/ag chat alliance <message>` sends directly to the alliance channel.
- `/ag chat alliance off` turns that channel off.
- `/ag chat alliance name <title>` lets the alliance leader set its radio title, capped at 32 characters.
- Permission: `aegis.chat.alliance`.

Alliance radio reaches every online member of the speaker's alliance. It does not grant Alliance Access, ownership, money, rentals, or administrative authority. Those remain separate systems, just as they were in the 1.3.0 Alliance Access design.

#### Group radio

- `/ag chat group` toggles group radio.
- `/ag chat group <message>` sends directly to the group channel.
- `/ag chat group off` turns that channel off.
- `/ag chat group name <title>` lets the group leader name the channel.
- Permission: `aegis.chat.group`.

It reaches every online member of the speaker's group.

#### Staff radio

Staff can use:

- `/ag staff`;
- `/ag staffchat`;
- `/agadmin staffchat`;
- `/agadmin sc` as the documented admin alias.

The channel reaches online staff with `aegis.admin.staffchat` or the plugin's administrative authority. Permission: `aegis.admin.staffchat`.

#### Opt-in and exclusive-channel behavior

Each player can be tuned to only one Aegis channel at a time: Plot Frequency, alliance, group, or staff. Selecting a different channel replaces the previous selection. While tuned, that player's ordinary chat messages go to the selected Aegis channel instead of public/global chat. Turning the channel off returns them to public chat.

Receiving a member channel message does not require every recipient to tune to that same channel first; the documented behavior is that online members receive the relevant plot, alliance, or group broadcast.

Servers that already use another chat plugin can leave Aegis radios unused or disable alliance, group, and staff channels with their respective configuration switches.

#### Java, Bedrock, Paper, and Folia behavior

Java and Bedrock players use the same radio commands. Floodgate or Geyser does not introduce a separate radio GUI or require a Bedrock client add-on.

The user-visible behavior is intended to be the same on Paper and Folia. Chat interception begins from Minecraft's asynchronous chat event, then AegisGuard schedules channel delivery through the supported player/main scheduling path. On conventional Paper-family scheduling this resolves through the main-thread path; on Folia it uses entity/region-safe scheduling. This matters because a chat feature should not achieve privacy by performing unsafe player operations from the asynchronous chat thread.

The correct compatibility claim is “the implementation is designed and tested for the project's supported Paper and Folia paths,” not “every possible third-party chat plugin combination is guaranteed.” Server owners using a separate formatter or chat-routing plugin should stage-test the combination.

### Hearth rooms: public-chat spaces, not radios

Hearth is a separate opt-in system for public text chat. Plot owners and server-plot stewards can turn it on in Claim Settings under Safety. It begins off.

When Hearth is enabled, public chat is limited to the room the player occupies. A room is either:

- a 3D subplot created through `/ag subplot`, such as a house, pit, or lobby; or
- the remainder of the plot when the player is not inside a subplot.

People outside cannot hear inside, and people inside cannot hear the street. Staff with `aegis.admin.hearth` can hear every text room and are not muffled when speaking.

The relationship to radios is simple:

- Aegis radios are explicit member/staff channels and work independently of Hearth.
- Hearth filters public chat that was not redirected into an Aegis radio.
- Neither system should be described as owning the other.

### Optional Simple Voice Chat hook

Minecraft does not include built-in microphone voice chat. If the separate Simple Voice Chat server plugin and matching player client mod are installed, Hearth rooms can also become isolated voice groups.

AegisGuard still starts without Simple Voice Chat. Player-created voice groups are left alone unless the server explicitly enables the override setting. The hook is documented as Folia-safe: voice-network work transitions to supported scheduling, room changes are coalesced, and reload resynchronizes groups.

Do not say that Aegis Radio becomes voice radio. The Aegis plot, alliance, group, and staff radios described above are vanilla text channels. The optional voice hook applies to Hearth room isolation.

### Language completeness

The 1.4.0 additions ship with real language keys across the nine included language packs, including Hearth, sanctuary settings, claim presets, staff seasons, flight skills, and voice-hook console messages. Fallback Java English is used only if a key is missing. Language synchronization adds missing keys without overwriting an administrator's existing translation, and `lang/overrides.yml` remains the highest-priority customization layer.

---

## Suggested long-audio discussion structure

The following structure is suitable for approximately 55 to 80 minutes, depending on how many examples the hosts use.

### Segment 1 — Set the timeline and correct the easy misconception (5–7 minutes)

Opening prompt:

> “Before we list shiny features, what did 1.3.0 already establish, and is it fair to call 1.3.5 just bug fixes plus beacons?”

Key points:

- 1.3.0 is the baseline modern territory generation.
- 1.3.5 is a follow-up in the same generation.
- Beacons are its headline player feature, but recovery, backups, Bedrock controls, claiming rules, language placeholders, and live-server fixes are also material.
- 1.4.0 is additive and migration-aware rather than a reset.

### Segment 2 — What owners and players already had in 1.3.0 (7–10 minutes)

Prompts:

- “Which systems would be inaccurate to advertise as brand-new in 1.4.0?”
- “How did Guest Passes, Lockdown, Alliance Access, Routes, Profiles, and the module switchboard change day-to-day territory management?”
- “Why does the opt-in, default-off approach matter for risky alliance permissions?”

Use this segment to establish that AegisGuard was already more than basic claim protection.

### Segment 3 — Why 1.3.5 mattered operationally (8–11 minutes)

Prompts:

- “What did Teleport Beacons add to the existing Safe Travel model?”
- “How did recovery change from claim-data snapshots into a safer operational workflow?”
- “Which 1.3.0 soak issues would a server owner actually notice?”
- “Why are default-off build backups and staged restore testing responsible defaults?”

Avoid making backup capability sound automatic by default. Both automatic data snapshots and build copies require server-owner choice.

### Segment 4 — Travel Atlas, Quick-Claim, and role continuity in 1.4.0 (8–11 minutes)

Prompts:

- “What does it mean to finish the Travel Atlas rather than merely add another travel menu?”
- “Why do existing plots remain on classic arrival?”
- “What happens when beacon arrival has no public pad?”
- “How does Quick-Claim remain within normal limits?”
- “Why should restore merge current roles instead of blindly replacing them?”

### Segment 5 — Succession, caravans, and community continuity (8–11 minutes)

Prompts:

- “What problem does Guardian Succession solve for long-running community plots?”
- “How do member locks, heir selection, cooldowns, and rollback reduce accidental takeovers?”
- “What does a caravan actually do, and what should we avoid claiming it does?”
- “How do public beacon hops turn infrastructure into a trade route?”

### Segment 6 — Dedicated Aegis Radio deep dive (10–14 minutes)

This should be a clearly announced standalone chapter.

Prompts:

- “What is Plot Frequency, and how is `/ag chat <message>` different from staying tuned?”
- “Who hears plot, alliance, group, and staff channels?”
- “How does one-channel-at-a-time behavior prevent accidental cross-posting?”
- “What permissions should owners assign?”
- “What does Bedrock parity mean here?”
- “Why does asynchronous interception plus supported scheduling matter on Paper and Folia?”
- “Most importantly, why is Aegis Radio independent from Hearth?”

Suggested example:

> A builder uses `/ag chat` while working across a large plot, an alliance leader names the alliance channel for an event, a group coordinates its own members, and staff use `/ag staff`. Each is a separate opt-in text context; none requires Hearth or a microphone mod.

### Segment 7 — Hearth, sanctuary, flight, presets, and seasons (8–12 minutes)

Prompts:

- “How is a Hearth room different from a private radio?”
- “What can staff protect at spawn, and which damage or death cases remain?”
- “Why are all sanctuary toggles off by default and unavailable to personal plots?”
- “Why is flight a progression/skill feature instead of a sanctuary toggle?”
- “How do presets speed setup without overwriting safety decisions?”
- “What does a staff season feature rather than replace?”

### Segment 8 — Optional voice, compatibility, and upgrade advice (5–8 minutes)

Prompts:

- “What works in vanilla text chat, and what requires Simple Voice Chat?”
- “Which parts are the same for Java and Bedrock?”
- “What does Folia support mean in practical terms?”
- “What should owners back up and test before changing a production server?”
- “Why is a full restart preferable to global `/reload`?”

Close with the continuity message: 1.4.0 adds new layers without discarding existing plots or requiring old plots to adopt new arrival rules immediately.

---

## Host prompts for a natural two-person conversation

Use questions like these to keep the audio analytical rather than reading a changelog:

- “Which change is most visible to players, and which matters most to administrators?”
- “Is this genuinely new, or is it a safer integration of something introduced earlier?”
- “What is the default, and what must a server owner explicitly enable?”
- “What happens when the expected destination, member, permission, or public beacon is missing?”
- “How does this behave differently for public chat, member radio, and optional voice?”
- “What survives an upgrade?”
- “What should be tested on a staging server?”
- “Where could an enthusiastic summary accidentally overpromise?”

Useful contrast pairs:

- 1.3.0 destinations vs. 1.4.0 unified Travel Atlas;
- 1.3.5 Teleport Beacons vs. 1.4.0 selectable arrival policy;
- 1.3.5 complete snapshot data vs. 1.4.0 role-preserving restore;
- Alliance Access permissions vs. alliance radio communication;
- Aegis Radio member channels vs. Hearth public rooms;
- Hearth text rooms vs. optional Simple Voice Chat voice groups;
- server sanctuary vs. plot flight;
- default-off safety features vs. opt-in community features.

---

## Accuracy guardrails for NotebookLM

The generated discussion should follow these rules:

1. Do not present planned roadmap items as shipped features.
2. Do not call 1.3.5 only a bug-fix release; mention beacons, recovery completion, backups, Bedrock usability, ClaimBlock and claim-size corrections, language placeholders, and stability work.
3. Do not say 1.4.0 invented Safe Travel, destinations, Routes, Alliance Access, group plots, snapshots, language packs, or the core territory system.
4. Do not conflate Alliance Access with alliance radio. One controls per-plot actions; the other is communication.
5. Do not conflate Aegis Radio with Hearth. Radios work with Hearth off.
6. Do not call Aegis Radio voice chat. It is vanilla text.
7. Do not imply that Simple Voice Chat is bundled. It is optional and needs its own server plugin plus client mod.
8. Do not imply that every sanctuary effect applies everywhere. Sanctuary toggles are for eligible server plots, start off, and do not create a universal safe mode.
9. Do not imply that flight comes from Hearth or sanctuary.
10. Do not say automatic backups or build copies are on by default.
11. Do not say beacon arrival is forced on existing plots. Existing plots remain classic unless changed or a server-wide rule is deliberately enabled.
12. Do not claim a public 1.4.0 GitHub Release page based solely on this source checkout.
13. Do not guarantee compatibility with every third-party chat or server plugin combination. Recommend staging tests.
14. Do not describe Caravans as visible moving vehicles unless separate evidence is provided.
15. Keep commands and permissions exact.

---

## Command and permission reference for the audio

| Feature | Command | Permission or authority |
|---|---|---|
| Plot Frequency toggle | `/ag chat` or `/ag frequency` | `aegis.chat` |
| Plot Frequency one-shot | `/ag chat <message>` | `aegis.chat` |
| Leave current Aegis channel | `/ag chat off` | Available as the channel-off path |
| Alliance radio | `/ag chat alliance [message]` | `aegis.chat.alliance`; alliance membership |
| Name alliance radio | `/ag chat alliance name <title>` | Alliance leader plus `aegis.chat.alliance` |
| Group radio | `/ag chat group [message]` | `aegis.chat.group`; group membership |
| Name group radio | `/ag chat group name <title>` | Group leader plus `aegis.chat.group` |
| Staff radio | `/ag staff`, `/ag staffchat`, `/agadmin staffchat` | `aegis.admin.staffchat` or administrative authority |
| Quick-Claim | `/ag quickclaim [radius]`, `/ag qc` | Normal claim checks still apply |
| Arrival policy | `/ag arrival <classic|beacon>` | Plot management authority |
| Stewardship | `/ag heir`, `/ag succession ...` | Ownership/stewardship rules |
| Caravans | `/ag caravan` | Module and normal feature checks |
| Hearth room definition | `/ag subplot [name]` | Existing subplot management rules |
| Temporary flight | `/agadmin skill fly <player> [seconds]` | Staff/admin skill authority |
| Staff season overlay | `/agadmin season` | Staff/admin authority |
| Upgrade confirmation | `/agadmin transition` | Staff/admin authority |

---

## Evidence basis used for this brief

Primary local sources in the verified 1.4.0 checkout:

- `README.md`, especially “What Is New In 1.4.0,” “What Is New In 1.3.5,” and “What Is New In 1.3.0”;
- `RELEASE_NOTES_1.4.0.md`;
- `RELEASE_NOTES_1.3.5.md`;
- `RELEASE_NOTES_1.3.0.md`;
- `wiki/Permissions-and-Commands.md`;
- `aegisguard-modern/src/main/resources/plugin.yml` for permission names;
- the 1.4.0 chat command, listener, and service wiring for command dispatch, exclusive-channel state, recipient scope, and scheduler behavior;
- Git history between `origin/V1.3.0`, `origin/V1.3.5`, and `origin/V1.4.0` to distinguish release generations and avoid attributing earlier systems to 1.4.0.

The `V1.3.0..V1.3.5` history confirms that 1.3.5 centered on preparing the 1.3.5 source branch, Teleport Beacons, beacon hardening, Spigot-review soak fixes, ClaimBlock/min-radius hardening, documentation, and release preparation. The 1.3.5 release notes supply the broader recovery and backup details. The `V1.3.5..V1.4.0` history confirms the staged 1.4.0 work: Plot Frequency and visual presence, Quick-Claim, role hardening, Atlas integration, succession, caravans, Folia hardening, sanctuary, flight, seasons, Hearth, optional voice integration, and alliance/group/staff channels.

---

## Closing summary for the hosts

AegisGuard 1.4.0 should be presented as an additive expansion of a mature territory platform.

Version 1.3.0 supplied the modern baseline: governance, temporary access, profiles, routes, alliances, language choice, modularity, staff oversight, Safe Travel, recovery, and Folia-aware operation. Version 1.3.5 stabilized that generation and made it more complete, with Teleport Beacons as the headline addition and major recovery, backup, Bedrock, localization, claiming, and restore-safety improvements behind it.

The 1.4.0 source line connects and extends those foundations. Travel becomes a unified Atlas with explicit arrival policy. Claiming becomes faster without bypassing limits. Restores respect current roles. Long-lived plots gain succession. Beacon infrastructure gains caravans. Server plots gain opt-in sanctuary settings. Flight remains a separate progression skill. Presets and seasons improve setup and discovery. Hearth creates physical public-chat rooms. Optional Simple Voice Chat can mirror those rooms for microphone audio.

And, as its own headline feature, Aegis Radio adds independent opt-in text channels for plots, alliances, groups, and staff—with exact permissions, one active channel per player, Java/Bedrock command parity, and scheduler-safe behavior on the supported Paper and Folia paths.

That is the clearest accurate story: not a replacement for 1.3.x, but a compatibility-conscious expansion of what the 1.3 generation made stable.

