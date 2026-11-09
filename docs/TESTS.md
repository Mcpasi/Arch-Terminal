# Test plan and status

Status: version 0.2.0, November 8, 2026. Host checks use Linux x86_64 with JDK 17, Android SDK 36 and NDK r28c. An Android 11 emulator (API 30, AOSP x86_64) runs startup/JNI, keyboard, language and multi-session regressions. These checks do not replace ARM64/PRoot device acceptance.

## Repeatable suite

`scripts/test.sh` runs:

| Area | Checks |
| --- | --- |
| Native PTY, CTest | stdin/stdout/stderr are TTYs; controlling terminal; initial size; UTF-8 input; resize; concurrent PTYs with separate I/O and geometry, and continued operation after another session closes; responsive silent shell; Ctrl-C; 32 MiB output; SIGKILL/exit codes; failed exec/chdir followed by successful restart |
| Native extractor, CTest | gzip/tar; executable files; directory modes under umask 0077 including private/read-only directories, sticky/setgid bits and late parent entries; safe/idempotent mtree repair with conflicts and escaped paths; relative and absolute guest symlinks; hardlink copies with forward references/chains; skip devices; reject `..`/absolute file paths, symlink escapes, missing/cyclic hardlink targets and duplicate files; cancellation signal |
| Android hardlink restriction, CTest | same extraction tests with seccomp returning EACCES for `link` and `linkat`; a normal probe hardlink must actually fail |
| App Java, JUnit | arguments with spaces; separate host/guest environments; explicit seccomp compatibility; pacman signature requirement and configuration; reject incomplete rootfs; one-time mirror/permission upgrade preserving custom mirror lists/shell profiles and retry after failures; clean read-only staging without following symlinks |
| Download Java, JUnit | real local HTTP connections: range resume, ignored range with clean restart, disconnect and resume, incorrect Content-Range, cancellation preserving partial archive |
| Session management, JUnit | independent shell state and selection; start/restart errors; stable terminal numbers; individual close and adjacent selection; background exit with retained transcript; restart only exited tab; delayed callbacks; immutable list snapshots; shared cleanup; process exit before output drain during close, restart and stop-all |
| Input queue, JUnit | copied input data and FIFO; full buffer neither blocks nor ends the session; writer wakes on close |
| Upstream emulator, JUnit | existing VT/Unicode/cursor/color/scroll/escape sequence regression suite |
| Python | pinned native inputs, rootfs architecture/key/hash and Gradle checksum; complete German/English resources and matching format arguments |
| Android Lint | API use, resources and app code |

The test APK is another suite, run separately on an Android device. Counts and results for earlier versions appear below.

## Mirrors and language 0.2.0

Java regressions cover migration of the earlier RWTH/dotsrc and Princeton defaults when the directory repair success marker already exists. Custom servers, additional Include directives, symlinks, unchanged package signature configuration and subsequent manual settings are preserved. Shell migration preserves custom profile contents, is idempotent and does not follow profile symlinks. Generated profiles are checked with `bash -n`; the greeting is executed in both languages and must mention `pacman -Syyu`. Other cases cover stored language codes, invalid preferences, English UTF-8 guest variables, runtime-dependent download error language and language-independent progress values.

`python3 scripts/test-mirrors.py` checks the app's HTTPS mirrors sequentially. For each it loads `core`, `extra`, `alarm` and `aur` for aarch64, reads actual filenames for `bash`, `pacman`, `git`, `mc`, `nodejs`, `npm` and `tree` from the databases, and expects HTTP 200 for each package and signature. It runs no Git command or package transaction on the host.

Android instrumentation switches Deutsch → English → Deutsch through the actual menu. After Activity recreation, the saved preference, interface and service notification must agree; the same PTY process with the same PID, shell variable and scrollback must continue running. The English process-exit notice is then checked. Run this case individually with `adb shell am instrument -w -r -e languageRegression true de.archterminal.test/de.archterminal.TerminalInstrumentation`.

The full host suite passed with 192 Java tests (44 app and 148 emulator/input queue), 4 Python tests and 3 native CTest runs. Final app tests and lint are logged in `output/tests/0.2.0/app-java-lint-final.txt`: no errors, three known lint warnings. The live mirror test confirmed both servers, including all tested packages and signatures (`output/tests/0.2.0/mirrors.txt`). Full Android instrumentation passed, including language switching (`output/tests/0.2.0/final-stable/android.txt`). The runner temporarily selects German for existing German menu tests and restores the original preference afterward. The final ARM64 build and APK verification succeeded (`output/tests/0.2.0/build-final.txt`).

