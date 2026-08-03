#!/usr/bin/env python3
"""Finish injecting/translating missing guide keys for remaining languages.

More resilient than per-line translation:
- translates whole string/list values as one unit
- retries with backoff + MyMemory fallback
- only fills missing/identical-English keys
"""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path
from typing import Any

import yaml
from deep_translator import GoogleTranslator, MyMemoryTranslator

ROOT = Path(__file__).resolve().parents[1]
LANG_ROOT = ROOT / "aegisguard-modern/src/main/resources/lang"
CODEX_ROOT = ROOT / "aegisguard-modern/src/main/resources/codex"
CACHE_DIR = ROOT / ".tmp-lang-cache"
MISSING_PATH = ROOT / ".tmp-missing-guide-keys.json"
BUNDLES = ["guis.yml", "system.yml", "upgrades.yml", "expansions.yml"]

# Finish languages that still need keys / cleanup.
LANGS = {
    "italian_it": ("it", "en-IT"),
    "german_de": ("de", "en-DE"),
    "polish_pl": ("pl", "en-PL"),
    "french_fr": ("fr", "en-FR"),  # cleanup failed English leftovers
    "portuguese_br": ("pt", "en-PT"),
    "spanish_mx": ("es", "en-ES"),
    "spanish_ar": ("es", "en-ES"),
}

SYSTEM_PREFIXES = (
    "admin_refresh",
    "snapshots_",
    "spawn_",
    "exchange_",
    "language_",
    "permission_label_",
    "expansion_manager_",
)

PROTECT_RE = re.compile(
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|AegisGuard|"
    r"/aegis(?:guard)?(?:admin)?(?:\s+[a-z0-9_<>\|\-]+)*|"
    r"ClaimBlocks|TradeStalls?)",
    re.IGNORECASE,
)

