# Proxy Switcher VPN PRD

## 1. Overview

`Proxy Switcher VPN` is a new Android app in the `proxy_switcher_vpn` repository. It should reuse the general style, app structure, naming conventions, and development approach of the read-only reference project at:

`/home/hightemp/Projects/proxy_switcher/proxy_switcher`

The reference app starts a local proxy server and manages Android system proxy settings. This app must instead start a real local Android VPN with `VpnService` and route device TCP traffic through the upstream proxy selected in the app.

Preferred traffic architecture:

`Android apps -> Android VpnService/TUN fd -> embedded sing-box core -> selected upstream proxy/direct outbound -> Internet`

The old system proxy screen is not part of this product. It should be replaced by VPN diagnostics.

## 2. Goals

- Start and stop a local Android VPN from the app UI.
- Route TCP traffic through the selected upstream proxy.
- Support SOCKS5, HTTP proxy, and HTTPS proxy upstreams.
- Support username/password authentication where each proxy type allows it.
- Keep DNS proxy-safe and prevent direct DNS leaks.
- Make IPv6 unsupported explicit for MVP and prevent IPv6 leaks.
- Block UDP/443 by default for MVP to avoid QUIC/HTTP3 bypassing the selected proxy.
- Provide useful VPN, proxy, DNS, UDP, logs, counters, and diagnostics UI.
- Preserve the reference project's Compose Material 3 UI style and MVVM/Clean Architecture approach.
- Keep future implementation tasks small enough for reliable Codex sessions.

## 3. Non-goals

- Do not change Android system proxy settings.
- Do not copy the reference project blindly.
- Do not mutate the reference project.
- Do not implement full UDP proxying in MVP.
- Do not implement split tunneling in MVP.
- Do not implement per-app include/exclude routing in MVP.
- Do not provide exact per-app traffic statistics in MVP.
- Do not silently fall back to direct TCP internet when the selected upstream proxy fails.
- Do not treat sing-box embedding as final until `SPIKE-001` validates feasibility, licensing, lifecycle, logging, and statistics access.

## 4. Target Users

- Android users who need device traffic routed through a manually configured upstream proxy.
- Developers/testers who need a local VPN-based proxy switcher without ADB-only system proxy permissions.
- Users who understand that connection/domain logging is sensitive and should be controlled by app settings.

## 5. Existing Reference Project Summary

Reference path:

`/home/hightemp/Projects/proxy_switcher/proxy_switcher`

Observed reference stack and patterns:

- Kotlin 2.0.0, JVM 11.
- Android app module with Gradle Kotlin DSL and version catalog.
- Jetpack Compose with Material 3.
- Hilt for dependency injection.
- Room for proxy persistence.
- Navigation Compose.
- Foreground service for long-running proxy work.
- Single `MainActivity` with `NavHost`.
- A central `ProxyViewModel` exposing `StateFlow` UI state.
- Package layout with `data/local`, `data/repository`, `di`, service/runtime modules, `ui/screens`, `ui/theme`, `ui/viewmodel`, and `utils`.
- Proxy model includes `HTTP`, `HTTPS`, and `SOCKS5`, plus host, port, optional username/password, optional label, and enabled state.
- Existing screens include Home, Proxy List, Add/Edit Proxy, Logs, Stats, and System Proxy diagnostics.
- Release workflow builds signed APKs from version tags and publishes GitHub releases.

New project should keep these conventions, but replace system proxy management with VPN service, VPN runtime, sing-box integration, and VPN diagnostics.

## 6. Product Requirements

- The user can create, edit, delete, select, and test upstream proxies.
- The user can explicitly select Direct mode from Home. Direct mode routes
  through the VPN engine's direct outbound and must not be used as a silent
  fallback when a selected upstream proxy fails.
- If VPN is running and the user selects Direct or another proxy, the app
  applies the new route without requiring a manual stop/start.
- Transient upstream proxy or VPN engine failures should emit detailed
  diagnostics and use bounded retry/reconnect attempts before the final
  fail-closed stop.
- The user can import and export the saved proxy list using the JSON format from
  the reference `proxy_switcher` app, so proxy exports from that app can be read.
