package com.portablediag.kjvbible.api;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Base64;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AppCompatDelegate;

import com.portablediag.kjvbible.Bible;
import com.portablediag.kjvbible.Bookmarks;
import com.portablediag.kjvbible.DivineSpeech;
import com.portablediag.kjvbible.Prefs;
import com.portablediag.kjvbible.R;
import com.portablediag.kjvbible.Ref;
import com.portablediag.kjvbible.ShareUtil;
import com.portablediag.kjvbible.StudyNotes;
import com.portablediag.kjvbible.VerseImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Everything the API can do. Each tool is one switch per key, off until the owner turns it on.
 * Every result is {ok:true, result:{...}} or {ok:false, error:{code, message}}; a refusal says
 * why and which tools this key does have, so an agent can work around it.
 */
public final class Tools {

    private Tools() {}

    public static final class Tool {
        public final String name;
        public final String label;       // what the switch says in the app
        public final String description;
        public final String schema;      // JSON Schema of the arguments

        Tool(String name, String label, String description, String schema) {
            this.name = name;
            this.label = label;
            this.description = description;
            this.schema = schema;
        }
    }

    private static final String PASSAGE_ONLY =
            "{\"type\":\"object\",\"properties\":{\"passage\":{\"type\":\"string\","
                    + "\"description\":\"One chapter or verses in it: 'John 3:16', 'John 3:16-18', "
                    + "'Jhn 3:16, 18', 'Psalm 23'\"}},\"required\":[\"passage\"]}";

    public static final List<Tool> ALL = Collections.unmodifiableList(Arrays.asList(
            new Tool("kjv.books", "List books",
                    "The 66 books: number, name, abbreviation, chapter count, testament.",
                    "{\"type\":\"object\",\"properties\":{}}"),
            new Tool("kjv.read", "Read passages",
                    "Read a chapter or verses. Each verse has its plain text, the red-letter "
                            + "spans (words of Christ, NT) and gold spans (words of God, OT) "
                            + "as [start,end) offsets into that text.",
                    PASSAGE_ONLY),
            new Tool("kjv.search", "Search",
                    "Full-text search, the same match as the app: case-insensitive, word or phrase.",
                    "{\"type\":\"object\",\"properties\":{"
                            + "\"query\":{\"type\":\"string\"},"
                            + "\"testament\":{\"type\":\"string\",\"enum\":[\"all\",\"ot\",\"nt\"]},"
                            + "\"book\":{\"type\":\"string\",\"description\":\"Limit to one book, by name or number\"},"
                            + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":800,\"default\":50}},"
                            + "\"required\":[\"query\"]}"),
            new Tool("kjv.share_text", "Share as text",
                    "The passage as the app shares it as text: a quoted verse with its "
                            + "reference, or numbered verses with the range, or the whole chapter.",
                    PASSAGE_ONLY),
            new Tool("kjv.share_image", "Share as image",
                    "The passage as the app shares it as an image: a 1080x1080 PNG with red and "
                            + "gold kept, inline verse numbers and the reference, plus the caption "
                            + "the app sends with it. Long passages shrink, then are cut off, as in the app.",
                    "{\"type\":\"object\",\"properties\":{"
                            + "\"passage\":{\"type\":\"string\"},"
                            + "\"theme\":{\"type\":\"string\",\"enum\":[\"app\",\"light\",\"dark\"],\"default\":\"app\"}},"
                            + "\"required\":[\"passage\"]}"),
            new Tool("kjv.notes", "Study notes",
                    "Study notes: those anchored in a passage (with the highlighted phrase), or "
                            + "one note by id. Each has its title, body and sources (a url, or a "
                            + "scripture reference).",
                    "{\"type\":\"object\",\"properties\":{"
                            + "\"passage\":{\"type\":\"string\"},"
                            + "\"id\":{\"type\":\"string\"}}}"),
            new Tool("kjv.bookmarks", "Read bookmarks",
                    "The saved bookmarks. verse 0 is a whole-chapter bookmark.",
                    "{\"type\":\"object\",\"properties\":{"
                            + "\"order\":{\"type\":\"string\",\"enum\":[\"bible\",\"saved\"],\"default\":\"bible\"}}}"),
            new Tool("kjv.bookmark_add", "Add bookmarks",
                    "Bookmark verses, or a whole chapter when the passage is a chapter.",
                    PASSAGE_ONLY),
            new Tool("kjv.bookmark_remove", "Remove bookmarks",
                    "Remove the bookmarks for verses, or the whole-chapter bookmark.",
                    PASSAGE_ONLY),
            new Tool("kjv.position", "Reading position",
                    "The book and chapter the reader is open at.",
                    "{\"type\":\"object\",\"properties\":{}}"),
            new Tool("kjv.open", "Open in the reader",
                    "Open the reader at a passage. Android lets an app bring itself forward only "
                            + "while it is on screen; otherwise this posts a notification the user "
                            + "taps, and says so.",
                    PASSAGE_ONLY)
    ));

