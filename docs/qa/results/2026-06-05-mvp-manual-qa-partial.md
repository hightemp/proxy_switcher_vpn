# MVP Manual QA Partial Run

Date: 2026-06-05

## Scope

Partial execution of `docs/qa/manual-vpn-checklist.md` for TASK-190 against the
production `ProxyVpnService -> libbox -> Android TUN -> sing-box` path.

## Environment

- Device: Samsung SM-A165F (`RF8Y705GAPK`)
- Android: 15
- Build: debug APK installed after `verifyLibboxArtifact` and `assembleDebug`
- Test proxy reachability: local host proxy harness exposed to the device with
  `adb reverse`

Test endpoints seeded into the app database:

- SOCKS5: `127.0.0.1:1081`
- HTTP CONNECT: `127.0.0.1:1082`
- HTTPS proxy: `127.0.0.1:1083`

The HTTPS endpoint used a local self-signed certificate. That is not a valid
production HTTPS-proxy acceptance endpoint because production config must not
set `tls.insecure=true`.

## Results

| Area | Result | Notes |
| --- | --- | --- |
| VPN permission denial | Pass | Denying the Android permission left the app stopped and surfaced `VPN permission was not granted.` |
| VPN permission approval | Pass | Allowing permission started the foreground VPN flow. |
| Foreground notification | Partial | Persistent VPN notification appeared and included a `Stop` action in `dumpsys notification`; direct notification-action tapping was not completed on the Samsung notification shade. |
| TUN creation | Pass | Android reported a VPN network owned by `com.hightemp.proxy_switcher_vpn`, interface `tun0`, address `172.19.0.1/30`, DNS `172.19.0.2`, and IPv4 routes `0.0.0.0/1` plus `128.0.0.0/1`. |
| SOCKS5 outbound | Pass | Host harness observed SOCKS5 CONNECT traffic for app TCP traffic and DoH traffic. |
| HTTP outbound | Pass | Host harness observed HTTP CONNECT traffic for app TCP traffic and DoH traffic. |
| HTTPS proxy outbound | Blocked | Only a self-signed local HTTPS proxy was available; production TLS validation correctly prevents using it as a passing endpoint. |
| DNS through proxy | Pass with fix | Host harness observed DoH traffic to `1.1.1.1:443` through SOCKS5/HTTP. A device Private DNS probe to `172.19.0.2:853` was found and fixed by adding a TCP/853 reject rule scoped to the TUN DNS address. |
| UDP/443 block | Partial | Unit tests verify the sing-box UDP/443 reject/drop rule. Manual UDP/443 packet validation was not completed because the host UDP/443 sink could not bind in this environment. |
| IPv6 unsupported | Pass | Connectivity output showed IPv6 as explicitly unreachable for the VPN path; MVP remains IPv4-only. |
| Runtime upstream failure | Pass with fix | Stopping the HTTP harness while VPN was running moved the app to ERROR, removed `tun0`, stopped the service, removed the active notification, and surfaced `Selected upstream proxy failed during runtime...`. This required a protected upstream watchdog. |
| Logs and diagnostics | Partial | Home diagnostics and runtime errors updated during the run. Full Logs screen filter/clear verification was not completed in this partial pass. |

## Defects Found And Fixed

- Missing `ACCESS_NETWORK_STATE` caused libbox startup failure on device.
- Android Private DNS attempted TCP/853 to the TUN DNS address; the config now
  rejects TCP/853 to `172.19.0.2/32`.
- Runtime upstream failure did not fail closed before the selected proxy went
  away; the service now runs a protected reachability watchdog and stops the VPN
  on failure.

## Remaining Blockers

- Provide a trusted HTTPS proxy endpoint reachable from the device to complete
  HTTPS proxy outbound validation without weakening production TLS validation.
- Complete manual UDP/443 packet validation with a bindable observer or another
  reliable packet/log capture method.
- Complete notification Stop action tapping and Logs screen filter/clear checks
  on the target device.

## Cleanup Verification

After the runtime failure test:

- No `proxy_probe.py` harness process remained active.
- `adb shell cmd notification list` showed no active
  `com.hightemp.proxy_switcher_vpn` notification.
- `adb shell dumpsys connectivity` and `adb shell ip addr` showed no active
  `tun0`/`172.19.0.x` VPN interface.
