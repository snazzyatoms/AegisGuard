#!/usr/bin/env python3
"""Generate AegisGuard language packs from modern_english with full key parity.

Protects Minecraft color codes and {PLACEHOLDER} tokens during translation.
"""
from __future__ import annotations

import argparse
import hashlib
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
SOURCE = "modern_english"
BUNDLES = ["guis.yml", "system.yml", "upgrades.yml", "expansions.yml"]
CACHE_DIR = ROOT / ".tmp-lang-cache"

LANGUAGES = {
    "portuguese_br": {
        "google": "pt",
        "title": "Brazilian Portuguese",
        "header": "Português (Brasil)",
        "style_self": "&bPortuguês (Brasil)",
    },
    "french_fr": {
        "google": "fr",
        "title": "French",
        "header": "Français",
        "style_self": "&bFrançais",
    },
    "italian_it": {
        "google": "it",
        "title": "Italian",
        "header": "Italiano",
        "style_self": "&bItaliano",
    },
    "german_de": {
        "google": "de",
        "title": "German",
        "header": "Deutsch",
        "style_self": "&bDeutsch",
    },
    "polish_pl": {
        "google": "pl",
        "title": "Polish",
        "header": "Polski",
        "style_self": "&bPolski",
    },
}

STYLE_KEYS = {
    "style_old_english": "&dOld English",
    "style_modern_english": "&aModern English",
    "style_spanish_mx": "&bEspañol (México)",
    "style_spanish_ar": "&bEspañol (Argentina)",
    "style_portuguese_br": "&bPortuguês (Brasil)",
    "style_french_fr": "&bFrançais",
    "style_italian_it": "&bItaliano",
    "style_german_de": "&bDeutsch",
    "style_polish_pl": "&bPolski",
}

# Domain post-fixes applied after machine translation (case-sensitive whole-word-ish).
GLOSSARY = {
    "portuguese_br": [
        (r"\breivindicaç(ão|ões)\b", lambda m: "parcela" if m.group(1).startswith("ã") else "parcelas"),
        (r"\bReivindicaç(ão|ões)\b", lambda m: "Parcela" if m.group(1).startswith("ã") else "Parcelas"),
        (r"\bclaim blocks\b", "Blocos de Claim", re.I),
        (r"\bClaim Blocks\b", "Blocos de Claim"),
        (r"\bAegisGuard\b", "AegisGuard"),
    ],
    "french_fr": [
        (r"\bClaim Blocks\b", "Blocs de claim"),
        (r"\bclaim blocks\b", "blocs de claim", re.I),
    ],
    "italian_it": [
        (r"\bClaim Blocks\b", "Blocchi claim"),
        (r"\bclaim blocks\b", "blocchi claim", re.I),
    ],
    "german_de": [
        (r"\bClaim Blocks\b", "Claim-Blöcke"),
        (r"\bclaim blocks\b", "Claim-Blöcke", re.I),
    ],
    "polish_pl": [
        (r"\bClaim Blocks\b", "Bloki claim"),
        (r"\bclaim blocks\b", "bloki claim", re.I),
    ],
}

PROTECT_RE = re.compile(
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|§x(?:§[0-9a-f]){6}|AegisGuard|/aegis(?:guard)?(?:admin)?(?:\s+[a-z0-9_<>\|\-]+)*)",
    re.IGNORECASE,
)
LETTER_RE = re.compile(r"[A-Za-zÀ-ÿ]")


def load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as fh:
        data = yaml.safe_load(fh)
    if not isinstance(data, dict):
        raise ValueError(f"Expected mapping in {path}")
    return data


def dump_yaml(path: Path, data: dict, header: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    class LiteralDumper(yaml.SafeDumper):
        pass

    def str_representer(dumper, value: str):
        style = "|" if "\n" in value else "'"
        return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)

    LiteralDumper.add_representer(str, str_representer)
    body = yaml.dump(
        data,
        Dumper=LiteralDumper,
        allow_unicode=True,
        sort_keys=False,
        width=120,
        default_flow_style=False,
    )
    path.write_text(header.rstrip() + "\n\n" + body, encoding="utf-8")


