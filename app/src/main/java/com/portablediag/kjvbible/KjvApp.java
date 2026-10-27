package com.portablediag.kjvbible;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class KjvApp extends Application {

    /** Started activities; above zero means a window is on screen (the local API's kjv.open). */
    private static int started;

    public static synchronized boolean isForeground() {
        return started > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeUtil.applySavedTheme(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { synchronized (KjvApp.class) { started++; } }
            @Override public void onActivityStopped(Activity a) { synchronized (KjvApp.class) { started--; } }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