    public static Tool byName(String name) {
        for (Tool t : ALL) if (t.name.equals(name)) return t;
        return null;
    }

    public static List<Tool> enabled(ApiKeys.Key key) {
        List<Tool> out = new ArrayList<>();
        for (Tool t : ALL) if (key.allows(t.name)) out.add(t);
        return out;
    }

    /** Thrown inside a tool for a caller mistake; becomes {ok:false, error:{code, message}}. */
    static final class ToolError extends Exception {
        final String code;

        ToolError(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    /** Runs one tool for one key, records it in the audit log, never throws. */
    public static JSONObject run(Context ctx, ApiKeys.Key key, String name, JSONObject args) {
        ApiKeys keys = ApiKeys.get(ctx);
        JSONObject out = new JSONObject();
        String detail = summary(args);
        try {
            Tool tool = byName(name);
            if (tool == null) {
                throw new ToolError("unknown_tool", "No tool called " + name + ". "
                        + allowedText(key));
            }
            if (!key.allows(name)) {
                throw new ToolError("not_allowed", name + " is switched off for \"" + key.name
                        + "\". The owner can switch it on in KJV Bible › Local API › " + key.name
                        + ". " + allowedText(key));
            }
            out.put("ok", true).put("result", dispatch(ctx, key, name, args));
            keys.record(key, name, true, detail);
        } catch (ToolError e) {
            put(out, e.code, e.getMessage());
            keys.record(key, name, false, e.code + ": " + detail);
        } catch (Exception e) {
            put(out, "failed", e.getClass().getSimpleName() + ": " + e.getMessage());
            keys.record(key, name, false, "failed: " + detail);
        }
        return out;
    }

    private static void put(JSONObject out, String code, String message) {
        try {
            out.put("ok", false).put("error", new JSONObject().put("code", code).put("message", message));
        } catch (Exception ignored) {
        }
    }

    private static String allowedText(ApiKeys.Key key) {
        List<Tool> on = enabled(key);
        if (on.isEmpty()) return "This key has no tools switched on yet.";
        StringBuilder sb = new StringBuilder("This key may use: ");
        for (int i = 0; i < on.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(on.get(i).name);
        }
        return sb.append('.').toString();
    }

    private static String summary(JSONObject args) {
        String p = args.optString("passage", "");
        if (!p.isEmpty()) return p;
        String q = args.optString("query", "");
        if (!q.isEmpty()) return "\"" + q + "\"";
        return args.optString("id", "");
    }

    private static JSONObject dispatch(Context ctx, ApiKeys.Key key, String name, JSONObject args)
            throws Exception {
        Bible bible = Bible.get(ctx);
        switch (name) {
            case "kjv.books": return books(bible);
            case "kjv.read": return read(ctx, bible, passage(bible, args));
            case "kjv.search": return search(bible, args);
            case "kjv.share_text": return shareText(bible, passage(bible, args));
            case "kjv.share_image": return shareImage(ctx, bible, passage(bible, args), args.optString("theme", "app"));
            case "kjv.notes": return notes(ctx, bible, args);
            case "kjv.bookmarks": return bookmarks(ctx, bible, args.optString("order", "bible"));
            case "kjv.bookmark_add": return bookmarkAdd(ctx, bible, passage(bible, args));
            case "kjv.bookmark_remove": return bookmarkRemove(ctx, bible, passage(bible, args));
            case "kjv.position": return position(ctx, bible);
            case "kjv.open": return Opener.open(ctx, key.name, bible, passage(bible, args));
            default: throw new ToolError("unknown_tool", "No tool called " + name);
        }
    }

    // ---- helpers ----

    private static Bible.Passage passage(Bible bible, JSONObject args) throws ToolError {
        String p = args.optString("passage", "").trim();
        if (p.isEmpty()) throw new ToolError("bad_args", "Give a passage, e.g. {\"passage\":\"John 3:16\"}");
        try {
            return bible.parsePassage(p);
        } catch (IllegalArgumentException e) {
            throw new ToolError("bad_passage", e.getMessage());
        }
    }

    static String reference(Bible bible, Bible.Passage p) {
        if (p.wholeChapter()) return bible.book(p.book).name + " " + p.chapter;
        return ShareUtil.reference(bible, p.refs(bible));
    }

    private static JSONArray spans(int[][] pairs) {
        JSONArray a = new JSONArray();
        if (pairs != null) for (int[] s : pairs) a.put(new JSONArray().put(s[0]).put(s[1]));
        return a;
    }

    /** Red-letter runs as plain-text offsets: the markers themselves take no room. */
    private static int[][] redSpans(String raw) {
        List<int[]> out = new ArrayList<>();
        int plainPos = 0, start = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == Bible.RED_START) { start = plainPos; continue; }
            if (c == Bible.RED_END) {
                if (start >= 0 && plainPos > start) out.add(new int[]{start, plainPos});
                start = -1;
                continue;
            }
            plainPos++;
        }
        if (start >= 0 && plainPos > start) out.add(new int[]{start, plainPos});
        return out.toArray(new int[0][]);
    }

