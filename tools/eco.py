#!/usr/bin/env python3
"""Veille economique d'une conversation OpenHands Cloud.

But : suivre une conversation soeur SANS que le suivi lui-meme fasse gonfler la
notre.  Le piege de `conv_peek.py --dump` et de `conv_sync.py` est qu'ils
renvoient le flux entier (jusqu'a ~90 Mo pour un long flux) : l'afficher, meme en
partie, fait exploser la consommation a chaque tour suivant.

Regles de cet outil :
  - l'etat (watermark + resume) vit sur disque, jamais dans la sortie ;
  - seuls les NOUVEAUX evenements sont regardes ;
  - la sortie est bornee en OCTETS (--budget, defaut 1500) ;
  - les messages de l'agent sont resumes a N caracteres (--chars, defaut 240) ;
  - les actions sont resumees sur une ligne (outil + cible), jamais leur contenu.

Usage :
    python3 tools/eco.py <conv_id>              # nouveautes depuis le watermark
    python3 tools/eco.py <conv_id> --last       # dernier message de l'agent
    python3 tools/eco.py <conv_id> --state      # etat en une ligne (sans reseau
                                                # si deja connu recemment)
    python3 tools/eco.py --peer                 # conversation soeur par defaut
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.request
from pathlib import Path

API = "https://app.all-hands.dev/api/v1/conversation"
STATE_DIR = Path(__file__).resolve().parent / ".eco"
STATE_DIR.mkdir(exist_ok=True)

# Conversation soeur de reference (celle reprise par Clone 5).
DEFAULT_PEER = "9c93aa402a634cdfa4a1fbf759935777"
PAGE = 100


def token() -> str:
    t = os.environ.get("OPENHANDS_API_KEY", "")
    if not t:
        sys.exit("OPENHANDS_API_KEY absent de l'environnement")
    return t


def out_of_date(dump: Path, cache: Path) -> bool:
    """Le dump local est-il plus vieux que ce qu'on a deja ?"""
    if not cache.exists():
        return False
    return dump.stat().st_mtime < cache.stat().st_mtime


def fetch_all(cid: str, ttl: int = 90) -> list[dict]:
    """Lit tout le flux, avec cache disque et recul sur 429.

    Le cache est le point cle : sans lui, chaque appel refait N requetes (un flux
    de 9000 evenements = ~92 requetes), ce qui declenche le rate-limit du serveur
    et fait attendre.  Avec un TTL court, une session qui interroge souvent ne
    paye qu'une fois par fenetre.
    """
    cache = STATE_DIR / f"stream_{cid}.json"
    if cache.exists() and time.time() - cache.stat().st_mtime < ttl:
        try:
            return json.loads(cache.read_text())
        except Exception:
            pass

    # Un dump local (conv_peek --dump) evite tout reseau.  Il vit sous
    # tools/.conv_peek/ et peut peser ~90 Mo : on le lit sur place, jamais affiche.
    dump = Path(__file__).resolve().parent / ".conv_peek" / f"{cid}.json"
    if dump.exists() and not out_of_date(dump, cache):
        try:
            evs = json.loads(dump.read_text())
            cache.write_text(json.dumps(evs, ensure_ascii=False))
            return evs
        except Exception:
            pass

    out: list[dict] = []
    page = 1
    hdr = {"Authorization": f"Bearer {token()}", "Accept": "application/json"}
    while True:
        url = f"{API}/{cid}/events/search?limit={PAGE}&page_id={page}"
        req = urllib.request.Request(url, headers=hdr)
        body = None
        for attempt in range(5):
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    body = json.loads(r.read().decode())
                break
            except urllib.error.HTTPError as e:
                if e.code == 429:
                    time.sleep(2.0 * (attempt + 1))
                    continue
                raise
        if body is None:
            break  # rate-limit persistant : on rend ce qu'on a
        items = body if isinstance(body, list) else body.get("items", body.get("events", []))
        if not items:
            break
        out.extend(items)
        # L'API pagine par DECALAGE, pas par numero de page : `next_page_id` est
        # l'offset du lot suivant.  Incrementer un compteur de page rejouait des
        # fenetres qui se recouvrent (~100 evenements d'ecart), donc des requetes
        # inutiles et un 429 qui figeait la veille.
        nxt = body.get("next_page_id") if isinstance(body, dict) else None
        try:
            nxt = int(nxt)
        except (TypeError, ValueError):
            break  # pas d'offset suivant exploitable
        if nxt <= page:
            break
        page = nxt
        if page > 400000:  # garde-fou : 400 000 evenements max
            break
    if out:
        cache.write_text(json.dumps(out, ensure_ascii=False))
    return out


