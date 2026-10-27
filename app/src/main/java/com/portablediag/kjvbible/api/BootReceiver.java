package com.portablediag.kjvbible.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the API back after a reboot or an app update, if it was on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            if (LocalApi.enabled(context)) ApiService.sync(context);
        }
    }
}
