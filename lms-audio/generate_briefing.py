#!/usr/bin/env python3
"""Speak the AegisGuard 1.2.7 → 1.3.0 briefing through LM Studio local TTS."""

from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time
import wave
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE / "out"
DEFAULT_BASE = os.environ.get("LMS_BASE", "http://127.0.0.1:1234")

# Kokoro-style defaults used by LM Studio. The notebook remaps if the server lists other voices.
VOICE_MIRA = os.environ.get("LMS_VOICE_MIRA", "af_bella")
VOICE_REX = os.environ.get("LMS_VOICE_REX", "am_michael")

DIALOGUE: list[tuple[str, str]] = [
    ("mira", "Welcome back. This is a spoken briefing of AegisGuard from the last public GitHub Release, one point two point seven, through the current one point three point zero work on the V one point three point zero branch."),
    ("rex", "Important context first. One point three point zero is soaking on that branch. It is not a public GitHub Release. The published release on GitHub stays one point two point seven. Existing one point two point seven plots, roles, economy data, and customized config remain valid. Config schema is twelve eighty-six, with automatic migration."),
    ("mira", "Java twenty-one or newer. Minecraft one point twenty or newer. Paper, Purpur, Spigot, and Folia. Do not use Bukkit global reload. Use AegisGuard reload after a proper restart when you swap the jar."),
    ("rex", "The original one point three point zero spine was seven milestones. Milestone one: the Staff Audit Ledger. Sensitive admin work lands in a structured trail you can filter. Restore, repair, migration, bypass, Guest Passes, Lockdown, and Alliance activity sit in one place."),
    ("mira", "Milestone two: Temporary Guest Passes. Time-limited plot access that expires by itself. Presets cover visitor, event guest, temporary builder, and temporary trusted guest. You can use wall-clock time, or Active Playtime, where the clock only ticks while that player is online. Expiry and revoke never rewrite permanent roles."),
    ("rex", "Milestone three: Emergency Plot Lockdown. Fast, reversible, plot-scoped. Grief, a dispute, or maintenance: lock the sensitive actions, then lift it from the plot menu. It is not a world wipe and it is not a permanent ban."),
    ("mira", "Milestone four: Realm Profiles and Noticeboards. A plot can have a public identity: display name, category, greeting, description, and a noticeboard visitors can read from travel and discovery."),
    ("rex", "Milestone five: clearer player guidance. When a blocked action fires, the message now points to the next useful step, including Guest Pass guidance where that applies. The first-claim walkthrough is optional, skippable, never blocks claiming, and you can replay it from Settings or slash A G guide."),
    ("mira", "Milestone six: Routes and Checkpoints. Staff publish named exploration routes with ordered stops. Players discover checkpoints by proximity, track progress, and may get optional completion rewards. Optional teleport defaults off, so discovery never requires a teleport."),
    ("rex", "Milestone seven: Alliance Access. Alliances are completely separate from ownership, money, rentals, and administration. Membership alone grants nothing. Each plot must opt in, toggle by toggle. Enter, Interact, Containers, Build, Animals, Friendly PvP. All default off. Server-wide disallow guardrails can block owners from turning on risky ones. Alliance Entry and Friendly PvP are wired into plot protection."),
    ("mira", "Around that spine came a lot of everyday territory polish. My Rentals and My Tenants for contracts. A Settlements Inbox. ClaimBlocks gifts with slash A G giftblocks. Adjacent claim merge with slash A G merge. Rent confirm before Vault charges. Role nicknames, a trusted catalog role, co-owner gaining manage-members, and member capacity."),
    ("rex", "Staff side: Instant Approvals history is now separate from the Pending Review queue. Convert-to-server has a dedicated GUI. Wand-create and convert share one stewardship pipeline. Convert wipes old access, grants Steward to the acting staffer, and can open Claim Settings. Managing a server zone needs server-zone manage permission or the Steward role, not blanket admin."),
    ("mira", "Safe Travel wraps voluntary teleports: visit, markets, spawn, staff destinations. Cooldowns, confirmation, combat tagging, and safe-point search. Protection also picked up hopper, liquid, teleport, and storm wards. Hooks and protection-compat plugins stay off until a server opts in."),
    ("rex", "Now the later one point three point zero shop work: TradeStalls and Local Market. Local Market is the plot hub. TradeStalls are the native shop: one chest or barrel, a sign beside it, not a double-chest mall. Create from the menu or bind a sign with stall or shop on the first line."),
    ("mira", "Buying is not a surprise click. Preview opens first. Confirm buy is its own screen, and the purchase path uses a per-slot lock so two players cannot double-charge the same listing. Hoppers cannot siphon a stall. You can Visit Stall through Safe Travel and land at the shop."),
    ("rex", "External shops are not banned. MarketBridge defaults to COEXIST, so QuickShop, ChestShop, Shopkeepers, and ExcellentShop can sit beside TradeStalls. Bridge slots live around the Trade Stalls button and do not steal Merge Claims. You can tighten that to disable on bridged plots, or globally if an external shop plugin is present. Default is coexist."),
    ("mira", "Claim Settings stopped being one crowded chest of toggles. It is a hub now: Safety, Mechanics, Wards, and Presets. Personal plots still get HOME, FARM, and cosmetics. Server plots hide those personal extras so staff are not offered home cosmetics on spawn land."),
    ("rex", "Staff Tools grew to a fifty-four slot chest with the same colored section bands as the main menu. Policy, Territory, Recovery, and the Guardian Toolbelt. Expansion Queue versus Instant sits in policy and no longer collides with the Arena button. Arena is in the modules row, slot thirty-eight. Expansion mode is slot sixteen."),
    ("mira", "Snapshots got restored as working staff tools. The Snapshot GUI initializes lazily, only when the snapshot manager exists, so a missing recovery service does not brick menus. From the menu you can create a snapshot here, or snapshot all server zones. Scheduled snapshots exist too, default off, default interval three hundred sixty minutes, targeting server zones."),
    ("rex", "Hear this clearly: those snapshots are plot-data metadata. Owner, flags, access, warp, that class of record. They do not copy world builds. The success text even says builds were not copied. Do not treat a scheduled pass as a WorldEdit backup."),
    ("mira", "First-claim onboarding now asks language first. If the player has no saved language style, Choose Your Language opens before the walkthrough. After they pick, the walkthrough continues. Settings also stopped cycling packs one click at a time. You get a real picker of every installed pack."),
    ("rex", "There are nine packs in parity: Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish. Codex fallbacks stay in sync, so switching should not dump English placeholders into menus. Console logs, Discord embeds, Guardian Guide, Help, and leftover staff chat were pushed through those packs too."),
    ("mira", "The Guardian Guide, the in-game guidebook, was refreshed for one point three point zero. Cards cover what is new, Safe Travel, routes, the language picker, Guest Passes, Lockdown, Alliance Access, Realm identity, and Arena, including the off-by-default warning. Schema twelve eighty-six is the config contract behind that."),
    ("rex", "Claim Status stayed on Territory. It was not moved to another dashboard. The button still sits with plot tools. The GUI was restyled to match the one point three point zero band language: cyan Overview, orange Owner Actions, plus an access snapshot. Merge, transfer, gift ClaimBlocks, and pay upkeep early remain owner actions. All nine languages and Codex received those keys."),
    ("mira", "Arena did ship in this window as an optional module. Cooperative party PvE on bound server plots. Disabled by default. When you enable it, scheduling goes through an internal Arena Scheduler that is Folia-safe: entity, region, global, and async paths. Use slash A G arena diag if it will not activate on Folia. Language packs cover Arena too."),
    ("rex", "Other real work in the same window: Folia-safe scheduling for exchanges and GUI closes, GUI click safety, Doctor health signals, YML to SQL plot migrator with backups, richer PlaceholderAPI, opt-in Discord webhooks for market, rental, lockdown, and Guest Pass events, map marker colors for For Rent, and route guidance on the action bar."),
    ("mira", "If you are coming from one point two point seven: stop the server, back up plugins AegisGuard and the worlds, confirm Java twenty-one, drop the one point three point zero jar, start, let migration run, refresh the lang folder, then run doctor before you reopen. The public GitHub Release page will still show one point two point seven until a public release is deliberately cut."),
    ("rex", "That is the product story. AegisGuard one point three point zero is a territory platform: ClaimBlocks, TradeStalls, Guest Passes, Lockdown, optional Arena, nine languages, and staff recovery that snapshots data, not builds. Simple. Steadfast. Eternal."),
    ("mira", "Thanks for listening. Load the jar on a soak server, walk a claim, open Local Market, open Claim Status, and only then decide when public one point three point zero is ready."),
]


