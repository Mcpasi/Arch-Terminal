# Testkonzept und Stand

Stand: Version 0.1.6, 7. November 2026. Die Host-Prüfungen liefen auf Linux x86_64 mit JDK 17, Android SDK 36 und NDK r28c. Ein Android-11-Emulator (API 30, AOSP x86_64) dient den Start-/JNI- und Tastaturregressionen. Diese Prüfungen ersetzen keine ARM64-/PRoot-Geräteabnahme.

## Wiederholbare Suite

`scripts/test.sh` führt diese Prüfungen aus:

| Bereich | Prüfung |
| --- | --- |
| Native PTY, CTest | stdin/stdout/stderr sind TTYs; controlling terminal; Anfangsgröße; UTF-8-Eingabe; Resize; stille, weiterhin bedienbare Shell; Ctrl-C; 32 MiB Ausgabe; SIGKILL/Exitcodes; fehlgeschlagenes exec/chdir und anschließender erfolgreicher Neustart |
| Nativer Entpacker, CTest | Gzip/Tar; ausführbare Dateien; Verzeichnismodi bei umask 0077 einschließlich privater/schreibgeschützter Verzeichnisse, Sticky-/Setgid-Bits und später Elterneinträge; sichere/idempotente mtree-Bestandsreparatur samt Konflikten und maskierten Pfadnamen; relative und absolute Guest-Symlinks; Hardlink-Dateikopien samt Vorwärtsreferenzen/Ketten; Geräte überspringen; `..`/absolute Dateipfade ablehnen; Symlink-Ausbruch; fehlende/zyklische Hardlink-Ziele; doppelte Dateien; Abbruchsignal |
| Android-Hardlink-Sperre, CTest | gleiche Entpacktests mit `link` und `linkat` durch seccomp auf EACCES gesperrt; normaler Probe-Hardlink muss tatsächlich scheitern |
| App-Java, JUnit | Argumente mit Leerzeichen; separate Host-/Guest-Umgebung; expliziter seccomp-Kompatibilitätsmodus; pacman-Signaturpflicht und Konfiguration; unvollständiges Rootfs ablehnen; einmaliger Mirror-/Rechte-Upgrade mit Erhalt eigener Mirrorlisten/Shellprofile und Wiederaufnahme nach Fehlern; schreibgeschütztes Staging ohne Symlink-Folgen aufräumen |
| Download-Java, JUnit | echte lokale HTTP-Verbindungen: Range-Fortsetzung, ignorierte Range mit sauberem Neustart, Verbindungsabbruch und Wiederaufnahme, falsche Content-Range, Abbruch mit erhaltenem Teilarchiv |
| Eingabequeue, JUnit | kopierte Eingabedaten und FIFO; voller Puffer blockiert/beendet die Sitzung nicht; Writer wird beim Schließen geweckt |
| Upstream-Emulator, JUnit | vorhandene VT-/Unicode-/Cursor-/Farb-/Scroll-/Escape-Sequenz-Regressionssuite |
| Python | gepinnte native Inputs, Rootfs-Architektur/Schlüssel/Hash und Gradle-Prüfsumme |
| Android Lint | API-Verwendung, Ressourcen und App-Code |

Die Java-Suite enthält 166 Tests (145 ursprüngliche Emulator-Tests und 21 zusätzliche Tests); dazu kommen 3 Python-Tests und 3 CTest-Läufe mit zwei Testprogrammen und mehreren Integrationsfällen. Das Test-APK ist eine weitere, separat auf einem Android-Gerät auszuführende Suite.

## Installationsanzeige 0.1.6

Die Android-Instrumentation prüft an der tatsächlichen Activity, dass ohne Dienstverbindung kein Installationsbutton und kein Downloadhinweis erscheint. Anschließend werden die UI-Zustände für vorhandenes Arch vor/nach dem Verbinden, fehlgeschlagenen Konsolenstart, fehlendes Arch und laufende Installation geprüft. Vorhandenes Arch muss „Konsole wird gestartet …“ anzeigen; ein Startfehler muss seinen Fehlertext und „Konsole erneut starten“ erhalten. Nur bestätigtes fehlendes Arch bietet „Arch installieren / fortsetzen“ an. Die Zustandsprüfung verändert keine Arch-Dateien und benötigt keinen Rootfs-Download.

