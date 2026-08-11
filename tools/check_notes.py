# -*- coding: utf-8 -*-
"""Asset checks for notes.json (Trellis basket 169) + divine-name case checks.

Section 6 mirrors StudyNotes.find() exactly: a match is a PHRASE if it contains any
char outside [A-Za-z'] (so "Jehovah-jireh" and "Lord GOD" are phrases); otherwise it is
a whole-word token match. Case-insensitive unless the note sets caseSensitive.
"""
import json, io, re, sys, collections

import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets")
RS, RE = chr(0xE000), chr(0xE001)
plain = lambda s: ''.join(c for c in s if c not in (RS, RE))
isword = lambda c: ('A' <= c <= 'Z') or ('a' <= c <= 'z') or c == "'"
isphrase = lambda s: any(not isword(c) for c in s)
TOK = re.compile(r"[A-Za-z']+")

_c = {}
def book(b):
    if b not in _c: _c[b] = json.load(io.open("%s/bible/%d.json" % (ROOT, b), encoding="utf-8"))
    return _c[b]
def vt(b, c, v): return plain(book(b)[c-1][v-1])

d = json.load(io.open(ROOT + "/study/notes.json", encoding="utf-8"))
words, passages = d["words"], d["passages"]
fail = []
bad = fail.append

# 1. ids unique
dupes = [k for k, v in collections.Counter(n["id"] for n in words + passages).items() if v > 1]
if dupes: bad("duplicate ids: %s" % dupes)

# 2. title / body / >=1 source
for n in words + passages:
    if not n.get("title", "").strip(): bad("%s: empty title" % n["id"])
    if not n.get("body", "").strip():  bad("%s: empty body" % n["id"])
    if not n.get("sources"):           bad("%s: no sources" % n["id"])

# 3. passage phrase occurs in its verse
for p in passages:
    if p["phrase"].lower() not in vt(p["book"], p["chapter"], p["verse"]).lower():
        bad("%s: phrase %r not in %d %d:%d" % (p["id"], p["phrase"], p["book"], p["chapter"], p["verse"]))

# 4. no hedge words
HEDGE = re.compile(r"\b(scholars say|widely|many scholars|most scholars|it is believed|"
                   r"weigh it for yourself|some say|arguably|generally accepted)\b", re.I)
for n in words + passages:
    m = HEDGE.search(n["body"])
    if m: bad("%s: hedge word %r" % (n["id"], m.group(0)))

# 5. no wikipedia
for n in words + passages:
    for s in n["sources"]:
        if "wikipedia" in json.dumps(s, ensure_ascii=False).lower():
            bad("%s: wikipedia source %r" % (n["id"], s))

# 6. count every match the way the app resolves it
cnt = collections.Counter()
specs = []                                    # (matchstring, key, kind, cs, noteid)
for w in words:
    cs = w.get("caseSensitive", False)
    for m in w["match"]:
        key = m if cs else m.lower()
        specs.append((m, key, "phrase" if isphrase(m) else "word", cs, w["id"]))

for b in range(1, 67):
    for ch in book(b):
        for vv in ch:
            t = plain(vv); low = t.lower()
            toks = TOK.findall(t)
            tokset = collections.Counter(toks)
            tokset_low = collections.Counter(x.lower() for x in toks)
            for m, key, kind, cs, _ in specs:
                if kind == "word":
                    cnt[key] += (tokset if cs else tokset_low)[key]
                else:
                    cnt[key] += (t if cs else low).count(key)

# A dead synonym is only a warning — the KJV spells some of these differently
# ("cherubims", "seraphims", "sadducees"). What must never happen is a note with no
# live match at all, which is a hard failure below.
warn = []
for m, key, kind, cs, nid in specs:
    if cnt[key] == 0:
        warn.append("%s: match %r (%s) never occurs in this edition" % (nid, m, kind))

# every note must have at least one live match
for w in words:
    cs = w.get("caseSensitive", False)
    if not any(cnt[m if cs else m.lower()] for m in w["match"]):
        bad("%s: NO live match — the note can never be shown" % w["id"])

# 7. numeric claims in the divine-name bodies must agree with the assets
byid = {w["id"]: w for w in words}
lord = cnt["LORD"] + cnt["LORD'S"]
if lord != 6569: bad("LORD+LORD'S = %d, body says 6,569" % lord)
if cnt["GOD"] != 310: bad("GOD = %d, body says 310" % cnt["GOD"])
if cnt["Lord GOD"] != 302: bad("'Lord GOD' = %d, body says 302" % cnt["Lord GOD"])
for nid, needle in [("w-divine-name-lord", "6,569"), ("w-divine-name-god-caps", "310 times"),
                    ("w-divine-name-god-caps", "302 of them")]:
    if needle not in byid[nid]["body"]: bad("%s: body no longer states %r" % (nid, needle))

print("--- divine-name / Rotherham match counts ---")
for m in ["LORD", "LORD'S", "GOD", "Lord GOD", "Jehovah", "Jehovah–jireh",
          "Jehovah–nissi", "Jehovah–shalom", "everlasting", "eternal"]:
    key = m if any(s[0] == m and s[3] for s in specs) else m.lower()
    print("  %-22r -> %5d" % (m, cnt[key]))

print("\nwords: %d  passages: %d  total: %d" % (len(words), len(passages), len(words) + len(passages)))
if warn:
    print("\n%d warning(s) — dead synonyms, note still reachable:" % len(warn))
    for w_ in warn: print("  ~", w_)
if fail:
    print("\nFAILED %d check(s):" % len(fail))
    for f in fail: print("  -", f)
    sys.exit(1)
print("\nALL CHECKS PASSED")
