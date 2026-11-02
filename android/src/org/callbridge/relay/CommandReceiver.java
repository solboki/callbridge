package org.callbridge.relay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Canal de secours, utile pour tester la moitié Android seule :
 *   devel-su appsupport-attach am broadcast -a org.callbridge.relay.POST \
 *       -n org.callbridge.relay/.CommandReceiver -e title "Test" -e text "Bonjour"
 */
public class CommandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra("key");
        if (key == null) {
            key = "broadcast";
        }
        if ("org.callbridge.relay.CANCEL".equals(intent.getAction())) {
            Notifier.cancel(context, key);
            return;
        }
        String kind = intent.getStringExtra("kind");
        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");
        Notifier.post(context, key, kind == null ? "other" : kind,
                title == null ? "Callbridge" : title, text == null ? "" : text);
    }
}