def _blocks(x) -> str:
    if isinstance(x, str):
        return x
    if isinstance(x, list):
        return " ".join(_blocks(i) for i in x)
    if isinstance(x, dict):
        for k in ("text", "content", "value"):
            if k in x:
                return _blocks(x[k])
        return " ".join(_blocks(v) for v in x.values())
    return str(x)


def text_of(ev: dict) -> str:
    v = ev.get("value")
    if isinstance(v, dict):
        for k in ("content", "text", "message", "args"):
            if k in v:
                return _blocks(v[k]).strip()
        return ""
    return _blocks(v).strip()


def kind_of(ev: dict) -> str:
    for k in ("kind", "event", "type"):
        if k in ev:
            return str(ev[k])
    return "?"


def msg_text(ev: dict) -> str:
    v = ev.get("llm_message")
    if isinstance(v, dict):
        if "content" in v:
            return _blocks(v["content"]).strip()
        if "text" in v:
            return _blocks(v["text"]).strip()
    if "value" in ev and isinstance(ev["value"], dict) and "content" in ev["value"]:
        return _blocks(ev["value"]["content"]).strip()
    return text_of(ev)


def action_line(ev: dict) -> str:
    """Une ligne par action : outil + cible, jamais le corps (qui peut peser des Mo)."""
    act = ev.get("action")
    if isinstance(act, dict):
        tool = act.get("kind") or act.get("tool_name") or act.get("name") or "?"
        for k in ("path", "command", "summary"):
            if k in act and act[k]:
                return f"{tool}: {str(act[k]).splitlines()[0]}"[:110]
        return str(tool)[:110]
    if ev.get("tool_name"):
        return str(ev["tool_name"])[:110]
    return str(ev.get("kind", "?"))[:110]


def load_state(cid: str) -> dict:
    p = STATE_DIR / f"{cid}.json"
    if p.exists():
        try:
            return json.loads(p.read_text())
        except Exception:
            pass
    return {"seen": 0, "last_agent": "", "last_ts": ""}


def save_state(cid: str, st: dict) -> None:
    (STATE_DIR / f"{cid}.json").write_text(json.dumps(st, ensure_ascii=False))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("cid", nargs="?", default=DEFAULT_PEER)
    ap.add_argument("--peer", action="store_true", help="utiliser la conversation soeur par defaut")
    ap.add_argument("--last", action="store_true", help="dernier message de l'agent, et rien d'autre")
    ap.add_argument("--state", action="store_true", help="etat en une ligne")
    ap.add_argument("--full", action="store_true", help="rejouer depuis le debut")
    ap.add_argument("--chars", type=int, default=240, help="caracteres par message")
    ap.add_argument("--budget", type=int, default=1500, help="budget de sortie en octets")
    a = ap.parse_args()

    cid = a.cid
    st = load_state(cid)
    evs = fetch_all(cid)
    total = len(evs)

    # Dernier message de l'agent : c'est ce qu'on veut citer, jamais une action.
    last_agent = ""
    for ev in reversed(evs):
        if ev.get("source") == "agent" and "Message" in kind_of(ev):
            t = msg_text(ev)
            if t:
                last_agent = t
                break

    if a.last:
        print(last_agent)
        st.update(seen=total, last_agent=last_agent, last_ts=time.strftime("%Y-%m-%dT%H:%M:%S"))
        save_state(cid, st)
        return 0

    if a.state:
        live = "actif" if (total > st.get("seen", 0)) else "au repos"
        print(f"{total} ev | {live} | dernier message {len(last_agent)} o")
        save_state(cid, st)
        return 0

    start = 0 if a.full else st.get("seen", 0)
    fresh = evs[start:]
    st.update(seen=total, last_agent=last_agent, last_ts=time.strftime("%Y-%m-%dT%H:%M:%S"))
    save_state(cid, st)

    if not fresh:
        print(f"aucun nouvel evenement ({total} au total)")
        return 0

    lines: list[str] = []
    used = 0
    for ev in fresh:
        if "Message" in kind_of(ev):
            t = msg_text(ev).replace("\n", " ")
            line = f"[msg] {t[: a.chars]}"
        elif "Action" in kind_of(ev):
            line = f"[act] {action_line(ev)}"
        else:
            continue
        if used + len(line) + 1 > a.budget:
            lines.append("… (budget atteint)")
            break
        lines.append(line)
        used += len(line) + 1

    print(f"+{len(fresh)} evenements depuis #{start}")
    for l in lines:
        print(l)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
