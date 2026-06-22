import csv, json, re, os

ASSETS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "bible")
RS, RE = chr(0xE000), chr(0xE001)
def plain(s): return ''.join(c for c in s if c not in (RS, RE))

USFM = "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL".split()
CODE2IDX = {c: i+1 for i, c in enumerate(USFM)}

idx = json.load(open(os.path.join(ASSETS, "index.json")))
VC = {b["i"]: b["vc"] for b in idx["books"]}
_books = {}
def verses(b):
    if b not in _books: _books[b] = json.load(open(os.path.join(ASSETS, f"{b}.json")))
    return _books[b]
def verse_text(b, c, v):
    d = verses(b)
    if c < 1 or c > len(d) or v < 1 or v > len(d[c-1]): return None
    return plain(d[c-1][v-1])

INTRO = re.compile(r'\b(?:said|saith|saying|spake|speaketh|answered|commanded|calleth|called|cried|charged|sware|testified|spoken)\b[^,:;]{0,55}?[,:;]\s+')

def quote_start(t):
    m = INTRO.search(t)
    if not m or m.start() > 30:
        return 0
    start = m.end()
    while True:
        m2 = INTRO.search(t, start)
        if m2 and (m2.start() - start) <= 6:
            start = m2.end()
        else:
            break
    return start

# High-precision trailing-narrative patterns (applied to the LAST verse of a speech only).
TAIL = [
    re.compile(r'[:;.]\s+and it was so\b.*$'),
    re.compile(r'[:;.]\s+and there was (?:light|evening|morning|day|night)\b.*$'),
    re.compile(r'[:;.]\s+and the evening and the morning\b.*$'),
    re.compile(r'[:;.]\s+and God (?:saw|called|made|blessed|created|divided|set|ended|finished)\b.*$'),
    re.compile(r'[:;.]\s+so (?:he|they) (?:was|were)\b.*$'),
    re.compile(r'[:;.]\s+and (?:he|they|[A-Z][a-z]+) did (?:so|according|even as)\b.*$'),
]
def tail_trim(t, start):
    end = len(t)
    for rx in TAIL:
        m = rx.search(t, start)
        if m and m.start() + 1 < end:
            end = m.start() + 1
    return end

def ref(r):
    b, cv = r.split(); c, v = cv.split(':'); return b, int(c), int(v)

def expand(b, c1, v1, c2, v2):
    out = []; vc = VC[CODE2IDX[b]]; c, v = c1, v1
    while (c < c2) or (c == c2 and v <= v2):
        out.append((c, v))
        if v < vc[c-1]: v += 1
        else: c += 1; v = 1
        if c > len(vc): break
    return out

rows = list(csv.DictReader(open(os.environ.get('SPEAKER_TSV', 'Clear-Aligned-Projections.tsv')), delimiter='\t'))
spans = {}
def add_span(b, c, v, s, e):
    if s >= e: return
    spans.setdefault(b, {}).setdefault(c, {}).setdefault(v, []).append([s, e])

count = 0
for r in rows:
    if r['SPEAKER (FCBH)'] != 'God': continue
    if r['QUOTE TYPE'] not in ('Normal', 'Implicit', 'Quotation', 'Dialogue'): continue
    sb, sc, sv = ref(r['START VS']); eb, ec, ev = ref(r['END VS'])
    if sb not in CODE2IDX or eb != sb: continue
    bidx = CODE2IDX[sb]; count += 1
    vs = expand(sb, sc, sv, ec, ev)
    for i, (c, v) in enumerate(vs):
        t = verse_text(bidx, c, v)
        if not t: continue
        start = quote_start(t) if i == 0 else 0
        end = tail_trim(t, start) if i == len(vs) - 1 else len(t)
        add_span(bidx, c, v, start, end)

for b in spans:
    for c in spans[b]:
        for v in spans[b][c]:
            segs = sorted(spans[b][c][v]); merged = [segs[0]]
            for s, e in segs[1:]:
                if s <= merged[-1][1]: merged[-1][1] = max(merged[-1][1], e)
                else: merged.append([s, e])
            spans[b][c][v] = merged

out = {"version": 1,
       "attribution": "Speaker data: MACULA Quotation and Speaker Data, (c) 2023 Clear Bible, Inc, CC BY 4.0, https://github.com/Clear-Bible/speaker-quotations",
       "spans": spans}
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "study", "divine_speech.json")
json.dump(out, open(OUT, "w"), separators=(',', ':'))
nv = sum(len(spans[b][c]) for b in spans for c in spans[b])
print("ranges:", count, "| gold verses:", nv, "| size:", os.path.getsize(OUT))