    private static String testament(int book) {
        return book <= 39 ? "ot" : "nt";
    }

    // ---- tools ----

    private static JSONObject books(Bible bible) throws Exception {
        JSONArray arr = new JSONArray();
        for (Bible.BookMeta b : bible.getBooks()) {
            arr.put(new JSONObject().put("book", b.index).put("name", b.name).put("abbr", b.abbr)
                    .put("chapters", b.chapterCount).put("testament", testament(b.index)));
        }
        return new JSONObject().put("books", arr);
    }

    private static JSONObject read(Context ctx, Bible bible, Bible.Passage p) throws Exception {
        DivineSpeech gold = DivineSpeech.get(ctx);
        JSONArray verses = new JSONArray();
        boolean anyGold = false;
        for (Ref r : p.refs(bible)) {
            String raw = bible.verse(r.book, r.chapter, r.verse);
            int[][] g = r.book <= 39 ? gold.find(r.book, r.chapter, r.verse) : null;
            if (g != null) anyGold = true;
            verses.put(new JSONObject().put("verse", r.verse).put("text", Bible.plain(raw))
                    .put("red", spans(redSpans(raw))).put("gold", spans(g)));
        }
        JSONObject o = new JSONObject().put("reference", reference(bible, p))
                .put("book", p.book).put("name", bible.book(p.book).name)
                .put("chapter", p.chapter).put("verses", verses);
        if (anyGold) {
            o.put("gold_attribution", "Gold spans derived from the MACULA Quotation and Speaker "
                    + "Data, (c) 2023 Clear Bible, Inc, CC BY 4.0 (github.com/Clear-Bible/speaker-quotations)");
        }
        return o;
    }

