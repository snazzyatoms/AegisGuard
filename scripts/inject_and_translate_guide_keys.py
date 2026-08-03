#!/usr/bin/env python3
"""Inject missing Guardian Guide / staff / help keys into all language packs.

Reads .tmp-missing-guide-keys.json (English source values extracted from Java),
adds them to modern_english, translates into every non-English pack, and rebuilds
codex mirrors.
"""
from __future__ import annotations

import json
import re
import sys
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

# Target Google language codes.
LANGS = {
    "spanish_mx": "es",
    "spanish_ar": "es",
    "portuguese_br": "pt",
    "french_fr": "fr",
    "italian_it": "it",
    "german_de": "de",
    "polish_pl": "pl",
}

SYSTEM_PREFIXES = (
    "admin_refresh",
    "snapshots_",
    "spawn_",
    "exchange_",
    "language_",
    "permission_label_",
)

PROTECT_RE = re.compile(
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|§x(?:§[0-9a-f]){6}|AegisGuard|"
    r"/aegis(?:guard)?(?:admin)?(?:\s+[a-z0-9_<>\|\-]+)*|"
    r"ClaimBlocks|TradeStalls?|Aegis Scepter)",
    re.IGNORECASE,
)

OLD_ENGLISH_MAP = [
    (r"\bGuide\b", "Tome"),
    (r"\bWelcome to\b", "Hail and welcome to"),
    (r"\bClick to\b", "Press to"),
    (r"\bmenu\b", "ledger"),
    (r"\bMenu\b", "Ledger"),
    (r"\bSettings\b", "Preferences"),
    (r"\bplot\b", "hold"),
    (r"\bPlot\b", "Hold"),
    (r"\bclaims?\b", "holds"),
    (r"\bClaim\b", "Hold"),
    (r"\bclaiming\b", "holding"),
    (r"\bClaiming\b", "Holding"),
    (r"\bReturn to\b", "Go back unto"),
    (r"\bClose\b", "Dismiss"),
    (r"\byour\b", "thy"),
    (r"\bYour\b", "Thy"),
    (r"\byou\b", "thee"),
    (r"\bYou\b", "Thee"),
]


def normalize_colors(text: str) -> str:
    return text.replace("§", "&")


def protect(text: str) -> tuple[str, dict[str, str]]:
    mapping: dict[str, str] = {}

    def repl(match: re.Match[str]) -> str:
        token = f"⟦T{len(mapping):04d}⟧"
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


def dump_yaml(path: Path, data: dict, header_lines: list[str] | None = None) -> None:
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
    if header_lines:
        header = "\n".join(header_lines).rstrip() + "\n\n"
    elif path.exists():
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


def normalize_value(value: Any) -> Any:
    if isinstance(value, list):
        return [normalize_colors(str(v)) for v in value]
    return normalize_colors(str(value))


def old_englishize(text: str) -> str:
    out = text
    for pattern, repl in OLD_ENGLISH_MAP:
        out = re.sub(pattern, repl, out)
    return out


def translate_text(translator: GoogleTranslator | None, text: str, cache: dict[str, str], lang: str) -> str:
    if text.strip() in {"", " "}:
        return text
    # Pure color/space/punctuation lines stay as-is.
    if not re.search(r"[A-Za-z]", text):
        return text
    if text in cache and cache[text] and cache[text] != text:
        return cache[text]
    if lang == "old_english":
        result = old_englishize(text)
        cache[text] = result
        return result
    assert translator is not None
    protected, mapping = protect(text)
    last_err: Exception | None = None
    for attempt in range(5):
        try:
            result = translator.translate(protected)
            if not result:
                raise RuntimeError("empty translation")
            restored = unprotect(result, mapping)
            # Keep blank lore separators intact.
            if text.strip() == "" and restored.strip():
                restored = text
            cache[text] = restored
            return restored
        except Exception as err:  # noqa: BLE001
            last_err = err
            time.sleep(1.0 * (attempt + 1))
    print(f"  WARN translate failed, keeping English: {text!r} ({last_err})", flush=True)
    cache[text] = text
    return text


