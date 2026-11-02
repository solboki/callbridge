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
            Log.w(TAG, "dossier introuvable : " + dir);
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
        Log.i(TAG, "service démarré, dossier : " + dir);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(poll);
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
                .setContentTitle("Callbridge actif")
                .setContentText("Relais des appels et SMS vers la montre")
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
                    lastEventAt = System.currentTimeMillis();
                    lastEventTitle = ev.optString("action") + " : "
                            + ev.optString("title", ev.optString("key"));
                }
            } catch (Exception e) {
                Log.w(TAG, "fichier illisible " + name + " : " + e);
            }
            remember(name);
            if (!f.delete()) {
                // Sailfish supprimera le fichier de lui-même au bout de deux minutes.
                Log.d(TAG, "suppression impossible : " + name);
            }
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
