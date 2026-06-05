# Manual VPN QA Checklist

Use this checklist for real device/emulator validation of Proxy Switcher VPN MVP behavior. It is intended for builds with the production `ProxyVpnService -> libbox CommandServer -> VpnService.Builder TUN -> sing-box` runtime path available; a foreground-service-only shell is not sufficient for the TUN, DNS, UDP, or outbound proxy checks.

## Prerequisites

- Android emulator or device with the debug APK installed.
- `adb` connected to the target device.
- Production native artifact verification passes before installing:

```sh
./gradlew verifyLibboxArtifact assembleDebug
```

- The build includes `app/libs/libbox.aar`, and diagnostics identify the core as `sing-box/libbox`.
- A clean app data state, unless testing upgrade behavior:

```sh
adb shell pm clear com.hightemp.proxy_switcher_vpn
```

- Test upstream proxies reachable from the device.
  - Emulator host proxies can use `10.0.2.2`.
  - Spike-proven local harness ports:
    - SOCKS5: `10.0.2.2:1081`
    - HTTP CONNECT: `10.0.2.2:1082`
    - HTTPS proxy: `10.0.2.2:1083`
- A way to observe host proxy traffic and Android logs:

```sh
adb logcat | rg "sing-box|Proxy Switcher VPN|VPN|dns|udp|reject|protect"
```

## Baseline App Checks

1. Launch the app.
2. Open Diagnostics.
3. Verify the stopped baseline.

Expected results:

- Home shows no running VPN.
- Diagnostics show VPN permission not granted or unknown.
- Foreground service is stopped.
- sing-box/libbox is shown as `libbox_stopped`.
- IPv4 route is enabled for MVP config.
- IPv6 is shown as unsupported/disabled.
- UDP/443 is shown as blocked.
- Config preview is absent until a proxy is selected.
- No system proxy controls or `WRITE_SECURE_SETTINGS` prompts appear.

## Proxy CRUD And Selection

1. Add SOCKS5, HTTP, and HTTPS proxy entries with host, port, optional label, optional username/password.
2. Save each proxy.
3. Edit one proxy and verify values persist.
4. Delete a non-selected proxy.
5. Select each remaining proxy in turn.

Expected results:

- List renders all saved proxies.
- Selected indicator moves to the chosen proxy.
- Passwords are not shown in proxy list, logs, diagnostics summary, or config preview.
- START VPN remains disabled when no valid selected proxy exists.

## Proxy Test Action

Run Test from the proxy list for SOCKS5, HTTP, and HTTPS entries.

Expected results:

- Reachable proxies show success.
- Unreachable or wrong-auth proxies show failure.
- Failed tests do not start or stop VPN state.
- Failure messages do not include username or password.

## VPN Permission Flow

1. Select a valid proxy.
2. Tap START VPN.
3. Deny/cancel the Android VPN permission dialog.
4. Tap START VPN again and allow permission.

Expected results:

- Denial leaves the app stopped.
- Home shows the permission error after denial.
- Allowing permission starts the foreground VPN flow.
- No duplicate permission dialogs are launched from rapid taps.

## Foreground Service And Notification

1. Start VPN with permission granted.
2. Pull notification shade.
3. Tap the notification Stop action.

Expected results:

- Persistent notification appears while VPN is active.
- Notification Stop stops the service.
- Home and Diagnostics return to stopped state.
- Repeated STOP actions are idempotent and do not crash.

## TUN Interface Creation

After VPN start, inspect connectivity:

```sh
adb shell dumpsys connectivity
adb shell ip addr
adb shell ip route
```

Expected results:

- Android reports a VPN network for `com.hightemp.proxy_switcher_vpn`.
- TUN interface is present.
- TUN address includes `172.19.0.1/30`.
- DNS includes `172.19.0.2`.
- IPv4 routes include `0.0.0.0/1` and `128.0.0.0/1`.
- IPv6 is disabled or explicitly unreachable for MVP.

## SOCKS5 Outbound