Mit der unveränderten 0.1.5-App scheitert derselbe Test reproduzierbar mit `Unconnected Activity offers Arch installation before checking the existing rootfs` (`output/tests/0.1.6/before-fix.txt`). Der Vergleich verwendet ein identisches Test-APK und dieselbe x86_64-JNI-Bibliothek. Der Installations-Click-Listener bleibt die ausdrückliche Freigabe des Downloads; die neue Anzeige startet keinen Download selbstständig.

Mit 0.1.6 besteht dasselbe Test-APK einschließlich der Installationszustände, Logo-, Hintergrund- und Tastaturprüfungen mit aktivierten und deaktivierten Systemanimationen (`archterminal.success=true` in beiden Läufen). Protokolle: `output/tests/0.1.6/android-animation-1.txt` und `android-animation-0.txt`. Der Emulator lief mit Vulkan aus und 768 MiB Gast-RAM; sein Spitzen-RSS lag bei 1297788 KiB. Gradle und Emulator liefen nacheinander.

## Startlogo 0.1.5

Der Android-Test zeigt die tatsächliche Startansicht und prüft das Logo-Drawing, nicht interaktive Fokus-/Accessibility-Attribute und die sichtbare Änderung der Deckkraft. Beim Wechsel auf den Android-Startbildschirm muss die Animation stoppen und ihre Deckkraft zurücksetzen; nach Rückkehr wird sie wieder aktiv. Der anschließende Wechsel zu einer temporären Android-PTY muss sofort die Konsole fokussieren und das Logo samt Animation ausblenden. Bei deaktivierten Systemanimationen wird ein vollständig sichtbares, statisches Logo erwartet. Der bestehende Tastaturtest prüft zusätzlich das ausgeblendete, gestoppte Logo vor der Befehlseingabe und führt weiterhin einen IME-Befehl bis zur echten Shell aus.

Der Hintergrundwechsel wird über Androids globale Home-Aktion ausgelöst. Der Tastatur-Menütest wartet vor der nächsten Lautstärketaste auf den tatsächlichen Fensterfokus der Activity, damit eine bereits geschlossene IME nicht mit einem noch schließenden Dialog verwechselt wird.

Für den lokalen Emulatorlauf werden Build und Emulator nacheinander ausgeführt. Gradle verwendet `--no-daemon`, maximal 1 GiB Heap, zwei Worker und keine parallelen Projekt-Builds. Die Emulatorparameter sind `-no-window -noaudio -no-snapshot -no-boot-anim -gpu swiftshader -feature -Vulkan -lowram -memory 768 -cores 2`. `-lowram` verhindert die hier beobachtete automatische Anhebung auf 2.048 MiB trotz kleinerem `-memory`-Wert. Eine Speicherüberwachung beendet den Testemulator bei mehr als 1.400.000 KiB RSS oder weniger als 384 MiB verfügbarem Host-RAM. Gast-RAM und gemessener Host-Verbrauch stehen unter `output/tests/0.1.5/`.

Die vollständige Android-Instrumentation besteht mit aktivierten und deaktivierten Systemanimationen (`archterminal.success=true` in beiden Läufen). Die erzeugte Startansicht wurde zusätzlich visuell geprüft. Der Emulator verwendete tatsächlich 768 MiB konfigurierten Gast-RAM; sein gemessener Spitzen-RSS lag bei 1.297.756 KiB (rund 1,24 GiB). Protokolle: `output/tests/0.1.5/android-animation-1.txt`, `android-animation-0.txt`, `emulator-memory.tsv` und `startup-logo.png`. Die Tests nutzen dieselbe Java-/JNI-Implementierung mit separat gebauter x86_64-Bibliothek; die ausgelieferte APK bleibt ausschließlich ARM64.

