# KJV Bible

A fast, **ad-free** King James Version Bible app for Android. Built to open instantly
and let you find, read, bookmark, and share Scripture without ads, tracking, or
network access — the entire KJV text is bundled in the app. An optional **local API**
(off by default) lets an agent or script on the phone do the same.

## Features

- **Red-letter text** — the words of the Lord Jesus Christ are shown in red; the rest of
  the text is black in light mode and white in dark mode. The divine name is rendered in
  small caps (**LORD**).
- **God's words in gold (Old Testament)** — direct divine speech in the OT is shown in gold,
  distinguishing what God says from narrative and others' responses (always on, like the
  red-letter words of Christ). End-boundaries are approximate; see attribution below.
- **Light & dark modes** — follows the system theme by default, or force Light / Dark.
- **Distraction-free reading** — the reader runs in immersive mode and hides the system
  navigation bar; swipe up from the bottom edge to reveal it.
- **Fast navigation** — tap the title to pick a passage: **Book → Chapter → Verse**, with
  **All / Old Testament / New Testament** tabs and section dividers. Floating Previous /
  Next buttons move between chapters and across books.
- **Bookmarks** — select one or more verses and bookmark them; bookmarked verses show a
  red bookmark marker in the reader. Manage and navigate bookmarks from one screen, remove
  individually, or clear all. The bookmark action toggles (add / remove). Bookmarks can be
  **exported and imported as JSON** from the Bookmarks menu (system file picker).
- **Sharing** — share a single verse (as a tidy quote), multiple selected verses, or a
  whole chapter, as clean plain text. You can also copy to the clipboard, or **share as an
  image** — a themed card with the verse(s), inline verse numbers, the full reference, and
  the red/gold colors preserved.
- **Full-text search** — search the entire Bible quickly, with matches highlighted.
- **Adjustable font size** — a comfortable default with a simple slider (80–200%).
- **Study mode** (off by default) — subtly highlights archaic words that have shifted
  meaning, contested/altered terms, named beings (angels, demons, entities), key people,
  the first-century sects, and terms worth a definition (Gentile, Zion, circumcision);
  tap a highlight for a neutral, sourced explanation (underlying Hebrew/Greek, history,
  manuscript notes) with **clickable sources** (Strong's → BibleHub, and scripture
  references that open the passage in the reader). Notes live in
  `app/src/main/assets/study/notes.json` and are easy to extend by hand.

  **The divine name** is covered where it appears: tapping **LORD** gives the Tetragrammaton
  beneath it, the Masoretic pointing that directed the reader to say *Adonai*, and how that
  substitution passed into Greek, Latin and English; **GOD** in small capitals, **JEHOVAH**
  and the compound names each have their own note. Because this text upper-cases the
  small-capital forms, matching can be made case-sensitive (`"caseSensitive": true`), which is
  what lets **LORD** (the Name) and **Lord** (*Adonai*) carry different notes.

  Where **Rotherham's Emphasised Bible** (1902) renders a word differently from the King
  James Version, the note says so — *Yahweh* for LORD, *assembly* for church, *immerse* for
  baptize, *age-abiding* for everlasting, and the untranslated *hades* / *Gehenna* for "hell".

  Notes are written in a plain, definition-style voice — the word, its etymology, the
  history, and the sources stated directly, without hedging and without interpretive
  verdicts in either direction. Every claim is linked so it can be checked.

