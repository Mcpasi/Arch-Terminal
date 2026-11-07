package de.archterminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;
import com.termux.terminal.TerminalSession;
import java.io.File;

/** Owns the PTY independently of the Activity. No idle, output, or command deadline. */
public final class TerminalService extends Service {
    interface Listener { void changed(); }
    final class LocalBinder extends Binder { TerminalService service() { return TerminalService.this; } }
    private final LocalBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final String CHANNEL = "arch-terminal", STOP = "de.archterminal.STOP";
    private Listener listener;
    private TerminalSession session;
    private RootfsInstaller installer;
    private PowerManager.WakeLock wake;
    private String status = "Arch Linux ARM ist bereit zur Installation.";
    private boolean installing, stopping, destroyed, startFailed;

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Arch-Konsole", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notification());
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArchTerminal:PTY");
        wake.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) stopSession();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    void listen(Listener listener) { this.listener = listener; if (listener != null) listener.changed(); }
    TerminalSession session() { return session; }
    String status() { return status; }
    boolean installing() { return installing; }
    boolean startFailed() { return startFailed; }
    boolean ready() { return RootfsInstaller.installed(this); }
    private void changed() {
        if (destroyed) return;
        getSystemService(NotificationManager.class).notify(1, notification());
        if (listener != null) listener.changed();
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, TerminalService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle("Arch Terminal").setContentText(status).setContentIntent(open)
            .setOngoing(true).addAction(new Notification.Action.Builder(null, "Beenden", stop).build()).build();
    }
    void install() {
        if (installing || ready()) return;
        installing = true;
        status = "Arch-Download wird vorbereitet …";
        acquireWake();
        installer = new RootfsInstaller(this, text -> main.post(() -> { status = text; changed(); }));
        RootfsInstaller current = installer;
        changed();
        new Thread(() -> {
            String failure = null;
            try { current.install(); }
            catch (Exception error) { failure = error.getMessage() == null ? error.toString() : error.getMessage(); }
            final String result = failure;
            main.post(() -> {
                installing = false;
                installer = null;
                status = result == null ? "Arch installiert. Konsole starten." : result;
                releaseWake();
                if (!destroyed && result == null) startSession();
                changed();
            });
        }, "arch-install").start();
    }
    void cancelInstall() { if (installer != null) { installer.cancel(); status = "Installation wird angehalten …"; changed(); } }
    void startSession() {
        if (installing || !ready() || (session != null && session.isRunning())) return;
        stopping = false;
        startFailed = false;
        try {
            String upgradeWarning = RuntimeFiles.prepare(this);
            boolean compatible = getSharedPreferences("terminal", MODE_PRIVATE).getBoolean("no_seccomp", false);
            LaunchSpec launch = new LaunchSpec(getFilesDir(), new File(getApplicationInfo().nativeLibraryDir), compatible);
            TerminalSession fresh = new TerminalSession(launch.executable, launch.cwd, launch.arguments, launch.environment, 10000, callbacks);
            fresh.updateSize(80, 24, 0, 0);
            if (upgradeWarning != null) {
                byte[] notice = ("Hinweis: " + upgradeWarning + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                fresh.getEmulator().append(notice, notice.length);
            }
            session = fresh;
            status = "Arch läuft · aarch64";
            acquireWake();
        } catch (Exception error) {
            startFailed = true;
            status = "Start fehlgeschlagen: " + error.getMessage() + " · Erneut versuchen oder Kompatibilitätsmodus wählen.";
            Toast.makeText(this, status, Toast.LENGTH_LONG).show();
            releaseWake();
        }
        changed();
    }
    void stopSession() {
        if (installing) { cancelInstall(); return; }
        if (session != null && session.isRunning()) {
            stopping = true;
            status = "Sitzung wird beendet …";
            session.finishIfRunning();
            changed();
        } else stopSelf();
    }
    void setWakeEnabled(boolean value) {
        getSharedPreferences("terminal", MODE_PRIVATE).edit().putBoolean("wake", value).apply();
        if (value && (installing || (session != null && session.isRunning()))) acquireWake(); else releaseWake();
    }
    boolean wakeEnabled() { return getSharedPreferences("terminal", MODE_PRIVATE).getBoolean("wake", true); }
    private void acquireWake() { if (wakeEnabled() && !wake.isHeld()) wake.acquire(); }
    private void releaseWake() { if (wake != null && wake.isHeld()) wake.release(); }
    private final SessionCallbacks callbacks = new SessionCallbacks() {
        @Override public void onTextChanged(TerminalSession changed) { if (listener != null) listener.changed(); }
        @Override public void onTitleChanged(TerminalSession changed) { if (listener != null) listener.changed(); }
        @Override public void onColorsChanged(TerminalSession changed) { if (listener != null) listener.changed(); }
        @Override public void onSessionFinished(TerminalSession ended) {
            if (session != ended) return;
            status = "Sitzung beendet · Status " + ended.getExitStatus() + " · Enter startet neu";
            releaseWake();
            changed();
            if (stopping) stopSelf();
        }
        @Override public void onCopyTextToClipboard(TerminalSession changed, String text) {
            // Only explicit user selection copies text, not program-generated OSC 52 sequences.
        }
        @Override public void logWarn(String tag, String message) {
            super.logWarn(tag, message);
            if (message.contains("Eingabepuffer voll")) Toast.makeText(TerminalService.this, message, Toast.LENGTH_LONG).show();
        }
    };
    @Override public void onDestroy() {
        destroyed = true;
        listener = null;
        cancelInstall();
        if (session != null) session.finishIfRunning();
        releaseWake();
        super.onDestroy();
    }
}
