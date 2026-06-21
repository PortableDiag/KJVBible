package com.portablediag.kjvbible;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent bookmark store, backed by a JSON array in SharedPreferences. */
public final class Bookmarks {

    public static final class Entry {
        public final Ref ref;
        public final long addedAt;
        public final String snippet;

        Entry(Ref ref, long addedAt, String snippet) {
            this.ref = ref;
            this.addedAt = addedAt;
            this.snippet = snippet;
        }
    }

    private final Prefs prefs;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<Long> keys = new HashSet<>();

    public Bookmarks(Context ctx) {
        this.prefs = new Prefs(ctx);
        load();
    }

    private void load() {
        entries.clear();
        keys.clear();
        try {
            JSONArray arr = new JSONArray(prefs.bookmarksJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Ref r = new Ref(o.getInt("b"), o.getInt("c"), o.getInt("v"));
                Entry e = new Entry(r, o.optLong("t", 0), o.optString("s", ""));
                entries.add(e);
                keys.add(r.key());
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("b", e.ref.book);
                o.put("c", e.ref.chapter);
                o.put("v", e.ref.verse);
                o.put("t", e.addedAt);
                o.put("s", e.snippet);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.setBookmarksJson(arr.toString());
    }

    public boolean contains(Ref r) {
        return keys.contains(r.key());
    }

    /** Adds a bookmark; returns true if added, false if it already existed. */
    public boolean add(Ref r, long when, String snippet) {
        if (keys.contains(r.key())) return false;
        entries.add(new Entry(r, when, snippet));
        keys.add(r.key());
        persist();
        return true;
    }

    public void remove(Ref r) {
        if (!keys.contains(r.key())) return;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).ref.equals(r)) {
                entries.remove(i);
                break;
            }
        }
        keys.remove(r.key());
        persist();
    }

    /** Toggle; returns true if now bookmarked. */
    public boolean toggle(Ref r, long when, String snippet) {
        if (contains(r)) {
            remove(r);
            return false;
        }
        add(r, when, snippet);
        return true;
    }

    public void clear() {
        entries.clear();
        keys.clear();
        persist();
    }

    /** Bookmarks ordered by canonical scripture position. */
    public List<Entry> all() {
        List<Entry> copy = new ArrayList<>(entries);
        Collections.sort(copy, (a, b) -> a.ref.compareTo(b.ref));
        return copy;
    }

    /** Bookmarks in the order they were saved (oldest first). */
    public List<Entry> allInSavedOrder() {
        return new ArrayList<>(entries);
    }

    public int size() {
        return entries.size();
    }

    /** Human-readable, re-importable JSON of all bookmarks (in canonical order). */
    public String exportJson(Bible bible) {
        JSONObject root = new JSONObject();
        try {
            root.put("app", "KJV Bible");
            root.put("type", "bookmarks");
            root.put("version", 1);
            JSONArray arr = new JSONArray();
            for (Entry e : all()) {
                JSONObject o = new JSONObject();
                o.put("book", e.ref.book);
                o.put("chapter", e.ref.chapter);
                o.put("verse", e.ref.verse);          // verse 0 == whole-chapter bookmark
                o.put("ref", e.ref.label(bible));     // for readability; ignored on import
                o.put("addedAt", e.addedAt);
                if (e.snippet != null && !e.snippet.isEmpty()) o.put("snippet", e.snippet);
                arr.put(o);
            }
            root.put("bookmarks", arr);
        } catch (JSONException ignored) {
        }
        return root.toString();
    }

    /**
     * Merge bookmarks from exported JSON. Accepts the wrapper format above or a bare array,
     * and either long ("book"/"chapter"/"verse") or short ("b"/"c"/"v") keys. Invalid or
     * out-of-range entries are skipped. Returns the number of new bookmarks added.
     */
    public int importJson(String json, Bible bible) throws JSONException {
        Object parsed = new JSONTokener(json).nextValue();
        JSONArray arr;
        if (parsed instanceof JSONArray) {
            arr = (JSONArray) parsed;
        } else if (parsed instanceof JSONObject) {
            arr = ((JSONObject) parsed).optJSONArray("bookmarks");
        } else {
            arr = null;
        }
        if (arr == null) return 0;

        int added = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            int b = o.has("book") ? o.optInt("book", -1) : o.optInt("b", -1);
            int c = o.has("chapter") ? o.optInt("chapter", -1) : o.optInt("c", -1);
            int v = o.has("verse") ? o.optInt("verse", -1) : o.optInt("v", -1);
            if (b < 1 || b > bible.bookCount()) continue;
            if (c < 1 || c > bible.chapterCount(b)) continue;
            if (v < 0 || v > bible.verseCount(b, c)) continue; // v == 0 allowed (chapter bookmark)
            long t = o.has("addedAt") ? o.optLong("addedAt", 0) : o.optLong("t", 0);
            String s = o.has("snippet") ? o.optString("snippet", "") : o.optString("s", "");
            if (add(new Ref(b, c, v), t, s)) added++;
        }
        return added;
    }
}
