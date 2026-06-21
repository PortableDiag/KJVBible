package com.portablediag.kjvbible;

import android.app.Application;

public class KjvApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeUtil.applySavedTheme(this);
    }
}
