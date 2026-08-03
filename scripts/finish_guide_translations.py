#!/usr/bin/env python3
"""Finish polish + leftover English guide keys with incremental dumps.

Avoids MyMemory hangs. Uses Google with short retries, then a hard-coded
fallback table for stubborn short labels.
"""
from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Any

import yaml
from deep_translator import GoogleTranslator

ROOT = Path(__file__).resolve().parents[1]
LANG_ROOT = ROOT / "aegisguard-modern/src/main/resources/lang"
CODEX_ROOT = ROOT / "aegisguard-modern/src/main/resources/codex"
CACHE_DIR = ROOT / ".tmp-lang-cache"
MISSING_PATH = ROOT / ".tmp-missing-guide-keys.json"
BUNDLES = ["guis.yml", "system.yml", "upgrades.yml", "expansions.yml"]

TARGETS = {
    "polish_pl": "pl",
    "italian_it": "it",
    "german_de": "de",
    "french_fr": "fr",
    "portuguese_br": "pt",
    "spanish_mx": "es",
    "spanish_ar": "es",
}

SYSTEM_PREFIXES = (
    "admin_refresh", "snapshots_", "spawn_", "exchange_", "language_",
    "permission_label_", "expansion_manager_",
)

PROTECT_RE = re.compile(
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|AegisGuard|"
    r"/aegis(?:guard)?(?:admin)?(?:\s+[a-z0-9_<>\|\-]+)*|"
    r"ClaimBlocks|TradeStalls?)",
    re.IGNORECASE,
)

# Exact value overrides (English source -> localized).
OVERRIDES: dict[str, dict[str, str]] = {
    "polish_pl": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStalle",
        "&eTradeStall Setup": "&eKonfiguracja TradeStall",
        "&aChat": "&aCzat",
        "&dTitle": "&dTytuł",
        "&bAction Bar": "&bPasek akcji",
        "&6Migration": "&6Migracja",
        "&eDiagnostics": "&eDiagnostyka",
        "&4Kick & Ban": "&4Wyrzuć i zbanuj",
        "&aTrusted Plot Travel": "&aPodróż do zaufanej działki",
        "&eReload All Settings": "&ePrzeładuj wszystkie ustawienia",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Nieprawidłowy styl języka: {STYLE}",
        "&cInvalid language style: {STYLE}": "&cNieprawidłowy styl języka: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Ustawiono język: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Menedżer wniosków o ekspansję jest niedostępny.",
        "&cClaimBlocks Exchange is unavailable.": "&cWymiana ClaimBlocks jest niedostępna.",
        "&cClaimBlocks Exchange is disabled in config.yml.": "&cWymiana ClaimBlocks jest wyłączona w config.yml.",
        "&cSnapshot system is unavailable.": "&cSystem snapshotów jest niedostępny.",
        "&cSnapshots are disabled in config.yml.": "&cSnapshoty są wyłączone w config.yml.",
        "&aLanguage packs refreshed.": "&aPakiety językowe odświeżone.",
        "&aRefreshing language packs...": "&aOdświeżanie pakietów językowych...",
        "&bWelcome to the Guardian's Guide": "&bWitaj w Przewodniku Strażnika",
        "&e&lI. Claiming": "&e&lI. Przejmowanie",
        "&b&lII. Travel": "&b&lII. Podróże",
        "&d&lIII. Menus": "&d&lIII. Menu",
        "&c&lIV. Security": "&c&lIV. Bezpieczeństwo",
        "&6&lV. Economy": "&6&lV. Ekonomia",
        "&3&lVI. Identity": "&3&lVI. Tożsamość",
        "&5&lVII. Advanced": "&5&lVII. Zaawansowane",
        "&d&lIII. Menus": "&d&lIII. Menu",
        "&cNotifications": "&cPowiadomienia",
        "&eNotifications": "&ePowiadomienia",
    },
    "italian_it": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStall",
        "&aChat": "&aChat",
        "&dTitle": "&dTitolo",
        "&bAction Bar": "&bBarra azioni",
        "&6Migration": "&6Migrazione",
        "&eDiagnostics": "&eDiagnostica",
        "&4Kick & Ban": "&4Kick e Ban",
        "&aTrusted Plot Travel": "&aViaggio nei plot fidati",
        "&eReload All Settings": "&eRicarica tutte le impostazioni",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Stile lingua non valido: {STYLE}",
        "&cInvalid language style: {STYLE}": "&cStile lingua non valido: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Lingua impostata su: &b{STYLE}",
    },
    "german_de": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStalls",
        "&eTradeStall Setup": "&eTradeStall-Einrichtung",
        "&aChat": "&aChat",
        "&dTitle": "&dTitel",
        "&bAction Bar": "&bActionbar",
        "&6Migration": "&6Migration",
        "&eDiagnostics": "&eDiagnose",
        "&4Kick & Ban": "&4Kicken & Bannen",
        "&aTrusted Plot Travel": "&aReise zu vertrauenswürdigem Grundstück",
        "&eReload All Settings": "&eAlle Einstellungen neu laden",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Ungültiger Sprachstil: {STYLE}",
        "&cInvalid language style: {STYLE}": "&cUngültiger Sprachstil: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Sprache eingestellt auf: &b{STYLE}",
    },
    "french_fr": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStalls",
        "&aChat": "&aChat",
        "&dTitle": "&dTitre",
        "&bAction Bar": "&bBarre d'action",
        "&6Migration": "&6Migration",
        "&eDiagnostics": "&eDiagnostic",
        "&d&lIII. Menus": "&d&lIII. Menus",
        "&cNotifications": "&cNotifications",
        "&eNotifications": "&eNotifications",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Style de langue invalide : {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Langue définie sur : &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Gestionnaire de demandes d'expansion indisponible.",
    },
    "portuguese_br": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStalls",
        "&aChat": "&aChat",
        "&dTitle": "&dTítulo",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Estilo de idioma inválido: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Idioma definido para: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Gerenciador de pedidos de expansão indisponível.",
    },
    "spanish_mx": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStalls",
        "&aChat": "&aChat",
        "&dTitle": "&dTítulo",
        "&eNotifications": "&eNotificaciones",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Estilo de idioma inválido: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Idioma establecido en: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] El administrador de solicitudes de expansión no está disponible.",
    },
    "spanish_ar": {
        "&6ClaimBlocks": "&6ClaimBlocks",
        "&dTradeStalls": "&dTradeStalls",
        "&aChat": "&aChat",
        "&dTitle": "&dTítulo",
        "&eNotifications": "&eNotificaciones",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Estilo de idioma inválido: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Idioma establecido en: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] El administrador de solicitudes de expansión no está disponible.",
    },
}

