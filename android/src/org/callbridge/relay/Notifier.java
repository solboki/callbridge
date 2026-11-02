package org.callbridge.relay;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONObject;

/**
 * Transforme un événement venu de Sailfish OS en notification Android.
 * C'est cette notification que l'application compagnon de la montre
 * (Garmin Connect, par exemple) voit et transmet au poignet.
 */
final class Notifier {

    /** Événements plus vieux que cela ne sont pas affichés (en ms). */
    static final long MAX_AGE_MS = 90 * 1000L;

    private Notifier() { }

    /**
     * Vrai si Android laisse l'application publier des notifications.
     * Sous Android 13 et plus, elles sont bloquées tant que l'utilisateur ne
     * les a pas autorisées ; elles disparaissent alors sans aucun message.
     * (Méthode de l'API 24, appelée par réflexion.)
     */
    static boolean enabled(Context ctx) {
        try {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            Object r = NotificationManager.class.getMethod("areNotificationsEnabled").invoke(nm);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return true;
        }
    }

    static int idFor(String key) {
        return 1000 + (key.hashCode() & 0x3fffffff) % 1000000;
    }

    /** Traite un événement JSON. Retourne vrai s'il a été appliqué. */
    static boolean handle(Context ctx, JSONObject ev) {
        long ts = ev.optLong("ts", 0);
        if (ts > 0 && System.currentTimeMillis() - ts > MAX_AGE_MS) {
            return false;
        }
        String action = ev.optString("action", "post");
        String key = ev.optString("key", "default");
        if ("cancel".equals(action)) {
            cancel(ctx, key);
        } else if ("command".equals(action)) {
            String cmd = ev.optString("command", "");
            if ("hide-icon".equals(cmd)) {
                RelayService.setIconHidden(ctx, true);
            } else if ("show-icon".equals(cmd)) {
                RelayService.setIconHidden(ctx, false);
            } else {
                RelayService.writeState(ctx);
            }
        } else {
            String kind = ev.optString("kind", "other");
            String title = ev.optString("title", "");
            String text = ev.optString("text", "");
            String number = ev.optString("number", "");
            if ("call".equals(kind) && ev.optString("name", "").length() == 0
                    && number.length() > 0) {
                String name = lookupName(ctx, number);
                if (name != null) {
                    title = name;
                    text = ev.optString("label", "Appel entrant") + " · " + number;
                }
            }
            post(ctx, key, kind, title, text);
        }
        return true;
    }

    /**
     * Cherche le nom d'un numéro dans les contacts du conteneur Android
     * (copie des contacts Sailfish faite par AppSupport). Null si inconnu
     * ou si l'autorisation « Contacts » n'est pas accordée.
     */
    static String lookupName(Context ctx, String number) {
        if (ctx.checkCallingOrSelfPermission("android.permission.READ_CONTACTS")
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Cursor c = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            c = ctx.getContentResolver().query(uri,
                    new String[] {ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                return name == null || name.length() == 0 ? null : name;
            }
        } catch (Exception e) {
            Log.w(RelayService.TAG, "contact lookup failed: " + e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    static void cancel(Context ctx, String key) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(idFor(key));
    }

    static void post(Context ctx, String key, String kind, String title, String text) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, 0);

        Notification.Builder b = new Notification.Builder(ctx)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                // Le téléphone sonne déjà via Sailfish : pas de son en double.
                .setDefaults(0)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PRIVATE);

        if ("call".equals(kind) || "missed".equals(kind)) {
            b.setCategory(Notification.CATEGORY_CALL);
        } else if ("sms".equals(kind)) {
            b.setCategory(Notification.CATEGORY_MESSAGE);
        } else if ("other".equals(kind)) {
            b.setCategory(Notification.CATEGORY_EMAIL);
        }

        nm.notify(idFor(key), b.build());
    }
}
