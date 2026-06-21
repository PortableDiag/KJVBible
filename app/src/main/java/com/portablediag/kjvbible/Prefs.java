package com.portablediag.kjvbible;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Lightweight wrapper over SharedPreferences for app settings + last position. */
public final class Prefs {

    private static final String FILE = "kjv_prefs";

    private static final String K_FONT_SCALE = "font_scale";
    private static final String K_THEME = "theme_mode";
    private static final String K_LAST_BOOK = "last_book";
    private static final String K_LAST_CHAPTER = "last_chapter";
    private static final String K_BOOKMARKS = "bookmarks_json";
    private static final String K_BOOKMARK_SORT = "bookmark_sort";

    public static final int SORT_BIBLE = 0;   // canonical order, with OT/NT separators
    public static final int SORT_SAVED = 1;   // order in which they were saved

    public static final float DEFAULT_FONT_SCALE = 1.0f;  // base text size = 18sp
    public static final float MIN_FONT_SCALE = 0.8f;
    public static final float MAX_FONT_SCALE = 2.0f;
    public static final float BASE_TEXT_SP = 18f;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public float fontScale() {
        return clamp(sp.getFloat(K_FONT_SCALE, DEFAULT_FONT_SCALE));
    }

    public void setFontScale(float v) {
        sp.edit().putFloat(K_FONT_SCALE, clamp(v)).apply();
    }

    public float textSizeSp() {
        return BASE_TEXT_SP * fontScale();
    }

    private static float clamp(float v) {
        if (v < MIN_FONT_SCALE) return MIN_FONT_SCALE;
        if (v > MAX_FONT_SCALE) return MAX_FONT_SCALE;
        return v;
    }

    /** One of AppCompatDelegate.MODE_NIGHT_* */
    public int themeMode() {
        return sp.getInt(K_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public void setThemeMode(int mode) {
        sp.edit().putInt(K_THEME, mode).apply();
    }

    public int lastBook() {
        return sp.getInt(K_LAST_BOOK, 1);
    }

    public int lastChapter() {
        return sp.getInt(K_LAST_CHAPTER, 1);
    }

    public void setLastPosition(int book, int chapter) {
        sp.edit().putInt(K_LAST_BOOK, book).putInt(K_LAST_CHAPTER, chapter).apply();
    }

    public int bookmarkSort() {
        return sp.getInt(K_BOOKMARK_SORT, SORT_BIBLE);
    }

    public void setBookmarkSort(int sort) {
        sp.edit().putInt(K_BOOKMARK_SORT, sort).apply();
    }

    public String bookmarksJson() {
        return sp.getString(K_BOOKMARKS, "[]");
    }

    public void setBookmarksJson(String json) {
        sp.edit().putString(K_BOOKMARKS, json).apply();
    }
}
