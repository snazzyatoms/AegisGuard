# AegisGuard 1.2.6.1 Configuration Guide

This guide is meant to help server owners set up AegisGuard quickly without having to guess which settings matter first.

## Start Here

Review these sections in order:

1. `localization`
   Choose your default language and fallback language.
2. `hooks`
   Disable integrations you do not use.
3. `storage`
   Keep `yml` for smaller servers. Move to database-backed storage later if needed.
4. `economy`
   Decide whether plots use Vault, ClaimBlocks, or both.
5. `claims`
   Set your personal claim limits, max size, and world-specific rules.
6. `protections`
   Choose what is protected by default in every newly created plot.
7. `group_plots`
   Configure shared plots, treasury behavior, and starter group claim sizing.
8. `upkeep`
   Enable this only if you want recurring claim taxes.

## Recommended Profiles

### Small survival server

Use these ideas if your server is new, private, or lightly staffed:

- `storage.type: "yml"`
- `claim_blocks.exchange.profile: safe_small`
- `expansions.approval_mode: QUEUE`
- `upkeep.enabled: false`
- `group_plots.enabled: true`
- `hooks.discord.enabled: false` if you do not actually use Discord webhooks

Why:
This keeps the server easy to manage and lowers the chance of economy abuse.

### Medium community server

Use these ideas for a public SMP with active moderators:

- `storage.type: "yml"` is still fine unless your data becomes very large
- `claim_blocks.exchange.profile: balanced_mid`
- `expansions.approval_mode: QUEUE`
- `group_plots.notifications.enabled: true`
- `titles.claim_enter_exit.mode: PER_PLAYER`
- `upkeep.enabled: true` only if you want land pressure and plot turnover

Why:
This keeps player freedom high while still giving staff control over risky changes.

### Large network or busy economy server

Use these ideas when claims, trading, and staff activity are heavy:

- Move away from `yml` storage when your dataset grows
- `claim_blocks.exchange.profile: fast_large`
- `hooks.protection_compat.enabled: true`
- Disable any unused map hooks to reduce noise
- Keep `snapshots.enabled: true`
- Keep `expansions.audit.enabled: true`
- Review `wilderness_revert` carefully before enabling aggressive cleanup

Why:
Large servers benefit most from auditability, tuned exchange limits, and fewer unnecessary hooks.

## Most Important Sections

### `economy`

This controls how claims and upgrades are paid for.

- `use_vault`: legacy master switch
- `vault.enabled`: newer Vault toggle
- `vault.fallback_to_blocks`: lets systems fall back to ClaimBlocks if Vault is unavailable
- `fair_pricing.enabled`: strongly recommended so large first claims are not underpriced

### `claims`

This is your main land-claim behavior section.

- `max_claims_per_player`: total personal plot count
- `min_radius` and `max_radius`: claim size limits
- `max_area`: upper claim area cap
- `per_world`: lets you disable or tighten claiming in specific worlds

### `claim_blocks`

This section controls progression and exchange.

- `starting_blocks`: first balance for new players
- `earn.playtime`: passive rewards
- `earn.player_opt_out_allowed`: lets players disable passive earnings
- `exchange.profile`: best place to tune the buy and sell system quickly

### `group_plots`

This controls shared plots and group treasury behavior.

- `invite_max_distance`: keeps invites local and harder to abuse
- `min_members_to_claim`: minimum group size before claiming
- `starter.base_area`: free starter group claim size
- `starter.member_age_minutes`: anti-abuse delay before members count toward free size
- `starter.removal_lock_minutes`: stops instant kick abuse after using starter sizing

### `protections`

These are the defaults for brand new plots.

- `pvp_protection`
- `container_protection`
- `entities_protection`
- `farm_protection`
- `tnt-damage`
- `fire-spread`
- `piston-use`

If you want a more survival-friendly setup, leave most of these enabled.

### `staff_access`

This is where you define elevated server-plot management permissions.

- `global_manage_permissions`
- `server_zone_manage_permissions`
- `market_plot_manage_permissions`

Use this if you want admins or delegated staff to manage server plots without relying on bypass mode.

## Good Default Choices

If you want a clean, approachable setup for most servers:

- Keep `fair_pricing.enabled: true`
- Keep `snapshots.enabled: true`
- Keep `expansions.snapshots.enabled: true`
- Keep `group_plots.notifications.enabled: true`
- Keep `titles.claim_enter_exit.mode: PER_PLAYER`
- Keep `protections.container_protection: true`
- Keep `protections.entities_protection: true`
- Keep `protections.farm_protection: true`

## Settings To Review Carefully

These toggles are powerful and should be chosen intentionally:

- `upkeep.enabled`
- `upkeep.unclaim_on_fail`
- `wilderness_revert.enabled`
- `admin.unlimited_plots`
- `admin.trust_operators`
- `hooks.protection_compat.overlap_policy`

## Permissions Notes

The cleaned `plugin.yml` groups permissions into:

- player permissions
- admin permissions
- notification permissions
- staff/server-plot permissions
- ClaimBlocks exchange permissions

If players should use the ClaimBlocks exchange, remember to grant the relevant permission nodes:

- `aegis.claimblocks.exchange`
- `aegis.claimblocks.buy`
- `aegis.claimblocks.sell`

## Safe Editing Workflow

1. Change only one section at a time.
2. Save the file.
3. Run `/agadmin reload`.
4. Check the console for YAML or hook errors.
5. Test with a normal player account and an admin account.

## Practical Maintenance Advice

- Keep snapshots enabled if your server uses expansions or admin recovery tools.
- Disable hooks you do not use.
- Use `QUEUE` expansion approval if you want staff oversight.
- Keep player notification toggles available unless you want a stricter server style.
- Review your `group_plots` anti-abuse values before advertising shared plots to players.

## Files To Keep Together

For the cleanest setup, review these files as a set:

- `src/main/resources/config.yml`
- `src/main/resources/plugin.yml`
- `src/main/resources/lang/*`

That gives you the behavior, the permissions, and the player-facing wording in one pass.