- The user can start VPN only after Android VPN permission is granted through `VpnService.prepare()`.
- The app runs the VPN as a foreground service with a clear persistent notification.
- The user can stop VPN from the app and from the foreground notification.
- The app shows current VPN state, selected proxy, counters, uptime, and last error.
- The app logs VPN lifecycle, selected proxy, upstream proxy success/failure, DNS events, blocked/bypassed UDP, reconnect attempts, counters, and last error.
- The app provides diagnostics for permission, foreground service, sing-box core, TUN interface, DNS mode, IPv4 route, IPv6 unsupported state, UDP handling, selected proxy, counters, last error, and generated config preview with masked secrets.
- The app uses the provided VPN logo PNG as its Android launcher icon.

## 7. Functional Requirements

### Proxy CRUD

- Store proxies locally.
- Supported protocols:
  - SOCKS5.
  - HTTP.
  - HTTPS, defined as HTTP CONNECT over TLS to the proxy server.
- Required fields:
  - protocol.
  - host.
  - port.
- Optional fields:
  - label.
  - username.
  - password.
- Validate host and port before save.
- Mask proxy passwords in logs, config previews, and UI summaries.
- Import/export proxy list as a JSON array compatible with the reference
  `proxy_switcher` app fields: `host`, `port`, `type`, optional `username`,
  optional `password`, optional `label`, and `isEnabled`.

### VPN Lifecycle

- Request VPN permission through `VpnService.prepare()`.
- Start the foreground VPN service only after permission is granted.
- Build an Android TUN interface through `VpnService.Builder`.
- Configure IPv4 for MVP.
- Avoid IPv6 route capture unless IPv6 is fully supported later.
- Stop VPN on user request.
- Retry/reconnect on transient selected upstream proxy failure, then stop VPN
  fail-closed if the retry budget is exhausted.
- Publish lifecycle state to UI through stable observable state.

### sing-box Core

- Generate sing-box config from selected proxy and runtime settings.
- Start and stop sing-box through an engine abstraction.
- Connect the Android VPN/TUN setup to sing-box TUN inbound.
- Collect core logs/events/statistics if available.
- Fail closed for TCP when the selected upstream proxy remains unavailable
  after bounded retry/reconnect attempts.

### DNS

- DNS must go through the selected proxy or another proxy-safe route.
- Prefer DNS over HTTPS or DNS over TCP configured in sing-box.
- DNS query events must be logged when logging is enabled.
- Direct device DNS must not be the default.

### UDP

- TCP goes through selected proxy.
- DNS goes through selected proxy.
- UDP/443 is blocked by default to force TCP/TLS fallback and prevent browser proxy bypass through QUIC/HTTP3.
- Other non-DNS UDP can be direct-bypassed only if the implementation proves that protected direct routing is safe and does not create leaks or loops.
- If safe direct UDP bypass is not implemented, non-DNS UDP behavior is a known MVP limitation and must be shown in diagnostics.

## 8. Non-functional Requirements

- Keep UI responsive while VPN/core work runs in background services or coroutines.
- Avoid broad rewrites and large patches.
- Keep architecture testable by introducing interfaces and fakes before native/VPN integration.
- Store sensitive values using the safest local pattern available in the project. Do not store proxy passwords in plaintext if a safer pattern exists or is added.
- Mask secrets in logs and generated config preview.
- Avoid direct TCP internet fallback on proxy failure.
- Keep APK size and native crash diagnostics visible as risks when embedding sing-box.
- Make builds reproducible where possible, especially for native binaries/libraries.

## 9. VPN Architecture

MVP target:

`Android apps -> VpnService TUN -> sing-box TUN inbound -> selected outbound proxy -> Internet`

Android Kotlin layer responsibilities:

- Compose UI.
- Room/DataStore or equivalent settings persistence.
- Proxy CRUD.
- VPN permission flow.
- Foreground service lifecycle.
- TUN setup.
- sing-box config generation.
- sing-box engine lifecycle.
- Socket protection strategy through `VpnService.protect()` or equivalent integration.
- Status, statistics, logs, and diagnostics state.

sing-box responsibilities:

- TUN inbound.
- TCP routing.
- Selected upstream outbound.
- DNS handling.
- Direct/block behavior where required.
- Core lifecycle hooks, logs, and events where available.

Loop prevention is mandatory. Any sockets used by the upstream proxy/core outbound traffic must be protected from being routed back into the VPN when Android API and the chosen embedding approach require it.

## 10. sing-box Core Architecture