Checks, Gradle builds and emulator runs were sequential on CPU 0: `ARCH_BUILD_JOBS=1`, `CMAKE_BUILD_PARALLEL_LEVEL=1`, `CTEST_PARALLEL_LEVEL=1`, one Gradle worker and `-XX:ActiveProcessorCount=1 -XX:+UseSerialGC`. The emulator used one virtual core; actual host affinity, guest cores and memory use are under `output/tests/0.2.0/final-stable/`. An intermediate run was blocked from window focus by an Android system ANR dialog visible before testing (`final/preflight.xml`, `final/failure-logcat.txt`). The retry used unchanged APKs and criteria; emulator preparation only confirmed the system dialogs “System UI isn't responding” and “Process system isn't responding” with “Wait”. The first successful full run remains under `after/`. Smartphone acceptance for 0.2.0 remains open.

## Session lifecycle 0.1.9

Both bugs were reproduced before their respective fixes. Android evidence uses the preserved, unchanged 0.1.8 app; app and test APK SHA-256 hashes are recorded in `output/tests/0.1.9/reproduction-apks.sha256`.

- **Process exit before complete output:** An Android shell child keeps the PTY open after the parent has been reaped by `waitpid`. The test explicitly checks `processEnded=true`, `isRunning()=false`, an open reader and a pending exit callback. In 0.1.8, closing or restarting discards or replaces the entry prematurely. Intentional failures are recorded in `output/tests/0.1.9/before/drain-close.txt` and `drain-restart.txt`. After releasing the child, `DEFERRED_TAIL` must arrive completely; only the final callback may complete close or allow restart. Three additional JUnit cases for close, restart and stop-all also fail against the original manager (`session-manager-before.txt`). Existing tests for fully exited shells now explicitly deliver their exit callbacks too.
- **Closing all tabs before an exit callback:** Two real PRoot sessions with detached children are closed individually in one main-thread iteration. The unchanged service incorrectly remains outside its stop state because both entries initially remain in the list. The test fails with `Closing every tab before exit callbacks must stop the service` (`output/tests/0.1.9/before/overlapping-close.txt`). The regression checks the bound service directly and expects an empty session list without notification or wake lock after both actual exits; existing menu tests still check Activity completion.

After installing the app and test APK, run the new cases individually:

```bash
adb shell am instrument -w -r -e sessionRegression drain-close de.archterminal.test/de.archterminal.TerminalInstrumentation
adb shell am instrument -w -r -e sessionRegression drain-restart de.archterminal.test/de.archterminal.TerminalInstrumentation
adb shell am instrument -w -r -e sessionRegression overlapping-close de.archterminal.test/de.archterminal.TerminalInstrumentation
```

Without `sessionRegression` they remain part of full instrumentation. The before/after comparison uses the same preserved test APK against the old and corrected apps. No Arch installation is modified.

With the fixes, all 180 Java tests, including 14 session manager tests, passed along with 3 native CTest runs, 3 Python tests and Android Lint. There were no test failures or skipped Java tests; lint reported only the four existing notices described below. Log: `output/tests/0.1.9/host-java-lint.txt`; counts: `test-summary.json`.

Full Android instrumentation also passed with `archterminal.success=true`, including new regressions, menu/notification stop, logo, keyboard and multi-session navigation (`output/tests/0.1.9/after/android.txt`). The first full run passed the new regressions but failed the existing keyboard test with `Menu did not reopen keyboard` (`after-window-animations/android.txt`). The retry used the same unchanged APKs and criteria; only emulator `window_animation_scale` and `transition_animation_scale` were set to 0. `animator_duration_scale=1` retained the tested logo animation. The failed run and Logcat remain available.

Host tests, builds and emulator runs were sequential on CPU 0 under `nice -n 10`: `ARCH_BUILD_JOBS=1`, `CMAKE_BUILD_PARALLEL_LEVEL=1`, `CTEST_PARALLEL_LEVEL=1`, one Gradle worker, no parallel test forks and Java `-XX:ActiveProcessorCount=1 -XX:+UseSerialGC`. The emulator used one virtual core, 1 GiB guest RAM and software graphics without Vulkan; host/guest cores and memory are logged under `output/tests/0.1.9/after/`. The ARM64 APK was checked for 0.1.9 / versionCode 10, signature, ZIP/ELF alignment and runtime files (`output/tests/0.1.9/build.txt`). Retesting this version on devices remains open.

