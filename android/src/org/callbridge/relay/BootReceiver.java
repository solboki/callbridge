package org.callbridge.relay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the relay when the Android container (AppSupport) boots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RelayService.start(context);
    }
}
