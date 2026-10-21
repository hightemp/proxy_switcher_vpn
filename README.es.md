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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <b>Español</b> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

App de Android que ejecuta una VPN local y envía el tráfico del dispositivo
a través del proxy de salida seleccionado o del modo Directo elegido
explícitamente.

La app no cambia la configuración de proxy del sistema Android. Usa
`VpnService` y un núcleo sing-box integrado.

## Capturas de pantalla

Tomadas de la app de depuración (v0.4.1) en Android API 35. Los perfiles de proxy
solo usan direcciones TEST-NET reservadas y no contienen credenciales; los
resultados de comprobación de proxies son datos de ejemplo.

<table>
  <tr>
    <th>Inicio — VPN activa</th>
    <th>Perfiles de proxy</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Inicio — VPN activa" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Perfiles de proxy" /></td>
  </tr>
  <tr>
    <th>Añadir proxy</th>
    <th>Diagnóstico de VPN</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Añadir proxy" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="Diagnóstico de VPN" /></td>
  </tr>
  <tr>
    <th colspan="2">Registros de VPN filtrados</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Registros de VPN filtrados" /></td>
  </tr>
</table>

## Idiomas

La app está disponible en inglés, ruso, ucraniano, alemán, francés, español,
italiano, portugués (Brasil), polaco, chino (simplificado), japonés y
coreano. Sigue el idioma del sistema; también puedes elegir un idioma en
Ajustes → Idioma o, en Android 13+, en los ajustes de idioma por app del
sistema. Las entradas del registro y los valores técnicos del diagnóstico se
mantienen en inglés para que los informes de errores sean fáciles de leer.

## Limitaciones

- solo IPv4;
- IPv6 no es compatible de forma explícita;
- UDP/443 se bloquea por defecto para que QUIC/HTTP3 no evite el proxy;
- si el proxy de salida seleccionado sigue sin estar disponible tras los
  reintentos, la VPN se detiene en modo fail-closed;
- el modo Directo se elige explícitamente y nunca se usa como alternativa
  silenciosa;
- las contraseñas de los proxies se ocultan en la interfaz, los registros y el
  diagnóstico.

## Mantener la VPN activa

Android, y en especial Samsung One UI, puede cerrar el proceso de la app en
segundo plano. Para mantener la VPN activa:

- permite que la app se ejecute en segundo plano (mientras la optimización de
  batería esté activa, Inicio muestra un aviso con «Permitir»); en Samsung,
  quítala también de «Aplicaciones en suspensión»;
- si quieres, activa la VPN siempre activa en los ajustes de VPN de Android
  (diálogo Ajustes → VPN siempre activa). Junto con «Bloquear conexiones sin
  VPN» funciona como kill switch: ningún tráfico sale del dispositivo mientras
  la VPN está caída.

Si el proceso se cierra con la VPN activada, la app restaura la VPN en uno o
dos minutos. El diagnóstico muestra el motivo de la última parada de la VPN y
del último cierre de la app.

## Compilación

```bash
make test
make build-local
```

La primera compilación descarga el AAR fijado de sing-box/libbox (unos 97 MB)
en la caché de Gradle del usuario. `gradle/libbox.properties` fija la revisión
del código fuente, la URL inmutable del artefacto, el tamaño y el SHA-256.
Cada compilación verifica el hash y las ABI nativas necesarias antes de
compilar. Para descargarlo explícitamente:

```bash
make fetch-libbox
```

APK de depuración:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalar en un dispositivo conectado:

```bash
make install
```

## Registros de depuración

La app escribe registros de ejecución sin datos sensibles en la pantalla
Registros y en Android logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

Los registros incluyen la selección de ruta, las pruebas de proxy, los
resultados de la monitorización del proxy, los intentos de reconexión, las
esperas, la configuración de TUN, el ciclo de vida del motor y los motivos
finales de las paradas fail-closed. Las contraseñas de los proxies se ocultan.

## Publicación

La versión se lee de `VERSION`.

```bash
make update-version
make release
```

`make release` actualiza `versionCode`/`versionName` en Gradle, ejecuta las
comprobaciones, compila la versión, crea un commit y una etiqueta y sube la
etiqueta.

Una versión firmada requiere `keystore.properties`. No subas secretos ni el
keystore al repositorio.

## Licencia

Proxy Switcher VPN se distribuye bajo
[GNU GPL v3 o posterior](LICENSE). Las versiones incluyen sing-box/libbox, por
lo que quienes las distribuyan también deben conservar los avisos de
terceros y proporcionar el código fuente correspondiente exacto que exige la
GPLv3. Consulta los [avisos de terceros](THIRD_PARTY_NOTICES.md) y la
[auditoría de licencias de dependencias](docs/legal/dependency-license-audit.md).
