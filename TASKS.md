# Proxy Switcher VPN Tasks

Status legend: `pending`, `in_progress`, `done`, `blocked`.

Execution rule: keep each task small. Before changing code, read `AGENTS.md`, `PRD.md`, and only the relevant task section. Treat `/home/hightemp/Projects/proxy_switcher/proxy_switcher` as read-only.

## Phase 0: Repository Bootstrap From Reference Project

### TASK-000: Document Current Baseline

- Status: done
- Goal: Capture the current new repository state before copying patterns from the reference project.
- Context files to inspect:
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/src/main/AndroidManifest.xml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/AGENTS.md`
- Files likely to create/change:
  - `TASKS.md`
- Implementation notes:
  - Record that the new app currently contains a minimal Compose Android template.
  - Do not implement app code in this task.
  - Baseline captured on 2026-06-05: the new app is a minimal Android Compose template with one `MainActivity`, Material theme files, launcher resources, default unit/instrumented example tests, namespace/application id `com.hightemp.proxy_switcher_vpn`, minSdk 24, compileSdk/targetSdk 34, AGP 8.7.2, Kotlin 2.0.0, and no Hilt/Room/Navigation/VPN code yet.
  - Baseline smoke command `./gradlew assembleDebug` was run and failed before code changes in `:app:checkDebugAarMetadata` because current AndroidX versions in the template require compileSdk 35/36 and AGP 8.9.1+ while the project is on compileSdk 34 and AGP 8.7.2. This is left for TASK-010 build stack alignment.
- Acceptance criteria:
  - Baseline notes are added to the task status or implementation notes.
  - No application code is changed.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - none
- Estimated risk: low

### TASK-001: Compare Reference Project Structure

- Status: done
- Goal: Identify the minimal reference files/patterns to copy or adapt.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/build.gradle.kts`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/gradle/libs.versions.toml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/MainActivity.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyEntity.kt`
- Files likely to create/change:
  - `TASKS.md`
- Implementation notes:
  - Use targeted file reads. Do not scan the whole reference project unless needed.
  - List concrete files to adapt in later phases.
  - Reference comparison completed on 2026-06-05 with targeted reads only.
  - Build stack to adapt in TASK-010: `/home/hightemp/Projects/proxy_switcher/proxy_switcher/gradle/libs.versions.toml` -> `gradle/libs.versions.toml`, and `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/build.gradle.kts` -> `app/build.gradle.kts`, keeping this app's namespace/application id and avoiding release signing scope until TASK-181.
  - App/Hilt wiring to adapt in TASK-011/TASK-030: `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ProxySwitcherApp.kt` -> `app/src/main/java/com/hightemp/proxy_switcher_vpn/ProxySwitcherVpnApp.kt`; `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/MainActivity.kt` -> `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`.
  - Data patterns to adapt in TASK-020..TASK-022: reference `data/local/ProxyEntity.kt`, `ProxyDao.kt`, `AppDatabase.kt`, and `data/repository/ProxyRepository.kt` -> matching `com.hightemp.proxy_switcher_vpn` package paths.
  - UI patterns to adapt later: reference `HomeScreen.kt`, `ProxyListScreen.kt`, `AddEditProxyScreen.kt`, and `LogsScreen.kt` -> matching VPN screens. Do not copy `SystemProxyScreen.kt` as behavior; use it only as a layout reference for TASK-151 VPN diagnostics.
  - Foreground service reference to inspect later: `ProxyService.kt` may inform notification/lifecycle style, but its local proxy server and system proxy management must not be copied into VPN implementation.
- Acceptance criteria:
  - Later phases reference concrete source and destination files.
  - Reference project remains unchanged.
- Test/smoke commands:
  - none
- Dependencies:
  - TASK-000
- Estimated risk: low

## Phase 1: Branding/Package Rename/Project Setup

### TASK-010: Align Build Stack With Reference

- Status: done
- Goal: Add the reference app stack to the new project without changing app behavior.
- Context files to inspect:
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/build.gradle.kts`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/gradle/libs.versions.toml`
- Files likely to create/change:
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
- Implementation notes:
  - Add Hilt, KSP, Room, Navigation Compose, Material icons.
  - Keep namespace/applicationId as `com.hightemp.proxy_switcher_vpn`.
  - Keep minSdk 24.
  - Completed on 2026-06-05: added Hilt, KSP, Room, Navigation Compose, and Material icons dependencies/plugins; aligned AndroidX versions with the reference stack to avoid the baseline AAR metadata failure; kept namespace/applicationId `com.hightemp.proxy_switcher_vpn` and minSdk 24.
  - Smoke command `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Gradle sync/build succeeds.
  - Hilt, Room, and Navigation dependencies are available.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-001
- Estimated risk: medium

### TASK-011: Add Application Class And Manifest Wiring

- Status: done
- Goal: Add Hilt application setup and required baseline manifest declarations.
- Context files to inspect:
  - `app/src/main/AndroidManifest.xml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/AndroidManifest.xml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ProxySwitcherApp.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ProxySwitcherVpnApp.kt`
  - `app/src/main/AndroidManifest.xml`