## Stopping sessions 0.1.8

For stop regressions, Android instrumentation uses the pinned PRoot and loader from the APK and the service's production stop backend. Inside PRoot, an Android shell ignores SIGTERM and starts a child detached with setsid. No Arch installation is needed or modified. Earlier simple Android shell fixtures remain for keyboard and tab navigation.

- Menu: Open two PRoot sessions, close the selected session through the menu and confirmation, check its detached child exits, and continue working in the other shell. Close the last session the same way; the Activity, notification and wake lock must end.
- Notification: With two PRoot sessions, background the Activity and invoke the actual stop-all PendingIntent from the published notification. Both tracers and detached children must exit. An additional test client keeps the service bound while notification and wake-lock removal are checked. Repeated stop must not bring back an empty notification.

The cause in 0.1.7 is visible in the pinned PRoot signal handler: SIGTERM is ignored; SIGQUIT calls kill_all_tracees and lets the event loop clean up. The service now chooses SIGQUIT. No command, idle or output timeout is introduced.

Full Android instrumentation passed with `archterminal.success=true`, including both new stop regressions and all previous startup, keyboard and multi-session tests. Log: `output/tests/0.1.8/android.txt`. Also passed: 177 Java tests, 3 native CTest runs, 3 Python tests and Android Lint. The ARM64 APK was checked for 0.1.8 / versionCode 9, signature, ZIP/ELF alignment and runtime files; logs: `output/tests/0.1.8/host-java-lint.txt` and `build.txt`.

Tests and builds remained sequential with one CPU core and worker. The successful Android 11 emulator run used one virtual core, host and guest CPU 0, software graphics and disabled Vulkan. Due to memory pressure at 768 MiB and system-server watchdogs, the guest received 1 GiB RAM; peak host RSS was 1,568,184 KiB, with at least 763,852 KiB host memory available. Startup checks use the accessible interface instead of a global broadcast-idle query. Measurements and startup diagnostics are under `output/tests/0.1.8/`.

## Multiple sessions 0.1.7

The Android-independent JUnit suite checks lifecycle through a replaceable backend. A failed new PTY start or restart and delayed exit callbacks must not change the selection or other running sessions. Closing processes remain managed until exit callbacks; stop-all waits for all processes. Naturally exited tabs retain output and are replaced at the same position and number when restarted.

Android instrumentation uses up to three concurrent temporary JNI PTYs with Android shells and a subsequent tab restart, without installing or modifying an Arch filesystem. It checks actual plus/menu actions, separate shell variables and output, IME input, tab switching and closing, focus and reset Ctrl state, navigation with extra keys hidden, background output and service rebinding with selection retained. Closing one terminal and natural exit of a background terminal must preserve other shells and their wake lock. Enter restarts only the exited tab under the same number. No managed terminals or wake lock may remain after the last test process. Existing startup, logo and keyboard regressions remain included.

For 0.1.7, tests and builds run sequentially under `taskset -c 0 nice -n 10`: one CPU core, `ARCH_BUILD_JOBS=1`, `CMAKE_BUILD_PARALLEL_LEVEL=1`, `CTEST_PARALLEL_LEVEL=1`, one Gradle worker, no parallel test forks and Java `-XX:ActiveProcessorCount=1 -XX:+UseSerialGC`. The emulator then starts with one virtual core, 768 MiB guest RAM and software graphics with Vulkan disabled. Multi-session acceptance with Arch/PRoot on ARM64 devices remains separately necessary.

Full Android instrumentation passed with `archterminal.success=true`, including plus/menu creation, IME, tab switching, background/return, isolated close, natural background exit, Enter restart and wake-lock release. Log: `output/tests/0.1.7/android.txt`. The successful run demonstrably used only CPU 0 on host and guest; peak RSS was 1,289,692 KiB. After an initial system UI ANR, instrumentation waits for Android boot broadcasts to finish. The IME test waits for the terminal to be attached and fully built before sending input. Measurements and preflight are also under `output/tests/0.1.7/`.

The final 177 Java tests, 3 native CTest runs including concurrent PTYs, 3 Python tests and Android Lint also passed. The final ARM64 build and APK verification for 0.1.7 / versionCode 8, signature, ZIP/ELF alignment and runtime files succeeded. Logs: `output/tests/0.1.7/host-java-lint.txt` and `build.txt`. Only the existing lint notices described below remained.

