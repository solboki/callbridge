package org.callbridge.relay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Relance le relais au démarrage du conteneur Android (AppSupport). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RelayService.start(context);
    }
}
