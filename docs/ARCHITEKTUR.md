# Architecture

```text
MainActivity / Termux TerminalView
  ↕ VT emulator, InputQueue, ByteQueue
TerminalService / SessionManager / multiple TerminalSession instances
  (Java, main thread and I/O threads per PTY)
  ↕ JNI
C++ PTY: /dev/ptmx → fork → setsid → TIOCSCTTY → execve
  ↕ controlling terminal / SIGWINCH
libproot.so → libproot-loader.so → Arch aarch64 / glibc → Bash / pacman
```

## PTY

`native/pty.cpp` creates the master/slave pair, sets UTF-8 and window size, disables XON/XOFF and starts a dedicated controlling terminal. A CLOEXEC pipe reports session setup and `execve` errors to Java. There is no timer. After `fork`, the child path uses only prepared data and async-signal-safe system calls. `waitpid` retries on EINTR and returns the exit code or a negative signal.

Emulation, rendering and callbacks run on Android's main thread. The reader, writer and waiter have separate duties. The reader and writer each have their own duplicated file descriptor, so a closing thread cannot close another thread's descriptor that has since been reused. Output is fully drained from the transport queue before the final exit callback. Work per main-thread iteration is bounded and continued later so large output does not starve input.

The display keeps 10,000 transcript lines. A 256 KiB output transport applies backpressure when rendering is slow. The input queue accepts up to 1 MiB of pending data without blocking its producer. A full queue is reported without closing the process or emulator.

`MainActivity` creates the embedded `TerminalView` in Java and explicitly sets `setFocusable(true)` and `setFocusableInTouchMode(true)`. The view constructor does not set these. Without touch focus, Android cannot select the console as its active input editor even with an `InputConnection`, and tapping does not open the on-screen keyboard. The Android regression test covers the path from tapping through the keyboard connection to the shell.

## Start screen

Since 0.1.6, the start screen checks the local installation marker before connecting to the service. The install button and download notices stay hidden during connection and console startup. Installation is offered only once the connected service confirms that it is missing. `TerminalService.startFailed()` distinguishes a failed start from ongoing preparation; an existing installation gets a retry action only on failure.

The 48 dp Arch logo is a decorative `ImageView` beside the start/install screen title. An `ObjectAnimator` changes only its opacity. It receives neither input nor accessibility focus. `render()` hides the entire start screen as soon as a session exists and stops the animation; PTY startup is not delayed. `onStop()` and loss of window focus also stop the animation and reset opacity. When Android animations are disabled the logo is static. There is no logo overlay or output in the terminal buffer.

## PRoot and environment

PRoot and its loader reside in the extracted native APK directory. `LaunchSpec` passes arguments as an array without shell quoting. The guest filesystem is `files/arch`; `/dev`, `/proc` and `/sys` are bound. `/dev/shm` maps to a private writable directory. DNS servers come from Android's active network where possible; otherwise 1.1.1.1 and 9.9.9.9 are used. The DNS file is generated at session start.

`PROOT_LOADER` and `PROOT_TMP_DIR` point to app-owned paths. The Android host's `LD_PRELOAD` and `LD_LIBRARY_PATH` are not passed to Arch. `/usr/bin/env -i` sets HOME, PATH, TERM, locale and other basic shell values for the guest. `--kill-on-exit` cleans up guest processes when the shell exits. Since 0.1.8 explicit stop sends SIGQUIT to the tracer. The pinned PRoot ignores SIGTERM; only its SIGQUIT path cleans up all tracees and makes the event loop wait for them, including detached processes. The generic terminal library allows the process owner to choose the signal; ordinary shell tests retain their previous SIGTERM default. This still needs testing on Android manufacturer kernels.

PRoot is a translation layer requiring neither root nor its own kernel. systemd as PID 1, real mounts, kernel modules and real Linux capabilities are unavailable. Guest root remains the Android app UID. PRoot is not an additional security boundary against programs the user runs in the app.

## Installation

`RootfsInstaller` downloads the pinned archive with range resumption and checks its size and SHA-256. Changes to the official image are additionally verified against its GPG signature on the development machine. The app trusts the lock shipped with the APK; it does not download a new hash.

The C++ extractor uses libarchive/gzip. It accepts relative paths, rejects device/proc/sys contents, creates files without following symlinks and preserves executable modes. Symlinks are created after files and hardlinks. Absolute symlink targets are valid for the eventual guest but are not traversed during extraction. Path traversal, duplicate regular files and hardlinks escaping the destination are rejected.

Since 0.1.1 archive hardlinks are stored as independent regular files with identical contents and executable permissions. Android app SELinux domains prohibit real hardlinks even with target 28, so the installer uses neither `link` nor `linkat`. Forward references and hardlink chains are resolved before symlinks; missing or cyclic targets cause an error. Extracted files do not share an inode. PRoot's `--link2symlink` remains active for later guest package operations. [AOSP rule](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/app_neverallows.te).

Since 0.1.3 directories receive their archive modes, including sticky/setgid bits, after all contents and links are complete. Until then they remain writable to the extractor. Children are finalized before parents; Android's umask does not alter final permissions. Necessary parent/runtime directories not described in the archive receive 0755. Cleanup of a failed staging directory restores write permission for deletion and does not follow symlinks.