## Installation screen 0.1.6

Android instrumentation checks the actual Activity to ensure no install button or download notice appears without a service connection. It then checks UI states for existing Arch before/after connection, failed console startup, missing Arch and installation in progress. Existing Arch must show a console startup message; startup failure must retain its error and offer a retry. Only confirmed missing Arch offers installation/resumption. These state checks neither modify Arch files nor require a rootfs download.

With the unchanged 0.1.5 app the same test reproducibly fails with `Unconnected Activity offers Arch installation before checking the existing rootfs` (`output/tests/0.1.6/before-fix.txt`). The comparison uses the same test APK and x86_64 JNI library. The install click listener remains the explicit authorization to download; the new screen does not start a download itself.

With 0.1.6 the same test APK passes installation states, logo, background and keyboard checks with system animations enabled and disabled (`archterminal.success=true` in both). Logs: `output/tests/0.1.6/android-animation-1.txt` and `android-animation-0.txt`. The emulator used disabled Vulkan and 768 MiB guest RAM; peak RSS was 1,297,788 KiB. Gradle and emulator ran sequentially.

## Startup logo 0.1.5

The Android test displays the actual start screen and checks logo drawing, non-interactive focus/accessibility attributes and visible opacity changes. Switching to the Android home screen must stop the animation and reset opacity; on return it resumes. Switching to a temporary Android PTY must immediately focus the console and hide the logo and animation. With system animations disabled a fully visible static logo is expected. The existing keyboard test also checks the hidden, stopped logo before command input and still sends an IME command to a real shell.

Backgrounding is triggered using Android's global Home action. Before the next volume key, the keyboard menu test waits for the Activity's actual window focus so an already closed IME is not confused with a dialog still closing.

For the local emulator run, build and emulator run sequentially. Gradle uses `--no-daemon`, at most 1 GiB heap, two workers and no parallel project builds. Emulator parameters are `-no-window -noaudio -no-snapshot -no-boot-anim -gpu swiftshader -feature -Vulkan -lowram -memory 768 -cores 2`. `-lowram` prevents the observed automatic increase to 2,048 MiB despite a smaller `-memory` value. A memory monitor stops the test emulator above 1,400,000 KiB RSS or below 384 MiB available host RAM. Guest RAM and measured host use are under `output/tests/0.1.5/`.

Full Android instrumentation passed with system animations enabled and disabled (`archterminal.success=true` in both runs). The generated start screen was also visually inspected. The emulator used 768 MiB configured guest RAM; measured peak RSS was 1,297,756 KiB (about 1.24 GiB). Logs: `output/tests/0.1.5/android-animation-1.txt`, `android-animation-0.txt`, `emulator-memory.tsv` and `startup-logo.png`. Tests use the same Java/JNI implementation with a separately built x86_64 library; the shipped APK remains ARM64 only.

## Regression 0.1.4: maintenance failure blocks console startup

Since 0.1.3 runtime preparation had required mirror/permission repair before PTY startup. A Java I/O error or JNI `IllegalStateException` therefore entered the general startup failure path and the shell never started. Version 0.1.4 treats only this maintenance as optional. Errors remain visible as notices, and without a success marker retry is possible. Errors creating required runtime files or starting the process remain real startup failures.

The Android test uses a temporary rootfs with deliberately truncated gzip mtree data and a custom mirror list. First, the real JNI repair call must fail. Then the same runtime preparation used for console startup and a real `/system/bin/sh` PTY run; DNS file, command output, unchanged mirror and missing success marker are checked. The test does not touch an installed Arch. The unchanged 0.1.3 startup path fails with `Optional rootfs repair blocked shell startup: … Cannot read package mtree`. Log: `output/tests/0.1.4/before-fix.txt`.

With 0.1.4 the test passes on the same Android 11 emulator with the unchanged test APK: runtime files are prepared despite JNI failure, the shell runs the test command, the custom mirror remains and the failed repair receives no success marker. Remaining instrumentation including TTY/UTF-8/resize, output drain, Ctrl-C and real keyboard input also passes (`archterminal.success=true`). Log: `output/tests/0.1.4/after-fix.txt`. The same JNI implementation was separately built for x86_64 for the emulator; the shipped APK contains only ARM64.

All 166 Java tests, 3 Python tests, 3 native CTest runs and Android Lint passed. All three shipped native libraries are byte-identical to 0.1.3; the product fix is limited to Java startup preparation and the maintenance notice. Test rootfs files are removed afterward.