def _http_json(url: str, payload: dict | None = None, timeout: int = 60) -> tuple[int, bytes, str]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    req = Request(url, data=data, headers=headers, method="GET" if data is None else "POST")
    try:
        with urlopen(req, timeout=timeout) as resp:
            body = resp.read()
            ctype = resp.headers.get("Content-Type", "")
            return resp.status, body, ctype
    except HTTPError as exc:
        return exc.code, exc.read() if exc.fp else b"", ""


def probe_lms(base: str) -> dict:
    info = {"base": base.rstrip("/"), "ok": False, "models": [], "error": ""}
    for path in ("/v1/models", "/api/v1/models"):
        try:
            status, body, _ = _http_json(info["base"] + path, timeout=5)
            if status == 200:
                parsed = json.loads(body.decode("utf-8"))
                models = parsed.get("data") or parsed.get("models") or []
                names = []
                for item in models:
                    if isinstance(item, dict):
                        names.append(item.get("id") or item.get("name") or "")
                    else:
                        names.append(str(item))
                info["models"] = [n for n in names if n]
                info["ok"] = True
                info["models_path"] = path
                return info
        except (URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
            info["error"] = str(exc)
    if not info["ok"] and not info["error"]:
        info["error"] = "LM Studio did not answer on /v1/models. Start the app, load a TTS model, and enable the local server."
    return info


def pick_tts_model(models: list[str], preferred: str | None) -> str:
    if preferred:
        return preferred
    lowered = [m.lower() for m in models]
    for needle in ("kokoro", "orpheus", "tts", "speech", "audio"):
        for model, low in zip(models, lowered):
            if needle in low:
                return model
    return models[0] if models else os.environ.get("LMS_TTS_MODEL", "kokoro")


def synthesize(base: str, model: str, voice: str, text: str, timeout: int = 180) -> bytes:
    payload = {
        "model": model,
        "input": text,
        "voice": voice,
        "response_format": "wav",
        "speed": 1.0,
    }
    last_error = None
    for path in ("/v1/audio/speech", "/api/v1/audio/speech"):
        try:
            req = Request(
                base.rstrip("/") + path,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "Accept": "audio/wav, audio/mpeg, application/octet-stream"},
                method="POST",
            )
            with urlopen(req, timeout=timeout) as resp:
                return resp.read()
        except HTTPError as exc:
            last_error = f"{path} HTTP {exc.code}: {exc.read()[:400]!r}"
        except (URLError, TimeoutError, OSError) as exc:
            last_error = f"{path}: {exc}"
    raise RuntimeError(last_error or "TTS request failed")


