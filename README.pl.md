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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <b>Polski</b> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

Aplikacja na Androida uruchamiająca lokalny VPN, który kieruje ruch urządzenia
przez wybrane nadrzędne proxy lub przez wyraźnie wybrany tryb bezpośredni.

Aplikacja nie zmienia systemowych ustawień proxy Androida. Używa `VpnService`
i wbudowanego rdzenia sing-box.

## Zrzuty ekranu

Wykonane w aplikacji debug (v0.4.1) na Android API 35. Profile proxy używają
tylko zastrzeżonych adresów TEST-NET i nie zawierają danych logowania; wyniki
sprawdzania proxy są przykładowe.

<table>
  <tr>
    <th>Ekran główny — VPN działa</th>
    <th>Profile proxy</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Ekran główny — VPN działa" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Profile proxy" /></td>
  </tr>
  <tr>
    <th>Dodawanie proxy</th>
    <th>Diagnostyka VPN</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Dodawanie proxy" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="Diagnostyka VPN" /></td>
  </tr>
  <tr>
    <th colspan="2">Przefiltrowane logi VPN</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Przefiltrowane logi VPN" /></td>
  </tr>
</table>

## Języki

Aplikacja jest dostępna po angielsku, rosyjsku, ukraińsku, niemiecku,
francusku, hiszpańsku, włosku, portugalsku (Brazylia), polsku, chińsku
(uproszczonym), japońsku i koreańsku. Domyślnie używa języka systemu; język
można też wybrać w Ustawienia → Język lub, na Androidzie 13+, w systemowych
ustawieniach języka aplikacji. Wpisy logów i techniczne wartości diagnostyki
pozostają po angielsku, aby zgłoszenia błędów były łatwe do czytania.

## Ograniczenia

- tylko IPv4;
- IPv6 nie jest obsługiwane;
- UDP/443 jest domyślnie blokowane, aby QUIC/HTTP3 nie omijał proxy;
- jeśli wybrane nadrzędne proxy pozostaje niedostępne po ponowieniach, VPN
  zatrzymuje się w trybie fail-closed;
- tryb bezpośredni wybiera się jawnie i nigdy nie jest używany po cichu jako
  rozwiązanie zapasowe;
- hasła proxy są maskowane w interfejsie, logach i diagnostyce.

## Jak utrzymać działanie VPN

Android, a zwłaszcza Samsung One UI, może zamykać proces aplikacji w tle. Aby
VPN pozostał włączony:

- zezwól aplikacji na działanie w tle (dopóki działa optymalizacja baterii,
  ekran główny pokazuje ostrzeżenie z przyciskiem „Zezwól”); na Samsungu
  usuń ją też z „Uśpionych aplikacji”;
- opcjonalnie włącz stały VPN w ustawieniach VPN Androida (okno Ustawienia →
  Stały VPN). Razem z „Blokuj połączenia bez VPN” działa to jak kill switch:
  żaden ruch nie opuszcza urządzenia, gdy VPN nie działa.

Jeśli proces zostanie zamknięty przy włączonym VPN, aplikacja przywróci VPN w
ciągu minuty lub dwóch. Diagnostyka pokazuje przyczynę ostatniego zatrzymania
VPN i ostatniego zamknięcia aplikacji.

## Budowanie

```bash
make test
make build-local
```

Pierwsze budowanie pobiera przypięty AAR sing-box/libbox (około 97 MB) do
pamięci podręcznej Gradle użytkownika. `gradle/libbox.properties` ustala
rewizję źródeł, niezmienny adres URL artefaktu, rozmiar i SHA-256. Każde
budowanie sprawdza przed kompilacją hash i wymagane natywne ABI. Aby pobrać go
ręcznie:

```bash
make fetch-libbox
```

APK debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalacja na podłączonym urządzeniu:

```bash
make install
```

## Logi debugowania

Aplikacja zapisuje oczyszczone logi działania na ekranie Logi i w Android
logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

Logi obejmują wybór trasy, testy proxy, wyniki monitorowania proxy, próby
ponownego połączenia, odstępy między nimi, konfigurację TUN, cykl życia
silnika i ostateczne przyczyny zatrzymań fail-closed. Hasła proxy są
maskowane.

## Wydanie

Wersja jest odczytywana z `VERSION`.

```bash
make update-version
make release
```

`make release` aktualizuje `versionCode`/`versionName` w Gradle, uruchamia
sprawdzenia, buduje wydanie, tworzy commit i tag oraz wypycha tag.

Podpisane wydanie wymaga `keystore.properties`. Nie commituj sekretów ani
keystore.

## Licencja

Proxy Switcher VPN jest udostępniany na licencji
[GNU GPL v3 lub nowszej](LICENSE). Wydania zawierają sing-box/libbox, więc
dystrybutorzy muszą też zachować informacje o komponentach zewnętrznych i
udostępnić dokładnie odpowiadający kod źródłowy, jak wymaga GPLv3. Zobacz
[informacje o komponentach zewnętrznych](THIRD_PARTY_NOTICES.md) i
[audyt licencji zależności](docs/legal/dependency-license-audit.md).