# Labels that are acceptable to keep as brand/loanwords.
ALLOW_SAME = {
    "&6ClaimBlocks", "&dTradeStalls", "&aChat", "&6Migration",
}


def normalize_colors(text: str) -> str:
    return text.replace("§", "&")


def protect(text: str) -> tuple[str, dict[str, str]]:
    mapping: dict[str, str] = {}

    def repl(match: re.Match[str]) -> str:
        token = f"[[T{len(mapping):04d}]]"
        mapping[token] = match.group(0)
        return token

    return PROTECT_RE.sub(repl, text), mapping


def unprotect(text: str, mapping: dict[str, str]) -> str:
    for token, original in mapping.items():
        text = text.replace(token, original)
    return text.replace("Aegis Guard", "AegisGuard")


def load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as fh:
        data = yaml.safe_load(fh) or {}
    if not isinstance(data, dict):
        raise ValueError(path)
    return data


def dump_yaml(path: Path, data: dict) -> None:
    plain_key = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")

    class LiteralDumper(yaml.SafeDumper):
        pass

    def str_representer(dumper, value: str):
        style = "|" if "\n" in value else "'"
        return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)

    def dict_representer(dumper, mapping):
        value = []
        for item_key, item_value in mapping.items():
            node_key = dumper.represent_data(item_key)
            if isinstance(item_key, str) and plain_key.match(item_key):
                node_key = dumper.represent_scalar("tag:yaml.org,2002:str", item_key, style="")
            value.append((node_key, dumper.represent_data(item_value)))
        return yaml.nodes.MappingNode("tag:yaml.org,2002:map", value)

    LiteralDumper.add_representer(str, str_representer)
    LiteralDumper.add_representer(dict, dict_representer)
    body = yaml.dump(
        data,
        Dumper=LiteralDumper,
        allow_unicode=True,
        sort_keys=False,
        width=120,
        default_flow_style=False,
    )
    body = re.sub(r"^'([A-Za-z_][A-Za-z0-9_]*)':", r"\1:", body, flags=re.M)
    header = ""
    if path.exists():
        raw = path.read_text(encoding="utf-8")
        if raw.lstrip().startswith("#"):
            lines = []
            for line in raw.splitlines():
                if line.startswith("#") or not line.strip():
                    lines.append(line)
                else:
                    break
            while lines and not lines[-1].strip():
                lines.pop()
            if lines:
                header = "\n".join(lines) + "\n\n"
    path.write_text(header + body, encoding="utf-8")


def bundle_for(key: str) -> str:
    if key.startswith(SYSTEM_PREFIXES):
        return "system.yml"
    return "guis.yml"


def pack_value(value: Any) -> str:
    if isinstance(value, list):
        return "\n".join(str(x) for x in value)
    return str(value)


def unpack_value(packed: str, template: Any) -> Any:
    if isinstance(template, list):
        lines = packed.split("\n")
        if len(lines) == len(template):
            return lines
        out = lines[: len(template)]
        while len(out) < len(template):
            out.append(" ")
        return out
    return packed