The pinned libarchive tar reader receives a targeted ACL fix during source preparation. PAX ACLs read before the actual tar header must not override its full mode: otherwise `/var/log/journal` loses its setgid bit, and a default-only ACL on `/var/lib/tpm2-tss/system/keystore` produces 0000. The fixed reader preserves tar modes 2755 and 2775 respectively. The patch is applied idempotently to hash-verified sources and rejects unexpected source versions. Host tests and Android builds use identical sources. Host ACLs and foreign user/group mappings are not applied.

Before the first session of an older installation, libarchive reads compressed mtree files from the local pacman database. It corrects only existing package-described directories with a known old mkdir mode. Regular files, symlink paths, /dev/proc/sys contents, custom modes and conflicting package data are skipped. A marker prevents further successful repair runs; new installations do not need this migration.

Since 0.1.4 this maintenance is separate from mandatory console startup. Java I/O errors and native repair errors produce a notice in Logcat and the started console. Without a success marker, retry remains possible. Creating required runtime/DNS files and successfully starting the PTY process are still required for a session.

Since 0.2.0 pacman uses `ca.us.mirror.archlinuxarm.org` over HTTPS followed by RWTH Aachen over HTTPS. A separate `.arch-terminal-mirrors-v2` marker separates mirror migration from completed directory repair. Only the unchanged active Princeton line or former default RWTH/dotsrc list is replaced; comments and different whitespace are recognized, and custom lists and symlinks are preserved. After migration, `pacman -Syyu` refreshes repository databases and performs a full system update. The shell notice names the command; the app does not start a package transaction automatically. The separately pinned bootstrap archive keeps its verified download lock. `scripts/test-mirrors.py` checks all four databases and their current package files and signatures sequentially.

The language choice is stored under `terminal/language` in SharedPreferences. `AppLanguage` creates a German or English resource context for the Activity; the service resolves status resources using the current preference each time they are displayed. Switching recreates the Activity and updates the notification while the service retains the same sessions. `LaunchSpec` sets `ARCH_TERMINAL_LANGUAGE` and `LANGUAGE` for new guests; English shells also use `LC_ALL=C.UTF-8`, avoiding generated locales. Existing app notices in regular Bash profiles become bilingual through precisely bounded replacements; other content and symlinks remain unchanged.

A separate `arch.installing` holds all changes until completion. Installation checks Bash, pacman and the package database, writes shell profiles and appropriate pacman options, and activates the directory by atomic rename. Package signature requirements remain enabled. `CheckSpace` is disabled because there is no real mount table; `DisableSandbox` accounts for missing Landlock/seccomp capabilities. The keyring is initialized at first interactive login. A failure leaves the shell open and displays a repair command.

Download and extraction can be canceled. The extractor checks an atomic cancellation signal between archive entries and when writing large files. Network disconnection happens off the main thread. A partial archive is retained for retry; an interrupted staging directory is recreated on the next attempt.

## Lifecycle

The Activity binds the service but does not own the processes. Rotation is handled by configuration handling and PTY resize; rebinding the Activity uses the same sessions and selection. Each Activity unregisters only its own listener so a late `onStop()` cannot unregister a newly connected Activity. Switching screens or apps does not terminate sessions. During installation or while at least one session is running, the started foreground service can hold a CPU wake lock without an expiry. It is released only when the last running session ends or the service is destroyed. The service is not exported. After actual Android process termination the app does not pretend a shell is still running or repeat a user command automatically.

## Multiple sessions

Since 0.1.7, `SessionManager<T>` maintains an ordered list with stable terminal numbers and a selected session on the main thread. Its backend encapsulates creation, running state and stop, allowing selection and lifecycle tests without Android or JNI. A new PTY is created before the list changes. If it fails, existing terminals and selection remain. Restarting an exited tab replaces only its terminal at the same number and position; late callbacks from the old terminal are discarded by identity.

Since 0.1.9 each entry also records whether its final exit callback has arrived. `TerminalSession.isRunning()` becomes false after `waitpid`, while the PTY reader and renderer may still be processing final output. Closing and stopping all therefore retain the entry until the callback. Restart is possible only afterward so the old session's final output is fully processed.

Each `TerminalSession` has its own PTY process, VT emulator, scrollback and separate I/O queues/threads. The Arch installation, including files and package database, is shared. `TerminalService` holds all sessions; only the selected session is bound to the single `TerminalView`. Background output is still processed in its own emulator but does not cause unnecessary redraws of the visible console. On switching, the Activity resets latched Ctrl/Alt state and focuses the console.

`SessionBar` encapsulates horizontal tab navigation and the always-available plus action. Tabs show the terminal number, optional shell title and exit/closing state. Their views are rebuilt only when the session list changes. The bar remains available independently of optional extra keys. Closing an individual session requires confirmation and terminates only its process. A closing PTY remains managed until its exit callback and full output drain; afterward a neighboring tab is selected if needed. Naturally exited sessions keep their scrollback until closed or restarted. Stopping all or destroying the service also closes background sessions.

Once no open tabs remain, the service enters the stop state; since 0.1.9 already-closing entries do not count as open. This also applies when multiple tabs are closed individually before their exit callbacks. After the last closing session's callback, the service removes its foreground notification and releases the wake lock, even if an Activity or test client is still bound. It does not publish a new notification while in this completed stop state. The Activity can then finish and unbind; the service stops without restarting sessions. Naturally exited tabs that have not been closed retain their scrollback.