def collect_strings(obj: Any, out: set[str]) -> None:
    if isinstance(obj, str):
        out.add(obj)
    elif isinstance(obj, list):
        for item in obj:
            collect_strings(item, out)
    elif isinstance(obj, dict):
        for value in obj.values():
            collect_strings(value, out)


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
    # Repair common translator damage around protection tokens only.
    text = text.replace("⟦ ", "⟦").replace(" ⟧", "⟧")
    text = re.sub(r"⟦T(\d{4})⟧", lambda m: mapping.get(f"⟦T{m.group(1)}⟧", m.group(0)), text)
    return text


def should_translate(text: str) -> bool:
    stripped = text.strip()
    if not stripped:
        return False
    # Strip color codes / placeholders; translate only if Latin letters remain.
    remainder = PROTECT_RE.sub("", stripped)
    remainder = re.sub(r"[0-9_/\\|<>=+\-–—•·…★☆✔✖❌⚡⛏🛡⚙✦⟵⟶←→]+", " ", remainder)
    return bool(LETTER_RE.search(remainder))


def apply_glossary(lang: str, text: str) -> str:
    for item in GLOSSARY.get(lang, []):
        if len(item) == 2:
            pattern, repl = item
            flags = 0
        else:
            pattern, repl, flags = item
        text = re.sub(pattern, repl, text, flags=flags if isinstance(flags, int) else 0)
    text = text.replace("Aegis Guard", "AegisGuard")
    return text


class TranslatorCache:
    def __init__(self, lang: str):
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        self.path = CACHE_DIR / f"{lang}.json"
        self.data: dict[str, str] = {}
        if self.path.exists():
            self.data = json.loads(self.path.read_text(encoding="utf-8"))

    def get(self, src: str) -> str | None:
        return self.data.get(src)

    def put(self, src: str, dst: str) -> None:
        self.data[src] = dst

    def save(self) -> None:
        self.path.write_text(json.dumps(self.data, ensure_ascii=False, indent=0), encoding="utf-8")


def translate_batch(translator: GoogleTranslator, texts: list[str], retries: int = 5) -> list[str]:
    # Google handles newline-separated batches reasonably for short UI strings.
    payload = "\n".join(texts)
    last_err: Exception | None = None
    for attempt in range(retries):
        try:
            result = translator.translate(payload)
            if result is None:
                raise RuntimeError("empty translation")
            parts = result.split("\n")
            if len(parts) != len(texts):
                # Fallback: translate one-by-one for this batch
                return [translator.translate(t) for t in texts]
            return parts
        except Exception as err:  # noqa: BLE001
            last_err = err
            time.sleep(1.2 * (attempt + 1))
    raise RuntimeError(f"Translation failed after retries: {last_err}")


def build_translation_map(lang: str, google_code: str, strings: set[str]) -> dict[str, str]:
    cache = TranslatorCache(lang)
    translator = GoogleTranslator(source="en", target=google_code)
    pending: list[str] = []
    protected_pending: list[tuple[str, str, dict[str, str]]] = []

    for src in sorted(strings, key=lambda s: (len(s), s)):
        if cache.get(src) is not None:
            continue
        if not should_translate(src):
            cache.put(src, src)
            continue
        protected, mapping = protect(src)
        protected_pending.append((src, protected, mapping))

    # Batch by character budget
    batch_src: list[str] = []
    batch_prot: list[str] = []
    batch_maps: list[dict[str, str]] = []
    budget = 0

    def flush() -> None:
        nonlocal batch_src, batch_prot, batch_maps, budget
        if not batch_src:
            return
        translated = translate_batch(translator, batch_prot)
        for src, dst, mapping in zip(batch_src, translated, batch_maps):
            restored = unprotect(dst, mapping)
            restored = apply_glossary(lang, restored)
            # Preserve leading/trailing color-code-only spacing patterns roughly
            cache.put(src, restored)
        cache.save()
        print(f"  [{lang}] translated {len(cache.data)}/{len(strings)} unique strings", flush=True)
        batch_src, batch_prot, batch_maps, budget = [], [], [], 0
        time.sleep(0.35)

    for src, protected, mapping in protected_pending:
        cost = len(protected) + 1
        if batch_src and budget + cost > 3500:
            flush()
        batch_src.append(src)
        batch_prot.append(protected)
        batch_maps.append(mapping)
        budget += cost
        if len(batch_src) >= 40:
            flush()
    flush()
    cache.save()
    return cache.data


