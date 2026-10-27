package com.portablediag.kjvbible.api;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.portablediag.kjvbible.Bible;
import com.portablediag.kjvbible.KjvApp;
import com.portablediag.kjvbible.MainActivity;
import com.portablediag.kjvbible.R;

import org.json.JSONObject;

/**
 * kjv.open: bring the reader to a passage.
 *
 * Android lets an app start its own activity only while one of its windows is on screen. When the
 * reader is showing, it moves at once; otherwise a notification asks the user to tap, and the
 * result says which of the two happened.
 */
final class Opener {

    private Opener() {}

    private static final String CHANNEL = "api_open";
    private static final int NOTIFY_ID = 20;

    static JSONObject open(Context ctx, String caller, Bible bible, Bible.Passage p) throws Exception {
        int verse = p.wholeChapter() ? 0 : p.verses.get(0);
        Intent i = new Intent(ctx, MainActivity.class)
                .setAction(MainActivity.ACTION_OPEN)
                .putExtra(MainActivity.EXTRA_BOOK, p.book)
                .putExtra(MainActivity.EXTRA_CHAPTER, p.chapter)
                .putExtra(MainActivity.EXTRA_VERSE, verse)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String ref = Tools.reference(bible, p);
        JSONObject out = new JSONObject().put("reference", ref);

        if (KjvApp.isForeground()) {
            new Handler(Looper.getMainLooper()).post(() -> ctx.startActivity(i));
            return out.put("opened", true).put("notified", false);
        }

        out.put("opened", false);
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return out.put("notified", false).put("reason", "KJV Bible is not on screen and may "
                    + "not post notifications, so the passage could not be offered. The user can "
                    + "allow notifications for KJV Bible in Android settings.");
        }
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                ctx.getString(R.string.api_open_channel), NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(NOTIFY_ID, new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_book)
                .setContentTitle(ctx.getString(R.string.api_open_title, ref))
                .setContentText(ctx.getString(R.string.api_open_text, caller))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
        return out.put("notified", true).put("reason", "KJV Bible is not on screen; Android only "
                + "lets it open itself then, so a notification asks the user to tap.");
    }
}