    private static JSONObject search(Bible bible, JSONObject args) throws Exception {
        String query = args.optString("query", "").trim().toLowerCase(Locale.US);
        if (query.isEmpty()) throw new ToolError("bad_args", "Give a query");
        int limit = Math.max(1, Math.min(800, args.optInt("limit", 50)));
        String t = args.optString("testament", "all").toLowerCase(Locale.US);
        int from = 1, to = bible.bookCount();
        if (t.equals("ot")) to = 39;
        else if (t.equals("nt")) from = 40;
        String book = args.optString("book", "").trim();
        if (!book.isEmpty()) {
            int b = bookIndex(bible, book);
            from = Math.max(from, b);
            to = Math.min(to, b);
        }
        JSONArray results = new JSONArray();
        int total = 0;
        for (int b = from; b <= to; b++) {
            int cc = bible.chapterCount(b);
            for (int c = 1; c <= cc; c++) {
                String[] vs = bible.chapter(b, c);
                for (int v = 0; v < vs.length; v++) {
                    String plain = Bible.plain(vs[v]);
                    if (!plain.toLowerCase(Locale.US).contains(query)) continue;
                    total++;
                    if (results.length() < limit) {
                        results.put(new JSONObject()
                                .put("reference", new Ref(b, c, v + 1).label(bible))
                                .put("book", b).put("chapter", c).put("verse", v + 1)
                                .put("text", plain));
                    }
                }
            }
        }
        return new JSONObject().put("query", query).put("total", total)
                .put("returned", results.length()).put("results", results);
    }

    private static int bookIndex(Bible bible, String s) throws ToolError {
        try {
            int n = Integer.parseInt(s);
            if (n >= 1 && n <= bible.bookCount()) return n;
        } catch (NumberFormatException ignored) {
        }
        try {
            return bible.parsePassage(s + " 1").book;
        } catch (IllegalArgumentException e) {
            throw new ToolError("bad_args", "No book called \"" + s + "\"");
        }
    }

    private static JSONObject shareText(Bible bible, Bible.Passage p) throws Exception {
        String text = p.wholeChapter()
                ? ShareUtil.buildChapter(bible, p.book, p.chapter)
                : ShareUtil.buildPassage(bible, p.refs(bible));
        return new JSONObject().put("reference", reference(bible, p)).put("text", text);
    }

    /** The PNG bytes plus the caption, shared by the tool and GET /api/image. */
    static final class Image {
        final String reference;
        final String caption;
        final byte[] png;

        Image(String reference, String caption, byte[] png) {
            this.reference = reference;
            this.caption = caption;
            this.png = png;
        }
    }

