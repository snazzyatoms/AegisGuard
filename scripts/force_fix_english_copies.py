#!/usr/bin/env python3
"""Force-retranslate leftover English values in the five new language packs.

Also syncs matching values into codex/<lang>.yml and the translation cache.
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
SOURCE = "modern_english"
BUNDLES = ["guis.yml", "system.yml", "upgrades.yml", "expansions.yml"]
LANGS = {
    "portuguese_br": "pt",
    "french_fr": "fr",
    "italian_it": "it",
    "german_de": "de",
    "polish_pl": "pl",
}

PROTECT_RE = re.compile(
    r"(\{[A-Z0-9_]+\}|&[0-9a-fk-orx]|§[0-9a-fk-orx]|§x(?:§[0-9a-f]){6}|AegisGuard|"
    r"/aegis(?:guard)?(?:admin)?(?:\s+[a-z0-9_<>\|\-]+)*|"
    r"plugins/AegisGuard/[A-Za-z0-9_./\-]+|"
    r"claim_blocks\.[A-Za-z0-9_./\-]+|"
    r"claims\.[A-Za-z0-9_./\-]+)",
    re.IGNORECASE,
)
LETTER_RE = re.compile(r"[A-Za-zÀ-ÿ]")

# Values that may legitimately stay English / symbols.
ALLOW_PLAIN = {
    "hub", "auto", "arena", "console", "dawnreach", "realmforge", "bastion",
    "off", "on", "ok", "n/a", "admin", "shop", "nether", "system", "claimblocks",
    "griefdefender", "vault", "dynmap", "luckperms", "placeholderapi", "worldguard",
    "towny", "essentials", "stonewright", "wayfinder", "starward dominion",
    "starward pulse", "realmspan", "skyline", "<1m",
}

# Exact English source -> forced translation overrides (applied when present).
MANUAL = {
    "portuguese_br": {
        "&3✦ AegisGuard Menu": "&3✦ Menu AegisGuard",
        "&7Status: &f{STATUS}": "&7Estado: &f{STATUS}",
        "&7Status: {STATUS}": "&7Estado: {STATUS}",
        "&7Base: &f{BASE}": "&7Base: &f{BASE}",
        "&7Subtotal: &e{SUBTOTAL}": "&7Subtotal: &e{SUBTOTAL}",
        "&fFrontier Surge III &8(+20)": "&fSurto da Fronteira III &8(+20)",
        "&bTerritory": "&bTerritório",
        "&7Your claim, profile, and land controls.": "&7Sua parcela, perfil e controles de terreno.",
        "&dAccess & Safety": "&dAcesso e Segurança",
        "&7Members, temporary access, and protection.": "&7Membros, acesso temporário e proteção.",
        "&6Economy & Progress": "&6Economia e Progresso",
        "&7Market, ClaimBlocks, upgrades, and auctions.": "&7Mercado, ClaimBlocks, melhorias e leilões.",
        "&aExplore": "&aExplorar",
        "&7Routes and server travel.": "&7Rotas e viagens do servidor.",
    },
    "french_fr": {
        "&3✦ AegisGuard Menu": "&3✦ Menu AegisGuard",
        "&bDiagnostics": "&bDiagnostic",
        "&a✦ Biome Studio": "&a✦ Studio de biomes",
        "&6Alliance Access": "&6Accès d'alliance",
        "&6&lAlliance Access": "&6&lAccès d'alliance",
        "&e[Audit] &7{SUMMARY}": "&e[Audit] &7{SUMMARY}",
        "&6Frontier Rise IV &8(+35)": "&6Essor de la Frontière IV &8(+35)",
        "&eFrontier Pulse II &8(+10)": "&ePulsation de la Frontière II &8(+10)",
        "&fFrontier Surge III &8(+20)": "&fVague de la Frontière III &8(+20)",
        "&a✔ You liked this claim. Total likes: &e{AMOUNT}":
            "&a✔ Vous avez aimé ce claim. Total des likes : &e{AMOUNT}",
        "&bTerritory": "&bTerritoire",
        "&7Your claim, profile, and land controls.": "&7Votre claim, profil et contrôles de terrain.",
        "&dAccess & Safety": "&dAccès et Sécurité",
        "&7Members, temporary access, and protection.": "&7Membres, accès temporaire et protection.",
        "&6Economy & Progress": "&6Économie et Progression",
        "&7Market, ClaimBlocks, upgrades, and auctions.": "&7Marché, ClaimBlocks, améliorations et enchères.",
        "&aExplore": "&aExplorer",
        "&7Routes and server travel.": "&7Itinéraires et voyages du serveur.",
    },
    "italian_it": {
        "&3✦ AegisGuard Menu": "&3✦ Menù AegisGuard",
        "&cMob Griefing": "&cDistruzione mob",
        "&7Base: &f{BASE}": "&7Base: &f{BASE}",
        "&5Horizon Gateway": "&5Portale dell'Orizzonte",
        "&7Chat: &f{MESSAGE}": "&7Chat: &f{MESSAGE}",
        "&6Frontier Rise IV &8(+35)": "&6Ascesa della Frontiera IV &8(+35)",
        "&fFrontier Surge III &8(+20)": "&fOndata della Frontiera III &8(+20)",
        "&6Dawnreach Horizon &8(+{GAIN})": "&6Orizzonte Dawnreach &8(+{GAIN})",
        "&bTerritory": "&bTerritorio",
        "&7Your claim, profile, and land controls.": "&7La tua trama, profilo e controlli del terreno.",
        "&dAccess & Safety": "&dAccesso e Sicurezza",
        "&7Members, temporary access, and protection.": "&7Membri, accesso temporaneo e protezione.",
        "&6Economy & Progress": "&6Economia e Progresso",
        "&7Market, ClaimBlocks, upgrades, and auctions.": "&7Mercato, ClaimBlocks, potenziamenti e aste.",
        "&aExplore": "&aEsplora",
        "&7Routes and server travel.": "&7Percorsi e viaggi del server.",
    },
    "german_de": {
        "&3✦ AegisGuard Menu": "&3✦ AegisGuard-Menü",
        "&cKick / Ban": "&cKicken / Bannen",
        "&eSoft Lockdown": "&eSanfte Sperre",
        "&5Plot Ascension": "&5Plot-Aufstieg",
        "&7Name: &f{NAME}": "&7Name: &f{NAME}",
        "&7Plot: &f{PLOT}": "&7Grundstück: &f{PLOT}",
        "&7Zone: &f{ZONE}": "&7Zone: &f{ZONE}",
        "&a✦ Biome Studio": "&a✦ Biom-Studio",
        "&5Horizon Gateway": "&5Horizon-Tor",
        "&4Territory Doctor": "&4Gebietsarzt",
        "&aGift ClaimBlocks": "&aClaimBlocks verschenken",
        "&bTerritory Doctor": "&bGebietsarzt",
        "&bTerritory Health": "&bGebietgesundheit",
        "&eGuest Pass Guide": "&eGastpass-Leitfaden",
        "&7Chat: &f{MESSAGE}": "&7Chat: &f{MESSAGE}",
        "&7Radius: &a{RADIUS}": "&7Radius: &a{RADIUS}",
        "&7Status: &f{STATUS}": "&7Status: &f{STATUS}",
        "&7Status: {STATUS}": "&7Status: {STATUS}",
        "&bFilter: &f{FILTER}": "&bFilter: &f{FILTER}",
        "&eFrontier Field Guide": "&eGrenzland-Handbuch",
        "&8✦ Claim Block Exchange": "&8✦ ClaimBlock-Tausch",
        "&b✦ Claim Block Exchange": "&b✦ ClaimBlock-Tausch",
        "&eClaiming & Group Plots": "&eClaimen & Gruppenplots",
        "&aFrontier Pulse I &8(+5)": "&aGrenzpuls I &8(+5)",
        "&6Frontier Rise IV &8(+35)": "&6Grenzaufstieg IV &8(+35)",
        "&bFrontier Zenith V &8(+50)": "&bGrenzzenit V &8(+50)",
        "&c&lGuardian Command Center": "&c&lWächter-Kommandozentrale",
        "&eFrontier Pulse II &8(+10)": "&eGrenzpuls II &8(+10)",
        "&fFrontier Surge III &8(+20)": "&fGrenzschub III &8(+20)",
        "&9Skyline Horizon &8(+{GAIN})": "&9Skyline-Horizont &8(+{GAIN})",
        "&b&lClaimBlock Exchange Guide": "&b&lClaimBlock-Tauschleitfaden",
        "&6Dawnreach Horizon &8(+{GAIN})": "&6Dawnreach-Horizont &8(+{GAIN})",
        "&dRealmspan Horizon &8(+{GAIN})": "&dRealmspan-Horizont &8(+{GAIN})",
        "&bTerritory": "&bGebiet",
        "&7Your claim, profile, and land controls.": "&7Dein Claim, Profil und Grundstückskontrollen.",
        "&dAccess & Safety": "&dZugang & Sicherheit",
        "&7Members, temporary access, and protection.": "&7Mitglieder, temporärer Zugang und Schutz.",
        "&6Economy & Progress": "&6Wirtschaft & Fortschritt",
        "&7Market, ClaimBlocks, upgrades, and auctions.": "&7Markt, ClaimBlocks, Upgrades und Auktionen.",
        "&aExplore": "&aErkunden",
        "&7Routes and server travel.": "&7Routen und Serverreisen.",
        "Territory Doctor": "Gebietsarzt",
    },
    "polish_pl": {
        "&3✦ AegisGuard Menu": "&3✦ Menu AegisGuard",
        "&bFrontier Zenith V &8(+50)": "&bZenit Pogranicza V &8(+50)",
        "&bTerritory": "&bTerytorium",
        "&7Your claim, profile, and land controls.": "&7Twoja działka, profil i kontrole terenu.",
        "&dAccess & Safety": "&dDostęp i Bezpieczeństwo",
        "&7Members, temporary access, and protection.": "&7Członkowie, dostęp tymczasowy i ochrona.",
        "&6Economy & Progress": "&6Ekonomia i Postęp",
        "&7Market, ClaimBlocks, upgrades, and auctions.": "&7Rynek, ClaimBlocks, ulepszenia i aukcje.",
        "&aExplore": "&aOdkrywaj",
        "&7Routes and server travel.": "&7Trasy i podróże serwerowe.",
    },
}

# Improve a few Portuguese labels that were awkward machine translations.
EXTRA_VALUE_FIXES = {
    "portuguese_br": {
        "&aBloqueios de parcela de presentes": "&aPresentear ClaimBlocks",
        "&7transferir propriedade ou blocos de presentes.": "&7transferir propriedade ou presentear blocos.",
    },
    "french_fr": {
        "&aBlocs de claim de cadeaux": "&aOffrir des blocs de claim",
    },
    "german_de": {
        "&aGift ClaimBlocks": "&aClaimBlocks verschenken",
    },
    "italian_it": {
        "&aBlocchi di richiesta regalo": "&aRegala ClaimBlocks",
    },
    "polish_pl": {
        "&aBloki roszczeń podarunkowych": "&aPodaruj ClaimBlocks",
    },
}


def strip_for_allow(text: str) -> str:
    plain = PROTECT_RE.sub(" ", text)
    plain = re.sub(r"[0-9_/\\|<>=+\-–—•·…★☆✔✖❌⚡⛏🛡⚙✦⟵⟶←→]+", " ", plain)
    return re.sub(r"\s+", " ", plain).strip().lower()


def should_force(text: str) -> bool:
    plain = strip_for_allow(text)
    if not plain or not LETTER_RE.search(plain):
        return False
    if plain in ALLOW_PLAIN:
        return False
    if re.fullmatch(r"\{[a-z0-9_]+\}[dhm]", plain):
        return False
    # Keep pure placeholder / path-ish rows when little prose remains.
    words = [w for w in plain.split() if len(w) > 1]
    if not words:
        return False
    return True


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
    text = text.replace("⟦ ", "⟦").replace(" ⟧", "⟧")
    return text


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
    # Keep contract tests that substring-match "key:" working.
    body = re.sub(r"^'([A-Za-z_][A-Za-z0-9_]*)':", r"\1:", body, flags=re.M)
    header = ""
    if header_lines:
        header = "\n".join(header_lines).rstrip() + "\n\n"
    elif path.exists():
        # Preserve existing comment header if present.
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


def walk_replace(obj: Any, table: dict[str, str]) -> tuple[Any, int]:
    changed = 0
    if isinstance(obj, str):
        if obj in table and table[obj] != obj:
            return table[obj], 1
        return obj, 0
    if isinstance(obj, list):
        out = []
        for item in obj:
            new_item, c = walk_replace(item, table)
            changed += c
            out.append(new_item)
        return out, changed
    if isinstance(obj, dict):
        out = {}
        for key, value in obj.items():
            new_val, c = walk_replace(value, table)
            changed += c
            out[key] = new_val
        return out, changed
    return obj, 0


def collect_identical(eng: dict[str, Any], dst: dict[str, Any], out: set[str]) -> None:
    if isinstance(eng, dict) and isinstance(dst, dict):
        for key, ev in eng.items():
            if key in dst:
                collect_identical(ev, dst[key], out)
    elif isinstance(eng, list) and isinstance(dst, list):
        for ev, dv in zip(eng, dst):
            collect_identical(ev, dv, out)
    elif isinstance(eng, str) and isinstance(dst, str):
        if eng == dst and should_force(eng):
            out.add(eng)


def translate_one(translator: GoogleTranslator, text: str) -> str:
    protected, mapping = protect(text)
    last_err: Exception | None = None
    for attempt in range(5):
        try:
            result = translator.translate(protected)
            if not result:
                raise RuntimeError("empty")
            restored = unprotect(result, mapping)
            restored = restored.replace("Aegis Guard", "AegisGuard")
            return restored
        except Exception as err:  # noqa: BLE001
            last_err = err
            time.sleep(1.0 * (attempt + 1))
    raise RuntimeError(f"translate failed: {last_err}")


def merge_codex(lang: str) -> None:
    merged: dict = {}
    for bundle in BUNDLES:
        merged.update(load_yaml(LANG_ROOT / lang / bundle))
    titles = {
        "portuguese_br": "Português (Brasil)",
        "french_fr": "Français",
        "italian_it": "Italiano",
        "german_de": "Deutsch",
        "polish_pl": "Polski",
    }
    header = [
        "# ======================================",
        f"# AegisGuard v1.3.0 - Codex: {titles[lang]}",
        f"# Language ID: {lang}",
        f"# Fallback bundle mirrored from lang/{lang}/",
        "# ======================================",
    ]
    dump_yaml(CODEX_ROOT / f"{lang}.yml", merged, header)


def sync_spanish_old_codex_section_keys() -> None:
    """Ensure codex mirrors for spanish/old_english pick up new main-menu section keys."""
    for lang in ("spanish_mx", "spanish_ar", "old_english", "modern_english"):
        codex_path = CODEX_ROOT / f"{lang}.yml"
        if not codex_path.exists():
            continue
        lang_guis = load_yaml(LANG_ROOT / lang / "guis.yml")
        codex = load_yaml(codex_path)
        changed = False
        for key in (
            "menu_title",
            "main_section_territory_name",
            "main_section_territory_lore",
            "main_section_access_name",
            "main_section_access_lore",
            "main_section_economy_name",
            "main_section_economy_lore",
            "main_section_explore_name",
            "main_section_explore_lore",
        ):
            if key in lang_guis and codex.get(key) != lang_guis[key]:
                codex[key] = lang_guis[key]
                changed = True
        if changed:
            dump_yaml(codex_path, codex)
            print(f"  synced section keys into codex/{lang}.yml")


def main() -> int:
    eng_bundles = {b: load_yaml(LANG_ROOT / SOURCE / b) for b in BUNDLES}
    totals: dict[str, int] = {}

    for lang, google in LANGS.items():
        print(f"== Fixing {lang} ==", flush=True)
        cache_path = CACHE_DIR / f"{lang}.json"
        cache: dict[str, str] = {}
        if cache_path.exists():
            cache = json.loads(cache_path.read_text(encoding="utf-8"))

        identical: set[str] = set()
        dst_bundles = {b: load_yaml(LANG_ROOT / lang / b) for b in BUNDLES}
        for bundle in BUNDLES:
            collect_identical(eng_bundles[bundle], dst_bundles[bundle], identical)

        # Also include cache identities that still look English.
        for src, dst in list(cache.items()):
            if src == dst and should_force(src):
                identical.add(src)

        manual = dict(MANUAL.get(lang, {}))
        manual.update(EXTRA_VALUE_FIXES.get(lang, {}))

        translator = GoogleTranslator(source="en", target=google)
        table: dict[str, str] = {}
        forced = 0
        for src in sorted(identical, key=lambda s: (len(s), s)):
            if src in manual:
                table[src] = manual[src]
            else:
                translated = translate_one(translator, src)
                if translated == src and src in manual:
                    translated = manual[src]
                # If Google still echoes English for multi-word UI, keep trying with a hint once.
                if translated == src and len(strip_for_allow(src).split()) >= 2:
                    try:
                        hinted = translator.translate(
                            f"UI string: {protect(src)[0]}"
                        )
                        if hinted:
                            hinted = re.sub(r"^UI string:\s*", "", hinted, flags=re.I)
                            hinted = unprotect(hinted, protect(src)[1])
                            if hinted and hinted != src:
                                translated = hinted
                    except Exception:  # noqa: BLE001
                        pass
                table[src] = translated
            cache[src] = table[src]
            forced += 1
            if forced % 10 == 0:
                print(f"  [{lang}] fixed {forced}/{len(identical)}", flush=True)
                cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
            time.sleep(0.12)

        # Apply value fixes that may not equal English source (bad prior translations).
        for bad, good in EXTRA_VALUE_FIXES.get(lang, {}).items():
            table[bad] = good
            cache[bad] = good

        # Always apply manual map for coverage even if not currently identical.
        for src, dst in manual.items():
            table[src] = dst
            cache[src] = dst

        changed_total = 0
        for bundle in BUNDLES:
            new_data, changed = walk_replace(dst_bundles[bundle], table)
            # Ensure section keys / menu title from manual English sources are present.
            if bundle == "guis.yml":
                for eng_val, loc_val in manual.items():
                    # set by matching known modern_english keys if values still English
                    pass
                # Direct key assignments for section frames
                guis_eng = eng_bundles["guis.yml"]
                for key in (
                    "menu_title",
                    "main_section_territory_name",
                    "main_section_territory_lore",
                    "main_section_access_name",
                    "main_section_access_lore",
                    "main_section_economy_name",
                    "main_section_economy_lore",
                    "main_section_explore_name",
                    "main_section_explore_lore",
                ):
                    eng_val = guis_eng.get(key)
                    if isinstance(eng_val, str) and eng_val in table:
                        if new_data.get(key) != table[eng_val]:
                            new_data[key] = table[eng_val]
                            changed += 1
            dump_yaml(LANG_ROOT / lang / bundle, new_data)
            changed_total += changed

        cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding="utf-8")
        merge_codex(lang)
        totals[lang] = changed_total
        print(f"== Done {lang}: rewrote {changed_total} string nodes; forced {len(identical)} unique ==", flush=True)

    print("Syncing spanish/old_english/modern_english codex section keys...", flush=True)
    sync_spanish_old_codex_section_keys()
    # Also rebuild spanish/old/modern codex from lang packs for section keys only already done;
    # rebuild modern_english codex section keys from guis
    print("Totals:", totals)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
