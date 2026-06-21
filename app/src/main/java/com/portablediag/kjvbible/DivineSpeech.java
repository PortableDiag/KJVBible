package com.portablediag.kjvbible;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Old-Testament "words of God" spans (rendered in gold), loaded from
 * assets/study/divine_speech.json.
 *
 * The spans were derived from the verse ranges in the MACULA Quotation and Speaker Data
 * (© 2023 Clear Bible, Inc, CC BY 4.0) where the speaker is God, with the narrative
 * introduction trimmed off the opening verse. Coordinates are into each verse's plain text.
 */
public final class DivineSpeech {

    private static DivineSpeech instance;

    // refKey(book,chapter,verse) -> array of {start,end} pairs
    private final Map<Long, int[][]> spans = new HashMap<>();

    private DivineSpeech(Context ctx) {
        load(ctx.getApplicationContext());
    }

    public static synchronized DivineSpeech get(Context ctx) {
        if (instance == null) instance = new DivineSpeech(ctx);
        return instance;
    }

    private void load(Context ctx) {
        try {
            JSONObject root = new JSONObject(readAsset(ctx, "study/divine_speech.json"));
            JSONObject books = root.getJSONObject("spans");
            for (Iterator<String> bi = books.keys(); bi.hasNext(); ) {
                String bk = bi.next();
                int book = Integer.parseInt(bk);
                JSONObject chapters = books.getJSONObject(bk);
                for (Iterator<String> ci = chapters.keys(); ci.hasNext(); ) {
                    String ck = ci.next();
                    int chapter = Integer.parseInt(ck);
                    JSONObject verses = chapters.getJSONObject(ck);
                    for (Iterator<String> vi = verses.keys(); vi.hasNext(); ) {
                        String vk = vi.next();
                        int verse = Integer.parseInt(vk);
                        JSONArray arr = verses.getJSONArray(vk);
                        int[][] pairs = new int[arr.length()][2];
                        for (int i = 0; i < arr.length(); i++) {
                            JSONArray p = arr.getJSONArray(i);
                            pairs[i][0] = p.getInt(0);
                            pairs[i][1] = p.getInt(1);
                        }
                        spans.put(refKey(book, chapter, verse), pairs);
                    }
                }
            }
        } catch (Exception e) {
            // Optional data; absence simply means no gold spans.
        }
    }

    private static long refKey(int book, int chapter, int verse) {
        return ((long) book << 24) | ((long) chapter << 12) | verse;
    }

    /** Gold spans (plain-body coordinates) for a verse, or null if none. */
    public int[][] find(int book, int chapter, int verse) {
        return spans.get(refKey(book, chapter, verse));
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