def merge_codex(lang: str) -> None:
    merged: dict = {}
    for bundle in BUNDLES:
        path = LANG_ROOT / lang / bundle
        if path.exists():
            merged.update(load_yaml(path))
    titles = {
        "polish_pl": "Polski", "italian_it": "Italiano", "german_de": "Deutsch",
        "french_fr": "Français", "portuguese_br": "Português (Brasil)",
        "spanish_mx": "Español (México)", "spanish_ar": "Español (Argentina)",
        "modern_english": "Modern English", "old_english": "Old English",
    }
    header = "\n".join([
        "# ======================================",
        f"# AegisGuard v1.3.0 - Codex: {titles.get(lang, lang)}",
        f"# Language ID: {lang}",
        f"# Fallback bundle mirrored from lang/{lang}/",
        "# ======================================",
        "",
    ])
    dump_yaml(CODEX_ROOT / f"{lang}.yml", merged)
    # prepend header
    path = CODEX_ROOT / f"{lang}.yml"
    body = path.read_text(encoding="utf-8")
    if not body.lstrip().startswith("#"):
        path.write_text(header + "\n" + body, encoding="utf-8")


def translate_text(lang: str, google: str, text: str, cache: dict[str, str], overrides: dict[str, str]) -> str:
    if not re.search(r"[A-Za-z]", text):
        return text
    if text in overrides:
        return overrides[text]
    if text in ALLOW_SAME:
        return text
    if text in cache and cache[text] and (cache[text] != text or text in ALLOW_SAME):
        return cache[text]

    # Translate line-by-line for lore blocks so failures are isolated.
    if "\n" in text:
        parts = [translate_text(lang, google, line, cache, overrides) for line in text.split("\n")]
        joined = "\n".join(parts)
        cache[text] = joined
        return joined

    protected, mapping = protect(text)
    for attempt in range(3):
        try:
            result = GoogleTranslator(source="en", target=google).translate(protected)
            if result:
                restored = unprotect(result, mapping)
                # If translator echoes English for short labels, prefer override-like tweak.
                if restored == text and text in overrides:
                    restored = overrides[text]
                cache[text] = restored
                return restored
        except Exception:  # noqa: BLE001
            time.sleep(0.8 * (attempt + 1))

    # Heuristic fallbacks for stubborn short UI labels.
    fallback = overrides.get(text, text)
    cache[text] = fallback
    if fallback == text and text not in ALLOW_SAME:
        print(f"  WARN English left in {lang}: {text[:90]!r}", flush=True)
    return fallback


def ensure_missing() -> dict[str, Any]:
    raw = json.loads(MISSING_PATH.read_text(encoding="utf-8"))
    extras = {
        "expansion_manager_unavailable": "&c[AegisGuard] Expansion request manager is unavailable.",
        "language_invalid_style": "&c⚠ Invalid language style: {STYLE}",
        "language_set_to": "&6🕮 Language set to: &b{STYLE}",
        "exchange_unavailable": "&cClaimBlocks Exchange is unavailable.",
        "exchange_disabled": "&cClaimBlocks Exchange is disabled in config.yml.",
        "snapshots_unavailable": "&cSnapshot system is unavailable.",
        "snapshots_disabled_config": "&cSnapshots are disabled in config.yml.",
    }
    out = dict(raw)
    for k, v in extras.items():
        out.setdefault(k, v)
    normalized = {}
    for k, v in out.items():
        if isinstance(v, list):
            normalized[k] = [normalize_colors(str(x)) for x in v]
        else:
            normalized[k] = normalize_colors(str(v))
    return normalized


def needs_fill(existing: Any, english: Any) -> bool:
    if existing is None or existing in ("", []):
        return True
    return pack_value(existing) == pack_value(english)


def main() -> int:
    missing = ensure_missing()

    # Ensure English source has extras.
    for key, value in missing.items():
        path = LANG_ROOT / "modern_english" / bundle_for(key)
        data = load_yaml(path)
        if key not in data:
            data[key] = value
            dump_yaml(path, data)

    totals = {}
    for lang, google in TARGETS.items():
        print(f"== {lang} ==", flush=True)
        cache_path = CACHE_DIR / f"{lang}.json"
        cache = {}
        if cache_path.exists():
            try:
                cache = json.loads(cache_path.read_text(encoding="utf-8"))
            except Exception:  # noqa: BLE001
                cache = {}
        overrides = OVERRIDES.get(lang, {})
        changed = 0

        for bundle in ("guis.yml", "system.yml"):
            keys = {k: v for k, v in missing.items() if bundle_for(k) == bundle}
            if not keys:
                continue
            path = LANG_ROOT / lang / bundle
            data = load_yaml(path)
            for i, (key, eng) in enumerate(sorted(keys.items()), 1):
                if not needs_fill(data.get(key), eng):
                    continue
                packed = pack_value(eng)
                translated = translate_text(lang, google, packed, cache, overrides)
                data[key] = unpack_value(translated, eng)
                changed += 1
                if changed % 5 == 0:
                    dump_yaml(path, data)
                    cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
                    print(f"  {lang}/{bundle}: {changed} updated (checkpoint)", flush=True)
                time.sleep(0.2)
            dump_yaml(path, data)

        cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
        merge_codex(lang)
        totals[lang] = changed
        print(f"  done {lang}: {changed}", flush=True)

    merge_codex("modern_english")
    merge_codex("old_english")
    print("Totals:", totals, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