    static Image renderImage(Context ctx, Bible bible, Bible.Passage p, String theme) throws ToolError {
        boolean dark;
        switch (theme == null ? "app" : theme.toLowerCase(Locale.US)) {
            case "light": dark = false; break;
            case "dark": dark = true; break;
            case "app":
            case "":
                int mode = new Prefs(ctx).themeMode();
                if (mode == AppCompatDelegate.MODE_NIGHT_YES) dark = true;
                else if (mode == AppCompatDelegate.MODE_NIGHT_NO) dark = false;
                else dark = (Resources.getSystem().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                break;
            default:
                throw new ToolError("bad_args", "theme is app, light or dark");
        }
        Configuration cfg = new Configuration(ctx.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        Context themed = new ContextThemeWrapper(ctx.createConfigurationContext(cfg), R.style.Theme_KJVBible);

        List<Ref> refs = p.refs(bible);
        Bitmap bmp = VerseImage.render(themed, bible, DivineSpeech.get(ctx), refs);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        return new Image(reference(bible, p), ShareUtil.buildPassage(bible, refs), out.toByteArray());
    }

    private static JSONObject shareImage(Context ctx, Bible bible, Bible.Passage p, String theme)
            throws Exception {
        Image img = renderImage(ctx, bible, p, theme);
        return new JSONObject().put("reference", img.reference).put("caption", img.caption)
                .put("mime", "image/png").put("width", 1080).put("height", 1080)
                .put("image_base64", Base64.encodeToString(img.png, Base64.NO_WRAP));
    }

    private static JSONObject note(StudyNotes.Note n) throws Exception {
        JSONArray sources = new JSONArray();
        for (StudyNotes.Source s : n.sources) {
            JSONObject o = new JSONObject().put("label", s.label);
            if (s.url != null) o.put("url", s.url);
            sources.put(o);
        }
        return new JSONObject().put("id", n.id).put("category", n.category).put("title", n.title)
                .put("body", n.body).put("sources", sources);
    }

    private static JSONObject notes(Context ctx, Bible bible, JSONObject args) throws Exception {
        StudyNotes sn = StudyNotes.get(ctx);
        String id = args.optString("id", "").trim();
        if (!id.isEmpty()) {
            StudyNotes.Note n = sn.byId(id);
            if (n == null) throw new ToolError("not_found", "No study note with id " + id);
            return new JSONObject().put("note", note(n));
        }
        Bible.Passage p = passage(bible, args);
        JSONArray found = new JSONArray();
        for (Ref r : p.refs(bible)) {
            String plain = Bible.plain(bible.verse(r.book, r.chapter, r.verse));
            for (StudyNotes.Span s : sn.find(r.book, r.chapter, r.verse, plain)) {
                StudyNotes.Note n = sn.byId(s.noteId);
                if (n == null) continue;
                found.put(new JSONObject().put("reference", r.label(bible)).put("verse", r.verse)
                        .put("phrase", plain.substring(s.start, s.end))
                        .put("span", new JSONArray().put(s.start).put(s.end))
                        .put("note", note(n)));
            }
        }
        return new JSONObject().put("reference", reference(bible, p)).put("notes", found);
    }

    private static JSONObject entry(Bible bible, Bookmarks.Entry e) throws Exception {
        return new JSONObject().put("reference", e.ref.label(bible)).put("book", e.ref.book)
                .put("chapter", e.ref.chapter).put("verse", e.ref.verse)
                .put("addedAt", e.addedAt).put("snippet", e.snippet);
    }

    private static JSONObject bookmarks(Context ctx, Bible bible, String order) throws Exception {
        Bookmarks bm = Bookmarks.get(ctx);
        List<Bookmarks.Entry> list = "saved".equalsIgnoreCase(order) ? bm.allInSavedOrder() : bm.all();
        JSONArray arr = new JSONArray();
        for (Bookmarks.Entry e : list) arr.put(entry(bible, e));
        return new JSONObject().put("count", arr.length()).put("bookmarks", arr);
    }

    /** The refs a passage bookmarks as: verses, or the chapter itself (verse 0), as in the app. */
    private static List<Ref> bookmarkRefs(Bible bible, Bible.Passage p) {
        if (p.wholeChapter()) return Collections.singletonList(new Ref(p.book, p.chapter, 0));
        return p.refs(bible);
    }

    private static JSONObject bookmarkAdd(Context ctx, Bible bible, Bible.Passage p) throws Exception {
        Bookmarks bm = Bookmarks.get(ctx);
        long now = System.currentTimeMillis();
        JSONArray added = new JSONArray(), already = new JSONArray();
        for (Ref r : bookmarkRefs(bible, p)) {
            int v = r.verse == 0 ? 1 : r.verse;   // a chapter bookmark keeps its first verse
            String snippet = Bible.plain(bible.verse(r.book, r.chapter, v));
            if (bm.add(r, now, snippet)) added.put(r.label(bible)); else already.put(r.label(bible));
        }
        return new JSONObject().put("added", added).put("already", already);
    }

    private static JSONObject bookmarkRemove(Context ctx, Bible bible, Bible.Passage p) throws Exception {
        Bookmarks bm = Bookmarks.get(ctx);
        JSONArray removed = new JSONArray(), absent = new JSONArray();
        for (Ref r : bookmarkRefs(bible, p)) {
            if (bm.contains(r)) {
                bm.remove(r);
                removed.put(r.label(bible));
            } else {
                absent.put(r.label(bible));
            }
        }
        return new JSONObject().put("removed", removed).put("not_bookmarked", absent);
    }

    private static JSONObject position(Context ctx, Bible bible) throws Exception {
        Prefs prefs = new Prefs(ctx);
        int b = prefs.lastBook(), c = prefs.lastChapter();
        return new JSONObject().put("reference", new Ref(b, c, 0).label(bible))
                .put("book", b).put("chapter", c);
    }
}
