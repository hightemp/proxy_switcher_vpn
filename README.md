# Proxy Switcher VPN

<p align="center">
  <img src="proxy_switcher_vpn_logox1254.png" width="100" alt="Proxy Switcher VPN" />
</p>

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/hightemp/proxy_switcher_vpn)](https://github.com/hightemp/proxy_switcher_vpn/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/hightemp/proxy_switcher_vpn/total)](https://github.com/hightemp/proxy_switcher_vpn/releases)
[![Android CI](https://img.shields.io/github/actions/workflow/status/hightemp/proxy_switcher_vpn/android-ci.yml?branch=main&label=ci)](https://github.com/hightemp/proxy_switcher_vpn/actions/workflows/android-ci.yml)
[![Android 7.0+](https://img.shields.io/badge/android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
![](https://asdertasd.site/counter/proxy_switcher_vpn)

Android app for running a local VPN that routes device traffic through the
selected upstream proxy or through explicit Direct mode.

The app does not change Android system proxy settings. It uses `VpnService`
and an embedded sing-box core.

## Limits

- IPv4 only;
- IPv6 is explicitly unsupported;
- UDP/443 is blocked by default to avoid QUIC/HTTP3 bypass;
- if the selected upstream proxy remains unavailable after retries, VPN stops
  fail-closed;
- Direct mode is explicit and is never used as a silent fallback;
- proxy passwords must be masked in UI, logs, and diagnostics.

## Build

```bash
make test
make build-local
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device:

```bash
make install
```

## Debug Logs

The app writes sanitized runtime logs to the in-app Logs screen and Android
logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

Logs include route selection, proxy tests, upstream monitor results, reconnect
attempts, backoff, TUN setup, engine lifecycle, and final fail-closed reasons.
Proxy passwords are masked.

## Release

The version is read from `VERSION`.

```bash
make update-version
make release
```

`make release` updates Gradle `versionCode`/`versionName`, runs checks, builds
the release, creates a commit/tag, and pushes the tag.

A signed release requires `keystore.properties`. Do not commit secrets or the
keystore.
