# MVP Manual QA Run

Date: 2026-06-05

## Scope

Execution of `docs/qa/manual-vpn-checklist.md` for TASK-190 against the
production `ProxyVpnService -> libbox -> Android TUN -> sing-box` path.

## Environment

- Device: Samsung SM-A165F (`RF8Y705GAPK`)
- Android: 15
- Build: debug APK installed after `verifyLibboxArtifact`, unit tests, and
  `assembleDebug`
- Test proxy reachability: local host proxy harness exposed to the device with
  `adb reverse`

Test endpoints seeded into the app database:

- SOCKS5: `127.0.0.1:1081`
- HTTP CONNECT: `127.0.0.1:1082`
- HTTPS proxy: `test.i.qip.sh:1083`, resolved on the device to `127.0.0.1`
  through `adb reverse tcp:1083 tcp:1083`

The HTTPS endpoint used qip.sh's publicly trusted wildcard certificate for
`*.i.qip.sh`; production TLS validation remained enabled and no
`tls.insecure=true` config was used.

## Results

| Area | Result | Notes |
| --- | --- | --- |
| VPN permission denial | Pass | Denying the Android permission left the app stopped and surfaced `VPN permission was not granted.` |
| VPN permission approval | Pass | Allowing permission started the foreground VPN flow. |
| Foreground notification | Pass | Persistent VPN notification appeared. Expanding the Samsung notification exposed `Stop`; tapping it removed `tun0`, removed the notification, and stopped the service. |
| TUN creation | Pass | Android reported a VPN network owned by `com.hightemp.proxy_switcher_vpn`, interface `tun0`, address `172.19.0.1/30`, DNS `172.19.0.2`, and IPv4 routes `0.0.0.0/1` plus `128.0.0.0/1`. |
| SOCKS5 outbound | Pass | Host harness observed SOCKS5 CONNECT traffic for app TCP traffic and DoH traffic. |
| HTTP outbound | Pass | Host harness observed HTTP CONNECT traffic for app TCP traffic and DoH traffic. |
| HTTPS proxy outbound | Pass with fix | Trusted qip.sh HTTPS proxy harness accepted production TLS. The active config used `server: "127.0.0.1"` with `tls.server_name: "test.i.qip.sh"` and the host harness observed repeated TLS-wrapped HTTP CONNECT traffic. |
| DNS through proxy | Pass with fixes | Host harness observed DoH traffic to `1.1.1.1:443` through SOCKS5/HTTP and HTTPS proxy modes. A device Private DNS probe to `172.19.0.2:853` was found and fixed by adding a TCP/853 reject rule scoped to the TUN DNS address. |
| UDP/443 block | Pass with capture limitation | Active config contained `{"network":"udp","port":443,"action":"reject","method":"drop"}`. A device UDP/443 netcat probe did not tear down the VPN and produced no HTTPS proxy CONNECT traffic; packet-level capture was not available on the non-rooted device. |
| IPv6 unsupported | Pass | Connectivity output showed IPv6 as explicitly unreachable for the VPN path; MVP remains IPv4-only. |
| Runtime upstream failure | Pass with fix | Stopping the HTTP harness while VPN was running moved the app to ERROR, removed `tun0`, stopped the service, removed the active notification, and surfaced `Selected upstream proxy failed during runtime...`. This required a protected upstream watchdog. |
| Logs and diagnostics | Pass | Home diagnostics updated during the run. Logs screen level filtering showed empty state for `ERROR`, type filtering retained `VPN` lifecycle entries, and Clear logs produced `No logs`. |

## Defects Found And Fixed

- Missing `ACCESS_NETWORK_STATE` caused libbox startup failure on device.
- Android Private DNS attempted TCP/853 to the TUN DNS address; the config now
  rejects TCP/853 to `172.19.0.2/32`.
- Runtime upstream failure did not fail closed before the selected proxy went
  away; the service now runs a protected reachability watchdog and stops the VPN
  on failure.
- Active-VPN proxy watchdog checks resolved proxy hostnames through the VPN DNS
  path; proxy reachability checks now resolve/bind through a non-VPN network
  while the VPN is active.
- HTTPS proxy config could not safely bootstrap a domain proxy host inside
  sing-box. The successful proxy probe now carries the resolved proxy address
  into config generation; HTTPS config preserves the original hostname in
  `tls.server_name`.
- The ADB-reverse HTTPS proxy resolves to loopback; sing-box auto interface
  detection is now disabled only for resolved loopback proxy endpoints so the
  core can reach device loopback while real upstream proxies retain interface
  auto-detection.

## Residual Limitations

- UDP/443 was verified by generated config, device probe behavior, and absence
  of HTTPS proxy CONNECT traffic. A packet capture was not possible on this
  non-rooted Samsung device.

## Cleanup Verification

After the runtime failure and notification Stop tests:

- No `proxy_probe.py` harness process remained active.
- `adb shell cmd notification list` showed no active
  `com.hightemp.proxy_switcher_vpn` notification.
- `adb shell dumpsys connectivity` and `adb shell ip addr` showed no active
  `tun0`/`172.19.0.x` VPN interface.
