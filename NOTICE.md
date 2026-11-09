# Components and attribution

Arch Terminal 0.2.0 is an independent Android app. **Mcpasi is the author of Arch Terminal's original main code**, including the app, PTY, installer and build scripts. This original code is licensed under GPL-3.0-or-later; see [LICENSE](LICENSE). Third-party components retain their own authorship and licenses, described below. Arch Linux ARM and Termux are independent projects and do not publish this app.

| Component | Source | License / local texts |
| --- | --- | --- |
| Arch Linux logo by Thayer Williams | [Official artwork sources 1.6](https://sources.archlinux.org/other/artwork/archlinux-artwork-1.6.tar.gz), `logos/archlinux-official-dark.svg` | CC-BY-NC-SA-3.0 according to the included COPYING; `third_party/ARCHLINUX-ARTWORK-COPYING`, `third_party/ARCHLINUX-ARTWORK-AUTHORS.txt`; unmodified SVG source in `third_party/archlinux-official-dark.svg` |
| Terminal emulator, TerminalView and original tests | [termux/termux-app v0.118.3](https://github.com/termux/termux-app/tree/5b657c6adf4304e5198951ce815fe0205dcac29c), commit `5b657c6adf4304e5198951ce815fe0205dcac29c` | Apache-2.0 exception for `terminal-emulator` and `terminal-view`; see `third_party/termux/UPSTREAM-LICENSE.md` and `APACHE-2.0.txt` |
| PRoot | [termux/proot v5.1.107.102](https://github.com/termux/proot/tree/v5.1.107.102) | GPL-2.0-or-later, `third_party/PROOT-COPYING` |
| talloc 2.4.3 | [Samba talloc](https://www.samba.org/ftp/talloc/) | LGPL-3.0-or-later, `third_party/TALLOC-LGPL-3.0.txt` |
| libarchive 3.7.7 | [libarchive](https://www.libarchive.org/) | BSD and component-specific notices, `third_party/LIBARCHIVE-COPYING` |
| AndroidX annotations 1.3.0 | [AndroidX](https://developer.android.com/jetpack/androidx/releases/annotation) | Apache-2.0 |
| Gradle Wrapper 8.14.3 | [Gradle](https://github.com/gradle/gradle/tree/v8.14.3) | Apache-2.0; unmodified standard wrapper from an existing local Android project |
| Arch Linux ARM rootfs | [Official downloads](https://archlinuxarm.org/about/downloads) via the Princeton mirror | Individual package licenses under `/usr/share/licenses` in the rootfs; the rootfs is not included in the APK |
| QEMU 7.2, optional host test only | [Debian qemu-user-static](https://packages.debian.org/bookworm/amd64/qemu-user-static/download) | GPL-2.0 and additional component licenses; not included in the APK |

The startup logo in `app/src/main/res/drawable/ic_arch_linux.xml` isolates the symbol from the official SVG as an Android vector, retaining its proportions and original color; the TM mark is represented as a path. The app animates only its opacity. Like the original, this artwork adaptation is licensed under CC-BY-NC-SA-3.0, separately from the original GPL app code. The source archive's SHA-256 is `9c6e7faea0ed66bf6ed64b134fad6316161cf146779e4e2f4cd95c644066aab2`. The Arch Linux name and logo are trademarks; see the [official artwork page](https://archlinux.org/art/) and [trademark policy](https://terms.archlinux.org/docs/trademark-policy/).

The emulator and view files retain their original notices. Changes include a new C++ JNI library; a replacement `TerminalSession` with separate file descriptors, a non-blocking input queue, full output draining before exit and a selectable termination signal (SIGQUIT for PRoot); host-translatable process-exit and input messages; a new `InputQueue` and tests; and explicit clipboard actions in text selection. Original unused JNI C files are included only as provenance references and are not built.

`AGENTCODI-Package-Edition` was examined for reusable components. The architectural approach already used there for `targetSdk 28`, private executable packages and long-running sessions was adopted. Neither Codex nor an agent runtime or its package archive is included. This project's PTY, installer, app and build scripts are original implementations.

Native downloads are pinned with SHA-256 in `scripts/dependencies.lock.json`. PRoot is built without a source patch; a forced include, disabled for freestanding loaders, addresses a missing `string.h`. The 32-bit loader is disabled for this ARM64 app. talloc is built unchanged with the documented compatibility header. libarchive is linked statically with gzip support. Since 0.1.3, `scripts/fetch-sources.py` applies a targeted tar-reader fix to prevent PAX ACLs from overriding original tar modes, including setgid bits.

When distributing an APK, include the corresponding archives from `scripts/source-bundle.sh` and these notices. They contain the app source, build instructions and exact talloc, PRoot and libarchive sources for rebuilding or relinking. No release key is supplied; self-built variants can be installed under a different signature by reinstalling the app.
