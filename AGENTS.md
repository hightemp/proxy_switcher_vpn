# AGENTS.md

Project map and rules for AI agents working on `proxy_switcher_vpn`.

## 1. Project Identity

- Repository/project name: `proxy_switcher_vpn`.
- App name: `Proxy Switcher VPN`.
- Android package/application id: `com.hightemp.proxy_switcher_vpn`.
- Product: Android VPN app that routes device TCP traffic through the selected upstream proxy using `VpnService` and embedded sing-box core.
- MVP is IPv4-only. IPv6 is unsupported/disabled for MVP.

## 2. Source Of Truth Docs

Before changing code, always read:

- `AGENTS.md`.
- `PRD.md`.
- The relevant section of `TASKS.md`.

Do not implement requirements that are only implied. If a new requirement is needed, update `PRD.md` or `TASKS.md` in the same patch when appropriate.

## 3. Reference Project Path And Read-only Rule

Reference project:

`/home/hightemp/Projects/proxy_switcher/proxy_switcher`

Prepared local upstream repositories for VPN/sing-box research:

- `tmp/sing-box`
- `tmp/sing-box-for-android`
- `tmp/sing-box-for-android/third_party/termux-app`

Rules:

- Treat the reference project as read-only.
- Treat prepared upstream repositories in `tmp/` as read-only references unless a task explicitly asks for a local prototype there.
- Do not modify, format, delete, move, or generate files inside the reference project.
- Copy only needed patterns/files manually into this repository.
- Do not blindly duplicate the whole reference project.
- Use targeted reads from the reference project.

Useful reference files:

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `AGENTS.md`
- `.github/workflows/release.yml`
- `app/src/main/java/com/hightemp/proxy_switcher/MainActivity.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ProxySwitcherApp.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyEntity.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyDao.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/data/local/AppDatabase.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/data/repository/ProxyRepository.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/di/DatabaseModule.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ui/screens/ProxyListScreen.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ui/screens/AddEditProxyScreen.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ui/screens/LogsScreen.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ui/screens/SystemProxyScreen.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/ui/viewmodel/ProxyViewModel.kt`
- `app/src/main/java/com/hightemp/proxy_switcher/utils/AppLogger.kt`

## 4. Development Rules

- Keep changes small and reviewable.
- Do not make broad rewrites.
- Do not implement future tasks early.
- Update `TASKS.md` status after completing each task.
- Each task should list exact files likely to be touched before implementation starts.
- Prefer existing local patterns over new abstractions.
- Use Kotlin, Android, Jetpack Compose, Material 3, MVVM/Clean Architecture style, Hilt, Room, DataStore or the selected local settings approach, foreground services, Gradle Kotlin DSL.
- Keep package names under `com.hightemp.proxy_switcher_vpn`.
- Avoid unrelated formatting churn.

## 5. Context Management Rules

- Codex context is limited. Read only what is needed.
- Use `rg`, `rg --files`, `find`, and targeted file reads.
- Do not read the whole reference project unless a task explicitly requires it.
- Prefer narrow searches by class/file name.
- When inspecting large files, read relevant ranges.
- Keep summaries in docs instead of pasting large source snippets.

## 6. Architecture Rules

- Preserve the reference project architecture style:
  - single `MainActivity`.
  - Navigation Compose.
  - `ui/screens`.
  - `ui/theme`.
  - Hilt ViewModels.
  - Room entities/DAO/database.
  - repository layer.
  - service/runtime state layer.
  - utility logger.
- Replace system proxy architecture with VPN architecture.
- For risky VPN/native work, first create interfaces and fake implementations, then integrate real engine code.
- Do not bind UI directly to native/sing-box APIs. Use Kotlin interfaces and state models.
- Keep engine lifecycle separate from config generation.
- Keep diagnostics state explicit and testable.

## 7. UI Rules

- Preserve the reference app's Compose Material 3 style.
- Use practical app screens, not a marketing landing page.
- Expected routes/screens:
  - Home.
  - Proxy list.
  - Add/edit proxy.
  - Logs.
  - VPN diagnostics.
- The old System Proxy screen must not be copied as-is.
- Replace system proxy wording and controls with VPN diagnostics.
- Home must show selected proxy, START/STOP VPN, status, bytes in/out, last error, and diagnostics summary.
- Add/Edit proxy must support SOCKS5, HTTP, and HTTPS.
- Logs must support filtering and clearing.
- Diagnostics must show permission, foreground service, sing-box, TUN, DNS, IPv4, IPv6 unsupported, UDP policy, selected proxy, counters, last error, and masked config preview.

