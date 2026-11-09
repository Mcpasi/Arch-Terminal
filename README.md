# Arch Terminal

Arch Terminal is an Android terminal for **Arch Linux ARM (aarch64)**. It uses a native C++ PTY, PRoot and pacman to run an Arch environment without root access or a separate Termux installation. It supports Android 9 (API 28) and later on `arm64-v8a` devices.

Mcpasi is the author of Arch Terminal's original main code, including its app, PTY, installer and build scripts. The project also uses third-party components with their own authors and licenses; see [NOTICE.md](NOTICE.md) for attribution and license details.

## What it does

- Provides a full-screen terminal with UTF-8, ANSI and true color, cursor control, an alternate screen, scrollback, text selection and PTY resizing.
- Runs multiple independent terminal sessions against the same Arch installation. Each session has its own shell, working directory and scrollback.
- Keeps sessions in a foreground service when you switch apps or turn off the screen, as long as Android keeps the process alive.
- Offers German and English interface text. The selection is saved, and switching languages keeps running terminals open. New shells use the selected language.
- Provides optional extra keys, a CPU wake lock and a PRoot compatibility mode.

## Install and use

Install the debug APK on an ARM64 device:

```bash
adb install -r output/ArchTerminal-0.2.0-arm64-debug.apk
```

Open the app and select **Install / resume Arch**. The initial download is about 791 MiB; allow at least 5 GiB of free internal storage. The installer checks the pinned SHA-256, extracts into a separate directory and activates the installation only after configuration succeeds. Interrupted downloads can resume, and a failed installation does not overwrite an existing Arch installation.

The first interactive login initializes the Arch package keyring. You can then update the system and install packages:

```bash
pacman -Syyu
pacman -S git vim base-devel
```

After upgrading to 0.2.0, open a new session and run `pacman -Syyu` once to refresh outdated package databases and update the system. The app migrates its recognized default mirror lists to `https://ca.us.mirror.archlinuxarm.org`, with RWTH Aachen as an HTTPS fallback. Custom mirror lists, symlinks and shell configuration are preserved. Package updates are always started by the user.

Your Arch files are stored in the app's private `files/arch` directory. **Uninstalling the app deletes them.** This version has no built-in backup, restore or file-sharing feature.

If extraction failed in 0.1.0, update the app and choose **Install / resume Arch** without uninstalling it. A completed download is reused and the incomplete extraction directory is rebuilt. Version 0.1.4 also fixed a problem where an optional rootfs repair could prevent an existing console from starting; a failed repair now displays a notice and can be retried later. Updating over an existing installation preserves the Arch filesystem, packages and custom configuration.

## Terminal controls

- Tap the terminal to open the keyboard; long-press text to select, copy or paste it.
- Use the bottom bar for Esc, Tab, Ctrl, Alt and arrow keys. Tap Ctrl or Alt again to release it. The extra-key bar can be hidden.
- Hold volume down for Ctrl; press volume up to open the menu, even when extra keys are hidden.
- Tap **+** or choose **New session** to open another shell. Tap a tab to switch sessions. Long-press a tab or choose **Close session** to close only that session after confirmation.
- **Stop all sessions** and the notification action stop every session. When a shell exits, its output remains visible; press Enter to restart that tab.
- The menu also controls the keyboard, Ctrl-C, font size, extra keys, CPU wake lock, PRoot compatibility, language and licenses. Compatibility mode sets `PROOT_NO_SECCOMP=1` for the next session.
- `exit` ends only the current shell; other sessions continue running.
- English guest shells use `C.UTF-8` without installing a language pack or running `locale-gen`. Your shell profiles and programs may override language settings.

## Build

You need Linux x86_64, JDK 17, Python 3.12 or later, CMake 3.22 or later, make, a C++17 compiler, curl, unzip and libarchive development files. GnuPG and `bsdtar` are optional for the full rootfs test.

```bash
# Existing SDK/NDK caches are used when available.
# Alternatively, set ANDROID_HOME and ANDROID_NDK_HOME.
scripts/setup-android.sh
scripts/test.sh
scripts/build.sh
```