## Regression 0.1.3: package mirrors and directory permissions

RWTH Aachen (`https://ftp.halifax.rwth-aachen.de/archlinux-arm`) and dotsrc (`https://mirrors.dotsrc.org/archlinuxarm`) were checked with all four repository databases and the current package files derived from them for `git`, `mc`, `nodejs` and `npm`: all returned HTTP 200. Princeton also returned HTTP 200 during this check, so the previously reported 404s could not be reproduced. The GeoIP HTTPS endpoint `mirror.archlinuxarm.org` still fails with a TLS hostname error. Log: `output/tests/0.1.3/mirrors.txt`.

The permission warning had a confirmed extractor bug as its cause: archive directory modes were ignored. With umask 0077 even shared package directories received 0700 instead of 0755. The new regression reproducibly fails with the old extractor (`archive directory mode lost under restrictive umask`), both with and without hardlinks blocked. Even with host umask 0022, 25 archive directories differed in the existing complete rootfs, including private and sticky/setgid directories. Repair corrected 12 directories unambiguously supported by local package metadata; non-package-described and conflicting entries were skipped. Logs are under `output/tests/0.1.3/`.

The corrected extractor applies final directory modes after contents and links. Regressions also check unchanged regular files, custom differing modes, conflicting package data, symlink parents, path traversal, mtree defaults and octal-escaped spaces. Java tests verify only the old single default mirror is replaced and repair succeeds at most once. Failed staging with a 0555 directory remains removable.

An additional full archive comparison found two PAX ACL edge cases: libarchive reported 0755 instead of 2755 for `/var/log/journal` and 0000 instead of 2775 for `/var/lib/tpm2-tss/system/keystore`. A targeted fix in the pinned tar reader preserves the actual tar header's permissions even when a PAX ACL was read first. Small custom PAX ACL fixtures cover both cases without package metadata. Host tests and APK use the same corrected libarchive sources; ACLs are not mapped to Android users/groups.

The complete pinned rootfs was then extracted again with the corrected reader, umask 0077 and hardlinks blocked. All 2,817 checked archive directories matched tar modes exactly, including both ACL cases. Log: `output/tests/0.1.3/full-rootfs-directory-modes.txt`. The repeatable host/Java/Python/lint suite and APK build succeeded.

In the fresh ARM64 rootfs, `git`, `mc`, `nodejs`, `npm` and `tree` and dependencies were installed with pacman: 22 packages, without a `directory permissions differ` warning. Packages downloaded from the new mirror and verified by pacman in the previous run were reused from cache. Node.js 26.13.0, npm 12.2.2 and Midnight Commander 4.8.33 start successfully. Git 2.56.0-1 was checked through pacman and its executable binary; no Git command was run. Like the app, the test sets `TERM=xterm-256color` and checks `mc --version` without a masking pipeline. Installation log: `output/tests/0.1.3/package-install.txt`; final runtime check: `output/tests/rootfs-network.txt`.

The finished APK contains the new mirrors, current native build and archive-reader fix notice. Its signing certificate matches 0.1.2, allowing an update over the previous APK. Log: `output/tests/0.1.3/update-signature.txt`.

## Regression 0.1.2: on-screen keyboard and command input

The new instrumentation test starts the actual `MainActivity` with a temporary `/system/bin/sh` PTY. It requires no installed Arch and restores the previous service session during cleanup. In touch mode it taps the terminal and checks the visible Android keyboard and active IME connection. A `printf` command entered through `InputConnection` must produce the expected shell output; input echo alone is insufficient. It then tests Back, tapping again and the menu toggle to close and open the keyboard.

With unchanged 0.1.1 `MainActivity` code the run reproducibly fails with `Tap did not open keyboard: focusable=false, focusableInTouchMode=false`. The fix sets only the two focus attributes on the programmatically created terminal view. The identical test APK then passes all instrumentation, including keyboard and menu checks (`archterminal.success=true`). Before/after logs are `output/tests/0.1.2/before-fix.txt` and `after-fix.txt`; isolated emulator details are in `environment.txt` in the same directory. The same JNI implementation was separately compiled for x86_64 for the emulator; the shipped APK still contains only ARM64.

The first real Android run also exposed two test harness issues: the Android asset merger decompresses `.gz` files during the build and removes the extension, so the compressed extraction fixture is now named `.tgz`. `test-device.sh` uses `am instrument -w -r` so the `archterminal.success=true` success criterion is actually emitted and can be evaluated alongside formatted output.

