# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.6.1] — 2026-06-21

### Changed
- Reworded the study notes for Revelation 2:9, 3:9, and John 8:44 to give the underlying
  word and the range of interpretations without asserting the scope of the passage — keeping
  the notes strictly neutral.

## [1.6.0] — 2026-06-21

### Added
- **Study notes greatly expanded** (now 100+ entries):
  - Named beings — angels (Gabriel, Michael, archangel), cherubim, seraphim; Satan, the
    devil and demons (with the *diabolos*/*daimonion* distinction), Beelzebub, Belial,
    Abaddon/Apollyon, the dragon; leviathan, behemoth, giants; the angel of the LORD, host
    of heaven, principalities & powers, sons of God, the watchers, the living creatures,
    Gog and Magog, the scapegoat (Azazel), and the morning star.
  - ~50 human figures — patriarchs, prophets, kings, and apostles — with disambiguation
    notes for shared names (Mary, John, James, Judas, Simon, Herod, Joseph, Saul, Philip,
    Lazarus).
  - The word "Jew": etymology (Judah/Judea), the Judean/Judahite translation debate, the
    Idumea/Hyrcanus background, and the outward/inward distinction (Romans 2:28-29), plus
    verse notes for Revelation 2:9, 3:9, and John 8:44.
- **Multi-word phrase matching** for study notes (e.g. "angel of the LORD").
- **Clickable sources** — Strong's references link to their BibleHub lexicon page.

## [1.5.0] — 2026-06-21

### Added
- **Share as image.** Tapping Share on a selection now offers "Share as text" (existing) or
  "Share as image", which renders the verse(s) to a square image and shares it with a text
  caption. The image respects the current light/dark theme and keeps the red-letter words of
  Christ and gold OT words of God, with inline verse numbers and the full reference
  (Book Chapter:Verse). A preview sheet confirms before sharing. Long passages shrink to fit.

## [1.4.0] — 2026-06-21

### Changed
- Changing the theme (or any recreate) now keeps your scroll position in the chapter instead
  of jumping to the top.

### Added
- **God's words in gold (Old Testament).** Direct divine speech in the OT is shown in gold,
  to distinguish what God says from narrative and others' responses — always on, a companion
  to the red-letter words of Christ in the NT.
  - Speaker/verse-range data derived from the MACULA Quotation and Speaker Data
    (© 2023 Clear Bible, Inc, CC BY 4.0), including continuation/implicit speech (covenants,
    the Torah's instructions, etc.), with the narrative introduction trimmed off the opening
    verse, plus a high-precision tail-trim that stops the gold before trailing narrative on a
    speech's final verse (e.g. "Let there be light:" — not "and there was light").
- **About** entry in the overflow menu (text licensing and data attribution).

## [1.3.0] — 2026-06-21

### Added
- **Import / Export bookmarks** as JSON from the Bookmarks screen menu, via the system
  file picker. Export writes a readable, re-importable file (`kjv-bookmarks.json` by
  default); import merges and skips duplicates and out-of-range entries. Accepts the
  exported wrapper format or a bare array, with long or short keys.

## [1.2.0] — 2026-06-21

### Changed
- Verse selection now requires a deliberate **press-and-hold (~0.5s)** instead of a tap, so
  scrolling never selects by accident (with a haptic tick when it triggers). Once a
  selection is active, a quick tap toggles additional verses.

### Added
- **Study mode** (overflow menu, off by default). When enabled, certain words and phrases
  are subtly highlighted; tapping one opens a bottom sheet with a neutral, sourced
  explanation. Verse selection is disabled while study mode is on, to keep the two
  interactions separate.
- Initial study-notes dataset (`assets/study/notes.json`), hand-curated and citation-backed:
  - Archaic words that have shifted meaning (e.g. *prevent* = precede, *conversation* =
    conduct, *suffer* = allow, *let* = hinder, *wot/wist*, *quick*, *meat*, *holpen*).
  - Contested or transliterated terms with their underlying Hebrew/Greek and history
    (e.g. *Easter* / Passover, *Lucifer*, *unicorn*, *hell*, *charity*, *baptize*, *church*,
    *Godhead*), and notable textual-variant passages (Comma Johanneum at 1 John 5:7,
    Mark 16:9–20, John 7:53–8:11).
  - Notes are plain JSON, easy to extend by hand; anchors are either a global whole-word
    match or a phrase within a specific verse.

## [1.1.0] — 2026-06-21

### Added
- Bookmark a whole chapter from the reader's toolbar bookmark icon (toggles; shows filled
  when the current chapter is bookmarked). Verse-level bookmarking via selection still works.
- Bookmarks screen sort toggle: **Bible order** (default, with Old/New Testament separators)
  or **order saved**; the choice is remembered.
- Bookmarks are reachable from the reader's overflow menu (Theme → Bookmarks → Share chapter).
- Verse-level selection in the passage picker (**Book → Chapter → Verse**).
- **All / Old Testament / New Testament** tabs in book selection, with
  "Old Testament" and "New Testament" section dividers in the All list.
- Immersive reading mode that hides the system navigation bar (swipe from the bottom edge
  to reveal it).
- Filled red bookmark marker shown in the reader for bookmarked verses.

### Changed
- The bookmark action in the selection bar now **toggles** (add / remove) and reflects the
  current state with a filled or outlined icon.
- Edge-to-edge layout: the floating chapter buttons and the verse list respect the system
  bar insets, so they no longer overlap the navigation bar.
- Clearer multi-verse selection: a live "N selected" count, and a verse you navigate to
  from search or bookmarks no longer lingers looking selected — its highlight clears the
  moment you tap.

### Fixed
- Removing a bookmark from the reader now works correctly.

## [1.0.0] — 2026-06-21

### Added
- Initial release. The complete King James Version, bundled for fully offline use.
- Red-letter words of Christ; small-caps divine name (**LORD**).
- Light and dark themes (follow system, or force Light / Dark).
- Bookmarks with add, navigate, remove, and clear-all.
- Full-text search across the whole Bible with highlighted matches.
- Share a verse, multiple verses, or a chapter as plain text; copy to clipboard.
- Adjustable font size with a sensible default.
- Two-tap book/chapter navigation; Previous / Next across chapters and books.
- Black cross launcher icon (adaptive, with monochrome/themed-icon support).
- Ad-free, tracking-free, no network permission.
