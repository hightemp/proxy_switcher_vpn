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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <b>한국어</b></p>

로컬 VPN을 실행해 기기 트래픽을 선택한 업스트림 프록시 또는 명시적으로 선택한 직접 연결 모드로 보내는 Android 앱입니다.

Android 시스템 프록시 설정은 변경하지 않습니다. `VpnService`와 내장된 sing-box 코어를 사용합니다.

## 스크린샷

Android API 35에서 디버그 앱(v0.4.1)으로 촬영했습니다. 프록시 프로필은 예약된 TEST-NET 주소만 사용하며 인증 정보가 없습니다. 프록시 검사 결과는 예시 데이터입니다.

<table>
  <tr>
    <th>홈 — VPN 실행 중</th>
    <th>프록시 프로필</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="홈 — VPN 실행 중" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="프록시 프로필" /></td>
  </tr>
  <tr>
    <th>프록시 추가</th>
    <th>VPN 진단</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="프록시 추가" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="VPN 진단" /></td>
  </tr>
  <tr>
    <th colspan="2">필터링한 VPN 로그</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="필터링한 VPN 로그" /></td>
  </tr>
</table>

## 언어

앱은 영어, 러시아어, 우크라이나어, 독일어, 프랑스어, 스페인어, 이탈리아어, 포르투갈어(브라질), 폴란드어, 중국어(간체), 일본어, 한국어를 지원합니다. 기본적으로 시스템 언어를 따르며, 설정 → 언어 또는 Android 13 이상에서는 시스템의 앱별 언어 설정에서도 언어를 선택할 수 있습니다. 로그 항목과 진단의 기술적인 값은 버그 보고서를 읽기 쉽도록 영어로 유지됩니다.

## 제한 사항

- IPv4만 지원합니다.
- IPv6는 명시적으로 지원하지 않습니다.
- QUIC/HTTP3가 프록시를 우회하지 않도록 UDP/443은 기본적으로 차단됩니다.
- 선택한 업스트림 프록시가 재시도 후에도 사용할 수 없으면 VPN은 fail-closed 방식으로 중지됩니다.
- 직접 연결 모드는 명시적으로 선택해야 하며, 대체 수단으로 몰래 사용되지 않습니다.
- 프록시 비밀번호는 화면, 로그, 진단에서 가려집니다.

## VPN을 계속 실행하려면

Android, 특히 삼성 One UI는 백그라운드에서 앱 프로세스를 종료할 수 있습니다. VPN을 유지하려면:

- 앱의 백그라운드 실행을 허용하세요(배터리 최적화가 켜져 있는 동안 홈에 "허용" 버튼이 있는 경고가 표시됩니다). 삼성 기기에서는 "절전 앱"에서도 앱을 빼세요.
- 원하면 Android VPN 설정에서 상시 VPN을 켜세요(설정 대화상자 → 상시 VPN). "VPN 없이 연결 차단"과 함께 쓰면 kill switch처럼 작동해, VPN이 꺼져 있는 동안 트래픽이 기기 밖으로 나가지 않습니다.

VPN이 켜진 상태에서 프로세스가 종료되면 앱이 1~2분 안에 VPN을 복원합니다. 진단 화면에서 마지막 VPN 중지 사유와 마지막 앱 종료 사유를 볼 수 있습니다.

## 빌드

```bash
make test
make build-local
```

첫 빌드는 고정된 sing-box/libbox AAR(약 97MB)을 사용자의 Gradle 캐시로 내려받습니다. `gradle/libbox.properties`에는 소스 리비전, 변경되지 않는 아티팩트 URL, 크기, SHA-256이 고정되어 있습니다. 모든 빌드는 컴파일 전에 해시와 필요한 네이티브 ABI를 검증합니다. 직접 내려받으려면:

```bash
make fetch-libbox
```

디버그 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

연결된 기기에 설치:

```bash
make install
```

## 디버그 로그

앱은 민감한 정보를 제거한 실행 로그를 앱의 로그 화면과 Android logcat에 기록합니다:

```bash
adb logcat -s ProxySwitcherVPN
```

로그에는 경로 선택, 프록시 테스트, 업스트림 모니터링 결과, 재연결 시도, 대기 시간, TUN 설정, 엔진 수명 주기, 최종 fail-closed 중지 사유가 포함됩니다. 프록시 비밀번호는 가려집니다.

## 릴리스

버전은 `VERSION`에서 읽습니다.

```bash
make update-version
make release
```

`make release`는 Gradle의 `versionCode`/`versionName`을 업데이트하고, 검사를 실행하고, 릴리스를 빌드하고, 커밋과 태그를 만든 뒤 태그를 푸시합니다.

서명된 릴리스에는 `keystore.properties`가 필요합니다. 비밀 정보나 keystore를 커밋하지 마세요.

## 라이선스

Proxy Switcher VPN은 [GNU GPL v3 이상](LICENSE)으로 배포됩니다. 릴리스에는 sing-box/libbox가 포함되므로 배포자는 서드파티 고지를 유지하고 GPLv3가 요구하는 정확히 대응하는 소스 코드도 제공해야 합니다. [서드파티 고지](THIRD_PARTY_NOTICES.md)와 [의존성 라이선스 감사](docs/legal/dependency-license-audit.md)를 참고하세요.
