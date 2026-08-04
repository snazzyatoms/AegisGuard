#!/usr/bin/env python3
"""Append newly added modern_english keys into all language packs with translations."""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

import yaml
from deep_translator import GoogleTranslator

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from inject_hardcoded_manuals import MANUAL  # noqa: E402

LANG_ROOT = ROOT / "aegisguard-modern/src/main/resources/lang"
CACHE_DIR = ROOT / ".tmp-lang-cache"
CACHE_DIR.mkdir(exist_ok=True)
SOURCE = "modern_english"
BUNDLES = ["system.yml", "guis.yml"]
MARKER = "# --- Hardcoded-string localization pass"

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
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|AegisGuard|"
    r"/[a-z0-9_<>\|\-\s]+|"
    r"ClaimBlocks|TradeStalls?|Guest Pass(?:es)?|"
    r"griefprevention|griefdefender|lands|Vault|Sentinel's Scepter|"
    r"config_schema|CURRENT_SCHEMA|UUID|YML|SQL)",
    re.IGNORECASE,
)


def yaml_escape(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def parse_marked_section(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")
    idx = text.find(MARKER)
    if idx < 0:
        raise SystemExit(f"Marker missing in {path}")
    data = yaml.safe_load(text[idx:]) or {}
    if not isinstance(data, dict):
        raise SystemExit(f"Marked section is not a mapping in {path}")
    return data


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


def translate_string(lang: str, google_code: str, value: str) -> str:
    manual = MANUAL.get(lang, {}).get(value)
    if manual is not None:
        return manual
    cached = cache_get(lang, value)
    if cached is not None:
        return cached
    if lang == "old_english":
        out = (
            value.replace("You ", "Thou ")
            .replace(" you ", " thee ")
            .replace("Your ", "Thy ")
            .replace(" your ", " thy ")
            .replace(" have ", " hast ")
            .replace(" has ", " hath ")
            .replace(" selected", " marked")
        )
        cache_put(lang, value, out)
        return out

    protected, tokens = protect(value)
    plain = PROTECT_RE.sub("", protected).strip(" -:")
    if len(plain) < 2:
        return value
    try:
        translated = GoogleTranslator(source="en", target=google_code).translate(protected)
        time.sleep(0.04)
    except Exception as exc:  # noqa: BLE001
        print(f"  translate fail ({lang}): {exc}")
        translated = protected
    out = unprotect(translated or protected, tokens)
    if value.startswith("&eUsage:") and "/ag" in value and "/ag" not in out:
        out = value
    cache_put(lang, value, out)
    return out


def format_entry(key: str, value: object) -> str:
    if isinstance(value, list):
        lines = [f"{key}:"]
        for item in value:
            lines.append(f"  - {yaml_escape(str(item))}")
        return "\n".join(lines)
    return f"{key}: {yaml_escape(str(value))}"


def main() -> None:
    for bundle in BUNDLES:
        english_path = LANG_ROOT / SOURCE / bundle
        new_keys = parse_marked_section(english_path)
        print(f"{bundle}: {len(new_keys)} new keys")
        for lang, google_code in LANGS.items():
            path = LANG_ROOT / lang / bundle
            data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
            missing: dict[str, object] = {}
            for key, eng in new_keys.items():
                if key not in data or data.get(key) == eng:
                    missing[key] = eng
            if not missing:
                print(f"  {lang}: up to date")
                continue

            text = path.read_text(encoding="utf-8")
            # Rewrite English-copy scalar keys in place when possible.
            for key, eng in list(missing.items()):
                if key in data and data[key] == eng and not isinstance(eng, list):
                    pattern = re.compile(rf"^{re.escape(key)}:\s*.*$", re.M)
                    new_val = translate_string(lang, google_code, str(eng))
                    text2, n = pattern.subn(format_entry(key, new_val), text, count=1)
                    if n:
                        text = text2
                        missing.pop(key)

            if missing:
                if not text.endswith("\n"):
                    text += "\n"
                # Avoid duplicating marker blocks: strip prior marked append for this pass.
                idx = text.find(MARKER)
                if idx >= 0:
                    # Keep file up to marker, then rewrite marker section fully for this bundle.
                    # But marker may only cover previous partial inject — rebuild from current missing+present new keys.
                    head = text[:idx].rstrip() + "\n"
                else:
                    head = text
                block = [MARKER + f" ({lang})"]
                # Prefer writing ALL new_keys translated so the marked section is complete.
                for key, eng in new_keys.items():
                    current = data.get(key)
                    if current is not None and current != eng and key not in missing:
                        val = current
                    elif isinstance(eng, list):
                        val = [translate_string(lang, google_code, str(i)) for i in eng]
                    else:
                        val = translate_string(lang, google_code, str(eng))
                    block.append(format_entry(key, val))
                text = head + "\n".join(block) + "\n"

            path.write_text(text, encoding="utf-8")
            print(f"  {lang}: localized {len(new_keys)} keys in {bundle}")


if __name__ == "__main__":
    main()