sing-box is the primary VPN/proxy engine candidate. The docs and tasks choose sing-box as the preferred approach, but implementation must not proceed past abstractions and spike work until `SPIKE-001` is completed.

Potential embedding approaches, in preferred order:

1. Reuse or build from the sing-box Android client/core integration approach.
2. Build sing-box as Android native libraries for required ABIs and call it through JNI or a small Kotlin wrapper.
3. Run sing-box as an embedded binary managed by the Android foreground service if library embedding is too complex.
4. Fall back to another core only if sing-box cannot satisfy MVP requirements.

Required ABI targets:

- `arm64-v8a`.
- `x86_64` for emulator.
- `armeabi-v7a` optional if easy and supportable.

Primary risks:

- GPLv3-or-later license implications.
- Android embedding complexity.
- JNI versus process boundary tradeoffs.
- APK size.
- Native crash diagnostics.
- Log collection.
- Core config generation.
- Core lifecycle management.
- Compatibility with Android `VpnService`.
- Reproducible native builds.
- CI build complexity.

## 11. Proxy Architecture

Proxy protocol mapping for sing-box config:

- SOCKS5 maps to `socks` outbound.
- HTTP proxy maps to `http` outbound.
- HTTPS proxy maps to `http` outbound with TLS enabled.
- Username/password maps to the matching sing-box outbound auth fields.

Failure behavior:

- If the selected upstream proxy fails during start, stop VPN and show the error.
- If the selected upstream proxy becomes unavailable during runtime, stop VPN for MVP unless a future task implements explicit reconnect with fail-closed behavior.
- Do not route proxied TCP traffic directly as fallback.
- Reconnect attempts can be logged, but automatic reconnect is post-MVP unless simple and safe.

## 12. DNS Architecture

DNS must be explicit in generated sing-box config.

MVP DNS behavior:

- Use DNS over HTTPS or DNS over TCP through the selected outbound/proxy-safe route.
- Log DNS query, result, failure, and server information where available.
- Expose DNS mode and last DNS error on diagnostics.
- Treat direct DNS as a leak unless a later feature deliberately exposes a user-controlled mode.

## 13. UDP Behavior

Android VPN captures IP packets, so TCP-only proxying still needs a clear UDP strategy.

MVP default:

- DNS UDP must not leak directly.
- UDP/443 is blocked by default.
- Blocked UDP events are counted and logged.
- Other non-DNS UDP is either blocked or bypassed only after spike validation proves safe protected routing.

Diagnostics must show:

- UDP/443 block status.
- Non-DNS UDP mode.
- Blocked UDP count.
- Bypassed UDP count if bypass exists.
- Known limitations.

## 14. IPv4/IPv6 Behavior

MVP supports IPv4 only.

- Configure IPv4 address and route on the VPN interface.
- Do not claim IPv6 support.
- Disable, block, or explicitly exclude IPv6 to avoid IPv6 leaks.
- Diagnostics must show IPv6 as unsupported/disabled for MVP.
- Post-MVP may add IPv6 routing once DNS, TCP, UDP, leak tests, and proxy/core behavior are validated.

## 15. Data Model

Initial Room entities should follow the reference style but adapt names to VPN:

### ProxyEntity

- `id: Long`.
- `host: String`.
- `port: Int`.
- `type: ProxyType`.
- `username: String?`.
- `password: String?` or encrypted/secure reference if secure storage is added.
- `label: String?`.
- `isEnabled: Boolean`.
- Optional post-MVP fields: test result, last used timestamp, last error, tags.

### ProxyType

- `SOCKS5`.
- `HTTP`.
- `HTTPS`.

### Runtime State

This can be in memory for MVP, exposed as `StateFlow`:

- VPN status.
- Selected proxy.
- Bytes in.
- Bytes out.
- Active connections if feasible.
- Total connections.
- Failed connections.
- DNS queries.
- Blocked UDP count.
- Bypassed UDP count.
- Last error.
- Uptime.
- sing-box status.
- TUN status.

### Settings

Use DataStore or the existing project storage style:

- Selected proxy id.
- Domain/destination logging enabled.
- Config preview enabled.
- DNS mode.
- UDP mode, initially fixed for MVP.
- Last acknowledged privacy disclosure if added.

## 16. UI/UX Requirements

Use the reference project's simple Compose Material 3 style:

