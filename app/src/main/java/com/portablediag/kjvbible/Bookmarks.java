package com.portablediag.kjvbible;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public int size() {
        return entries.size();
    }
}
