# SPIKE-001: sing-box Android Embedding

Date: 2026-06-05

Status: complete. Source validation, Android AAR build proof, and emulator runtime proof completed.

## Decision

Use embedded `libbox.aar` from sing-box as the preferred MVP integration path.

Recommended architecture:

`Android apps -> Android VpnService/TUN fd -> sing-box libbox TUN inbound -> selected outbound proxy -> Internet`

Do not use a `tun2socks -> local SOCKS gateway` design as the default MVP path. The prepared upstream Android client already proves the intended shape at the source level: Android owns `VpnService.Builder` and `VpnService.protect(fd)`, while libbox owns sing-box core lifecycle, config loading, TUN options, logging/status streams, and connection events.

## Source Evidence

Android VPN permission and TUN setup:

- Official Android VPN guide says `VpnService.prepare()` returns a permission intent when consent is needed and `null` when already prepared: https://developer.android.com/develop/connectivity/vpn
- Official `VpnService.Builder` API exposes `addAddress`, `addRoute`, `addDnsServer`, and `establish`: https://developer.android.com/reference/android/net/VpnService.Builder
- `tmp/sing-box-for-android/app/src/main/AndroidManifest.xml` declares `.bg.VPNService` with `android.permission.BIND_VPN_SERVICE` and `android.net.VpnService`.
- `tmp/sing-box-for-android/app/src/main/java/io/nekohasekai/sfa/bg/VPNService.kt` extends `VpnService`, delegates lifecycle to `BoxService`, calls `protect(fd)` in `autoDetectInterfaceControl`, builds the Android TUN in `openTun(options)`, adds addresses/routes/DNS servers from `TunOptions`, calls `builder.establish()`, stores the returned `ParcelFileDescriptor`, and returns the fd to libbox.
- `tmp/sing-box/experimental/libbox/service.go` implements `adapter.PlatformInterface`; `OpenInterface()` calls the Android-provided `OpenTun`, duplicates the fd, stores it in sing-box TUN options, and returns `tun.New(*options)`.

Lifecycle and core start/stop:

- `tmp/sing-box-for-android/app/src/main/java/io/nekohasekai/sfa/bg/BoxService.kt` creates `CommandServer(this, platformInterface)`, calls `startOrReloadService(configContent, OverrideOptions())`, closes the TUN fd on stop/revoke, calls `closeService()`, closes the command server, and stops the Android service.
- This is a better fit than an embedded standalone binary because it gives direct Kotlin/Java access to platform hooks and event streams.

Build and ABI path:

- `tmp/sing-box-for-android/app/build.gradle.kts` consumes `app/libs/libbox.aar` for API 23+ flavors and `libbox-legacy.aar` for legacy, and configures ABI splits for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.
- `tmp/sing-box/cmd/internal/build_libbox/main.go` uses `gomobile bind -target android` to build `libbox.aar` with Android API 23 and `libbox-legacy.aar` with Android API 21.
- `tmp/sing-box/experimental/libbox/ffi.json` defines Android AAR artifacts with NDK `28.0.13004108`.
- Required MVP ABIs `arm64-v8a` and `x86_64` are covered by upstream Android split configuration. `armeabi-v7a` is also covered upstream.

Proxy outbound config:

- SOCKS5: `tmp/sing-box/docs/configuration/outbound/socks.md` defines `type: "socks"`, `server`, `server_port`, optional `username`, optional `password`, and `version: "5"`.
- HTTP: `tmp/sing-box/docs/configuration/outbound/http.md` defines `type: "http"` as an HTTP CONNECT proxy client with `server`, `server_port`, optional `username`, optional `password`.
- HTTPS proxy: same `http` outbound with `tls: {}` enabled. This maps to HTTP CONNECT over TLS to the proxy server.

DNS and route config:

- TUN inbound: `tmp/sing-box/docs/configuration/inbound/tun.md` supports `type: "tun"`, `address`, `mtu`, `dns_mode`, `dns_address`, `auto_route`, `strict_route`, and route addresses.
- DoH: `tmp/sing-box/docs/configuration/dns/server/https.md` defines DNS over HTTPS server config.
- Dial fields: `tmp/sing-box/docs/configuration/shared/dial.md` defines `detour` as the upstream outbound tag. DNS server dial should use the selected proxy outbound instead of direct.
- DNS hijack: `tmp/sing-box/docs/configuration/route/rule_action.md` supports `action: "hijack-dns"`.
- UDP/443 block: `tmp/sing-box/docs/configuration/route/rule.md` supports matching `network` and `port`; `tmp/sing-box/docs/configuration/route/rule_action.md` supports `action: "reject"` with `method: "drop"`; runtime validation used `{"network":"udp","port":443,"action":"reject","method":"drop"}`.