def translate_value(translator: GoogleTranslator | None, value: Any, cache: dict[str, str], lang: str) -> Any:
    if isinstance(value, list):
        return [translate_text(translator, str(line), cache, lang) for line in value]
    return translate_text(translator, str(value), cache, lang)


def merge_codex(lang: str) -> None:
    merged: dict = {}
    for bundle in BUNDLES:
        path = LANG_ROOT / lang / bundle
        if path.exists():
            merged.update(load_yaml(path))
    titles = {
        "modern_english": "Modern English",
        "old_english": "Old English",
        "spanish_mx": "Español (México)",
        "spanish_ar": "Español (Argentina)",
        "portuguese_br": "Português (Brasil)",
        "french_fr": "Français",
        "italian_it": "Italiano",
        "german_de": "Deutsch",
        "polish_pl": "Polski",
    }
    header = [
        "# ======================================",
        f"# AegisGuard v1.3.0 - Codex: {titles.get(lang, lang)}",
        f"# Language ID: {lang}",
        f"# Fallback bundle mirrored from lang/{lang}/",
        "# ======================================",
    ]
    dump_yaml(CODEX_ROOT / f"{lang}.yml", merged, header)


def main() -> int:
    if not MISSING_PATH.exists():
        print("Missing .tmp-missing-guide-keys.json — extract first.", file=sys.stderr)
        return 1

    missing_raw = json.loads(MISSING_PATH.read_text(encoding="utf-8"))
    missing = {k: normalize_value(v) for k, v in missing_raw.items()}
    print(f"Injecting {len(missing)} keys...", flush=True)

    # 1) modern_english
    by_bundle: dict[str, dict[str, Any]] = {b: {} for b in BUNDLES}
    for key, value in missing.items():
        by_bundle[bundle_for(key)][key] = value

    eng_counts = {}
    for bundle, additions in by_bundle.items():
        if not additions:
            continue
        path = LANG_ROOT / "modern_english" / bundle
        data = load_yaml(path)
        added = 0
        for key, value in additions.items():
            if key not in data:
                data[key] = value
                added += 1
            else:
                # Keep existing if present; do not overwrite curated English.
                pass
        dump_yaml(path, data)
        eng_counts[bundle] = added
        print(f"  modern_english/{bundle}: +{added}", flush=True)

    # Collect unique English strings for translation.
    unique_strings: set[str] = set()
    for value in missing.values():
        if isinstance(value, list):
            unique_strings.update(str(x) for x in value)
        else:
            unique_strings.add(str(value))

    totals: dict[str, int] = {"modern_english": sum(eng_counts.values())}

    # 2) Translate into each target language
    for lang in ["old_english", *LANGS.keys()]:
        print(f"== {lang} ==", flush=True)
        cache_path = CACHE_DIR / f"{lang}.json"
        cache: dict[str, str] = {}
        if cache_path.exists():
            try:
                cache = json.loads(cache_path.read_text(encoding="utf-8"))
            except Exception:  # noqa: BLE001
                cache = {}

        translator = None
        if lang != "old_english":
            translator = GoogleTranslator(source="en", target=LANGS[lang])

        # Prefetch unique strings
        for i, src in enumerate(sorted(unique_strings, key=lambda s: (len(s), s)), 1):
            translate_text(translator, src, cache, lang)
            if i % 25 == 0:
                cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
                print(f"  translated strings {i}/{len(unique_strings)}", flush=True)
                time.sleep(0.05)

        changed = 0
        for bundle, additions in by_bundle.items():
            if not additions:
                continue
            path = LANG_ROOT / lang / bundle
            data = load_yaml(path)
            for key, eng_value in additions.items():
                if key in data and data[key] not in (None, "", [], eng_value):
                    # Keep existing non-English translation.
                    existing = data[key]
                    if existing != eng_value:
                        continue
                data[key] = translate_value(translator, eng_value, cache, lang)
                changed += 1
            dump_yaml(path, data)

        cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
        merge_codex(lang)
        totals[lang] = changed
        print(f"  wrote {changed} keys + codex mirror", flush=True)

    # Always refresh modern_english / existing languages' codex after English inject.
    merge_codex("modern_english")
    print("Totals:", totals, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