# High-value manual overrides when machine translation fails.
MANUAL = {
    "italian_it": {
        "&bWelcome to the Guardian's Guide": "&bBenvenuto nella Guida del Guardiano",
        "&e&lI. Claiming": "&e&lI. Reclami",
        "&b&lII. Travel": "&b&lII. Viaggi",
        "&d&lIII. Menus": "&d&lIII. Menu",
        "&c&lIV. Security": "&c&lIV. Sicurezza",
        "&6&lV. Economy": "&6&lV. Economia",
        "&3&lVI. Identity": "&3&lVI. Identità",
        "&5&lVII. Advanced": "&5&lVII. Avanzate",
        "&bOpen Your Main Menu": "&bApri il menu principale",
        "&eGet The Aegis Scepter": "&eOttieni lo Scettro Aegis",
        "&eGet Your Scepter": "&ePrendi il tuo Scettro",
        "&bConfirm The Claim": "&bConferma il claim",
        "&6Server Locations": "&6Luoghi del server",
        "&bRecovery & Safety": "&bRecupero e sicurezza",
        "&aLanguage packs refreshed.": "&aPacchetti lingua aggiornati.",
        "&aRefreshing language packs...": "&aAggiornamento pacchetti lingua...",
        "&cClaimBlocks Exchange is unavailable.": "&cLo scambio ClaimBlocks non è disponibile.",
        "&cClaimBlocks Exchange is disabled in config.yml.": "&cLo scambio ClaimBlocks è disabilitato in config.yml.",
        "&cSnapshot system is unavailable.": "&cIl sistema snapshot non è disponibile.",
        "&cSnapshots are disabled in config.yml.": "&cGli snapshot sono disabilitati in config.yml.",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Stile lingua non valido: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Lingua impostata su: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Gestore richieste espansione non disponibile.",
    },
    "german_de": {
        "&bWelcome to the Guardian's Guide": "&bWillkommen beim Wächter-Leitfaden",
        "&e&lI. Claiming": "&e&lI. Beanspruchen",
        "&b&lII. Travel": "&b&lII. Reisen",
        "&d&lIII. Menus": "&d&lIII. Menüs",
        "&c&lIV. Security": "&c&lIV. Sicherheit",
        "&6&lV. Economy": "&6&lV. Wirtschaft",
        "&3&lVI. Identity": "&3&lVI. Identität",
        "&5&lVII. Advanced": "&5&lVII. Erweitert",
        "&bOpen Your Main Menu": "&bÖffne dein Hauptmenü",
        "&eGet The Aegis Scepter": "&eHole das Aegis-Zepter",
        "&eGet Your Scepter": "&eHole dein Zepter",
        "&bConfirm The Claim": "&bClaim bestätigen",
        "&6Server Locations": "&6Server-Orte",
        "&bRecovery & Safety": "&bWiederherstellung & Sicherheit",
        "&aLanguage packs refreshed.": "&aSprachpakete aktualisiert.",
        "&aRefreshing language packs...": "&aSprachpakete werden aktualisiert...",
        "&cClaimBlocks Exchange is unavailable.": "&cClaimBlocks-Tausch ist nicht verfügbar.",
        "&cClaimBlocks Exchange is disabled in config.yml.": "&cClaimBlocks-Tausch ist in config.yml deaktiviert.",
        "&cSnapshot system is unavailable.": "&cSnapshot-System ist nicht verfügbar.",
        "&cSnapshots are disabled in config.yml.": "&cSnapshots sind in config.yml deaktiviert.",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Ungültiger Sprachstil: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Sprache eingestellt auf: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Erweiterungsanfragen-Manager nicht verfügbar.",
    },
    "polish_pl": {
        "&bWelcome to the Guardian's Guide": "&bWitaj w Przewodniku Strażnika",
        "&e&lI. Claiming": "&e&lI. Przejmowanie",
        "&b&lII. Travel": "&b&lII. Podróże",
        "&d&lIII. Menus": "&d&lIII. Menu",
        "&c&lIV. Security": "&c&lIV. Bezpieczeństwo",
        "&6&lV. Economy": "&6&lV. Ekonomia",
        "&3&lVI. Identity": "&3&lVI. Tożsamość",
        "&5&lVII. Advanced": "&5&lVII. Zaawansowane",
        "&bOpen Your Main Menu": "&bOtwórz menu główne",
        "&eGet The Aegis Scepter": "&eWeź Berło Aegis",
        "&eGet Your Scepter": "&eWeź swoje Berło",
        "&bConfirm The Claim": "&bPotwierdź działkę",
        "&6Server Locations": "&6Lokalizacje serwera",
        "&bRecovery & Safety": "&bOdzyskiwanie i bezpieczeństwo",
        "&aLanguage packs refreshed.": "&aPakiety językowe odświeżone.",
        "&aRefreshing language packs...": "&aOdświeżanie pakietów językowych...",
        "&cClaimBlocks Exchange is unavailable.": "&cWymiana ClaimBlocks jest niedostępna.",
        "&cClaimBlocks Exchange is disabled in config.yml.": "&cWymiana ClaimBlocks jest wyłączona w config.yml.",
        "&cSnapshot system is unavailable.": "&cSystem snapshotów jest niedostępny.",
        "&cSnapshots are disabled in config.yml.": "&cSnapshoty są wyłączone w config.yml.",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Nieprawidłowy styl języka: {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Ustawiono język: &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Menedżer wniosków o ekspansję jest niedostępny.",
    },
    "french_fr": {
        "&aLanguage packs refreshed.": "&aPacks de langue actualisés.",
        "&aRefreshing language packs...": "&aActualisation des packs de langue...",
        "&cClaimBlocks Exchange is unavailable.": "&cL'échange de ClaimBlocks est indisponible.",
        "&cClaimBlocks Exchange is disabled in config.yml.": "&cL'échange de ClaimBlocks est désactivé dans config.yml.",
        "&cSnapshot system is unavailable.": "&cLe système de snapshots est indisponible.",
        "&cSnapshots are disabled in config.yml.": "&cLes snapshots sont désactivés dans config.yml.",
        "&c⚠ Invalid language style: {STYLE}": "&c⚠ Style de langue invalide : {STYLE}",
        "&6🕮 Language set to: &b{STYLE}": "&6🕮 Langue définie sur : &b{STYLE}",
        "&c[AegisGuard] Expansion request manager is unavailable.": "&c[AegisGuard] Gestionnaire de demandes d'expansion indisponible.",
        "&7Open Members & Roles to trust friends": "&7Ouvrez Membres et Rôles pour faire confiance à vos amis",
        "&7group tools carefully on shared plots.": "&7utilisez prudemment les outils de groupe sur les parcelles partagées.",
        "&7check this guide before experimenting.": "&7consultez ce guide avant d'expérimenter.",
        "&8players from messy border conflicts.": "&8les joueurs des conflits de frontières.",
    },
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
        # Preserve length when possible.
        if len(lines) == len(template):
            return lines
        # Fallback: pad/truncate.
        out = lines[: len(template)]
        while len(out) < len(template):
            out.append(template[len(out)] if len(out) < len(template) else " ")
        return out
    return packed


def needs_fill(existing: Any, english: Any) -> bool:
    if existing is None:
        return True
    if existing == "" or existing == []:
        return True
    return pack_value(existing) == pack_value(english)


def translate_packed(lang: str, google_code: str, mymemory_code: str, text: str, cache: dict[str, str], manual: dict[str, str]) -> str:
    if not re.search(r"[A-Za-z]", text):
        return text
    if text in manual:
        return manual[text]
    if text in cache and cache[text] and cache[text] != text:
        return cache[text]

    # Try line-wise manual for multi-line.
    if "\n" in text:
        parts = text.split("\n")
        if all((p in manual) or (p in cache and cache.get(p) != p) or (not re.search(r"[A-Za-z]", p)) for p in parts):
            out_parts = []
            for p in parts:
                if not re.search(r"[A-Za-z]", p):
                    out_parts.append(p)
                elif p in manual:
                    out_parts.append(manual[p])
                else:
                    out_parts.append(cache[p])
            joined = "\n".join(out_parts)
            cache[text] = joined
            return joined

    protected, mapping = protect(text)
    # Google
    for attempt in range(4):
        try:
            result = GoogleTranslator(source="en", target=google_code).translate(protected)
            if result:
                restored = unprotect(result, mapping)
                if restored != text:
                    cache[text] = restored
                    return restored
        except Exception:  # noqa: BLE001
            time.sleep(1.2 * (attempt + 1))

    # MyMemory fallback
    for attempt in range(3):
        try:
            result = MyMemoryTranslator(source="en-GB", target=mymemory_code).translate(protected)
            if result:
                restored = unprotect(result, mapping)
                if restored and restored != text:
                    cache[text] = restored
                    return restored
        except Exception:  # noqa: BLE001
            time.sleep(1.0 * (attempt + 1))

    # Last resort: translate each line independently with longer waits.
    if "\n" in text:
        out_lines = []
        for line in text.split("\n"):
            out_lines.append(translate_packed(lang, google_code, mymemory_code, line, cache, manual))
            time.sleep(0.25)
        joined = "\n".join(out_lines)
        cache[text] = joined
        return joined

    print(f"  WARN keeping English for {lang}: {text[:80]!r}", flush=True)
    cache[text] = text
    return text


def merge_codex(lang: str) -> None:
    merged: dict = {}
    for bundle in BUNDLES:
        path = LANG_ROOT / lang / bundle
        if path.exists():
            merged.update(load_yaml(path))
    titles = {
        "spanish_mx": "Español (México)",
        "spanish_ar": "Español (Argentina)",
        "portuguese_br": "Português (Brasil)",
        "french_fr": "Français",
        "italian_it": "Italiano",
        "german_de": "Deutsch",
        "polish_pl": "Polski",
        "modern_english": "Modern English",
        "old_english": "Old English",
    }
    header = [
        "# ======================================",
        f"# AegisGuard v1.3.0 - Codex: {titles.get(lang, lang)}",
        f"# Language ID: {lang}",
        f"# Fallback bundle mirrored from lang/{lang}/",
        "# ======================================",
    ]
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
        merged,
        Dumper=LiteralDumper,
        allow_unicode=True,
        sort_keys=False,
        width=120,
        default_flow_style=False,
    )
    body = re.sub(r"^'([A-Za-z_][A-Za-z0-9_]*)':", r"\1:", body, flags=re.M)
    (CODEX_ROOT / f"{lang}.yml").write_text("\n".join(header) + "\n\n" + body, encoding="utf-8")


def ensure_extra_keys(missing: dict[str, Any]) -> dict[str, Any]:
    extras = {
        "expansion_manager_unavailable": "&c[AegisGuard] Expansion request manager is unavailable.",
        "language_invalid_style": "&c⚠ Invalid language style: {STYLE}",
        "language_set_to": "&6🕮 Language set to: &b{STYLE}",
        "exchange_unavailable": "&cClaimBlocks Exchange is unavailable.",
        "exchange_disabled": "&cClaimBlocks Exchange is disabled in config.yml.",
        "snapshots_unavailable": "&cSnapshot system is unavailable.",
        "snapshots_disabled_config": "&cSnapshots are disabled in config.yml.",
    }
    out = dict(missing)
    for k, v in extras.items():
        out.setdefault(k, v)
    return {k: (normalize_colors(v) if isinstance(v, str) else [normalize_colors(str(x)) for x in v]) for k, v in out.items()}


def main() -> int:
    missing = ensure_extra_keys(json.loads(MISSING_PATH.read_text(encoding="utf-8")))

    # Ensure modern_english has every key.
    for key, value in missing.items():
        path = LANG_ROOT / "modern_english" / bundle_for(key)
        data = load_yaml(path)
        if key not in data:
            data[key] = value
            dump_yaml(path, data)
            print(f"+ modern_english {key}", flush=True)

    totals = {}
    for lang, (google_code, mymemory_code) in LANGS.items():
        print(f"== {lang} ==", flush=True)
        cache_path = CACHE_DIR / f"{lang}.json"
        cache = {}
        if cache_path.exists():
            try:
                cache = json.loads(cache_path.read_text(encoding="utf-8"))
            except Exception:  # noqa: BLE001
                cache = {}
        manual = MANUAL.get(lang, {})
        changed = 0
        by_bundle: dict[str, dict[str, Any]] = {}
        for key, eng in missing.items():
            by_bundle.setdefault(bundle_for(key), {})[key] = eng

        for bundle, items in by_bundle.items():
            path = LANG_ROOT / lang / bundle
            data = load_yaml(path) if path.exists() else {}
            for key, eng in items.items():
                existing = data.get(key)
                if not needs_fill(existing, eng):
                    continue
                packed = pack_value(eng)
                translated = translate_packed(lang, google_code, mymemory_code, packed, cache, manual)
                data[key] = unpack_value(translated, eng)
                changed += 1
                if changed % 10 == 0:
                    print(f"  {lang}: {changed} keys...", flush=True)
                    cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
                time.sleep(0.35)
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