## Regression 0.1.4: Wartungsfehler blockiert Konsolenstart

Seit 0.1.3 rief die Laufzeitvorbereitung die Mirror-/Rechtereparatur vor dem PTY-Start verpflichtend auf. Ein Java-I/O-Fehler oder eine vom JNI gemeldete `IllegalStateException` landete dadurch im allgemeinen Fehlerpfad `Start fehlgeschlagen …`; die Shell wurde gar nicht gestartet. 0.1.4 behandelt ausschließlich diese Wartung als optional. Fehler bleiben als Hinweis sichtbar, und ohne Erfolgsmarker ist ein neuer Versuch möglich. Fehler beim Erzeugen der benötigten Laufzeitdateien oder beim eigentlichen Prozessstart bleiben echte Startfehler.

Der Android-Test verwendet ein temporäres Rootfs mit absichtlich abgeschnittenen gzip-mtree-Daten und einer eigenen Mirrorliste. Zunächst muss der echte JNI-Reparaturaufruf fehlschlagen. Anschließend werden dieselbe Laufzeitvorbereitung wie beim Konsolenstart und eine echte `/system/bin/sh`-PTY ausgeführt; DNS-Datei, Befehlsausgabe, unveränderter Mirror und fehlender Erfolgsmarker werden geprüft. Der Test berührt kein installiertes Arch. Beim unveränderten 0.1.3-Startpfad scheitert er mit `Optional rootfs repair blocked shell startup: … Cannot read package mtree`. Protokoll: `output/tests/0.1.4/before-fix.txt`.

Mit 0.1.4 besteht der Test auf demselben Android-11-Emulator mit unverändertem Test-APK: Laufzeitdateien werden trotz JNI-Fehler vorbereitet, die Shell führt den Prüf-Befehl aus, der eigene Mirror bleibt erhalten und der fehlgeschlagene Reparaturlauf erhält keinen Erfolgsmarker. Auch die übrige Instrumentation einschließlich TTY/UTF-8/Resize, Ausgabedrain, Ctrl-C und echter Tastaturbedienung besteht (`archterminal.success=true`). Protokoll: `output/tests/0.1.4/after-fix.txt`. Für diesen Emulator wurde dieselbe JNI-Implementierung separat für x86_64 gebaut; die ausgelieferte APK enthält ausschließlich ARM64.

Die 166 Java-Tests, 3 Python-Tests, 3 nativen CTest-Läufe und Android Lint sind erfolgreich. Alle drei ausgelieferten nativen Bibliotheken stimmen bytegenau mit 0.1.3 überein; der Produktfix beschränkt sich auf die Java-Startvorbereitung und die Ausgabe des Wartungshinweises. Die Test-Rootfs-Dateien werden anschließend wieder entfernt.

## Regression 0.1.3: Paketmirror und Verzeichnisrechte

RWTH Aachen (`https://ftp.halifax.rwth-aachen.de/archlinux-arm`) und dotsrc (`https://mirrors.dotsrc.org/archlinuxarm`) wurden mit allen vier Repository-Datenbanken und den daraus ermittelten aktuellen Paketdateien für `git`, `mc`, `nodejs` und `npm` geprüft: jeweils HTTP 200. Princeton lieferte bei dieser Prüfung ebenfalls HTTP 200; die zuvor gemeldeten 404 waren damit nicht erneut reproduzierbar. Der GeoIP-HTTPS-Endpunkt `mirror.archlinuxarm.org` scheitert weiterhin an einem TLS-Hostnamefehler. Protokoll: `output/tests/0.1.3/mirrors.txt`.

