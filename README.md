# Proxy Switcher VPN

Android app for running a local VPN that routes device traffic through the
selected upstream proxy or through explicit Direct mode.

The app does not change Android system proxy settings. It uses `VpnService`
and an embedded sing-box core.

## Features

- route selection on the Home screen: `Direct` or a saved proxy;
- `SOCKS5`, `HTTP`, and `HTTPS` proxy support;
- add, edit, delete, and test proxies;
- import and export proxy lists in a format compatible with `proxy_switcher`;
- switch proxy while VPN is running without manual stop/start;
- logs, VPN diagnostics, traffic counters, and masked config preview.

## MVP Limits

- IPv4 only;
- IPv6 is explicitly unsupported;
- UDP/443 is blocked by default to avoid QUIC/HTTP3 bypass;
- if the selected upstream proxy fails, VPN stops fail-closed;
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
