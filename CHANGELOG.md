# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
