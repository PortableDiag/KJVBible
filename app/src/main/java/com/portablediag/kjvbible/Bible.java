package com.portablediag.kjvbible;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the King James Bible from bundled JSON assets.
 *
 * Asset layout:
 *   bible/index.json   -> {"books":[{"i":1,"n":"Genesis","a":"Gen","c":50,"vc":[31,25,...]}...]}
 *   bible/<n>.json     -> [ [ "verse1", "verse2", ... ],  // chapter 1
 *                           [ ... ] ... ]                  // chapter 2 ...
 *
 * Verse strings embed red-letter (words of Christ) runs between the control
 * characters {@link #RED_START} and {@link #RED_END}.
 */
public final class Bible {

    public static final char RED_START = '\uE000';
    public static final char RED_END   = '\uE001';

    public static final class BookMeta {
        public final int index;        // 1-based
        public final String name;      // e.g. "1 Samuel"
        public final String abbr;      // e.g. "1Sa"
        public final int chapterCount;
        public final int[] verseCounts; // verseCounts[c-1] = verses in chapter c

        BookMeta(int index, String name, String abbr, int chapterCount, int[] verseCounts) {
            this.index = index;
            this.name = name;
            this.abbr = abbr;
            this.chapterCount = chapterCount;
            this.verseCounts = verseCounts;
        }
    }

    private static Bible instance;

    private final Context appContext;
    private final List<BookMeta> books = new ArrayList<>(66);

    // Small LRU cache of loaded book bodies: bookIndex -> chapters -> verses.
    private final Map<Integer, String[][]> bookCache =
            new LinkedHashMap<Integer, String[][]>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String[][]> eldest) {
                    return size() > 4;
                }
            };

    private Bible(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        loadIndex();
    }

    public static synchronized Bible get(Context ctx) {
        if (instance == null) {
            instance = new Bible(ctx);
        }
        return instance;
    }

    private void loadIndex() {
        try {
            JSONObject root = new JSONObject(readAsset("bible/index.json"));
            JSONArray arr = root.getJSONArray("books");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject b = arr.getJSONObject(i);
                JSONArray vc = b.getJSONArray("vc");
                int[] counts = new int[vc.length()];
                for (int j = 0; j < vc.length(); j++) counts[j] = vc.getInt(j);
                books.add(new BookMeta(
                        b.getInt("i"), b.getString("n"), b.getString("a"),
                        b.getInt("c"), counts));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Bible index", e);
        }
    }

    public List<BookMeta> getBooks() {
        return books;
    }

    public BookMeta book(int bookIndex) {
        return books.get(bookIndex - 1);
    }

    public int bookCount() {
        return books.size();
    }

    public int chapterCount(int bookIndex) {
        return book(bookIndex).chapterCount;
    }

    public int verseCount(int bookIndex, int chapter) {
        int[] vc = book(bookIndex).verseCounts;
        if (chapter < 1 || chapter > vc.length) return 0;
        return vc[chapter - 1];
    }

    /** Returns the verses of a chapter (1-based chapter). Index 0 == verse 1. */
    public synchronized String[] chapter(int bookIndex, int chapter) {
        String[][] body = bookCache.get(bookIndex);
        if (body == null) {
            body = loadBook(bookIndex);
            bookCache.put(bookIndex, body);
        }
        if (chapter < 1 || chapter > body.length) return new String[0];
        return body[chapter - 1];
    }

    public String verse(int bookIndex, int chapter, int verse) {
        String[] verses = chapter(bookIndex, chapter);
        if (verse < 1 || verse > verses.length) return "";
        return verses[verse - 1];
    }

    private String[][] loadBook(int bookIndex) {
        try {
            JSONArray chapters = new JSONArray(readAsset("bible/" + bookIndex + ".json"));
            String[][] out = new String[chapters.length()][];
            for (int c = 0; c < chapters.length(); c++) {
                JSONArray verses = chapters.getJSONArray(c);
                String[] vs = new String[verses.length()];
                for (int v = 0; v < verses.length(); v++) vs[v] = verses.getString(v);
                out[c] = vs;
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load book " + bookIndex, e);
        }
    }

    private String readAsset(String path) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = appContext.getAssets().open(path);
             BufferedReader r = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    /** Strip red-letter control markers, leaving plain text (for search, share, copy). */
    public static String plain(String raw) {
        if (raw.indexOf(RED_START) < 0 && raw.indexOf(RED_END) < 0) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != RED_START && c != RED_END) sb.append(c);
        }
        return sb.toString();
    }
}
