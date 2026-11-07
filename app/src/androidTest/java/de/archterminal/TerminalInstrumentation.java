package de.archterminal;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ImageView;
import android.widget.TextView;
import com.termux.view.TerminalView;
import com.termux.terminal.TerminalSession;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Runs on real ARM64 Android. Every deadline here belongs only to the test harness. */
public final class TerminalInstrumentation extends Instrumentation {
    private Bundle arguments;
    private final StringBuilder passed = new StringBuilder();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            testExtraction();
            testUpgradeFailureDoesNotBlockShell();
            testPty();
            testTail();
            testInterrupt();
            if ("true".equals(arguments.getString("rootfs"))) testArch();
            testStartupLogo();
            testKeyboard();
            result.putString("stream", "\nPASS\n" + passed);
            result.putBoolean("archterminal.success", true);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + error + "\nPassed:\n" + passed);
            result.putBoolean("archterminal.success", false);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void testExtraction() throws Exception {
        Path test = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "arch-extract-test-");
        try {
            Path archive = test.resolve("rootfs.tar.gz");
            // The Android asset merger transparently expands files ending in .gz; .tgz keeps this fixture compressed.
            try (InputStream input = getContext().getAssets().open("extractor-hardlinks.tgz")) { Files.copy(input, archive); }
            Path root = Files.createDirectory(test.resolve("root"));
            Native.resetExtractionCancellation();
            Native.extract(archive.toString(), root.toString());
            byte[] expected = "#!/bin/sh\nprintf 'hardlink copy works\\n'\n".getBytes(StandardCharsets.UTF_8);
            long sourceInode = android.system.Os.stat(root.resolve("usr/bin/source").toString()).st_ino;
            for (String name : new String[]{"alias", "forward-alias"}) {
                Path copy = root.resolve("usr/bin/" + name);
                if (Files.isSymbolicLink(copy) || !Arrays.equals(expected, Files.readAllBytes(copy)) || !copy.toFile().canExecute())
                    throw new AssertionError("Archive hardlink copy lost contents or execute mode: " + name);
                if (android.system.Os.stat(copy.toString()).st_ino == sourceInode)
                    throw new AssertionError("Extractor created a real hardlink: " + name);
            }
            Files.delete(root.resolve("usr/bin/source"));
            if (!Arrays.equals(expected, Files.readAllBytes(root.resolve("usr/bin/alias"))))
                throw new AssertionError("Hardlink copy depends on original path");
            passed.append("Android rootfs extraction: hardlink copies, forward chain, executable modes\n");
        } finally { RootfsInstaller.deleteTree(test); }
    }
    private void testUpgradeFailureDoesNotBlockShell() throws Exception {
        Path files = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "arch-upgrade-test-");
        try {
            Path root = files.resolve("arch");
            Path packageDirectory = Files.createDirectories(root.resolve("var/lib/pacman/local/broken-1"));
            // Truncated gzip: the actual JNI repair must reject this optional package metadata.
            Files.write(packageDirectory.resolve("mtree"), new byte[]{0x1f, (byte) 0x8b, 0x08});
            Path mirror = root.resolve("etc/pacman.d/mirrorlist");
            Files.createDirectories(mirror.getParent());
            byte[] customMirror = "Server = https://chosen.example/$arch/$repo\n".getBytes(StandardCharsets.UTF_8);
            Files.write(mirror, customMirror);
            boolean rejected = false;
            try { Native.repairDirectoryModes(root.toString()); }
            catch (IllegalStateException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Invalid mtree fixture did not fail native repair");
            Context isolated = new ContextWrapper(getTargetContext()) {
                @Override public File getFilesDir() { return files.toFile(); }
            };
            // Reflection also runs this unchanged test APK against 0.1.3's void-returning method.
            Method prepare = RuntimeFiles.class.getDeclaredMethod("prepare", Context.class);
            prepare.setAccessible(true);
            try { prepare.invoke(null, isolated); }
            catch (java.lang.reflect.InvocationTargetException error) {
                throw new AssertionError("Optional rootfs repair blocked shell startup: " + error.getCause(), error.getCause());
            }
            if (!Files.isRegularFile(files.resolve("runtime/resolv.conf"))) throw new AssertionError("Runtime DNS file missing after failed repair");
            if (Files.exists(root.resolve(RootfsConfig.DIRECTORY_MODES_MARKER))) throw new AssertionError("Failed repair marked successful");
            if (!Arrays.equals(customMirror, Files.readAllBytes(mirror))) throw new AssertionError("Custom mirror changed");
            Probe probe = shell("printf 'UPGRADE_FAILURE_SHELL_OK\\n'");
            probe.awaitExit();
            if (!probe.text.contains("UPGRADE_FAILURE_SHELL_OK")) throw new AssertionError("Shell did not run after failed repair");
            passed.append("Optional upgrade failure: actual JNI, runtime preparation, working shell, preserved mirror and retry\n");
        } finally { RootfsInstaller.deleteTree(files); }
    }
    private final class Probe extends SessionCallbacks {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile String text = "";
        volatile int exit = Integer.MIN_VALUE;
        TerminalSession session;
        @Override public void onTextChanged(TerminalSession session) { text = session.getEmulator().getScreen().getTranscriptText(); }
        @Override public void onSessionFinished(TerminalSession session) { text = session.getEmulator().getScreen().getTranscriptText(); exit = session.getExitStatus(); finished.countDown(); }
        void awaitText(String expected) throws Exception {
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (!text.contains(expected)) {
                if (android.os.SystemClock.elapsedRealtime() > deadline) throw new AssertionError("Missing output: " + expected + "\n" + text);
                Thread.sleep(20);
            }
        }
        void awaitExit() throws Exception {
            if (!finished.await(30, TimeUnit.SECONDS)) { runOnMainSync(session::finishIfRunning); throw new AssertionError("Device test did not finish\n" + text); }
            if (exit != 0) throw new AssertionError("Process exit " + exit + "\n" + text);
        }
    }
    private Probe shell(String command) {
        Probe probe = new Probe();
        runOnMainSync(() -> {
            probe.session = new TerminalSession("/system/bin/sh", getTargetContext().getFilesDir().getAbsolutePath(),
                new String[]{"sh", "-c", command}, new String[]{"PATH=/system/bin", "TERM=xterm-256color"}, 10000, probe);
            probe.session.updateSize(80, 24, 0, 0);
        });
        return probe;
    }
    private void testPty() throws Exception {
        Probe probe = shell("test -t 0 && test -t 1 && test -t 2 || exit 9; printf 'READY\\n'; read line; stty size; printf '<%s>DONE\\n' \"$line\"");
        probe.awaitText("READY");
        Thread.sleep(600);
        AtomicBoolean running = new AtomicBoolean();
        runOnMainSync(() -> { running.set(probe.session.isRunning()); probe.session.updateSize(101, 47, 0, 0); probe.session.write("Grüße\n"); });
        if (!running.get()) throw new AssertionError("Idle PTY was closed");
        probe.awaitExit();
        if (!probe.text.contains("47 101") || !probe.text.contains("<Grüße>DONE")) throw new AssertionError("PTY UTF-8/resize failure\n" + probe.text);
        passed.append("Android C++ PTY, TTY, resize, UTF-8, idle\n");
    }
    private void testTail() throws Exception {
        Probe probe = shell("head -c 262144 /dev/zero | tr '\\000' x; printf '\\r\\nFINAL_MARKER\\n'");
        probe.awaitExit();
        if (!probe.text.contains("FINAL_MARKER")) throw new AssertionError("Final output was lost on exit");
        passed.append("256 KiB output, exit drain\n");
    }
    private void testInterrupt() throws Exception {
        Probe probe = shell("trap 'echo INTERRUPTED; exit 0' INT; echo READY; while :; do sleep 1; done");
        probe.awaitText("READY");
        runOnMainSync(() -> probe.session.write("\003"));
        probe.awaitExit();
        if (!probe.text.contains("INTERRUPTED")) throw new AssertionError("Ctrl-C failed");
        passed.append("Ctrl-C delivery\n");
    }
    private void testStartupLogo() throws Exception {
        Probe probe = shell("exec /system/bin/sh");
        Activity activity = null;
        TerminalService owner = null;
        TerminalSession previous = null;
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent);
            Activity current = activity;
            awaitUi(() -> field(current, "service") != null && current.hasWindowFocus(), "Logo Activity did not connect");
            owner = (TerminalService) field(current, "service");
            previous = owner.session();
            TerminalService connected = owner;
            // Display setup without changing the service's session or any installed Arch.
            runOnMainSync(() -> {
                connected.listen(null);
                field(current, "service", null);
                field(current, "attached", null);
                render(current);
            });
            testSetupActions(current);
            ImageView logo = (ImageView) field(current, "startupLogo");
            ObjectAnimator pulse = (ObjectAnimator) field(current, "logoPulse");
            awaitUi(() -> logo.isShown() && logo.getWidth() > 0, "Startup logo was not visible");
            runOnMainSync(() -> {
                if (logo.getDrawable() == null || logo.isFocusable() || logo.isClickable()
                    || logo.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_NO)
                    throw new AssertionError("Startup decoration can intercept input or accessibility focus");
                if (((View) field(current, "terminal")).isShown()) throw new AssertionError("Setup overlaps the terminal");
            });
            boolean animationsEnabled = ValueAnimator.areAnimatorsEnabled();
            if (animationsEnabled) awaitUi(() -> pulse.isRunning() && logo.getAlpha() < 0.9f, "Startup logo did not pulse");
            else awaitUi(() -> !pulse.isStarted() && logo.getAlpha() == 1f, "Disabled animations were not respected");
            if (!getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
                throw new AssertionError("Android did not accept the global Home action");
            awaitUi(() -> !current.hasWindowFocus(), "Global Home action did not put the Activity in background");
            awaitUi(() -> !pulse.isStarted() && logo.getAlpha() == 1f, "Startup animation kept running in background");
            getTargetContext().startActivity(intent);
            awaitUi(() -> field(current, "service") != null && current.hasWindowFocus(), "Logo Activity did not resume");
            runOnMainSync(() -> {
                connected.listen(null);
                field(current, "service", null);
                field(current, "attached", null);
                render(current);
            });
            if (animationsEnabled) awaitUi(() -> pulse.isRunning() && logo.getAlpha() < 0.9f, "Startup animation did not resume");
            runOnMainSync(() -> {
                field(connected, "session", probe.session);
                field(current, "service", connected);
                render(current);
            });
            TerminalView terminal = (TerminalView) field(current, "terminal");
            awaitUi(() -> terminal.isShown() && terminal.hasFocus() && !logo.isShown()
                && !pulse.isStarted() && logo.getAlpha() == 1f, "Startup logo interferes with the terminal");
            passed.append("Startup logo: visible pulse, decorative focus, background stop/resume, immediate terminal handoff\n");
        } finally {
            Activity current = activity;
            TerminalService connected = owner;
            TerminalSession saved = previous;
            runOnMainSync(() -> {
                if (connected != null) field(connected, "session", saved);
                if (current != null) {
                    field(current, "service", connected);
                    field(current, "attached", null);
                    if (connected != null) render(current);
                    current.finish();
                }
                probe.session.finishIfRunning();
            });
        }
    }
    private void testKeyboard() throws Exception {
        Probe probe = shell("printf 'KEYBOARD_READY\\n'; exec /system/bin/sh");
        Activity activity = null;
        TerminalService owner = null;
        TerminalSession previous = null;
        try {
            probe.awaitText("KEYBOARD_READY");
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity current = activity;
            awaitUi(() -> field(current, "service") != null && current.hasWindowFocus(), "Activity did not connect and gain window focus");
            owner = (TerminalService) field(current, "service");
            previous = owner.session();
            TerminalService connected = owner;
            // Exercise the real Activity with a disposable Android shell; no Arch download is needed.
            runOnMainSync(() -> { field(connected, "session", probe.session); render(current); });
            TerminalView terminal = (TerminalView) field(current, "terminal");
            awaitUi(() -> terminal.isShown() && terminal.getWidth() > 0, "Terminal was not laid out");
            runOnMainSync(() -> {
                if (((View) field(current, "startupLogo")).isShown() || ((ObjectAnimator) field(current, "logoPulse")).isStarted())
                    throw new AssertionError("Startup logo is still active during command input");
            });
            setInTouchMode(true);
            runOnMainSync(() -> current.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(terminal.getWindowToken(), 0));
            awaitUi(() -> !keyboardVisible(terminal), "Keyboard did not close before tap test");
            tap(terminal);
            awaitUi(() -> keyboardVisible(terminal), "Tap did not open keyboard: focusable=" + terminal.isFocusable()
                + ", focusableInTouchMode=" + terminal.isFocusableInTouchMode());
            awaitUi(() -> terminal.hasFocus() && current.getSystemService(android.view.inputmethod.InputMethodManager.class).isActive(terminal),
                "Terminal did not become the active input editor");
            runOnMainSync(() -> {
                InputConnection input = terminal.onCreateInputConnection(new EditorInfo());
                if (input == null || !input.commitText("printf 'IME_%s\\n' 'INPUT_OK'\n", 1))
                    throw new AssertionError("IME text was not accepted");
            });
            probe.awaitText("IME_INPUT_OK");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            awaitUi(() -> !keyboardVisible(terminal), "Back did not hide keyboard");
            tap(terminal);
            awaitUi(() -> keyboardVisible(terminal), "Tap did not reopen keyboard");
            keyboardMenu(current);
            awaitUi(() -> !keyboardVisible(terminal), "Menu did not hide keyboard");
            keyboardMenu(current);
            awaitUi(() -> keyboardVisible(terminal), "Menu did not reopen keyboard");
            passed.append("Keyboard: touch focus, tap/open, IME command through PTY, Back/reopen, menu hide/show\n");
        } finally {
            Activity current = activity;
            TerminalService connected = owner;
            TerminalSession saved = previous;
            runOnMainSync(() -> {
                if (connected != null) { field(connected, "session", saved); render(current); }
                if (current != null) current.finish();
                probe.session.finishIfRunning();
            });
        }
    }
    private void testSetupActions(Activity activity) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try {
                View action = (View) field(activity, "action");
                View cancel = (View) field(activity, "cancel");
                TextView message = (TextView) field(activity, "message");
                if (action.getVisibility() != View.GONE || message.getText().toString().contains("791 MiB"))
                    throw new AssertionError("Unconnected Activity offers Arch installation before checking the existing rootfs");
                setup(activity, false, true, false, false, null);
                if (action.getVisibility() != View.GONE || !message.getText().toString().equals("Konsole wird gestartet …"))
                    throw new AssertionError("Installed Arch shows an install action while connecting");
                setup(activity, true, true, false, false, "Arch Linux ARM ist bereit zur Installation.");
                if (action.getVisibility() != View.GONE || message.getText().toString().contains("Installation"))
                    throw new AssertionError("Installed Arch shows the service's initial installation message");
                setup(activity, true, true, false, true, "Start fehlgeschlagen: Testfehler");
                if (action.getVisibility() != View.VISIBLE || !((TextView) action).getText().toString().equals("Konsole erneut starten")
                    || !message.getText().toString().contains("Testfehler") || message.getText().toString().contains("791 MiB"))
                    throw new AssertionError("Failed console start offers installation instead of a retry");
                setup(activity, false, false, false, false, null);
                if (action.getVisibility() != View.GONE || message.getText().toString().contains("791 MiB"))
                    throw new AssertionError("Installation offered before the service connected");
                setup(activity, true, false, false, false, "Arch fehlt.");
                if (action.getVisibility() != View.VISIBLE || !((TextView) action).getText().toString().equals("Arch installieren / fortsetzen")
                    || !message.getText().toString().contains("791 MiB"))
                    throw new AssertionError("Missing Arch cannot be installed after the service connected");
                setup(activity, true, false, true, false, "Arch wird geladen …");
                if (action.getVisibility() != View.GONE || cancel.getVisibility() != View.VISIBLE
                    || !message.getText().toString().equals("Arch wird geladen …"))
                    throw new AssertionError("Active installation shows a duplicate install action");
                render(activity);
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Setup UI check failed: " + failure.get(), failure.get());
        passed.append("Setup actions: no install while connecting/installed, console retry on failure, install only for missing Arch\n");
    }
    private static void setup(Activity activity, boolean connected, boolean ready, boolean installing, boolean failed, String status) {
        try {
            Method render = MainActivity.class.getDeclaredMethod("renderSetup", boolean.class, boolean.class, boolean.class, boolean.class, String.class);
            render.setAccessible(true);
            render.invoke(activity, connected, ready, installing, failed, status);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private void keyboardMenu(Activity activity) throws Exception {
        // With animations disabled the IME can close before the previous dialog returns focus.
        awaitUi(activity::hasWindowFocus, "Activity did not regain focus before the menu key");
        sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> items = root.findAccessibilityNodeInfosByText("Tastatur ein / aus");
                if (!items.isEmpty()) {
                    Rect bounds = new Rect();
                    items.get(0).getBoundsInScreen(bounds);
                    tap(bounds.centerX(), bounds.centerY());
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Keyboard menu item was not available");
    }
    private void tap(View view) {
        int[] position = new int[2];
        runOnMainSync(() -> { view.getLocationOnScreen(position); position[0] += view.getWidth() / 2; position[1] += Math.min(view.getHeight() / 2, 100); });
        tap(position[0], position[1]);
    }
    private void tap(int x, int y) {
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now + (action == MotionEvent.ACTION_UP ? 40 : 0), action, x, y, 0);
            sendPointerSync(event);
            event.recycle();
        }
        waitForIdleSync();
    }
    private boolean keyboardVisible(View view) {
        WindowInsets insets = view.getRootWindowInsets();
        if (Build.VERSION.SDK_INT >= 30) return insets != null && insets.isVisible(WindowInsets.Type.ime());
        float threshold = 120 * view.getResources().getDisplayMetrics().density;
        if (insets != null && insets.getSystemWindowInsetBottom() - insets.getStableInsetBottom() > threshold) return true;
        Rect visible = new Rect();
        view.getWindowVisibleDisplayFrame(visible);
        return view.getRootView().getHeight() - visible.bottom > threshold;
    }
    private void awaitUi(BooleanSupplier condition, String failure) throws Exception {
        AtomicBoolean result = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 10000;
        do {
            runOnMainSync(() -> result.set(condition.getAsBoolean()));
            if (result.get()) return;
            Thread.sleep(20);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError(failure);
    }
    private static Object field(Object target, String name) { return field(target, name, null, false); }
    private static void field(Object target, String name, Object value) { field(target, name, value, true); }
    private static Object field(Object target, String name, Object value, boolean write) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            if (write) field.set(target, value);
            return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void render(Activity activity) {
        try {
            Method render = MainActivity.class.getDeclaredMethod("render");
            render.setAccessible(true);
            render.invoke(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private void testArch() throws Exception {
        if (!RootfsInstaller.installed(getTargetContext())) throw new AssertionError("Install Arch in the app before --rootfs device tests");
        RuntimeFiles.prepare(getTargetContext());
        Probe probe = new Probe();
        LaunchSpec launch = new LaunchSpec(getTargetContext().getFilesDir(), new File(getTargetContext().getApplicationInfo().nativeLibraryDir), true,
            "/bin/sh", "-c", "test -t 0 && uname -m && pacman --version && pacman -Q bash pacman && printf 'ARCH_PACMAN_OK\\n'");
        runOnMainSync(() -> {
            probe.session = new TerminalSession(launch.executable, launch.cwd, launch.arguments, launch.environment, 10000, probe);
            probe.session.updateSize(80, 24, 0, 0);
        });
        probe.awaitExit();
        if (!probe.text.contains("aarch64") || !probe.text.contains("ARCH_PACMAN_OK")) throw new AssertionError("ARM64 pacman failed\n" + probe.text);
        passed.append("Android PRoot, glibc loader, Arch ARM64, pacman query\n");
    }
}
