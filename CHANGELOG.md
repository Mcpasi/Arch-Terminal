# Changelog

## 0.2.0 · versionCode 11

- Changed pacman's default to the direct HTTPS mirror `ca.us.mirror.archlinuxarm.org`, with RWTH Aachen over HTTPS as a fallback. A separate migration also handles the old RWTH/dotsrc list after directory repair has completed; custom lists and symlinks are preserved. The console recommends `pacman -Syyu` to refresh stale package databases.
- Added a persistent **Deutsch / English** choice to the start screen and console menu. The interface, installation, notifications, process-exit and input notices, and managed shell text can be translated. Switching languages preserves running PTYs and their state; new English shells use `C.UTF-8` without a language pack.
- Made exactly recognized older app messages in regular shell profiles translatable through idempotent replacements; custom content and profile symlinks are preserved.
- Added regression tests for existing installations, custom configurations, Bash syntax, both shell languages, guest environment, download language and complete translations. Android instrumentation checks selection, persistence, Activity recreation, a running PTY, notifications and the English exit notice. A separate sequential network test checks both mirrors, including referenced package files and signatures.
- Updated version, APK/source archive names, device test script and license notice to 0.2.0. Tests and builds remain sequential and limited to one CPU core and worker.

## 0.1.9 · versionCode 10

- Fixed sessions being removed or replaced between process exit and complete PTY output drain. The session manager records the final exit callback; closing and stopping all retain the entry until then, and restart is only possible afterward.
- Fixed service shutdown when all tabs are closed in quick succession. Closing tabs no longer count as open; after the last exit callback the foreground notification and wake lock disappear and a bound Activity closes.
- Reproduced both bugs before their fixes using JUnit and real Android PTYs with controlled delayed final output, and overlapping close requests with real PRoot and its production stop backend. Regression tests cover closing, restarting and shared cleanup.
- Updated version, APK/source archive names, device test script and license notice to 0.1.9. Aligned documentation on multiple sessions, completion timing and existing hardware feedback. Tests and builds run sequentially on one CPU core and worker.

## 0.1.8 · versionCode 9

- Fixed stopping from the menu and Android notification: pinned PRoot ignores SIGTERM. The service now sends SIGQUIT, causing PRoot's cleanup to terminate and wait for all tracees, including detached processes. The generic terminal library lets the process owner choose the termination signal.
- Explicitly remove the wake lock and foreground notification after the last session even if the service is still bound. Late updates and repeated stop actions do not recreate an empty notification.
- Android regressions use real PRoot and the production stop backend: one or the last session via the menu, notification PendingIntent in the background, two parallel sessions, detached children and complete notification/wake-lock release.
- Updated app version, APK/source archive names and device test script to 0.1.8. Tests and builds remain limited to one worker.

## 0.1.7 · versionCode 8

- Added multiple independent terminal sessions in the same Arch installation. The top session bar and **New session** open additional PTYs; tabs switch between shells with their own state and scrollback. Navigation remains available when extra keys are hidden.
- Implemented session management as the Android-independent `SessionManager` module and display as a separate `SessionBar`. A failed new session preserves existing sessions and selection; Enter replaces only an exited tab.
- Individual sessions can be closed after confirmation by long-pressing a tab or using the menu. Other processes keep running. The menu and notification also offer stopping all sessions. Wake locks and the service lifecycle account for all sessions and delayed exit callbacks.
- Added JUnit coverage for selection, independent state, start/restart failures, stable numbering, isolated close, natural exit, shared cleanup and delayed callbacks. Android instrumentation checks multiple real PTYs, IME, tabs, background/return, scrollback and wake locks.
- Updated version and APK/source archive names to 0.1.7. Gradle and native builds default to one worker; Java runtimes see one processor and use serial garbage collection, and CTest runs serially.

## 0.1.6 · versionCode 7

- Fixed the unnecessary installation screen when starting an existing Arch installation. The installation marker is checked before the service connects; no install button or download notice appears while disconnected.
- An existing installation shows a console startup message. An actual start failure retains its error text and offers a retry. The service's initial installation status is no longer displayed as a startup message.
- Added UI regressions for connection, existing/missing Arch, failed console startup and installation in progress. Logo, keyboard input and memory-saving build/emulator settings remain in place.

## 0.1.5 · versionCode 6