- Implementation notes:
  - Use `@HiltAndroidApp`.
  - Do not add VPN service implementation yet beyond manifest placeholders required by later tasks.
  - Completed on 2026-06-05: added `ProxySwitcherVpnApp` with `@HiltAndroidApp`, wired it via `android:name`, and added baseline `INTERNET`, `FOREGROUND_SERVICE`, and `POST_NOTIFICATIONS` permissions without introducing VPN service code.
  - Smoke command `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - App launches with Hilt application class.
  - No VPN code is implemented early.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-010
- Estimated risk: low

### TASK-012: Establish Package Structure

- Status: done
- Goal: Create package directories matching the reference architecture.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/AGENTS.md`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/repository/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/`
- Implementation notes:
  - Add directories as part of real files in later tasks if empty directories are not tracked.
  - Do not create placeholder code with no consumer unless needed for build.
  - Completed on 2026-06-05: package layout is established as the destination map for upcoming real files. Empty directories are intentionally not tracked; `data/local`, `data/repository`, `di`, `data/settings`, `vpn/*`, `service`, `ui/screens`, `ui/viewmodel`, and `utils` will be created by their implementation tasks.
  - No placeholder code or broad reference copy was added.
- Acceptance criteria:
  - Future task file locations are clear.
  - No broad copy of reference code.
- Test/smoke commands:
  - none
- Dependencies:
  - TASK-010
- Estimated risk: low

## Phase 2: Data Model And Proxy Storage

### TASK-020: Implement Proxy Entity And DAO

- Status: done
- Goal: Store SOCKS5/HTTP/HTTPS proxies in Room.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyEntity.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyDao.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/ProxyEntity.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/ProxyDao.kt`
- Implementation notes:
  - Include `ProxyType.HTTP`, `ProxyType.HTTPS`, `ProxyType.SOCKS5`.
  - Include optional auth fields.
  - Decide whether password is stored encrypted now or represented through a secure storage abstraction.
  - Completed on 2026-06-05: added `ProxyEntity`, `ProxyType`, and `ProxyDao` under `data/local`. Password storage remains a local Room field for now because no safer storage abstraction exists yet; the entity overrides `toString()` to redact username/password and avoid accidental credential logging.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Room entity and DAO compile.
  - DAO supports Flow list reads, id lookup, insert, update, delete.
  - Passwords are not logged by tests or toString usage.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-010
- Estimated risk: medium

### TASK-021: Implement AppDatabase And DI Module

- Status: done
- Goal: Provide Room database and DAO through Hilt.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/AppDatabase.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/di/DatabaseModule.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/AppDatabase.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/DatabaseModule.kt`
- Implementation notes:
  - Use database name specific to this app, such as `proxy_switcher_vpn.db`.
  - Start at schema version 1.
  - Completed on 2026-06-05: added Room `AppDatabase` version 1 and Hilt `DatabaseModule` that provides `AppDatabase` and `ProxyDao` with database name `proxy_switcher_vpn.db`.
  - Smoke command `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Hilt can provide database and DAO.
  - Build succeeds with KSP.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-020
- Estimated risk: low

### TASK-022: Implement Proxy Repository

- Status: done
- Goal: Provide a thin repository layer around the DAO.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/repository/ProxyRepository.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/repository/ProxyRepository.kt`
- Implementation notes:
  - Keep style close to the reference repository.
  - Add selected proxy persistence only if this task remains small; otherwise defer to TASK-023.
  - Completed on 2026-06-05: added injectable `ProxyRepository` as a thin wrapper around `ProxyDao`; selected proxy persistence remains deferred to TASK-023.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Repository exposes proxy list Flow and CRUD methods.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-021
- Estimated risk: low

### TASK-023: Add App Settings Storage

- Status: done
- Goal: Persist selected proxy and privacy/logging settings.
- Context files to inspect:
  - `PRD.md`
  - reference project settings approach if one exists.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/AppSettings.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/SettingsRepository.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/SettingsModule.kt`
- Implementation notes:
  - Use DataStore unless the reference project has a safer established pattern.
  - Store selected proxy id, domain logging enabled, and privacy disclosure state.
  - Completed on 2026-06-05: reference app only has service-local `SharedPreferences`, so this app uses Preferences DataStore. Added `AppSettings`, `SettingsRepository`, and `SettingsModule`; selected proxy id is persisted, detailed domain/destination logging defaults off, and enabling it is gated by privacy disclosure acceptance.
  - Added unit tests for default privacy state, disclosure-gated sensitive logging, and repository recreation over the same settings store.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Settings are observable as Flow.
  - Selected proxy survives process restart.
  - Domain/destination logging defaults to disabled until disclosure is handled.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-022
- Estimated risk: medium

## Phase 3: VPN Permission And Foreground Service Skeleton

### TASK-030: Add VPN Permission Flow Contract

- Status: done
- Goal: Model the `VpnService.prepare()` permission flow without starting a real VPN yet.
- Context files to inspect:
  - `PRD.md` section 20.
  - Android `VpnService` documentation.
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Use Activity Result API for VPN permission intent.
  - Keep UI state independent from real engine.
  - Completed on 2026-06-05: added `VpnViewModel` permission state and wired `MainActivity` to `VpnService.prepare()` with `ActivityResultContracts.StartActivityForResult`. The START VPN action requests permission when Android returns a permission intent, records permission results in UI state, and does not start a TUN, service, or sing-box engine.
  - Smoke command `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - START action requests VPN permission when needed.
  - UI receives permission result.
  - No real TUN or sing-box start yet.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-023
- Estimated risk: medium

### TASK-031: Create Foreground Service Skeleton

- Status: done
- Goal: Add a foreground service shell for future VPN lifecycle.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/service/ProxyService.kt`
  - `app/src/main/AndroidManifest.xml`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
  - `app/src/main/AndroidManifest.xml`
- Implementation notes:
  - Start foreground notification promptly.
  - Add `ACTION_START`, `ACTION_STOP`, status broadcast or StateFlow bridge.
  - Do not implement TUN setup yet.
  - Completed on 2026-06-05: added `VpnForegroundService` with `ACTION_START`, `ACTION_STOP`, notification channel, persistent foreground notification, notification stop action, manifest declaration, and `VpnRuntimeState` StateFlow bridge. `MainActivity` starts the service only after VPN permission is granted and can send stop. No TUN or sing-box setup is implemented in this task.
  - Smoke command `./gradlew assembleDebug` passed.
  - Manual start/stop service smoke was not run because `adb devices` reported no connected device/emulator.
- Acceptance criteria:
  - Service starts and stops.
  - Notification appears.
  - Runtime state updates.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: start/stop service from UI on a device/emulator.
- Dependencies:
  - TASK-030
- Estimated risk: medium

### TASK-032: Add VpnService Subclass Skeleton

- Status: done
- Goal: Introduce the Android `VpnService` component without a full packet/core pipeline.
- Context files to inspect:
  - Android `VpnService` documentation.
  - `app/src/main/AndroidManifest.xml`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/AndroidManifest.xml`
- Implementation notes:
  - Declare service with `android.permission.BIND_VPN_SERVICE`.
  - Keep lifecycle minimal and observable.
  - Decide whether this class also owns foreground notification or delegates to service abstraction.
  - Completed on 2026-06-05: added inert `ProxyVpnService` subclass with revoke handling and declared it in the manifest with `android.permission.BIND_VPN_SERVICE` and the `android.net.VpnService` intent filter. Foreground notification remains delegated to `VpnForegroundService` from TASK-031; no TUN setup is implemented.
  - Smoke command `./gradlew assembleDebug` passed.
  - Manual VPN permission dialog smoke was not run because `adb devices` reported no connected device/emulator.
- Acceptance criteria:
  - Android recognizes the VPN service.
  - Permission flow can target this service.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: Android VPN permission dialog appears.
- Dependencies:
  - TASK-031
- Estimated risk: medium

## Phase 4: VPN Engine Abstraction

### TASK-040: Define Engine Interfaces And Fake Engine

- Status: done
- Goal: Create testable engine interfaces before native/sing-box integration.
- Context files to inspect:
  - `PRD.md` sections 9, 10, 17, 18.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/VpnEngine.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/VpnEngineState.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/FakeVpnEngine.kt`
- Implementation notes:
  - Model start, stop, status Flow, logs Flow, counters Flow, and last error.
  - Include selected proxy and generated config in start request.
  - Completed on 2026-06-05: added `VpnEngine`, `VpnEngineStartRequest`, command result types, state/log/counter models, redacted `SelectedProxySummary`, and `FakeVpnEngine` with running/stopped/error simulation.
  - Added unit tests for successful start, stop/reset, configured start failure, runtime error, and credential redaction from fake engine logs.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Fake engine can simulate running, stopped, and error states.
  - Unit tests cover state transitions.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-032
- Estimated risk: low

### TASK-041: Define TUN Session Model

- Status: done
- Goal: Model Android TUN configuration and lifecycle independent from sing-box.
- Context files to inspect:
  - Android `VpnService.Builder` documentation.
  - `PRD.md` sections 9 and 14.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/tun/TunConfig.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/tun/TunSession.kt`
- Implementation notes:
  - IPv4-only MVP.
  - Capture route strategy and DNS route strategy explicitly.
  - Include fields needed by sing-box integration.
  - Completed on 2026-06-05: added pure Kotlin `TunConfig`, `TunAddress`, `TunRoute`, DNS route strategy, IPv6 unsupported mode, and immutable `TunSession` lifecycle model. Defaults capture IPv4 `0.0.0.0/0`, include proxy-safe DNS intent, and keep IPv6 routes empty/unsupported.
  - Added unit tests for IPv4-only defaults, IPv6 unsupported state, proxy-safe DNS strategy, and session state transitions.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Tun config can be created without platform side effects.
  - Unit tests validate IPv4-only defaults and IPv6 unsupported state.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-040
- Estimated risk: medium

## Phase 5: SPIKE-001 sing-box Android Embedding Validation

### SPIKE-001: Validate sing-box Android Embedding

- Status: done
- Goal: Verify whether and how sing-box can be embedded or run in this app.
- Context files to inspect:
  - `PRD.md` sections 10, 21, 22.
  - `tmp/sing-box`
  - `tmp/sing-box-for-android`
  - `tmp/sing-box-for-android/third_party/termux-app`
  - Android `VpnService` and `protect()` documentation.
- Files likely to create/change:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `TASKS.md`
  - optional throwaway prototype files under a clearly named spike/prototype path.
- Implementation notes:
  - This spike must be completed before full VPN implementation tasks.
  - Validate required ABIs: `arm64-v8a`, `x86_64`, optionally `armeabi-v7a`.
  - Compare embedding as Android library, native/JNI library, and embedded binary.
  - Validate how outbound sockets are protected from VPN loops.
  - Validate how logs/statistics/events are collected.
  - Validate license obligations.
  - Source-level validation documented on 2026-06-05 in `docs/spikes/SPIKE-001-sing-box-android.md`.
  - Recommended approach is embedded sing-box `libbox.aar`, with Android `VpnService` providing TUN fd and `protect(fd)` through libbox `PlatformInterface`.
  - Runtime validation completed on 2026-06-05 with an ignored prototype under `build/spike-singbox/prototype`.
  - Built `libbox-amd64.aar` and `libbox-android-all.aar`; the all-ABI artifact contains `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64` `libbox.so` entries.
  - Emulator proof on `Medium_Phone_API_35` validated `VpnService` TUN start/stop, `VpnService.protect(fd)`, SOCKS5 outbound, HTTP outbound, HTTPS proxy outbound, DNS hijack plus DoH through the selected proxy, and UDP/443 `reject(drop)`.
  - Production implementation must include a non-VPN default-interface monitor for libbox, filter or normalize IPv6 zone-bearing interface prefixes, keep MVP IPv4-only, and explicitly handle Android Private DNS / DoT attempts to the TUN DNS address.
- Acceptance criteria:
  - Documented recommended embedding approach.
  - Documented build steps and artifacts.
  - Proof of `VpnService` plus sing-box TUN start/stop.
  - Proof of SOCKS5 outbound.
  - Proof of HTTP outbound.
  - Proof of HTTPS proxy outbound using HTTP CONNECT over TLS.
  - Proof of DNS through proxy.
  - Proof of UDP/443 block behavior.
  - Proof or limitation note for logs/statistics access.
  - GPL/license conclusion recorded.
  - Follow-up tasks adjusted if the selected approach differs from current plan.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual device/emulator VPN start/stop.
  - Manual SOCKS5/HTTP/HTTPS proxy verification.
  - Manual DNS leak check.
  - Manual UDP/443/QUIC block check.
- Dependencies:
  - TASK-040
  - TASK-041
- Estimated risk: high

## Phase 6: sing-box Config Generator

### TASK-060: Create Config Model And Serializer

- Status: done
- Goal: Generate sing-box config from Kotlin models.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box config documentation.
  - `PRD.md` sections 10, 11, 12, 13.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfig.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Use a structured JSON serializer if available.
  - Include secret masking helper.
- Acceptance criteria:
  - Valid JSON config generated for fake selected proxy.
  - Masked preview hides username/password where needed.
  - Unit tests cover config shape.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - SPIKE-001
- Estimated risk: medium

### TASK-061: Generate TUN Inbound And Route Defaults

- Status: done
- Goal: Add MVP TUN inbound, IPv4 routing, and default route behavior to generated config.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box TUN inbound docs.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Keep IPv6 unsupported/disabled explicit.
  - Add route tags/constants for proxy, direct, block, and DNS.
  - Completed on 2026-06-05: generated config now includes the spike-validated TUN inbound (`172.19.0.1/30`, `172.19.0.2`, `auto_route`, `strict_route`, `gvisor`) and top-level route defaults with `final: "proxy"` plus `auto_detect_interface: true`.
  - Updated shared `TunConfig.mvpDefault()` to use IPv4 split default routes `0.0.0.0/1` and `128.0.0.0/1` so the TUN model matches the validated sing-box config and keeps IPv6 routes empty/unsupported.
  - Added route tag constants for proxy, direct, block, and DNS. DNS server generation and UDP blocking remain deferred to their dedicated tasks.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Generated config includes TUN inbound as validated by spike.
  - IPv4-only defaults are covered by tests.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-060
- Estimated risk: medium

## Phase 7: Upstream Proxy Config: SOCKS5/HTTP/HTTPS

### TASK-070: Add SOCKS5 Outbound Generation

- Status: done
- Goal: Generate sing-box `socks` outbound for SOCKS5 proxies.
- Context files to inspect:
  - sing-box `socks` outbound docs.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/ProxyEntity.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Support no auth and username/password auth.
  - Completed on 2026-06-05: SOCKS5 outbound generation uses sing-box `type: "socks"`, `version: "5"`, `network: "tcp"`, selected proxy host/port, and optional username/password fields.
  - Added explicit unit coverage for SOCKS5 without auth and with username/password auth, including masked config preview assertions.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Unit tests cover SOCKS5 without auth and with auth.
  - Passwords are masked in preview.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-061
- Estimated risk: low

### TASK-071: Add HTTP Outbound Generation

- Status: done
- Goal: Generate sing-box `http` outbound for HTTP proxies.
- Context files to inspect:
  - sing-box `http` outbound docs.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Support no auth and username/password auth.
  - Completed on 2026-06-05: HTTP outbound generation uses sing-box `type: "http"`, selected proxy host/port, no TLS for plain HTTP proxies, and optional username/password fields.
  - Added explicit unit coverage for HTTP without auth and with username/password auth, including masked config preview assertions.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Unit tests cover HTTP without auth and with auth.
  - Passwords are masked in preview.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-061
- Estimated risk: low

### TASK-072: Add HTTPS Proxy Outbound Generation

- Status: done
- Goal: Generate sing-box `http` outbound with TLS enabled for HTTPS proxy upstreams.
- Context files to inspect:
  - sing-box `http` outbound TLS docs.
  - `PRD.md` section 11.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - HTTPS proxy means HTTP CONNECT over TLS to the proxy server.
  - Support no auth and username/password auth.
  - Completed on 2026-06-05: HTTPS proxy generation uses sing-box `type: "http"` with `tls.enabled=true`, no production `tls.insecure`, selected proxy host/port, and optional username/password fields.
  - Added explicit unit coverage for HTTPS without auth and with username/password auth, including masked config preview assertions.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Unit tests cover HTTPS without auth and with auth.
  - Generated config enables TLS exactly as validated in SPIKE-001.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-061
- Estimated risk: medium

## Phase 8: DNS Through Proxy

### TASK-080: Add DNS Config Generation

- Status: done
- Goal: Ensure generated config routes DNS through a proxy-safe path.
- Context files to inspect:
  - sing-box DNS docs.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `PRD.md` section 12.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/dns/DnsMode.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Prefer DoH or DoT/TCP through selected outbound as validated by spike.
  - Avoid direct DNS default.
  - Completed on 2026-06-05: generated config now includes a top-level DNS section with DoH server `1.1.1.1:443`, `/dns-query`, `tls.enabled=true`, `final: "doh"`, `strategy: "ipv4_only"`, and `detour: "proxy"` so DNS does not use direct routing by default.
  - Added the TUN DNS hijack route rule `inbound: "tun-in"`, `port: 53`, `action: "hijack-dns"` before proxy final routing.
  - Added `DnsMode` diagnostics model with proxy-safe DoH fields. DNS event logging remains deferred to TASK-081, and UDP blocking remains deferred to Phase 9.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Unit tests prove DNS route is not direct.
  - Diagnostics model can expose DNS mode.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-072
- Estimated risk: medium

### TASK-081: Add DNS Event Logging Adapter

- Status: done
- Goal: Convert core DNS events/logs into app logs and counters.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `PRD.md` sections 17 and 18.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/events/VpnEvent.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/events/SingBoxEventParser.kt`
- Implementation notes:
  - Keep domain logging privacy setting in mind.
  - Mask sensitive values.
  - Completed on 2026-06-05: added `AppLogger`, DNS `VpnEvent` models, `SingBoxEventParser`, and `DnsEventLoggingAdapter`.
  - DNS log lines such as `dns: exchange example.com. IN A` are parsed into DNS events. The adapter increments `VpnEngineCounters.dnsQueries` and logs either detailed domain/query data when `AppSettings.domainDestinationLoggingEnabled` is true or a redacted generic message when detailed logging is disabled.
  - Added unit tests for DNS query/failure parsing, DNS counter increments, privacy-gated domain logging, and warning-level DNS failure logs.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - DNS counters update.
  - DNS logs respect privacy/logging settings.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-080
- Estimated risk: medium

## Phase 9: UDP/443 Block And UDP Behavior

### TASK-090: Add UDP Policy Model

- Status: done
- Goal: Make MVP UDP behavior explicit in code and diagnostics.
- Context files to inspect:
  - `PRD.md` section 13.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/udp/UdpPolicy.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnostics.kt`
- Implementation notes:
  - Default: block UDP/443.
  - Other non-DNS UDP is blocked unless spike validates safe direct bypass.
  - Completed on 2026-06-05: added `UdpPolicy.mvpDefault()` with UDP/443 blocked and non-DNS UDP blocked, plus diagnostics-facing status strings and UDP counters via `VpnDiagnostics`.
  - `GeneratedSingBoxConfig` now carries the active UDP policy so the config generator and upcoming route generation can consume the same policy model.
  - Added unit tests for the default UDP policy, diagnostics exposure, and generator policy metadata.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - UDP policy is visible to config generator and diagnostics.
  - Unit tests cover default policy.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - SPIKE-001
- Estimated risk: medium

### TASK-091: Generate UDP/443 Block Rules

- Status: done
- Goal: Add sing-box rules that block UDP/443 by default.
- Context files to inspect:
  - sing-box route/rule docs.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/udp/UdpPolicy.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Use exact config syntax validated in SPIKE-001.
  - Add counters/events if core exposes blocked UDP logs.
  - Completed on 2026-06-05: generated route rules now append the spike-validated UDP/443 block rule `{"network":"udp","port":443,"action":"reject","method":"drop"}` after the TUN DNS hijack rule when `UdpPolicy.blocksUdp443` is true.
  - Diagnostics already expose UDP/443 as blocked through `VpnDiagnostics` from TASK-090.
  - Smoke command `./gradlew test` passed.
  - Manual QUIC/UDP/443 bypass verification was not run because production VPN runtime integration is not implemented yet.
- Acceptance criteria:
  - Unit tests prove UDP/443 block rule exists.
  - Diagnostics show UDP/443 blocked.
- Test/smoke commands:
  - `./gradlew test`
  - Manual: verify QUIC/UDP/443 does not bypass proxy.
- Dependencies:
  - TASK-090
  - TASK-080
- Estimated risk: medium

## Phase 10: Statistics And Counters

### TASK-100: Add Runtime Counters Model

- Status: done
- Goal: Track MVP counters independent from Android `TrafficStats`.
- Context files to inspect:
  - `PRD.md` section 18.
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/proxy/ProxyStats.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStats.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStore.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStoreTest.kt`
- Implementation notes:
  - Include bytes in/out, total connections, failed connections, DNS queries, blocked UDP, bypassed UDP, uptime, last error.
  - Completed on 2026-06-05: added `VpnStats` snapshot and `VpnStatsStore` with bytes in/out, active/total/failed connections, DNS queries, blocked/bypassed UDP, last error, session start/stop times, and deterministic uptime calculation.
  - Stats update through record methods and DNS `VpnEvent` application, and can be bridged to existing `VpnEngineCounters` without relying on Android `TrafficStats`.
  - Added unit tests for session reset, frozen uptime after stop, counter updates, DNS event updates, and `VpnEngineCounters` conversion.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Counters update through events.
  - Uptime calculation is deterministic in tests.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-040
- Estimated risk: low

### TASK-101: Wire sing-box Stats/Event Source

- Status: done
- Goal: Populate counters from sing-box logs/API/events where available.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStore.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/events/SingBoxEventParser.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStore.kt`
- Implementation notes:
  - If exact active connections are unavailable, expose unknown/not supported instead of fake precision.
  - Completed on 2026-06-05: extended `VpnEvent` with sing-box status snapshots and parsed UDP blocked events. `VpnStatsStore` now updates traffic totals and active connection counts from status events only when those fields are available, updates DNS/UDP counters from parsed events, and marks unavailable exact stats explicitly.
  - `SingBoxEventParser` now parses UDP reject log lines in addition to DNS exchange/failure lines. `VpnDiagnostics.fromStats()` exposes `available` versus `not_supported` status for traffic and active connection stats.
  - Added unit tests for status event updates, unavailable stats behavior, UDP reject parsing/counters, and diagnostics availability strings.
  - Smoke command `./gradlew test` passed.
  - Manual traffic test while VPN runs was not run because production VPN runtime integration is not implemented yet.
- Acceptance criteria:
  - Available counters update from real or parsed engine events.
  - Unsupported exact stats are documented in diagnostics.
- Test/smoke commands:
  - `./gradlew test`
  - Manual traffic test while VPN runs.
- Dependencies:
  - TASK-100
  - SPIKE-001
- Estimated risk: high

## Phase 11: Logging

### TASK-110: Implement App Logger

- Status: done
- Goal: Provide structured in-memory logs for UI.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/utils/AppLogger.kt`
  - `PRD.md` section 17.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/LogEntry.kt`
- Implementation notes:
  - Include timestamp, level, type, message.
  - Add filtering support.
  - Keep bounded in-memory history.
  - Completed on 2026-06-05: added `LogEntry` with timestamp, level, type, and message, and upgraded `AppLogger` to expose structured `StateFlow<List<LogEntry>>`.
  - Logger supports level/type filtering, clearing, bounded 1000-entry history, and explicit sensitive-value masking. DNS logging adapter now emits `LogType.DNS`.
  - Added unit tests for observe/clear, filtering, masking, bounded history, and DNS log type.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Logs can be observed as Flow/StateFlow.
  - Logs can be cleared.
  - Secrets are masked.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-023
- Estimated risk: low

### TASK-111: Add Privacy Gate For Domain/Destination Logging

- Status: done
- Goal: Make sensitive connection/domain logging user-visible and controllable.
- Context files to inspect:
  - `PRD.md` sections 17 and 19.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/SettingsRepository.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/AppSettings.kt`
  - UI file selected for the disclosure/settings control.
- Implementation notes:
  - Default detailed domain/destination logging to off unless disclosure is accepted.
  - Generic event logging may remain enabled.
  - Completed on 2026-06-05: `SettingsRepository` already gates detailed domain/destination logging behind privacy disclosure; `VpnViewModel` now exposes settings and update methods to UI.
  - Current Compose screen includes privacy disclosure and detailed domain logging switches. Detailed logging is disabled until disclosure is accepted, and disabling disclosure clears the detailed logging setting.
  - DNS event logging remains generic when detailed logging is disabled and includes domains only when `AppSettings.domainDestinationLoggingEnabled` is true.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
  - Manual toggle verification was not run because no production device/emulator UI session is active in this task.
- Acceptance criteria:
  - Sensitive logs are suppressed when setting is disabled.
  - UI exposes disclosure or setting.
- Test/smoke commands:
  - `./gradlew test`
  - Manual: toggle setting and verify logs.
- Dependencies:
  - TASK-110
  - TASK-023
- Estimated risk: medium

## Phase 12: Home UI

### TASK-120: Build Home Screen Shell

- Status: done
- Goal: Replace template UI with reference-style Home screen for VPN control.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
  - `PRD.md` section 16.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Show selected proxy, VPN status, START/STOP VPN, bytes in/out, last error, quick diagnostics summary.
  - Do not include system proxy permission UI.
  - Completed on 2026-06-05: added `ui/screens/HomeScreen.kt` and moved the app surface from the permission-only template into a VPN Home shell with selected proxy, runtime/permission status, START/STOP VPN controls, bytes in/out, diagnostics summary, optional last error, and privacy controls.
  - `MainActivity` now renders `HomeScreen`; labels reference VPN behavior only, with no system proxy controls or wording.
  - Smoke command `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Home screen compiles and navigates.
  - Button labels reference VPN, not proxy server/system proxy.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-030
  - TASK-100
- Estimated risk: medium

### TASK-121: Wire Home Screen To VPN Runtime

- Status: done
- Goal: Connect Home UI to VPN permission, service, selected proxy, and stats state.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Implementation notes:
  - Disable START if no selected proxy or validation fails.
  - Show last error.
  - Completed on 2026-06-05: `VpnViewModel` now exposes proxy list, selected proxy from settings, start eligibility, and `VpnStats`; Home consumes selected proxy label, stats, last error, and `canStartVpn`.
  - START VPN is disabled unless a selected proxy exists, is enabled, has a nonblank host, and has a valid port. Start attempts without a valid proxy set a visible last error. Stop requests update stats and service state remains driven by `VpnRuntimeState`.
  - Smoke command `./gradlew assembleDebug` passed.
  - Manual start/stop flow was not run because no production device/emulator UI session is active in this task.
- Acceptance criteria:
  - User can request start/stop from Home.
  - UI state remains accurate across service status changes.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: start/stop flow.
- Dependencies:
  - TASK-120
  - TASK-031
- Estimated risk: medium

## Phase 13: Proxy List/Add/Edit UI

### TASK-130: Port Proxy List Screen Pattern

- Status: done
- Goal: Provide saved proxy list with selected indicator and CRUD actions.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/ProxyListScreen.kt`
  - `PRD.md` section 16.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyListScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Implementation notes:
  - Adapt naming to VPN.
  - Add active/selected indicator.
  - Test proxy action can be placeholder only if wired to a clear future task.
  - Completed on 2026-06-05: added a stateless Compose `ProxyListScreen` with saved proxy rows, selected indicator, add/edit/delete/select actions, empty state, and VPN-specific labels.
  - `MainActivity` now uses Navigation Compose for Home and proxy list routes. Home exposes a manage-proxies action, and proxy list selection/deletion are wired through `VpnViewModel`.
  - Add/edit actions are exposed from the list and currently route to a clear "Proxy editor is not available yet" UI state until TASK-131 implements the Add/Edit proxy screen.
  - Smoke command `./gradlew assembleDebug` passed.
  - Manual CRUD smoke was not run because no production device/emulator UI session is active in this task; edit form behavior is deferred to TASK-131.
- Acceptance criteria:
  - Saved proxies list renders.
  - User can select, edit, and delete proxies.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual CRUD smoke.
- Dependencies:
  - TASK-022
  - TASK-023
- Estimated risk: medium

### TASK-131: Port Add/Edit Proxy Screen Pattern

- Status: done
- Goal: Add form for SOCKS5/HTTP/HTTPS proxy creation and editing.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/AddEditProxyScreen.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/ProxyFormValidator.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/AddEditProxyScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyFormValidator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyFormValidatorTest.kt`
- Implementation notes:
  - Validate host and port.
  - Username/password optional.
  - Avoid logging password.
  - Completed on 2026-06-05: added `AddEditProxyScreen` with SOCKS5/HTTP/HTTPS protocol selection, host/port fields, optional username/password fields, enabled toggle, validation error display, and save/cancel actions.
  - `MainActivity` now routes `add_proxy` and `edit_proxy/{proxyId}` from the proxy list, and `VpnViewModel.onProxySaved()` inserts or updates proxies through `ProxyRepository` without logging credentials.
  - Added `ProxyFormValidator` and focused unit tests covering invalid host, invalid port, password-without-username, valid no-auth, and valid auth cases.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Form supports create and edit.
  - Validator tests cover invalid host, invalid port, and valid auth/no-auth cases.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-130
- Estimated risk: low

### TASK-132: Add Proxy Test Action

- Status: done
- Goal: Test upstream proxy reachability without starting full VPN.
- Context files to inspect:
  - `PRD.md` section 6.
  - Reference proxy utilities if needed.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/proxy/ProxyTester.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyListScreen.kt`
- Implementation notes:
  - Keep timeout short.
  - Mask secrets in errors.
  - HTTPS proxy means CONNECT over TLS to proxy.
  - Completed on 2026-06-05: added `ProxyTester` with a four-second reachability probe for SOCKS5, HTTP CONNECT, and HTTPS-proxy CONNECT over TLS.
  - Proxy test results are tracked per saved proxy in `VpnViewModel` and rendered on `ProxyListScreen` with a row-level Test action. Failed tests update only proxy-test UI state and do not alter VPN runtime or permission state.
  - Error messages are sanitized and do not include usernames or passwords; invalid proxy input fails before opening a socket.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
  - Manual test with a known proxy was not run because no known SOCKS5/HTTP/HTTPS proxy endpoint is available in this task environment.
- Acceptance criteria:
  - User can run a test and see success/failure.
  - Failed test does not alter VPN state.
- Test/smoke commands:
  - `./gradlew test`
  - Manual with known proxy.
- Dependencies:
  - TASK-131
- Estimated risk: medium

## Phase 14: Logs UI

### TASK-140: Build Logs Screen

- Status: done
- Goal: Show structured logs with filters and clear action.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/LogsScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/LogsScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Filters by level/type.
  - Include timestamps.
  - Keep UI dense and scan-friendly.
  - Completed on 2026-06-05: added `LogsScreen` backed by live `AppLogger.logs`, with timestamped rows, level/type metadata, level and type filter menus, auto-scroll, empty state, and clear action.
  - `HomeScreen` now exposes a logs top-bar action, and `MainActivity` routes to the Logs screen and wires `AppLogger.clear()`.
  - Smoke command `./gradlew assembleDebug` passed.
  - Manual start/stop log generation and filter verification was not run because no production device/emulator UI session is active in this task.
- Acceptance criteria:
  - Logs render and update live.
  - Clear logs works.
  - Filters work.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: generate start/stop logs and filter.
- Dependencies:
  - TASK-110
- Estimated risk: low

## Phase 15: VPN Diagnostics UI

### TASK-150: Create Diagnostics Model

- Status: done
- Goal: Consolidate VPN/core/TUN/DNS/UDP status for UI.
- Context files to inspect:
  - `PRD.md` sections 16, 17, 18.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnostics.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnosticsRepository.kt`
- Implementation notes:
  - Include generated config preview with masked secrets.
  - Include unsupported IPv6 status.
  - Completed on 2026-06-05: expanded `VpnDiagnostics` with explicit fields for VPN permission, foreground service, sing-box, TUN, DNS, IPv4, IPv6 unsupported/disabled, UDP policy, selected proxy, counters, last error, and masked config preview.
  - Added `VpnDiagnosticsRepository`, which combines permission, runtime, selected proxy, and stats flows into diagnostics state and generates only the masked sing-box config preview.
  - `VpnViewModel` now exposes diagnostics as a `StateFlow<VpnDiagnostics>` for the upcoming diagnostics screen.
  - Unit tests cover diagnostics Flow emission, unsupported IPv6 status, selected proxy masking, and masked config preview that excludes username/password secrets.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Diagnostics state is available as Flow/StateFlow.
  - Unit tests cover masked config preview.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-060
  - TASK-090
  - TASK-100
- Estimated risk: medium

### TASK-151: Build VPN Diagnostics Screen

- Status: done
- Goal: Replace old System Proxy concept with VPN diagnostics UI.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/SystemProxyScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnostics.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/VpnDiagnosticsScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Do not copy system proxy text/actions.
  - Show permission, foreground service, sing-box, TUN, DNS, IPv4, IPv6, UDP, proxy, counters, last error, config preview.
  - Completed on 2026-06-05: added `VpnDiagnosticsScreen` that renders VPN permission, foreground service, sing-box, TUN, DNS, IPv4, IPv6 unsupported/disabled, UDP policy, selected proxy, counters, last error, and masked config preview from `VpnDiagnostics`.
  - `HomeScreen` now exposes a diagnostics top-bar action, and `MainActivity` routes to the diagnostics screen using `VpnViewModel.diagnostics`.
  - Verified app source contains no old system proxy text, `WRITE_SECURE_SETTINGS`, `Settings.Global`, or global HTTP proxy controls.
  - Smoke command `./gradlew assembleDebug` passed.
  - Manual stopped/running diagnostics inspection was not run because no production device/emulator UI session is active in this task.
- Acceptance criteria:
  - Diagnostics screen renders all MVP diagnostics.
  - No system proxy controls remain.
  - Secrets are masked.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: inspect diagnostics while stopped and running.
- Dependencies:
  - TASK-150
- Estimated risk: medium

## Phase 16: Error Handling And Lifecycle Hardening

### TASK-160: Implement Fail-Closed Upstream Failure Handling

- Status: done
- Goal: Stop VPN and show error when selected upstream proxy fails.
- Context files to inspect:
  - `PRD.md` sections 7 and 11.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
- Implementation notes:
  - No silent direct TCP fallback.
  - Log last error.
  - Update UI state.
  - Completed on 2026-06-05: added `VpnRuntimeController`, which probes the selected upstream proxy before engine start, generates config only after probe success, starts the engine, and stops/logs fail-closed on proxy probe failure, config generation failure, engine start failure, or unexpected tester/engine exceptions.
  - `VpnForegroundService` is now Hilt-injected, loads the selected proxy from DataStore/Room instead of receiving secrets through intents, calls the runtime controller, and marks runtime state failed/stopped when start cannot proceed.
  - `VpnRuntimeSnapshot` now carries `lastError`; Home and diagnostics surface runtime fail-closed errors. `AppLogger` records sanitized fail-closed failures with proxy username/password masked.
  - Added DI bindings for `VpnEngine` and `ProxyReachabilityTester`, and unit tests proving simulated proxy probe and engine start failures stop the engine and emit sanitized failure logs.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
  - Manual start with an unavailable proxy was not run because no production device/emulator UI session is active in this task.
- Acceptance criteria:
  - Simulated upstream failure stops engine/service.
  - UI shows last error.
  - Logs contain failure.
- Test/smoke commands:
  - `./gradlew test`
  - Manual: start with unavailable proxy.
- Dependencies:
  - TASK-101
  - TASK-121
- Estimated risk: high

### TASK-161: Harden Start/Stop Race Conditions

- Status: done
- Goal: Make rapid start/stop, permission cancellation, and process recreation predictable.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Implementation notes:
  - Use explicit states: idle, permissionRequired, starting, running, stopping, stopped, error.
  - Avoid duplicate engine instances.
  - Completed on 2026-06-05: added explicit `ERROR` runtime status and runtime tests for fail-closed error state and error clearing on start/stop transitions.
  - `VpnForegroundService` now ignores duplicate START while STARTING/RUNNING, ignores START while STOPPING, treats STOP as idempotent while STOPPED/ERROR/STOPPING, and still allows restart from ERROR.
  - Permission cancellation and unavailable permission paths now stop stats/runtime state explicitly, leaving the app stopped with no foreground service.
  - `VpnViewModel.canStartVpnNow()` also checks foreground runtime activity before requesting permission/start.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
  - Manual rapid start/stop and notification-stop verification was not run because no production device/emulator UI session is active in this task.
- Acceptance criteria:
  - Rapid taps do not create multiple engines.
  - Permission cancellation leaves app stopped.
  - Notification stop works.
- Test/smoke commands:
  - `./gradlew test`
  - Manual rapid start/stop.
- Dependencies:
  - TASK-160
- Estimated risk: high

## Phase 17: Tests

### TASK-170: Add Config Generator Test Suite

- Status: done
- Goal: Cover all MVP sing-box config generation paths.
- Context files to inspect:
  - Existing config generator tests.
- Files likely to create/change:
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Cover SOCKS5/HTTP/HTTPS auth/no-auth.
  - Cover DNS through proxy.
  - Cover UDP/443 block.
  - Cover IPv4-only/IPv6 unsupported.
  - Cover secret masking.
  - Completed on 2026-06-05: existing config generator tests already covered SOCKS5/HTTP/HTTPS auth/no-auth, HTTPS proxy TLS, DNS through selected proxy, no direct DNS default, UDP/443 reject/drop, IPv4-only TUN routes, IPv6 absence, and secret masking.
  - Added guard tests proving the generated config does not define a direct outbound fallback and that masked previews hide credentials for every supported proxy type.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Tests fail on direct DNS default.
  - Tests fail if HTTPS proxy TLS flag is missing.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-091
- Estimated risk: low

### TASK-171: Add ViewModel And Runtime State Tests

- Status: done
- Goal: Cover UI/runtime state behavior.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
- Files likely to create/change:
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModelTest.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeStateTest.kt`
- Implementation notes:
  - Use fake repository/engine.
  - Completed on 2026-06-05: added `VpnViewModelTest` with an in-memory `ProxyDao`, fake preferences `DataStore`, fake reachability tester, and test main dispatcher.
  - ViewModel tests cover start disabled without a selected proxy and permission cancellation leaving runtime/stats stopped with a visible permission error.
  - Existing `VpnRuntimeStateTest` now covers error state and last error behavior from TASK-161, including clearing previous errors on start/stop transitions.
  - Smoke command `./gradlew test` passed.
- Acceptance criteria:
  - Tests cover start disabled without selected proxy.
  - Tests cover permission cancellation.
  - Tests cover error state and last error.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-161
- Estimated risk: medium

### TASK-172: Add Manual VPN QA Script

- Status: done
- Goal: Create a repeatable manual checklist for real VPN behavior.
- Context files to inspect:
  - `PRD.md` section 23.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
- Files likely to create/change:
  - `docs/qa/manual-vpn-checklist.md`
- Implementation notes:
  - Include device/emulator setup, proxy endpoints, DNS leak checks, UDP/443 checks, failure cases.
  - Completed on 2026-06-05: added `docs/qa/manual-vpn-checklist.md` with prerequisites, emulator/device setup, spike-proven proxy endpoints, baseline app checks, proxy CRUD/test checks, VPN permission flow, foreground notification stop, TUN verification, SOCKS5/HTTP/HTTPS outbound checks, DNS-through-proxy checks, UDP/443/QUIC block checks, IPv6 unsupported/leak checks, upstream failure fail-closed checks, logs/diagnostics checks, rapid start/stop checks, and release-blocking pass criteria.
  - Smoke check verified the checklist file exists and contains the expected manual QA sections.
  - Manual checklist execution was not run because no production device/emulator VPN session is active in this task.
- Acceptance criteria:
  - Checklist covers all MVP manual tests.
  - Checklist includes expected results.
- Test/smoke commands:
  - Manual checklist execution.
- Dependencies:
  - TASK-160
- Estimated risk: low

## Phase 18: CI/Release

### TASK-180: Add Debug CI Workflow

- Status: done
- Goal: Run build and unit tests on pushes/PRs.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/.github/workflows/release.yml`
- Files likely to create/change:
  - `.github/workflows/android-ci.yml`
- Implementation notes:
  - Use JDK 17.
  - Run unit tests and assembleDebug.
  - Add native artifact checks only after sing-box approach is chosen.
  - Completed on 2026-06-05: added `.github/workflows/android-ci.yml` for push, pull request, and manual dispatch.
  - Workflow checks out code, sets up Temurin JDK 17, makes the Gradle wrapper executable, runs `./gradlew test`, builds `./gradlew assembleDebug`, and uploads the debug APK artifact.
  - Native/libbox artifact checks remain deferred until production native artifacts are committed or fetched by the build.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed locally.
- Acceptance criteria:
  - CI builds debug APK and runs tests.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-010
- Estimated risk: low

### TASK-181: Add Release Workflow

- Status: done
- Goal: Add signed APK release flow similar to the reference project.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/.github/workflows/release.yml`
  - `app/build.gradle.kts`
- Files likely to create/change:
  - `.github/workflows/release.yml`
  - `app/build.gradle.kts`
  - `keystore.properties.example`
- Implementation notes:
  - Rename artifact to `proxy_switcher_vpn-${tag}.apk`.
  - Include native artifact checks if applicable.
  - Completed on 2026-06-05: added release signing support to `app/build.gradle.kts` using either ignored local `keystore.properties` or CI environment variables (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
  - Added `keystore.properties.example` and ignored real `keystore.properties` so signing secrets are not committed.
  - Added `.github/workflows/release.yml` for `v*.*.*` tags. It validates signing secrets, decodes `KEYSTORE_BASE64`, builds `app:assembleRelease`, verifies the APK signature with `apksigner`, renames the artifact to `proxy_switcher_vpn-${tag}.apk`, uploads it, and creates a GitHub release.
  - Native/libbox artifact checks are not yet applicable because production native artifacts are not committed or fetched by the app build.
  - Smoke command `./gradlew assembleRelease` passed locally; without a local keystore it produced the expected unsigned release APK for smoke validation.
- Acceptance criteria:
  - Release workflow builds signed APK for `v*.*.*` tags.
  - APK signature verification step exists.
- Test/smoke commands:
  - `./gradlew assembleRelease`
- Dependencies:
  - TASK-180
  - SPIKE-001
- Estimated risk: medium

## Phase 18.5: Production sing-box/libbox VPN Integration

### TASK-182: Bundle And Verify libbox AAR

- Status: done
- Goal: Make the spike-produced libbox Android artifact a real app dependency with deterministic verification.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `build/spike-singbox/libbox-android-all.aar`
  - `app/build.gradle.kts`
  - `.github/workflows/android-ci.yml`
  - `.github/workflows/release.yml`
- Files likely to create/change:
  - `app/libs/libbox.aar`
  - `app/libs/libbox.aar.sha256`
  - `app/build.gradle.kts`
  - `.github/workflows/android-ci.yml`
  - `.github/workflows/release.yml`
  - `TASKS.md`
- Implementation notes:
  - Preserve required MVP ABI coverage for `arm64-v8a` and `x86_64`; `armeabi-v7a` may remain bundled if present in the artifact.
  - Add a Gradle verification task that checks the artifact hash and required native ABI entries before build/test/release.
  - Do not commit signing secrets.
  - Completed on 2026-06-05: copied the spike-produced all-ABI artifact to `app/libs/libbox.aar` and recorded SHA-256 `f8dbec0658177ef3310fec8c38d917d75e0db74a1565f88ef78a96df3e0a3905` in `app/libs/libbox.aar.sha256`.
  - Added `implementation(files("libs/libbox.aar"))` so app code can compile against `io.nekohasekai.libbox`.
  - Added `verifyLibboxArtifact`, which verifies the recorded hash and required `jni/arm64-v8a/libbox.so` and `jni/x86_64/libbox.so` entries; `preBuild` and `check` depend on it.
  - Added explicit `./gradlew verifyLibboxArtifact` steps to debug CI and release workflows.
  - Smoke command `./gradlew verifyLibboxArtifact` passed locally.
- Acceptance criteria:
  - App code can compile against `io.nekohasekai.libbox` classes.
  - libbox artifact hash is recorded and checked.
  - Required native ABI entries are checked in local builds and CI/release.
- Test/smoke commands:
  - `./gradlew verifyLibboxArtifact`
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - SPIKE-001
  - TASK-181
- Estimated risk: medium

### TASK-183: Implement Android VpnService TUN Platform Bridge

- Status: done
- Goal: Provide the Android platform callbacks libbox needs for fd protection, TUN creation, and default interface discovery.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `tmp/sing-box-for-android/app/src/main/java/io/nekohasekai/sfa/bg/VPNService.kt`
  - `tmp/sing-box-for-android/app/src/main/java/io/nekohasekai/sfa/bg/PlatformInterfaceWrapper.kt`
  - `tmp/sing-box-for-android/app/src/main/java/io/nekohasekai/sfa/bg/DefaultNetworkMonitor.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/platform/ActiveVpnServiceBridge.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/platform/AndroidLibboxPlatformInterface.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/platform/DefaultNetworkMonitor.kt`
  - `app/src/main/AndroidManifest.xml`
  - `TASKS.md`
- Implementation notes:
  - The app start/stop path must target `ProxyVpnService` because only `VpnService` can establish the TUN fd.
  - Use `VpnService.protect()` for libbox outbound fd protection.
  - Use `VpnService.Builder` with libbox `TunOptions`, MVP IPv4-only routes/DNS, no IPv6 routes, and close the `ParcelFileDescriptor` on stop/revoke.
  - Do not copy root, platform shell, system proxy, or per-app routing behavior from the upstream app.
  - Completed on 2026-06-05: replaced the `ProxyVpnService` stub with the real foreground VPN service start/stop path and switched `MainActivity` to start/stop that service after `VpnService.prepare()`.
  - Added `ActiveVpnServiceBridge`, `AndroidLibboxPlatformInterface`, and `DefaultNetworkMonitor` so libbox can protect outbound sockets, request a TUN fd, and receive default-interface updates without coupling UI code to libbox.
  - `ProxyVpnService.openTun()` now uses libbox `TunOptions` to add IPv4 address/routes and proxy-safe IPv4 DNS, deliberately ignores IPv6 options for MVP, and closes the TUN fd on stop, destroy, and revoke.
  - Manifest now declares foreground service metadata on the actual VPN service path.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed locally.
- Acceptance criteria:
  - Start and stop actions are handled by `ProxyVpnService`.
  - libbox can request a TUN fd through `PlatformInterface.openTun`.
  - libbox can protect outbound sockets through `PlatformInterface.autoDetectInterfaceControl`.
  - TUN fd is closed on stop, service destroy, and VPN revoke.
  - IPv6 route/address handling remains disabled for MVP.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-182
- Estimated risk: high

### TASK-184: Implement libbox Command Server VPN Engine

- Status: done
- Goal: Replace the fake runtime engine in production with a libbox-backed engine that starts/stops sing-box from generated config.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `tmp/sing-box-for-android/app/src/main/java/io/nekohasekai/sfa/bg/BoxService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeController.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/VpnEngine.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/LibboxVpnEngine.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/LibboxCommandServerHandler.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/VpnEngineModule.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/engine/LibboxVpnEngineTest.kt`
  - `TASKS.md`
- Implementation notes:
  - Use `CommandServer.start()`, `CommandServer.startOrReloadService(config, OverrideOptions())`, `closeService()`, and `close()`.
  - Keep UI and runtime controller bound to the existing `VpnEngine` abstraction.
  - Surface sanitized lifecycle errors and stop fail-closed; do not route direct on proxy failure.
  - Preserve `FakeVpnEngine` for unit tests that need deterministic fakes.
  - Completed on 2026-06-05: added `LibboxSetup` and initialized it from `ProxySwitcherVpnApp`, with defensive setup retry from the engine start path.
  - Added `LibboxCommandServerHandler` with unsupported system-proxy/root/native-crash features disabled for this app.
  - Added `LibboxVpnEngine`, which starts `CommandServer`, calls `startOrReloadService(generatedConfig, OverrideOptions())`, closes libbox and the TUN fd on stop/failure, and updates engine state/log/counter flows.
  - Updated production Hilt binding from `FakeVpnEngine` to `LibboxVpnEngine`; `FakeVpnEngine` remains available for direct unit tests.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed locally.
- Acceptance criteria:
  - Production DI binds `VpnEngine` to the libbox engine.
  - Start creates and starts a libbox command server using generated sing-box config.
  - Stop closes the libbox service and TUN fd.
  - Engine state/log flows reflect starting, running, stopping, stopped, and error states.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-183
- Estimated risk: high

### TASK-185: Wire Real VPN Service Runtime And Diagnostics

- Status: done
- Goal: Make the foreground VPN lifecycle, notification stop action, runtime state, logs, and diagnostics reflect the real `ProxyVpnService`/libbox engine path.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnosticsRepository.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnosticsRepository.kt`
  - `TASKS.md`
- Implementation notes:
  - The old foreground service shell may be removed or left unused only if the real VPN service path owns the foreground notification.
  - Diagnostics must no longer imply that the VPN is backed only by fake runtime state.
  - Completed on 2026-06-05: removed the obsolete `.service.VpnForegroundService` source and manifest entry so the only app start/stop path is `ProxyVpnService`.
  - Renamed `MainActivity` helper methods to start/stop `ProxyVpnService` directly after VPN permission is granted.
  - Updated diagnostics to identify the active core as `sing-box/libbox` with `libbox_*` state values.
  - Added an engine-to-service fail-closed callback so unexpected libbox service stop closes the TUN, clears foreground runtime state, and stops the active VPN service.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed locally.
- Acceptance criteria:
  - UI start/stop controls and notification stop action operate on `ProxyVpnService`.
  - Runtime state is updated by the real service path.
  - Diagnostics identify sing-box/libbox as the active engine when the dependency is present.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-184
- Estimated risk: high

### TASK-186: Production VPN Integration Build Readiness

- Status: done
- Goal: Add local verification coverage for the production native artifact and real-engine wiring before manual QA.
- Context files to inspect:
  - `docs/qa/manual-vpn-checklist.md`
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `app/build.gradle.kts`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/LibboxVpnEngine.kt`
- Files likely to create/change:
  - `docs/qa/manual-vpn-checklist.md`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/platform/`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/engine/`
  - `TASKS.md`
- Implementation notes:
  - Native behavior still requires emulator/device manual QA in TASK-190.
  - Unit tests should focus on deterministic mapping/guard code, not Android `VpnService.Builder` behavior.
  - Completed on 2026-06-05: added `ProductionVpnWiringTest` to verify the committed libbox artifact hash, required ABI entries, production Hilt binding to `LibboxVpnEngine`, and manifest use of `ProxyVpnService` as the VPN service path.
  - Updated `docs/qa/manual-vpn-checklist.md` to explicitly reference `ProxyVpnService -> libbox CommandServer -> VpnService.Builder TUN -> sing-box` and the `verifyLibboxArtifact` preflight.
  - Updated the blocked QA result and TASK-190 note so missing production integration is no longer listed as an active blocker.
  - Smoke commands `./gradlew verifyLibboxArtifact`, `./gradlew test`, and `./gradlew assembleDebug` passed locally.
- Acceptance criteria:
  - Local build verifies native artifact and production DI wiring.
  - Manual QA checklist references the real `ProxyVpnService`/libbox path.
  - Remaining blocker is only device/emulator availability, not missing production integration.
- Test/smoke commands:
  - `./gradlew verifyLibboxArtifact`
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-185
- Estimated risk: medium

## Phase 19: Manual QA Checklist

### TASK-190: Execute MVP Manual QA

- Status: completed
- Goal: Verify the complete MVP on a real device or emulator.
- Context files to inspect:
  - `docs/qa/manual-vpn-checklist.md`
  - `PRD.md` MVP scope.
- Files likely to create/change:
  - `docs/qa/results/`
  - `TASKS.md`
- Implementation notes:
  - Record Android version, device/emulator, proxy endpoints, and results.
  - Blocked on 2026-06-05: `adb devices` returned no connected device/emulator, no `emulator` binary is available in `PATH`, and the production libbox/TUN integration required by the manual checklist is not present in `app/src/main`.
  - Production libbox/TUN integration was added later on 2026-06-05 by TASK-182 through TASK-186; at that point the remaining blocker was connected device/emulator availability plus reachable test proxy endpoints. This was superseded by the partial connected-device run below.
  - Added `docs/qa/results/2026-06-05-mvp-manual-qa-blocked.md` documenting the attempted environment checks, blockers, unexecuted checklist items, and requirements to unblock.
  - Partially executed on 2026-06-05 against Samsung SM-A165F / Android 15 with local SOCKS5 and HTTP proxy harnesses exposed through `adb reverse`; SOCKS5, HTTP, permission approval/denial, TUN creation, DoH-through-proxy, IPv6 unreachable behavior, and runtime upstream fail-closed behavior passed after fixes.
  - Device QA found and fixed missing `ACCESS_NETWORK_STATE`, Android Private DNS TCP/853 leakage to the TUN DNS address, and missing runtime upstream fail-closed monitoring.
  - Added `docs/qa/results/2026-06-05-mvp-manual-qa-partial.md` with the completed connected-device run.
  - Completed on 2026-06-05 against Samsung SM-A165F / Android 15. HTTPS proxy validation passed with a trusted qip.sh wildcard certificate endpoint over `adb reverse`, production TLS validation enabled, resolved proxy server `127.0.0.1`, and `tls.server_name: "test.i.qip.sh"`.
  - HTTPS DNS-through-proxy passed after carrying the protected proxy probe's resolved host into sing-box config generation and disabling sing-box auto interface detection only for resolved loopback proxy endpoints; the host harness observed `CONNECT 1.1.1.1:443`.
  - Foreground notification Stop passed from the Samsung notification shade; tapping `Stop` removed `tun0`, removed the notification, and stopped the service.
  - Logs UI passed level filtering, type filtering, and clearing; Clear logs produced `No logs`.
  - UDP/443 block was verified by active config `reject/drop`, a device UDP/443 probe with VPN stability, and no HTTPS proxy CONNECT traffic. Packet-level capture was not available on the non-rooted device and is documented as a residual limitation in the QA result.
- Acceptance criteria:
  - VPN permission flow passes.
  - Foreground notification passes.
  - SOCKS5/HTTP/HTTPS pass.
  - DNS through proxy passes.
  - UDP/443 block passes.
  - IPv6 unsupported/no leak behavior passes.
  - Upstream failure stops VPN.
  - Logs and diagnostics are useful.
- Test/smoke commands:
  - Manual checklist.
- Dependencies:
  - TASK-172
  - TASK-181
  - TASK-186
- Estimated risk: high

## Phase 19.5: Connected Device Runtime Fixes

### TASK-205: Fix Chrome DNS Timeout Through VPN

- Status: done
- Goal: Fix the connected-device DNS timeout where Chrome shows
  `DNS_PROBE_FINISHED_NO_INTERNET` for `2ip.ru` after VPN start.
- Context files to inspect:
  - `PRD.md` DNS and UDP sections.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box TUN inbound and route rule action docs.
  - Android `VpnService.Builder` DNS/route docs.
- Files likely to change:
  - `TASKS.md`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/platform/DefaultNetworkMonitor.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfig.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/engine/ProductionVpnWiringTest.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Connected-device reproduction on Samsung SM-A165F / Android 15 showed
    Chrome DNS requests on the VPN network timing out while the VPN TUN and
    DNS server address `172.19.0.2` were configured.
  - sing-box TUN docs state that setting `dns_address` disables the derived
    auto-hijack behavior and requires an explicit `hijack-dns` route rule.
  - sing-box route docs show `protocol` matching is based on sniffed protocol,
    and proxy client examples place an `action: "sniff"` rule before
    `protocol: "dns", action: "hijack-dns"`.
  - Android P+ can report the VPN network through default-network callbacks;
    the sing-box default interface listener now requests a non-VPN internet
    network and uses `CHANGE_NETWORK_STATE`.
  - Connected-device verification on Samsung SM-A165F / Android 15 with Chrome
    loaded `https://2ip.ru` while VPN was running. The page reported Android
    15 / Chrome 147, and Fornex Hosting S.L. in Frankfurt
    (Germany), matching the selected upstream proxy path.
- Acceptance criteria:
  - Generated route rules sniff traffic before DNS hijack.
  - Generated DNS hijack rule targets sniffed `protocol: "dns"`.
  - Android Private DNS TCP/853 reject and UDP/443 drop rules remain present.
  - Unit tests cover the new route rule order.
  - Debug APK is installed on the connected device and Chrome can load `2ip.ru`
    while VPN is running.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
  - connected-device Chrome check for `https://2ip.ru`
  - `adb shell dumpsys connectivity`
- Dependencies:
  - TASK-186
- Estimated risk: medium

### TASK-206: Add Home Route Selector And Runtime Switching

- Status: done
- Goal: Add a reference-style Home route selector with Direct and saved proxies,
  and apply route changes while VPN is already running.
- Context files to inspect:
  - `PRD.md` Home UI, VPN lifecycle, DNS, and fail-closed sections.
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeController.kt`
- Files likely to change:
  - `PRD.md`
  - `TASKS.md`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeController.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/dns/DnsMode.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/`
  - focused unit tests for ViewModel, config generation, diagnostics, and runtime.
- Acceptance criteria:
  - Home shows a dropdown with Direct first and then saved enabled proxies.
  - Home route selector layout is refined by TASK-207.
  - Direct is an explicit mode, not a fallback after upstream proxy failure.
  - Starting VPN in Direct creates a sing-box config with a direct outbound and
    DoH routed through that explicit direct outbound.
  - Selecting another proxy or Direct while VPN is running applies the new route
    without requiring a manual stop/start.
  - Selecting a failing upstream proxy while switching still stops fail-closed.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
  - connected-device UI smoke on Samsung SM-A165F / Android 15:
    - Home dropdown showed Direct and saved proxies.
    - Direct was selected and started VPN successfully.
    - While VPN was RUNNING, selecting `proxy fornex ge:8888 (HTTPS)` switched
      the active VPN route without manual stop/start.
    - Chrome loaded `https://2ip.ru` after the switch and showed IP
      `5.187.7.126`.
- Implementation notes:
  - Added `VpnRouteSelection` for explicit Direct/Proxy runtime mode.
  - Direct config uses a sing-box `direct` outbound as route final. DoH remains
    HTTPS-based; direct-mode DNS omits `detour: "direct"` because sing-box
    rejects detouring DNS to an empty direct outbound.
  - `ProxyVpnService.ACTION_SWITCH_ROUTE` reloads the route while preserving the
    foreground VPN service lifecycle. Upstream monitoring runs only for proxy
    selections.
- Dependencies:
  - TASK-205
- Estimated risk: medium

### TASK-207: Refine Home Route Control Layout

- Status: done
- Goal: Make the Home route selector standalone and replace the separate
  START/STOP controls with one stateful action button directly below it.
- Context files to inspect:
  - `PRD.md` Home UI section.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Files likely to change:
  - `PRD.md`
  - `TASKS.md`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Acceptance criteria:
  - Home route selector has no add/manage buttons beside it.
  - Proxy management remains accessible outside the selector.
  - A single full-width START/STOP VPN button appears directly below the
    route selector and switches text/action by runtime state.
  - Direct/proxy selection and running-VPN route switching behavior from
    TASK-206 remains unchanged.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Implementation notes:
  - Removed adjacent route-selector icon buttons from Home.
  - Kept the top app bar manage-proxies action.
  - Collapsed the two lower START/STOP buttons into one stateful button under
    the selected-route card.
- Dependencies:
  - TASK-206
- Estimated risk: low

## Phase 20: Post-MVP Improvements

### TASK-200: Evaluate Full UDP Proxying

- Status: pending
- Goal: Decide whether full UDP proxying is feasible and safe.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box UDP support docs.
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
  - future UDP implementation files.
- Implementation notes:
  - Do not enable by default without leak tests.
- Acceptance criteria:
  - Decision documented.
  - Follow-up tasks created if feasible.
- Test/smoke commands:
  - Manual UDP tests.
- Dependencies:
  - TASK-190
- Estimated risk: high

### TASK-201: Evaluate IPv6 Support

- Status: pending
- Goal: Plan IPv6 support after MVP.
- Context files to inspect:
  - `PRD.md` section 14.
  - sing-box IPv6/TUN docs.
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
  - future IPv6 implementation files.
- Implementation notes:
  - Require DNS, TCP, UDP, and leak tests.
- Acceptance criteria:
  - IPv6 plan is documented with tests and risks.
- Test/smoke commands:
  - Manual IPv6 leak checks.
- Dependencies:
  - TASK-190
- Estimated risk: high

### TASK-202: Evaluate Split Tunneling And Per-App Routing

- Status: pending
- Goal: Plan post-MVP per-app include/exclude routing.
- Context files to inspect:
  - Android `VpnService.Builder` app allow/disallow docs.
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
- Implementation notes:
  - Keep UX explicit and avoid accidental bypass.
- Acceptance criteria:
  - Split tunneling scope and risks documented.
- Test/smoke commands:
  - Manual per-app route tests.
- Dependencies:
  - TASK-190
- Estimated risk: medium

### TASK-203: Add Compatible Proxy Import And Export

- Status: done
- Goal: Add proxy list import/export that can read JSON exported by the reference
  `proxy_switcher` app.
- Context files to inspect:
  - `PRD.md` Proxy CRUD section.
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/utils/ProxyTransfer.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/ProxyListScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyListScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/ProxyTransfer.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyListScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/utils/ProxyTransferTest.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModelTest.kt`
- Implementation notes:
  - Preserve the reference JSON array format: `host`, `port`, `type`,
    optional `username`, optional `password`, optional `label`, and `isEnabled`.
  - Import creates new local proxy rows with fresh ids.
  - Export may include proxy passwords because it is a user-requested backup
    format; logs, diagnostics, and previews must still mask secrets.
  - Completed on 2026-06-05: added `ProxyTransfer` using the reference app's
    JSON array fields and defaults, plus unit tests proving imports from
    reference-style exports and round-trip exports stay compatible.
  - `VpnViewModel` now exports the current proxy list and imports parsed proxies
    as fresh rows without logging proxy credentials.
  - `ProxyListScreen` now exposes top-bar import/export actions, JSON paste/copy
    dialogs, and Android document picker load/save flows matching the reference
    app pattern while keeping the screen stateless for ViewModel ownership.
  - Smoke commands `./gradlew test` and `./gradlew assembleDebug` passed.
- Acceptance criteria:
  - Proxy list exposes import and export actions.
  - JSON exported by the reference `proxy_switcher` app imports successfully.
  - Exported JSON remains compatible with the reference format.
  - Invalid or empty imports return a useful error.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-130
  - TASK-131
- Estimated risk: medium

### TASK-204: Add Reference-Style Makefile

- Status: done
- Goal: Add a root Makefile similar to the reference `proxy_switcher` project,
  adapted for the VPN app's Gradle, release, verification, and ADB workflows.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/Makefile`
  - `app/build.gradle.kts`
  - `.github/workflows/android-ci.yml`
  - `.github/workflows/release.yml`
  - `keystore.properties.example`
- Files likely to create/change:
  - `Makefile`
  - `VERSION`
  - `.gitignore`
  - `TASKS.md`
- Implementation notes:
  - Keep release/tag/version bump behavior similar to the reference project.
  - Use `com.hightemp.proxy_switcher_vpn` and `proxy_switcher_vpn` artifact names.
  - Include `verifyLibboxArtifact`, unit test, debug build, release build, install,
    keystore, and VPN-safe ADB helpers.
  - Do not copy old system proxy grant/clear/check helpers.
  - Completed on 2026-06-05: added root `Makefile` with reference-style
    `release`, `tag`, `update-version`, debug/release build, install, keystore,
    and git tag flow, adapted to `com.hightemp.proxy_switcher_vpn` and the
    `proxy_switcher_vpn` artifact naming.
  - Added `verify`, `test`, `check`, and `ci` targets around the existing
    `verifyLibboxArtifact`, unit test, and debug build commands.
  - Added VPN-safe ADB helpers for force-stop, VPN/connectivity status, and
    app/libbox/sing-box logcat filtering. Old system proxy grant/clear/check
    helpers were intentionally not copied.
  - Added `VERSION` with `1.0.0`, matching the reference Makefile's
    `MAJOR.MINOR.PATCH` versionCode calculation, and ignored generated local
    keystore files in `.gitignore`.
  - Smoke commands `make help` and `make verify` passed.
- Acceptance criteria:
  - `make help` works.
  - Makefile targets reference this app id/package and VPN-safe commands.
  - Signing secrets generated by helper targets are ignored by git.
- Test/smoke commands:
  - `make help`
  - `make verify`
- Dependencies:
  - TASK-181
  - TASK-186
- Estimated risk: low
