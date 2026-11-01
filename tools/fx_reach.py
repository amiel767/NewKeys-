"""Separe la fenetre FX vivante du cluster mort, par imports REELS.

Un symbole du meme paquet est visible SANS import : c'est ce que le premier
essai avait rate, et il declarait morts FxCard, FxEffectPage, FxOrb... qui sont
pourtant dessines par `FxWindow.kt`. On tient donc compte de deux choses :

  1. la visibilite (meme paquet, import explicite, import wildcard) ;
  2. l'usage REEl (commentaires et chaines retires, sinon un symbole seulement
     cite dans un KDoc passerait pour utilise).

L'analyse surestime volontairement le vivant : on ne supprime que ce qui est
certainement mort.
"""
import re, glob, sys

ROOT = 'app/src'
files = [p for p in glob.glob(ROOT + '/**/*.kt', recursive=True)]


def strip_code(txt):
    """Retire commentaires et litteraux : on ne garde que le code."""
    txt = re.sub(r'/\*.*?\*/', ' ', txt, flags=re.S)
    txt = re.sub(r'//[^\n]*', ' ', txt)
    txt = re.sub(r'"""(?:.|\n)*?"""', '""', txt)
    txt = re.sub(r'"(?:\\.|[^"\\])*"', '""', txt)
    txt = re.sub(r"'(?:\\.|[^'\\])*'", "''", txt)
    return txt


PKG, CODE, IMPORTS = {}, {}, {}
for p in files:
    raw = open(p, encoding='utf-8').read()
    m = re.search(r'^package\s+([\w.]+)', raw, re.M)
    PKG[p] = m.group(1) if m else ''
    CODE[p] = strip_code(raw)
    IMPORTS[p] = re.findall(r'^import\s+([\w.]+)', raw, re.M)


def decls(path):
    out = set()
    for m in re.finditer(
        r'\b(?:class|interface|object|fun|val|var|typealias)\s+([A-Za-z_]\w*)',
        CODE[path]):
        out.add(m.group(1))
    return out


DECL = {p: decls(p) for p in files}
owner = {}
for p in files:
    for s in DECL[p]:
        owner.setdefault(s, set()).add(p)


def visible(frm):
    """Fichiers dont les symboles sont accessibles sans qualification."""
    out = {q for q in files if q != frm and PKG[q] == PKG[frm]}
    for imp in IMPORTS[frm]:
        if imp.endswith('.*'):
            p = imp[:-2]
            out |= {q for q in files if q != frm and PKG[q] == p}
        else:
            sym, pkg = imp.rsplit('.', 1)
            out |= {q for q in owner.get(sym, ()) if PKG[q] == pkg}
    return out


# un appel qualifie complet (`com.x.y.FxDesignHost(...)`) n'a pas d'import :
# on indexe donc aussi les noms pointes presents dans le code.
QUAL = {}
for p in files:
    QUAL[p] = set(re.findall(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+', CODE[p]))


def uses(frm):
    """Fichiers dont un symbole est REELLEMENT employe par `frm`."""
    words = set(re.findall(r'[A-Za-z_]\w*', CODE[frm]))
    out = set()
    for q in visible(frm):
        if DECL[q] & words:
            out.add(q)
    for q in files:
        if q == frm:
            continue
        if any((PKG[q] + '.' + s) in QUAL[frm] for s in DECL[q]):
            out.add(q)
    return out


ENTREES = sys.argv[1:] or [
    'app/src/main/java/com/soundstage/mixer/ui/MixerScreen.kt']

seen, stack = set(), list(ENTREES)
while stack:
    p = stack.pop()
    if p in seen or p not in PKG:
        continue
    seen.add(p)
    stack.extend(uses(p) - seen)

fx = sorted(p for p in files if '/fx/' in p or 'aetherkeys/fx' in p)
print('=== VIVANT (atteignable depuis %s) ===' % ', '.join(ENTREES))
for p in fx:
    if p in seen:
        print('   ', p.replace('app/src/', ''))
print()
print('=== MORT (jamais atteint) ===')
for p in fx:
    if p not in seen:
        print('   ', p.replace('app/src/', ''))
print()
print('=== fichiers hors fx qui importent un MORT ===')
mort = {p for p in fx if p not in seen}
for p in files:
    if p in mort:
        continue
    hit = uses(p) & mort
    if hit:
        print('    %s -> %s' % (p.replace('app/src/', ''),
                                ', '.join(q.rsplit('/', 1)[1] for q in sorted(hit))))
