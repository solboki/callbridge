package org.callbridge.relay;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Service de premier plan qui surveille le dossier d'échange
 * /sdcard/Callbridge (= ~/android_storage/Callbridge côté Sailfish).
 *
 * Deux mécanismes se complètent : FileObserver réagit tout de suite quand le
 * noyau signale l'arrivée d'un fichier, et une relecture toutes les deux
 * secondes rattrape les cas où le signal ne traverse pas la couche de
 * stockage du conteneur.
 */
public class RelayService extends Service {

    static final String TAG = "Callbridge";
    static final String DIR_NAME = "Callbridge";
    private static final long POLL_MS = 2000;
    private static final int FG_ID = 1;

    static volatile long lastEventAt = 0;
    static volatile String lastEventTitle = "";
    static volatile boolean running = false;

    /** Last events handled, newest first, for diagnosis: "15:31:02 call Jean (1.2 s)". */
    static final java.util.LinkedList<String> recent = new java.util.LinkedList<String>();

    static synchronized void addRecent(JSONObject ev) {
        String action = ev.optString("action");
        if ("command".equals(action)) {
            return;
        }
        long ts = ev.optLong("ts", 0);
        String delay = ts > 0
                ? String.format(java.util.Locale.ROOT, "%.1f s", (System.currentTimeMillis() - ts) / 1000.0)
                : "?";
        String what = "cancel".equals(action) ? "end" : ev.optString("kind");
        String title = ev.optString("title", "");
        if (title.length() > 24) {
            title = title.substring(0, 24) + "…";
        }
        recent.addFirst(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
                .format(new java.util.Date()) + " " + what
                + (title.length() > 0 ? " " + title : "") + " (" + delay + ")");
        while (recent.size() > 8) {
            recent.removeLast();
        }
    }

    static synchronized java.util.List<String> recentLines() {
        return new java.util.ArrayList<String>(recent);
    }

    private static final long STATE_MS = 30 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> done = new LinkedHashSet<String>();
    private FileObserver observer;
    private File dir;

    static File exchangeDir() {
        return new File(Environment.getExternalStorageDirectory(), DIR_NAME);
    }

    static void start(Context ctx) {
        Intent i = new Intent(ctx, RelayService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // startForegroundService existe depuis l'API 26 ; appel par
                // réflexion car l'application est compilée contre l'API 23.
                Context.class.getMethod("startForegroundService", Intent.class)
                        .invoke(ctx, i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            try {
                ctx.startService(i);
            } catch (Exception e2) {
                Log.e(TAG, "impossible de démarrer le service", e2);
            }
        }
    }

    private final Runnable stateWriter = new Runnable() {
        @Override
        public void run() {
            writeState(RelayService.this);
            handler.postDelayed(this, STATE_MS);
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            scan();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(FG_ID, buildOngoing());
        dir = exchangeDir();
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "folder not found: " + dir);
        }
        observer = new FileObserver(dir.getAbsolutePath(),
                FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
            @Override
            public void onEvent(int event, String path) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        scan();
                    }
                });
            }
        };
        observer.startWatching();
        handler.post(poll);
        running = true;
        handler.post(stateWriter);
        Log.i(TAG, "service started, folder: " + dir);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(stateWriter);
        if (observer != null) {
            observer.stopWatching();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildOngoing() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                0);
        return new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(L.t("Callbridge is running", "Callbridge actif"))
                .setContentText(L.t("Forwarding calls and SMS to your watch",
                        "Relais des appels et SMS vers la montre"))
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    /** Lit et applique les fichiers déposés, dans l'ordre chronologique. */
    private void scan() {
        String[] names = dir.list();
        if (names == null) {
            return;
        }
        Arrays.sort(names);
        for (String name : names) {
            if (name.startsWith(".") || !name.endsWith(".json") || done.contains(name)) {
                continue;
            }
            File f = new File(dir, name);
            try {
                JSONObject ev = new JSONObject(read(f));
                if (Notifier.handle(this, ev)) {
                    addRecent(ev);
                    writeState(this);
                    lastEventAt = System.currentTimeMillis();
                    lastEventTitle = ev.optString("action") + " : "
                            + ev.optString("title", ev.optString("key"));
                }
            } catch (Exception e) {
                Log.w(TAG, "unreadable file " + name + ": " + e);
            }
            remember(name);
            if (!f.delete()) {
                // The Sailfish service deletes stale files after two minutes anyway.
                Log.d(TAG, "cannot delete " + name);
            }
        }
    }

    // ------------------------------------------------------------------
    // Icon and state, driven by the Sailfish settings app
    // ------------------------------------------------------------------

    /**
     * Uninstall requested from the Sailfish settings app. Android only lets a
     * visible screen ask for an uninstall, so the request is stored here and
     * MainActivity shows Android's uninstall dialog when it opens.
     */
    static void requestUninstall(Context ctx) {
        ctx.getSharedPreferences("callbridge", Context.MODE_PRIVATE).edit()
                .putLong("uninstall_requested", System.currentTimeMillis()).apply();
    }

    /** True (once) if an uninstall was requested in the last two minutes. */
    static boolean takeUninstallRequest(Context ctx) {
        android.content.SharedPreferences p =
                ctx.getSharedPreferences("callbridge", Context.MODE_PRIVATE);
        long at = p.getLong("uninstall_requested", 0);
        p.edit().remove("uninstall_requested").apply();
        return at > 0 && System.currentTimeMillis() - at < 120 * 1000L;
    }

    private static boolean granted(Context ctx, String perm) {
        return ctx.checkCallingOrSelfPermission(perm)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Writes relay.state in the shared folder. The Sailfish settings app reads
     * it to show whether the Android side is alive and correctly set up.
     * The .state extension keeps it out of the event queue.
     */
    static void writeState(Context ctx) {
        try {
            JSONObject st = new JSONObject();
            st.put("version", ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName);
            st.put("running", running);
            st.put("notifications", Notifier.enabled(ctx));
            st.put("storage", granted(ctx, "android.permission.WRITE_EXTERNAL_STORAGE"));
            st.put("contacts", granted(ctx, "android.permission.READ_CONTACTS"));
            st.put("level", Notifier.level(ctx));
            st.put("last_event", lastEventAt);
            st.put("recent", new org.json.JSONArray(recentLines()));
            st.put("ts", System.currentTimeMillis());
            File d = exchangeDir();
            File tmp = new File(d, ".relay.state.tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            try {
                out.write(st.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            File dst = new File(d, "relay.state");
            if (!tmp.renameTo(dst)) {
                dst.delete();
                tmp.renameTo(dst);
            }
        } catch (Exception e) {
            Log.w(TAG, "cannot write state: " + e);
        }
    }

    private void remember(String name) {
        done.add(name);
        if (done.size() > 500) {
            Iterator<String> it = done.iterator();
            for (int i = 0; i < 100 && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
    }

    private static String read(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