- Added a small, subtly pulsing Arch Linux vector logo beside the start/install screen title. The console starts without extra delay; the entire start screen disappears once a session exists, leaving the terminal area, touch focus and command input available.
- Animation stops when the window loses focus, in the background and when switching to the console. Android's disabled-animation setting is respected.
- Added Android regression coverage for visible animation, decorative focus attributes, background/return and immediate transition to the console. The keyboard test also checks that no logo remains active during actual IME command input.
- Documented and included the official logo source, author and separate artwork license. Updated version and output files to 0.1.5.
- Limited build memory usage: no persistent Gradle daemon, at most 1 GiB Java heap, two workers and no parallel project builds.

## 0.1.4 · versionCode 5

- Fixed a 0.1.3 startup regression: failures in optional mirror/directory repair prevented an existing console from starting. These maintenance failures are now logged and shown in the console after startup. Required runtime files and the actual PTY start remain mandatory.
- Failed repair is still not marked successful and can be retried on the next start. Custom mirrors, shell profiles and installed packages are preserved.
- Added an Android regression with real JNI calls: corrupt mtree metadata, subsequent runtime preparation and a PTY shell that actually runs. Additional Java tests cover both error classes and successful retry.
- Updated version, APK/source archive names and update notes to 0.1.4.

## 0.1.3 · versionCode 4

- Changed the default package mirror to RWTH Aachen (HTTPS), with dotsrc (HTTPS) as fallback. The previous single Princeton default is replaced at the next session start; custom mirror lists and shell profiles are preserved.
- Fixed a confirmed extractor bug: directory permissions from the archive were ignored and determined by the host umask instead. Final modes, including sticky and setgid bits, are now applied from the inside out after all file contents and links.
- Fixed bootstrap archive ACL edge cases in the pinned libarchive tar reader: a PAX ACL read earlier must not replace the full permissions in the actual tar header. This preserves setgid bits and prevents default-only ACLs from producing mode 0000. Host tests and the APK use the same corrected reader.
- Added a one-time targeted directory repair for existing installations using local pacman mtree data. Only known old mkdir modes are changed; regular files, symlink paths, runtime mounts and conflicting package data are skipped.
- Added regressions for restrictive umask, private/read-only directories, special bits and safe, idempotent repair of existing installations. Java tests cover mirror migration, preservation of custom mirrors/profiles and retry after repair failure.
- Updated version and output files to 0.1.3. After a mirror change, run `pacman -Syyu` once to avoid using stale repository databases.

## 0.1.2 · versionCode 3

- Fixed missing touch focus on the programmatically created terminal view. The console can now become Android's active input editor, so a tap opens the on-screen keyboard and allows command input.
- Added an Android regression test covering an actual tap, visible keyboard, active IME editor and a command sent through InputConnection to the PTY shell. It also checks Back, tapping again and the menu toggle. The test uses a temporary Android shell instead of the large Arch rootfs.
- Fixed the test harness: `.tgz` preserves the compressed extraction fixture in the Android test APK; raw instrumentation results allow the device test script to evaluate its success criterion.
- Updated app version, APK names, device test script, source archives and documentation to 0.1.2.

Cause: the embedded `TerminalView` does not set its own focus attributes. These settings were missing when created in Java, so `requestFocus()` could not establish the focus needed for keyboard input in touch mode. [Android: focus and the on-screen keyboard](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility).

## 0.1.1 · versionCode 2

- Fixed Android error `filesystem error: cannot create hard link: Permission denied` during extraction. The pinned Arch archive's 1,833 hardlink entries are created as file copies, preserving contents and executable permissions.
- Hardlink chains and forward references are resolved without traversing symlinks. Missing/cyclic targets and collisions remain errors.
- A new regression test blocks `link` and `linkat` with EACCES. The original extractor reproduces the reported error; the fixed version must still extract successfully.
- Android instrumentation includes a small archive extraction test with a hardlink chain, content/permission checks and independent files.
- Updated package version, APK names, source archives and device test script. APK verification also checks the built version.
- Verified and added missing AAPT2 checksums from Google's Maven downloads so resource relinking works with dependency verification enabled.
- Downloads and app data remain when updating the APK. Resume installation; no new download is needed if the archive is complete.

Cause: Android prohibits hardlinks for normal app domains regardless of the target 28 exception for executable files. [Android SELinux](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/app_neverallows.te).

## 0.1.0 · versionCode 1

- Initial version with C++ PTY, PRoot, Arch Linux ARM64, pacman, a full-screen console and build/test suite.