- Single activity.
- Navigation Compose routes.
- Top app bar actions.
- Material 3 cards, buttons, dropdowns, and outlined fields.
- Central `StateFlow` state from a Hilt ViewModel.
- Screens under `ui/screens`.
- Theme under `ui/theme`.

Expected screens:

1. Home screen
   - selected route dropdown/card with Direct and saved proxies.
   - proxy management action outside the route dropdown.
   - single START/STOP VPN button directly below the route dropdown.
   - current VPN status.
   - current selected route.
   - bytes in/out.
   - last error.
   - quick diagnostics summary.

2. Proxy list screen
   - list saved proxies.
   - active/selected indicator.
   - add/edit/delete proxy actions.
   - test proxy action if available.

3. Add/Edit proxy screen
   - protocol selection: SOCKS5, HTTP, HTTPS.
   - host.
   - port.
   - username/password optional.
   - validation.
   - save/cancel.

4. Logs screen
   - filter by level/type.
   - clear logs.
   - timestamps.
   - VPN/proxy/DNS/errors/events.

5. VPN diagnostics screen
   - VPN permission status.
   - foreground service status.
   - sing-box core status.
   - TUN interface status.
   - DNS mode/status.
   - IPv4 route status.
   - IPv6 disabled/unsupported status.
   - UDP handling status.
   - selected proxy details.
   - bytes/counters.
   - last error.
   - generated config preview with secrets masked.

Do not copy the old system proxy screen as-is.

## 17. Logging and Diagnostics

The app must log:

- VPN start/stop.
- Selected proxy.
- Proxy connection success/failure.
- DNS events.
- Blocked UDP or bypassed UDP.
- Reconnect attempts.
- Bytes in/out snapshots.
- Last error.
- Domains and destination addresses of connections when enabled.

Diagnostics must show:

- VPN permission state.
- Foreground service state.
- sing-box core state.
- TUN interface state.
- DNS status.
- IPv4 route state.
- IPv6 unsupported state.
- UDP mode.
- Selected proxy summary.
- Last generated config preview with secrets masked.
- Last error.

Connection/domain logging is sensitive. The app must include a user-visible setting or disclosure before recording detailed domains and destination addresses. Logs must not include proxy passwords or other secrets.

## 18. Statistics

Display useful statistics:

- Current VPN status.
- Selected proxy.
- Bytes in.
- Bytes out.
- Active connections if feasible.
- Total connections.
- Failed connections.
- DNS queries.
- Blocked UDP count.
- Bypassed UDP count.
- Last error.
- Uptime.

Do not rely only on Android `TrafficStats` for accurate VPN-wide stats.

Preferred sources:

- sing-box logs/API/events if available.
- Kotlin service lifecycle counters.
- Generated config state.
- Connection/error/DNS event parsing if needed.

Exact per-app traffic statistics are not part of MVP.

## 19. Privacy and Security

- Proxy passwords are sensitive and must be masked in UI summaries, logs, and config previews.
- Do not store proxy passwords in plaintext if the project has or adds a safer local storage pattern.
- Domain and destination address logging is sensitive. Make it user-visible and controllable.
- VPN state should fail closed for TCP when upstream proxy fails.
- DNS must not leak directly by default.
- IPv6 unsupported state must be explicit.
- UDP/443 must be blocked by default in MVP.
- Generated config preview must mask credentials.
- Release signing secrets must remain out of the repository.

## 20. Android Permissions/Services

Expected Android components and permissions:

- `VpnService` subclass with `android.permission.BIND_VPN_SERVICE`.
- Foreground service declaration.
- Foreground service notification channel.
- Foreground service type appropriate for VPN/data sync behavior on supported Android versions.
- `android.permission.INTERNET`.
- `android.permission.FOREGROUND_SERVICE`.
- Android 13+ notification permission flow if notifications are required.

Runtime flow:

1. User taps START VPN.
2. ViewModel checks selected proxy and validates state.
3. App calls `VpnService.prepare()`.
4. If permission intent is returned, app launches permission flow.
5. After permission, app starts foreground VPN service.
6. Service creates TUN interface and starts engine.
7. UI observes runtime status.

## 21. sing-box Integration Decision

Documentation decision:

- Plan around embedded sing-box as the primary engine.
- Do not use `TUN -> tun2socks -> local SOCKS5 gateway -> upstream proxy` as the default architecture.
- Mention local gateway/tun2socks only as fallback if sing-box embedding cannot satisfy MVP.
- Complete `SPIKE-001: Validate sing-box Android embedding` before full VPN implementation tasks.

