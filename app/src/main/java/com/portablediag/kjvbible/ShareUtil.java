package com.portablediag.kjvbible;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Builds plain-text passages for sharing / copying, and dispatches the intents. */
public final class ShareUtil {

    private ShareUtil() {}

    /**
     * Build a shareable passage from a sorted list of refs that all belong to the
     * SAME book &amp; chapter. Single verse -> quoted form; multiple -> numbered lines.
     */
    public static String buildPassage(Bible bible, List<Ref> refs) {
        if (refs.isEmpty()) return "";
        Ref first = refs.get(0);
        String book = bible.book(first.book).name;
        int chapter = first.chapter;

        if (refs.size() == 1) {
            String text = Bible.plain(bible.verse(first.book, first.chapter, first.verse));
            return "“" + text + "”\n— " + book + " " + chapter + ":"
                    + first.verse + " (KJV)";
        }

        StringBuilder sb = new StringBuilder();
        for (Ref r : refs) {
            String text = Bible.plain(bible.verse(r.book, r.chapter, r.verse));
            sb.append(r.verse).append(' ').append(text).append('\n');
        }
        sb.append('\n').append(book).append(' ').append(chapter).append(':')
                .append(verseRange(refs)).append(" (KJV)");
        return sb.toString();
    }

    /** Build the whole chapter as shareable text. */
    public static String buildChapter(Bible bible, int book, int chapter) {
        StringBuilder sb = new StringBuilder();
        sb.append(bible.book(book).name).append(' ').append(chapter).append(" (KJV)\n\n");
        String[] verses = bible.chapter(book, chapter);
        for (int i = 0; i < verses.length; i++) {
            sb.append(i + 1).append(' ').append(Bible.plain(verses[i])).append('\n');
        }
        return sb.toString().trim();
    }

    /** Compact verse list within one chapter, e.g. "16-18" or "16, 18, 20-21". */
    private static String verseRange(List<Ref> refs) {
        StringBuilder out = new StringBuilder();
        int runStart = refs.get(0).verse;
        int prev = runStart;
        for (int i = 1; i <= refs.size(); i++) {
            int cur = (i < refs.size()) ? refs.get(i).verse : Integer.MIN_VALUE;
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            if (out.length() > 0) out.append(", ");
            if (runStart == prev) out.append(runStart);
            else out.append(runStart).append('-').append(prev);
            runStart = cur;
            prev = cur;
        }
        return out.toString();
    }

    public static void share(Context ctx, String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_via)));
    }

    public static void copy(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("verse", text));
    }
}