Die Rechtewarnung hat einen bestätigten Entpackerfehler als Ursache: Verzeichnis-Modi aus dem Archiv wurden ignoriert. Mit umask 0077 erhielten auch gemeinsame Paketverzeichnisse 0700 statt 0755. Der neue Regressionstest scheitert mit dem bisherigen Entpacker reproduzierbar (`archive directory mode lost under restrictive umask`), sowohl mit als auch ohne Hardlink-Sperre. Auch unter Host-umask 0022 wichen im vorhandenen vollständigen Rootfs 25 Archivverzeichnisse ab, darunter private Verzeichnisse und Sticky-/Setgid-Verzeichnisse. Die Reparatur korrigierte 12 durch lokale Paketmetadaten eindeutig belegte Verzeichnisse; nicht paketbeschriebene und widersprüchliche Angaben werden ausgelassen. Protokolle liegen unter `output/tests/0.1.3/`.

Der korrigierte Entpacker setzt endgültige Verzeichnismodi nach den Inhalten und Links. Die Regressionen prüfen außerdem unveränderte reguläre Dateien, eigene abweichende Modi, widersprüchliche Paketangaben, Symlink-Eltern, Pfad-Traversal, mtree-Standardattribute und oktal maskierte Leerzeichen. Java-Tests prüfen, dass nur der alte einzelne Standard-Mirror ersetzt wird und die Reparatur höchstens einmal erfolgreich läuft. Fehlgeschlagenes Staging mit einem 0555-Verzeichnis bleibt löschbar.

Der zusätzliche vollständige Archivabgleich fand zwei PAX-ACL-Sonderfälle: libarchive meldete für `/var/log/journal` 0755 statt 2755 und für `/var/lib/tpm2-tss/system/keystore` 0000 statt 2775. Eine gezielte Korrektur im gepinnten Tarleser bewahrt die Rechte des eigentlichen Tar-Headers, auch wenn zuvor ein PAX-ACL gelesen wurde. Eigene kleine PAX-ACL-Fixtures sichern beide Fälle ab, ohne auf Paketmetadaten angewiesen zu sein. Hosttests und APK verwenden dieselben korrigierten libarchive-Quellen; ACLs werden nicht auf Android-Nutzer/Gruppen abgebildet.

Das vollständige gepinnte Rootfs wurde anschließend mit dem korrigierten Leser, umask 0077 und gesperrten Hardlinks erneut entpackt. Alle 2.817 geprüften Archivverzeichnisse stimmen exakt mit den Tar-Modi überein, einschließlich der beiden ACL-Sonderfälle. Protokoll: `output/tests/0.1.3/full-rootfs-directory-modes.txt`. Die wiederholbare Host-/Java-/Python-/Lint-Suite und der APK-Build sind erfolgreich.

Im frischen ARM64-Rootfs wurden anschließend `git`, `mc`, `nodejs`, `npm` und `tree` einschließlich ihrer Abhängigkeiten mit pacman installiert: 22 Pakete, keine Warnung `directory permissions differ`. Die bereits im vorangegangenen Lauf vom neuen Mirror geladenen und von pacman geprüften Pakete wurden aus dem Cache weiterverwendet. Node.js 26.13.0, npm 12.2.2 und Midnight Commander 4.8.33 starten erfolgreich. Git 2.56.0-1 wurde über pacman und das ausführbare Binary geprüft; kein Git-Befehl wurde ausgeführt. Der Test setzt wie die App `TERM=xterm-256color` und prüft `mc --version` ohne verdeckende Pipeline. Installationsprotokoll: `output/tests/0.1.3/package-install.txt`; abschließende Laufzeitprüfung: `output/tests/rootfs-network.txt`.

Die fertige APK enthält die neuen Mirrors, den aktuellen nativen Build sowie den Hinweis auf die Archivleser-Korrektur. Ihr Signaturzertifikat stimmt mit 0.1.2 überein; ein Update über die bisherige APK ist möglich. Protokoll: `output/tests/0.1.3/update-signature.txt`.

## Regression 0.1.2: Bildschirmtastatur und Befehlseingabe