Logs, statistics, and diagnostics:

- `tmp/sing-box/experimental/libbox/command_client.go` supports command subscriptions for logs, status, groups/outbounds, and connection events.
- `tmp/sing-box/experimental/libbox/command_types.go` exposes `StatusMessage` with memory, goroutine count, connection counts, traffic availability, uplink/downlink, and totals.
- Connection events include destination, domain, protocol, outbound, uplink/downlink totals, and process/package info where available. These fields are sensitive and must stay behind the app privacy setting.

License:

- `tmp/sing-box/LICENSE` is GPLv3-or-later.
- `tmp/sing-box-for-android/LICENSE` is GPLv3-or-later.
- `tmp/sing-box-for-android/third_party/termux-app/LICENSE.md` is GPLv3 only, with exceptions for some subcomponents.
- Any release that embeds or derives from sing-box/libbox must be planned as GPL-compatible with corresponding source/build-script obligations. Do not plan a closed-source release around this integration.

## Build Proof

`gomobile` and `gobind` were installed into `/home/hightemp/go/bin` from `github.com/sagernet/gomobile/cmd/gomobile@v0.1.12` and `github.com/sagernet/gomobile/cmd/gobind@v0.1.12`. `gomobile init -v` completed with:

- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
- `ANDROID_HOME=/home/hightemp/Android/Sdk`
- `ANDROID_NDK_HOME=/home/hightemp/Android/Sdk/ndk/28.0.12433566`

The prepared upstream repo was copied to ignored local build space before building:

`rsync -a --delete --exclude '.git' --exclude 'build' tmp/sing-box/ build/spike-singbox/sing-box-src/`

The current upstream build tags were taken from `cmd/internal/build_libbox/main.go`. A stale tag set failed because current sing-box intentionally rejects deprecated `with_ech` and `with_reality_server` tags.

Successful proof builds:

- x86_64 emulator build: `build/spike-singbox/libbox-amd64.aar`
  - size: 24 MB
  - SHA-256: `2bde00d17b74b441cc046a46df0da1c81b855618dbe87d67c032b371efe2e2b0`
  - contents include `classes.jar` and `jni/x86_64/libbox.so`
- all Android ABI build: `build/spike-singbox/libbox-android-all.aar`
  - size: 93 MB
  - SHA-256: `f8dbec0658177ef3310fec8c38d917d75e0db74a1565f88ef78a96df3e0a3905`
  - contents include `classes.jar`, `jni/armeabi-v7a/libbox.so`, `jni/arm64-v8a/libbox.so`, `jni/x86/libbox.so`, and `jni/x86_64/libbox.so`

The all-ABI AAR covers required MVP ABIs `arm64-v8a` and `x86_64`. `armeabi-v7a` is also buildable.

## Runtime Proof

Runtime validation used a disposable, ignored Android prototype under:

`build/spike-singbox/prototype`

The prototype used the generated `libbox-android-all.aar`, a minimal `VpnService`, `CommandServer`, and `PlatformInterface`. It was built with:

`./gradlew -p build/spike-singbox/prototype assembleDebug`

The prototype was installed on `Medium_Phone_API_35` / `emulator-5554` and VPN consent was allowed with:

`adb shell appops set com.hightemp.proxy_switcher_vpn.spike ACTIVATE_VPN allow`

Local host proxy harness:

- SOCKS5 proxy: `10.0.2.2:1081`
- HTTP CONNECT proxy: `10.0.2.2:1082`
- HTTPS proxy: `10.0.2.2:1083` with a temporary self-signed cert and prototype config `tls.insecure=true`
- UDP/443 host sink could not bind as an unprivileged user, but sing-box reject logs proved the route rule matched and dropped UDP/443.

Validated MVP config shape:

