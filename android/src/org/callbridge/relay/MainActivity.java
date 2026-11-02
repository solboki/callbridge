package org.callbridge.relay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Single screen: relay status, permissions and test notification. */
public class MainActivity extends Activity {

    private static final String[] PERMS = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_CONTACTS",
    };

    private TextView status;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Callbridge");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        TextView intro = new TextView(this);
        intro.setText(L.t(
                "Forwards native Sailfish OS calls and SMS to Android, so that your watch app "
                        + "(Garmin Connect, for instance) can pass them on.\n\n"
                        + "In your watch app, allow notifications from \"Callbridge\".",
                "Relaie vers Android les appels et SMS natifs de Sailfish OS, pour que "
                        + "l'application de la montre (Garmin Connect, par exemple) les transmette.\n\n"
                        + "Dans l'application de la montre, autorisez les notifications de "
                        + "« Callbridge »."));
        intro.setPadding(0, pad / 2, 0, pad);
        col.addView(intro);

        status = new TextView(this);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(0, 0, 0, pad);
        col.addView(status);

        col.addView(button(L.t("Grant permissions", "Accorder les autorisations"),
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!askPermissions()) {
                    toast(L.t("Storage and contacts permissions are granted.",
                            "Les autorisations (stockage, contacts) sont accordées."));
                }
                if (!Notifier.enabled(MainActivity.this)) {
                    toast(L.t("Notifications are blocked: enable them in the next screen.",
                            "Les notifications sont bloquées : activez-les dans l'écran qui s'ouvre."));
                    openNotificationSettings();
                }
            }
        }));
        col.addView(button(L.t("Notification settings", "Réglages des notifications"),
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationSettings();
            }
        }));
        col.addView(button(L.t("Test notification", "Notification de test"),
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Notifier.enabled(MainActivity.this)) {
                    toast(L.t("Android blocks Callbridge notifications, so nothing can reach "
                                    + "the watch. Open \"Notification settings\".",
                            "Android bloque les notifications de Callbridge : aucune ne peut "
                                    + "partir. Ouvrez « Réglages des notifications »."));
                    return;
                }
                Notifier.post(MainActivity.this, "test", "sms", "Callbridge",
                        L.t("Test notification: if it reaches your watch, everything works.",
                                "Notification de test : si elle arrive sur la montre, tout fonctionne."));
                toast(L.t("Test notification posted in Android.",
                        "Notification de test publiée dans Android."));
            }
        }));
        col.addView(button(L.t("Don't optimise battery", "Ne pas optimiser la batterie"),
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ignored) {
                    // setting missing in some containers
                }
            }
        }));
        col.addView(button(L.t("Restart relay", "Redémarrer le relais"),
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, RelayService.class));
                RelayService.start(MainActivity.this);
                toast(L.t("Relay restarted.", "Relais redémarré."));
            }
        }));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);

        askPermissions();
        RelayService.start(this);
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /** Opens Callbridge's notification settings, or its app info page as a fallback. */
    private void openNotificationSettings() {
        Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
        i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
        i.putExtra("app_package", getPackageName());
        i.putExtra("app_uid", getApplicationInfo().uid);
        try {
            startActivity(i);
            return;
        } catch (Exception ignored) {
            // screen missing: fall back to the app info page
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast(L.t("Cannot open Android settings.", "Impossible d'ouvrir les réglages Android."));
        }
    }

    /** Requests missing permissions. Returns true if a request was made. */
    private boolean askPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        List<String> missing = new ArrayList<String>();
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), 1);
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        // The relay must restart to gain access to the shared folder.
        stopService(new Intent(this, RelayService.class));
        RelayService.start(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
        if (RelayService.takeUninstallRequest(this)) {
            // Android's own uninstall dialog (API 14+); the user confirms there.
            try {
                startActivity(new Intent(Intent.ACTION_DELETE,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                toast(L.t("Cannot open the uninstall dialog.",
                        "Impossible d'ouvrir la fenêtre de désinstallation."));
            }
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        RelayService.writeState(this);
        super.onPause();
    }

    private String yes(boolean b) {
        return b ? L.t("yes", "oui") : L.t("NO", "NON");
    }

    private void updateStatus() {
        File dir = RelayService.exchangeDir();
        StringBuilder sb = new StringBuilder();
        sb.append(L.t("Relay: ", "Relais : "))
                .append(RelayService.running ? L.t("running", "actif") : L.t("stopped", "arrêté"))
                .append('\n');
        sb.append(L.t("Folder: ", "Dossier : ")).append(dir).append('\n');
        sb.append(L.t("Readable: ", "Lisible : ")).append(yes(dir.canRead())).append('\n');
        sb.append(L.t("Notifications allowed: ", "Notifications autorisées : "))
                .append(yes(Notifier.enabled(this))).append('\n');
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append(L.t("Storage: ", "Stockage : "))
                    .append(yes(checkSelfPermission(PERMS[1]) == PackageManager.PERMISSION_GRANTED))
                    .append('\n');
            sb.append(L.t("Contacts: ", "Contacts : "))
                    .append(yes(checkSelfPermission(PERMS[2]) == PackageManager.PERMISSION_GRANTED))
                    .append('\n');
        }
        if (RelayService.lastEventAt > 0) {
            sb.append(L.t("Last event: ", "Dernier événement : "))
                    .append(DateFormat.getTimeInstance().format(new Date(RelayService.lastEventAt)))
                    .append("\n  ").append(RelayService.lastEventTitle);
        } else {
            sb.append(L.t("No event received since start.", "Aucun événement reçu depuis le démarrage."));
        }
        status.setText(sb.toString());
    }
}
