package com.portablediag.kjvbible;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Study annotations loaded from assets/study/notes.json.
 *
 * Two kinds of anchors:
 *  - "words"    : a whole-word match that applies everywhere (e.g. archaic words).
 *  - "passages" : a phrase highlighted only in a specific verse (e.g. contested terms).
 *
 * Notes are intended to be neutral and evidentiary, with cited sources.
 */
public final class StudyNotes {

    public static final class Note {
        public final String id;
        public final String category;   // "archaic" | "contested"
        public final String title;
        public final String body;
        public final List<String> sources;

        Note(String id, String category, String title, String body, List<String> sources) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.body = body;
            this.sources = sources;
        }
    }

    /** A highlight range within a verse's plain-text body. */
    public static final class Span {
        public final int start;
        public final int end;
        public final String noteId;

        Span(int start, int end, String noteId) {
            this.start = start;
            this.end = end;
            this.noteId = noteId;
        }
    }

    private static final class Passage {
        final String phrase;
        final String noteId;
        Passage(String phrase, String noteId) { this.phrase = phrase; this.noteId = noteId; }
    }

    private static StudyNotes instance;

    private final Map<String, Note> byId = new HashMap<>();
    private final Map<String, String> wordToNote = new HashMap<>();        // lowercased word -> note id
    private final Map<Long, List<Passage>> passagesByRef = new HashMap<>();

    private StudyNotes(Context ctx) {
        load(ctx.getApplicationContext());
    }

    public static synchronized StudyNotes get(Context ctx) {
        if (instance == null) instance = new StudyNotes(ctx);
        return instance;
    }

    private void load(Context ctx) {
        try {
            JSONObject root = new JSONObject(readAsset(ctx, "study/notes.json"));

            JSONArray words = root.optJSONArray("words");
            if (words != null) {
                for (int i = 0; i < words.length(); i++) {
                    JSONObject o = words.getJSONObject(i);
                    Note note = parseNote(o);
                    byId.put(note.id, note);
                    JSONArray match = o.getJSONArray("match");
                    for (int j = 0; j < match.length(); j++) {
                        wordToNote.put(match.getString(j).toLowerCase(Locale.US), note.id);
                    }
                }
            }

            JSONArray passages = root.optJSONArray("passages");
            if (passages != null) {
                for (int i = 0; i < passages.length(); i++) {
                    JSONObject o = passages.getJSONObject(i);
                    Note note = parseNote(o);
                    byId.put(note.id, note);
                    long key = refKey(o.getInt("book"), o.getInt("chapter"), o.getInt("verse"));
                    List<Passage> list = passagesByRef.get(key);
                    if (list == null) { list = new ArrayList<>(); passagesByRef.put(key, list); }
                    list.add(new Passage(o.getString("phrase"), note.id));
                }
            }
        } catch (Exception e) {
            // Study notes are optional; failure simply means no annotations.
        }
    }

    private Note parseNote(JSONObject o) throws org.json.JSONException {
        List<String> sources = new ArrayList<>();
        JSONArray src = o.optJSONArray("sources");
        if (src != null) for (int i = 0; i < src.length(); i++) sources.add(src.getString(i));
        return new Note(o.getString("id"), o.optString("category", ""),
                o.optString("title", ""), o.optString("body", ""), sources);
    }

    public Note byId(String id) {
        return byId.get(id);
    }

    private static long refKey(int book, int chapter, int verse) {
        return ((long) book << 24) | ((long) chapter << 12) | verse;
    }

    /**
     * Find annotation spans within one verse's plain text. Returns non-overlapping spans
     * ordered by start index. Returns an empty list if there is nothing to annotate.
     */
    public List<Span> find(int book, int chapter, int verse, String plain) {
        List<Span> spans = new ArrayList<>();

        // Verse-specific phrases first (they take precedence on overlap).
        List<Passage> passages = passagesByRef.get(refKey(book, chapter, verse));
        if (passages != null) {
            String lower = plain.toLowerCase(Locale.US);
            for (Passage p : passages) {
                int idx = lower.indexOf(p.phrase.toLowerCase(Locale.US));
                if (idx >= 0) spans.add(new Span(idx, idx + p.phrase.length(), p.noteId));
            }
        }

        // Whole-word matches.
        if (!wordToNote.isEmpty()) {
            int n = plain.length(), i = 0;
            while (i < n) {
                char c = plain.charAt(i);
                if (isWordChar(c)) {
                    int j = i + 1;
                    while (j < n && isWordChar(plain.charAt(j))) j++;
                    String token = plain.substring(i, j).toLowerCase(Locale.US);
                    String noteId = wordToNote.get(token);
                    if (noteId != null) spans.add(new Span(i, j, noteId));
                    i = j;
                } else {
                    i++;
                }
            }
        }

        if (spans.size() <= 1) return spans;

        // Sort by start, drop overlaps (keep the earlier-added / earlier-starting span).
        java.util.Collections.sort(spans, (a, b) ->
                a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end - b.start, a.end - a.start));
        List<Span> out = new ArrayList<>(spans.size());
        int lastEnd = -1;
        for (Span s : spans) {
            if (s.start >= lastEnd) {
                out.add(s);
                lastEnd = s.end;
            }
        }
        return out;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '\'';
    }

    private String readAsset(Context ctx, String path) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = ctx.getAssets().open(path);
             BufferedReader r = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
