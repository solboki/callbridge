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

    // ------------------------------------------------------------------
    // How loud the notification is on the phone
    //
    // AppSupport copies every Android notification into Sailfish and derives
    // sound and banner from its priority. For an app targeting API 25,
    // Android also derives the notification's importance from that priority:
    //   high -> sound and banner, low -> silent, min -> silent and folded.
    // The watch app reads the notification on the Android side whatever its
    // priority, but some watch apps skip the quietest ones: hence a setting.
    // ------------------------------------------------------------------

    static final String LEVEL_HIGH = "high";
    static final String LEVEL_LOW = "low";
    static final String LEVEL_MIN = "min";

    static String level(Context ctx) {
        return ctx.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
                .getString("level", LEVEL_LOW);
    }

    static void setLevel(Context ctx, String level) {
        if (!LEVEL_HIGH.equals(level) && !LEVEL_LOW.equals(level) && !LEVEL_MIN.equals(level)) {
            return;
        }
        ctx.getSharedPreferences("callbridge", Context.MODE_PRIVATE).edit()
                .putString("level", level).apply();
    }

    @SuppressWarnings("deprecation")
    static int priority(Context ctx) {
        String l = level(ctx);
        if (LEVEL_HIGH.equals(l)) {
            return Notification.PRIORITY_HIGH;
        }
        return LEVEL_MIN.equals(l) ? Notification.PRIORITY_MIN : Notification.PRIORITY_LOW;
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
            if ("uninstall".equals(cmd)) {
                RelayService.requestUninstall(ctx);
            } else if ("set-level".equals(cmd)) {
                setLevel(ctx, ev.optString("level", LEVEL_LOW));
                RelayService.writeState(ctx);
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
                .setPriority(priority(ctx))
                .setOnlyAlertOnce(true)
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