- TUN inbound: `type: "tun"`, `address: ["172.19.0.1/30"]`, `dns_mode: "hijack"`, `dns_address: ["172.19.0.2"]`, `auto_route: true`, `strict_route: true`, `route_address: ["0.0.0.0/1", "128.0.0.0/1"]`, `stack: "gvisor"`.
- DNS: DoH server `1.1.1.1:443` with `detour: "proxy"`.
- Route rules: first `{"inbound":"tun-in","port":53,"action":"hijack-dns"}`, then `{"network":"udp","port":443,"action":"reject","method":"drop"}`.
- Route final: selected proxy outbound.
- Outbound mapping:
  - SOCKS5: `type: "socks"`, `version: "5"`, `network: "tcp"`.
  - HTTP: `type: "http"`.
  - HTTPS proxy: `type: "http"` plus `tls.enabled=true` (the prototype used `insecure=true` only for the local self-signed proxy).

Validated platform hooks:

- `startDefaultInterfaceMonitor()` must call `InterfaceUpdateListener.updateDefaultInterface(name, index, metered, constrained)` with a non-VPN interface. Without this, libbox failed outbound dialing with `no available network interface`.
- `autoDetectInterfaceControl(fd)` called Android `VpnService.protect(fd)`. Runtime logs showed `SPIKE_PROTECT ... result=true` for outbound sockets.
- `getInterfaces()` must avoid IPv6 link-local zone suffixes such as `%eth0`; the prototype filtered IPv6 interface prefixes for MVP. Before filtering, libbox panicked on `netip.ParsePrefix("fe80::...%eth0/64")`.

Runtime result summary:

- `VpnService` plus sing-box TUN start:
  - log markers: `SPIKE_TUN_ESTABLISHED`, `SPIKE_STARTED`
  - Android connectivity showed VPN network `VPN:com.hightemp.proxy_switcher_vpn.spike`, `InterfaceName: tun0`, address `172.19.0.1/30`, DNS `172.19.0.2`, routes `0.0.0.0/1`, `128.0.0.0/1`, and IPv6 `::/0 unreachable`.
- Controlled stop:
  - log marker: `SPIKE_STOPPED`
  - `dumpsys activity services com.hightemp.proxy_switcher_vpn.spike` returned no active service
  - `dumpsys connectivity` no longer showed the prototype VPN network.
- SOCKS5:
  - host proxy observed `SOCKS5 CONNECT 1.1.1.1:443`
  - app probe logged `SPIKE_HTTP_PROBE status=200 bytes=512 url=http://example.com/`
- HTTP:
  - host proxy observed `HTTP CONNECT 1.1.1.1:443` and `HTTP CONNECT ...:80`
  - app probe logged `SPIKE_HTTP_PROBE status=200 bytes=512 url=http://example.com/`
- HTTPS proxy:
  - host TLS proxy observed CONNECT traffic on the HTTPS listener, including DoH and `example.com` probe traffic
  - app probe logged `SPIKE_HTTP_PROBE status=200 bytes=512 url=http://example.com/`
- DNS through proxy:
  - device logs showed `router: match ... port=53 => hijack-dns`
  - device logs showed `dns: exchange example.com. IN A`
  - device logs showed selected outbound proxy connecting to `1.1.1.1:443`
  - host proxy observed CONNECT to `1.1.1.1:443`
- UDP/443:
  - app sent UDP to `1.1.1.1:443`
  - device logs showed `router: pre-match ... network=udp port=443 => reject(drop)` and `inbound/tun[tun-in]: reject udp connection ... to 1.1.1.1`

## Follow-Up Task Impact

SPIKE-001 is complete. Downstream full VPN/sing-box implementation tasks may proceed using embedded `libbox.aar` as the selected approach.

Implementation tasks should carry forward these requirements from the runtime proof:

- Build/fetch `libbox.aar` reproducibly and verify checksums before bundling it.
- Implement the Android `PlatformInterface` as a production wrapper, not UI code.
- Provide a real non-VPN default-interface monitor and call `updateDefaultInterface`.
- Call `VpnService.protect(fd)` from `autoDetectInterfaceControl(fd)`.
- Filter or correctly normalize IPv6 zone-bearing interface prefixes; MVP remains IPv4-only and must show IPv6 unsupported/disabled.
- Include an explicit DNS hijack route rule for TUN DNS traffic before proxy final routing.
- Use DoH or another proxy-safe DNS transport with `detour` set to the selected proxy outbound.
- Use the validated UDP/443 reject rule.
- Account for Android Private DNS / DoT attempts to the TUN DNS address (`172.19.0.2:853` appeared in runtime logs and failed through the proxy). Production config should explicitly handle or block this path and surface it in diagnostics.
- Keep connection event destination/domain fields behind the user-visible privacy setting.
