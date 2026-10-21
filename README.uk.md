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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <b>Українська</b> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

Android-застосунок, який запускає локальний VPN і спрямовує трафік пристрою
через вибраний вищий проксі або явно вибраний режим «Напряму».

Застосунок не змінює системні налаштування проксі Android. Він використовує
`VpnService` і вбудоване ядро sing-box.

## Знімки екрана

Зроблено в налагоджувальній збірці (v0.4.1) на Android API 35. Профілі проксі
використовують лише зарезервовані адреси TEST-NET і не містять облікових даних;
результати перевірок проксі — демонстраційні.

<table>
  <tr>
    <th>Головний екран — VPN працює</th>
    <th>Профілі проксі</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Головний екран — VPN працює" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Профілі проксі" /></td>
  </tr>
  <tr>
    <th>Додавання проксі</th>
    <th>Діагностика VPN</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Додавання проксі" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="Діагностика VPN" /></td>
  </tr>
  <tr>
    <th colspan="2">Відфільтрований журнал VPN</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Відфільтрований журнал VPN" /></td>
  </tr>
</table>

## Мови

Застосунок доступний англійською, російською, українською, німецькою,
французькою, іспанською, італійською, португальською (Бразилія), польською,
китайською (спрощеною), японською та корейською. Типово використовується мова
системи; мову можна вибрати в «Налаштування → Мова» або, на Android 13+, у
системних налаштуваннях мови застосунку. Записи журналу й технічні значення
діагностики залишаються англійською, щоб звіти про помилки було легко читати.

## Обмеження

- лише IPv4;
- IPv6 явно не підтримується;
- UDP/443 типово блокується, щоб QUIC/HTTP3 не йшов в обхід;
- якщо вибраний проксі залишається недоступним після повторних спроб, VPN
  зупиняється в режимі fail-closed;
- режим «Напряму» вибирається явно й ніколи не вмикається мовчки як запасний
  варіант;
- паролі проксі маскуються в інтерфейсі, журналі й діагностиці.

## Щоб VPN не вимикався

Android, особливо Samsung One UI, може завершувати процес застосунку у фоні.
Щоб VPN залишався увімкненим:

- дозвольте застосунку працювати у фоні (поки діє оптимізація батареї, на
  головному екрані видно попередження з кнопкою «Дозволити»); на Samsung також
  приберіть застосунок зі «Сплячих застосунків»;
- за бажання увімкніть «Постійний VPN» у системних налаштуваннях VPN Android
  (діалог «Налаштування» → «Постійний VPN»). Разом із «Блокувати з’єднання без
  VPN» це працює як kill switch: поки VPN не працює, трафік не залишає
  пристрій.

Якщо процес завершено, а VPN був увімкнений, застосунок відновить VPN протягом
хвилини-двох. У діагностиці видно причину останньої зупинки VPN і причину
останнього завершення застосунку.

## Збірка

```bash
make test
make build-local
```

Перша збірка завантажує зафіксований AAR sing-box/libbox (близько 97 МБ) у
кеш Gradle користувача. `gradle/libbox.properties` фіксує ревізію сирців,
незмінний URL артефакту, розмір і SHA-256. Кожна збірка перевіряє хеш і
потрібні нативні ABI перед компіляцією. Завантажити вручну:

```bash
make fetch-libbox
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Встановлення на підключений пристрій:

```bash
make install
```

## Налагоджувальний журнал

Застосунок пише очищений від секретів журнал на екран «Журнал» і в Android
logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

У журналі є вибір маршруту, перевірки проксі, результати моніторингу проксі,
спроби перепідключення, паузи між ними, налаштування TUN, життєвий цикл рушія
та підсумкові причини зупинки fail-closed. Паролі проксі маскуються.

## Реліз

Версія береться з `VERSION`.

```bash
make update-version
make release
```

`make release` оновлює `versionCode`/`versionName` у Gradle, запускає
перевірки, збирає реліз, створює коміт і тег та пушить тег.

Для підписаного релізу потрібен `keystore.properties`. Не комітьте секрети й
keystore.

## Ліцензія

Proxy Switcher VPN розповсюджується за ліцензією
[GNU GPL v3 або новішою](LICENSE). Релізи містять sing-box/libbox, тому
розповсюджувачі також мають зберігати повідомлення про сторонні компоненти й
надавати точний відповідний вихідний код, як вимагає GPLv3. Див.
[повідомлення про сторонні компоненти](THIRD_PARTY_NOTICES.md) і
[аудит ліцензій залежностей](docs/legal/dependency-license-audit.md).