## Regression 0.1.1: extraction without hardlink permission

The 0.1.0 user device test downloaded Arch but failed during extraction with `filesystem error ... Permission denied`. The new host test with hardlinks blocked reproduces exactly `filesystem error: cannot create hard link: Permission denied` with the old extractor. After the fix, the same restricted test and normal extraction tests pass. Before/after logs: `output/tests/0.1.1/before-fix.txt` and `after-fix.txt`.

The complete pinned Arch archive was also extracted into a new directory while `link`/`linkat` returned EACCES. All 1,833 hardlink entries were independently checked: regular files with their own inodes, identical SHA-256 contents and the same permissions as their sources (8,369,010 bytes of copies total). Result: `output/tests/0.1.1/full-rootfs-extraction.txt`. The seccomp restriction models this operation; it does not simulate additional Android/SELinux rules.

Bash and pacman subsequently start in the freshly extracted filesystem under PRoot/QEMU, and local package queries for Bash and pacman succeed. Log: `output/tests/0.1.1/rootfs-smoke.txt`.

CTest and instrumentation timeouts detect stalled tests. Production sessions do not use these timeouts. Silent host sessions are checked for responsiveness; an eight-hour hardware endurance test remains open.

## Finished APK

`scripts/build.sh` builds the app and test APK. `verify-apk.py` uses Android Build Tools and reads ELF headers directly to check:

- APK signature and ZIP alignment.
- App ID `de.archterminal`, minimum 28, target 28, ARM64 only.
- JNI library, PRoot and loader are actually included.
- ARM64 ELF files and 16 KiB-compatible PT_LOAD segments.
- Rootfs lock and license texts are included.

Checksums are in `output/SHA256SUMS`. The debug key is generated locally, so builds on different developers' machines do not automatically produce identical APK bytes.

## Real Arch ARM64 on the host

Run successfully:

```bash
scripts/test-rootfs.sh --network
```

The test checks the complete rootfs download against SHA-256 and the official RSA signature, extracts it with production C++ code, applies the app's pacman configuration and uses a host build of pinned PRoot sources. On x86_64, QEMU runs the aarch64 binaries. QEMU comes from a SHA-256-verified Debian package and is not included in the APK.

Observed: `uname -m` returns aarch64, Bash 5.3.15 starts, and pacman 7.1.0/libalpm 16.0.1 queries the local package database. `pacman-key --init` and `--populate archlinuxarm` work. Repository databases and packages were downloaded over HTTPS; pacman verified the keyring and package integrity. Since 0.1.3 package checks cover `git`, `mc`, `nodejs` and `npm` as well as `tree`. Test rootfs output is traceable in `output/tests/rootfs-host.txt` and `rootfs-network.txt`.

The generated first-login profile was also checked for Bash syntax and run under PRoot/QEMU. Successful key initialization is logged as `FIRST_LOGIN_KEYS_OK` in `output/tests/first-login-host.txt`.

This package transaction happens only in the disposable test rootfs. In a running Arch installation, normally use `pacman -Syu`. The host test does not replace checks of Android SELinux, ptrace/seccomp profiles or manufacturer kernels.

## Android instrumentation

```bash
scripts/test-device.sh
scripts/test-device.sh --rootfs
```

The scripts first check for an authorized ARM64 device. Without one they exit with code 2 and a clear message; they do not claim a pass. The second variant requires Arch to be installed in the app already.

Since 0.1.1 the custom runner first tests a small archive extraction directly in the Android app, with a hardlink chain, file contents, independent inodes and executable permissions. It then checks JNI PTY, TTY file descriptors, UTF-8, resize, a silent session, 256 KiB output including final lines before exit, and Ctrl-C. Since 0.1.2 it also checks the actual Activity with on-screen keyboard and command input. `--rootfs` adds PRoot, the glibc loader and pacman on the Android kernel. Results go to `output/tests/device.txt`. The automated 0.2.0 instrumentation suite has not yet run on ARM64 hardware. Earlier successful user device tests for 0.1.6 and 0.1.8 are documented in [OFFEN.md](OFFEN.md).

## Lint notices

No lint errors. Known notices concern a newer Gradle plugin version, the missing ChromeOS x86_64 ABI and the deliberately indefinite wake lock. The wake lock is released on end, cancellation or service destruction and can be disabled in the menu. German and English are externalized in resources and remain available together even with app-bundle installations.