Der neue Instrumentationstest startet die tatsächliche `MainActivity` mit einer temporären `/system/bin/sh`-PTY. Er benötigt kein installiertes Arch und stellt die vorherige Dienst-Sitzung beim Aufräumen wieder her. Im Touch-Modus tippt er auf die Terminalansicht und prüft die sichtbare Android-Tastatur sowie die aktive IME-Verbindung. Ein über die `InputConnection` eingegebener `printf`-Befehl muss die erwartete Ausgabe in der Shell erzeugen; das Eingabeecho allein reicht nicht aus. Anschließend folgen Zurück, erneutes Antippen und der Menüschalter zum Schließen und Öffnen.

Mit dem unveränderten `MainActivity`-Code aus 0.1.1 scheitert der Lauf reproduzierbar mit `Tap did not open keyboard: focusable=false, focusableInTouchMode=false`. Die Korrektur setzt ausschließlich die beiden Fokusattribute der programmatisch erzeugten Terminalansicht. Mit dem identischen Test-APK besteht anschließend die gesamte Instrumentation einschließlich Tastatur- und Menüprüfung (`archterminal.success=true`). Vorher-/Nachher-Protokolle stehen in `output/tests/0.1.2/before-fix.txt` und `after-fix.txt`, Angaben zur isolierten Emulatorumgebung in `environment.txt` desselben Verzeichnisses. Für den Emulator wurde dieselbe JNI-Implementierung separat für x86_64 kompiliert; die ausgelieferte APK enthält weiterhin ausschließlich ARM64.

Der erste echte Android-Lauf deckte außerdem zwei Probleme im bisherigen Test-Harness auf: Der Android-Asset-Merger entpackt `.gz`-Dateien beim Bauen und entfernt diese Endung; die komprimierte Entpack-Fixture heißt deshalb jetzt `.tgz`. `test-device.sh` verwendet `am instrument -w -r`, damit das Erfolgskriterium `archterminal.success=true` neben der formatierten Textausgabe tatsächlich ausgegeben und ausgewertet wird.

## Regression 0.1.1: Entpacken ohne Hardlink-Berechtigung

Der Nutzer-Gerätetest von 0.1.0 erreichte den Arch-Download, scheiterte beim Entpacken aber mit `filesystem error ... Permission denied`. Der neue Host-Test mit gesperrten Hardlinks reproduziert beim alten Entpacker exakt `filesystem error: cannot create hard link: Permission denied`. Nach der Korrektur bestehen derselbe eingeschränkte Test und die normalen Entpacktests. Vorher-/Nachher-Protokolle: `output/tests/0.1.1/before-fix.txt` und `after-fix.txt`.

Zusätzlich wurde das vollständige gepinnte Arch-Archiv in ein neues Verzeichnis entpackt, während `link`/`linkat` EACCES liefern. Alle 1.833 Hardlink-Einträge wurden unabhängig geprüft: reguläre Dateien mit eigenen Inodes, identischen SHA-256-Inhalten und gleichen Dateirechten wie ihre Quellen (insgesamt 8.369.010 Byte Kopien). Ergebnis: `output/tests/0.1.1/full-rootfs-extraction.txt`. Die seccomp-Sperre bildet die konkrete Operation ab; weitere Android-/SELinux-Regeln sind damit nicht simuliert.

Im frisch entpackten Dateisystem starten anschließend Bash und pacman unter PRoot/QEMU; die lokale Paketabfrage für Bash und pacman ist erfolgreich. Protokoll: `output/tests/0.1.1/rootfs-smoke.txt`.

Testfristen in CTest und Instrumentation erkennen hängende Tests. Die produktive Sitzung verwendet diese Fristen nicht. Stille Host-Sitzungen werden auf Erreichbarkeit geprüft; ein achtstündiger Hardware-Dauertest steht noch aus.

## Fertige APK

`scripts/build.sh` baut die App und das Test-APK. `verify-apk.py` prüft mit Android Build Tools und direkt an den ELF-Headern:

- APK-Signatur und ZIP-Ausrichtung.
- App-ID `de.archterminal`, Minimum 28, Target 28, ausschließlich ARM64.
- JNI-Bibliothek, PRoot und Loader tatsächlich enthalten.
- ARM64-ELFs und 16-KiB-kompatible PT_LOAD-Segmente.
- Rootfs-Lock und Lizenztexte enthalten.

