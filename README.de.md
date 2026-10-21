<p align="center">
  <img src="proxy_switcher_vpn_logox1254.png" width="120" alt="Proxy Switcher VPN" />
</p>

<h1 align="center">Proxy Switcher VPN</h1>

<p align="center">
  <a href="https://github.com/hightemp/proxy_switcher_vpn/releases/latest"><img src="https://img.shields.io/github/v/release/hightemp/proxy_switcher_vpn" alt="GitHub release (latest SemVer)" /></a>
  <a href="https://github.com/hightemp/proxy_switcher_vpn/releases"><img src="https://img.shields.io/github/downloads/hightemp/proxy_switcher_vpn/total" alt="GitHub all releases" /></a>
  <a href="https://github.com/hightemp/proxy_switcher_vpn/actions/workflows/android-ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/hightemp/proxy_switcher_vpn/android-ci.yml?branch=main&amp;label=ci" alt="Android CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue" alt="License: GPL-3.0-or-later" /></a>
  <a href="https://developer.android.com/about/versions/nougat"><img src="https://img.shields.io/badge/android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 7.0+" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/kotlin-2.0.0-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin" /></a>
  <img src="https://asdertasd.site/counter/proxy_switcher_vpn" alt="Statistics" />
</p>

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <b>Deutsch</b> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

Android-App für ein lokales VPN, das den Datenverkehr des Geräts über den
ausgewählten Upstream-Proxy oder über den ausdrücklich gewählten
Direktmodus leitet.

Die App ändert keine Android-System-Proxy-Einstellungen. Sie nutzt
`VpnService` und einen eingebetteten sing-box-Kern.

## Screenshots

Aufgenommen mit der Debug-App (v0.4.1) unter Android API 35. Die Proxy-Profile
verwenden nur reservierte TEST-NET-Adressen und enthalten keine Zugangsdaten; die
Ergebnisse der Proxy-Prüfung sind Beispieldaten.

<table>
  <tr>
    <th>Startseite – VPN aktiv</th>
    <th>Proxy-Profile</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Startseite – VPN aktiv" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Proxy-Profile" /></td>
  </tr>
  <tr>
    <th>Proxy hinzufügen</th>
    <th>VPN-Diagnose</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Proxy hinzufügen" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="VPN-Diagnose" /></td>
  </tr>
  <tr>
    <th colspan="2">Gefiltertes VPN-Protokoll</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Gefiltertes VPN-Protokoll" /></td>
  </tr>
</table>

## Sprachen

Die App ist auf Englisch, Russisch, Ukrainisch, Deutsch, Französisch,
Spanisch, Italienisch, Portugiesisch (Brasilien), Polnisch, Chinesisch
(vereinfacht), Japanisch und Koreanisch verfügbar. Sie folgt der
Systemsprache; du kannst die Sprache auch unter Einstellungen → Sprache oder
ab Android 13 in den System-Spracheinstellungen pro App wählen.
Protokolleinträge und technische Diagnosewerte bleiben auf Englisch, damit
Fehlerberichte leicht lesbar sind.

## Einschränkungen

- nur IPv4;
- IPv6 wird ausdrücklich nicht unterstützt;
- UDP/443 ist standardmäßig blockiert, damit QUIC/HTTP3 den Proxy nicht
  umgeht;
- bleibt der ausgewählte Upstream-Proxy nach Wiederholungen nicht erreichbar,
  stoppt das VPN fail-closed;
- der Direktmodus wird ausdrücklich gewählt und nie stillschweigend als
  Ausweichlösung verwendet;
- Proxy-Passwörter werden in Oberfläche, Protokoll und Diagnose maskiert.

## VPN dauerhaft aktiv halten

Android, insbesondere Samsung One UI, kann den App-Prozess im Hintergrund
beenden. So bleibt das VPN aktiv:

- erlaube der App die Ausführung im Hintergrund (solange die Akkuoptimierung
  aktiv ist, zeigt die Startseite eine Warnung mit „Erlauben“); entferne sie
  auf Samsung-Geräten außerdem aus „Inaktive Apps“;
- aktiviere bei Bedarf „Durchgehend aktives VPN“ in den Android-VPN-
  Einstellungen (Einstellungen-Dialog → Durchgehend aktives VPN). Zusammen mit
  „Verbindungen ohne VPN blockieren“ wirkt das als Kill Switch: Solange das
  VPN nicht läuft, verlässt kein Datenverkehr das Gerät.

Wird der Prozess beendet, während das VPN eingeschaltet ist, stellt die App
das VPN innerhalb von ein bis zwei Minuten wieder her. Die Diagnose zeigt den
Grund des letzten VPN-Stopps und des letzten App-Endes.

## Build

```bash
make test
make build-local
```

Der erste Build lädt das festgelegte sing-box/libbox-AAR (etwa 97 MB) in den
Gradle-Cache des Nutzers. `gradle/libbox.properties` legt Quell-Revision,
unveränderliche Artefakt-URL, Größe und SHA-256 fest. Jeder Build prüft vor
dem Kompilieren den Hash und die benötigten nativen ABIs. Zum expliziten
Herunterladen:

```bash
make fetch-libbox
```

Debug-APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Installation auf einem angeschlossenen Gerät:

```bash
make install
```

## Debug-Protokoll

Die App schreibt bereinigte Laufzeitprotokolle in den Protokoll-Bildschirm
der App und in Android logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

Das Protokoll enthält Routenauswahl, Proxy-Tests, Ergebnisse der
Proxy-Überwachung, Wiederverbindungsversuche, Wartezeiten, TUN-Einrichtung,
Engine-Lebenszyklus und die endgültigen Fail-Closed-Gründe. Proxy-Passwörter
werden maskiert.

## Release

Die Version wird aus `VERSION` gelesen.

```bash
make update-version
make release
```

`make release` aktualisiert `versionCode`/`versionName` in Gradle, führt die
Prüfungen aus, baut das Release, erstellt Commit und Tag und pusht den Tag.

Ein signiertes Release benötigt `keystore.properties`. Committe keine
Geheimnisse und keinen Keystore.

## Lizenz

Proxy Switcher VPN steht unter der
[GNU GPL v3 oder später](LICENSE). Releases enthalten sing-box/libbox, daher
müssen Weiterverteiler auch die Hinweise zu Drittkomponenten beibehalten und
den genau entsprechenden Quellcode bereitstellen, wie es die GPLv3 verlangt.
Siehe [Hinweise zu Drittkomponenten](THIRD_PARTY_NOTICES.md) und die
[Lizenzprüfung der Abhängigkeiten](docs/legal/dependency-license-audit.md).
