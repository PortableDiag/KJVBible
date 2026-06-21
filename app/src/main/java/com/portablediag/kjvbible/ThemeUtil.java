package com.portablediag.kjvbible;

import android.content.Context;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;

/** Theme helpers: applying night mode and resolving themed color attributes. */
public final class ThemeUtil {

    private ThemeUtil() {}

    public static void applySavedTheme(Context ctx) {
        AppCompatDelegate.setDefaultNightMode(new Prefs(ctx).themeMode());
    }

    public static int color(Context ctx, int attr) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}