Die Prüfsummen stehen in `output/SHA256SUMS`. Der Debugschlüssel ist lokal erzeugt; unterschiedliche Entwicklerrechner erzeugen daher nicht automatisch identische APK-Bytes.

## Echtes Arch ARM64 auf dem Host

Ausgeführt und erfolgreich:

```bash
scripts/test-rootfs.sh --network
```

Der Test prüft den kompletten Rootfs-Download gegen SHA-256 und die offizielle RSA-Signatur, entpackt ihn mit dem produktiven C++-Code, wendet die App-pacman-Konfiguration an und verwendet den gepinnten PRoot-Quellstand als Host-Build. Auf x86_64 führt QEMU die aarch64-Binaries aus. QEMU wird aus dem mit SHA-256 geprüften Debian-Paket entnommen und nicht ins APK eingebaut.

Beobachtet: `uname -m` liefert aarch64, Bash 5.3.15 startet, pacman 7.1.0/libalpm 16.0.1 fragt die lokale Paketdatenbank ab. `pacman-key --init` und `--populate archlinuxarm` funktionieren. Repository-Datenbanken und Pakete wurden über HTTPS geladen; pacman hat Schlüsselbund und Paketintegrität geprüft. Seit 0.1.3 deckt die Paketprüfung neben `tree` auch `git`, `mc`, `nodejs` und `npm` ab. Im Test-Rootfs bleiben die Ausgaben unter `output/tests/rootfs-host.txt` und `rootfs-network.txt` nachvollziehbar.

Das erzeugte erste Loginprofil wurde zusätzlich auf Bash-Syntax geprüft und unter PRoot/QEMU ausgeführt. Der erfolgreiche Abschluss der Schlüsselinitialisierung ist als `FIRST_LOGIN_KEYS_OK` in `output/tests/first-login-host.txt` protokolliert.

Diese Pakettransaktion erfolgt ausschließlich im wegwerfbaren Test-Rootfs. Im laufenden Arch regulär `pacman -Syu` verwenden. Der Host-Test ersetzt keine Prüfung von Androids SELinux, ptrace/seccomp-Profil oder Herstellerkernel.

## Android-Instrumentation

```bash
scripts/test-device.sh
scripts/test-device.sh --rootfs
```

Die Skripte prüfen zuerst, ob ein autorisiertes ARM64-Gerät verbunden ist. Ohne Gerät brechen sie mit Exitcode 2 und klarer Meldung ab; es wird kein Bestehen vorgetäuscht. Die zweite Variante setzt ein in der App bereits installiertes Arch voraus.

Der eigene Runner testet seit 0.1.1 zuerst einen kleinen Archiv-Entpackvorgang mit Hardlink-Kette, Dateiinhalten, eigenen Inodes und Ausführungsrechten direkt in der Android-App. Danach folgen JNI-PTY, TTY-Dateideskriptoren, UTF-8, Resize, stille Sitzung, 256 KiB Ausgabe einschließlich letzter Zeilen vor Exit und Ctrl-C. Seit 0.1.2 prüft er außerdem die tatsächliche Activity samt Bildschirmtastatur und Befehlseingabe. `--rootfs` ergänzt PRoot, den glibc-Loader und pacman auf dem Android-Kernel. Ergebnisse landen in `output/tests/device.txt`. Die Ausführung auf ARM64-Hardware steht aus.

## Lint-Hinweise

Keine Lint-Fehler. Bekannte Hinweise: neuere Gradle-Version erhältlich, kein ChromeOS-x86_64-ABI, absichtlich zeitlich unbegrenzter WakeLock und noch nicht durchgängig in Ressourcen ausgelagerte deutsche UI-Texte. Der WakeLock wird bei Ende/Abbruch/Service-Zerstörung freigegeben und kann im Menü deaktiviert werden.