def wav_frames(blob: bytes) -> tuple[bytes, int, int, int]:
    with wave.open(io.BytesIO(blob), "rb") as reader:
        return reader.readframes(reader.getnframes()), reader.getnchannels(), reader.getsampwidth(), reader.getframerate()


def concat_wavs(blobs: list[bytes], dest: Path) -> Path:
    frames = []
    params = None
    for blob in blobs:
        try:
            chunk, channels, width, rate = wav_frames(blob)
        except wave.Error as exc:
            raise RuntimeError(
                "LM Studio returned audio that is not WAV. Set the TTS model to a Kokoro or Orpheus voice that can emit WAV."
            ) from exc
        if params is None:
            params = (channels, width, rate)
        elif params != (channels, width, rate):
            raise RuntimeError("Voice clips used different WAV formats; keep both voices on the same TTS model.")
        frames.append(chunk)
        # Half-second silence between turns.
        frames.append(b"\x00" * (params[0] * params[1] * params[2] // 2))
    dest.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(dest), "wb") as writer:
        writer.setnchannels(params[0])
        writer.setsampwidth(params[1])
        writer.setframerate(params[2])
        writer.writeframes(b"".join(frames))
    return dest


def generate(
    base: str = DEFAULT_BASE,
    mira: str = VOICE_MIRA,
    rex: str = VOICE_REX,
    model: str | None = None,
    pause: float = 0.15,
) -> Path:
    probe = probe_lms(base)
    if not probe["ok"]:
        raise RuntimeError(probe["error"] or "LM Studio is not reachable at " + base)
    tts_model = pick_tts_model(probe["models"], model)
    print(f"LM Studio ok at {base}. TTS model: {tts_model}. Mira={mira} Rex={rex}")
    blobs: list[bytes] = []
    for index, (speaker, line) in enumerate(DIALOGUE, start=1):
        voice = mira if speaker == "mira" else rex
        label = "Mira" if speaker == "mira" else "Rex"
        print(f"[{index}/{len(DIALOGUE)}] {label}: {line[:72]}...")
        blobs.append(synthesize(base, tts_model, voice, line))
        time.sleep(pause)
    dest = OUT_DIR / "aegisguard-1.3.0-briefing.wav"
    concat_wavs(blobs, dest)
    print(f"Wrote {dest}")
    return dest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate the AegisGuard 1.3.0 LM Studio audio briefing.")
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--mira", default=VOICE_MIRA)
    parser.add_argument("--rex", default=VOICE_REX)
    parser.add_argument("--model", default=os.environ.get("LMS_TTS_MODEL"))
    args = parser.parse_args(argv)
    try:
        generate(base=args.base, mira=args.mira, rex=args.rex, model=args.model)
    except Exception as exc:
        print("Failed:", exc, file=sys.stderr)
        print("Start LM Studio, load a TTS model (Kokoro is a good default), enable the local server on port 1234, then rerun.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
