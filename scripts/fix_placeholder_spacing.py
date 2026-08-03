#!/usr/bin/env python3
"""Restore placeholder spacing in translated packs to match modern_english."""
from __future__ import annotations

import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1] / "aegisguard-modern/src/main/resources/lang"
BUNDLES = ["guis.yml", "system.yml", "upgrades.yml", "expansions.yml"]
LANGS = ["portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl"]
PLACEHOLDER = re.compile(r"\{[A-Z0-9_]+\}")


def load(path: Path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def restore_spacing(english: str, translated: str) -> str:
    if not isinstance(translated, str) or not isinstance(english, str):
        return translated
    eng_ph = PLACEHOLDER.findall(english)
    dst_ph = PLACEHOLDER.findall(translated)
    if not eng_ph or set(eng_ph) != set(dst_ph):
        return translated

    out = translated
    for ph in sorted(set(eng_ph), key=len, reverse=True):
        # If English uses ": {PH}" and translation compacted to ":{PH}", restore.
        if re.search(rf":\s+{re.escape(ph)}", english):
            out = re.sub(rf":\s*{re.escape(ph)}", f": {ph}", out)
        # If English has a space immediately before placeholder, ensure one remains.
        if re.search(rf"\s{re.escape(ph)}", english):
            out = re.sub(rf"(?<![\s:&]){re.escape(ph)}", f" {ph}", out)
        # If English has a space immediately after placeholder, ensure one remains
        # when the next char is alphanumeric (not punctuation/end).
        if re.search(rf"{re.escape(ph)}\s+\S", english):
            out = re.sub(rf"{re.escape(ph)}(?=[A-Za-zÀ-ÿ0-9])", f"{ph} ", out)
    out = re.sub(r" {2,}", " ", out)
    return out


def walk_fix(eng_obj, dst_obj):
    if isinstance(eng_obj, dict) and isinstance(dst_obj, dict):
        return {k: walk_fix(eng_obj.get(k), dst_obj.get(k)) if k in dst_obj else dst_obj.get(k)
                for k in dst_obj}
    if isinstance(eng_obj, list) and isinstance(dst_obj, list) and len(eng_obj) == len(dst_obj):
        return [walk_fix(a, b) for a, b in zip(eng_obj, dst_obj)]
    if isinstance(eng_obj, str) and isinstance(dst_obj, str):
        return restore_spacing(eng_obj, dst_obj)
    return dst_obj


def dump(path: Path, data: dict):
    # Keep existing header comments if present.
    raw = path.read_text(encoding="utf-8")
    header_lines = []
    for line in raw.splitlines():
        if line.startswith("#") or not line.strip():
            header_lines.append(line)
            if line.strip() and not line.startswith("#"):
                break
            continue
        break
    # Simpler: preserve first comment block until blank line after headers
    header = []
    lines = raw.splitlines()
    i = 0
    while i < len(lines) and (lines[i].startswith("#") or lines[i].strip() == ""):
        header.append(lines[i])
        i += 1
        if i > 1 and lines[i - 1].strip() == "" and not lines[i - 1].startswith("#"):
            break
        if i > 8:
            break

    class LiteralDumper(yaml.SafeDumper):
        pass

    def str_representer(dumper, value: str):
        style = "|" if "\n" in value else "'"
        return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)

    LiteralDumper.add_representer(str, str_representer)
    body = yaml.dump(data, Dumper=LiteralDumper, allow_unicode=True, sort_keys=False, width=120)
    prefix = "\n".join(header).rstrip() + "\n\n" if header else ""
    path.write_text(prefix + body, encoding="utf-8")


def main():
    for lang in LANGS:
        lang_dir = ROOT / lang
        if not lang_dir.exists():
            continue
        for bundle in BUNDLES:
            eng = load(ROOT / "modern_english" / bundle)
            path = lang_dir / bundle
            if not path.exists():
                continue
            dst = load(path)
            fixed = walk_fix(eng, dst)
            dump(path, fixed)
            print(f"fixed {lang}/{bundle}")


if __name__ == "__main__":
    main()