- **Local API** (off by default) — an agent or script on the phone can read, search, get a
  passage as text or as the share image, read study notes, manage bookmarks and open the
  reader, over HTTP on `127.0.0.1`. See [The local API](#the-local-api).

No ads. No tracking. No account. The app never connects anywhere. Since 1.8 the manifest
holds `INTERNET`, because Android requires it to open the local API's listening socket, even
one bound to `127.0.0.1`. That socket stays closed until you switch the API on.

## The local API

Turn it on under **⋮ › Local API**. It has the same shape as Lull, TextX, DialX and NotifX, so
PhoneAPI can register it as a provider:

- **Off by default.** While it is on, a quiet notification says so and has **Stop**.
- **Bound to `127.0.0.1`** on port **8794**. **LAN access** is a separate switch, off by
  default. From a computer, use `adb forward tcp:8794 tcp:8794`.
- **A key per caller.** Each key is shown once and stored only as a SHA-256 hash. **Every tool
  is off for a new key** until you switch it on. A switch takes effect on the next call.
  Revoking a key stops it at once.
- **Audit log.** Every call, allowed or refused, is listed under *Recent calls*.

```bash
K=kjv_...   # from New key
curl -H "X-API-Key: $K" "http://127.0.0.1:8794/api/read?passage=John%203:16"
curl -H "X-API-Key: $K" -X POST http://127.0.0.1:8794/api/tools/kjv.share_text -d '{"passage":"Psalm 23"}'
curl -H "X-API-Key: $K" "http://127.0.0.1:8794/api/image?passage=John%2014:6&theme=dark" -o verse.png
```

| Tool | What it does |
|---|---|
| `kjv.books` | The 66 books: number, name, abbreviation, chapters, testament |
| `kjv.read` | A chapter or verses: plain text, red-letter and gold spans as `[start,end)` offsets |
| `kjv.search` | Full-text search, the same match as the app; `testament`, `book`, `limit` |
| `kjv.share_text` | The passage exactly as the app shares it as text |
| `kjv.share_image` | The 1080×1080 share image (PNG, base64) plus the caption the app sends with it; `theme` app / light / dark |
| `kjv.notes` | Study notes anchored in a passage, or one note by `id`, with sources |
| `kjv.bookmarks` / `kjv.bookmark_add` / `kjv.bookmark_remove` | Bookmarks; a chapter passage is a whole-chapter bookmark |
| `kjv.position` | Where the reader is open |
| `kjv.open` | Open the reader at a passage. Android only lets the app do this while it is on screen; otherwise it posts a notification for the user to tap, and the result says which happened |

A passage is one chapter or verses in it: `John 3:16`, `John 3:16-18`, `Jhn 3:16, 18`,
`Psalm 23`. Routes: `GET /api/health` (no key), `/api/me`, `/api/tools`, `/api/docs`,
`POST /api/tools/{name}`, `GET /api/read|search|image`, and `POST /mcp` (MCP over JSON-RPC:
`initialize`, `tools/list`, `tools/call`, `ping`). An image comes back from MCP as an `image`
content block. A switched-off tool answers `403` with what this key may use.

## Build

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Toolchain: Gradle 8.11.1, Android Gradle Plugin 8.7.3, JDK 17, `compileSdk` 35,
`minSdk` 26, `targetSdk` 35. Java + Android Views (Material Components + RecyclerView).

## Project layout

```
app/src/main/
├── java/com/portablediag/kjvbible/
│   ├── MainActivity.java       Reader: chapter view, selection, sharing, settings, immersive mode
│   ├── PickerActivity.java     Book (All/OT/NT) → Chapter → Verse picker
│   ├── SearchActivity.java     Background full-text search
│   ├── BookmarksActivity.java  Bookmark list / management
│   ├── Bible.java              Loads bundled JSON assets (lazy, per-book LRU cache)
│   ├── VerseAdapter.java       Verse rendering + tap-to-select
│   ├── VerseFormatter.java     Verse numbers, red-letter spans, bookmark glyph
│   ├── ShareUtil.java          Plain-text passage building + share/copy intents
│   ├── Bookmarks.java          Persistent bookmark store (SharedPreferences JSON)
│   ├── Prefs.java / ThemeUtil.java / Ref.java / KjvApp.java
│   └── api/                    The local API, off unless switched on
│       ├── LocalApi.java       Settings, routes, auth, MCP, docs
│       ├── Tools.java          Every tool, checked against the caller's switches
│       ├── ApiKeys.java        A hashed key per caller, its tool switches, the audit log
│       ├── ApiServer.java / Http.java   Loopback HTTP/1.1, one request per connection
│       ├── ApiService.java     Foreground service while on; BootReceiver.java restores it
│       ├── ApiActivity.java    The Local API screen
│       └── Opener.java         kjv.open: move the reader, or notify when not on screen
└── assets/bible/
    ├── index.json              Book names, abbreviations, chapter/verse counts
    └── 1.json … 66.json        One file per book: chapters → verses
```

## Bible text & data

King James Version (public domain). The text — including red-letter (words of Christ) and
small-caps (divine name) markup — is pre-processed into compact per-book JSON assets under
`app/src/main/assets/bible/`. Within each verse, words of Christ are wrapped in private-use
control characters and rendered red at runtime.

Source markup derived from the
[jburson/bible-data](https://github.com/jburson/bible-data) dataset
(31,102 verses, 1,189 chapters, 66 books).

Old Testament "words of God" gold highlighting is derived from the verse ranges in the
**MACULA Quotation and Speaker Data**, © 2023 Clear Bible, Inc, licensed
[CC BY 4.0](http://creativecommons.org/licenses/by/4.0/)
([Clear-Bible/speaker-quotations](https://github.com/Clear-Bible/speaker-quotations)),
with the narrative introductions trimmed off the opening verse of each speech. Speech
boundaries are approximate.

## Licensing

- **Application code** — MIT License (see [LICENSE](LICENSE)).
- **King James Version text** — public domain.
- **Red-letter (words of Christ) and small-caps (divine name) designations** — factual,
  convention-driven designations about the public-domain KJV text (which words Christ spoke;
  where the divine name appears). They were sourced from
  [jburson/bible-data](https://github.com/jburson/bible-data) and re-encoded into this
  project's own format. That repository carries no license; we redistribute none of its
  files, only the underlying public-domain text together with these standard editorial
  designations. Attribution is given as a courtesy.
- **Old Testament "words of God" gold spans** — derived from the MACULA Quotation and Speaker
  Data, © 2023 Clear Bible, Inc, licensed
  [CC BY 4.0](http://creativecommons.org/licenses/by/4.0/)
  ([Clear-Bible/speaker-quotations](https://github.com/Clear-Bible/speaker-quotations)).
- **Study notes** — hand-curated with cited sources.

The scripts that regenerate the derived assets live in [`tools/`](tools/).