The build pins SDK 36, Build Tools 35.0.0, NDK r28c (`28.2.13676358`), Gradle 8.14.3 and AGP 8.11.0. `scripts/android-env.sh` can also find an existing Buildozer cache. PRoot, talloc and libarchive are verified with SHA-256 and built from source. The Gradle wrapper verifies its download, and `gradle/verification-metadata.xml` verifies Maven artifacts. When deliberately updating dependencies, verify new checksums against their sources and update the metadata with the version change.

By default Gradle uses no persistent daemon, at most 1 GiB of Java heap, one worker, one visible JVM processor and serial garbage collection; project builds and test forks do not run in parallel. Native builds default to `ARCH_BUILD_JOBS=1` and CTest runs serially. On Linux, use `taskset -c <core>` to restrict a script to one available CPU core. On low-memory machines, run Gradle and the emulator sequentially; the test emulator uses `-cores 1`, software graphics and disabled Vulkan.

Build outputs:

- `output/ArchTerminal-0.2.0-arm64-debug.apk`: app signed with the local Android debug key.
- `output/ArchTerminal-tests.apk`: Android instrumentation tests.
- `output/SHA256SUMS`: APK checksums.
- `scripts/source-bundle.sh`: packages app sources and the exact native source archives for distribution with an APK.

The CI workflow in `.github/workflows/build.yml` runs host and Java tests, lint, the build and APK verification. The large rootfs smoke test can be enabled manually. Verification checks the finished APK's signature, target 28, ARM64 ELF files and 16 KiB segment alignment.

## Android compatibility and long sessions

The app compiles against SDK 36 but deliberately targets **API 28**. Android restricts execution from writable app directories for apps targeting API 29 and later; pacman and installed Arch binaries need to run from that private directory. PRoot and its loader are also extracted by the package installer as native APK files. See [Android's execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission) and the [Termux execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment). Android may warn about the older target. Target 28 is not a Google Play release strategy.

There is no command, idle or output time limit in the app. The display keeps 10,000 scrollback lines and the output transport has a 256 KiB buffer. Old display lines are displaced or output is subject to backpressure; neither ends the PTY. Input is written separately so a process that does not read cannot freeze the interface. A full input buffer is reported without ending the session. pacman's `DisableDownloadTimeout` disables its speed/download timeouts.

The installer has a 20-second connection attempt timeout but no read or total time limit. Connection failures can be retried without affecting an existing console, and a checksum mismatch prevents activation. A foreground service and an optional CPU wake lock support long sessions, but Android process termination, manufacturer power management and device restarts can still stop the app. Successful device feedback for 0.1.6 and 0.1.8 is documented; long-term behavior and the 0.2.0 changes still need device testing. See [open work](docs/OFFEN.md).

## Tests

```bash
scripts/test.sh                   # C++ integration, Java and original emulator tests, lint
python3 scripts/test-mirrors.py   # check ARM64 repositories, package files and signatures on both mirrors
scripts/test-rootfs.sh            # ~791 MiB: GPG/SHA, extraction, real ARM64 Bash and pacman under PRoot/QEMU
scripts/test-rootfs.sh --network  # also install and check packages in a disposable test rootfs
scripts/test-device.sh            # instrumentation on a connected ARM64 Android device
scripts/test-device.sh --rootfs   # also test PRoot and pacman in an installed Arch environment
```

See [TESTS.md](docs/TESTS.md) for details and results actually observed. Test harness timeouts are used to detect stalled tests; they are not app session limits.

The Arch image is pinned by SHA-256 and was verified using the official fingerprint `68B3537F39A313B3E574D06777193F152BDBE6A6`. Because the mirror's `latest` filename can change, a new image must be verified explicitly and `scripts/rootfs.lock.json` updated. The app does not automatically accept a new hash. See [Arch Linux ARM downloads and signatures](https://archlinuxarm.org/about/downloads).

For technical details see [architecture](docs/ARCHITEKTUR.md), [open work](docs/OFFEN.md), [the changelog](CHANGELOG.md) and [components and licenses](NOTICE.md).
