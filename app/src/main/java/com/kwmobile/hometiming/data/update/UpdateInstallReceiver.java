package com.kwmobile.hometiming.data.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Explicit mutable PendingIntent target; not exported to other apps. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { UpdateManager.get(context).installationResult(intent); }
}