1. Select the SOCKS5 proxy.
2. Start VPN.
3. Generate TCP traffic from a browser or test app, for example `http://example.com/`.
4. Observe the host SOCKS5 proxy.

Expected results:

- Host proxy observes SOCKS5 CONNECT traffic.
- App traffic succeeds through the proxy.
- No direct TCP fallback appears when the proxy is stopped.
- Logs show VPN/proxy lifecycle without credentials.

## HTTP Outbound

Repeat the SOCKS5 flow with the HTTP proxy selected.

Expected results:

- Host proxy observes HTTP CONNECT traffic.
- TCP traffic succeeds through the HTTP proxy.
- DNS still routes through the selected proxy path.
- No direct TCP fallback occurs on proxy failure.

## HTTPS Proxy Outbound

Repeat the flow with the HTTPS proxy selected.

Expected results:

- Host TLS proxy observes CONNECT traffic over TLS.
- Config preview maps HTTPS proxy to sing-box `http` outbound with TLS enabled.
- Production config must not use `tls.insecure=true` except in an explicit local test build.
- Credentials remain masked.

## DNS Through Proxy

1. Start VPN with each proxy type.
2. Open a domain that is not cached.
3. Observe device logs and host proxy logs.

Expected results:

- Device logs show DNS hijack or DNS exchange events.
- Host proxy observes traffic to the configured DoH server, currently `1.1.1.1:443`.
- No direct UDP/TCP DNS to the local network resolver is observed.
- Diagnostics show DNS as proxy-safe.

## UDP/443 And QUIC Block

1. Start VPN.
2. Generate QUIC-like UDP/443 traffic, for example from a browser with HTTP/3 enabled or a small UDP sender to `1.1.1.1:443`.
3. Observe logs.

Expected results:

- UDP/443 is rejected/dropped.
- Logs include a UDP reject/drop event.
- Browser traffic falls back to TCP/TLS where applicable.
- Host/proxy logs do not show UDP/443 bypassing the selected proxy.

## IPv6 Unsupported Behavior

1. Start VPN.
2. Visit an IPv6-capable endpoint or inspect routes with `adb shell ip -6 route`.
3. Check Diagnostics.

Expected results:

- Diagnostics show IPv6 unsupported/disabled for MVP.
- No IPv6 route leaks around the VPN.
- If Android reports an IPv6 default route, it is unreachable through the MVP VPN path and treated as a failure to fix before release.

## Upstream Failure Fail-Closed

1. Select a proxy entry that points to an unavailable host/port, or stop the selected upstream proxy.
2. Tap START VPN.
3. Repeat while VPN is already running by stopping the upstream proxy.

Expected results:

- Start failure stops the VPN/service.
- Runtime state moves to error/stopped with a visible last error.
- Logs contain a fail-closed failure.
- No direct TCP internet fallback occurs.
- Credentials are masked in logs and diagnostics.

## Logs And Diagnostics

1. Start and stop VPN.
2. Run proxy tests.
3. Trigger DNS and UDP/443 events.
4. Open Logs and Diagnostics.

Expected results:

- Logs update live.
- Level and type filters work.
- Clear logs empties the list.
- Diagnostics show permission, foreground service, sing-box, TUN, DNS, IPv4, IPv6, UDP, selected proxy, counters, last error, and masked config preview.
- Diagnostics identify the active core as `sing-box/libbox`, with `libbox_running` while VPN is active.
- Detailed domain/destination data appears only when the privacy disclosure is accepted and detailed logging is enabled.

## Rapid Start/Stop

1. Rapidly tap START VPN multiple times.
2. Rapidly tap STOP VPN multiple times.
3. Use the notification Stop action during start and while running.

Expected results:

- Only one engine/service instance starts.
- STOP is idempotent.
- Permission cancellation leaves the app stopped.
- Process recreation or task removal stops cleanly and does not leave stale running UI state.

## Pass Criteria

The build passes manual QA only if all expected results above are met for SOCKS5, HTTP, and HTTPS proxy types. Any DNS leak, IPv6 leak, UDP/443 bypass, direct TCP fallback, credential exposure, or stuck foreground service is a release blocker.
