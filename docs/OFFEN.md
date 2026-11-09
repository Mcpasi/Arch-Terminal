# Open work and device acceptance

Status: version 0.2.0, November 8, 2026.

## Before a reliable smartphone release

The first user device test of 0.1.0 downloaded Arch but failed during extraction because Android prohibits hardlink creation. Since 0.1.1, file copies are used instead. The 0.1.2 keyboard bug was reproduced in an Android 11 AOSP x86_64 emulator and fixed by enabling touch focus on the terminal. The emulator uses the real Activity and JNI PTY with an Android shell.

**User feedback reports successful Arch workflows with 0.1.6 on a Redmi Note 14 and a Redmi 14C, and successful hardware feedback for 0.1.8.** These reports remain valid; the changes in 0.2.0 need another device test.

For 0.2.0, check mirror migration after updating, `pacman -Syyu`, and switching German/English while terminals run. Also retest multiple parallel Arch/PRoot sessions: separate shell state and output, switching during commands, closing individual sessions, stopping all, background/return behavior and keeping the CPU awake until the last process exits. Test closing tabs in quick succession and closing/restarting while final output is pending. Automated Android regressions use temporary Android shells and, for stop tests, real PRoot without an Arch installation.

- Run `scripts/test-device.sh` on ARM64 Android; then install Arch in the app and run `scripts/test-device.sh --rootfs`.
- Test Android 9/10, 13 and 15/16 as devices become available. Include at least one device with 16 KiB memory pages.
- Test the first Arch download, interruption and resumption, TLS, cancellation during extraction, low storage and recovery after an actual app process termination.
- Run pacman, newly installed ELF files, scripts and the glibc loader directly from the **private Android app directory**. Target 28 is documented and checked in the APK; OEM/SELinux differences remain unmeasured.
- Compare the default and `PROOT_NO_SECCOMP=1` modes on manufacturer kernels. ptrace and process handling must work on the device.
- Test first-login pacman key initialization, `pacman -Syu` and package installation on the Android kernel. Signed installs of `git`, `mc`, `nodejs`, `npm` and `tree` were tested under PRoot/QEMU; Node.js, npm and Midnight Commander start there. Git was not invoked, as requested.
- Test full screen, display cutouts, gesture navigation, Gboard/Samsung keyboards, landscape, resizing during Vim/less and copy/paste on smartphones. The keyboard test covers Android 11 with the AOSP keyboard in the x86_64 emulator; other Android versions and manufacturer keyboards remain open.
- Run for at least eight hours with a silent command, large output, screen off/on and repeated app switches. Android phantom-process rules and manufacturer power management can terminate processes despite the foreground service and wake lock.
- Test explicit termination with ordinary, background and `setsid`-detached guest processes; no processes or wake locks should remain. Since 0.1.8 the app uses PRoot's SIGQUIT cleanup. Stopping through the menu and notification, including detached children, passed with real PRoot in the Android 11 emulator; retest on ARM64 manufacturer devices.

## Distribution and reproducibility

- A locally signed **debug build** exists. A release key, signed release process and publication remain to be set up. Keys are not copied from other projects.
- The GitHub Actions workflow exists but had not been run on GitHub for this new local repository at the time of this record. No remote was created or pushed.
- Existing SDK/NDK caches were reused locally. A full SDK setup with `setup-android.sh` on a machine without those caches remains to be tested.
- The mutable Arch `latest` URL is pinned to the bytes verified on November 6. A separately hosted, immutable release artifact that can be distributed lawfully, or an archive mirror, would improve long-term availability. Until then, a new upstream file causes a checksum failure and the lock must be deliberately updated after signature verification.
- Since 0.2.0 pacman uses the directly verified HTTPS addresses for `ca.us.mirror.archlinuxarm.org` and RWTH Aachen. The SHA-256-pinned bootstrap archive remains on Princeton. Test availability and fallback on target devices.
- Maven inputs are recorded by SHA-256 in `gradle/verification-metadata.xml`; native archives, SDK/NDK versions, Gradle and the Arch image are pinned. Byte-identical builds also require consistent signing keys and other host/timestamp parameters.

## Current limitations

- Multiple sessions have separate PTYs, shell state and scrollback, but share the Arch filesystem and package database.
- There is no built-in backup, restore or Android file picker. Uninstalling the app deletes the Arch filesystem.
- DNS is determined when a session starts; automatic refresh on a network change is not implemented. A new session uses the current connection.
- The CPU wake lock is enabled by default and can be disabled. There is no screen wake lock, Wi-Fi lock, battery optimization dialog or manufacturer-specific automation.
- No systemd boot, real mounts, kernel modules, Linux capabilities or additional kernel isolation are available; these are fundamental PRoot limitations.
- Target 28 allows execution of writable guest binaries. A modern Google Play target would require a different execution/distribution architecture, which is not implemented here.