The spike must validate:

- How to embed or run sing-box core.
- `VpnService` plus sing-box TUN start/stop.
- SOCKS5 outbound.
- HTTP outbound.
- HTTPS proxy outbound using HTTP CONNECT over TLS.
- DNS through proxy.
- UDP/443 block behavior.
- Log/statistics access.
- License implications.

## 22. GPL/License Risk

sing-box licensing must be validated before implementation proceeds.

Risks to document and resolve:

- Whether the selected sing-box artifacts/source have GPLv3-or-later obligations.
- Whether app distribution must comply with reciprocal source obligations.
- Whether linked libraries, embedded binaries, or reused Android client code change obligations.
- Whether CI must publish corresponding source/build scripts for native artifacts.
- Whether another core is needed if licensing is incompatible with distribution goals.

No release plan should assume sing-box is legally acceptable until the spike confirms it.

## 23. Testing Strategy

Unit tests:

- Proxy form validation.
- Proxy entity/repository behavior.
- sing-box config generation.
- Secret masking.
- DNS config generation.
- UDP routing/block rules.
- Runtime state reducers/counters.
- Log parsing if used.

Integration/manual tests:

- VPN permission flow.
- Foreground service start/stop.
- TUN interface creation.
- SOCKS5 outbound.
- HTTP outbound.
- HTTPS proxy outbound.
- DNS through proxy.
- UDP/443 block.
- Upstream proxy failure stops VPN.
- IPv6 unsupported behavior does not leak.
- Logs and diagnostics update.

Smoke commands:

- `./gradlew test`.
- `./gradlew connectedAndroidTest` when a device/emulator is available.
- `./gradlew assembleDebug`.
- Release/CI tasks once signing and native artifacts are configured.

## 24. Release/CI Requirements

Follow the reference release workflow style where applicable:

- GitHub Actions on `v*.*.*` tags.
- JDK 17.
- Gradle wrapper executable step.
- Signed release APK using repository secrets.
- APK signature verification.
- APK artifact upload.
- GitHub release publishing.

VPN/sing-box additions:

- CI must build or fetch reproducible sing-box artifacts for required ABIs.
- CI must fail if required native artifacts are missing.
- CI should retain native symbol files or crash diagnostics artifacts if available.
- Release notes should disclose VPN behavior, IPv4-only MVP, UDP limitations, and domain logging privacy controls.

## 25. Known Risks

- sing-box Android embedding may be more complex than expected.
- `VpnService.protect()` integration may differ by embedding approach.
- Native library or binary distribution may increase APK size.
- GPLv3-or-later obligations may affect app distribution.
- Exact stats may require log parsing or core API access.
- DNS leaks are likely if DNS config is incomplete.
- IPv6 leaks are possible if unsupported behavior is not explicit.
- UDP behavior can create bypasses if not designed carefully.
- Foreground service and notification requirements vary by Android version.
- Native crash diagnostics may be difficult without symbols and structured logging.

## 26. MVP Scope

Included:

- Proxy CRUD for SOCKS5, HTTP, HTTPS with optional auth.
- Compatible proxy list import/export using the reference app JSON format.
- Selected proxy persistence.
- VPN permission flow.
- Foreground VPN service.
- IPv4-only TUN setup.
- sing-box engine abstraction and validated integration.
- sing-box config generator.
- TCP through selected proxy.
- DNS through selected proxy/proxy-safe route.
- UDP/443 blocked by default.
- Logs, statistics, and diagnostics.
- Fail-closed behavior on upstream failure.

Excluded:

- Full UDP proxying.
- Split tunneling.
- Per-app routing.
- Exact per-app stats.
- IPv6 support.
- Automatic reconnect unless trivial and fail-closed.
- Advanced rule-based routing.

## 27. Post-MVP Roadmap

- Full UDP proxying if safe and supported.
- IPv6 routing and leak tests.
- Split tunneling and per-app include/exclude.
- Automatic reconnect with backoff and fail-closed policy.
- Per-app traffic statistics if core/platform support allows.
- Proxy health checks and latency display.
- Advanced DNS server selection.
- Configurable QUIC/UDP policy.
- Structured log export with redaction.
- Native crash symbol upload and diagnostics.