def map_object(obj: Any, table: dict[str, str]) -> Any:
    if isinstance(obj, str):
        return table.get(obj, obj)
    if isinstance(obj, list):
        return [map_object(item, table) for item in obj]
    if isinstance(obj, dict):
        return {key: map_object(value, table) for key, value in obj.items()}
    return obj


def flatten(prefix: str, obj: Any, out: dict[str, Any]) -> None:
    if isinstance(obj, dict):
        for key, value in obj.items():
            path = f"{prefix}.{key}" if prefix else str(key)
            flatten(path, value, out)
    else:
        out[prefix] = obj


def ensure_style_keys(data: dict, lang: str) -> None:
    styles = dict(STYLE_KEYS)
    styles[f"style_{lang}"] = LANGUAGES[lang]["style_self"]
    # Insert near settings language keys when present; otherwise append.
    for key, value in styles.items():
        data[key] = value


def parity_report(source_data: dict[str, dict], lang: str, translated: dict[str, dict]) -> list[str]:
    missing: list[str] = []
    for bundle, src in source_data.items():
        flat_src: dict[str, Any] = {}
        flat_dst: dict[str, Any] = {}
        flatten("", src, flat_src)
        flatten("", translated[bundle], flat_dst)
        for key in flat_src:
            if key not in flat_dst:
                missing.append(f"{bundle}:{key}")
    return missing


def merge_for_codex(translated: dict[str, dict]) -> dict:
    merged: dict = {}
    for bundle in BUNDLES:
        merged.update(translated[bundle])
    return merged


def generate_language(lang: str, source_data: dict[str, dict], strings: set[str]) -> None:
    meta = LANGUAGES[lang]
    print(f"== Generating {lang} ({meta['title']}) ==", flush=True)
    table = build_translation_map(lang, meta["google"], strings)
    out: dict[str, dict] = {}
    for bundle, data in source_data.items():
        translated = map_object(data, table)
        if bundle == "guis.yml":
            ensure_style_keys(translated, lang)
        out[bundle] = translated
        header = (
            f"# ======================================\n"
            f"# AegisGuard v1.3.0 - {meta['header']}\n"
            f"# Language ID: {lang}\n"
            f"# File: {bundle}\n"
            f"# Source of truth keys: modern_english/{bundle}\n"
            f"# ======================================"
        )
        dump_yaml(LANG_ROOT / lang / bundle, translated, header)

    missing = parity_report(source_data, lang, out)
    if missing:
        raise SystemExit(f"{lang} missing keys after generation: {missing[:20]} (+{max(0,len(missing)-20)} more)")

    codex_header = (
        f"# ======================================\n"
        f"# AegisGuard v1.3.0 - Codex: {meta['header']}\n"
        f"# Language ID: {lang}\n"
        f"# Fallback bundle mirrored from lang/{lang}/\n"
        f"# ======================================"
    )
    dump_yaml(CODEX_ROOT / f"{lang}.yml", merge_for_codex(out), codex_header)
    print(f"== Done {lang}: keys OK ==", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", nargs="*", choices=sorted(LANGUAGES), help="Generate only these languages")
    args = parser.parse_args()
    targets = args.only or list(LANGUAGES)

    source_data = {bundle: load_yaml(LANG_ROOT / SOURCE / bundle) for bundle in BUNDLES}
    strings: set[str] = set()
    for data in source_data.values():
        collect_strings(data, strings)
    print(f"Source unique strings: {len(strings)}", flush=True)

    for lang in targets:
        generate_language(lang, source_data, strings)
    return 0


if __name__ == "__main__":
    sys.exit(main())
