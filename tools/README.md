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

## Study notes

`app/src/main/assets/study/notes.json` (archaic / contested-term annotations) is hand-curated
with cited sources — there is no generator for it.
