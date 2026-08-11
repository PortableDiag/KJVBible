# Data tools

These scripts regenerate the bundled assets from their upstream sources, so the derived
data is reproducible and auditable. They are not part of the app build.

## `convert_bible.py` — bundled KJV text

Generates `app/src/main/assets/bible/{1..66}.json` and `index.json` from the
[jburson/bible-data](https://github.com/jburson/bible-data) `data/kjv/kjv.json` file.

- Red-letter (words of Christ) runs are wrapped in the private-use control characters
  U+E000 / U+E001.
- The small-caps divine name (`Lord*s`) is upper-cased to `LORD`.
- The upstream per-word marker is `*` followed by a run of flag letters (e.g. `*rp`).

```bash
# download kjv.json from jburson/bible-data into this folder first
KJV_SRC=kjv.json python3 convert_bible.py
```

## `gen_divine_speech.py` — Old Testament "words of God" gold spans

Generates `app/src/main/assets/study/divine_speech.json` (per-verse character spans rendered
in gold) from the **MACULA Quotation and Speaker Data**
([Clear-Bible/speaker-quotations](https://github.com/Clear-Bible/speaker-quotations),
© 2023 Clear Bible, Inc, CC BY 4.0), file `tsv/Clear-Aligned-Projections.tsv`.

- Keeps rows where `SPEAKER (FCBH) == God` and `QUOTE TYPE` is
  `Normal | Implicit | Quotation | Dialogue` (Old Testament, same-book ranges).
- Trims the narrative introduction off the opening verse of each speech, and applies a
  conservative tail-trim of trailing narrative on the final verse.
- Speech boundaries are approximate; the output JSON is hand-editable for corrections.

```bash
# download Clear-Aligned-Projections.tsv from Clear-Bible/speaker-quotations into this folder
SPEAKER_TSV=Clear-Aligned-Projections.tsv python3 gen_divine_speech.py
```

## `fix_gold_intros.py` — trim narrative introductions from the gold spans

**Run this after `gen_divine_speech.py`, every time.** The generator gives up on the opening
verse of a speech when the introducing verb falls late in the verse, and leaves the whole
verse gold — so text like *"And the LORD said unto Moses,"* is coloured as though God spoke
it. This pass removes the lead-in and starts the gold at the speech itself.

It is deliberately conservative, and refuses any lead-in that is a subordinate or relative
clause, is spoken in the first person, or does not pair a divine speaker with a speech verb —
because a circumstantial clause *inside* a speech (Jeremiah 3:9, Ezekiel 16:23, 2 Chronicles
7:22) legitimately begins at offset 0 and must not be trimmed. Re-running is safe: it is
idempotent, and it never moves the end of a span.

```bash
python3 fix_gold_intros.py            # dry run — prints what it would trim and what it rejects
python3 fix_gold_intros.py --apply    # write
```

## Study notes

`app/src/main/assets/study/notes.json` (archaic / contested-term annotations) is hand-curated
with cited sources — there is no generator for it.

`check_notes.py` validates it against the bundled text and the editorial rules: ids unique,
every note has a title, a body and at least one source, every passage phrase actually occurs
in its verse, no hedging language, no Wikipedia sources, and every note has at least one
anchor that really matches the text. It mirrors `StudyNotes.find()`, including the
case-sensitive matching used for the small-capital divine name.

```bash
python3 check_notes.py                # exits non-zero on failure; warns on dead synonyms
```