## 8. VPN/sing-box Rules

- Preferred architecture:

`Android apps -> Android VpnService/TUN fd -> embedded sing-box core -> selected upstream proxy/direct outbound -> Internet`

- sing-box is the primary engine candidate.
- Do not use `TUN -> tun2socks -> local SOCKS5 gateway -> upstream proxy` as the default architecture.
- A local gateway/tun2socks approach may be used only as fallback if sing-box cannot satisfy MVP.
- Complete `SPIKE-001: Validate sing-box Android embedding` before full VPN implementation.
- Required ABI targets:
  - `arm64-v8a`.
  - `x86_64`.
  - `armeabi-v7a` optional.
- Use `VpnService.prepare()` for permission flow.
- Run VPN as a foreground service.
- Use `VpnService.protect()` or equivalent protection for upstream/core outbound sockets when required to avoid VPN loops.
- TCP must route through selected upstream proxy.
- SOCKS5 maps to sing-box `socks` outbound.
- HTTP maps to sing-box `http` outbound.
- HTTPS proxy maps to sing-box `http` outbound with TLS enabled.
- DNS must go through proxy/proxy-safe route.
- UDP/443 is blocked by default for MVP.
- Other non-DNS UDP is blocked unless safe protected bypass is validated.
- IPv6 is unsupported/disabled for MVP.
- Do not silently fall back to direct TCP internet when upstream proxy fails.
  Use bounded retry/reconnect where implemented; if the retry budget is
  exhausted, stop VPN and surface the error.

## 9. Security/Privacy Rules

- Proxy passwords are sensitive.
- Do not store proxy passwords in plaintext if the existing project has a safer pattern or if a safe storage abstraction is added.
- Mask secrets in logs, UI summaries, diagnostics, and config previews.
- Domain and destination address logs are sensitive.
- Detailed domain/destination logging must be user-visible and controllable.
- DNS leaks are unacceptable for default MVP behavior.
- IPv6 unsupported behavior must be explicit to avoid IPv6 leaks.
- Release signing secrets must not be committed.
- License obligations for sing-box must be validated before release planning.

## 10. Testing Rules

- Every implementation task needs acceptance criteria and test/smoke commands.
- Prefer unit tests for:
  - proxy validation.
  - config generation.
  - secret masking.
  - DNS route behavior.
  - UDP/443 block rules.
  - runtime state transitions.
  - log filtering/parsing.
- Use manual tests for:
  - VPN permission dialog.
  - foreground notification.
  - TUN creation.
  - SOCKS5/HTTP/HTTPS upstreams.
  - DNS leak checks.
  - UDP/443/QUIC block checks.
  - upstream failure reconnect and fail-closed exhaustion behavior.
- Do not claim exact per-app traffic statistics in MVP.

## 11. Git/Commit Rules

- Keep patches focused.
- Do not revert user changes unless explicitly asked.
- Do not run destructive git commands unless explicitly asked.
- Prefer small commits/patches by task.
- If unrelated files are dirty, leave them alone.
- If a task touches a file with user changes, inspect carefully and preserve those changes.

## 12. Task Execution Protocol

For each task:

1. Read this file, `PRD.md`, and the relevant `TASKS.md` task.
2. Inspect only the context files listed in the task unless more are required.
3. Confirm dependencies are complete.
4. Make the smallest implementation that satisfies acceptance criteria.
5. Run listed test/smoke commands when feasible.
6. Update the task status in `TASKS.md`.
7. Summarize changed files, verification, and remaining risks.

Do not start the next task unless the user asked for it.

## 13. Definition Of Done

A task is done only when:

- The task acceptance criteria are satisfied.
- Required tests or smoke commands were run, or skipped with a concrete reason.
- Secrets are not exposed.
- Reference project was not modified.
- `TASKS.md` status is updated.
- Any new risks or scope changes are documented.

## 14. Common Pitfalls

- Accidentally implementing system proxy behavior from the old app.
- Copying `SystemProxyScreen` without replacing its meaning.
- Starting full VPN work before `SPIKE-001`.
- Forgetting `VpnService.protect()` or equivalent loop prevention.
- Letting DNS leak directly.
- Leaving IPv6 ambiguous.
- Allowing QUIC/UDP/443 to bypass the selected proxy.
- Treating Android `TrafficStats` as exact VPN-wide stats.
- Logging domains/destinations without user-visible privacy control.
- Logging or previewing proxy passwords.
- Silently falling back to direct TCP internet after proxy failure or retry
  exhaustion.
- Broadly reading or copying the reference project.
