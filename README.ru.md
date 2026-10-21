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

<p align="center"><a href="README.md">English</a> · <b>Русский</b> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

Android-приложение, которое поднимает локальный VPN и направляет трафик
устройства через выбранный вышестоящий прокси или явно выбранный режим
«Напрямую».

Приложение не меняет системные настройки прокси Android. Оно использует
`VpnService` и встроенное ядро sing-box.

## Скриншоты

Сняты в отладочной сборке (v0.4.1) на Android API 35. Профили прокси используют
только зарезервированные адреса TEST-NET и не содержат учётных данных; результаты
проверок прокси — демонстрационные.

<table>
  <tr>
    <th>Главный экран — VPN работает</th>
    <th>Профили прокси</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Главный экран — VPN работает" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Профили прокси" /></td>
  </tr>
  <tr>
    <th>Добавление прокси</th>
    <th>Диагностика VPN</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Добавление прокси" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="Диагностика VPN" /></td>
  </tr>
  <tr>
    <th colspan="2">Отфильтрованные логи VPN</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Отфильтрованные логи VPN" /></td>
  </tr>
</table>

## Языки

Приложение доступно на английском, русском, украинском, немецком,
французском, испанском, итальянском, португальском (Бразилия), польском,
китайском (упрощённом), японском и корейском. По умолчанию используется язык
системы; язык можно выбрать в «Настройки → Язык» или, на Android 13+, в
системных настройках языка приложения. Записи логов и технические значения
диагностики остаются на английском, чтобы баг-репорты было легко читать.

## Ограничения

- только IPv4;
- IPv6 явно не поддерживается;
- UDP/443 по умолчанию блокируется, чтобы QUIC/HTTP3 не шёл в обход;
- если выбранный прокси остаётся недоступным после повторных попыток, VPN
  останавливается в режиме fail-closed;
- режим «Напрямую» выбирается явно и никогда не включается молча как запасной
  вариант;
- пароли прокси маскируются в интерфейсе, логах и диагностике.

## Чтобы VPN не отключался

Android, особенно Samsung One UI, может завершать процесс приложения в фоне.
Чтобы VPN оставался включённым:

- разрешите приложению работать в фоне (пока действует оптимизация батареи,
  на главном экране видно предупреждение с кнопкой «Разрешить»); на Samsung
  также уберите приложение из «Спящих приложений»;
- при желании включите «Постоянный VPN» в системных настройках VPN Android
  (диалог «Настройки» → «Постоянный VPN»). Вместе с «Блокировать подключения
  без VPN» это работает как kill switch: пока VPN не работает, трафик не
  покидает устройство.

Если процесс завершён, а VPN был включён, приложение восстановит VPN в течение
минуты-двух. В диагностике видны причина последней остановки VPN и причина
последнего завершения приложения.

## Сборка

```bash
make test
make build-local
```

Первая сборка скачивает зафиксированный AAR sing-box/libbox (около 97 МБ) в
кэш Gradle пользователя. `gradle/libbox.properties` фиксирует ревизию
исходников, неизменяемый URL артефакта, размер и SHA-256. Каждая сборка
проверяет хеш и нужные нативные ABI перед компиляцией. Скачать вручную:

```bash
make fetch-libbox
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Установка на подключённое устройство:

```bash
make install
```

## Отладочные логи

Приложение пишет очищенные от секретов логи на экран «Логи» и в Android
logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

В логах есть выбор маршрута, проверки прокси, результаты мониторинга прокси,
попытки переподключения, паузы между ними, настройка TUN, жизненный цикл
движка и итоговые причины остановки fail-closed. Пароли прокси маскируются.

## Релиз

Версия берётся из `VERSION`.

```bash
make update-version
make release
```

`make release` обновляет `versionCode`/`versionName` в Gradle, запускает
проверки, собирает релиз, создаёт коммит и тег и пушит тег.

Для подписанного релиза нужен `keystore.properties`. Не коммитьте секреты и
keystore.

## Лицензия

Proxy Switcher VPN распространяется под лицензией
[GNU GPL v3 или новее](LICENSE). Релизы включают sing-box/libbox, поэтому
распространители также должны сохранять уведомления о сторонних компонентах
и предоставлять точный соответствующий исходный код, как требует GPLv3. См.
[уведомления о сторонних компонентах](THIRD_PARTY_NOTICES.md) и
[аудит лицензий зависимостей](docs/legal/dependency-license-audit.md).
