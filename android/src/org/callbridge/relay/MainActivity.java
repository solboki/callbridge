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

/** Écran unique : état du relais, autorisations et notification de test. */
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
        intro.setText("Relaie vers Android les appels et SMS natifs de Sailfish OS, "
                + "pour que l'application de la montre (Garmin Connect, par exemple) les transmette.\n\n"
                + "Dans l'application de la montre, autorisez les notifications de l'application "
                + "« Callbridge ».");
        intro.setPadding(0, pad / 2, 0, pad);
        col.addView(intro);

        status = new TextView(this);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(0, 0, 0, pad);
        col.addView(status);

        col.addView(button("Accorder les autorisations", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!askPermissions()) {
                    toast("Les autorisations (stockage, contacts) sont accordées.");
                }
                if (!Notifier.enabled(MainActivity.this)) {
                    toast("Les notifications sont bloquées : activez-les dans l'écran qui s'ouvre.");
                    openNotificationSettings();
                }
            }
        }));
        col.addView(button("Réglages des notifications", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationSettings();
            }
        }));
        col.addView(button("Notification de test", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Notifier.enabled(MainActivity.this)) {
                    toast("Android bloque les notifications de Callbridge : "
                            + "aucune ne peut partir. Ouvrez « Réglages des notifications ».");
                    return;
                }
                Notifier.post(MainActivity.this, "test", "sms", "Callbridge",
                        "Notification de test : si elle arrive sur la montre, tout fonctionne.");
                toast("Notification de test publiée dans Android.");
            }
        }));
        col.addView(button("Ne pas optimiser la batterie", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ignored) {
                    // réglage absent dans certains conteneurs
                }
            }
        }));
        col.addView(button("Redémarrer le relais", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, RelayService.class));
                RelayService.start(MainActivity.this);
                toast("Relais redémarré.");
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

    /** Ouvre l'écran des notifications de Callbridge (ou, à défaut, sa fiche). */
    private void openNotificationSettings() {
        Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
        i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
        i.putExtra("app_package", getPackageName());
        i.putExtra("app_uid", getApplicationInfo().uid);
        try {
            startActivity(i);
            return;
        } catch (Exception ignored) {
            // écran absent : on passe à la fiche de l'application
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("Impossible d'ouvrir les réglages Android.");
        }
    }

    /** Demande les autorisations manquantes. Retourne vrai si une demande a été faite. */
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
        // Le relais doit être relancé pour accéder au dossier partagé.
        stopService(new Intent(this, RelayService.class));
        RelayService.start(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void updateStatus() {
        File dir = RelayService.exchangeDir();
        StringBuilder sb = new StringBuilder();
        sb.append("Relais : ").append(RelayService.running ? "actif" : "arrêté").append('\n');
        sb.append("Dossier : ").append(dir).append('\n');
        sb.append("Lisible : ").append(dir.canRead() ? "oui" : "non").append('\n');
        sb.append("Notifications autorisées : ")
                .append(Notifier.enabled(this) ? "oui" : "NON").append('\n');
        if (Build.VERSION.SDK_INT >= 23) {
            for (String p : PERMS) {
                boolean ok = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
                sb.append(p.substring(p.lastIndexOf('.') + 1)).append(" : ")
                        .append(ok ? "oui" : "non").append('\n');
            }
        }
        if (RelayService.lastEventAt > 0) {
            sb.append("Dernier événement : ")
                    .append(DateFormat.getTimeInstance().format(new Date(RelayService.lastEventAt)))
                    .append("\n  ").append(RelayService.lastEventTitle);
        } else {
            sb.append("Aucun événement reçu depuis le démarrage.");
        }
        status.setText(sb.toString());
    }
}
