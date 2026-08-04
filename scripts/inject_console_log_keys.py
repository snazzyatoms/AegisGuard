#!/usr/bin/env python3
"""Inject console/Discord/activity i18n keys into all 9 language packs."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path

from deep_translator import GoogleTranslator

ROOT = Path(__file__).resolve().parents[1]
LANG_ROOT = ROOT / "aegisguard-modern/src/main/resources/lang"
CACHE_DIR = ROOT / ".tmp-lang-cache"
CACHE_DIR.mkdir(exist_ok=True)
MARKER = "# --- Console / Discord / activity localization pass ---"

ENGLISH: dict[str, str] = {
    # Console / operational
    "log_folia_detected": "Folia detected! Enabling Region Scheduler compatibility.",
    "log_standard_server": "Standard Bukkit/Spigot/Paper detected.",
    "log_codex_initialized": "Codex language engine initialized.",
    "log_codex_init_failed": "Codex language engine failed to initialize: {ERROR}",
    "log_notifications_initialized": "Notification Manager initialized.",
    "log_notifications_init_failed": "NotificationManager failed to initialize: {ERROR}",
    "log_enabled": "AegisGuard enabled.",
    "log_disabled": "AegisGuard disabled.",
    "log_paper_mob_fallback": "Paper entity movement API not found; using the Spigot mob-barrier fallback.",
    "log_paper_mob_failed": "Could not enable the Paper mob boundary: {ERROR}",
    "log_guest_pass_freeze_failed": "Failed to freeze Guest Pass sessions: {ERROR}",
    "log_save_plot_store_failed": "Failed to save plot store: {ERROR}",
    "log_save_claim_blocks_failed": "Failed to save claim blocks: {ERROR}",
    "log_claimblocks_exchange_shutdown_failed": "Failed to shut down ClaimBlocks exchange: {ERROR}",
    "log_save_snapshots_failed": "Failed to save snapshots: {ERROR}",
    "log_save_expansions_failed": "Failed to save expansion requests: {ERROR}",
    "log_save_audit_failed": "Failed to save the audit ledger: {ERROR}",
    "log_save_groups_failed": "Failed to save groups: {ERROR}",
    "log_save_routes_failed": "Failed to save routes: {ERROR}",
    "log_save_alliances_failed": "Failed to save alliances: {ERROR}",
    "log_save_territory_life_failed": "Failed to save territory life data: {ERROR}",
    "log_save_horizons_failed": "Failed to save Horizon reward data: {ERROR}",
    "log_reloaded": "AegisGuard reloaded successfully.",
    "log_autosave_error": "Auto-save error: {ERROR}",
    "log_upkeep_error": "Upkeep task error: {ERROR}",
    "log_wilderness_sql_required": "Wilderness Revert is enabled, but the active storage backend is not SQL. Skipping wilderness revert startup.",
    "log_wilderness_error": "Wilderness revert task error: {ERROR}",
    "log_mob_barrier_error": "Mob barrier task error: {ERROR}",
    "log_claimblock_task_error": "ClaimBlock task error: {ERROR}",
    "log_guest_pass_sweep_error": "Guest Pass expiry sweep error: {ERROR}",
    "log_lockdown_sweep_error": "Lockdown expiry sweep error: {ERROR}",
    "log_lang_keys_added": "Added {COUNT} missing language key(s) to {PATH}; the previous file was backed up.",
    "log_lang_write_failed": "Failed to write language file: {PATH} ({ERROR})",
    "log_banned_player_detected": "[AegisGuard] Banned Player Detected: {PLAYER}",
    "log_banned_plots_removed": "[AegisGuard] Auto-removed {COUNT} plots belonging to {PLAYER}",
    "log_discord_webhook_failed": "[Discord] Failed to send webhook: {ERROR}",
    "log_admin_audit": "[Admin Audit] {PLAYER} {ACTION}.",
    "log_admin_console_reload": "AegisGuard was reloaded from the server console.",
    "log_messages_util_loaded": "[AegisGuard] MessagesUtil compat loaded (NO messages.yml). Default style: {STYLE}",
    "log_player_prefs_loaded": "[AegisGuard] Loaded {COUNT} player language preferences.",
    "log_world_rules_defaults": "[AegisGuard] No per-world configuration found. Using defaults.",
    "log_world_rules_loaded": "[AegisGuard] Loaded rules for {COUNT} worlds.",
    "log_alliances_loaded": "Loaded {COUNT} alliance(s).",
    "log_routes_loaded": "Loaded {COUNT} exploration route(s).",
    "log_notifications_loaded": "Loaded {COUNT} notification preferences",
    "log_notifications_migrated": "Migrated {COUNT} legacy notification settings",
    "log_notifications_invalid_uuid": "Invalid UUID in notifications: {UUID}",
    "log_settlement_queued": "Queued pending economy settlement of {AMOUNT} for {PLAYER}: {REASON}",
    "log_territory_life_save_failed": "Failed to save territory-life.yml: {ERROR}",
    "log_snapshots_created": "[Snapshots] Created snapshot {ID} for plot {PLOT} ({TYPE})",
    "log_snapshots_rollback_missing": "[Snapshots] Cannot rollback: snapshot {ID} not found",
    "log_snapshots_rolled_back": "[Snapshots] Rolled back plot {PLOT} to snapshot {ID}",
    "log_snapshots_deleted": "[Snapshots] Deleted snapshot {ID}",
    "log_snapshots_pruned": "[Snapshots] Pruned {COUNT} old snapshots",
    "log_snapshots_loaded": "[Snapshots] Loaded {COUNT} snapshots",
    "log_map_dynmap_hooked": "Successfully hooked into Dynmap.",
    "log_map_bluemap_hooked": "Hooked into BlueMap!",
    "log_map_bluemap_failed": "BlueMap detected but API failed to initialize.",
    "log_map_pl3xmap_hooked": "Hooked into Pl3xMap!",
    "log_map_pl3xmap_failed": "Pl3xMap detected but API failed to initialize.",
    "log_map_dynmap_update_failed": "Dynmap update failed!",
    "log_map_bluemap_update_failed": "BlueMap update failed!",
    "log_map_pl3xmap_update_failed": "Pl3xMap update failed!",
    "log_plot_sale_offline_seller": "Plot sale aborted: could not pay offline seller {SELLER}.",
    "log_plot_sale_failed": "Plot sale transaction failed for {ID}: {ERROR}",
    "log_plot_sale_reverse_failed": "Could not reverse seller payment for failed plot sale {ID}.",
    "log_plot_rental_failed": "Plot rental transaction failed for {ID}: {ERROR}",
    "log_plot_rental_reverse_failed": "Could not reverse owner payment for failed plot rental {ID}.",
    "log_convert_audit": "[Admin Audit] {PLAYER} converted plot {PLOT} into a server zone.",
    # Discord display
    "discord_ban_wipe_title": "Banned Player Wipe",
    "discord_ban_wipe_description": "Player **{PLAYER}** was detected as banned. Their land has been seized.",
    "discord_ban_wipe_action_name": "Action",
    "discord_ban_wipe_action_value": "All plots removed",
    "discord_ban_wipe_count_name": "Count",
    "discord_ban_wipe_footer": "AegisGuard Automation",
    "discord_claim_title_plot": "Plot Claimed",
    "discord_claim_title_server": "Admin Zone Created",
    "discord_claim_description": "{PLAYER} claimed a plot.",
    "discord_claim_field_world": "World",
    "discord_claim_field_area": "Area",
    "discord_claim_field_owner": "Owner",
    "discord_claim_owner_server": "Server",
    "discord_event_lockdown_title": "Emergency lockdown activated",
    "discord_event_lockdown_description": "{PLAYER} activated lockdown for {PLOT}.",
    "discord_event_market_sale_title": "Plot sold",
    "discord_event_market_sale_description": "{BUYER} bought {PLOT} from {SELLER} for {PRICE}.",
    "discord_event_rental_start_title": "Plot rental started",
    "discord_event_rental_start_description": "{PLAYER} rented {PLOT} for {DAYS} day(s).",
    "discord_event_rental_end_title": "Plot rental ended",
    "discord_event_rental_end_description": "{PLAYER} ended the rental for {PLOT}",
    "discord_event_zone_rental_end_title": "Zone rental ended",
    "discord_event_zone_rental_end_description": "{PLAYER} left zone {ZONE} on {PLOT}",
    "discord_event_guest_pass_title": "Guest pass issued",
    "discord_event_guest_pass_description": "{PLAYER} issued {PRESET} access to {TARGET} for {PLOT}.",
    # Activity details + type labels
    "activity_detail_plot_claimed": "Territory claimed in {WORLD}.",
    "activity_detail_plot_deleted": "Territory removed.",
    "activity_detail_visit": "Plot visited through discovery or travel.",
    "activity_detail_rental_expired": "Rental term expired normally.",
    "activity_detail_plot_sold": "Ownership transferred from {SELLER} to {BUYER} for {PRICE}.",
    "activity_detail_rental_started": "Rental started for {DAYS} day(s); rent={RENT}, deposit={DEPOSIT}.",
    "activity_detail_rental_renewed": "Contract renewed for {DAYS} day(s).",
    "activity_detail_rental_cancelled": "Contract ended early by {ACTOR_ROLE}.",
    "activity_detail_zone_rent_left": "Zone {ZONE} left early by renter.",
    "activity_detail_transfer_settle": "Cleared rentals/deposits before ownership transfer.",
    "activity_detail_server_zone_convert": "Player territory converted into a server zone ({TARGET}).",
    "activity_detail_server_zone_merge": "Merged adjacent server zone {PLOT}.",
    "activity_detail_admin_rental_cancel": "Contract cancelled by administrator.",
    "activity_detail_admin_discovery": "Discovery state changed: {ACTION}.",
    "activity_detail_notice_posted": "Posted a noticeboard notice.",
    "activity_detail_notice_removed": "Removed a noticeboard notice.",
    "activity_detail_rental_listed": "Listed for {PRICE}, deposit={DEPOSIT}, term={DAYS} day(s).",
    "activity_detail_rental_unlisted": "Rental listing removed.",
    "activity_detail_discovery_category": "Discovery category changed to {CATEGORY}.",
    "activity_detail_discovery_visibility": "Discovery visibility changed to {VISIBILITY}.",
    "activity_detail_claim_merge": "Merged plot {OTHER} into {BASE}.",
    "activity_detail_doctor_repair": "Doctor repaired inconsistent territory state.",
    "activity_detail_upkeep_paid": "Early upkeep payment collected: {AMOUNT}.",
    "activity_type_plot_claimed": "Plot Claimed",
    "activity_type_plot_deleted": "Plot Deleted",
    "activity_type_visit": "Visit",
    "activity_type_rental_expired": "Rental Expired",
    "activity_type_plot_sold": "Plot Sold",
    "activity_type_rental_started": "Rental Started",
    "activity_type_rental_renewed": "Rental Renewed",
    "activity_type_rental_cancelled": "Rental Cancelled",
    "activity_type_zone_rent_left": "Zone Rent Left",
    "activity_type_ownership_transfer_settle": "Ownership Transfer Settle",
    "activity_type_server_zone_convert": "Server Zone Convert",
    "activity_type_server_zone_merge": "Server Zone Merge",
    "activity_type_admin_rental_cancel": "Admin Rental Cancel",
    "activity_type_admin_discovery": "Admin Discovery",
    "activity_type_notice_posted": "Notice Posted",
    "activity_type_notice_removed": "Notice Removed",
    "activity_type_rental_listed": "Rental Listed",
    "activity_type_rental_unlisted": "Rental Unlisted",
    "activity_type_discovery_category": "Discovery Category",
    "activity_type_discovery_visibility": "Discovery Visibility",
    "activity_type_claim_merge": "Claim Merge",
    "activity_type_doctor_repair": "Doctor Repair",
    "activity_type_upkeep_paid_early": "Upkeep Paid Early",
    "rental_contract_ended_early_notice": "&eThe rental contract for plot &f{PLOT} &ewas ended early.",
    "zone_rent_left_landlord_notice": "&eTenant &f{PLAYER} &eleft zone &f{ZONE}&e early.",
}

LANGS = {
    "old_english": "en",
    "spanish_mx": "es",
    "spanish_ar": "es",
    "portuguese_br": "pt",
    "french_fr": "fr",
    "italian_it": "it",
    "german_de": "de",
    "polish_pl": "pl",
}

PROTECT_RE = re.compile(
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|\*\*[^*]+\*\*|"
    r"AegisGuard|ClaimBlocks|Guest Pass(?:es)?|Dynmap|BlueMap|Pl3xMap|"
    r"Folia|Bukkit|Spigot|Paper|Codex|MessagesUtil|messages\.yml|"
    r"territory-life\.yml|UUID|SQL|Horizon|NO )",
    re.IGNORECASE,
)

# Curated overrides where machine translation is weak / style matters.
MANUAL: dict[str, dict[str, str]] = {
    "old_english": {
        "AegisGuard enabled.": "AegisGuard hath been enabled.",
        "AegisGuard disabled.": "AegisGuard hath been disabled.",
        "AegisGuard reloaded successfully.": "AegisGuard hath been reloaded successfully.",
        "Plot Claimed": "Plot Claimed",
        "Banned Player Wipe": "Banned Player Wipe",
    },
    "spanish_mx": {
        "AegisGuard enabled.": "AegisGuard habilitado.",
        "AegisGuard disabled.": "AegisGuard deshabilitado.",
        "AegisGuard reloaded successfully.": "AegisGuard recargado correctamente.",
        "Banned Player Wipe": "Limpieza por jugador baneado",
        "Plot Claimed": "Terreno reclamado",
        "Admin Zone Created": "Zona de admin creada",
        "Plot sold": "Terreno vendido",
        "Plot rental started": "Alquiler de terreno iniciado",
        "Plot rental ended": "Alquiler de terreno terminado",
        "Zone rental ended": "Alquiler de zona terminado",
        "Guest pass issued": "Guest Pass emitido",
        "Emergency lockdown activated": "Bloqueo de emergencia activado",
        "Action": "Acción",
        "All plots removed": "Todos los terrenos eliminados",
        "Count": "Cantidad",
        "World": "Mundo",
        "Area": "Área",
        "Owner": "Dueño",
        "Server": "Servidor",
        "Territory removed.": "Territorio eliminado.",
        "Rental term expired normally.": "El plazo de alquiler venció normalmente.",
        "Contract cancelled by administrator.": "Contrato cancelado por un administrador.",
        "Posted a noticeboard notice.": "Se publicó un aviso en el tablón.",
        "Removed a noticeboard notice.": "Se eliminó un aviso del tablón.",
        "Rental listing removed.": "Listado de alquiler eliminado.",
        "Doctor repaired inconsistent territory state.": "Doctor reparó un estado inconsistente del territorio.",
        "Plot Claimed": "Terreno reclamado",
        "Plot Deleted": "Terreno eliminado",
        "Visit": "Visita",
        "Rental Expired": "Alquiler vencido",
        "Plot Sold": "Terreno vendido",
        "Rental Started": "Alquiler iniciado",
        "Rental Renewed": "Alquiler renovado",
        "Rental Cancelled": "Alquiler cancelado",
        "Zone Rent Left": "Salida de alquiler de zona",
        "Ownership Transfer Settle": "Liquidación por transferencia",
        "Server Zone Convert": "Conversión a zona del servidor",
        "Server Zone Merge": "Fusión de zona del servidor",
        "Admin Rental Cancel": "Cancelación admin de alquiler",
        "Admin Discovery": "Descubrimiento admin",
        "Notice Posted": "Aviso publicado",
        "Notice Removed": "Aviso eliminado",
        "Rental Listed": "Alquiler listado",
        "Rental Unlisted": "Alquiler retirado",
        "Discovery Category": "Categoría de descubrimiento",
        "Discovery Visibility": "Visibilidad de descubrimiento",
        "Claim Merge": "Fusión de terrenos",
        "Doctor Repair": "Reparación Doctor",
        "Upkeep Paid Early": "Mantenimiento pagado anticipado",
        "&eThe rental contract for plot &f{PLOT} &ewas ended early.": "&eEl contrato de alquiler del terreno &f{PLOT} &efue terminado anticipadamente.",
        "&eTenant &f{PLAYER} &eleft zone &f{ZONE}&e early.": "&eEl inquilino &f{PLAYER} &edejó la zona &f{ZONE}&e anticipadamente.",
    },
}


def yaml_escape(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def cache_get(lang: str, src: str) -> str | None:
    path = CACHE_DIR / f"{lang}.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8")).get(src)


def cache_put(lang: str, src: str, dst: str) -> None:
    path = CACHE_DIR / f"{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    data[src] = dst
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def protect(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def repl(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"⟦{len(tokens) - 1}⟧"

    return PROTECT_RE.sub(repl, text), tokens


def unprotect(text: str, tokens: list[str]) -> str:
    def repl(match: re.Match[str]) -> str:
        idx = int(match.group(1))
        return tokens[idx] if 0 <= idx < len(tokens) else match.group(0)

    return re.sub(r"⟦(\d+)⟧", repl, text)


def stylize_old_english(text: str) -> str:
    # Light archaic flavor without breaking placeholders.
    reps = [
        (r"\bhas been\b", "hath been"),
        (r"\bhave been\b", "have been"),
        (r"\bfailed\b", "failed"),
        (r"\bloaded\b", "hath been loaded"),
        (r"\benabled\b", "enabled"),
        (r"\bdisabled\b", "disabled"),
    ]
    out = text
    for pat, rep in reps:
        out = re.sub(pat, rep, out, flags=re.IGNORECASE)
    return out


def translate_string(lang: str, google_code: str, value: str) -> str:
    if lang in MANUAL and value in MANUAL[lang]:
        return MANUAL[lang][value]
    # spanish_ar shares spanish_mx curated strings when present
    if lang == "spanish_ar" and value in MANUAL.get("spanish_mx", {}):
        return MANUAL["spanish_mx"][value]
    cached = cache_get(lang, value)
    if cached is not None:
        return cached
    if lang == "old_english":
        out = stylize_old_english(value)
        cache_put(lang, value, out)
        return out

    protected, tokens = protect(value)
    try:
        translated = GoogleTranslator(source="en", target=google_code).translate(protected)
        time.sleep(0.05)
        if not translated:
            translated = value
        out = unprotect(translated, tokens)
    except Exception as exc:  # noqa: BLE001
        print(f"  translate fail ({lang}): {value[:60]} -> {exc}")
        out = value
    cache_put(lang, value, out)
    return out


def existing_keys(path: Path) -> set[str]:
    keys = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.lstrip().startswith("#") or ":" not in line:
            continue
        key = line.split(":", 1)[0].strip()
        if key:
            keys.add(key)
    return keys


def append_keys(path: Path, pairs: dict[str, str]) -> int:
    present = existing_keys(path)
    missing = {k: v for k, v in pairs.items() if k not in present}
    if not missing:
        return 0
    text = path.read_text(encoding="utf-8")
    if not text.endswith("\n"):
        text += "\n"
    if MARKER not in text:
        text += "\n" + MARKER + "\n"
    block = "".join(f"{k}: {yaml_escape(v)}\n" for k, v in missing.items())
    # Append after marker section end (file end is fine).
    path.write_text(text + block, encoding="utf-8")
    return len(missing)


def main() -> None:
    en_path = LANG_ROOT / "modern_english" / "system.yml"
    added = append_keys(en_path, ENGLISH)
    print(f"modern_english: +{added}")

    for lang, code in LANGS.items():
        translated = {}
        for key, value in ENGLISH.items():
            translated[key] = translate_string(lang, code, value)
            if len(translated) % 25 == 0:
                print(f"  {lang}: {len(translated)}/{len(ENGLISH)}")
        path = LANG_ROOT / lang / "system.yml"
        n = append_keys(path, translated)
        print(f"{lang}: +{n}")


if __name__ == "__main__":
    main()
