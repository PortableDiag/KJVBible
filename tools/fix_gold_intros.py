# -*- coding: utf-8 -*-
"""Trim narrative INTRODUCTIONS that were golded as divine speech.

Conservative: a span is trimmed only when the lead-in is unambiguously a narrative
speech-introduction (third-person subject + speech verb + comma). Subordinate and
relative clauses are rejected, because there the verb is not introducing speech:
  Ezekiel 14:9  "And if the prophet be deceived when he hath spoken a thing,"
  Numbers 15:23 "Even all that the LORD hath commanded you by the hand of Moses,"

Run with --apply to write; default is a dry run.
"""
import json, io, os, re, sys, collections

import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets")
RS, RE = chr(0xE000), chr(0xE001)
plain = lambda s: ''.join(c for c in s if c not in (RS, RE))
idx = json.load(io.open(ROOT + "/bible/index.json", encoding="utf-8"))
NAME = {b["i"]: b["n"] for b in idx["books"]}
_c = {}
def vt(b, c, v):
    if b not in _c: _c[b] = json.load(io.open("%s/bible/%d.json" % (ROOT, b), encoding="utf-8"))
    return plain(_c[b][c-1][v-1])

VERB = (r'(?:said|saith|spake|answered|commanded|called|cried|charged|sware|'
        r'testified|appeared|came|proceeded)')
# The whole lead-in: connective? + subject + speech verb + addressee? + terminator
LEAD = re.compile(
    r'^(?:And|Then|So|Now|But|Therefore|Moreover|Afterward|Again)?\s*'
    r'(?P<subj>[^,:;]{0,80}?)'
    r'\b(?P<verb>' + VERB + r')\b'
    r'(?:\s+(?:unto|to|him|them|her|me|us|saying)[^,:;]{0,50}?)?'
    r'\s*[,:;]\s+')

SUBORD = re.compile(r'^\s*(?:And\s+|Then\s+|So\s+|But\s+)?'
                    r'(?:if|when|whenever|because|though|although|that|whoso|whosoever|'
                    r'even\s+all|all\s+that|according|concerning|as\s+)\b', re.I)
RELATIVE = re.compile(r'\b(?:which|that|whom|whereof|wherein)\b[^,:;]{0,40}?\b' + VERB + r'\b', re.I)
# Reject only when first person is the SUBJECT ("And I have said,", "I the LORD have said,")
# — that is God speaking. An addressee "me" ("Then the LORD said unto me,") is the prophet
# narrating, and the lead-in is still narrative that must be trimmed.
FIRST = re.compile(r'\b(?:I|my|mine)\b')
# The lead-in must actually pair a divine speaker with a speech verb. Without this,
# circumstantial clauses that sit INSIDE a speech get trimmed and correct gold is lost:
#   Jeremiah 3:9    "And it came to pass through the lightness of her whoredom,"
#   2 Chronicles 7:22 "And it shall be answered,"
#   Isaiah 30:16    "But ye said,"      (God quoting the people)
#   Ezekiel 23:17   "And the Babylonians came to her into the bed of love,"
# Those verses are continuation verses whose span legitimately starts at 0.
SPEAKER = r'(?:the LORD God|the LORD of hosts|the LORD|the word of the LORD|God|he)'
SPEAKER_OK = re.compile(SPEAKER + r'\s+(?:said|saith|spake|answered|called|appeared|came|commanded)\b')

d = json.load(io.open(ROOT + "/study/divine_speech.json", encoding="utf-8"))
spans = d["spans"]

applied, rejected = [], []
for b in sorted(spans, key=int):
    for c in sorted(spans[b], key=int):
        for v in sorted(spans[b][c], key=int):
            t = vt(int(b), int(c), int(v))
            for si, se in enumerate(spans[b][c][v]):
                s, e = se
                if s != 0:
                    continue
                gold = t[s:e]
                m = LEAD.match(gold)
                if not m:
                    continue
                lead = gold[:m.end()]
                ref = "%s %s:%s" % (NAME[int(b)], c, v)
                why = None
                if SUBORD.match(lead):            why = "subordinate clause"
                elif RELATIVE.search(lead):       why = "relative clause"
                elif FIRST.search(lead):          why = "first person (God speaking)"
                elif not SPEAKER_OK.search(lead): why = "no third-person subject"
                elif len(lead) > 120:             why = "lead too long"
                elif (e - m.end()) < 15:          why = "remainder too short"
                if why:
                    rejected.append((ref, why, lead))
                    continue
                applied.append({"ref": ref, "b": b, "c": c, "v": v, "i": si,
                                "old": s, "new": m.end(), "lead": lead,
                                "head": gold[m.end():m.end()+60]})

print("candidates trimmed : %d" % len(applied))
print("candidates rejected: %d" % len(rejected))
print("\n--- REJECTED (left untouched) ---")
for ref, why, lead in rejected:
    print("  [%-28s] %s\n      %s" % (why, ref, lead[:96]))
print("\n--- TRIMMED ---")
for a in applied:
    print("  %s" % a["ref"])
    print("      drop: %s" % a["lead"][:100])
    print("      keep: %s..." % a["head"])

if "--apply" in sys.argv:
    for a in applied:
        spans[a["b"]][a["c"]][a["v"]][a["i"]][0] = a["new"]
    with io.open(ROOT + "/study/divine_speech.json", "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, separators=(',', ':'))
    print("\nAPPLIED %d trims; file rewritten (%d bytes)"
          % (len(applied), os.path.getsize(ROOT + "/study/divine_speech.json")))
else:
    print("\n(dry run — pass --apply to write)")
