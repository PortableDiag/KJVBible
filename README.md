# KJV Bible

A fast, **ad-free** King James Version Bible app for Android. Built to open instantly
and let you find, read, bookmark, and share Scripture without ads, tracking, or
network access — the entire KJV text is bundled in the app.

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
  meaning and contested/altered terms; tap a highlight for a neutral, sourced explanation
  (underlying Hebrew/Greek, history, manuscript notes). Notes live in
  `app/src/main/assets/study/notes.json` and are easy to extend by hand.

No ads. No tracking. No account. No network permission.

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
