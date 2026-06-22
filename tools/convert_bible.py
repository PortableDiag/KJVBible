import json, re, os

SRC = os.environ.get("KJV_SRC", "kjv.json")  # jburson/bible-data data/kjv/kjv.json
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "bible")
os.makedirs(OUT, exist_ok=True)

RED_START = ""
RED_END   = ""

# Standard book order + 3-letter abbreviations (66)
ABBR = {
 "Genesis":"Gen","Exodus":"Exo","Leviticus":"Lev","Numbers":"Num","Deuteronomy":"Deu",
 "Joshua":"Jos","Judges":"Jdg","Ruth":"Rut","1 Samuel":"1Sa","2 Samuel":"2Sa",
 "1 Kings":"1Ki","2 Kings":"2Ki","1 Chronicles":"1Ch","2 Chronicles":"2Ch","Ezra":"Ezr",
 "Nehemiah":"Neh","Esther":"Est","Job":"Job","Psalm":"Psa","Proverbs":"Pro",
 "Ecclesiastes":"Ecc","Song of Solomon":"Sng","Isaiah":"Isa","Jeremiah":"Jer","Lamentations":"Lam",
 "Ezekiel":"Eze","Daniel":"Dan","Hosea":"Hos","Joel":"Joe","Amos":"Amo","Obadiah":"Oba",
 "Jonah":"Jon","Micah":"Mic","Nahum":"Nah","Habakkuk":"Hab","Zephaniah":"Zep","Haggai":"Hag",
 "Zechariah":"Zec","Malachi":"Mal","Matthew":"Mat","Mark":"Mrk","Luke":"Luk","John":"Jhn",
 "Acts":"Act","Romans":"Rom","1 Corinthians":"1Co","2 Corinthians":"2Co","Galatians":"Gal",
 "Ephesians":"Eph","Philippians":"Php","Colossians":"Col","1 Thessalonians":"1Th",
 "2 Thessalonians":"2Th","1 Timothy":"1Ti","2 Timothy":"2Ti","Titus":"Tit","Philemon":"Phm",
 "Hebrews":"Heb","James":"Jas","1 Peter":"1Pe","2 Peter":"2Pe","1 John":"1Jn","2 John":"2Jn",
 "3 John":"3Jn","Jude":"Jud","Revelation":"Rev"
}

marker_re = re.compile(r'\*([a-zA-Z]+)')

def clean_verse(t):
    words = t.split(' ')
    out = []
    in_red = False
    for i, w in enumerate(words):
        flags = set()
        for g in marker_re.findall(w):
            flags.update(g)
        cw = marker_re.sub('', w)
        red = 'r' in flags
        sc  = 's' in flags
        if sc:
            cw = cw.upper()
        if i > 0:
            out.append(' ')
        if red and not in_red:
            out.append(RED_START); in_red = True
        elif (not red) and in_red:
            out.append(RED_END); in_red = False
        out.append(cw)
    if in_red:
        out.append(RED_END)
    return ''.join(out)

data = json.load(open(SRC))

books = []        # ordered list of (name)
book_data = {}    # name -> list of chapters; chapter -> list of verses

for v in data:
    parts = v['r'].split(':')   # kjv:Book:chap:verse
    name = parts[1]; chap = int(parts[2]); verse = int(parts[3])
    if verse == 0:
        continue   # chapter heading entry
    if name not in book_data:
        book_data[name] = {}
        books.append(name)
    ch = book_data[name].setdefault(chap, {})
    ch[verse] = clean_verse(v['t'])

index = {"books": []}
for bi, name in enumerate(books):
    chapters = book_data[name]
    nch = max(chapters.keys())
    arr = []
    for c in range(1, nch+1):
        vmap = chapters.get(c, {})
        nv = max(vmap.keys()) if vmap else 0
        arr.append([vmap.get(vn, "") for vn in range(1, nv+1)])
    fn = os.path.join(OUT, f"{bi+1}.json")
    with open(fn, "w", encoding="utf-8") as f:
        json.dump(arr, f, ensure_ascii=False, separators=(',',':'))
    index["books"].append({
        "i": bi+1,
        "n": name,
        "a": ABBR.get(name, name[:3]),
        "c": nch,
        "vc": [len(ch) for ch in arr],   # verse count per chapter
    })

with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
    json.dump(index, f, ensure_ascii=False, separators=(',',':'))

# stats
total_verses = sum(len(ch) for n in books for ch in [book_data[n][c] for c in book_data[n]])
print("books:", len(books))
print("total chapters:", sum(b['c'] for b in index['books']))
print("total verses:", total_verses)
sz = sum(os.path.getsize(os.path.join(OUT,f)) for f in os.listdir(OUT))
print("assets size bytes:", sz)
# sanity: John 3:16 and a red verse
j = json.load(open(os.path.join(OUT,"43.json")))   # John is book 43
print("John 3:16:", repr(j[2][15]))
print("John 3:3 (red):", repr(j[2][2]))
